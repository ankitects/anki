// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod csv;
mod import;
mod json;

use serde_derive::{Deserialize, Serialize};

use crate::backend_proto::import_csv_request::DupeResolution;

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct ForeignData {
    dupe_resolution: DupeResolution,
    default_deck: String,
    default_notetype: String,
    notes: Vec<ForeignNote>,
    notetypes: Vec<ForeignNotetype>,
}

#[derive(Debug, Clone, PartialEq, Default, Serialize, Deserialize)]
#[serde(default)]
pub struct ForeignNote {
    fields: Vec<String>,
    tags: Vec<String>,
    notetype: String,
    deck: String,
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

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ForeignNotetype {
    name: String,
    fields: Vec<String>,
    templates: Vec<ForeignTemplate>,
    #[serde(default)]
    is_cloze: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ForeignTemplate {
    name: String,
    qfmt: String,
    afmt: String,
}
