// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::{AnkiError, Result};
use crate::i18n::{tr_args, tr_strs, FString, I18n};
use crate::latex::extract_latex_expanding_clozes;
use crate::log::{debug, Logger};
use crate::media::col::{
    for_every_note, get_note_types, mark_collection_modified, open_or_create_collection_db,
    set_note, Note,
};
use crate::media::database::MediaDatabaseContext;
use crate::media::files::{
    data_for_file, filename_if_normalized, trash_folder, MEDIA_SYNC_FILESIZE_LIMIT,
};
use crate::text::{normalize_to_nfc, MediaRef};
use crate::{media::MediaManager, text::extract_media_refs};
use coarsetime::Instant;
use lazy_static::lazy_static;
use regex::Regex;
use std::collections::{HashMap, HashSet};
use std::path::Path;
use std::{borrow::Cow, fs, io};

lazy_static! {
    static ref REMOTE_FILENAME: Regex = Regex::new("(?i)^https?://").unwrap();
}

#[derive(Debug, PartialEq)]
pub struct MediaCheckOutput {
    pub unused: Vec<String>,
    pub missing: Vec<String>,
    pub renamed: HashMap<String, String>,
    pub dirs: Vec<String>,
    pub oversize: Vec<String>,
    pub trash_count: u64,
    pub trash_bytes: u64,
}

#[derive(Debug, PartialEq, Default)]
struct MediaFolderCheck {
    files: Vec<String>,
    renamed: HashMap<String, String>,
    dirs: Vec<String>,
    oversize: Vec<String>,
}

pub struct MediaChecker<'a, P>
where
    P: FnMut(usize) -> bool,
{
    mgr: &'a MediaManager,
    col_path: &'a Path,
    progress_cb: P,
    checked: usize,
    progress_updated: Instant,
    i18n: &'a I18n,
    log: &'a Logger,
}

impl<P> MediaChecker<'_, P>
where
    P: FnMut(usize) -> bool,
{
    pub fn new<'a>(
        mgr: &'a MediaManager,
        col_path: &'a Path,
        progress_cb: P,
        i18n: &'a I18n,
        log: &'a Logger,
    ) -> MediaChecker<'a, P> {
        MediaChecker {
            mgr,
            col_path,
            progress_cb,
            checked: 0,
            progress_updated: Instant::now(),
            i18n,
            log,
        }
    }

    pub fn check(&mut self) -> Result<MediaCheckOutput> {
        let mut ctx = self.mgr.dbctx();

        let folder_check = self.check_media_folder(&mut ctx)?;
        let referenced_files = self.check_media_references(&folder_check.renamed)?;
        let (unused, missing) = find_unused_and_missing(folder_check.files, referenced_files);
        let (trash_count, trash_bytes) = self.files_in_trash()?;
        Ok(MediaCheckOutput {
            unused,
            missing,
            renamed: folder_check.renamed,
            dirs: folder_check.dirs,
            oversize: folder_check.oversize,
            trash_count,
            trash_bytes,
        })
    }

    pub fn summarize_output(&self, output: &mut MediaCheckOutput) -> String {
        let mut buf = String::new();
        let i = &self.i18n;

        // top summary area
        if output.trash_count > 0 {
            let megs = (output.trash_bytes as f32) / 1024.0 / 1024.0;
            buf += &i.trn(
                FString::MediaCheckTrashCount,
                tr_args!["count"=>output.trash_count, "megs"=>megs],
            );
            buf.push('\n');
        }

        buf += &i.trn(
            FString::MediaCheckMissingCount,
            tr_args!["count"=>output.missing.len()],
        );
        buf.push('\n');

        buf += &i.trn(
            FString::MediaCheckUnusedCount,
            tr_args!["count"=>output.unused.len()],
        );
        buf.push('\n');

        if !output.renamed.is_empty() {
            buf += &i.trn(
                FString::MediaCheckRenamedCount,
                tr_args!["count"=>output.renamed.len()],
            );
            buf.push('\n');
        }
        if !output.oversize.is_empty() {
            buf += &i.trn(
                FString::MediaCheckOversizeCount,
                tr_args!["count"=>output.oversize.len()],
            );
            buf.push('\n');
        }
        if !output.dirs.is_empty() {
            buf += &i.trn(
                FString::MediaCheckSubfolderCount,
                tr_args!["count"=>output.dirs.len()],
            );
            buf.push('\n');
        }

        buf.push('\n');

        if !output.renamed.is_empty() {
            buf += &i.tr(FString::MediaCheckRenamedHeader);
            buf.push('\n');
            for (old, new) in &output.renamed {
                buf += &i.trn(
                    FString::MediaCheckRenamedFile,
                    tr_strs!["old"=>old,"new"=>new],
                );
                buf.push('\n');
            }
            buf.push('\n')
        }

        if !output.oversize.is_empty() {
            output.oversize.sort();
            buf += &i.tr(FString::MediaCheckOversizeHeader);
            buf.push('\n');
            for fname in &output.oversize {
                buf += &i.trn(FString::MediaCheckOversizeFile, tr_strs!["filename"=>fname]);
                buf.push('\n');
            }
            buf.push('\n')
        }

        if !output.dirs.is_empty() {
            output.dirs.sort();
            buf += &i.tr(FString::MediaCheckSubfolderHeader);
            buf.push('\n');
            for fname in &output.dirs {
                buf += &i.trn(
                    FString::MediaCheckSubfolderFile,
                    tr_strs!["filename"=>fname],
                );
                buf.push('\n');
            }
            buf.push('\n')
        }

        if !output.missing.is_empty() {
            output.missing.sort();
            buf += &i.tr(FString::MediaCheckMissingHeader);
            buf.push('\n');
            for fname in &output.missing {
                buf += &i.trn(FString::MediaCheckMissingFile, tr_strs!["filename"=>fname]);
                buf.push('\n');
            }
            buf.push('\n')
        }

        if !output.unused.is_empty() {
            output.unused.sort();
            buf += &i.tr(FString::MediaCheckUnusedHeader);
            buf.push('\n');
            for fname in &output.unused {
                buf += &i.trn(FString::MediaCheckUnusedFile, tr_strs!["filename"=>fname]);
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
    fn check_media_folder(&mut self, ctx: &mut MediaDatabaseContext) -> Result<MediaFolderCheck> {
        let mut out = MediaFolderCheck::default();
        for dentry in self.mgr.media_folder.read_dir()? {
            let dentry = dentry?;

            self.checked += 1;
            if self.checked % 10 == 0 {
                self.maybe_fire_progress_cb()?;
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
            if metadata.len() > MEDIA_SYNC_FILESIZE_LIMIT as u64 {
                out.oversize.push(disk_fname.to_string());
                continue;
            }
            if metadata.len() == 0 {
                continue;
            }

            // rename if required
            let (norm_name, renamed_on_disk) = self.normalize_and_maybe_rename(ctx, &disk_fname)?;
            if renamed_on_disk {
                out.renamed
                    .insert(disk_fname.to_string(), norm_name.to_string());
            }

            out.files.push(norm_name.into_owned());
        }

        Ok(out)
    }

    /// Returns (normalized_form, needs_rename)
    fn normalize_and_maybe_rename<'a>(
        &mut self,
        ctx: &mut MediaDatabaseContext,
        disk_fname: &'a str,
    ) -> Result<(Cow<'a, str>, bool)> {
        // already normalized?
        if let Some(fname) = filename_if_normalized(disk_fname) {
            return Ok((fname, false));
        }

        // add a copy of the file using the correct name
        let data = data_for_file(&self.mgr.media_folder, disk_fname)?.ok_or_else(|| {
            AnkiError::IOError {
                info: "file disappeared".into(),
            }
        })?;
        let fname = self.mgr.add_file(ctx, disk_fname, &data)?;
        debug!(self.log, "renamed"; "from"=>disk_fname, "to"=>&fname.as_ref());
        assert_ne!(fname.as_ref(), disk_fname);

        // remove the original file
        let path = &self.mgr.media_folder.join(disk_fname);
        fs::remove_file(path)?;

        Ok((fname, true))
    }

    fn fire_progress_cb(&mut self) -> Result<()> {
        if (self.progress_cb)(self.checked) {
            Ok(())
        } else {
            Err(AnkiError::Interrupted)
        }
    }

    fn maybe_fire_progress_cb(&mut self) -> Result<()> {
        let now = Instant::now();
        if now.duration_since(self.progress_updated).as_f64() < 0.15 {
            return Ok(());
        }
        self.progress_updated = now;
        self.fire_progress_cb()
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
                self.maybe_fire_progress_cb()?;
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
                self.maybe_fire_progress_cb()?;
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
                self.maybe_fire_progress_cb()?;
            }

            let orig_path = self.mgr.media_folder.join(dentry.file_name());
            // if the original filename doesn't exist, we can just rename
            if let Err(e) = fs::metadata(&orig_path) {
                if e.kind() == io::ErrorKind::NotFound {
                    fs::rename(&dentry.path(), &orig_path)?;
                } else {
                    return Err(e.into());
                }
            } else {
                // ensure we don't overwrite different data
                let fname_os = dentry.file_name();
                let fname = fname_os.to_string_lossy();
                if let Some(data) = data_for_file(&trash, fname.as_ref())? {
                    let _new_fname =
                        self.mgr
                            .add_file(&mut self.mgr.dbctx(), fname.as_ref(), &data)?;
                } else {
                    debug!(self.log, "file disappeared while restoring trash"; "fname"=>fname.as_ref());
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
    ) -> Result<HashSet<String>> {
        let mut db = open_or_create_collection_db(self.col_path)?;
        let trx = db.transaction()?;

        let mut referenced_files = HashSet::new();
        let note_types = get_note_types(&trx)?;
        let mut collection_modified = false;

        for_every_note(&trx, |note| {
            self.checked += 1;
            if self.checked % 10 == 0 {
                self.maybe_fire_progress_cb()?;
            }
            let nt = note_types
                .get(&note.mid)
                .ok_or_else(|| AnkiError::DBError {
                    info: "missing note type".to_string(),
                })?;
            if fix_and_extract_media_refs(note, &mut referenced_files, renamed)? {
                // note was modified, needs saving
                set_note(&trx, note, nt)?;
                collection_modified = true;
            }

            // extract latex
            extract_latex_refs(note, &mut referenced_files, nt.latex_uses_svg());
            Ok(())
        })?;

        if collection_modified {
            mark_collection_modified(&trx)?;
            trx.commit()?;
        }

        Ok(referenced_files)
    }
}

/// Returns true if note was modified.
fn fix_and_extract_media_refs(
    note: &mut Note,
    seen_files: &mut HashSet<String>,
    renamed: &HashMap<String, String>,
) -> Result<bool> {
    let mut updated = false;

    for idx in 0..note.fields().len() {
        let field = normalize_and_maybe_rename_files(&note.fields()[idx], renamed, seen_files);
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
    seen_files: &mut HashSet<String>,
) -> Cow<'a, str> {
    let refs = extract_media_refs(field);
    let mut field: Cow<str> = field.into();

    for media_ref in refs {
        if REMOTE_FILENAME.is_match(media_ref.fname) {
            // skip remote references
            continue;
        }

        // normalize fname into NFC
        let mut fname = normalize_to_nfc(media_ref.fname);
        // and look it up to see if it's been renamed
        if let Some(new_name) = renamed.get(fname.as_ref()) {
            fname = new_name.to_owned().into();
        }
        // if it was not in NFC or was renamed, update the field
        if let Cow::Owned(ref new_name) = fname {
            field = rename_media_ref_in_field(field.as_ref(), &media_ref, new_name).into();
        }
        // and mark this filename as having been referenced
        seen_files.insert(fname.into_owned());
    }

    field
}

fn rename_media_ref_in_field(field: &str, media_ref: &MediaRef, new_name: &str) -> String {
    let updated_tag = media_ref.full_ref.replace(media_ref.fname, new_name);
    field.replace(media_ref.full_ref, &updated_tag)
}

/// Returns (unused, missing)
fn find_unused_and_missing(
    files: Vec<String>,
    mut references: HashSet<String>,
) -> (Vec<String>, Vec<String>) {
    let mut unused = vec![];

    for file in files {
        if !file.starts_with('_') && !references.contains(&file) {
            unused.push(file);
        } else {
            references.remove(&file);
        }
    }

    (unused, references.into_iter().collect())
}

fn extract_latex_refs(note: &Note, seen_files: &mut HashSet<String>, svg: bool) {
    for field in note.fields() {
        let (_, extracted) = extract_latex_expanding_clozes(field, svg);
        for e in extracted {
            seen_files.insert(e.fname);
        }
    }
}

#[cfg(test)]
mod test {
    use crate::err::Result;
    use crate::i18n::I18n;
    use crate::log;
    use crate::log::Logger;
    use crate::media::check::{MediaCheckOutput, MediaChecker};
    use crate::media::files::trash_folder;
    use crate::media::MediaManager;
    use std::path::{Path, PathBuf};
    use std::{fs, io};
    use tempfile::{tempdir, TempDir};

    fn common_setup() -> Result<(TempDir, MediaManager, PathBuf, Logger, I18n)> {
        let dir = tempdir()?;
        let media_dir = dir.path().join("media");
        fs::create_dir(&media_dir)?;
        let media_db = dir.path().join("media.db");
        let col_path = dir.path().join("col.anki2");
        fs::write(
            &col_path,
            &include_bytes!("../../tests/support/mediacheck.anki2")[..],
        )?;

        let mgr = MediaManager::new(&media_dir, media_db)?;

        let log = log::terminal();

        let i18n = I18n::new(&["zz"], "dummy", log.clone());

        Ok((dir, mgr, col_path, log, i18n))
    }

    #[test]
    fn media_check() -> Result<()> {
        let (_dir, mgr, col_path, log, i18n) = common_setup()?;

        // add some test files
        fs::write(&mgr.media_folder.join("zerobytes"), "")?;
        fs::create_dir(&mgr.media_folder.join("folder"))?;
        fs::write(&mgr.media_folder.join("normal.jpg"), "normal")?;
        fs::write(&mgr.media_folder.join("foo[.jpg"), "foo")?;
        fs::write(&mgr.media_folder.join("_under.jpg"), "foo")?;
        fs::write(&mgr.media_folder.join("unused.jpg"), "foo")?;

        let progress = |_n| true;
        let mut checker = MediaChecker::new(&mgr, &col_path, progress, &i18n, &log);
        let mut output = checker.check()?;

        assert_eq!(
            output,
            MediaCheckOutput {
                unused: vec!["unused.jpg".into()],
                missing: vec!["ぱぱ.jpg".into()],
                renamed: vec![("foo[.jpg".into(), "foo.jpg".into())]
                    .into_iter()
                    .collect(),
                dirs: vec!["folder".to_string()],
                oversize: vec![],
                trash_count: 0,
                trash_bytes: 0
            }
        );

        assert!(fs::metadata(&mgr.media_folder.join("foo[.jpg")).is_err());
        assert!(fs::metadata(&mgr.media_folder.join("foo.jpg")).is_ok());

        let report = checker.summarize_output(&mut output);
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
        let (_dir, mgr, col_path, log, i18n) = common_setup()?;
        let trash_folder = trash_folder(&mgr.media_folder)?;
        fs::write(trash_folder.join("test.jpg"), "test")?;

        let progress = |_n| true;
        let mut checker = MediaChecker::new(&mgr, &col_path, progress, &i18n, &log);

        checker.restore_trash()?;

        // file should have been moved to media folder
        assert_eq!(files_in_dir(&trash_folder), Vec::<String>::new());
        assert_eq!(
            files_in_dir(&mgr.media_folder),
            vec!["test.jpg".to_string()]
        );

        // if we repeat the process, restoring should do the same thing if the contents are equal
        fs::write(trash_folder.join("test.jpg"), "test")?;
        checker.restore_trash()?;
        assert_eq!(files_in_dir(&trash_folder), Vec::<String>::new());
        assert_eq!(
            files_in_dir(&mgr.media_folder),
            vec!["test.jpg".to_string()]
        );

        // but rename if required
        fs::write(trash_folder.join("test.jpg"), "test2")?;
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
        let (_dir, mgr, col_path, log, i18n) = common_setup()?;

        fs::write(&mgr.media_folder.join("ぱぱ.jpg"), "nfd encoding")?;

        let progress = |_n| true;
        let mut checker = MediaChecker::new(&mgr, &col_path, progress, &i18n, &log);
        let mut output = checker.check()?;
        output.missing.sort();

        if cfg!(target_vendor = "apple") {
            // on a Mac, the file should not have been renamed, but the returned name
            // should be in NFC format
            assert_eq!(
                output,
                MediaCheckOutput {
                    unused: vec![],
                    missing: vec!["foo[.jpg".into(), "normal.jpg".into()],
                    renamed: Default::default(),
                    dirs: vec![],
                    oversize: vec![],
                    trash_count: 0,
                    trash_bytes: 0
                }
            );
            assert!(fs::metadata(&mgr.media_folder.join("ぱぱ.jpg")).is_ok());
        } else {
            // on other platforms, the file should have been renamed to NFC
            assert_eq!(
                output,
                MediaCheckOutput {
                    unused: vec![],
                    missing: vec!["foo[.jpg".into(), "normal.jpg".into()],
                    renamed: vec![("ぱぱ.jpg".into(), "ぱぱ.jpg".into())]
                        .into_iter()
                        .collect(),
                    dirs: vec![],
                    oversize: vec![],
                    trash_count: 0,
                    trash_bytes: 0
                }
            );
            assert!(fs::metadata(&mgr.media_folder.join("ぱぱ.jpg")).is_err());
            assert!(fs::metadata(&mgr.media_folder.join("ぱぱ.jpg")).is_ok());
        }

        Ok(())
    }
}
