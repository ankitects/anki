// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    collection::Collection, decks::DeckID, err::Result, notetype::NoteTypeID,
    timestamp::TimestampSecs,
};
use serde::{de::DeserializeOwned, Serialize};
use serde_aux::field_attributes::deserialize_bool_from_anything;
use serde_derive::Deserialize;
use serde_json::json;
use serde_repr::{Deserialize_repr, Serialize_repr};
use slog::warn;
use strum::IntoStaticStr;

/// These items are expected to exist in schema 11. When adding
/// new config variables, you do not need to add them here -
/// just create an accessor function below with an appropriate
/// default on missing/invalid values instead.
pub(crate) fn schema11_config_as_string() -> String {
    let obj = json!({
        "activeDecks": [1],
        "curDeck": 1,
        "newSpread": 0,
        "collapseTime": 1200,
        "timeLim": 0,
        "estTimes": true,
        "dueCounts": true,
        "curModel": null,
        "nextPos": 1,
        "sortType": "noteFld",
        "sortBackwards": false,
        "addToCur": true,
        "dayLearnFirst": false,
        "schedVer": 1,
    });
    serde_json::to_string(&obj).unwrap()
}

#[derive(IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
pub(crate) enum ConfigKey {
    CreationOffset,
    FirstDayOfWeek,
    LocalOffset,
    Rollover,

    #[strum(to_string = "timeLim")]
    AnswerTimeLimitSecs,
    #[strum(to_string = "sortType")]
    BrowserSortKind,
    #[strum(to_string = "curDeck")]
    CurrentDeckID,
    #[strum(to_string = "curModel")]
    CurrentNoteTypeID,
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

#[derive(Debug, Clone, Copy, IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
pub enum BoolKey {
    CardCountsSeparateInactive,
    CollapseCardState,
    CollapseDecks,
    CollapseFlags,
    CollapseNotetypes,
    CollapseSavedSearches,
    CollapseTags,
    CollapseToday,
    FutureDueShowBacklog,
    PreviewBothSides,
    Sched2021,

    #[strum(to_string = "sortBackwards")]
    BrowserSortBackwards,
    #[strum(to_string = "normalize_note_text")]
    NormalizeNoteText,
    #[strum(to_string = "dayLearnFirst")]
    ShowDayLearningCardsFirst,
    #[strum(to_string = "estTimes")]
    ShowIntervalsAboveAnswerButtons,
    #[strum(to_string = "dueCounts")]
    ShowRemainingDueCountsInStudy,
}

#[derive(Debug, Clone, Copy, IntoStaticStr)]
#[strum(serialize_all = "camelCase")]
pub enum StringKey {
    SetDueBrowser,
    SetDueReviewer,
}

#[derive(PartialEq, Serialize_repr, Deserialize_repr, Clone, Copy, Debug)]
#[repr(u8)]
pub(crate) enum SchedulerVersion {
    V1 = 1,
    V2 = 2,
}
/// This is a workaround for old clients that used ints to represent boolean
/// values. For new config items, prefer using a bool directly.
#[derive(Deserialize, Default)]
struct BoolLike(#[serde(deserialize_with = "deserialize_bool_from_anything")] bool);

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
            Err(e) => {
                warn!(self.log, "error accessing config key"; "key"=>key, "err"=>?e);
                None
            }
        }
    }

    // /// Get config item, returning default value if missing/invalid.
    pub(crate) fn get_config_default<T, K>(&self, key: K) -> T
    where
        T: DeserializeOwned + Default,
        K: Into<&'static str>,
    {
        self.get_config_optional(key).unwrap_or_default()
    }

    pub(crate) fn set_config<'a, T: Serialize, K>(&self, key: K, val: &T) -> Result<()>
    where
        K: Into<&'a str>,
    {
        self.storage
            .set_config_value(key.into(), val, self.usn()?, TimestampSecs::now())
    }

    pub(crate) fn remove_config<'a, K>(&self, key: K) -> Result<()>
    where
        K: Into<&'a str>,
    {
        self.storage.remove_config(key.into())
    }

    pub(crate) fn get_bool(&self, key: BoolKey) -> bool {
        match key {
            BoolKey::BrowserSortBackwards => {
                // older clients were storing this as an int
                self.get_config_default::<BoolLike, _>(BoolKey::BrowserSortBackwards)
                    .0
            }

            // some keys default to true
            BoolKey::FutureDueShowBacklog
            | BoolKey::ShowRemainingDueCountsInStudy
            | BoolKey::CardCountsSeparateInactive
            | BoolKey::NormalizeNoteText => self.get_config_optional(key).unwrap_or(true),

            // other options default to false
            other => self.get_config_default(other),
        }
    }

    pub(crate) fn set_bool(&self, key: BoolKey, value: bool) -> Result<()> {
        self.set_config(key, &value)
    }

    pub(crate) fn get_string(&self, key: StringKey) -> String {
        let default = match key {
            StringKey::SetDueBrowser => "0",
            StringKey::SetDueReviewer => "1",
            // other => "",
        };
        self.get_config_optional(key)
            .unwrap_or_else(|| default.to_string())
    }

    pub(crate) fn set_string(&self, key: StringKey, val: &str) -> Result<()> {
        self.set_config(key, &val)
    }

    pub(crate) fn get_browser_sort_kind(&self) -> SortKind {
        self.get_config_default(ConfigKey::BrowserSortKind)
    }

    pub(crate) fn get_current_deck_id(&self) -> DeckID {
        self.get_config_optional(ConfigKey::CurrentDeckID)
            .unwrap_or(DeckID(1))
    }

    pub(crate) fn get_creation_utc_offset(&self) -> Option<i32> {
        self.get_config_optional(ConfigKey::CreationOffset)
    }

    pub(crate) fn set_creation_utc_offset(&self, mins: Option<i32>) -> Result<()> {
        if let Some(mins) = mins {
            self.set_config(ConfigKey::CreationOffset, &mins)
        } else {
            self.remove_config(ConfigKey::CreationOffset)
        }
    }

    pub(crate) fn get_configured_utc_offset(&self) -> Option<i32> {
        self.get_config_optional(ConfigKey::LocalOffset)
    }

    pub(crate) fn set_configured_utc_offset(&self, mins: i32) -> Result<()> {
        self.set_config(ConfigKey::LocalOffset, &mins)
    }

    pub(crate) fn get_v2_rollover(&self) -> Option<u8> {
        self.get_config_optional::<u8, _>(ConfigKey::Rollover)
            .map(|r| r.min(23))
    }

    pub(crate) fn set_v2_rollover(&self, hour: u32) -> Result<()> {
        self.set_config(ConfigKey::Rollover, &hour)
    }

    #[allow(dead_code)]
    pub(crate) fn get_current_notetype_id(&self) -> Option<NoteTypeID> {
        self.get_config_optional(ConfigKey::CurrentNoteTypeID)
    }

    pub(crate) fn set_current_notetype_id(&self, id: NoteTypeID) -> Result<()> {
        self.set_config(ConfigKey::CurrentNoteTypeID, &id)
    }

    pub(crate) fn get_next_card_position(&self) -> u32 {
        self.get_config_default(ConfigKey::NextNewCardPosition)
    }

    pub(crate) fn get_and_update_next_card_position(&self) -> Result<u32> {
        let pos: u32 = self
            .get_config_optional(ConfigKey::NextNewCardPosition)
            .unwrap_or_default();
        self.set_config(ConfigKey::NextNewCardPosition, &pos.wrapping_add(1))?;
        Ok(pos)
    }

    pub(crate) fn set_next_card_position(&self, pos: u32) -> Result<()> {
        self.set_config(ConfigKey::NextNewCardPosition, &pos)
    }

    pub(crate) fn scheduler_version(&self) -> SchedulerVersion {
        self.get_config_optional(ConfigKey::SchedulerVersion)
            .unwrap_or(SchedulerVersion::V1)
    }

    /// Caution: this only updates the config setting.
    pub(crate) fn set_scheduler_version_config_key(&self, ver: SchedulerVersion) -> Result<()> {
        self.set_config(ConfigKey::SchedulerVersion, &ver)
    }

    pub(crate) fn learn_ahead_secs(&self) -> u32 {
        self.get_config_optional(ConfigKey::LearnAheadSecs)
            .unwrap_or(1200)
    }

    pub(crate) fn set_learn_ahead_secs(&self, secs: u32) -> Result<()> {
        self.set_config(ConfigKey::LearnAheadSecs, &secs)
    }

    pub(crate) fn get_new_review_mix(&self) -> NewReviewMix {
        match self.get_config_default::<u8, _>(ConfigKey::NewReviewMix) {
            1 => NewReviewMix::ReviewsFirst,
            2 => NewReviewMix::NewFirst,
            _ => NewReviewMix::Mix,
        }
    }

    pub(crate) fn set_new_review_mix(&self, mix: NewReviewMix) -> Result<()> {
        self.set_config(ConfigKey::NewReviewMix, &(mix as u8))
    }

    pub(crate) fn get_first_day_of_week(&self) -> Weekday {
        self.get_config_optional(ConfigKey::FirstDayOfWeek)
            .unwrap_or(Weekday::Sunday)
    }

    pub(crate) fn set_first_day_of_week(&self, weekday: Weekday) -> Result<()> {
        self.set_config(ConfigKey::FirstDayOfWeek, &weekday)
    }

    pub(crate) fn get_answer_time_limit_secs(&self) -> u32 {
        self.get_config_optional(ConfigKey::AnswerTimeLimitSecs)
            .unwrap_or_default()
    }

    pub(crate) fn set_answer_time_limit_secs(&self, secs: u32) -> Result<()> {
        self.set_config(ConfigKey::AnswerTimeLimitSecs, &secs)
    }

    pub(crate) fn get_last_unburied_day(&self) -> u32 {
        self.get_config_optional(ConfigKey::LastUnburiedDay)
            .unwrap_or_default()
    }

    pub(crate) fn set_last_unburied_day(&self, day: u32) -> Result<()> {
        self.set_config(ConfigKey::LastUnburiedDay, &day)
    }
}

#[derive(Deserialize, PartialEq, Debug, Clone, Copy)]
#[serde(rename_all = "camelCase")]
pub enum SortKind {
    #[serde(rename = "noteCrt")]
    NoteCreation,
    NoteMod,
    #[serde(rename = "noteFld")]
    NoteField,
    #[serde(rename = "note")]
    NoteType,
    NoteTags,
    CardMod,
    CardReps,
    CardDue,
    CardEase,
    CardLapses,
    #[serde(rename = "cardIvl")]
    CardInterval,
    #[serde(rename = "deck")]
    CardDeck,
    #[serde(rename = "template")]
    CardTemplate,
}

impl Default for SortKind {
    fn default() -> Self {
        Self::NoteCreation
    }
}

// 2021 scheduler moves this into deck config
pub(crate) enum NewReviewMix {
    Mix = 0,
    ReviewsFirst = 1,
    NewFirst = 2,
}

impl Default for NewReviewMix {
    fn default() -> Self {
        NewReviewMix::Mix
    }
}

#[derive(PartialEq, Serialize_repr, Deserialize_repr, Clone, Copy)]
#[repr(u8)]
pub(crate) enum Weekday {
    Sunday = 0,
    Monday = 1,
    Friday = 5,
    Saturday = 6,
}

#[cfg(test)]
mod test {
    use super::SortKind;
    use crate::collection::open_test_collection;
    use crate::decks::DeckID;

    #[test]
    fn defaults() {
        let col = open_test_collection();
        assert_eq!(col.get_current_deck_id(), DeckID(1));
        assert_eq!(col.get_browser_sort_kind(), SortKind::NoteField);
    }

    #[test]
    fn get_set() {
        let col = open_test_collection();

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
            .execute(
                "update config set val=? where key='test'",
                &[b"xx".as_ref()],
            )
            .unwrap();
        assert_eq!(col.get_config_optional::<i64, _>("test"), None,);
    }
}
