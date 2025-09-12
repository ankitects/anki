// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::fs;
use std::fs::FileTimes;
use std::io;
use std::io::Read;
use std::path::Path;
use std::path::PathBuf;
use std::sync::LazyLock;
use std::time;

use anki_io::create_dir;
use anki_io::open_file;
use anki_io::set_file_times;
use anki_io::write_file;
use anki_io::FileIoError;
use anki_io::FileIoSnafu;
use anki_io::FileOp;
use regex::Regex;
use sha1::Digest;
use sha1::Sha1;
use tracing::debug;
use unic_ucd_category::GeneralCategory;
use unicode_normalization::is_nfc;
use unicode_normalization::UnicodeNormalization;

use crate::prelude::*;
use crate::sync::media::MAX_MEDIA_FILENAME_LENGTH;

static WINDOWS_DEVICE_NAME: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"(?xi)
            # starting with one of the following names
            ^
            (
                CON | PRN | AUX | NUL | COM[1-9] | LPT[1-9]
            )
            # either followed by a dot, or no extension
            (
                \. | $
            )
        ",
    )
    .unwrap()
});
static WINDOWS_TRAILING_CHAR: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r"(?x)
            # filenames can't end with a space or period
            (
                \x20 | \.
            )    
            $
            ",
    )
    .unwrap()
});
pub(crate) static NONSYNCABLE_FILENAME: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(
        r#"(?xi)
            ^
            (:?
                thumbs.db | .ds_store
            )
            $
            "#,
    )
    .unwrap()
});

/// True if character may cause problems on one or more platforms.
fn disallowed_char(char: char) -> bool {
    match char {
        '[' | ']' | '<' | '>' | ':' | '"' | '/' | '?' | '*' | '^' | '\\' | '|' => true,
        c if c.is_ascii_control() => true,
        // Macs do not allow invalid Unicode characters like 05F8 to be in a filename.
        c if GeneralCategory::of(c) == GeneralCategory::Unassigned => true,
        _ => false,
    }
}

fn nonbreaking_space(char: char) -> bool {
    char == '\u{a0}'
}

/// Adjust filename into the format Anki expects.
///
/// - The filename is normalized to NFC.
/// - Any problem characters are removed.
/// - Windows device names like CON and PRN have '_' appended
/// - The filename is limited to 120 bytes.
pub(crate) fn normalize_filename(fname: &str) -> Cow<'_, str> {
    let mut output = Cow::Borrowed(fname);

    if !is_nfc(output.as_ref()) {
        output = output.chars().nfc().collect::<String>().into();
    }

    normalize_nfc_filename(output)
}

/// See normalize_filename(). This function expects NFC-normalized input.
pub(crate) fn normalize_nfc_filename(mut fname: Cow<'_, str>) -> Cow<'_, str> {
    if fname.contains(disallowed_char) {
        fname = fname.replace(disallowed_char, "").into()
    }

    // convert nonbreaking spaces to regular ones, as the filename extraction
    // code treats nonbreaking spaces as regular ones
    if fname.contains(nonbreaking_space) {
        fname = fname.replace(nonbreaking_space, " ").into()
    }

    if let Cow::Owned(o) = WINDOWS_DEVICE_NAME.replace_all(fname.as_ref(), "${1}_${2}") {
        fname = o.into();
    }

    if WINDOWS_TRAILING_CHAR.is_match(fname.as_ref()) {
        fname = format!("{}_", fname.as_ref()).into();
    }

    if let Cow::Owned(o) = truncate_filename(fname.as_ref(), MAX_MEDIA_FILENAME_LENGTH) {
        fname = o.into();
    }

    fname
}

/// Return the filename in NFC form if the filename is valid.
///
/// Returns None if the filename is not normalized
/// (NFD, invalid chars, etc)
///
/// On Apple devices, the filename may be stored on disk in NFD encoding,
/// but can be accessed as NFC. On these devices, if the filename
/// is otherwise valid, the filename is returned as NFC.
#[allow(clippy::collapsible_else_if)]
pub(crate) fn filename_if_normalized(fname: &str) -> Option<Cow<'_, str>> {
    if cfg!(target_vendor = "apple") {
        if !is_nfc(fname) {
            let as_nfc = fname.chars().nfc().collect::<String>();
            if let Cow::Borrowed(_) = normalize_nfc_filename(as_nfc.as_str().into()) {
                Some(as_nfc.into())
            } else {
                None
            }
        } else {
            if let Cow::Borrowed(_) = normalize_nfc_filename(fname.into()) {
                Some(fname.into())
            } else {
                None
            }
        }
    } else {
        if let Cow::Borrowed(_) = normalize_filename(fname) {
            Some(fname.into())
        } else {
            None
        }
    }
}

/// Write desired_name into folder, renaming if existing file has different
/// content. Returns the used filename.
pub fn add_data_to_folder_uniquely<'a, P>(
    folder: P,
    desired_name: &'a str,
    data: &[u8],
    sha1: Sha1Hash,
) -> Result<Cow<'a, str>, FileIoError>
where
    P: AsRef<Path>,
{
    let normalized_name = normalize_filename(desired_name);

    let mut target_path = folder.as_ref().join(normalized_name.as_ref());

    let existing_file_hash = existing_file_sha1(&target_path)?;
    if existing_file_hash.is_none() {
        // no file with that name exists yet
        write_file(&target_path, data)?;
        return Ok(normalized_name);
    }

    if existing_file_hash.unwrap() == sha1 {
        // existing file has same checksum, nothing to do
        return Ok(normalized_name);
    }

    // give it a unique name based on its hash
    let hashed_name = add_hash_suffix_to_file_stem(normalized_name.as_ref(), &sha1);
    target_path.set_file_name(&hashed_name);

    write_file(&target_path, data)?;
    Ok(hashed_name.into())
}

/// Convert foo.jpg into foo-abcde12345679.jpg
pub(crate) fn add_hash_suffix_to_file_stem(fname: &str, hash: &Sha1Hash) -> String {
    // when appending a hash to make unique, it will be 40 bytes plus the hyphen.
    let max_len = MAX_MEDIA_FILENAME_LENGTH - 40 - 1;

    let (stem, ext) = split_and_truncate_filename(fname, max_len);

    format!("{}-{}.{}", stem, hex::encode(hash), ext)
}

/// If filename is longer than max_bytes, truncate it.
fn truncate_filename(fname: &str, max_bytes: usize) -> Cow<'_, str> {
    if fname.len() <= max_bytes {
        return Cow::Borrowed(fname);
    }

    let (stem, ext) = split_and_truncate_filename(fname, max_bytes);

    let mut new_name = if ext.is_empty() {
        stem.to_string()
    } else {
        format!("{stem}.{ext}")
    };

    // make sure we don't break Windows by ending with a space or dot
    if WINDOWS_TRAILING_CHAR.is_match(&new_name) {
        new_name.push('_');
    }

    new_name.into()
}

/// Split filename into stem and extension, and trim both so the
/// resulting filename would be under max_bytes.
/// Returns (stem, extension)
fn split_and_truncate_filename(fname: &str, max_bytes: usize) -> (&str, &str) {
    // the code assumes max_bytes will be at least 11
    debug_assert!(max_bytes > 10);

    let mut iter = fname.rsplitn(2, '.');
    let mut ext = iter.next().unwrap();
    let mut stem = if let Some(s) = iter.next() {
        s
    } else {
        // no extension, so ext holds the full filename
        let ext_tmp = ext;
        ext = "";
        ext_tmp
    };

    // cap extension to 10 bytes so stem_len can't be negative
    ext = truncated_to_char_boundary(ext, 10);

    // cap stem, allowing for the . and a trailing _
    let stem_len = max_bytes - ext.len() - 2;
    stem = truncated_to_char_boundary(stem, stem_len);

    (stem, ext)
}

/// Return a substring on a valid UTF8 boundary.
/// Based on a function in the Rust stdlib.
fn truncated_to_char_boundary(s: &str, mut max: usize) -> &str {
    if max >= s.len() {
        s
    } else {
        while !s.is_char_boundary(max) {
            max -= 1;
        }
        &s[..max]
    }
}

/// Return the SHA1 of a file if it exists, or None.
fn existing_file_sha1(path: &Path) -> Result<Option<Sha1Hash>, FileIoError> {
    match sha1_of_file(path) {
        Ok(o) => Ok(Some(o)),
        Err(e) if e.is_not_found() => Ok(None),
        Err(e) => Err(e),
    }
}

/// Return the SHA1 of a file, failing if it doesn't exist.
pub(crate) fn sha1_of_file(path: &Path) -> Result<Sha1Hash, FileIoError> {
    let mut file = open_file(path)?;
    sha1_of_reader(&mut file).context(FileIoSnafu {
        path,
        op: FileOp::Read,
    })
}

/// Return the SHA1 of a stream.
pub(crate) fn sha1_of_reader(reader: &mut impl Read) -> io::Result<Sha1Hash> {
    let mut hasher = Sha1::new();
    let mut buf = [0; 64 * 1024];
    loop {
        match reader.read(&mut buf) {
            Ok(0) => break,
            Ok(n) => hasher.update(&buf[0..n]),
            Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
            Err(e) => return Err(e),
        };
    }
    Ok(hasher.finalize().into())
}

/// Return the SHA1 of provided data.
pub(crate) fn sha1_of_data(data: &[u8]) -> Sha1Hash {
    let mut hasher = Sha1::new();
    hasher.update(data);
    hasher.finalize().into()
}

pub(crate) fn mtime_as_i64<P: AsRef<Path>>(path: P) -> io::Result<i64> {
    Ok(path
        .as_ref()
        .metadata()?
        .modified()?
        .duration_since(time::UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64)
}

pub fn remove_files<S>(media_folder: &Path, files: &[S]) -> Result<()>
where
    S: AsRef<str> + std::fmt::Debug,
{
    if files.is_empty() {
        return Ok(());
    }

    let trash_folder = trash_folder(media_folder)?;

    for file in files {
        let src_path = media_folder.join(file.as_ref());
        let dst_path = trash_folder.join(file.as_ref());

        // if the file doesn't exist, nothing to do
        if let Err(e) = fs::metadata(&src_path) {
            if e.kind() == io::ErrorKind::NotFound {
                return Ok(());
            } else {
                return Err(e.into());
            }
        }

        // move file to trash, clobbering any existing file with the same name
        fs::rename(&src_path, &dst_path)?;

        // mark it as modified, so we can expire it in the future
        let secs = time::SystemTime::now();
        let times = FileTimes::new().set_accessed(secs).set_modified(secs);
        if let Err(err) = set_file_times(&dst_path, times) {
            // The libc utimes() call fails on (some? all?) Android devices. Since we don't
            // do automatic expiry yet, we can safely ignore the error.
            if !cfg!(target_os = "android") {
                return Err(err.into());
            }
        }
    }

    Ok(())
}

pub(super) fn trash_folder(media_folder: &Path) -> Result<PathBuf> {
    let trash_folder = media_folder.with_file_name("media.trash");
    match create_dir(&trash_folder) {
        Ok(()) => Ok(trash_folder),
        Err(e) => {
            if e.source.kind() == io::ErrorKind::AlreadyExists {
                Ok(trash_folder)
            } else {
                Err(e.into())
            }
        }
    }
}

pub struct AddedFile {
    pub fname: String,
    pub sha1: Sha1Hash,
    pub mtime: i64,
    pub renamed_from: Option<String>,
}

/// Add a file received from AnkiWeb into the media folder.
///
/// Because AnkiWeb did not previously enforce file name limits and invalid
/// characters, we'll need to rename the file if it is not valid.
pub(crate) fn add_file_from_ankiweb(
    media_folder: &Path,
    fname: &str,
    data: &[u8],
) -> Result<AddedFile> {
    let sha1 = sha1_of_data(data);
    let normalized = normalize_filename(fname);

    // if the filename is already valid, we can write the file directly
    let (renamed_from, path) = if let Cow::Borrowed(_) = normalized {
        let path = media_folder.join(normalized.as_ref());
        debug!(fname = normalized.as_ref(), "write");
        write_file(&path, data)?;
        (None, path)
    } else {
        // ankiweb sent us a non-normalized filename, so we'll rename it
        let new_name = add_data_to_folder_uniquely(media_folder, fname, data, sha1)?;
        debug!(
            fname,
            rename_to = new_name.as_ref(),
            "non-normalized filename received"
        );
        (
            Some(fname.to_string()),
            media_folder.join(new_name.as_ref()),
        )
    };

    let mtime = mtime_as_i64(path)?;

    Ok(AddedFile {
        fname: normalized.to_string(),
        sha1,
        mtime,
        renamed_from,
    })
}

pub(crate) fn data_for_file(media_folder: &Path, fname: &str) -> Result<Option<Vec<u8>>> {
    let mut file = match open_file(media_folder.join(fname)) {
        Err(e) if e.is_not_found() => return Ok(None),
        res => res?,
    };
    let mut buf = vec![];
    file.read_to_end(&mut buf)?;
    Ok(Some(buf))
}

#[cfg(test)]
mod test {
    use std::borrow::Cow;

    use tempfile::tempdir;

    use crate::media::files::add_data_to_folder_uniquely;
    use crate::media::files::add_hash_suffix_to_file_stem;
    use crate::media::files::normalize_filename;
    use crate::media::files::remove_files;
    use crate::media::files::sha1_of_data;
    use crate::media::files::truncate_filename;
    use crate::sync::media::MAX_MEDIA_FILENAME_LENGTH;

    #[test]
    fn normalize() {
        assert_eq!(normalize_filename("foo.jpg"), Cow::Borrowed("foo.jpg"));
        assert_eq!(
            normalize_filename("con.jpg[]><:\"/?*^\\|\0\r\n").as_ref(),
            "con_.jpg"
        );

        assert_eq!(normalize_filename("test.").as_ref(), "test._");
        assert_eq!(normalize_filename("test ").as_ref(), "test _");

        let expected_stem_len = MAX_MEDIA_FILENAME_LENGTH - ".jpg".len() - 1;
        assert_eq!(
            normalize_filename(&format!(
                "{}.jpg",
                "x".repeat(MAX_MEDIA_FILENAME_LENGTH * 2)
            )),
            "x".repeat(expected_stem_len) + ".jpg"
        );
    }

    #[test]
    fn add_hash_suffix() {
        let hash = sha1_of_data(b"hello");
        assert_eq!(
            add_hash_suffix_to_file_stem("test.jpg", &hash).as_str(),
            "test-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d.jpg"
        );
    }

    #[test]
    fn adding_removing() {
        let dir = tempdir().unwrap();
        let dpath = dir.path();

        // no existing file case
        let h1 = sha1_of_data(b"hello");
        assert_eq!(
            add_data_to_folder_uniquely(dpath, "test.mp3", b"hello", h1).unwrap(),
            "test.mp3"
        );

        // same contents case
        assert_eq!(
            add_data_to_folder_uniquely(dpath, "test.mp3", b"hello", h1).unwrap(),
            "test.mp3"
        );

        // different contents
        let h2 = sha1_of_data(b"hello1");
        assert_eq!(
            add_data_to_folder_uniquely(dpath, "test.mp3", b"hello1", h2).unwrap(),
            "test-88fdd585121a4ccb3d1540527aee53a77c77abb8.mp3"
        );

        let mut written_files = std::fs::read_dir(dpath)
            .unwrap()
            .map(|d| d.unwrap().file_name().to_string_lossy().into_owned())
            .collect::<Vec<_>>();
        written_files.sort();
        assert_eq!(
            written_files,
            vec![
                "test-88fdd585121a4ccb3d1540527aee53a77c77abb8.mp3",
                "test.mp3",
            ]
        );

        // remove
        remove_files(dpath, written_files.as_slice()).unwrap();
    }

    #[test]
    fn truncation() {
        let one_less = "x".repeat(MAX_MEDIA_FILENAME_LENGTH - 1);
        assert_eq!(
            truncate_filename(&one_less, MAX_MEDIA_FILENAME_LENGTH),
            Cow::Borrowed(&one_less)
        );
        let equal = "x".repeat(MAX_MEDIA_FILENAME_LENGTH);
        assert_eq!(
            truncate_filename(&equal, MAX_MEDIA_FILENAME_LENGTH),
            Cow::Borrowed(&equal)
        );
        let equal = format!("{}.jpg", "x".repeat(MAX_MEDIA_FILENAME_LENGTH - 4));
        assert_eq!(
            truncate_filename(&equal, MAX_MEDIA_FILENAME_LENGTH),
            Cow::Borrowed(&equal)
        );
        let one_more = "x".repeat(MAX_MEDIA_FILENAME_LENGTH + 1);
        assert_eq!(
            truncate_filename(&one_more, MAX_MEDIA_FILENAME_LENGTH),
            Cow::<str>::Owned("x".repeat(MAX_MEDIA_FILENAME_LENGTH - 2))
        );
        assert_eq!(
            truncate_filename(
                &" ".repeat(MAX_MEDIA_FILENAME_LENGTH + 1),
                MAX_MEDIA_FILENAME_LENGTH
            ),
            Cow::<str>::Owned(format!("{}_", " ".repeat(MAX_MEDIA_FILENAME_LENGTH - 2)))
        );
    }
}
