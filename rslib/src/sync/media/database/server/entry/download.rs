// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use rusqlite::params;

use crate::error;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::media::database::server::entry::MediaEntry;
use crate::sync::media::database::server::ServerMediaDatabase;
use crate::sync::media::MAX_MEDIA_FILES_IN_ZIP;
use crate::sync::media::MEDIA_SYNC_TARGET_ZIP_BYTES;

impl ServerMediaDatabase {
    /// Return a list of entries in the same order as the provided files,
    /// truncating the list if the configured total bytes is exceeded.
    ///
    /// If any file entries were missing or deleted, we don't have any way in
    /// the current sync protocol to signal that they should be skipped, so
    /// we abort with a conflict.
    pub fn get_entries_for_download(&self, files: &[String]) -> HttpResult<Vec<MediaEntry>> {
        if files.len() > *MAX_MEDIA_FILES_IN_ZIP {
            None.or_bad_request("too many files requested")?;
        }

        let mut entries = vec![];
        let mut accumulated_size = 0;
        for filename in files {
            let Some(entry) = self
                .get_nonempty_entry(filename)
                .or_internal_err("fetching entry")?
            else {
                return None.or_conflict(format!("missing/empty file entry: {filename}"));
            };

            accumulated_size += entry.size;
            entries.push(entry);
            if accumulated_size > *MEDIA_SYNC_TARGET_ZIP_BYTES as u64 {
                break;
            }
        }
        Ok(entries)
    }

    /// Delete provided file from media DB, leaving no record of deletion. It
    /// was probably missing due to an interrupted deletion, but removing
    /// the entry errs on the side of caution, ensuring the deletion doesn't
    /// propagate to other clients.
    pub fn forget_missing_file(&mut self, entry: &MediaEntry) -> error::Result<()> {
        assert!(entry.size > 0);
        self.with_transaction(|db, meta| {
            meta.total_bytes = meta.total_bytes.saturating_sub(entry.size);
            meta.total_nonempty_files = meta.total_nonempty_files.saturating_sub(1);
            db.db
                .prepare_cached("delete from media where fname = ?")?
                .execute(params![&entry.nfc_filename,])?;
            Ok(())
        })?;
        Ok(())
    }
}
