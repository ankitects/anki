// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod csv;
mod import;
mod json;

use serde_derive::Deserialize;
use serde_derive::Serialize;

use super::LogNote;
use crate::pb::import_export::csv_metadata::DupeResolution;
use crate::pb::import_export::csv_metadata::MatchScope;

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct ForeignData {
    dupe_resolution: DupeResolution,
    match_scope: MatchScope,
    default_deck: NameOrId,
    default_notetype: NameOrId,
    notes: Vec<ForeignNote>,
    notetypes: Vec<ForeignNotetype>,
    global_tags: Vec<String>,
    updated_tags: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct ForeignNote {
    guid: String,
    fields: Vec<Option<String>>,
    tags: Option<Vec<String>>,
    notetype: NameOrId,
    deck: NameOrId,
    cards: Vec<ForeignCard>,
}

#[derive(Debug, Clone, Copy, PartialEq, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct ForeignCard {
    pub due: i32,
    pub interval: u32,
    pub ease_factor: f32,
    pub reps: u32,
    pub lapses: u32,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ForeignNotetype {
    name: String,
    fields: Vec<String>,
    templates: Vec<ForeignTemplate>,
    #[serde(default)]
    is_cloze: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ForeignTemplate {
    name: String,
    qfmt: String,
    afmt: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(untagged)]
pub enum NameOrId {
    Id(i64),
    Name(String),
}

impl Default for NameOrId {
    fn default() -> Self {
        NameOrId::Name(String::new())
    }
}

impl From<String> for NameOrId {
    fn from(s: String) -> Self {
        Self::Name(s)
    }
}

impl ForeignNote {
    pub(crate) fn into_log_note(self) -> LogNote {
        LogNote {
            id: None,
            fields: self
                .fields
                .into_iter()
                .map(Option::unwrap_or_default)
                .collect(),
        }
    }
}
