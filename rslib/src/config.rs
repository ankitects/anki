// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::collection::Collection;
use crate::decks::DeckID;
use crate::err::Result;
use crate::timestamp::TimestampSecs;
use serde::{de::DeserializeOwned, Serialize};
use serde_aux::field_attributes::deserialize_bool_from_anything;
use serde_derive::Deserialize;
use serde_json::json;
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

    pub(crate) fn set_config<T: Serialize>(&self, key: &str, val: &T) -> Result<()> {
        self.storage
            .set_config_value(key, val, self.usn()?, TimestampSecs::now())
    }

    pub(crate) fn remove_config(&self, key: &str) -> Result<()> {
        self.storage.remove_config(key)
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

    pub(crate) fn get_local_mins_west(&self) -> Option<i32> {
        self.get_config_optional(ConfigKey::LocalOffset)
    }

    pub(crate) fn get_rollover(&self) -> Option<i8> {
        self.get_config_optional(ConfigKey::Rollover)
    }
}

#[derive(Deserialize, PartialEq, Debug)]
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
