// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::io;
use std::io::Read;
use std::io::Write;

use serde::Deserialize;
use serde_tuple::Serialize_tuple;
use unicode_normalization::is_nfc;
use zip::write::FileOptions;
use zip::ZipWriter;

use crate::media::files::sha1_of_data;
use crate::prelude::*;
use crate::sync::media::MAX_INDIVIDUAL_MEDIA_FILE_SIZE;
use crate::sync::media::MAX_MEDIA_FILENAME_LENGTH_SERVER;

pub struct ZipFileMetadata {
    pub filename: String,
    pub total_bytes: u32,
    pub sha1: String,
}

/// Write provided `[(filename, data)]` into a zip file, returning its data.
/// The metadata is in a different format to the upload case, since deletions
/// don't need to be represented.
pub fn zip_files_for_download(files: Vec<(String, Vec<u8>)>) -> Result<Vec<u8>> {
    let options: FileOptions<'_, ()> =
        FileOptions::default().compression_method(zip::CompressionMethod::Stored);
    let mut zip = ZipWriter::new(io::Cursor::new(vec![]));
    let mut entries = HashMap::new();

    for (idx, (filename, data)) in files.into_iter().enumerate() {
        assert!(!data.is_empty());
        let idx_str = idx.to_string();
        entries.insert(idx_str.clone(), filename);
        zip.start_file(idx_str, options)?;
        zip.write_all(&data)?;
    }

    let meta = serde_json::to_vec(&entries)?;
    zip.start_file("_meta", options)?;
    zip.write_all(&meta)?;

    Ok(zip.finish()?.into_inner())
}

pub fn zip_files_for_upload(entries_: Vec<(String, Option<Vec<u8>>)>) -> Result<Vec<u8>> {
    let options: FileOptions<'_, ()> =
        FileOptions::default().compression_method(zip::CompressionMethod::Stored);
    let mut zip = ZipWriter::new(io::Cursor::new(vec![]));
    let mut entries = vec![];

    for (idx, (filename, data)) in entries_.into_iter().enumerate() {
        match data {
            None => {
                entries.push(UploadEntry {
                    actual_filename: filename,
                    filename_in_zip: None,
                });
            }
            Some(data) => {
                let idx_str = idx.to_string();
                zip.start_file(&idx_str, options)?;
                zip.write_all(&data)?;
                entries.push(UploadEntry {
                    actual_filename: filename,
                    filename_in_zip: Some(idx_str),
                });
            }
        }
    }

    let meta = serde_json::to_vec(&entries)?;
    zip.start_file("_meta", options)?;
    zip.write_all(&meta)?;

    Ok(zip.finish()?.into_inner())
}

pub struct UploadedChange {
    pub nfc_filename: String,
    pub kind: UploadedChangeKind,
}

pub enum UploadedChangeKind {
    AddOrReplace {
        nonempty_data: Vec<u8>,
        sha1: Vec<u8>,
    },
    Delete,
}

pub fn unzip_and_validate_files(zip_data: &[u8]) -> Result<Vec<UploadedChange>> {
    let mut zip = zip::ZipArchive::new(io::Cursor::new(zip_data))?;

    // meta map first, limited to a reasonable size
    let meta_file = zip.by_name("_meta")?;
    let entries: Vec<UploadEntry> = serde_json::from_reader(meta_file.take(50 * 1024))?;
    if entries.len() > 25 {
        invalid_input!("too many files in zip");
    }

    // extract files/deletions from zip
    entries
        .into_iter()
        .map(|entry| {
            if entry.actual_filename.len() > MAX_MEDIA_FILENAME_LENGTH_SERVER {
                invalid_input!("filename too long: {}", entry.actual_filename.len());
            }
            if !is_nfc(&entry.actual_filename) {
                invalid_input!("filename was not not in nfc: {}", entry.actual_filename);
            }
            if entry.actual_filename.contains(std::path::is_separator) {
                invalid_input!("filename contained separator: {}", entry.actual_filename);
            }
            let data = if let Some(filename_in_zip) = entry.filename_in_zip.as_ref() {
                if filename_in_zip.is_empty() {
                    // older clients/AnkiDroid use an empty string instead of null
                    UploadedChangeKind::Delete
                } else {
                    let file = zip.by_name(filename_in_zip)?;
                    if file.size() > MAX_INDIVIDUAL_MEDIA_FILE_SIZE as u64 {
                        invalid_input!("file too large");
                    }
                    let mut data = vec![];
                    // the .take() is because we don't trust the header to be correct
                    let bytes_read = file
                        .take(MAX_INDIVIDUAL_MEDIA_FILE_SIZE as u64)
                        .read_to_end(&mut data)?;
                    if bytes_read == 0 {
                        invalid_input!("file entry was zero bytes");
                    }
                    let sha1 = sha1_of_data(&data).to_vec();
                    UploadedChangeKind::AddOrReplace {
                        nonempty_data: data,
                        sha1,
                    }
                }
            } else {
                UploadedChangeKind::Delete
            };
            Ok(UploadedChange {
                nfc_filename: entry.actual_filename,
                kind: data,
            })
        })
        .collect()
}

#[derive(Serialize_tuple, Deserialize)]
struct UploadEntry {
    actual_filename: String,
    filename_in_zip: Option<String>,
}
