// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::Result;
use crate::media::database::{open_or_create, MediaDatabaseContext, MediaEntry};
use crate::media::files::{
    add_data_to_folder_uniquely, mtime_as_i64, sha1_of_data, sha1_of_file,
    MEDIA_SYNC_FILESIZE_LIMIT, NONSYNCABLE_FILENAME,
};
use rusqlite::Connection;
use std::borrow::Cow;
use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::time;

pub mod database;
pub mod files;
pub mod sync;

pub struct MediaManager {
    db: Connection,
    media_folder: PathBuf,
}

struct FilesystemEntry {
    fname: String,
    sha1: Option<[u8; 20]>,
    mtime: i64,
    is_new: bool,
}

impl MediaManager {
    pub fn new<P, P2>(media_folder: P, media_db: P2) -> Result<Self>
    where
        P: Into<PathBuf>,
        P2: AsRef<Path>,
    {
        let db = open_or_create(media_db.as_ref())?;
        Ok(MediaManager {
            db,
            media_folder: media_folder.into(),
        })
    }

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
        self.dbctx().transact(|ctx| {
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

    fn dbctx(&self) -> MediaDatabaseContext {
        MediaDatabaseContext::new(&self.db)
    }

    // db helpers

    //    pub(super) fn query<F, R>(&self, func: F) -> Result<R>
    //    where
    //        F: FnOnce(&mut MediaDatabaseContext) -> Result<R>,
    //    {
    //        MediaDatabaseContext::query(&self.db, func)
    //    }

    //    pub(super) fn transact<F, R>(&self, func: F) -> Result<R>
    //    where
    //        F: FnOnce(&mut MediaDatabaseContext) -> Result<R>,
    //    {
    //        MediaDatabaseContext::transact(&self.db, func)
    //    }
}

fn register_changes(ctx: &mut MediaDatabaseContext, folder: &Path) -> Result<()> {
    ctx.transact(|ctx| {
        // folder mtime unchanged?
        let dirmod = mtime_as_i64(folder)?;

        let mut meta = ctx.get_meta()?;
        if dirmod == meta.folder_mtime {
            return Ok(());
        } else {
            meta.folder_mtime = dirmod;
        }

        let mtimes = ctx.all_mtimes()?;

        let (changed, removed) = media_folder_changes(folder, mtimes)?;

        add_updated_entries(ctx, changed)?;
        remove_deleted_files(ctx, removed)?;

        ctx.set_meta(&meta)?;

        Ok(())
    })
}

/// Scan through the media folder, finding changes.
/// Returns (added/changed files, removed files).
///
/// Checks for invalid filenames and unicode normalization are deferred
/// until syncing time, as we can't trust the entries previous Anki versions
/// wrote are correct.
fn media_folder_changes(
    media_folder: &Path,
    mut mtimes: HashMap<String, i64>,
) -> Result<(Vec<FilesystemEntry>, Vec<String>)> {
    let mut added_or_changed = vec![];

    // loop through on-disk files
    for dentry in media_folder.read_dir()? {
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
fn add_updated_entries(
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
    }

    Ok(())
}

/// Remove deleted files from the media DB.
fn remove_deleted_files(ctx: &mut MediaDatabaseContext, removed: Vec<String>) -> Result<()> {
    for fname in removed {
        ctx.set_entry(&MediaEntry {
            fname,
            sha1: None,
            mtime: 0,
            sync_required: true,
        })?;
    }

    Ok(())
}

#[cfg(test)]
mod test {
    use crate::err::Result;
    use crate::media::database::MediaEntry;
    use crate::media::files::sha1_of_data;
    use crate::media::{register_changes, MediaManager};
    use std::path::Path;
    use std::time::Duration;
    use std::{fs, time};
    use tempfile::tempdir;

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

        let mgr = MediaManager::new(&media_dir, media_db)?;
        let mut ctx = mgr.dbctx();

        assert_eq!(ctx.count()?, 0);

        // add a file and check it's picked up
        let f1 = media_dir.join("file.jpg");
        fs::write(&f1, "hello")?;

        change_mtime(&media_dir);
        register_changes(&mut ctx, &mgr.media_folder)?;

        let mut entry = ctx.transact(|ctx| {
            assert_eq!(ctx.count()?, 1);
            assert!(!ctx.get_pending_uploads(1)?.is_empty());
            let mut entry = ctx.get_entry("file.jpg")?.unwrap();
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
            ctx.set_entry(&entry)?;
            assert!(ctx.get_pending_uploads(1)?.is_empty());

            // modify it
            fs::write(&f1, "hello1")?;
            change_mtime(&f1);

            change_mtime(&media_dir);

            Ok(entry)
        })?;

        register_changes(&mut ctx, &mgr.media_folder)?;

        ctx.transact(|ctx| {
            assert_eq!(ctx.count()?, 1);
            assert!(!ctx.get_pending_uploads(1)?.is_empty());
            assert_eq!(
                ctx.get_entry("file.jpg")?.unwrap(),
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
            ctx.set_entry(&entry)?;
            assert!(ctx.get_pending_uploads(1)?.is_empty());

            Ok(())
        })?;

        // delete it
        fs::remove_file(&f1)?;

        change_mtime(&media_dir);
        register_changes(&mut ctx, &mgr.media_folder)?;

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
