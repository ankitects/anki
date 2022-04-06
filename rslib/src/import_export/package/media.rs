// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    borrow::Cow,
    collections::HashMap,
    fs::{self, File},
    io,
    path::{Path, PathBuf},
};

use prost::Message;
use zip::{read::ZipFile, ZipArchive};
use zstd::stream::copy_decode;

use super::{MediaEntries, MediaEntry, Meta};
use crate::{
    error::ImportError, io::filename_is_safe, media::files::normalize_filename, prelude::*,
};

/// Like [MediaEntry], but with a safe filename and set zip filename.
pub(super) struct SafeMediaEntry {
    pub(super) name: String,
    pub(super) size: u32,
    #[allow(dead_code)]
    pub(super) sha1: [u8; 20],
    pub(super) index: usize,
}

impl MediaEntry {
    pub(super) fn new(
        name: impl Into<String>,
        size: impl TryInto<u32>,
        sha1: impl Into<Vec<u8>>,
    ) -> Self {
        MediaEntry {
            name: name.into(),
            size: size.try_into().unwrap_or_default(),
            sha1: sha1.into(),
            legacy_zip_filename: None,
        }
    }
}

impl SafeMediaEntry {
    pub(super) fn from_entry(enumerated: (usize, MediaEntry)) -> Result<Self> {
        let (index, entry) = enumerated;
        if let Ok(sha1) = entry.sha1.try_into() {
            if !matches!(safe_normalized_file_name(&entry.name)?, Cow::Owned(_)) {
                return Ok(Self {
                    name: entry.name,
                    size: entry.size,
                    sha1,
                    index,
                });
            }
        }
        Err(AnkiError::ImportError(ImportError::Corrupt))
    }

    pub(super) fn from_legacy(legacy_entry: (&str, String)) -> Result<Self> {
        let zip_filename: usize = legacy_entry.0.parse()?;
        let name = match safe_normalized_file_name(&legacy_entry.1)? {
            Cow::Owned(new_name) => new_name,
            Cow::Borrowed(_) => legacy_entry.1,
        };
        Ok(Self {
            name,
            size: 0,
            sha1: [0; 20],
            index: zip_filename,
        })
    }

    pub(super) fn file_path(&self, media_folder: &Path) -> PathBuf {
        media_folder.join(&self.name)
    }

    pub(super) fn fetch_file<'a>(&self, archive: &'a mut ZipArchive<File>) -> Result<ZipFile<'a>> {
        archive
            .by_name(&self.index.to_string())
            .map_err(|_| AnkiError::invalid_input(&format!("{} missing from archive", self.index)))
    }

    pub(super) fn is_equal_to(
        &self,
        meta: &Meta,
        self_zipped: &ZipFile,
        other_path: &Path,
    ) -> bool {
        // TODO: check hashs (https://github.com/ankitects/anki/pull/1723#discussion_r829653147)
        let self_size = if meta.media_list_is_hashmap() {
            self_zipped.size()
        } else {
            self.size as u64
        };
        fs::metadata(other_path)
            .map(|metadata| metadata.len() as u64 == self_size)
            .unwrap_or_default()
    }
}

pub(super) fn extract_media_entries(
    meta: &Meta,
    archive: &mut ZipArchive<File>,
) -> Result<Vec<SafeMediaEntry>> {
    let media_list_data = get_media_list_data(archive, meta)?;
    if meta.media_list_is_hashmap() {
        let map: HashMap<&str, String> = serde_json::from_slice(&media_list_data)?;
        map.into_iter().map(SafeMediaEntry::from_legacy).collect()
    } else {
        MediaEntries::decode_safe_entries(&media_list_data)
    }
}

fn safe_normalized_file_name(name: &str) -> Result<Cow<str>> {
    if !filename_is_safe(name) {
        Err(AnkiError::ImportError(ImportError::Corrupt))
    } else {
        Ok(normalize_filename(name))
    }
}

fn get_media_list_data(archive: &mut ZipArchive<File>, meta: &Meta) -> Result<Vec<u8>> {
    let mut file = archive.by_name("media")?;
    let mut buf = Vec::new();
    if meta.zstd_compressed() {
        copy_decode(file, &mut buf)?;
    } else {
        io::copy(&mut file, &mut buf)?;
    }
    Ok(buf)
}

impl MediaEntries {
    fn decode_safe_entries(buf: &[u8]) -> Result<Vec<SafeMediaEntry>> {
        let entries: Self = Message::decode(buf)?;
        entries
            .entries
            .into_iter()
            .enumerate()
            .map(SafeMediaEntry::from_entry)
            .collect()
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn normalization() {
        // legacy entries get normalized on deserialisation
        let entry = SafeMediaEntry::from_legacy(("1", "con".to_owned())).unwrap();
        assert_eq!(entry.name, "con_");

        // new-style entries should have been normalized on export
        let mut entries = Vec::new();
        MediaEntries {
            entries: vec![MediaEntry::new("con", 0, Vec::new())],
        }
        .encode(&mut entries)
        .unwrap();
        assert!(MediaEntries::decode_safe_entries(&entries).is_err());
    }
}
