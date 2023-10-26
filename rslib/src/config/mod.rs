// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod bool;
mod deck;
mod notetype;
mod number;
pub(crate) mod schema11;
mod string;
pub(crate) mod undo;

use anki_proto::config::preferences::BackupLimits;
use serde::de::DeserializeOwned;
use serde::Serialize;
use serde_repr::Deserialize_repr;
use serde_repr::Serialize_repr;
use strum::IntoStaticStr;

pub use self::bool::BoolKey;
pub use self::deck::DeckConfigKey;
pub use self::notetype::get_aux_notetype_config_key;
pub use self::number::I32ConfigKey;
pub use self::string::StringKey;
use crate::import_export::package::UpdateCondition;
use crate::prelude::*;

/// Only used when updating/undoing.
#[derive(Debug)]
pub(crate) struct ConfigEntry {
    pub key: String,
    pub value: Vec<u8>,
    pub usn: Usn,
    pub mtime: TimestampSecs,
}

impl ConfigEntry {
    pub(crate) fn boxed(key: &str, value: Vec<u8>, usn: Usn, mtime: TimestampSecs) -> Box<Self> {
        Box::new(Self {
            key: key.into(),
            value,
            usn,
            mtime,
        })
    }
}

#[derive(IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
pub(crate) enum ConfigKey {
    CreationOffset,
    FirstDayOfWeek,
    LocalOffset,
    Rollover,
    Backups,
    UpdateNotes,
    UpdateNotetypes,

    #[strum(to_string = "timeLim")]
    AnswerTimeLimitSecs,
    #[strum(to_string = "curDeck")]
    CurrentDeckId,
    #[strum(to_string = "curModel")]
    CurrentNotetypeId,
    #[strum(to_string = "lastUnburied")]
    LastUnburiedDay,
    #[strum(to_string = "collapseTime")]
    LearnAheadSecs,
    #[strum(to_string = "newSpread")]
    NewReviewMix,
    #[strum(to_string = "nextPos")]
    NextNewCardPosition,
    #[strum(to_string = "schedVer")]
    SchedulerVersion,
}

#[derive(PartialEq, Eq, Serialize_repr, Deserialize_repr, Clone, Copy, Debug)]
#[repr(u8)]
pub enum SchedulerVersion {
    V1 = 1,
    V2 = 2,
}

impl Collection {
    pub fn set_config_json<T: Serialize>(
        &mut self,
        key: &str,
        val: &T,
        undoable: bool,
    ) -> Result<OpOutput<()>> {
        let op = if undoable {
            Op::UpdateConfig
        } else {
            Op::SkipUndo
        };
        self.transact(op, |col| {
            col.set_config(key, val)?;
            Ok(())
        })
    }

    pub fn remove_config(&mut self, key: &str) -> Result<OpOutput<()>> {
        self.transact(Op::UpdateConfig, |col| col.remove_config_inner(key))
    }
}

impl Collection {
    /// Get config item, returning None if missing/invalid.
    pub(crate) fn get_config_optional<'a, T, K>(&self, key: K) -> Option<T>
    where
        T: DeserializeOwned,
        K: Into<&'a str>,
    {
        let key = key.into();
        match self.storage.get_config_value(key) {
            Ok(Some(val)) => Some(val),
            Ok(None) => None,
            // If the key is missing or invalid, we use the default value.
            Err(_) => None,
        }
    }

    // /// Get config item, returning default value if missing/invalid.
    pub(crate) fn get_config_default<'a, T, K>(&self, key: K) -> T
    where
        T: DeserializeOwned + Default,
        K: Into<&'a str>,
    {
        self.get_config_optional(key).unwrap_or_default()
    }

    /// True if added, or new value is different.
    pub(crate) fn set_config<'a, T: Serialize, K>(&mut self, key: K, val: &T) -> Result<bool>
    where
        K: Into<&'a str>,
    {
        let entry = ConfigEntry::boxed(
            key.into(),
            serde_json::to_vec(val)?,
            self.usn()?,
            TimestampSecs::now(),
        );
        self.set_config_undoable(entry)
    }

    pub(crate) fn remove_config_inner<'a, K>(&mut self, key: K) -> Result<()>
    where
        K: Into<&'a str>,
    {
        self.remove_config_undoable(key.into())
    }

    /// Remove all keys starting with provided prefix, which must end with '_'.
    pub(crate) fn remove_config_prefix(&mut self, key: &str) -> Result<()> {
        for (key, _val) in self.storage.get_config_prefix(key)? {
            self.remove_config_inner(key.as_str())?;
        }
        Ok(())
    }

    pub(crate) fn get_creation_utc_offset(&self) -> Option<i32> {
        self.get_config_optional(ConfigKey::CreationOffset)
    }

    pub(crate) fn set_creation_utc_offset(&mut self, mins: Option<i32>) -> Result<()> {
        self.state.scheduler_info = None;
        if let Some(mins) = mins {
            self.set_config(ConfigKey::CreationOffset, &mins)
                .map(|_| ())
        } else {
            self.remove_config_inner(ConfigKey::CreationOffset)
        }
    }

    /// In minutes west of UTC.
    pub fn get_configured_utc_offset(&self) -> Option<i32> {
        self.get_config_optional(ConfigKey::LocalOffset)
    }

    /// In minutes west of UTC.
    pub fn set_configured_utc_offset(&mut self, mins: i32) -> Result<()> {
        self.state.scheduler_info = None;
        self.set_config(ConfigKey::LocalOffset, &mins).map(|_| ())
    }

    pub(crate) fn get_v2_rollover(&self) -> Option<u8> {
        self.get_config_optional::<u8, _>(ConfigKey::Rollover)
            .map(|r| r.min(23))
    }

    pub(crate) fn set_v2_rollover(&mut self, hour: u32) -> Result<()> {
        self.state.scheduler_info = None;
        self.set_config(ConfigKey::Rollover, &hour).map(|_| ())
    }

    pub(crate) fn get_next_card_position(&self) -> u32 {
        self.get_config_default(ConfigKey::NextNewCardPosition)
    }

    pub(crate) fn get_and_update_next_card_position(&mut self) -> Result<u32> {
        let pos: u32 = self
            .get_config_optional(ConfigKey::NextNewCardPosition)
            .unwrap_or_default();
        self.set_config(ConfigKey::NextNewCardPosition, &pos.wrapping_add(1))?;
        Ok(pos)
    }

    pub(crate) fn set_next_card_position(&mut self, pos: u32) -> Result<()> {
        self.set_config(ConfigKey::NextNewCardPosition, &pos)
            .map(|_| ())
    }

    pub(crate) fn scheduler_version(&self) -> SchedulerVersion {
        self.get_config_optional(ConfigKey::SchedulerVersion)
            .unwrap_or(SchedulerVersion::V1)
    }

    pub fn v2_enabled(&self) -> bool {
        self.scheduler_version() == SchedulerVersion::V2
    }

    pub fn v3_enabled(&self) -> bool {
        self.scheduler_version() == SchedulerVersion::V2 && self.get_config_bool(BoolKey::Sched2021)
    }

    /// Caution: this only updates the config setting.
    pub(crate) fn set_scheduler_version_config_key(&mut self, ver: SchedulerVersion) -> Result<()> {
        self.state.scheduler_info = None;
        self.set_config(ConfigKey::SchedulerVersion, &ver)
            .map(|_| ())
    }

    pub(crate) fn learn_ahead_secs(&self) -> u32 {
        self.get_config_optional(ConfigKey::LearnAheadSecs)
            .unwrap_or(1200)
    }

    pub(crate) fn set_learn_ahead_secs(&mut self, secs: u32) -> Result<()> {
        self.set_config(ConfigKey::LearnAheadSecs, &secs)
            .map(|_| ())
    }

    pub(crate) fn get_new_review_mix(&self) -> NewReviewMix {
        match self.get_config_default::<u8, _>(ConfigKey::NewReviewMix) {
            1 => NewReviewMix::ReviewsFirst,
            2 => NewReviewMix::NewFirst,
            _ => NewReviewMix::Mix,
        }
    }

    pub(crate) fn set_new_review_mix(&mut self, mix: NewReviewMix) -> Result<()> {
        self.set_config(ConfigKey::NewReviewMix, &(mix as u8))
            .map(|_| ())
    }

    pub(crate) fn get_first_day_of_week(&self) -> Weekday {
        self.get_config_optional(ConfigKey::FirstDayOfWeek)
            .unwrap_or(Weekday::Sunday)
    }

    pub(crate) fn set_first_day_of_week(&mut self, weekday: Weekday) -> Result<()> {
        self.set_config(ConfigKey::FirstDayOfWeek, &weekday)
            .map(|_| ())
    }

    pub(crate) fn get_answer_time_limit_secs(&self) -> u32 {
        self.get_config_optional(ConfigKey::AnswerTimeLimitSecs)
            .unwrap_or_default()
    }

    pub(crate) fn set_answer_time_limit_secs(&mut self, secs: u32) -> Result<()> {
        self.set_config(ConfigKey::AnswerTimeLimitSecs, &secs)
            .map(|_| ())
    }

    pub(crate) fn get_last_unburied_day(&self) -> u32 {
        self.get_config_optional(ConfigKey::LastUnburiedDay)
            .unwrap_or_default()
    }

    pub(crate) fn set_last_unburied_day(&mut self, day: u32) -> Result<()> {
        self.set_config(ConfigKey::LastUnburiedDay, &day)
            .map(|_| ())
    }

    pub(crate) fn get_backup_limits(&self) -> BackupLimits {
        self.get_config_optional(ConfigKey::Backups).unwrap_or(
            // 2d + 12d + 10w + 9m ≈ 1y
            BackupLimits {
                daily: 12,
                weekly: 10,
                monthly: 9,
                minimum_interval_mins: 30,
            },
        )
    }

    pub(crate) fn set_backup_limits(&mut self, limits: BackupLimits) -> Result<()> {
        self.set_config(ConfigKey::Backups, &limits).map(|_| ())
    }

    pub(crate) fn get_update_notes(&self) -> UpdateCondition {
        self.get_config_optional(ConfigKey::UpdateNotes)
            .unwrap_or_default()
    }

    pub(crate) fn get_update_notetypes(&self) -> UpdateCondition {
        self.get_config_optional(ConfigKey::UpdateNotetypes)
            .unwrap_or_default()
    }
}

// 2021 scheduler moves this into deck config
#[derive(Default)]
pub(crate) enum NewReviewMix {
    #[default]
    Mix = 0,
    ReviewsFirst = 1,
    NewFirst = 2,
}

#[derive(PartialEq, Eq, Serialize_repr, Deserialize_repr, Clone, Copy)]
#[repr(u8)]
pub(crate) enum Weekday {
    Sunday = 0,
    Monday = 1,
    Friday = 5,
    Saturday = 6,
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn defaults() {
        let col = Collection::new();
        assert_eq!(col.get_current_deck_id(), DeckId(1));
    }

    #[test]
    fn get_set() {
        let mut col = Collection::new();

        // missing key
        assert_eq!(col.get_config_optional::<Vec<i64>, _>("test"), None);

        // normal retrieval
        col.set_config("test", &vec![1, 2]).unwrap();
        assert_eq!(
            col.get_config_optional::<Vec<i64>, _>("test"),
            Some(vec![1, 2])
        );

        // invalid type conversion
        assert_eq!(col.get_config_optional::<i64, _>("test"), None,);

        // invalid json
        col.storage
            .db
            .execute("update config set val=? where key='test'", [b"xx".as_ref()])
            .unwrap();
        assert_eq!(col.get_config_optional::<i64, _>("test"), None,);
    }
}
