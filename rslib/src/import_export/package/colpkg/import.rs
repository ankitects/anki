// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs::File;
use std::io;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;

use zip::read::ZipFile;
use zip::ZipArchive;
use zstd::stream::copy_decode;

use crate::collection::CollectionBuilder;
use crate::error::FileIoSnafu;
use crate::error::FileOp;
use crate::error::ImportError;
use crate::import_export::package::media::extract_media_entries;
use crate::import_export::package::media::SafeMediaEntry;
use crate::import_export::package::Meta;
use crate::import_export::ImportProgress;
use crate::import_export::IncrementableProgress;
use crate::io::atomic_rename;
use crate::io::create_dir_all;
use crate::io::new_tempfile_in_parent_of;
use crate::io::open_file;
use crate::media::MediaManager;
use crate::prelude::*;

pub fn import_colpkg(
    colpkg_path: &str,
    target_col_path: &str,
    target_media_folder: &Path,
    media_db: &Path,
    progress_fn: impl 'static + FnMut(ImportProgress, bool) -> bool,
) -> Result<()> {
    let mut progress = IncrementableProgress::new(progress_fn);
    progress.call(ImportProgress::File)?;
    let col_path = PathBuf::from(target_col_path);
    let mut tempfile = new_tempfile_in_parent_of(&col_path)?;

    let backup_file = open_file(colpkg_path)?;
    let mut archive = ZipArchive::new(backup_file)?;
    let meta = Meta::from_archive(&mut archive)?;

    copy_collection(&mut archive, &mut tempfile, &meta)?;
    progress.call(ImportProgress::File)?;
    check_collection_and_mod_schema(tempfile.path())?;
    progress.call(ImportProgress::File)?;

    restore_media(
        &meta,
        &mut progress,
        &mut archive,
        target_media_folder,
        media_db,
    )?;

    atomic_rename(tempfile, &col_path, true)?;

    Ok(())
}

fn check_collection_and_mod_schema(col_path: &Path) -> Result<()> {
    CollectionBuilder::new(col_path)
        .build()
        .ok()
        .and_then(|mut col| {
            col.set_schema_modified().ok()?;
            col.set_modified().ok()?;
            col.storage
                .db
                .pragma_query_value(None, "integrity_check", |row| row.get::<_, String>(0))
                .ok()
        })
        .and_then(|s| (s == "ok").then_some(()))
        .ok_or(AnkiError::ImportError {
            source: ImportError::Corrupt,
        })
}

fn restore_media(
    meta: &Meta,
    progress: &mut IncrementableProgress<ImportProgress>,
    archive: &mut ZipArchive<File>,
    media_folder: &Path,
    media_db: &Path,
) -> Result<()> {
    let media_entries = extract_media_entries(meta, archive)?;
    if media_entries.is_empty() {
        return Ok(());
    }

    create_dir_all(media_folder)?;
    let media_manager = MediaManager::new(media_folder, media_db)?;
    let mut media_comparer = MediaComparer::new(meta, progress, &media_manager)?;

    let mut incrementor = progress.incrementor(ImportProgress::Media);
    for mut entry in media_entries {
        incrementor.increment()?;
        maybe_restore_media_file(meta, media_folder, archive, &mut entry, &mut media_comparer)?;
    }

    Ok(())
}

fn maybe_restore_media_file(
    meta: &Meta,
    media_folder: &Path,
    archive: &mut ZipArchive<File>,
    entry: &mut SafeMediaEntry,
    media_comparer: &mut MediaComparer,
) -> Result<()> {
    let file_path = entry.file_path(media_folder);
    let mut zip_file = entry.fetch_file(archive)?;
    if meta.media_list_is_hashmap() {
        entry.size = zip_file.size() as u32;
    }

    let already_exists = media_comparer.entry_is_equal_to(entry, &file_path)?;
    if !already_exists {
        restore_media_file(meta, &mut zip_file, &file_path)?;
    };

    Ok(())
}

fn restore_media_file(meta: &Meta, zip_file: &mut ZipFile, path: &Path) -> Result<()> {
    let mut tempfile = new_tempfile_in_parent_of(path)?;
    meta.copy(zip_file, &mut tempfile)
        .with_context(|_| FileIoSnafu {
            path: tempfile.path(),
            op: FileOp::copy(zip_file.name()),
        })?;
    atomic_rename(tempfile, path, false)?;
    Ok(())
}

fn copy_collection(
    archive: &mut ZipArchive<File>,
    writer: &mut impl Write,
    meta: &Meta,
) -> Result<()> {
    let mut file =
        archive
            .by_name(meta.collection_filename())
            .map_err(|_| AnkiError::ImportError {
                source: ImportError::Corrupt,
            })?;
    if !meta.zstd_compressed() {
        io::copy(&mut file, writer)?;
    } else {
        copy_decode(file, writer)?;
    }

    Ok(())
}

type GetChecksumFn<'a> = dyn FnMut(&str) -> Result<Option<Sha1Hash>> + 'a;

struct MediaComparer<'a>(Option<Box<GetChecksumFn<'a>>>);

impl<'a> MediaComparer<'a> {
    fn new(
        meta: &Meta,
        progress: &mut IncrementableProgress<ImportProgress>,
        media_manager: &'a MediaManager,
    ) -> Result<Self> {
        Ok(Self(if meta.media_list_is_hashmap() {
            None
        } else {
            let mut db_progress_fn = progress.media_db_fn(ImportProgress::MediaCheck)?;
            media_manager.register_changes(&mut db_progress_fn)?;
            Some(Box::new(media_manager.checksum_getter()))
        }))
    }

    fn entry_is_equal_to(&mut self, entry: &SafeMediaEntry, other_path: &Path) -> Result<bool> {
        if let Some(ref mut get_checksum) = self.0 {
            Ok(entry.has_checksum_equal_to(get_checksum)?)
        } else {
            Ok(entry.has_size_equal_to(other_path))
        }
    }
}
