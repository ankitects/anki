// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::HashMap,
    ffi::OsStr,
    fs::{self, read_dir, remove_file, DirEntry, File},
    io::{Read, Write},
    path::{Path, PathBuf},
    thread,
};

use chrono::prelude::*;
use itertools::Itertools;
use log::error;
use serde_derive::{Deserialize, Serialize};
use zip::{read::ZipFile, write::FileOptions, CompressionMethod, ZipArchive, ZipWriter};
use zstd;

use crate::{
    collection::CollectionBuilder,
    error::{DbError, DbErrorKind},
    log,
    prelude::*,
    text::normalize_to_nfc,
};

/// Bump if making changes that break restoring on older releases.
/// Versions 1 and 2 wrote different archives, so minimum expected value is 3.
const BACKUP_VERSION: u8 = 3;
const BACKUP_FORMAT_STRING: &str = "backup-%Y-%m-%d-%H.%M.%S.colpkg";
/// Backups in the last [NO_THINNING_SECS] seconds are not thinned.
const NO_THINNING_SECS: u64 = 2 * 24 * 60 * 60;
/// At most one backup every [THINNING_INTERVAL_SECS] seconds is kept.
const THINNING_INTERVAL_SECS: i64 = 24 * 60 * 60;

#[derive(Debug, Default, Serialize, Deserialize)]
#[serde(default)]
struct Meta {
    #[serde(rename = "ver")]
    version: u8,
}

pub fn backup<P1, P2>(col_path: P1, backup_folder: P2) -> Result<()>
where
    P1: AsRef<Path>,
    P2: AsRef<Path> + Send + 'static,
{
    let col_file = File::open(col_path)?;
    let col_data = zstd::encode_all(col_file, 0)?;

    thread::spawn(move || backup_inner(&col_data, &backup_folder));

    Ok(())
}

pub fn restore_backup(col_path: &str, backup_path: &str, media_folder: &str) -> Result<()> {
    let backup_file = File::open(backup_path)?;
    let mut archive = ZipArchive::new(backup_file)?;
    let new_col_data = extract_collection_data(&mut archive)?;
    let old_col_data = fs::read(col_path)?;
    fs::write(col_path, new_col_data)?;

    check_collection(col_path)
        .or_else(|err| {
            fs::write(col_path, old_col_data)?;
            Err(err)
        })
        .and_then(|_| restore_media(&mut archive, media_folder))
}

fn backup_inner<P: AsRef<Path>>(col_data: &[u8], backup_folder: P) {
    let log = log::terminal();
    if let Err(error) = write_backup(col_data, backup_folder.as_ref()) {
        error!(log, "failed to backup collection: {:?}", error);
    }
    if let Err(error) = thin_backups(backup_folder) {
        error!(log, "failed to thin backups: {:?}", error);
    }
}

fn write_backup<S: AsRef<OsStr>>(col_data: &[u8], backup_folder: S) -> Result<()> {
    let out_file = File::create(out_path(backup_folder))?;
    let mut zip = ZipWriter::new(out_file);
    let options = FileOptions::default().compression_method(CompressionMethod::Stored);
    let meta = serde_json::to_string(&Meta {
        version: BACKUP_VERSION,
    })
    .unwrap();

    zip.start_file("meta", options)?;
    zip.write_all(meta.as_bytes())?;
    zip.start_file("collection.anki21b", options)?;
    zip.write_all(col_data)?;
    zip.start_file("media", options)?;
    zip.write_all(b"{}")?;
    zip.finish()?;

    Ok(())
}

fn thin_backups<P: AsRef<Path>>(backup_folder: P) -> Result<()> {
    let backups = read_dir(backup_folder)?
        .filter_map(|entry| entry.ok().and_then(Backup::from_entry))
        .sorted_unstable_by_key(|entry| entry.timestamp)
        .rev();
    let mut last_kept_backup_time = TimestampSecs(i64::MAX);

    for backup in backups {
        let keep_this_backup = backup.timestamp.elapsed_secs() < NO_THINNING_SECS
            || last_kept_backup_time.elapsed_secs_since(backup.timestamp) > THINNING_INTERVAL_SECS;
        if keep_this_backup {
            last_kept_backup_time = backup.timestamp;
        } else {
            remove_file(backup.path)?;
        }
    }

    Ok(())
}

fn out_path<S: AsRef<OsStr>>(backup_folder: S) -> PathBuf {
    Path::new(&backup_folder).join(&format!("{}", Local::now().format(BACKUP_FORMAT_STRING)))
}

fn timestamp_from_file_name(file_name: &str) -> Option<i64> {
    NaiveDateTime::parse_from_str(file_name, BACKUP_FORMAT_STRING)
        .ok()
        .and_then(|datetime| Local.from_local_datetime(&datetime).latest())
        .map(|datetime| datetime.timestamp())
}

#[derive(Debug)]
struct Backup {
    path: PathBuf,
    timestamp: TimestampSecs,
}

impl Backup {
    fn from_entry(entry: DirEntry) -> Option<Self> {
        entry
            .file_name()
            .to_str()
            .and_then(timestamp_from_file_name)
            .map(|secs| Self {
                path: entry.path(),
                timestamp: TimestampSecs(secs),
            })
    }
}

impl Meta {
    fn from_archive(archive: &mut ZipArchive<File>) -> Option<Self> {
        archive
            .by_name("meta")
            .ok()
            .and_then(|file| serde_json::from_reader(file).ok())
    }
}

fn malformed_archive_err() -> AnkiError {
    AnkiError::DbError(DbError {
        info: "".to_string(),
        kind: DbErrorKind::Corrupt,
    })
}

fn too_new_err() -> AnkiError {
    AnkiError::DbError(DbError {
        info: "".to_string(),
        kind: DbErrorKind::FileTooNew,
    })
}

fn check_collection(col_path: &str) -> Result<()> {
    let col = CollectionBuilder::new(col_path).build()?;
    col.storage
        .db
        .pragma_query_value(None, "integrity_check", |row| row.get::<_, String>(0))
        .map_err(Into::into)
        .and_then(|s| match s.as_str() {
            "ok" => Ok(()),
            _ => Err(AnkiError::invalid_input(format!("corrupt: {}", s))),
        })
}

fn restore_media(archive: &mut ZipArchive<File>, media_folder: &str) -> Result<()> {
    let media_file_names = extract_media_file_names(archive)?;
    for (archive_file_name, file_name) in media_file_names {
        if let Ok(mut file) = archive.by_name(&archive_file_name) {
            let file_path = Path::new(&media_folder).join(normalize_to_nfc(&file_name).as_ref());
            let files_are_equal = fs::metadata(&file_path)
                .map(|metadata| metadata.len() == file.size())
                .unwrap_or_default();
            if !files_are_equal {
                let contents = get_zip_file_contents(&mut file)?;
                fs::write(&file_path, &contents)?;
            }
        }
    }
    Ok(())
}

fn extract_media_file_names(archive: &mut ZipArchive<File>) -> Result<HashMap<String, String>> {
    archive
        .by_name("media")
        .map_err(|_| malformed_archive_err())
        .and_then(|mut file| get_zip_file_contents(&mut file))
        .and_then(|bytes| serde_json::from_slice(&bytes).map_err(|_| malformed_archive_err()))
}

fn extract_collection_data(archive: &mut ZipArchive<File>) -> Result<Vec<u8>> {
    match Meta::from_archive(archive).map(|meta| meta.version) {
        Some(3) => extract_version_3_data(archive),
        Some(_) => Err(too_new_err()),
        None => extract_legacy_data(archive),
    }
}

fn extract_version_3_data(archive: &mut ZipArchive<File>) -> Result<Vec<u8>> {
    archive
        .by_name("collection.anki21b")
        .ok()
        .and_then(|file| zstd::decode_all(file).ok())
        .ok_or_else(malformed_archive_err)
}

fn extract_legacy_data(archive: &mut ZipArchive<File>) -> Result<Vec<u8>> {
    fn extract_by_name(archive: &mut ZipArchive<File>, name: &str) -> Option<Vec<u8>> {
        archive
            .by_name(name)
            .ok()
            .and_then(|mut file| get_zip_file_contents(&mut file).ok())
    }

    extract_by_name(archive, "collection.anki21")
        .or_else(|| extract_by_name(archive, "collection.anki2"))
        .ok_or_else(malformed_archive_err)
}

fn get_zip_file_contents(file: &mut ZipFile) -> Result<Vec<u8>> {
    let mut buf = Vec::new();
    if file.read_to_end(&mut buf).is_ok() {
        Ok(buf)
    } else {
        Err(malformed_archive_err())
    }
}
