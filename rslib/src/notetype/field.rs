// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto::{NoteField as NoteFieldProto, NoteFieldConfig},
    serde::deserialize_bool_from_anything,
};
use serde_derive::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct NoteFieldSchema11 {
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

impl Default for NoteFieldSchema11 {
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

impl From<NoteFieldSchema11> for NoteFieldProto {
    fn from(f: NoteFieldSchema11) -> Self {
        NoteFieldProto {
            ord: f.ord as u32,
            name: f.name,
            config: Some(NoteFieldConfig {
                sticky: f.sticky,
                rtl: f.rtl,
                font_name: f.font,
                font_size: f.size as u32,
                other: serde_json::to_vec(&f.other).unwrap(),
            }),
        }
    }
}

impl From<NoteFieldProto> for NoteFieldSchema11 {
    fn from(p: NoteFieldProto) -> Self {
        let conf = p.config.unwrap();
        NoteFieldSchema11 {
            name: p.name,
            ord: p.ord as u16,
            sticky: conf.sticky,
            rtl: conf.rtl,
            font: conf.font_name,
            size: conf.font_size as u16,
            other: serde_json::from_slice(&conf.other).unwrap(),
        }
    }
}
