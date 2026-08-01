// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::error;
use crate::sync::media::database::server::meta::StoreMetadata;
use crate::sync::media::database::server::ServerMediaDatabase;
use crate::sync::media::zip::UploadedChange;
use crate::sync::media::zip::UploadedChangeKind;

pub enum UploadedChangeResult {
    FileAlreadyDeleted {
        filename: String,
    },
    FileIdentical {
        filename: String,
        sha1: Vec<u8>,
    },
    Added {
        filename: String,
        data: Vec<u8>,
        sha1: Vec<u8>,
    },
    Removed {
        filename: String,
        sha1: Vec<u8>,
    },
    Replaced {
        filename: String,
        data: Vec<u8>,
        old_sha1: Vec<u8>,
        new_sha1: Vec<u8>,
    },
}

impl ServerMediaDatabase {
    /// Add/modify/remove a single file.
    pub fn register_uploaded_change(
        &mut self,
        meta: &mut StoreMetadata,
        update: UploadedChange,
    ) -> error::Result<UploadedChangeResult> {
        let existing_file = self.get_nonempty_entry(&update.nfc_filename)?;
        match (existing_file, update.kind) {
            // deletion
            (None, UploadedChangeKind::Delete) => Ok(UploadedChangeResult::FileAlreadyDeleted {
                filename: update.nfc_filename,
            }),
            (Some(mut existing_nonempty), UploadedChangeKind::Delete) => {
                self.remove_entry(meta, &mut existing_nonempty)?;
                Ok(UploadedChangeResult::Removed {
                    filename: existing_nonempty.nfc_filename,
                    sha1: existing_nonempty.sha1,
                })
            }
            // addition
            (
                None,
                UploadedChangeKind::AddOrReplace {
                    nonempty_data,
                    sha1,
                },
            ) => {
                let entry = self.add_entry(meta, update.nfc_filename, nonempty_data.len(), sha1)?;
                Ok(UploadedChangeResult::Added {
                    filename: entry.nfc_filename,
                    data: nonempty_data,
                    sha1: entry.sha1,
                })
            }
            // replacement
            (
                Some(mut existing_nonempty),
                UploadedChangeKind::AddOrReplace {
                    nonempty_data,
                    sha1,
                },
            ) => {
                if existing_nonempty.sha1 == sha1 {
                    Ok(UploadedChangeResult::FileIdentical {
                        filename: existing_nonempty.nfc_filename,
                        sha1,
                    })
                } else {
                    let old_sha1 = self.replace_entry(
                        meta,
                        &mut existing_nonempty,
                        nonempty_data.len(),
                        sha1,
                    )?;
                    Ok(UploadedChangeResult::Replaced {
                        filename: existing_nonempty.nfc_filename,
                        data: nonempty_data,
                        old_sha1,
                        new_sha1: existing_nonempty.sha1,
                    })
                }
            }
        }
    }
}
