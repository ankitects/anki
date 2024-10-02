// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod check;
pub mod files;
mod service;

use std::borrow::Cow;
use std::collections::HashMap;
use std::path::Path;
use std::path::PathBuf;

use anki_io::create_dir_all;
use reqwest::Client;

use crate::media::files::add_data_to_folder_uniquely;
use crate::media::files::mtime_as_i64;
use crate::media::files::remove_files;
use crate::media::files::sha1_of_data;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::login::SyncAuth;
use crate::sync::media::database::client::changetracker::ChangeTracker;
use crate::sync::media::database::client::MediaDatabase;
use crate::sync::media::database::client::MediaEntry;
use crate::sync::media::progress::MediaSyncProgress;
use crate::sync::media::syncer::MediaSyncer;

pub type Sha1Hash = [u8; 20];

impl Collection {
    pub fn media(&self) -> Result<MediaManager> {
        MediaManager::new(&self.media_folder, &self.media_db)
    }
}

pub struct MediaManager {
    pub(crate) db: MediaDatabase,
    pub(crate) media_folder: PathBuf,
}

impl MediaManager {
    pub fn new<P, P2>(media_folder: P, media_db: P2) -> Result<Self>
    where
        P: Into<PathBuf>,
        P2: AsRef<Path>,
    {
        let media_folder = media_folder.into();
        if media_folder.as_os_str().is_empty() {
            invalid_input!("attempted media operation without media folder set");
        }
        create_dir_all(&media_folder)?;
        Ok(MediaManager {
            db: MediaDatabase::new(media_db.as_ref())?,
            media_folder,
        })
    }

    /// Add a file to the media folder.
    ///
    /// If a file with differing contents already exists, a hash will be
    /// appended to the name.
    ///
    /// Also notes the file in the media database.
    pub fn add_file<'a>(&self, desired_name: &'a str, data: &[u8]) -> Result<Cow<'a, str>> {
        let data_hash = sha1_of_data(data);

        self.transact(|db| {
            let chosen_fname =
                add_data_to_folder_uniquely(&self.media_folder, desired_name, data, data_hash)?;
            let file_mtime = mtime_as_i64(self.media_folder.join(chosen_fname.as_ref()))?;

            let existing_entry = db.get_entry(&chosen_fname)?;
            let new_sha1 = Some(data_hash);

            let entry_update_required = existing_entry.map(|e| e.sha1 != new_sha1).unwrap_or(true);

            if entry_update_required {
                db.set_entry(&MediaEntry {
                    fname: chosen_fname.to_string(),
                    sha1: new_sha1,
                    mtime: file_mtime,
                    sync_required: true,
                })?;
            }

            Ok(chosen_fname)
        })
    }

    pub fn remove_files<S>(&self, filenames: &[S]) -> Result<()>
    where
        S: AsRef<str> + std::fmt::Debug,
    {
        self.transact(|db| {
            remove_files(&self.media_folder, filenames)?;
            for fname in filenames {
                if let Some(mut entry) = db.get_entry(fname.as_ref())? {
                    entry.sha1 = None;
                    entry.mtime = 0;
                    entry.sync_required = true;
                    db.set_entry(&entry)?;
                }
            }
            Ok(())
        })
    }

    /// Opens a transaction and manages folder mtime, so user should perform not
    /// only db ops, but also all file ops inside the closure.
    pub(crate) fn transact<T>(&self, func: impl FnOnce(&MediaDatabase) -> Result<T>) -> Result<T> {
        let start_folder_mtime = mtime_as_i64(&self.media_folder)?;
        self.db.transact(|db| {
            let out = func(db)?;

            let mut meta = db.get_meta()?;
            if meta.folder_mtime == start_folder_mtime {
                // if media db was in sync with folder prior to this add,
                // we can keep it in sync
                meta.folder_mtime = mtime_as_i64(&self.media_folder)?;
                db.set_meta(&meta)?;
            } else {
                // otherwise, leave it alone so that other pending changes
                // get picked up later
            }

            Ok(out)
        })
    }

    /// Set entry for a newly added file. Caller must ensure transaction.
    pub(crate) fn add_entry(&self, fname: impl Into<String>, sha1: [u8; 20]) -> Result<()> {
        let fname = fname.into();
        let mtime = mtime_as_i64(self.media_folder.join(&fname))?;
        self.db.set_entry(&MediaEntry {
            fname,
            mtime,
            sha1: Some(sha1),
            sync_required: true,
        })
    }

    /// Sync media.
    pub async fn sync_media(
        self,
        progress: ThrottlingProgressHandler<MediaSyncProgress>,
        auth: SyncAuth,
        client: Client,
        server_usn: Option<Usn>,
    ) -> Result<()> {
        let client = HttpSyncClient::new(auth, client);
        let mut syncer = MediaSyncer::new(self, progress, client)?;
        syncer.sync(server_usn).await
    }

    pub fn all_checksums_after_checking(
        &self,
        progress: impl FnMut(usize) -> bool,
    ) -> Result<HashMap<String, Sha1Hash>> {
        ChangeTracker::new(&self.media_folder, progress).register_changes(&self.db)?;
        self.db.all_registered_checksums()
    }

    pub fn checksum_getter(&self) -> impl FnMut(&str) -> Result<Option<Sha1Hash>> + '_ {
        |fname: &str| {
            self.db
                .get_entry(fname)
                .map(|opt| opt.and_then(|entry| entry.sha1))
        }
    }

    pub fn register_changes(&self, progress: &mut impl FnMut(usize) -> bool) -> Result<()> {
        ChangeTracker::new(&self.media_folder, progress).register_changes(&self.db)
    }

    /// All checksums without registering changes first.
    #[cfg(test)]
    pub(crate) fn all_checksums_as_is(&self) -> HashMap<String, [u8; 20]> {
        self.db.all_registered_checksums().unwrap()
    }
}
