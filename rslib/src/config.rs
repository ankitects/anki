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

pub(crate) enum ConfigKey {
    BrowserSortKind,
    BrowserSortReverse,
    CurrentDeckID,
    CreationOffset,
    Rollover,
    LocalOffset,
    CurrentNoteTypeID,
    NextNewCardPosition,
    SchedulerVersion,
    LearnAheadSecs,
    NormalizeNoteText,
    ShowRemainingDueCountsInStudy,
    ShowIntervalsAboveAnswerButtons,
    NewReviewMix,
    AnswerTimeLimitSecs,
    ShowDayLearningCardsFirst,
}
#[derive(PartialEq, Serialize_repr, Deserialize_repr, Clone, Copy)]
#[repr(u8)]
pub(crate) enum SchedulerVersion {
    V1 = 1,
    V2 = 2,
}

impl From<ConfigKey> for &'static str {
    fn from(c: ConfigKey) -> Self {
        match c {
            ConfigKey::BrowserSortKind => "sortType",
            ConfigKey::BrowserSortReverse => "sortBackwards",
            ConfigKey::CurrentDeckID => "curDeck",
            ConfigKey::CreationOffset => "creationOffset",
            ConfigKey::Rollover => "rollover",
            ConfigKey::LocalOffset => "localOffset",
            ConfigKey::CurrentNoteTypeID => "curModel",
            ConfigKey::NextNewCardPosition => "nextPos",
            ConfigKey::SchedulerVersion => "schedVer",
            ConfigKey::LearnAheadSecs => "collapseTime",
            ConfigKey::NormalizeNoteText => "normalize_note_text",
            ConfigKey::ShowRemainingDueCountsInStudy => "dueCounts",
            ConfigKey::ShowIntervalsAboveAnswerButtons => "estTimes",
            ConfigKey::NewReviewMix => "newSpread",
            ConfigKey::AnswerTimeLimitSecs => "timeLim",
            ConfigKey::ShowDayLearningCardsFirst => "dayLearnFirst",
        }
    }
}

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

    pub(crate) fn get_browser_sort_kind(&self) -> SortKind {
        self.get_config_default(ConfigKey::BrowserSortKind)
    }

    pub(crate) fn get_browser_sort_reverse(&self) -> bool {
        let b: BoolLike = self.get_config_default(ConfigKey::BrowserSortReverse);
        b.0
    }

    pub(crate) fn get_current_deck_id(&self) -> DeckID {
        self.get_config_optional(ConfigKey::CurrentDeckID)
            .unwrap_or(DeckID(1))
    }

    pub(crate) fn get_creation_mins_west(&self) -> Option<i32> {
        self.get_config_optional(ConfigKey::CreationOffset)
    }

    pub(crate) fn set_creation_mins_west(&self, mins: Option<i32>) -> Result<()> {
        if let Some(mins) = mins {
            self.set_config(ConfigKey::CreationOffset, &mins)
        } else {
            self.remove_config(ConfigKey::CreationOffset)
        }
    }

    pub(crate) fn get_local_mins_west(&self) -> Option<i32> {
        self.get_config_optional(ConfigKey::LocalOffset)
    }

    pub(crate) fn set_local_mins_west(&self, mins: i32) -> Result<()> {
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

    pub(crate) fn sched_ver(&self) -> SchedulerVersion {
        self.get_config_optional(ConfigKey::SchedulerVersion)
            .unwrap_or(SchedulerVersion::V1)
    }

    pub(crate) fn learn_ahead_secs(&self) -> u32 {
        self.get_config_optional(ConfigKey::LearnAheadSecs)
            .unwrap_or(1200)
    }

    pub(crate) fn set_learn_ahead_secs(&self, secs: u32) -> Result<()> {
        self.set_config(ConfigKey::LearnAheadSecs, &secs)
    }

    /// This is a stop-gap solution until we can decouple searching from canonical storage.
    pub(crate) fn normalize_note_text(&self) -> bool {
        self.get_config_optional(ConfigKey::NormalizeNoteText)
            .unwrap_or(true)
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

    pub(crate) fn get_show_due_counts(&self) -> bool {
        self.get_config_optional(ConfigKey::ShowRemainingDueCountsInStudy)
            .unwrap_or(true)
    }

    pub(crate) fn set_show_due_counts(&self, on: bool) -> Result<()> {
        self.set_config(ConfigKey::ShowRemainingDueCountsInStudy, &on)
    }

    pub(crate) fn get_show_intervals_above_buttons(&self) -> bool {
        self.get_config_optional(ConfigKey::ShowIntervalsAboveAnswerButtons)
            .unwrap_or(true)
    }

    pub(crate) fn set_show_intervals_above_buttons(&self, on: bool) -> Result<()> {
        self.set_config(ConfigKey::ShowIntervalsAboveAnswerButtons, &on)
    }

    pub(crate) fn get_answer_time_limit_secs(&self) -> u32 {
        self.get_config_optional(ConfigKey::AnswerTimeLimitSecs)
            .unwrap_or_default()
    }

    pub(crate) fn set_answer_time_limit_secs(&self, secs: u32) -> Result<()> {
        self.set_config(ConfigKey::AnswerTimeLimitSecs, &secs)
    }

    pub(crate) fn get_day_learn_first(&self) -> bool {
        self.get_config_optional(ConfigKey::ShowDayLearningCardsFirst)
            .unwrap_or_default()
    }

    pub(crate) fn set_day_learn_first(&self, on: bool) -> Result<()> {
        self.set_config(ConfigKey::ShowDayLearningCardsFirst, &on)
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

pub(crate) enum NewReviewMix {
    Mix = 0,
    ReviewsFirst = 1,
    NewFirst = 2,
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
