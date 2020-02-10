// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::Result;
use crate::media::database::{open_or_create, MediaDatabaseContext, MediaEntry};
use crate::media::files::{add_data_to_folder_uniquely, mtime_as_i64, sha1_of_data};
use crate::media::sync::{MediaSyncProgress, MediaSyncer};
use rusqlite::Connection;
use std::borrow::Cow;
use std::path::{Path, PathBuf};

pub mod changetracker;
pub mod check;
pub mod col;
pub mod database;
pub mod files;
pub mod sync;

pub struct MediaManager {
    db: Connection,
    media_folder: PathBuf,
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
    pub fn add_file<'a>(
        &self,
        ctx: &mut MediaDatabaseContext,
        desired_name: &'a str,
        data: &[u8],
    ) -> Result<Cow<'a, str>> {
        let pre_add_folder_mtime = mtime_as_i64(&self.media_folder)?;

        // add file to folder
        let data_hash = sha1_of_data(data);
        let chosen_fname =
            add_data_to_folder_uniquely(&self.media_folder, desired_name, data, data_hash)?;
        let file_mtime = mtime_as_i64(self.media_folder.join(chosen_fname.as_ref()))?;
        let post_add_folder_mtime = mtime_as_i64(&self.media_folder)?;

        // add to the media DB
        ctx.transact(|ctx| {
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

    /// Sync media.
    pub async fn sync_media<F>(&self, progress: F, endpoint: &str, hkey: &str) -> Result<()>
    where
        F: Fn(&MediaSyncProgress) -> bool,
    {
        let mut syncer = MediaSyncer::new(self, progress, endpoint);
        syncer.sync(hkey).await
    }

    pub fn dbctx(&self) -> MediaDatabaseContext {
        MediaDatabaseContext::new(&self.db)
    }
}
