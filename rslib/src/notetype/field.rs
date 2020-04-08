// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::serde::deserialize_bool_from_anything;
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct NoteField {
    pub(crate) name: String,
    pub(crate) ord: u16,
    #[serde(deserialize_with = "deserialize_bool_from_anything")]
    pub(crate) sticky: bool,
    #[serde(deserialize_with = "deserialize_bool_from_anything")]
    pub(crate) rtl: bool,
    pub(crate) font: String,
    pub(crate) size: u16,
    #[serde(flatten)]
    pub(crate) other: HashMap<String, Value>,
}

impl Default for NoteField {
    fn default() -> Self {
        Self {
            name: String::new(),
            ord: 0,
            sticky: false,
            rtl: false,
            font: "Arial".to_string(),
            size: 20,
            other: Default::default(),
        }
    }
}
