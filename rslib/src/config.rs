// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::decks::DeckID;
use serde::Deserialize as DeTrait;
use serde_aux::field_attributes::deserialize_number_from_string;
use serde_derive::Deserialize;
use serde_json::Value;

pub(crate) fn default_on_invalid<'de, T, D>(deserializer: D) -> Result<T, D::Error>
where
    T: Default + DeTrait<'de>,
    D: serde::de::Deserializer<'de>,
{
    let v: Value = DeTrait::deserialize(deserializer)?;
    Ok(T::deserialize(v).unwrap_or_default())
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Config {
    #[serde(
        rename = "curDeck",
        deserialize_with = "deserialize_number_from_string"
    )]
    pub(crate) current_deck_id: DeckID,
    pub(crate) rollover: Option<i8>,
    pub(crate) creation_offset: Option<i32>,
    pub(crate) local_offset: Option<i32>,
    #[serde(rename = "sortType", deserialize_with = "default_on_invalid")]
    pub(crate) browser_sort_kind: SortKind,
    #[serde(rename = "sortBackwards", deserialize_with = "default_on_invalid")]
    pub(crate) browser_sort_reverse: bool,
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
