// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs;
use std::io::ErrorKind;
use std::path::Path;

use anki_io::write_file;
use anki_io::FileIoError;
use anki_io::FileIoSnafu;
use anki_io::FileOp;
use snafu::ResultExt;
use tracing::info;

use crate::error;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::http_server::media_manager::ServerMediaManager;
use crate::sync::media::database::server::entry::upload::UploadedChangeResult;
use crate::sync::media::upload::MediaUploadResponse;
use crate::sync::media::zip::unzip_and_validate_files;

impl ServerMediaManager {
    pub fn process_uploaded_changes(
        &mut self,
        zip_data: Vec<u8>,
    ) -> HttpResult<MediaUploadResponse> {
        let extracted = unzip_and_validate_files(&zip_data).or_bad_request("unzip files")?;
        let folder = &self.media_folder;
        let mut processed = 0;
        let new_usn = self
            .db
            .with_transaction(|db, meta| {
                for change in extracted {
                    match db.register_uploaded_change(meta, change)? {
                        UploadedChangeResult::FileAlreadyDeleted { filename } => {
                            info!(filename, "already deleted");
                        }
                        UploadedChangeResult::FileIdentical { filename, sha1 } => {
                            info!(filename, sha1 = hex::encode(sha1), "already have");
                        }
                        UploadedChangeResult::Added {
                            filename,
                            data,
                            sha1,
                        } => {
                            info!(filename, sha1 = hex::encode(sha1), "added");
                            add_or_replace_file(&folder.join(filename), data)?;
                        }
                        UploadedChangeResult::Replaced {
                            filename,
                            data,
                            old_sha1,
                            new_sha1,
                        } => {
                            info!(
                                filename,
                                old_sha1 = hex::encode(old_sha1),
                                new_sha1 = hex::encode(new_sha1),
                                "replaced"
                            );
                            add_or_replace_file(&folder.join(filename), data)?;
                        }
                        UploadedChangeResult::Removed { filename, sha1 } => {
                            info!(filename, sha1 = hex::encode(sha1), "removed");
                            remove_file(&folder.join(filename))?;
                        }
                    }
                    processed += 1;
                }
                Ok(())
            })
            .or_internal_err("handle uploaded change")?;
        Ok(MediaUploadResponse {
            processed,
            current_usn: new_usn,
        })
    }
}

fn add_or_replace_file(path: &Path, data: Vec<u8>) -> error::Result<(), FileIoError> {
    write_file(path, data)
}

fn remove_file(path: &Path) -> error::Result<(), FileIoError> {
    if let Err(err) = fs::remove_file(path) {
        // if transaction was previously aborted, the file may have already been deleted
        if err.kind() != ErrorKind::NotFound {
            return Err(err).context(FileIoSnafu {
                path,
                op: FileOp::Remove,
            });
        }
    }
    Ok(())
}
