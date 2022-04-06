// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    fs::File,
    io::{self, Write},
    path::{Path, PathBuf},
};

use zip::{read::ZipFile, ZipArchive};
use zstd::{self, stream::copy_decode};

use crate::{
    collection::CollectionBuilder,
    error::ImportError,
    import_export::{
        package::{
            media::{extract_media_entries, SafeMediaEntry},
            Meta,
        },
        ImportProgress,
    },
    io::{atomic_rename, tempfile_in_parent_of},
    prelude::*,
};

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

    for entry in &media_entries {
        if entry.index % 10 == 0 {
            progress_fn(ImportProgress::Media(entry.index))?;
        }
        maybe_restore_media_file(meta, media_folder, archive, entry)?;
    }

    Ok(())
}

fn maybe_restore_media_file(
    meta: &Meta,
    media_folder: &Path,
    archive: &mut ZipArchive<File>,
    entry: &SafeMediaEntry,
) -> Result<()> {
    let file_path = entry.file_path(media_folder);
    let mut zip_file = entry.fetch_file(archive)?;
    let already_exists = entry.is_equal_to(meta, &zip_file, &file_path);
    if !already_exists {
        restore_media_file(meta, &mut zip_file, &file_path)?;
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
