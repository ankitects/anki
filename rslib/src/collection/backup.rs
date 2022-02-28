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
use tempfile::NamedTempFile;
use zip::{read::ZipFile, write::FileOptions, CompressionMethod, ZipArchive, ZipWriter};
use zstd;

use crate::{
    collection::CollectionBuilder,
    config::BackupLimits,
    error::{DbError, DbErrorKind},
    log,
    prelude::*,
    text::normalize_to_nfc,
};

/// Bump if making changes that break restoring on older releases.
/// Versions 1 and 2 wrote different archives, so minimum expected value is 3.
const BACKUP_VERSION: u8 = 3;
const BACKUP_FORMAT_STRING: &str = "backup-%Y-%m-%d-%H.%M.%S.colpkg";

#[derive(Debug, Default, Serialize, Deserialize)]
#[serde(default)]
struct Meta {
    #[serde(rename = "ver")]
    version: u8,
}

pub fn backup<P1, P2>(col_path: P1, backup_folder: P2, limits: BackupLimits) -> Result<()>
where
    P1: AsRef<Path>,
    P2: AsRef<Path> + Send + 'static,
{
    let col_data = std::fs::read(col_path)?;
    thread::spawn(move || backup_inner(&col_data, &backup_folder, limits));

    Ok(())
}

pub fn restore_backup(
    progress_fn: impl FnMut(usize) -> bool,
    col_path: &str,
    backup_path: &str,
    media_folder: &str,
) -> Result<()> {
    let col_path = PathBuf::from(col_path);
    let col_dir = col_path
        .parent()
        .ok_or_else(|| AnkiError::invalid_input("bad collection path"))?;
    let tempfile = NamedTempFile::new_in(col_dir)?;

    let backup_file = File::open(backup_path)?;
    let mut archive = ZipArchive::new(backup_file)?;
    let new_col_data = extract_collection_data(&mut archive)?;
    fs::write(&tempfile, new_col_data)?;

    check_collection(tempfile.path())?;
    restore_media(progress_fn, &mut archive, media_folder)?;
    tempfile.persist(&col_path).map_err(|err| err.error)?;

    Ok(())
}

fn backup_inner<P: AsRef<Path>>(col_data: &[u8], backup_folder: P, limits: BackupLimits) {
    let log = log::terminal();
    if let Err(error) = write_backup(col_data, backup_folder.as_ref()) {
        error!(log, "failed to backup collection: {:?}", error);
    }
    if let Err(error) = thin_backups(backup_folder, limits) {
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

    let compressed_data = zstd::encode_all(col_data, 0)?;

    zip.start_file("meta", options)?;
    zip.write_all(meta.as_bytes())?;
    zip.start_file("collection.anki21b", options)?;
    zip.write_all(&compressed_data)?;
    zip.start_file("media", options)?;
    zip.write_all(b"{}")?;
    zip.finish()?;

    Ok(())
}

fn thin_backups<P: AsRef<Path>>(backup_folder: P, limits: BackupLimits) -> Result<()> {
    let backups =
        read_dir(backup_folder)?.filter_map(|entry| entry.ok().and_then(Backup::from_entry));
    let obsolete_backups = BackupThinner::new(Local::today(), limits).thin(backups);
    for backup in obsolete_backups {
        let _ = remove_file(backup.path);
    }

    Ok(())
}

fn out_path<S: AsRef<OsStr>>(backup_folder: S) -> PathBuf {
    Path::new(&backup_folder).join(&format!("{}", Local::now().format(BACKUP_FORMAT_STRING)))
}

fn datetime_from_file_name(file_name: &str) -> Option<DateTime<Local>> {
    NaiveDateTime::parse_from_str(file_name, BACKUP_FORMAT_STRING)
        .ok()
        .and_then(|datetime| Local.from_local_datetime(&datetime).latest())
}

#[derive(Debug, PartialEq, Clone)]
struct Backup {
    path: PathBuf,
    datetime: DateTime<Local>,
}

impl Backup {
    /// Serial day number
    fn day(&self) -> i32 {
        self.datetime.num_days_from_ce()
    }

    /// Serial week number, starting on Monday
    fn week(&self) -> i32 {
        // Day 1 (01/01/01) was a Monday, meaning week rolled over on Sunday (when day % 7 == 0).
        // We subtract 1 to shift the rollover to Monday.
        (self.day() - 1) / 7
    }

    /// Serial month number
    fn month(&self) -> u32 {
        self.datetime.year() as u32 * 12 + self.datetime.month()
    }
}

impl Backup {
    fn from_entry(entry: DirEntry) -> Option<Self> {
        entry
            .file_name()
            .to_str()
            .and_then(datetime_from_file_name)
            .map(|datetime| Self {
                path: entry.path(),
                datetime,
            })
    }
}

#[derive(Debug)]
struct BackupThinner {
    yesterday: i32,
    last_kept_day: i32,
    last_kept_week: i32,
    last_kept_month: u32,
    limits: BackupLimits,
    obsolete: Vec<Backup>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
enum BackupStage {
    Daily,
    Weekly,
    Monthly,
}

impl BackupThinner {
    fn new(today: Date<Local>, limits: BackupLimits) -> Self {
        Self {
            yesterday: today.num_days_from_ce() - 1,
            last_kept_day: i32::MAX,
            last_kept_week: i32::MAX,
            last_kept_month: u32::MAX,
            limits,
            obsolete: Vec::new(),
        }
    }

    /// Returns a [Vec] with the obsolete [Backup]s.
    fn thin(mut self, backups: impl Iterator<Item = Backup>) -> Vec<Backup> {
        use BackupStage::*;

        for backup in backups
            .sorted_unstable_by_key(|b| b.datetime.timestamp())
            .rev()
        {
            if self.is_recent(&backup) {
                self.keep(None, backup);
            } else if self.remaining(Daily) {
                self.keep_or_delete(Daily, backup);
            } else if self.remaining(Weekly) {
                self.keep_or_delete(Weekly, backup);
            } else if self.remaining(Monthly) {
                self.keep_or_delete(Monthly, backup);
            } else {
                self.delete(backup);
            }
        }

        self.obsolete
    }

    fn is_recent(&self, backup: &Backup) -> bool {
        backup.day() >= self.yesterday
    }

    fn remaining(&self, stage: BackupStage) -> bool {
        match stage {
            BackupStage::Daily => self.limits.daily > 0,
            BackupStage::Weekly => self.limits.weekly > 0,
            BackupStage::Monthly => self.limits.monthly > 0,
        }
    }

    fn keep_or_delete(&mut self, stage: BackupStage, backup: Backup) {
        let keep = match stage {
            BackupStage::Daily => backup.day() < self.last_kept_day,
            BackupStage::Weekly => backup.week() < self.last_kept_week,
            BackupStage::Monthly => backup.month() < self.last_kept_month,
        };
        if keep {
            self.keep(Some(stage), backup);
        } else {
            self.delete(backup);
        }
    }

    /// Adjusts limits as per the stage of the kept backup, and last kept times.
    fn keep(&mut self, stage: Option<BackupStage>, backup: Backup) {
        self.last_kept_day = backup.day();
        self.last_kept_week = backup.week();
        self.last_kept_month = backup.month();
        match stage {
            None => (),
            Some(BackupStage::Daily) => self.limits.daily -= 1,
            Some(BackupStage::Weekly) => self.limits.weekly -= 1,
            Some(BackupStage::Monthly) => self.limits.monthly -= 1,
        }
    }

    fn delete(&mut self, backup: Backup) {
        self.obsolete.push(backup);
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

fn check_collection(col_path: &Path) -> Result<()> {
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

fn restore_media(
    mut progress_fn: impl FnMut(usize) -> bool,
    archive: &mut ZipArchive<File>,
    media_folder: &str,
) -> Result<()> {
    let media_file_names = extract_media_file_names(archive)?;
    let mut count = 0;

    for (archive_file_name, file_name) in media_file_names {
        count += 1;
        if count % 10 == 0 && !progress_fn(count) {
            return Err(AnkiError::Interrupted);
        }

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

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn thinning() {
        macro_rules! backup {
            ($year:expr, $month:expr, $day:expr) => {
                Backup {
                    datetime: Local.ymd($year, $month, $day).and_hms(0, 0, 0),
                    path: PathBuf::new(),
                }
            };
            ($year:expr, $month:expr, $day:expr, $hour:expr, $min:expr, $sec:expr) => {
                Backup {
                    datetime: Local.ymd($year, $month, $day).and_hms($hour, $min, $sec),
                    path: PathBuf::new(),
                }
            };
        }

        let today = Local.ymd(2022, 2, 22);
        let limits = BackupLimits {
            daily: 3,
            weekly: 2,
            monthly: 1,
        };

        // true => should be removed
        let backups = [
            // grace period
            (backup!(2022, 2, 22), false),
            (backup!(2022, 2, 22), false),
            (backup!(2022, 2, 21), false),
            // daily
            (backup!(2022, 2, 20, 6, 0, 0), true),
            (backup!(2022, 2, 20, 18, 0, 0), false),
            (backup!(2022, 2, 10), false),
            (backup!(2022, 2, 9), false),
            // weekly
            (backup!(2022, 2, 7), true), // Monday, week already backed up
            (backup!(2022, 2, 6, 1, 0, 0), true),
            (backup!(2022, 2, 6, 2, 0, 0), false),
            (backup!(2022, 1, 6), false),
            // monthly
            (backup!(2022, 1, 5), true),
            (backup!(2021, 12, 24), false),
            (backup!(2021, 12, 1), true),
            (backup!(2021, 11, 1), true),
        ];

        let expected: Vec<_> = backups
            .iter()
            .filter_map(|b| b.1.then(|| b.0.clone()))
            .collect();
        let obsolete_backups =
            BackupThinner::new(today, limits).thin(backups.into_iter().map(|b| b.0));

        assert_eq!(obsolete_backups, expected);
    }
}
