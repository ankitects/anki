// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    borrow::Cow,
    collections::HashMap,
    fs::{self, File},
    io::{self, Read, Write},
    path::{Component, Path, PathBuf},
};

use prost::Message;
use tempfile::NamedTempFile;
use zip::ZipArchive;
use zstd::{self, stream::copy_decode};

use super::super::Version;
use crate::{
    collection::CollectionBuilder,
    error::ImportError,
    import_export::{
        package::{MediaEntries, MediaEntry, Meta},
        ImportProgress,
    },
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
    tr: &I18n,
    mut progress_fn: impl FnMut(ImportProgress) -> Result<()>,
) -> Result<()> {
    progress_fn(ImportProgress::Collection)?;
    let col_path = PathBuf::from(target_col_path);
    let col_dir = col_path
        .parent()
        .ok_or_else(|| AnkiError::invalid_input("bad collection path"))?;
    let mut tempfile = NamedTempFile::new_in(col_dir)?;

    let backup_file = File::open(colpkg_path)?;
    let mut archive = ZipArchive::new(backup_file)?;
    let meta = Meta::from_archive(&mut archive)?;

    copy_collection(&mut archive, &mut tempfile, &meta)?;
    progress_fn(ImportProgress::Collection)?;
    check_collection(tempfile.path())?;
    progress_fn(ImportProgress::Collection)?;

    let media_folder = Path::new(target_media_folder);
    let media_import_result = restore_media(&meta, progress_fn, &mut archive, media_folder)
        .map_err(|err| {
            AnkiError::ImportError(ImportError::MediaImportFailed(
                err.localized_description(tr),
            ))
        });

    // Proceed with replacing collection, regardless of media import result
    tempfile.as_file().sync_all()?;
    tempfile.persist(&col_path).map_err(|err| err.error)?;
    if !cfg!(windows) {
        File::open(col_dir)?.sync_all()?;
    }

    media_import_result
}

fn check_collection(col_path: &Path) -> Result<()> {
    CollectionBuilder::new(col_path)
        .build()
        .ok()
        .and_then(|col| {
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
    let mut count = 0;

    for (archive_file_name, entry) in media_entries.iter().enumerate() {
        count += 1;
        if count % 10 == 0 {
            progress_fn(ImportProgress::Media(count))?;
        }

        if let Ok(mut zip_file) = archive.by_name(&archive_file_name.to_string()) {
            check_filename_safe(&entry.name)?;
            let normalized = maybe_normalizing(&entry.name, meta.strict_media_checks())?;
            let file_path = media_folder.join(normalized.as_ref());
            let size_in_colpkg = if meta.media_list_is_hashmap() {
                zip_file.size()
            } else {
                entry.size as u64
            };
            let files_are_equal = fs::metadata(&file_path)
                .map(|metadata| metadata.len() == size_in_colpkg)
                .unwrap_or_default();
            if !files_are_equal {
                // FIXME: write to temp file and atomic rename
                let mut file = match File::create(&file_path) {
                    Ok(file) => file,
                    Err(err) => return Err(AnkiError::file_io_error(err, &file_path)),
                };
                if meta.zstd_compressed() {
                    copy_decode(&mut zip_file, &mut file)
                } else {
                    io::copy(&mut zip_file, &mut file).map(|_| ())
                }
                .map_err(|err| AnkiError::file_io_error(err, &file_path))?;
            }
        } else {
            return Err(AnkiError::invalid_input(&format!(
                "{archive_file_name} missing from archive"
            )));
        }
    }
    Ok(())
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

/// Return an error if name contains any path separators.
fn check_filename_safe(name: &str) -> Result<()> {
    let mut components = Path::new(name).components();
    let first_element_normal = components
        .next()
        .map(|component| matches!(component, Component::Normal(_)))
        .unwrap_or_default();
    if !first_element_normal || components.next().is_some() {
        Err(AnkiError::ImportError(ImportError::Corrupt))
    } else {
        Ok(())
    }
}

fn extract_media_entries(meta: &Meta, archive: &mut ZipArchive<File>) -> Result<Vec<MediaEntry>> {
    let mut file = archive.by_name("media")?;
    let mut buf = Vec::new();
    if meta.zstd_compressed() {
        copy_decode(file, &mut buf)?;
    } else {
        io::copy(&mut file, &mut buf)?;
    }
    if meta.media_list_is_hashmap() {
        let map: HashMap<&str, String> = serde_json::from_slice(&buf)?;
        let mut entries: Vec<(usize, String)> = map
            .into_iter()
            .map(|(k, v)| (k.parse().unwrap_or_default(), v))
            .collect();
        entries.sort_unstable();
        // any gaps in the file numbers would lead to media being imported under the wrong name
        if entries
            .iter()
            .enumerate()
            .any(|(idx1, (idx2, _))| idx1 != *idx2)
        {
            return Err(AnkiError::ImportError(ImportError::Corrupt));
        }
        Ok(entries
            .into_iter()
            .map(|(_str_idx, name)| MediaEntry {
                name,
                size: 0,
                sha1: vec![],
            })
            .collect())
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
    fn path_traversal() {
        assert!(check_filename_safe("foo").is_ok(),);

        assert!(check_filename_safe("..").is_err());
        assert!(check_filename_safe("foo/bar").is_err());
        assert!(check_filename_safe("/foo").is_err());
        assert!(check_filename_safe("../foo").is_err());

        if cfg!(windows) {
            assert!(check_filename_safe("foo\\bar").is_err());
            assert!(check_filename_safe("c:\\foo").is_err());
            assert!(check_filename_safe("\\foo").is_err());
        }
    }

    #[test]
    fn normalization() {
        assert_eq!(&maybe_normalizing("con", false).unwrap(), "con_");
        assert!(&maybe_normalizing("con", true).is_err());
    }
}
