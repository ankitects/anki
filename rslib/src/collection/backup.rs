// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    ffi::OsStr,
    fs::{read_dir, remove_file, DirEntry},
    path::{Path, PathBuf},
    thread::{self, JoinHandle},
    time::SystemTime,
};

use chrono::prelude::*;
use itertools::Itertools;
use log::error;

use crate::{
    backend_proto::preferences::BackupLimits, import_export::package::export_colpkg_from_data, log,
    prelude::*,
};

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
            let log = self.log.clone();
            let tr = self.tr.clone();
            self.storage.checkpoint()?;
            let col_data = std::fs::read(&self.col_path)?;
            self.update_last_backup_timestamp()?;
            Ok(Some(thread::spawn(move || {
                backup_inner(&col_data, &backup_folder, limits, log, &tr)
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
    let recent_secs = (recent_mins * 60) as u64;
    let now = SystemTime::now();
    Ok(read_dir(backup_folder)?
        .filter_map(|res| res.ok())
        .filter_map(|entry| entry.metadata().ok())
        .filter_map(|meta| meta.created().ok())
        .filter_map(|time| now.duration_since(time).ok())
        .any(|duration| duration.as_secs() < recent_secs))
}

fn backup_inner<P: AsRef<Path>>(
    col_data: &[u8],
    backup_folder: P,
    limits: BackupLimits,
    log: Logger,
    tr: &I18n,
) -> Result<()> {
    write_backup(col_data, backup_folder.as_ref(), tr)?;
    thin_backups(backup_folder, limits, &log)
}

fn write_backup<S: AsRef<OsStr>>(col_data: &[u8], backup_folder: S, tr: &I18n) -> Result<()> {
    let out_path =
        Path::new(&backup_folder).join(&format!("{}", Local::now().format(BACKUP_FORMAT_STRING)));
    export_colpkg_from_data(&out_path, col_data, tr)
}

fn thin_backups<P: AsRef<Path>>(
    backup_folder: P,
    limits: BackupLimits,
    log: &Logger,
) -> Result<()> {
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
    limits: BackupLimits,
    obsolete: Vec<Backup>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
enum BackupStage {
    Daily,
    Weekly,
    Monthly,
}

impl BackupFilter {
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
