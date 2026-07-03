// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs;
use std::io::ErrorKind;

use anki_io::FileIoSnafu;
use anki_io::FileOp;
use snafu::ResultExt;

use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::http_server::media_manager::ServerMediaManager;
use crate::sync::media::database::server::entry::MediaEntry;
use crate::sync::media::zip::zip_files_for_download;

impl ServerMediaManager {
    pub fn zip_files_for_download(&mut self, files: Vec<String>) -> HttpResult<Vec<u8>> {
        let entries = self.db.get_entries_for_download(&files)?;
        let filenames_with_data = self.gather_file_data(&entries)?;
        zip_files_for_download(filenames_with_data).or_internal_err("zip files")
    }

    /// Mutable for the missing file case.
    fn gather_file_data(&mut self, entries: &[MediaEntry]) -> HttpResult<Vec<(String, Vec<u8>)>> {
        let mut out = vec![];
        for entry in entries {
            let path = self.media_folder.join(&entry.nfc_filename);
            match fs::read(&path) {
                Ok(data) => out.push((entry.nfc_filename.clone(), data)),
                Err(err) if err.kind() == ErrorKind::NotFound => {
                    self.db
                        .forget_missing_file(entry)
                        .or_internal_err("forget missing")?;
                    None.or_conflict(format!(
                        "requested a file that doesn't exist: {}",
                        entry.nfc_filename
                    ))?;
                }
                Err(err) => Err(err)
                    .context(FileIoSnafu {
                        path,
                        op: FileOp::Read,
                    })
                    .or_internal_err("gather file data")?,
            }
        }
        Ok(out)
    }
}
