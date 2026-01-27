// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::path::Path;

use serde::Deserialize;
use serde_tuple::Serialize_tuple;
use tracing::debug;

use crate::media::files::data_for_file;
use crate::media::files::normalize_filename;
use crate::prelude::*;
use crate::sync::media::database::client::MediaDatabase;
use crate::sync::media::database::client::MediaEntry;
use crate::sync::media::MAX_INDIVIDUAL_MEDIA_FILE_SIZE;
use crate::sync::media::MEDIA_SYNC_TARGET_ZIP_BYTES;

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct MediaUploadResponse {
    /// Always equal to number of uploaded files now. Old AnkiWeb versions used
    /// to terminate processing early if too much time had elapsed, so older
    /// clients will upload the same material again if this is less than the
    /// count they uploaded.
    pub processed: usize,
    pub current_usn: Usn,
}

/// Filename -> Some(Data), or None in the deleted case.
type ZipDataForUpload = Vec<(String, Option<Vec<u8>>)>;

/// Gather [(filename, data)] for provided entries, up to configured limit.
/// Data is None if file is deleted.
/// Returns None if one or more of the entries were inaccessible or in the wrong
/// format.
pub fn gather_zip_data_for_upload(
    ctx: &MediaDatabase,
    media_folder: &Path,
    files: &[MediaEntry],
) -> Result<Option<ZipDataForUpload>> {
    let mut invalid_entries = vec![];
    let mut accumulated_size = 0;
    let mut entries = vec![];

    for file in files {
        if accumulated_size > *MEDIA_SYNC_TARGET_ZIP_BYTES {
            break;
        }

        #[cfg(target_vendor = "apple")]
        {
            use unicode_normalization::is_nfc;
            if !is_nfc(&file.fname) {
                // older Anki versions stored non-normalized filenames in the DB; clean them up
                debug!(fname = file.fname, "clean up non-nfc entry");
                invalid_entries.push(&file.fname);
                continue;
            }
        }

        let file_data = if file.sha1.is_some() {
            match data_for_file(media_folder, &file.fname) {
                Ok(data) => data,
                Err(e) => {
                    debug!("error accessing {}: {}", &file.fname, e);
                    invalid_entries.push(&file.fname);
                    continue;
                }
            }
        } else {
            // uploading deletion
            None
        };

        if let Some(data) = file_data {
            let normalized = normalize_filename(&file.fname);
            if let Cow::Owned(o) = normalized {
                debug!("media check required: {} should be {}", &file.fname, o);
                invalid_entries.push(&file.fname);
                continue;
            }

            if data.is_empty() {
                invalid_entries.push(&file.fname);
                continue;
            }
            if data.len() > MAX_INDIVIDUAL_MEDIA_FILE_SIZE {
                invalid_entries.push(&file.fname);
                continue;
            }
            accumulated_size += data.len();
            entries.push((file.fname.clone(), Some(data)));
            debug!(file.fname, kind = "addition", "will upload");
        } else {
            entries.push((file.fname.clone(), None));
            debug!(file.fname, kind = "removal", "will upload");
        }
    }

    if !invalid_entries.is_empty() {
        // clean up invalid entries; we'll build a new zip
        ctx.transact(|ctx| {
            for fname in invalid_entries {
                ctx.remove_entry(fname)?;
            }
            Ok(())
        })?;
        return Ok(None);
    }

    Ok(Some(entries))
}
