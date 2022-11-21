// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    borrow::Cow,
    collections::HashMap,
    path::{Path, PathBuf},
};

use rusqlite::Connection;
use slog::Logger;

use self::changetracker::ChangeTracker;
use crate::{
    media::{
        database::{open_or_create, MediaDatabaseContext, MediaEntry},
        files::{add_data_to_folder_uniquely, mtime_as_i64, remove_files, sha1_of_data},
        sync::{MediaSyncProgress, MediaSyncer},
    },
    prelude::*,
};

pub mod changetracker;
pub mod check;
pub mod database;
pub mod files;
pub mod sync;

pub type Sha1Hash = [u8; 20];

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
        let data_hash = sha1_of_data(data);

        self.transact(ctx, |ctx| {
            let chosen_fname =
                add_data_to_folder_uniquely(&self.media_folder, desired_name, data, data_hash)?;
            let file_mtime = mtime_as_i64(self.media_folder.join(chosen_fname.as_ref()))?;

            let existing_entry = ctx.get_entry(&chosen_fname)?;
            let new_sha1 = Some(data_hash);

            let entry_update_required = existing_entry.map(|e| e.sha1 != new_sha1).unwrap_or(true);

            if entry_update_required {
                ctx.set_entry(&MediaEntry {
                    fname: chosen_fname.to_string(),
                    sha1: new_sha1,
                    mtime: file_mtime,
                    sync_required: true,
                })?;
            }

            Ok(chosen_fname)
        })
    }

    pub fn remove_files<S>(&self, ctx: &mut MediaDatabaseContext, filenames: &[S]) -> Result<()>
    where
        S: AsRef<str> + std::fmt::Debug,
    {
        self.transact(ctx, |ctx| {
            remove_files(&self.media_folder, filenames)?;
            for fname in filenames {
                if let Some(mut entry) = ctx.get_entry(fname.as_ref())? {
                    entry.sha1 = None;
                    entry.mtime = 0;
                    entry.sync_required = true;
                    ctx.set_entry(&entry)?;
                }
            }
            Ok(())
        })
    }

    /// Opens a transaction and manages folder mtime, so user should perform not
    /// only db ops, but also all file ops inside the closure.
    pub(crate) fn transact<T>(
        &self,
        ctx: &mut MediaDatabaseContext,
        func: impl FnOnce(&mut MediaDatabaseContext) -> Result<T>,
    ) -> Result<T> {
        let start_folder_mtime = mtime_as_i64(&self.media_folder)?;
        ctx.transact(|ctx| {
            let out = func(ctx)?;

            let mut meta = ctx.get_meta()?;
            if meta.folder_mtime == start_folder_mtime {
                // if media db was in sync with folder prior to this add,
                // we can keep it in sync
                meta.folder_mtime = mtime_as_i64(&self.media_folder)?;
                ctx.set_meta(&meta)?;
            } else {
                // otherwise, leave it alone so that other pending changes
                // get picked up later
            }

            Ok(out)
        })
    }

    /// Set entry for a newly added file. Caller must ensure transaction.
    pub(crate) fn add_entry(
        &self,
        ctx: &mut MediaDatabaseContext,
        fname: impl Into<String>,
        sha1: [u8; 20],
    ) -> Result<()> {
        let fname = fname.into();
        let mtime = mtime_as_i64(self.media_folder.join(&fname))?;
        ctx.set_entry(&MediaEntry {
            fname,
            mtime,
            sha1: Some(sha1),
            sync_required: true,
        })
    }

    /// Sync media.
    pub async fn sync_media<'a, F>(
        &'a self,
        progress: F,
        host_number: u32,
        hkey: &'a str,
        log: Logger,
    ) -> Result<()>
    where
        F: FnMut(MediaSyncProgress) -> bool,
    {
        let mut syncer = MediaSyncer::new(self, progress, host_number, log);
        syncer.sync(hkey).await
    }

    pub fn dbctx(&self) -> MediaDatabaseContext {
        MediaDatabaseContext::new(&self.db)
    }

    pub fn all_checksums(
        &self,
        progress: impl FnMut(usize) -> bool,
        log: &Logger,
    ) -> Result<HashMap<String, Sha1Hash>> {
        let mut dbctx = self.dbctx();
        ChangeTracker::new(&self.media_folder, progress, log).register_changes(&mut dbctx)?;
        dbctx.all_checksums()
    }

    pub fn checksum_getter(&self) -> impl FnMut(&str) -> Result<Option<Sha1Hash>> + '_ {
        let mut dbctx = self.dbctx();
        move |fname: &str| {
            dbctx
                .get_entry(fname)
                .map(|opt| opt.and_then(|entry| entry.sha1))
        }
    }

    pub fn register_changes(
        &self,
        progress: &mut impl FnMut(usize) -> bool,
        log: &Logger,
    ) -> Result<()> {
        ChangeTracker::new(&self.media_folder, progress, log).register_changes(&mut self.dbctx())
    }
}

#[cfg(test)]
mod test {
    use super::*;

    impl MediaManager {
        /// All checksums without registering changes first.
        pub(crate) fn all_checksums_as_is(&self) -> HashMap<String, [u8; 20]> {
            let mut dbctx = self.dbctx();
            dbctx.all_checksums().unwrap()
        }
    }
}
