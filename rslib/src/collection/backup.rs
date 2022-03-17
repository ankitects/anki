// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::HashMap,
    ffi::OsStr,
    fs::{self, read_dir, remove_file, DirEntry, File},
    io::{self, Read, Write},
    path::{Path, PathBuf},
    thread::{self, JoinHandle},
    time::SystemTime,
};

use chrono::prelude::*;
use itertools::Itertools;
use log::error;
use prost::Message;
use tempfile::NamedTempFile;
use zip::ZipArchive;
use zstd::{self, stream::copy_decode};

use crate::{
    backend_proto::{
        media_entries::MediaEntry, package_metadata::Version, preferences::Backups, MediaEntries,
        PackageMetadata as Meta,
    },
    collection::{exporting::export_collection_data, CollectionBuilder},
    error::ImportError,
    log,
    prelude::*,
    text::normalize_to_nfc,
};

const BACKUP_FORMAT_STRING: &str = "backup-%Y-%m-%d-%H.%M.%S.colpkg";
/// Default seconds after a backup, in which further backups will be skipped.
const MINIMUM_BACKUP_INTERVAL: u64 = 5 * 60;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ImportProgress {
    Collection,
    Media(usize),
}

pub fn backup(
    col_path: impl AsRef<Path>,
    backup_folder: impl AsRef<Path> + Send + 'static,
    limits: Backups,
    minimum_backup_interval: Option<u64>,
    log: Logger,
    tr: I18n,
) -> Result<Option<JoinHandle<()>>> {
    let recent_secs = minimum_backup_interval.unwrap_or(MINIMUM_BACKUP_INTERVAL);
    if recent_secs > 0 && has_recent_backup(backup_folder.as_ref(), recent_secs)? {
        Ok(None)
    } else {
        let col_data = std::fs::read(col_path)?;
        Ok(Some(thread::spawn(move || {
            backup_inner(&col_data, &backup_folder, limits, log, &tr)
        })))
    }
}

fn has_recent_backup(backup_folder: &Path, recent_secs: u64) -> Result<bool> {
    let now = SystemTime::now();
    Ok(read_dir(backup_folder)?
        .filter_map(|res| res.ok())
        .filter_map(|entry| entry.metadata().ok())
        .filter_map(|meta| meta.created().ok())
        .filter_map(|time| now.duration_since(time).ok())
        .any(|duration| duration.as_secs() < recent_secs))
}

pub fn restore_backup(
    mut progress_fn: impl FnMut(ImportProgress) -> Result<()>,
    col_path: &str,
    backup_path: &str,
    media_folder: &str,
    tr: &I18n,
) -> Result<String> {
    progress_fn(ImportProgress::Collection)?;
    let col_path = PathBuf::from(col_path);
    let col_dir = col_path
        .parent()
        .ok_or_else(|| AnkiError::invalid_input("bad collection path"))?;
    let mut tempfile = NamedTempFile::new_in(col_dir)?;

    let backup_file = File::open(backup_path)?;
    let mut archive = ZipArchive::new(backup_file)?;
    let meta = Meta::from_archive(&mut archive)?;

    copy_collection(&mut archive, &mut tempfile, &meta)?;
    progress_fn(ImportProgress::Collection)?;
    check_collection(tempfile.path())?;
    progress_fn(ImportProgress::Collection)?;

    let mut result = String::new();
    if let Err(e) = restore_media(&meta, progress_fn, &mut archive, media_folder) {
        result = tr
            .importing_failed_to_import_media_file(e.localized_description(tr))
            .into_owned()
    };

    tempfile.as_file().sync_all()?;
    tempfile.persist(&col_path).map_err(|err| err.error)?;
    if !cfg!(windows) {
        File::open(col_dir)?.sync_all()?;
    }

    Ok(result)
}

fn backup_inner<P: AsRef<Path>>(
    col_data: &[u8],
    backup_folder: P,
    limits: Backups,
    log: Logger,
    tr: &I18n,
) {
    if let Err(error) = write_backup(col_data, backup_folder.as_ref(), tr) {
        error!(log, "failed to backup collection: {error:?}");
    }
    if let Err(error) = thin_backups(backup_folder, limits, &log) {
        error!(log, "failed to thin backups: {error:?}");
    }
}

fn write_backup<S: AsRef<OsStr>>(col_data: &[u8], backup_folder: S, tr: &I18n) -> Result<()> {
    let out_path =
        Path::new(&backup_folder).join(&format!("{}", Local::now().format(BACKUP_FORMAT_STRING)));
    export_collection_data(&out_path, col_data, tr)
}

fn thin_backups<P: AsRef<Path>>(backup_folder: P, limits: Backups, log: &Logger) -> Result<()> {
    let backups =
        read_dir(backup_folder)?.filter_map(|entry| entry.ok().and_then(Backup::from_entry));
    let obsolete_backups = BackupFilter::new(Local::today(), limits).obsolete_backups(backups);
    for backup in obsolete_backups {
        if let Err(error) = remove_file(&backup.path) {
            error!(log, "failed to remove {:?}: {error:?}", &backup.path);
        };
    }

    Ok(())
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
struct BackupFilter {
    yesterday: i32,
    last_kept_day: i32,
    last_kept_week: i32,
    last_kept_month: u32,
    limits: Backups,
    obsolete: Vec<Backup>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
enum BackupStage {
    Daily,
    Weekly,
    Monthly,
}

impl BackupFilter {
    fn new(today: Date<Local>, limits: Backups) -> Self {
        Self {
            yesterday: today.num_days_from_ce() - 1,
            last_kept_day: i32::MAX,
            last_kept_week: i32::MAX,
            last_kept_month: u32::MAX,
            limits,
            obsolete: Vec::new(),
        }
    }

    fn obsolete_backups(mut self, backups: impl Iterator<Item = Backup>) -> Vec<Backup> {
        use BackupStage::*;

        for backup in backups
            .sorted_unstable_by_key(|b| b.datetime.timestamp())
            .rev()
        {
            if self.is_recent(&backup) {
                self.mark_fresh(None, backup);
            } else if self.remaining(Daily) {
                self.mark_fresh_or_obsolete(Daily, backup);
            } else if self.remaining(Weekly) {
                self.mark_fresh_or_obsolete(Weekly, backup);
            } else if self.remaining(Monthly) {
                self.mark_fresh_or_obsolete(Monthly, backup);
            } else {
                self.mark_obsolete(backup);
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

    fn mark_fresh_or_obsolete(&mut self, stage: BackupStage, backup: Backup) {
        let keep = match stage {
            BackupStage::Daily => backup.day() < self.last_kept_day,
            BackupStage::Weekly => backup.week() < self.last_kept_week,
            BackupStage::Monthly => backup.month() < self.last_kept_month,
        };
        if keep {
            self.mark_fresh(Some(stage), backup);
        } else {
            self.mark_obsolete(backup);
        }
    }

    /// Adjusts limits as per the stage of the kept backup, and last kept times.
    fn mark_fresh(&mut self, stage: Option<BackupStage>, backup: Backup) {
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

    fn mark_obsolete(&mut self, backup: Backup) {
        self.obsolete.push(backup);
    }
}

impl Meta {
    /// Extracts meta data from an archive and checks if its version is supported.
    fn from_archive(archive: &mut ZipArchive<File>) -> Result<Self> {
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
    media_folder: &str,
) -> Result<()> {
    let media_entries = extract_media_entries(meta, archive)?;
    let mut count = 0;

    for (archive_file_name, entry) in media_entries.iter().enumerate() {
        count += 1;
        if count % 10 == 0 {
            progress_fn(ImportProgress::Media(count))?;
        }

        if let Ok(mut zip_file) = archive.by_name(&archive_file_name.to_string()) {
            let file_path = Path::new(&media_folder).join(normalize_to_nfc(&entry.name).as_ref());
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
        .by_name(meta.collection_name())
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

    macro_rules! backup {
        ($num_days_from_ce:expr) => {
            Backup {
                datetime: Local
                    .from_local_datetime(
                        &NaiveDate::from_num_days_from_ce($num_days_from_ce).and_hms(0, 0, 0),
                    )
                    .latest()
                    .unwrap(),
                path: PathBuf::new(),
            }
        };
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

    #[test]
    fn thinning_manual() {
        let today = Local.ymd(2022, 2, 22);
        let limits = Backups {
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
            BackupFilter::new(today, limits).obsolete_backups(backups.into_iter().map(|b| b.0));

        assert_eq!(obsolete_backups, expected);
    }

    #[test]
    fn thinning_generic() {
        let today = Local.ymd(2022, 1, 1);
        let today_ce_days = today.num_days_from_ce();
        let limits = Backups {
            // config defaults
            daily: 12,
            weekly: 10,
            monthly: 9,
        };
        let backups: Vec<_> = (1..366).map(|i| backup!(today_ce_days - i)).collect();
        let mut expected = Vec::new();

        // one day grace period, then daily backups
        let mut backup_iter = backups.iter().skip(1 + limits.daily as usize);

        // weekly backups from the last day of the week (Sunday)
        for _ in 0..limits.weekly {
            for backup in backup_iter.by_ref() {
                if backup.datetime.weekday() == Weekday::Sun {
                    break;
                } else {
                    expected.push(backup.clone())
                }
            }
        }

        // monthly backups from the last day of the month
        for _ in 0..limits.monthly {
            for backup in backup_iter.by_ref() {
                if backup.datetime.date().month() != backup.datetime.date().succ().month() {
                    break;
                } else {
                    expected.push(backup.clone())
                }
            }
        }

        // limits reached; collect rest
        backup_iter
            .cloned()
            .for_each(|backup| expected.push(backup));

        let obsolete_backups =
            BackupFilter::new(today, limits).obsolete_backups(backups.into_iter());
        assert_eq!(obsolete_backups, expected);
    }
}
