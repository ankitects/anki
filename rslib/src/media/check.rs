// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::{AnkiError, Result};
use crate::media::col::{
    for_every_note, get_note_types, mark_collection_modified, open_or_create_collection_db,
    set_note, Note,
};
use crate::media::database::MediaDatabaseContext;
use crate::media::files::{
    data_for_file, filename_if_normalized, remove_files, trash_folder, MEDIA_SYNC_FILESIZE_LIMIT,
};
use crate::text::{normalize_to_nfc, MediaRef};
use crate::{media::MediaManager, text::extract_media_refs};
use coarsetime::Instant;
use log::debug;
use std::collections::{HashMap, HashSet};
use std::path::Path;
use std::{borrow::Cow, fs, time};

#[derive(Debug, PartialEq)]
pub struct MediaCheckOutput {
    unused: Vec<String>,
    missing: Vec<String>,
    renamed: HashMap<String, String>,
    dirs: Vec<String>,
    oversize: Vec<String>,
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
}

impl<P> MediaChecker<'_, P>
where
    P: FnMut(usize) -> bool,
{
    pub fn new<'a>(
        mgr: &'a MediaManager,
        col_path: &'a Path,
        progress_cb: P,
    ) -> MediaChecker<'a, P> {
        MediaChecker {
            mgr,
            col_path,
            progress_cb,
            checked: 0,
            progress_updated: Instant::now(),
        }
    }

    pub fn check(&mut self) -> Result<MediaCheckOutput> {
        self.expire_old_trash()?;

        let mut ctx = self.mgr.dbctx();

        let folder_check = self.check_media_folder(&mut ctx)?;
        let referenced_files = self.check_media_references(&folder_check.renamed)?;
        let (unused, missing) = find_unused_and_missing(folder_check.files, referenced_files);

        Ok(MediaCheckOutput {
            unused,
            missing,
            renamed: folder_check.renamed,
            dirs: folder_check.dirs,
            oversize: folder_check.oversize,
        })
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
            let (norm_name, renamed) = self.normalize_and_maybe_rename(ctx, &disk_fname)?;
            if renamed {
                let orig_as_nfc = normalize_to_nfc(&disk_fname);
                // if the only difference is the unicode normalization,
                // we don't mark the file as a renamed file
                if orig_as_nfc.as_ref() != norm_name.as_ref() {
                    out.renamed
                        .insert(orig_as_nfc.to_string(), norm_name.to_string());
                }
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
        debug!("renamed {} to {}", disk_fname, fname);
        assert_ne!(fname.as_ref(), disk_fname);

        // move the originally named file to the trash
        remove_files(&self.mgr.media_folder, &[disk_fname])?;

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
        if now.duration_since(self.progress_updated).as_secs() < 1 {
            return Ok(());
        }
        self.progress_updated = now;
        self.fire_progress_cb()
    }

    fn expire_old_trash(&mut self) -> Result<()> {
        let trash = trash_folder(&self.mgr.media_folder)?;
        let now = time::SystemTime::now();

        for dentry in trash.read_dir()? {
            let dentry = dentry?;

            self.checked += 1;
            if self.checked % 10 == 0 {
                self.maybe_fire_progress_cb()?;
            }

            let meta = dentry.metadata()?;
            let elap_secs = now
                .duration_since(meta.modified()?)
                .map(|d| d.as_secs())
                .unwrap_or(0);
            if elap_secs >= 7 * 86_400 {
                debug!(
                    "removing {:?} from trash, as 7 days have elapsed",
                    dentry.path()
                );
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
            if fix_and_extract_media_refs(note, &mut referenced_files, renamed)? {
                // note was modified, needs saving
                set_note(
                    &trx,
                    note,
                    note_types
                        .get(&note.mid)
                        .ok_or_else(|| AnkiError::DBError {
                            info: "missing note type".to_string(),
                        })?,
                )?;
                collection_modified = true;
            }

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
        if !references.contains(&file) {
            unused.push(file);
        } else {
            references.remove(&file);
        }
    }

    (unused, references.into_iter().collect())
}

#[cfg(test)]
mod test {
    use crate::err::Result;
    use crate::media::check::{MediaCheckOutput, MediaChecker};
    use crate::media::MediaManager;
    use std::fs;
    use std::path::PathBuf;
    use tempfile::{tempdir, TempDir};

    fn common_setup() -> Result<(TempDir, MediaManager, PathBuf)> {
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

        Ok((dir, mgr, col_path))
    }

    #[test]
    fn test_media_check() -> Result<()> {
        let (_dir, mgr, col_path) = common_setup()?;

        // add some test files
        fs::write(&mgr.media_folder.join("zerobytes"), "")?;
        fs::create_dir(&mgr.media_folder.join("folder"))?;
        fs::write(&mgr.media_folder.join("normal.jpg"), "normal")?;
        fs::write(&mgr.media_folder.join("foo[.jpg"), "foo")?;

        let progress = |_n| true;
        let mut checker = MediaChecker::new(&mgr, &col_path, progress);
        let output = checker.check()?;

        assert_eq!(
            output,
            MediaCheckOutput {
                unused: vec![],
                missing: vec!["ぱぱ.jpg".into()],
                renamed: vec![("foo[.jpg".into(), "foo.jpg".into())]
                    .into_iter()
                    .collect(),
                dirs: vec!["folder".to_string()],
                oversize: vec![]
            }
        );

        assert!(fs::metadata(&mgr.media_folder.join("foo[.jpg")).is_err());
        assert!(fs::metadata(&mgr.media_folder.join("foo.jpg")).is_ok());

        Ok(())
    }

    #[test]
    fn test_unicode_normalization() -> Result<()> {
        let (_dir, mgr, col_path) = common_setup()?;

        fs::write(&mgr.media_folder.join("ぱぱ.jpg"), "nfd encoding")?;

        let progress = |_n| true;
        let mut checker = MediaChecker::new(&mgr, &col_path, progress);
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
                    oversize: vec![]
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
                    oversize: vec![]
                }
            );
            assert!(fs::metadata(&mgr.media_folder.join("ぱぱ.jpg")).is_err());
            assert!(fs::metadata(&mgr.media_folder.join("ぱぱ.jpg")).is_ok());
        }

        Ok(())
    }
}
