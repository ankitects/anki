// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto::{NoteField as NoteFieldProto, NoteFieldConfig, OptionalUInt32};

#[derive(Debug)]
pub struct NoteField {
    pub ord: Option<u32>,
    pub name: String,
    pub config: NoteFieldConfig,
}

impl From<NoteField> for NoteFieldProto {
    fn from(f: NoteField) -> Self {
        NoteFieldProto {
            ord: f.ord.map(|n| OptionalUInt32 { val: n }),
            name: f.name,
            config: Some(f.config),
        }
    }
}

impl NoteField {
    pub fn new(name: impl Into<String>) -> Self {
        NoteField {
            ord: None,
            name: name.into(),
            config: NoteFieldConfig {
                sticky: false,
                rtl: false,
                font_name: "Arial".into(),
                font_size: 20,
                other: vec![],
            },
        }
    }
}
