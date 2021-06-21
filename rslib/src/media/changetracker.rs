// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashMap, path::Path, time};

use crate::{
    error::{AnkiError, Result},
    log::{debug, Logger},
    media::{
        database::{MediaDatabaseContext, MediaEntry},
        files::{
            filename_if_normalized, mtime_as_i64, sha1_of_file, MEDIA_SYNC_FILESIZE_LIMIT,
            NONSYNCABLE_FILENAME,
        },
    },
};

struct FilesystemEntry {
    fname: String,
    sha1: Option<[u8; 20]>,
    mtime: i64,
    is_new: bool,
}

pub(super) struct ChangeTracker<'a, F>
where
    F: FnMut(usize) -> bool,
{
    media_folder: &'a Path,
    progress_cb: F,
    checked: usize,
    log: &'a Logger,
}

impl<F> ChangeTracker<'_, F>
where
    F: FnMut(usize) -> bool,
{
    pub(super) fn new<'a>(
        media_folder: &'a Path,
        progress: F,
        log: &'a Logger,
    ) -> ChangeTracker<'a, F> {
        ChangeTracker {
            media_folder,
            progress_cb: progress,
            checked: 0,
            log,
        }
    }

    fn fire_progress_cb(&mut self) -> Result<()> {
        if (self.progress_cb)(self.checked) {
            Ok(())
        } else {
            Err(AnkiError::Interrupted)
        }
    }

    pub(super) fn register_changes(&mut self, ctx: &mut MediaDatabaseContext) -> Result<()> {
        ctx.transact(|ctx| {
            // folder mtime unchanged?
            let dirmod = mtime_as_i64(self.media_folder)?;

            let mut meta = ctx.get_meta()?;
            debug!(self.log, "begin change check"; "folder_mod" => dirmod, "db_mod" => meta.folder_mtime);
            if dirmod == meta.folder_mtime {
                debug!(self.log, "skip check");
                return Ok(());
            } else {
                meta.folder_mtime = dirmod;
            }

            let mtimes = ctx.all_mtimes()?;
            self.checked += mtimes.len();
            self.fire_progress_cb()?;

            let (changed, removed) = self.media_folder_changes(mtimes)?;

            self.add_updated_entries(ctx, changed)?;
            self.remove_deleted_files(ctx, removed)?;

            ctx.set_meta(&meta)?;

            // unconditional fire at end of op for accurate counts
            self.fire_progress_cb()?;

            Ok(())
        })
    }

    /// Scan through the media folder, finding changes.
    /// Returns (added/changed files, removed files).
    fn media_folder_changes(
        &mut self,
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
            let disk_fname = match fname_os.to_str() {
                Some(s) => s,
                None => continue,
            };

            // make sure the filename is normalized
            let fname = match filename_if_normalized(disk_fname) {
                Some(fname) => fname,
                None => {
                    // not normalized; skip it
                    debug!(self.log, "ignore non-normalized"; "fname"=>disk_fname);
                    continue;
                }
            };

            // ignore blacklisted files
            if NONSYNCABLE_FILENAME.is_match(fname.as_ref()) {
                continue;
            }

            // ignore large files and zero byte files
            let metadata = dentry.metadata()?;
            if metadata.len() > MEDIA_SYNC_FILESIZE_LIMIT as u64 {
                continue;
            }
            if metadata.len() == 0 {
                continue;
            }

            // remove from mtimes for later deletion tracking
            let previous_mtime = mtimes.remove(fname.as_ref());

            // skip files that have not been modified
            let mtime = metadata
                .modified()?
                .duration_since(time::UNIX_EPOCH)
                .unwrap()
                .as_secs() as i64;
            if let Some(previous_mtime) = previous_mtime {
                if previous_mtime == mtime {
                    debug!(self.log, "mtime unchanged"; "fname"=>fname.as_ref());
                    continue;
                }
            }

            // add entry to the list
            let data = sha1_of_file(&dentry.path())
                .map_err(|e| AnkiError::IoError(format!("unable to read {}: {}", fname, e)))?;
            let sha1 = Some(data);
            added_or_changed.push(FilesystemEntry {
                fname: fname.to_string(),
                sha1,
                mtime,
                is_new: previous_mtime.is_none(),
            });
            debug!(self.log, "added or changed";
                "fname"=>fname.as_ref(),
                "mtime"=>mtime,
                "sha1"=>sha1.as_ref().map(|s| hex::encode(&s[0..4])));

            self.checked += 1;
            if self.checked % 10 == 0 {
                self.fire_progress_cb()?;
            }
        }

        // any remaining entries from the database have been deleted
        let removed: Vec<_> = mtimes.into_iter().map(|(k, _)| k).collect();
        for f in &removed {
            debug!(self.log, "db entry missing on disk"; "fname"=>f);
        }

        Ok((added_or_changed, removed))
    }

    /// Add added/updated entries to the media DB.
    ///
    /// Skip files where the mod time differed, but checksums are the same.
    fn add_updated_entries(
        &mut self,
        ctx: &mut MediaDatabaseContext,
        entries: Vec<FilesystemEntry>,
    ) -> Result<()> {
        for fentry in entries {
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
                fname: fentry.fname,
                sha1: fentry.sha1,
                mtime: fentry.mtime,
                sync_required,
            })?;

            self.checked += 1;
            if self.checked % 10 == 0 {
                self.fire_progress_cb()?;
            }
        }

        Ok(())
    }

    /// Remove deleted files from the media DB.
    fn remove_deleted_files(
        &mut self,
        ctx: &mut MediaDatabaseContext,
        removed: Vec<String>,
    ) -> Result<()> {
        for fname in removed {
            ctx.set_entry(&MediaEntry {
                fname,
                sha1: None,
                mtime: 0,
                sync_required: true,
            })?;

            self.checked += 1;
            if self.checked % 10 == 0 {
                self.fire_progress_cb()?;
            }
        }

        Ok(())
    }
}

#[cfg(test)]
mod test {
    use std::{fs, path::Path, time, time::Duration};

    use tempfile::tempdir;

    use crate::{
        error::Result,
        media::{
            changetracker::ChangeTracker, database::MediaEntry, files::sha1_of_data, MediaManager,
        },
    };

    // helper
    fn change_mtime(p: &Path) {
        let mtime = p.metadata().unwrap().modified().unwrap();
        let new_mtime = mtime - Duration::from_secs(3);
        let secs = new_mtime
            .duration_since(time::UNIX_EPOCH)
            .unwrap()
            .as_secs() as i64;
        utime::set_file_times(p, secs, secs).unwrap();
    }

    #[test]
    fn change_tracking() -> Result<()> {
        let dir = tempdir()?;
        let media_dir = dir.path().join("media");
        std::fs::create_dir(&media_dir)?;
        let media_db = dir.path().join("media.db");

        let mgr = MediaManager::new(&media_dir, media_db)?;
        let mut ctx = mgr.dbctx();

        assert_eq!(ctx.count()?, 0);

        // add a file and check it's picked up
        let f1 = media_dir.join("file.jpg");
        fs::write(&f1, "hello")?;

        change_mtime(&media_dir);

        let progress_cb = |_n| true;

        let log = crate::log::terminal();

        ChangeTracker::new(&mgr.media_folder, progress_cb, &log).register_changes(&mut ctx)?;

        let mut entry = ctx.transact(|ctx| {
            assert_eq!(ctx.count()?, 1);
            assert!(!ctx.get_pending_uploads(1)?.is_empty());
            let mut entry = ctx.get_entry("file.jpg")?.unwrap();
            assert_eq!(
                entry,
                MediaEntry {
                    fname: "file.jpg".into(),
                    sha1: Some(sha1_of_data(b"hello")),
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
            ctx.set_entry(&entry)?;
            assert!(ctx.get_pending_uploads(1)?.is_empty());

            // modify it
            fs::write(&f1, "hello1")?;
            change_mtime(&f1);

            change_mtime(&media_dir);

            Ok(entry)
        })?;

        ChangeTracker::new(&mgr.media_folder, progress_cb, &log).register_changes(&mut ctx)?;

        ctx.transact(|ctx| {
            assert_eq!(ctx.count()?, 1);
            assert!(!ctx.get_pending_uploads(1)?.is_empty());
            assert_eq!(
                ctx.get_entry("file.jpg")?.unwrap(),
                MediaEntry {
                    fname: "file.jpg".into(),
                    sha1: Some(sha1_of_data(b"hello1")),
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
            ctx.set_entry(&entry)?;
            assert!(ctx.get_pending_uploads(1)?.is_empty());

            Ok(())
        })?;

        // delete it
        fs::remove_file(&f1)?;

        change_mtime(&media_dir);

        ChangeTracker::new(&mgr.media_folder, progress_cb, &log).register_changes(&mut ctx)?;

        assert_eq!(ctx.count()?, 0);
        assert!(!ctx.get_pending_uploads(1)?.is_empty());
        assert_eq!(
            ctx.get_entry("file.jpg")?.unwrap(),
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
