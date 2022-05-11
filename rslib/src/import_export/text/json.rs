// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    import_export::{text::ForeignData, NoteLog},
    prelude::*,
};

impl Collection {
    pub fn import_json_file(&mut self, path: &str) -> Result<OpOutput<NoteLog>> {
        let slice = std::fs::read(path)?;
        let data: ForeignData = serde_json::from_slice(&slice)?;
        data.import(self)
    }

    pub fn import_json_string(&mut self, json: &str) -> Result<OpOutput<NoteLog>> {
        let data: ForeignData = serde_json::from_str(json)?;
        data.import(self)
    }
}
