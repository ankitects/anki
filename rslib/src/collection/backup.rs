// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::ffi::OsStr;
use std::fs::read_dir;
use std::fs::remove_file;
use std::fs::DirEntry;
use std::path::Path;
use std::path::PathBuf;
use std::thread;
use std::thread::JoinHandle;
use std::time::SystemTime;

use anki_io::read_locked_db_file;
use anki_proto::config::preferences::BackupLimits;
use chrono::prelude::*;
use itertools::Itertools;
use tracing::error;
use tracing::warn;

use crate::import_export::package::check_colpkg_header;
use crate::import_export::package::export_colpkg_from_data;
use crate::import_export::package::validate_colpkg;
use crate::prelude::*;

const BACKUP_FORMAT_STRING: &str = "backup-%Y-%m-%d-%H.%M.%S.colpkg";

impl Collection {
    /// Create a backup if enough time has elapsed, or if forced.
    /// Returns a handle that can be awaited if a backup was created.
    pub fn maybe_backup(
        &mut self,
        backup_folder: impl AsRef<Path> + Send + 'static,
        force: bool,
    ) -> Result<Option<JoinHandle<Result<()>>>> {
        if !self.changed_since_last_backup()? {
            return Ok(None);
        }
        let limits = self.get_backup_limits();
        if should_skip_backup(force, limits.minimum_interval_mins, backup_folder.as_ref())? {
            Ok(None)
        } else {
            let tr = self.tr.clone();
            self.storage.checkpoint()?;
            let col_data = read_locked_db_file(&self.col_path)?;
            let snapshot_modified = self.storage.get_collection_timestamps()?.collection_change;
            let last_backup_modified = self.state.last_backup_modified.clone();
            Ok(Some(thread::spawn(move || {
                backup_inner(&col_data, &backup_folder, limits, &tr)?;
                *last_backup_modified.lock().unwrap() = Some(snapshot_modified);
                Ok(())
            })))
        }
    }
}

fn should_skip_backup(
    force: bool,
    minimum_interval_mins: u32,
    backup_folder: &Path,
) -> Result<bool> {
    if force {
        Ok(false)
    } else {
        has_recent_backup(backup_folder, minimum_interval_mins)
    }
}

fn has_recent_backup(backup_folder: &Path, recent_mins: u32) -> Result<bool> {
    let recent_secs = u64::from(recent_mins) * 60;
    let now = SystemTime::now();
    Ok(read_dir(backup_folder)?
        .filter_map(|res| res.ok())
        .filter_map(Backup::from_entry)
        .filter(|backup| {
            let Ok(meta) = backup.path.metadata() else {
                return false;
            };
            // created time unsupported on Android
            #[cfg(target_os = "android")]
            let time = meta.modified();
            #[cfg(not(target_os = "android"))]
            let time = meta.created();
            time.ok()
                .and_then(|time| now.duration_since(time).ok())
                .is_some_and(|duration| duration.as_secs() < recent_secs)
        })
        // Runs with the collection locked on every periodic check, so only the
        // header is read; archives were fully validated when they were written.
        .any(|backup| backup.has_usable_header()))
}

fn backup_inner<P: AsRef<Path>>(
    col_data: &[u8],
    backup_folder: P,
    limits: BackupLimits,
    tr: &I18n,
) -> Result<()> {
    write_backup(col_data, backup_folder.as_ref(), tr)?;
    thin_backups(backup_folder, limits, Local::now())
}

fn write_backup<S: AsRef<OsStr>>(col_data: &[u8], backup_folder: S, tr: &I18n) -> Result<()> {
    let out_path =
        Path::new(&backup_folder).join(format!("{}", Local::now().format(BACKUP_FORMAT_STRING)));
    export_colpkg_from_data(out_path, col_data, tr)
}

fn thin_backups<P: AsRef<Path>>(
    backup_folder: P,
    limits: BackupLimits,
    now: DateTime<Local>,
) -> Result<()> {
    let backups: Vec<_> = read_dir(backup_folder)?
        .filter_map(|entry| entry.ok().and_then(Backup::from_entry))
        .collect();
    // If all candidates fit, leave every file in place without extracting old
    // collections. Before any deletion, validate which archives can occupy slots.
    if BackupFilter::new(now, limits)
        .obsolete_backups(backups.iter().cloned())
        .is_empty()
    {
        return Ok(());
    }
    let obsolete_backups = BackupFilter::new(now, limits)
        .obsolete_backups(backups.into_iter().filter(Backup::is_usable));
    for backup in obsolete_backups {
        if let Err(error) = remove_file(&backup.path) {
            error!("failed to remove {:?}: {error:?}", &backup.path);
        };
    }

    Ok(())
}

fn datetime_from_file_name(file_name: &str) -> Option<DateTime<Local>> {
    NaiveDateTime::parse_from_str(file_name, BACKUP_FORMAT_STRING)
        .ok()
        .and_then(|datetime| Local.from_local_datetime(&datetime).latest())
}

#[derive(Debug, PartialEq, Eq, Clone)]
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
        // Day 1 (01/01/01) was a Monday, meaning week rolled over on Sunday (when day %
        // 7 == 0). We subtract 1 to shift the rollover to Monday.
        (self.day() - 1) / 7
    }

    /// Serial month number
    fn month(&self) -> u32 {
        self.datetime.year() as u32 * 12 + self.datetime.month()
    }
}

impl Backup {
    fn from_entry(entry: DirEntry) -> Option<Self> {
        if !entry.file_type().ok()?.is_file() {
            return None;
        }
        entry
            .file_name()
            .to_str()
            .and_then(datetime_from_file_name)
            .map(|datetime| Self {
                path: entry.path(),
                datetime,
            })
    }

    /// Full check, required before a backup may displace older ones.
    fn is_usable(&self) -> bool {
        self.passes(validate_colpkg)
    }

    fn has_usable_header(&self) -> bool {
        self.passes(check_colpkg_header)
    }

    fn passes(&self, check: fn(&Path) -> Result<()>) -> bool {
        match check(&self.path) {
            Ok(()) => true,
            Err(error) => {
                warn!(path = ?self.path, ?error, "ignoring unreadable backup");
                false
            }
        }
    }
}

#[derive(Debug)]
struct BackupFilter {
    yesterday: i32,
    last_kept_day: i32,
    last_kept_week: i32,
    last_kept_month: u32,
    limits: BackupLimits,
    obsolete: Vec<Backup>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum BackupStage {
    Daily,
    Weekly,
    Monthly,
}

impl BackupFilter {
    fn new(today: DateTime<Local>, limits: BackupLimits) -> Self {
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

#[cfg(test)]
mod test {
    use super::*;
    use crate::collection::CollectionBuilder;

    fn create_backup(path: &Path) {
        let col_dir = tempfile::tempdir().unwrap();
        CollectionBuilder::new(col_dir.path().join("collection.anki2"))
            .build()
            .unwrap()
            .export_colpkg(path, false, false)
            .unwrap();
    }

    fn create_legacy_backup(path: &Path, collection: &[u8]) {
        use std::io::Write;

        let mut zip = zip::ZipWriter::new(std::fs::File::create(path).unwrap());
        zip.start_file("collection.anki2", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(collection).unwrap();
        zip.start_file("media", zip::write::SimpleFileOptions::default())
            .unwrap();
        zip.write_all(b"{}").unwrap();
        zip.finish().unwrap();
    }

    fn create_invalid_collection_backup(path: &Path) {
        create_legacy_backup(path, b"not a SQLite collection");
    }

    #[test]
    fn corrupt_backups_do_not_displace_valid_backups() {
        let now = Local
            .with_ymd_and_hms(2022, 2, 22, 0, 0, 0)
            .latest()
            .unwrap();
        for limits in [
            BackupLimits {
                daily: 1,
                ..Default::default()
            },
            BackupLimits {
                weekly: 1,
                ..Default::default()
            },
            BackupLimits {
                monthly: 1,
                ..Default::default()
            },
        ] {
            for corrupt_data in [b"".as_slice(), b"PK\x03\x04incomplete backup".as_slice()] {
                let dir = tempfile::tempdir().unwrap();
                let valid = dir.path().join("backup-2000-01-01-00.00.00.colpkg");
                let corrupt = dir.path().join("backup-2000-02-01-00.00.00.colpkg");
                create_backup(&valid);
                let original = std::fs::read(&valid).unwrap();
                std::fs::write(&corrupt, corrupt_data).unwrap();
                thin_backups(dir.path(), limits, now).unwrap();
                assert_eq!(std::fs::read(&valid).unwrap(), original);
                assert_eq!(std::fs::read(&corrupt).unwrap(), corrupt_data);
                // A valid newer backup still occupies the slot and removes the old one.
                create_backup(&corrupt);
                thin_backups(dir.path(), limits, now).unwrap();
                assert!(!valid.exists());
                assert!(corrupt.exists());
            }
        }
    }

    #[test]
    fn invalid_collections_do_not_displace_valid_backups() {
        let dir = tempfile::tempdir().unwrap();
        let valid = dir.path().join("backup-2000-01-01-00.00.00.colpkg");
        let corrupt = dir.path().join("backup-2000-02-01-00.00.00.colpkg");
        create_backup(&valid);
        create_invalid_collection_backup(&corrupt);
        thin_backups(
            dir.path(),
            BackupLimits {
                daily: 1,
                ..Default::default()
            },
            Local::now(),
        )
        .unwrap();
        assert!(valid.exists());
        assert!(corrupt.exists());
    }

    #[test]
    fn only_usable_backups_suppress_automatic_backup() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::write(dir.path().join("unrelated.txt"), b"unrelated").unwrap();
        let path = dir
            .path()
            .join(Local::now().format(BACKUP_FORMAT_STRING).to_string());
        std::fs::create_dir(&path).unwrap();
        assert!(!has_recent_backup(dir.path(), 30).unwrap());
        std::fs::remove_dir(&path).unwrap();
        for data in [b"".as_slice(), b"PK\x03\x04incomplete backup".as_slice()] {
            std::fs::write(&path, data).unwrap();
            assert!(!has_recent_backup(dir.path(), 30).unwrap());
        }
        std::fs::remove_file(&path).unwrap();
        create_invalid_collection_backup(&path);
        assert!(!has_recent_backup(dir.path(), 30).unwrap());
        create_backup(&path);
        assert!(has_recent_backup(dir.path(), 30).unwrap());
        assert!(!has_recent_backup(dir.path(), 0).unwrap());
    }

    #[test]
    fn recent_backup_check_does_not_read_collection_body() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir
            .path()
            .join(Local::now().format(BACKUP_FORMAT_STRING).to_string());
        let mut collection = b"SQLite format 3\0".to_vec();
        collection.resize(4096, 0xff);
        create_legacy_backup(&path, &collection);
        // Full validation is left to backup creation and thinning.
        assert!(validate_colpkg(&path).is_err());
        assert!(has_recent_backup(dir.path(), 30).unwrap());
    }

    #[test]
    fn completion_records_snapshot_timestamp() {
        let dir = tempfile::tempdir().unwrap();
        let mut col = CollectionBuilder::new(dir.path().join("collection.anki2"))
            .build()
            .unwrap();
        let snapshot = col
            .storage
            .get_collection_timestamps()
            .unwrap()
            .collection_change;
        let task = col
            .maybe_backup(dir.path().to_path_buf(), true)
            .unwrap()
            .unwrap();
        col.set_modified_time_undoable(TimestampMillis(snapshot.0 + 1), snapshot)
            .unwrap();
        task.join().unwrap().unwrap();
        assert_eq!(
            *col.state.last_backup_modified.lock().unwrap(),
            Some(snapshot)
        );
        assert!(col.changed_since_last_backup().unwrap());
        let next = tempfile::tempdir().unwrap();
        col.maybe_backup(next.path().to_path_buf(), true)
            .unwrap()
            .unwrap()
            .join()
            .unwrap()
            .unwrap();
        assert!(!col.changed_since_last_backup().unwrap());
        assert!(col
            .maybe_backup(next.path().to_path_buf(), true)
            .unwrap()
            .is_none());
    }

    macro_rules! backup {
        ($num_days_from_ce:expr) => {
            Backup {
                datetime: Local
                    .from_local_datetime(
                        &NaiveDate::from_num_days_from_ce_opt($num_days_from_ce)
                            .unwrap()
                            .and_hms_opt(0, 0, 0)
                            .unwrap(),
                    )
                    .latest()
                    .unwrap(),
                path: PathBuf::new(),
            }
        };
        ($year:expr, $month:expr, $day:expr) => {
            Backup {
                datetime: Local
                    .with_ymd_and_hms($year, $month, $day, 0, 0, 0)
                    .latest()
                    .unwrap(),
                path: PathBuf::new(),
            }
        };
        ($year:expr, $month:expr, $day:expr, $hour:expr, $min:expr, $sec:expr) => {
            Backup {
                datetime: Local
                    .with_ymd_and_hms($year, $month, $day, $hour, $min, $sec)
                    .latest()
                    .unwrap(),
                path: PathBuf::new(),
            }
        };
    }

    #[test]
    fn thinning_manual() {
        let today = Local
            .with_ymd_and_hms(2022, 2, 22, 0, 0, 0)
            .latest()
            .unwrap();
        let limits = BackupLimits {
            daily: 3,
            weekly: 2,
            monthly: 1,
            ..Default::default()
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
            .filter(|b| b.1)
            .map(|b| b.0.clone())
            .collect();
        let obsolete_backups =
            BackupFilter::new(today, limits).obsolete_backups(backups.into_iter().map(|b| b.0));

        assert_eq!(obsolete_backups, expected);
    }

    #[test]
    fn thinning_generic() {
        let today = Local
            .with_ymd_and_hms(2022, 1, 1, 0, 0, 0)
            .latest()
            .unwrap();
        let today_ce_days = today.num_days_from_ce();
        let limits = BackupLimits {
            // config defaults
            daily: 12,
            weekly: 10,
            monthly: 9,
            ..Default::default()
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
                if backup.datetime.month()
                    != backup.datetime.date_naive().succ_opt().unwrap().month()
                {
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
