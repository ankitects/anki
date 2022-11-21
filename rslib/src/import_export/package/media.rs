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

use super::{colpkg::export::MediaCopier, MediaEntries, MediaEntry, Meta};
use crate::{
    error::ImportError,
    io::{atomic_rename, filename_is_safe, new_tempfile_in},
    media::files::normalize_filename,
    prelude::*,
};

/// Like [MediaEntry], but with a safe filename and set zip filename.
pub(super) struct SafeMediaEntry {
    pub(super) name: String,
    pub(super) size: u32,
    pub(super) sha1: Option<Sha1Hash>,
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
                    sha1: Some(sha1),
                    index,
                });
            }
        }
        Err(AnkiError::ImportError {
            source: ImportError::Corrupt,
        })
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
            sha1: None,
            index: zip_filename,
        })
    }

    pub(super) fn file_path(&self, media_folder: &Path) -> PathBuf {
        media_folder.join(&self.name)
    }

    pub(super) fn fetch_file<'a>(&self, archive: &'a mut ZipArchive<File>) -> Result<ZipFile<'a>> {
        match archive.by_name(&self.index.to_string()) {
            Ok(file) => Ok(file),
            Err(err) => invalid_input!(err, "{} missing from archive", self.index),
        }
    }

    pub(super) fn has_checksum_equal_to(
        &self,
        get_checksum: &mut impl FnMut(&str) -> Result<Option<Sha1Hash>>,
    ) -> Result<bool> {
        get_checksum(&self.name)
            .map(|opt| opt.map_or(false, |sha1| sha1 == self.sha1.expect("sha1 not set")))
    }

    pub(super) fn has_size_equal_to(&self, other_path: &Path) -> bool {
        fs::metadata(other_path).map_or(false, |metadata| metadata.len() == self.size as u64)
    }

    /// Copy the archived file to the target folder, setting its hash if necessary.
    pub(super) fn copy_and_ensure_sha1_set(
        &mut self,
        archive: &mut ZipArchive<File>,
        target_folder: &Path,
        copier: &mut MediaCopier,
    ) -> Result<()> {
        let mut file = self.fetch_file(archive)?;
        let mut tempfile = new_tempfile_in(target_folder)?;
        if self.sha1.is_none() {
            let (_, sha1) = copier.copy(&mut file, &mut tempfile)?;
            self.sha1 = Some(sha1);
        } else {
            io::copy(&mut file, &mut tempfile)?;
        }
        atomic_rename(tempfile, &self.file_path(target_folder), false)?;

        Ok(())
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

pub(super) fn safe_normalized_file_name(name: &str) -> Result<Cow<str>> {
    if !filename_is_safe(name) {
        Err(AnkiError::ImportError {
            source: ImportError::Corrupt,
        })
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
