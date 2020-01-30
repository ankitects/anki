// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::Result;
use crate::media::database::MediaEntry;
use crate::media::MediaManager;
use lazy_static::lazy_static;
use regex::Regex;
use sha1::Sha1;
use std::borrow::Cow;
use std::collections::HashMap;
use std::io::Read;
use std::path::Path;
use std::{fs, io, time};
use unicode_normalization::{is_nfc_quick, IsNormalized, UnicodeNormalization};

/// The maximum length we allow a filename to be. When combined
/// with the rest of the path, the full path needs to be under ~240 chars
/// on some platforms, and some filesystems like eCryptFS will increase
/// the length of the filename.
static MAX_FILENAME_LENGTH: usize = 120;

/// Media syncing does not support files over 100MiB.
static MEDIA_SYNC_FILESIZE_LIMIT: usize = 100 * 1024 * 1024;

lazy_static! {
    static ref WINDOWS_DEVICE_NAME: Regex = Regex::new(
        r#"(?xi)
            # starting with one of the following names
            ^
            (
                CON | PRN | AUX | NUL | COM[1-9] | LPT[1-9]
            )
            # either followed by a dot, or no extension
            (
                \. | $
            )
        "#
    )
    .unwrap();
    static ref NONSYNCABLE_FILENAME: Regex = Regex::new(
        r#"(?xi)
            ^
            (:?
                thumbs.db | .ds_store
            )
            $
            "#
    )
    .unwrap();
}

/// True if character may cause problems on one or more platforms.
fn disallowed_char(char: char) -> bool {
    match char {
        '[' | ']' | '<' | '>' | ':' | '"' | '/' | '?' | '*' | '^' | '\\' | '|' => true,
        c if c.is_ascii_control() => true,
        _ => false,
    }
}

/// Adjust filename into the format Anki expects.
///
/// - The filename is normalized to NFC.
/// - Any problem characters are removed.
/// - Windows device names like CON and PRN have '_' appended
/// - The filename is limited to 120 bytes.
fn normalize_filename(fname: &str) -> Cow<str> {
    let mut output = Cow::Borrowed(fname);

    if is_nfc_quick(output.chars()) != IsNormalized::Yes {
        output = output.chars().nfc().collect::<String>().into();
    }

    if output.chars().any(disallowed_char) {
        output = output.replace(disallowed_char, "").into()
    }

    if let Cow::Owned(o) = WINDOWS_DEVICE_NAME.replace_all(output.as_ref(), "${1}_${2}") {
        output = o.into();
    }

    if let Cow::Owned(o) = truncate_filename(output.as_ref(), MAX_FILENAME_LENGTH) {
        output = o.into();
    }

    output
}

/// Write desired_name into folder, renaming if existing file has different content.
/// Returns the used filename.
pub fn add_data_to_folder_uniquely<'a, P>(
    folder: P,
    desired_name: &'a str,
    data: &[u8],
    sha1: [u8; 20],
) -> io::Result<Cow<'a, str>>
where
    P: AsRef<Path>,
{
    let normalized_name = normalize_filename(desired_name);

    let mut target_path = folder.as_ref().join(normalized_name.as_ref());

    let existing_file_hash = existing_file_sha1(&target_path)?;
    if existing_file_hash.is_none() {
        // no file with that name exists yet
        fs::write(&target_path, data)?;
        return Ok(normalized_name);
    }

    if existing_file_hash.unwrap() == sha1 {
        // existing file has same checksum, nothing to do
        return Ok(normalized_name);
    }

    // give it a unique name based on its hash
    let hashed_name = add_hash_suffix_to_file_stem(normalized_name.as_ref(), &sha1);
    target_path.set_file_name(&hashed_name);

    fs::write(&target_path, data)?;
    Ok(hashed_name.into())
}

/// Convert foo.jpg into foo-abcde12345679.jpg
fn add_hash_suffix_to_file_stem(fname: &str, hash: &[u8; 20]) -> String {
    // when appending a hash to make unique, it will be 20 bytes plus the hyphen.
    let max_len = MAX_FILENAME_LENGTH - 20 - 1;

    let (stem, ext) = split_and_truncate_filename(fname, max_len);

    format!("{}-{}.{}", stem, hex::encode(hash), ext)
}

/// If filename is longer than max_bytes, truncate it.
fn truncate_filename(fname: &str, max_bytes: usize) -> Cow<str> {
    if fname.len() <= max_bytes {
        return Cow::Borrowed(fname);
    }

    let (stem, ext) = split_and_truncate_filename(fname, max_bytes);

    format!("{}.{}", stem, ext).into()
}

/// Split filename into stem and extension, and trim both so the
/// resulting filename would be under max_bytes.
/// Returns (stem, extension)
fn split_and_truncate_filename(fname: &str, max_bytes: usize) -> (&str, &str) {
    // the code assumes the length will be at least 11
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
    ext = truncate_to_char_boundary(ext, 10);

    // cap stem, allowing for the .
    let stem_len = max_bytes - ext.len() - 1;
    stem = truncate_to_char_boundary(stem, stem_len);

    (stem, ext)
}

/// Trim a string on a valid UTF8 boundary.
/// Based on a funtion in the Rust stdlib.
fn truncate_to_char_boundary(s: &str, mut max: usize) -> &str {
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
fn existing_file_sha1(path: &Path) -> io::Result<Option<[u8; 20]>> {
    match sha1_of_file(path) {
        Ok(o) => Ok(Some(o)),
        Err(e) => {
            if e.kind() == io::ErrorKind::NotFound {
                Ok(None)
            } else {
                Err(e)
            }
        }
    }
}

/// Return the SHA1 of a file, failing if it doesn't exist.
fn sha1_of_file(path: &Path) -> io::Result<[u8; 20]> {
    let mut file = fs::File::open(path)?;
    let mut hasher = Sha1::new();
    let mut buf = [0; 64 * 1024];
    loop {
        match file.read(&mut buf) {
            Ok(0) => break,
            Ok(n) => hasher.update(&buf[0..n]),
            Err(e) => {
                if e.kind() == io::ErrorKind::Interrupted {
                    continue;
                } else {
                    return Err(e);
                }
            }
        };
    }
    Ok(hasher.digest().bytes())
}

/// Return the SHA1 of provided data.
pub(crate) fn sha1_of_data(data: &[u8]) -> [u8; 20] {
    let mut hasher = Sha1::new();
    hasher.update(data);
    hasher.digest().bytes()
}

struct FilesystemEntry {
    fname: String,
    sha1: Option<[u8; 20]>,
    mtime: i64,
    is_new: bool,
}

fn mtime_as_i64<P: AsRef<Path>>(path: P) -> io::Result<i64> {
    Ok(path
        .as_ref()
        .metadata()?
        .modified()?
        .duration_since(time::UNIX_EPOCH)
        .unwrap()
        .as_secs() as i64)
}

impl MediaManager {
    /// Add a file to the media folder.
    ///
    /// If a file with differing contents already exists, a hash will be
    /// appended to the name.
    ///
    /// Also notes the file in the media database.
    pub fn add_file<'a>(&mut self, desired_name: &'a str, data: &[u8]) -> Result<Cow<'a, str>> {
        let pre_add_folder_mtime = mtime_as_i64(&self.media_folder)?;

        // add file to folder
        let data_hash = sha1_of_data(data);
        let chosen_fname =
            add_data_to_folder_uniquely(&self.media_folder, desired_name, data, data_hash)?;
        let file_mtime = mtime_as_i64(self.media_folder.join(chosen_fname.as_ref()))?;
        let post_add_folder_mtime = mtime_as_i64(&self.media_folder)?;

        // add to the media DB
        self.transact(|ctx| {
            let existing_entry = ctx.get_entry(&chosen_fname)?;
            let new_sha1 = Some(data_hash);

            let entry_update_required = match existing_entry {
                Some(existing) if existing.sha1 == new_sha1 => false,
                _ => true,
            };

            if entry_update_required {
                ctx.set_entry(&MediaEntry {
                    fname: chosen_fname.to_string(),
                    sha1: new_sha1,
                    mtime: file_mtime,
                    sync_required: true,
                })?;
            }

            let mut meta = ctx.get_meta()?;
            if meta.folder_mtime == pre_add_folder_mtime {
                // if media db was in sync with folder prior to this add,
                // we can keep it in sync
                meta.folder_mtime = post_add_folder_mtime;
                ctx.set_meta(&meta)?;
            } else {
                // otherwise, leave it alone so that other pending changes
                // get picked up later
            }

            Ok(())
        })?;

        Ok(chosen_fname)
    }

    /// Note any added/changed/deleted files.
    ///
    /// In the future, we could register files in the media DB as they
    /// are added, meaning that for users who don't modify files externally, the
    /// folder scan could be skipped.
    pub fn register_changes(&mut self) -> Result<()> {
        // folder mtime unchanged?
        let dirmod = mtime_as_i64(&self.media_folder)?;
        let mut meta = self.get_meta()?;
        if dirmod == meta.folder_mtime {
            return Ok(());
        } else {
            meta.folder_mtime = dirmod;
        }

        let mtimes = self.query(|ctx| ctx.all_mtimes())?;

        let (changed, removed) = self.media_folder_changes(mtimes)?;

        self.add_updated_entries(changed)?;
        self.remove_deleted_files(removed)?;

        self.set_meta(&meta)?;

        Ok(())
    }

    /// Scan through the media folder, finding changes.
    /// Returns (added/changed files, removed files).
    ///
    /// Checks for invalid filenames and unicode normalization are deferred
    /// until syncing time, as we can't trust the entries previous Anki versions
    /// wrote are correct.
    fn media_folder_changes(
        &self,
        mut mtimes: HashMap<String, i64>,
    ) -> Result<(Vec<FilesystemEntry>, Vec<String>)> {
        let mut added_or_changed = vec![];

        // loop through on-disk files
        for dentry in self.media_folder.read_dir()? {
            let dentry = dentry?;

            // skip folders
            if dentry.file_type()?.is_dir() {
                continue;
            }

            // if the filename is not valid unicode, skip it
            let fname_os = dentry.file_name();
            let fname = match fname_os.to_str() {
                Some(s) => s,
                None => continue,
            };

            // ignore blacklisted files
            if NONSYNCABLE_FILENAME.is_match(fname) {
                continue;
            }

            // ignore large files
            let metadata = dentry.metadata()?;
            if metadata.len() > MEDIA_SYNC_FILESIZE_LIMIT as u64 {
                continue;
            }

            // remove from mtimes for later deletion tracking
            let previous_mtime = mtimes.remove(fname);

            // skip files that have not been modified
            let mtime = metadata
                .modified()?
                .duration_since(time::UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64;
            if let Some(previous_mtime) = previous_mtime {
                if previous_mtime == mtime {
                    continue;
                }
            }

            // add entry to the list
            let sha1 = Some(sha1_of_file(&dentry.path())?);
            added_or_changed.push(FilesystemEntry {
                fname: fname.to_string(),
                sha1,
                mtime,
                is_new: previous_mtime.is_none(),
            });
        }

        // any remaining entries from the database have been deleted
        let removed: Vec<_> = mtimes.into_iter().map(|(k, _)| k).collect();

        Ok((added_or_changed, removed))
    }

    /// Add added/updated entries to the media DB.
    ///
    /// Skip files where the mod time differed, but checksums are the same.
    fn add_updated_entries(&mut self, entries: Vec<FilesystemEntry>) -> Result<()> {
        for chunk in entries.chunks(1_024) {
            self.transact(|ctx| {
                for fentry in chunk {
                    let mut sync_required = true;
                    if !fentry.is_new {
                        if let Some(db_entry) = ctx.get_entry(&fentry.fname)? {
                            if db_entry.sha1 == fentry.sha1 {
                                // mtime bumped but file contents are the same,
                                // so we can preserve the current updated flag.
                                // we still need to update the mtime however.
                                sync_required = db_entry.sync_required
                            }
                        }
                    };

                    ctx.set_entry(&MediaEntry {
                        fname: fentry.fname.clone(),
                        sha1: fentry.sha1,
                        mtime: fentry.mtime,
                        sync_required,
                    })?;
                }

                Ok(())
            })?;
        }

        Ok(())
    }

    /// Remove deleted files from the media DB.
    fn remove_deleted_files(&mut self, removed: Vec<String>) -> Result<()> {
        for chunk in removed.chunks(4_096) {
            self.transact(|ctx| {
                for fname in chunk {
                    ctx.set_entry(&MediaEntry {
                        fname: fname.clone(),
                        sha1: None,
                        mtime: 0,
                        sync_required: true,
                    })?;
                }

                Ok(())
            })?;
        }

        Ok(())
    }
}

#[cfg(test)]
mod test {
    use crate::err::Result;
    use crate::media::database::MediaEntry;
    use crate::media::files::{
        add_data_to_folder_uniquely, add_hash_suffix_to_file_stem, normalize_filename,
        sha1_of_data, MAX_FILENAME_LENGTH,
    };
    use crate::media::MediaManager;
    use std::borrow::Cow;
    use std::path::Path;
    use std::time::Duration;
    use std::{fs, time};
    use tempfile::tempdir;
    use utime;

    #[test]
    fn test_normalize() {
        assert_eq!(normalize_filename("foo.jpg"), Cow::Borrowed("foo.jpg"));
        assert_eq!(
            normalize_filename("con.jpg[]><:\"/?*^\\|\0\r\n").as_ref(),
            "con_.jpg"
        );

        let expected_stem_len = MAX_FILENAME_LENGTH - ".jpg".len();
        assert_eq!(
            normalize_filename(&format!("{}.jpg", "x".repeat(MAX_FILENAME_LENGTH * 2))),
            "x".repeat(expected_stem_len) + ".jpg"
        );
    }

    #[test]
    fn test_add_hash_suffix() {
        let hash = sha1_of_data("hello".as_bytes());
        assert_eq!(
            add_hash_suffix_to_file_stem("test.jpg", &hash).as_str(),
            "test-aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d.jpg"
        );
    }

    #[test]
    fn test_adding() {
        let dir = tempdir().unwrap();
        let dpath = dir.path();

        // no existing file case
        let h1 = sha1_of_data("hello".as_bytes());
        assert_eq!(
            add_data_to_folder_uniquely(dpath, "test.mp3", "hello".as_bytes(), h1).unwrap(),
            "test.mp3"
        );

        // same contents case
        assert_eq!(
            add_data_to_folder_uniquely(dpath, "test.mp3", "hello".as_bytes(), h1).unwrap(),
            "test.mp3"
        );

        // different contents
        let h2 = sha1_of_data("hello1".as_bytes());
        assert_eq!(
            add_data_to_folder_uniquely(dpath, "test.mp3", "hello1".as_bytes(), h2).unwrap(),
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
    }

    // helper
    fn change_mtime(p: &Path) {
        let mtime = p.metadata().unwrap().modified().unwrap();
        let new_mtime = mtime - Duration::from_secs(3);
        let secs = new_mtime
            .duration_since(time::UNIX_EPOCH)
            .unwrap()
            .as_secs();
        utime::set_file_times(p, secs, secs).unwrap();
    }

    #[test]
    fn test_change_tracking() -> Result<()> {
        let dir = tempdir()?;
        let media_dir = dir.path().join("media");
        std::fs::create_dir(&media_dir)?;
        let media_db = dir.path().join("media.db");

        let mut mgr = MediaManager::new(&media_dir, media_db)?;
        assert_eq!(mgr.count()?, 0);

        // add a file and check it's picked up
        let f1 = media_dir.join("file.jpg");
        fs::write(&f1, "hello")?;

        change_mtime(&media_dir);
        mgr.register_changes()?;

        assert_eq!(mgr.count()?, 1);
        assert_eq!(mgr.changes_pending()?, 1);
        let mut entry = mgr.get_entry("file.jpg")?.unwrap();
        assert_eq!(
            entry,
            MediaEntry {
                fname: "file.jpg".into(),
                sha1: Some(sha1_of_data("hello".as_bytes())),
                mtime: f1
                    .metadata()?
                    .modified()?
                    .duration_since(time::UNIX_EPOCH)
                    .unwrap()
                    .as_secs() as i64,
                sync_required: true,
            }
        );

        // mark it as unmodified
        entry.sync_required = false;
        mgr.set_entry(&entry)?;
        assert_eq!(mgr.changes_pending()?, 0);

        // modify it
        fs::write(&f1, "hello1")?;
        change_mtime(&f1);

        change_mtime(&media_dir);
        mgr.register_changes()?;

        assert_eq!(mgr.count()?, 1);
        assert_eq!(mgr.changes_pending()?, 1);
        assert_eq!(
            mgr.get_entry("file.jpg")?.unwrap(),
            MediaEntry {
                fname: "file.jpg".into(),
                sha1: Some(sha1_of_data("hello1".as_bytes())),
                mtime: f1
                    .metadata()?
                    .modified()?
                    .duration_since(time::UNIX_EPOCH)
                    .unwrap()
                    .as_secs() as i64,
                sync_required: true,
            }
        );

        // mark it as unmodified
        entry.sync_required = false;
        mgr.set_entry(&entry)?;
        assert_eq!(mgr.changes_pending()?, 0);

        // delete it
        fs::remove_file(&f1)?;

        change_mtime(&media_dir);
        mgr.register_changes().unwrap();

        assert_eq!(mgr.count()?, 0);
        assert_eq!(mgr.changes_pending()?, 1);
        assert_eq!(
            mgr.get_entry("file.jpg")?.unwrap(),
            MediaEntry {
                fname: "file.jpg".into(),
                sha1: None,
                mtime: 0,
                sync_required: true,
            }
        );

        Ok(())
    }
}
