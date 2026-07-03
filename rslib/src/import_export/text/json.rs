// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_io::read_file;

use crate::import_export::text::ForeignData;
use crate::import_export::NoteLog;
use crate::prelude::*;

impl Collection {
    pub fn import_json_file(&mut self, path: &str) -> Result<OpOutput<NoteLog>> {
        let progress = self.new_progress_handler();
        let slice = read_file(path)?;
        let data: ForeignData = serde_json::from_slice(&slice)?;
        data.import(self, progress)
    }

    pub fn import_json_string(&mut self, json: &str) -> Result<OpOutput<NoteLog>> {
        let progress = self.new_progress_handler();
        let data: ForeignData = serde_json::from_str(json)?;
        data.import(self, progress)
    }
}
