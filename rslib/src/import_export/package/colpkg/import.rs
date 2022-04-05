// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    borrow::Cow,
    collections::HashMap,
    fs::{self, File},
    io::{self, Read, Write},
    path::{Path, PathBuf},
};

use prost::Message;
use zip::{read::ZipFile, ZipArchive};
use zstd::{self, stream::copy_decode};

use super::super::Version;
use crate::{
    collection::CollectionBuilder,
    error::ImportError,
    import_export::{
        package::{MediaEntries, MediaEntry, Meta},
        ImportProgress,
    },
    io::{atomic_rename, filename_is_safe, tempfile_in_parent_of},
    media::files::normalize_filename,
    prelude::*,
};

impl Meta {
    /// Extracts meta data from an archive and checks if its version is supported.
    pub(super) fn from_archive(archive: &mut ZipArchive<File>) -> Result<Self> {
        let meta_bytes = archive.by_name("meta").ok().and_then(|mut meta_file| {
            let mut buf = vec![];
            meta_file.read_to_end(&mut buf).ok()?;
            Some(buf)
        });
        let meta = if let Some(bytes) = meta_bytes {
            let meta: Meta = Message::decode(&*bytes)?;
            if meta.version() == Version::Unknown {
                return Err(AnkiError::ImportError(ImportError::TooNew));
            }
            meta
        } else {
            Meta {
                version: if archive.by_name("collection.anki21").is_ok() {
                    Version::Legacy2
                } else {
                    Version::Legacy1
                } as i32,
            }
        };
        Ok(meta)
    }
}

pub fn import_colpkg(
    colpkg_path: &str,
    target_col_path: &str,
    target_media_folder: &str,
    mut progress_fn: impl FnMut(ImportProgress) -> Result<()>,
) -> Result<()> {
    progress_fn(ImportProgress::Collection)?;
    let col_path = PathBuf::from(target_col_path);
    let mut tempfile = tempfile_in_parent_of(&col_path)?;

    let backup_file = File::open(colpkg_path)?;
    let mut archive = ZipArchive::new(backup_file)?;
    let meta = Meta::from_archive(&mut archive)?;

    copy_collection(&mut archive, &mut tempfile, &meta)?;
    progress_fn(ImportProgress::Collection)?;
    check_collection_and_mod_schema(tempfile.path())?;
    progress_fn(ImportProgress::Collection)?;

    let media_folder = Path::new(target_media_folder);
    restore_media(&meta, progress_fn, &mut archive, media_folder)?;

    atomic_rename(tempfile, &col_path, true)
}

fn check_collection_and_mod_schema(col_path: &Path) -> Result<()> {
    CollectionBuilder::new(col_path)
        .build()
        .ok()
        .and_then(|mut col| {
            col.set_schema_modified().ok()?;
            col.storage
                .db
                .pragma_query_value(None, "integrity_check", |row| row.get::<_, String>(0))
                .ok()
        })
        .and_then(|s| (s == "ok").then(|| ()))
        .ok_or(AnkiError::ImportError(ImportError::Corrupt))
}

fn restore_media(
    meta: &Meta,
    mut progress_fn: impl FnMut(ImportProgress) -> Result<()>,
    archive: &mut ZipArchive<File>,
    media_folder: &Path,
) -> Result<()> {
    let media_entries = extract_media_entries(meta, archive)?;
    std::fs::create_dir_all(media_folder)?;

    for (entry_idx, entry) in media_entries.iter().enumerate() {
        if entry_idx % 10 == 0 {
            progress_fn(ImportProgress::Media(entry_idx))?;
        }

        let zip_filename = entry
            .legacy_zip_filename
            .map(|n| n as usize)
            .unwrap_or(entry_idx)
            .to_string();

        if let Ok(mut zip_file) = archive.by_name(&zip_filename) {
            maybe_restore_media_file(meta, media_folder, entry, &mut zip_file)?;
        } else {
            return Err(AnkiError::invalid_input(&format!(
                "{zip_filename} missing from archive"
            )));
        }
    }

    Ok(())
}

fn maybe_restore_media_file(
    meta: &Meta,
    media_folder: &Path,
    entry: &MediaEntry,
    zip_file: &mut ZipFile,
) -> Result<()> {
    let file_path = entry.safe_normalized_file_path(meta, media_folder)?;
    let already_exists = entry.is_equal_to(meta, zip_file, &file_path);
    if !already_exists {
        restore_media_file(meta, zip_file, &file_path)?;
    };

    Ok(())
}

fn restore_media_file(meta: &Meta, zip_file: &mut ZipFile, path: &Path) -> Result<()> {
    let mut tempfile = tempfile_in_parent_of(path)?;

    if meta.zstd_compressed() {
        copy_decode(zip_file, &mut tempfile)
    } else {
        io::copy(zip_file, &mut tempfile).map(|_| ())
    }
    .map_err(|err| AnkiError::file_io_error(err, path))?;

    atomic_rename(tempfile, path, false)
}

impl MediaEntry {
    fn safe_normalized_file_path(&self, meta: &Meta, media_folder: &Path) -> Result<PathBuf> {
        if !filename_is_safe(&self.name) {
            return Err(AnkiError::ImportError(ImportError::Corrupt));
        }
        let normalized = maybe_normalizing(&self.name, meta.strict_media_checks())?;
        Ok(media_folder.join(normalized.as_ref()))
    }

    fn is_equal_to(&self, meta: &Meta, self_zipped: &ZipFile, other_path: &Path) -> bool {
        // TODO: checks hashs (https://github.com/ankitects/anki/pull/1723#discussion_r829653147)
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

/// - If strict is true, return an error if not normalized.
/// - If false, return the normalized version.
fn maybe_normalizing(name: &str, strict: bool) -> Result<Cow<str>> {
    let normalized = normalize_filename(name);
    if strict && matches!(normalized, Cow::Owned(_)) {
        // exporting code should have checked this
        Err(AnkiError::ImportError(ImportError::Corrupt))
    } else {
        Ok(normalized)
    }
}

pub(crate) fn extract_media_entries(
    meta: &Meta,
    archive: &mut ZipArchive<File>,
) -> Result<Vec<MediaEntry>> {
    let mut file = archive.by_name("media")?;
    let mut buf = Vec::new();
    if meta.zstd_compressed() {
        copy_decode(file, &mut buf)?;
    } else {
        io::copy(&mut file, &mut buf)?;
    }
    if meta.media_list_is_hashmap() {
        let map: HashMap<&str, String> = serde_json::from_slice(&buf)?;
        map.into_iter()
            .map(|(idx_str, name)| {
                let idx: u32 = idx_str.parse()?;
                Ok(MediaEntry {
                    name,
                    size: 0,
                    sha1: vec![],
                    legacy_zip_filename: Some(idx),
                })
            })
            .collect()
    } else {
        let entries: MediaEntries = Message::decode(&*buf)?;
        Ok(entries.entries)
    }
}

fn copy_collection(
    archive: &mut ZipArchive<File>,
    writer: &mut impl Write,
    meta: &Meta,
) -> Result<()> {
    let mut file = archive
        .by_name(meta.collection_filename())
        .map_err(|_| AnkiError::ImportError(ImportError::Corrupt))?;
    if !meta.zstd_compressed() {
        io::copy(&mut file, writer)?;
    } else {
        copy_decode(file, writer)?;
    }

    Ok(())
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn normalization() {
        assert_eq!(&maybe_normalizing("con", false).unwrap(), "con_");
        assert!(&maybe_normalizing("con", true).is_err());
    }
}
