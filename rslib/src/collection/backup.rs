// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    ffi::OsStr,
    fs::{read_dir, remove_file, DirEntry, File},
    io::Write,
    path::{Path, PathBuf},
    thread,
};

use chrono::prelude::*;
use itertools::Itertools;
use log::error;
use zip::{write::FileOptions, CompressionMethod, ZipWriter};
use zstd;

use crate::{log, prelude::*};

const BACKUP_FORMAT_STRING: &str = "backup-%Y-%m-%d-%H.%M.%S.colpkg";
/// Backups in the last [NO_THINNING_SECS] seconds are not thinned.
const NO_THINNING_SECS: u64 = 2 * 24 * 60 * 60;
/// At most one backup every [THINNING_INTERVAL_SECS] seconds is kept.
const THINNING_INTERVAL_SECS: i64 = 24 * 60 * 60;

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

    zip.start_file("meta", options)?;
    zip.write_all(b"{\"ver\":3}")?;
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
