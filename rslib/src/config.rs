// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::types::ObjID;
use serde_aux::field_attributes::deserialize_number_from_string;
use serde_derive::Deserialize;

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Config {
    #[serde(
        rename = "curDeck",
        deserialize_with = "deserialize_number_from_string"
    )]
    pub(crate) current_deck_id: ObjID,
    pub(crate) rollover: Option<i8>,
    pub(crate) creation_offset: Option<i32>,
    pub(crate) local_offset: Option<i32>,
    #[serde(rename = "sortType")]
    pub(crate) browser_sort_kind: SortKind,
    #[serde(rename = "sortBackwards", default)]
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
    CardMod,
    CardReps,
    CardDue,
    CardEase,
    CardLapses,
    #[serde(rename = "cardIvl")]
    CardInterval,
}

impl Default for SortKind {
    fn default() -> Self {
        Self::NoteCreation
    }
}
