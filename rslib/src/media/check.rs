// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;
use std::collections::HashSet;
use std::fs;
use std::io;
use std::path::Path;

use anki_i18n::without_unicode_isolation;
use tracing::debug;

use crate::error::DbErrorKind;
use crate::latex::extract_latex_expanding_clozes;
use crate::media::files::data_for_file;
use crate::media::files::filename_if_normalized;
use crate::media::files::normalize_nfc_filename;
use crate::media::files::trash_folder;
use crate::media::MediaManager;
use crate::prelude::*;
use crate::sync::media::MAX_INDIVIDUAL_MEDIA_FILE_SIZE;
use crate::text::extract_media_refs;
use crate::text::normalize_to_nfc;
use crate::text::MediaRef;
use crate::text::REMOTE_FILENAME;

#[derive(Debug, PartialEq, Eq, Clone)]
pub struct MediaCheckOutput {
    pub unused: Vec<String>,
    pub missing: Vec<String>,
    pub missing_media_notes: Vec<NoteId>,
    pub renamed: HashMap<String, String>,
    pub dirs: Vec<String>,
    pub oversize: Vec<String>,
    pub trash_count: u64,
    pub trash_bytes: u64,
}

#[derive(Debug, PartialEq, Eq, Default)]
struct MediaFolderCheck {
    files: Vec<String>,
    renamed: HashMap<String, String>,
    dirs: Vec<String>,
    oversize: Vec<String>,
}

pub struct MediaChecker<'a, 'b, P>
where
    P: FnMut(usize) -> bool,
{
    ctx: &'a mut Collection,
    mgr: &'b MediaManager,
    progress_cb: P,
    checked: usize,
}

impl<P> MediaChecker<'_, '_, P>
where
    P: FnMut(usize) -> bool,
{
    pub(crate) fn new<'a, 'b>(
        ctx: &'a mut Collection,
        mgr: &'b MediaManager,
        progress_cb: P,
    ) -> MediaChecker<'a, 'b, P> {
        MediaChecker {
            ctx,
            mgr,
            progress_cb,
            checked: 0,
        }
    }

    pub fn check(&mut self) -> Result<MediaCheckOutput> {
        let folder_check = self.check_media_folder()?;
        let references = self.check_media_references(&folder_check.renamed)?;
        let unused_and_missing = UnusedAndMissingFiles::new(folder_check.files, references);
        let (trash_count, trash_bytes) = self.files_in_trash()?;
        Ok(MediaCheckOutput {
            unused: unused_and_missing.unused,
            missing: unused_and_missing.missing,
            missing_media_notes: unused_and_missing.missing_media_notes,
            renamed: folder_check.renamed,
            dirs: folder_check.dirs,
            oversize: folder_check.oversize,
            trash_count,
            trash_bytes,
        })
    }

    pub fn summarize_output(&self, output: &mut MediaCheckOutput) -> String {
        let mut buf = String::new();
        let tr = &self.ctx.tr;

        // top summary area
        if output.trash_count > 0 {
            let megs = (output.trash_bytes as f32) / 1024.0 / 1024.0;
            buf += &tr.media_check_trash_count(output.trash_count, megs);
            buf.push('\n');
        }

        buf += &tr.media_check_missing_count(output.missing.len());
        buf.push('\n');

        buf += &tr.media_check_unused_count(output.unused.len());
        buf.push('\n');

        if !output.renamed.is_empty() {
            buf += &tr.media_check_renamed_count(output.renamed.len());
            buf.push('\n');
        }
        if !output.oversize.is_empty() {
            buf += &tr.media_check_oversize_count(output.oversize.len());
            buf.push('\n');
        }
        if !output.dirs.is_empty() {
            buf += &tr.media_check_subfolder_count(output.dirs.len());
            buf.push('\n');
        }

        buf.push('\n');

        if !output.renamed.is_empty() {
            buf += &tr.media_check_renamed_header();
            buf.push('\n');
            for (old, new) in &output.renamed {
                buf += &without_unicode_isolation(
                    &tr.media_check_renamed_file(old.as_str(), new.as_str()),
                );
                buf.push('\n');
            }
            buf.push('\n')
        }

        if !output.oversize.is_empty() {
            output.oversize.sort();
            buf += &tr.media_check_oversize_header();
            buf.push('\n');
            for fname in &output.oversize {
                buf += &without_unicode_isolation(&tr.media_check_oversize_file(fname.as_str()));
                buf.push('\n');
            }
            buf.push('\n')
        }

        if !output.dirs.is_empty() {
            output.dirs.sort();
            buf += &tr.media_check_subfolder_header();
            buf.push('\n');
            for fname in &output.dirs {
                buf += &without_unicode_isolation(&tr.media_check_subfolder_file(fname.as_str()));
                buf.push('\n');
            }
            buf.push('\n')
        }

        if !output.missing.is_empty() {
            output.missing.sort();
            buf += &tr.media_check_missing_header();
            buf.push('\n');
            for fname in &output.missing {
                buf += &without_unicode_isolation(&tr.media_check_missing_file(fname.as_str()));
                buf.push('\n');
            }
            buf.push('\n')
        }

        if !output.unused.is_empty() {
            output.unused.sort();
            buf += &tr.media_check_unused_header();
            buf.push('\n');
            for fname in &output.unused {
                buf += &without_unicode_isolation(&tr.media_check_unused_file(fname.as_str()));
                buf.push('\n');
            }
        }

        buf
    }

    /// Check all the files in the media folder.
    ///
    /// - Renames files with invalid names
    /// - Notes folders/oversized files
    /// - Gathers a list of all files
    fn check_media_folder(&mut self) -> Result<MediaFolderCheck> {
        let mut out = MediaFolderCheck::default();
        for dentry in self.mgr.media_folder.read_dir()? {
            let dentry = dentry?;

            self.checked += 1;
            if self.checked % 10 == 0 {
                self.fire_progress_cb()?;
            }

            // if the filename is not valid unicode, skip it
            let fname_os = dentry.file_name();
            let disk_fname = match fname_os.to_str() {
                Some(s) => s,
                None => continue,
            };

            // skip folders
            if dentry.file_type()?.is_dir() {
                out.dirs.push(disk_fname.to_string());
                continue;
            }

            // ignore large files and zero byte files
            let metadata = dentry.metadata()?;
            if metadata.len() > MAX_INDIVIDUAL_MEDIA_FILE_SIZE as u64 {
                out.oversize.push(disk_fname.to_string());
                continue;
            }
            if metadata.len() == 0 {
                continue;
            }

            if let Some(norm_name) = filename_if_normalized(disk_fname) {
                out.files.push(norm_name.into_owned());
            } else {
                match data_for_file(&self.mgr.media_folder, disk_fname)? {
                    Some(data) => {
                        let norm_name = self.normalize_file(disk_fname, data)?;
                        out.renamed
                            .insert(disk_fname.to_string(), norm_name.to_string());
                        out.files.push(norm_name.into_owned());
                    }
                    None => {
                        // file not found, caused by the file being removed at this exact instant,
                        // or the path being larger than MAXPATH on Windows
                        continue;
                    }
                };
            }
        }

        Ok(out)
    }

    /// Write file data to normalized location, moving old file to trash.
    fn normalize_file<'a>(&mut self, disk_fname: &'a str, data: Vec<u8>) -> Result<Cow<'a, str>> {
        // add a copy of the file using the correct name
        let fname = self.mgr.add_file(disk_fname, &data)?;
        debug!(from = disk_fname, to = &fname.as_ref(), "renamed");
        assert_ne!(fname.as_ref(), disk_fname);

        // remove the original file
        let path = &self.mgr.media_folder.join(disk_fname);
        fs::remove_file(path)?;

        Ok(fname)
    }

    fn fire_progress_cb(&mut self) -> Result<()> {
        if (self.progress_cb)(self.checked) {
            Ok(())
        } else {
            Err(AnkiError::Interrupted)
        }
    }

    /// Returns the count and total size of the files in the trash folder
    fn files_in_trash(&mut self) -> Result<(u64, u64)> {
        let trash = trash_folder(&self.mgr.media_folder)?;
        let mut total_files = 0;
        let mut total_bytes = 0;

        for dentry in trash.read_dir()? {
            let dentry = dentry?;

            self.checked += 1;
            if self.checked % 10 == 0 {
                self.fire_progress_cb()?;
            }

            if dentry.file_name() == ".DS_Store" {
                continue;
            }

            let meta = dentry.metadata()?;

            total_files += 1;
            total_bytes += meta.len();
        }

        Ok((total_files, total_bytes))
    }

    pub fn empty_trash(&mut self) -> Result<()> {
        let trash = trash_folder(&self.mgr.media_folder)?;

        for dentry in trash.read_dir()? {
            let dentry = dentry?;

            self.checked += 1;
            if self.checked % 10 == 0 {
                self.fire_progress_cb()?;
            }

            fs::remove_file(dentry.path())?;
        }

        Ok(())
    }

    pub fn restore_trash(&mut self) -> Result<()> {
        let trash = trash_folder(&self.mgr.media_folder)?;

        for dentry in trash.read_dir()? {
            let dentry = dentry?;

            self.checked += 1;
            if self.checked % 10 == 0 {
                self.fire_progress_cb()?;
            }

            let orig_path = self.mgr.media_folder.join(dentry.file_name());
            // if the original filename doesn't exist, we can just rename
            if let Err(e) = fs::metadata(&orig_path) {
                if e.kind() == io::ErrorKind::NotFound {
                    fs::rename(dentry.path(), &orig_path)?;
                } else {
                    return Err(e.into());
                }
            } else {
                // ensure we don't overwrite different data
                let fname_os = dentry.file_name();
                let fname = fname_os.to_string_lossy();
                if let Some(data) = data_for_file(&trash, fname.as_ref())? {
                    let _new_fname = self.mgr.add_file(fname.as_ref(), &data)?;
                } else {
                    debug!(?fname, "file disappeared while restoring trash");
                }
                fs::remove_file(dentry.path())?;
            }
        }

        Ok(())
    }

    /// Find all media references in notes, fixing as necessary.
    fn check_media_references(
        &mut self,
        renamed: &HashMap<String, String>,
    ) -> Result<HashMap<String, Vec<NoteId>>> {
        let mut referenced_files = HashMap::new();
        let notetypes = self.ctx.get_all_notetypes()?;
        let mut collection_modified = false;

        let nids = self.ctx.search_notes_unordered("")?;
        let usn = self.ctx.usn()?;
        for nid in nids {
            self.checked += 1;
            if self.checked % 10 == 0 {
                self.fire_progress_cb()?;
            }
            let mut note = self.ctx.storage.get_note(nid)?.unwrap();
            let nt = notetypes.get(&note.notetype_id).ok_or_else(|| {
                AnkiError::db_error("missing note type", DbErrorKind::MissingEntity)
            })?;
            let mut tracker = |fname| {
                referenced_files
                    .entry(fname)
                    .or_insert_with(Vec::new)
                    .push(nid)
            };
            if fix_and_extract_media_refs(&mut note, &mut tracker, renamed, &self.mgr.media_folder)?
            {
                // note was modified, needs saving
                note.prepare_for_update(nt, false)?;
                note.set_modified(usn);
                self.ctx.storage.update_note(&note)?;
                collection_modified = true;
            }

            // extract latex
            extract_latex_refs(&note, &mut tracker, nt.config.latex_svg);
        }

        if collection_modified {
            // fixme: need to refactor to use new transaction handling?
            // self.ctx.storage.commit_trx()?;
        }

        Ok(referenced_files)
    }
}

/// Returns true if note was modified.
fn fix_and_extract_media_refs(
    note: &mut Note,
    mut tracker: impl FnMut(String),
    renamed: &HashMap<String, String>,
    media_folder: &Path,
) -> Result<bool> {
    let mut updated = false;

    for idx in 0..note.fields().len() {
        let field = normalize_and_maybe_rename_files(
            &note.fields()[idx],
            renamed,
            &mut tracker,
            media_folder,
        );
        if let Cow::Owned(field) = field {
            // field was modified, need to save
            note.set_field(idx, field)?;
            updated = true;
        }
    }

    Ok(updated)
}

/// Convert any filenames that are not in NFC form into NFC,
/// and update any files that were renamed on disk.
fn normalize_and_maybe_rename_files<'a>(
    field: &'a str,
    renamed: &HashMap<String, String>,
    mut tracker: impl FnMut(String),
    media_folder: &Path,
) -> Cow<'a, str> {
    let refs = extract_media_refs(field);
    let mut field: Cow<str> = field.into();

    for media_ref in refs {
        if REMOTE_FILENAME.is_match(media_ref.fname) {
            // skip remote references
            continue;
        }

        // normalize fname into NFC
        let mut fname = normalize_to_nfc(&media_ref.fname_decoded);
        // and look it up to see if it's been renamed
        if let Some(new_name) = renamed.get(fname.as_ref()) {
            fname = new_name.to_owned().into();
        }
        // if the filename was in NFC and was not renamed as part of the
        // media check, it may have already been renamed during a previous
        // sync. If that's the case and the renamed version exists on disk,
        // we'll need to update the field to match it. It may be possible
        // to remove this check in the future once we can be sure all media
        // files stored on AnkiWeb are in normalized form.
        if matches!(fname, Cow::Borrowed(_)) {
            if let Cow::Owned(normname) = normalize_nfc_filename(fname.as_ref().into()) {
                let path = media_folder.join(&normname);
                if path.exists() {
                    fname = normname.into();
                }
            }
        }
        // update the field if the filename was modified
        if let Cow::Owned(ref new_name) = fname {
            field = rename_media_ref_in_field(field.as_ref(), &media_ref, new_name).into();
        }
        // and mark this filename as having been referenced
        tracker(fname.into_owned());
    }

    field
}

fn rename_media_ref_in_field(field: &str, media_ref: &MediaRef, new_name: &str) -> String {
    let new_name = if matches!(media_ref.fname_decoded, Cow::Owned(_)) {
        // filename had quoted characters like &amp; - need to re-encode
        htmlescape::encode_minimal(new_name)
    } else {
        new_name.into()
    };
    let updated_tag = media_ref.full_ref.replace(media_ref.fname, &new_name);
    field.replace(media_ref.full_ref, &updated_tag)
}

struct UnusedAndMissingFiles {
    unused: Vec<String>,
    missing: Vec<String>,
    missing_media_notes: Vec<NoteId>,
}

impl UnusedAndMissingFiles {
    fn new(files: Vec<String>, mut references: HashMap<String, Vec<NoteId>>) -> Self {
        let mut unused = vec![];
        for file in files {
            if !file.starts_with('_') && !references.contains_key(&file) {
                unused.push(file);
            } else {
                references.remove(&file);
            }
        }

        let mut missing = Vec::new();
        let mut notes = HashSet::new();
        for (fname, nids) in references {
            missing.push(fname);
            notes.extend(nids);
        }

        Self {
            unused,
            missing,
            missing_media_notes: notes.into_iter().collect(),
        }
    }
}

fn extract_latex_refs(note: &Note, mut tracker: impl FnMut(String), svg: bool) {
    for field in note.fields() {
        let (_, extracted) = extract_latex_expanding_clozes(field, svg);
        for e in extracted {
            tracker(e.fname);
        }
    }
}

#[cfg(test)]
pub(crate) mod test {
    pub(crate) const MEDIACHECK_ANKI2: &[u8] =
        include_bytes!("../../tests/support/mediacheck.anki2");

    use std::collections::HashMap;

    use tempfile::tempdir;
    use tempfile::TempDir;

    use super::*;
    use crate::collection::CollectionBuilder;
    use crate::io::create_dir;
    use crate::io::write_file;

    fn common_setup() -> Result<(TempDir, MediaManager, Collection)> {
        let dir = tempdir()?;
        let media_folder = dir.path().join("media");
        create_dir(&media_folder)?;
        let media_db = dir.path().join("media.db");
        let col_path = dir.path().join("col.anki2");
        write_file(&col_path, MEDIACHECK_ANKI2)?;

        let mgr = MediaManager::new(&media_folder, media_db.clone())?;
        let col = CollectionBuilder::new(col_path)
            .set_media_paths(media_folder, media_db)
            .build()?;

        Ok((dir, mgr, col))
    }

    #[test]
    fn media_check() -> Result<()> {
        let (_dir, mgr, mut col) = common_setup()?;

        // add some test files
        write_file(mgr.media_folder.join("zerobytes"), "")?;
        create_dir(mgr.media_folder.join("folder"))?;
        write_file(mgr.media_folder.join("normal.jpg"), "normal")?;
        write_file(mgr.media_folder.join("foo[.jpg"), "foo")?;
        write_file(mgr.media_folder.join("_under.jpg"), "foo")?;
        write_file(mgr.media_folder.join("unused.jpg"), "foo")?;

        let progress = |_n| true;

        let (output, report) = {
            let mut checker = MediaChecker::new(&mut col, &mgr, progress);
            let output = checker.check()?;
            let summary = checker.summarize_output(&mut output.clone());
            (output, summary)
        };

        assert_eq!(
            output,
            MediaCheckOutput {
                unused: vec!["unused.jpg".into()],
                missing: vec!["ぱぱ.jpg".into()],
                missing_media_notes: vec![NoteId(1581236461568)],
                renamed: vec![("foo[.jpg".into(), "foo.jpg".into())]
                    .into_iter()
                    .collect(),
                dirs: vec!["folder".to_string()],
                oversize: vec![],
                trash_count: 0,
                trash_bytes: 0
            }
        );

        assert!(fs::metadata(mgr.media_folder.join("foo[.jpg")).is_err());
        assert!(fs::metadata(mgr.media_folder.join("foo.jpg")).is_ok());

        assert_eq!(
            report,
            "Missing files: 1
Unused files: 1
Renamed files: 1
Subfolders: 1

Some files have been renamed for compatibility:
Renamed: foo[.jpg -> foo.jpg

Folders inside the media folder are not supported.
Folder: folder

The following files are referenced by cards, but were not found in the media folder:
Missing: ぱぱ.jpg

The following files were found in the media folder, but do not appear to be used on any cards:
Unused: unused.jpg
"
        );

        Ok(())
    }

    fn files_in_dir(dir: &Path) -> Vec<String> {
        let mut files = fs::read_dir(dir)
            .unwrap()
            .map(|dentry| {
                let dentry = dentry.unwrap();
                Ok(dentry.file_name().to_string_lossy().to_string())
            })
            .collect::<io::Result<Vec<_>>>()
            .unwrap();
        files.sort();
        files
    }

    #[test]
    fn trash_handling() -> Result<()> {
        let (_dir, mgr, mut col) = common_setup()?;
        let trash_folder = trash_folder(&mgr.media_folder)?;
        write_file(trash_folder.join("test.jpg"), "test")?;

        let progress = |_n| true;

        let mut checker = MediaChecker::new(&mut col, &mgr, progress);
        checker.restore_trash()?;

        // file should have been moved to media folder
        assert_eq!(files_in_dir(&trash_folder), Vec::<String>::new());
        assert_eq!(
            files_in_dir(&mgr.media_folder),
            vec!["test.jpg".to_string()]
        );

        // if we repeat the process, restoring should do the same thing if the contents
        // are equal
        write_file(trash_folder.join("test.jpg"), "test")?;

        let mut checker = MediaChecker::new(&mut col, &mgr, progress);
        checker.restore_trash()?;

        assert_eq!(files_in_dir(&trash_folder), Vec::<String>::new());
        assert_eq!(
            files_in_dir(&mgr.media_folder),
            vec!["test.jpg".to_string()]
        );

        // but rename if required
        write_file(trash_folder.join("test.jpg"), "test2")?;

        let mut checker = MediaChecker::new(&mut col, &mgr, progress);
        checker.restore_trash()?;

        assert_eq!(files_in_dir(&trash_folder), Vec::<String>::new());
        assert_eq!(
            files_in_dir(&mgr.media_folder),
            vec![
                "test-109f4b3c50d7b0df729d299bc6f8e9ef9066971f.jpg".to_string(),
                "test.jpg".into()
            ]
        );

        Ok(())
    }

    #[test]
    fn unicode_normalization() -> Result<()> {
        let (_dir, mgr, mut col) = common_setup()?;

        write_file(mgr.media_folder.join("ぱぱ.jpg"), "nfd encoding")?;

        let progress = |_n| true;

        let mut output = {
            let mut checker = MediaChecker::new(&mut col, &mgr, progress);
            checker.check()
        }?;

        output.missing.sort();

        if cfg!(target_vendor = "apple") {
            // on a Mac, the file should not have been renamed, but the returned name
            // should be in NFC format
            assert_eq!(
                output,
                MediaCheckOutput {
                    unused: vec![],
                    missing: vec!["foo[.jpg".into(), "normal.jpg".into()],
                    missing_media_notes: vec![NoteId(1581236386334)],
                    renamed: Default::default(),
                    dirs: vec![],
                    oversize: vec![],
                    trash_count: 0,
                    trash_bytes: 0
                }
            );
            assert!(fs::metadata(mgr.media_folder.join("ぱぱ.jpg")).is_ok());
        } else {
            // on other platforms, the file should have been renamed to NFC
            assert_eq!(
                output,
                MediaCheckOutput {
                    unused: vec![],
                    missing: vec!["foo[.jpg".into(), "normal.jpg".into()],
                    missing_media_notes: vec![NoteId(1581236386334)],
                    renamed: vec![("ぱぱ.jpg".into(), "ぱぱ.jpg".into())]
                        .into_iter()
                        .collect(),
                    dirs: vec![],
                    oversize: vec![],
                    trash_count: 0,
                    trash_bytes: 0
                }
            );
            assert!(fs::metadata(mgr.media_folder.join("ぱぱ.jpg")).is_err());
            assert!(fs::metadata(mgr.media_folder.join("ぱぱ.jpg")).is_ok());
        }

        Ok(())
    }

    fn normalize_and_maybe_rename_files_helper(field: &str) -> HashSet<String> {
        let mut seen = HashSet::new();
        normalize_and_maybe_rename_files(
            field,
            &HashMap::new(),
            |fname| {
                seen.insert(fname);
            },
            Path::new("/tmp"),
        );
        seen
    }

    #[test]
    fn html_encoding() {
        let mut field = "[sound:a &amp; b.mp3]";
        let seen = normalize_and_maybe_rename_files_helper(field);
        assert!(seen.contains("a & b.mp3"));

        field = r#"<img src="a&b.jpg">"#;
        let seen = normalize_and_maybe_rename_files_helper(field);
        assert!(seen.contains("a&b.jpg"));

        field = r#"<img src="a&amp;b.jpg">"#;
        let seen = normalize_and_maybe_rename_files_helper(field);
        assert!(seen.contains("a&b.jpg"));
    }
}
