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

    pub(super) fn from_legacy(legacy_entry: (&str, String)) -> Result<Self> {
        let idx: u32 = legacy_entry.0.parse()?;
        let name = match safe_normalized_file_name(&legacy_entry.1)? {
            Cow::Owned(new_name) => new_name,
            Cow::Borrowed(_) => legacy_entry.1,
        };
        Ok(Self {
            name,
            size: 0,
            sha1: vec![],
            legacy_zip_filename: Some(idx),
        })
    }

    pub(super) fn file_path(&self, media_folder: &Path) -> PathBuf {
        media_folder.join(&self.name)
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
) -> Result<Vec<MediaEntry>> {
    let media_list_data = get_media_list_data(archive, meta)?;
    if meta.media_list_is_hashmap() {
        let map: HashMap<&str, String> = serde_json::from_slice(&media_list_data)?;
        map.into_iter().map(MediaEntry::from_legacy).collect()
    } else {
        MediaEntries::decode_checked(&media_list_data).map(|m| m.entries)
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
    fn decode_checked(buf: &[u8]) -> Result<Self> {
        let entries: Self = Message::decode(buf)?;
        for entry in &entries.entries {
            if matches!(safe_normalized_file_name(&entry.name)?, Cow::Owned(_)) {
                return Err(AnkiError::ImportError(ImportError::Corrupt));
            }
        }
        Ok(entries)
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn normalization() {
        // legacy entries get normalized on deserialisation
        let entry = MediaEntry::from_legacy(("1", "con".to_owned())).unwrap();
        assert_eq!(entry.name, "con_");

        // new-style entries should have been normalized on export
        let mut entries = Vec::new();
        MediaEntries {
            entries: vec![MediaEntry::new("con", 0, Vec::new())],
        }
        .encode(&mut entries)
        .unwrap();
        assert!(MediaEntries::decode_checked(&entries).is_err());
    }
}
