// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{decks::DeckID, serde::default_on_invalid};
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

#[derive(Serialize, Deserialize, Debug, Default, Clone)]
pub struct CardTemplate {
    pub(crate) name: String,
    pub(crate) ord: u16,
    pub(crate) qfmt: String,
    #[serde(default)]
    pub(crate) afmt: String,
    #[serde(default)]
    pub(crate) bqfmt: String,
    #[serde(default)]
    pub(crate) bafmt: String,
    #[serde(rename = "did", deserialize_with = "default_on_invalid")]
    pub(crate) override_did: Option<DeckID>,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) bfont: String,
    #[serde(default, deserialize_with = "default_on_invalid")]
    pub(crate) bsize: u8,
    #[serde(flatten)]
    pub(crate) other: HashMap<String, Value>,
}
