// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::import_export::text::ForeignData;
use crate::import_export::ImportProgress;
use crate::import_export::NoteLog;
use crate::io::read_file;
use crate::prelude::*;

impl Collection {
    pub fn import_json_file(
        &mut self,
        path: &str,
        mut progress_fn: impl 'static + FnMut(ImportProgress, bool) -> bool,
    ) -> Result<OpOutput<NoteLog>> {
        progress_fn(ImportProgress::Gathering, false);
        let slice = read_file(path)?;
        let data: ForeignData = serde_json::from_slice(&slice)?;
        data.import(self, progress_fn)
    }

    pub fn import_json_string(
        &mut self,
        json: &str,
        mut progress_fn: impl 'static + FnMut(ImportProgress, bool) -> bool,
    ) -> Result<OpOutput<NoteLog>> {
        progress_fn(ImportProgress::Gathering, false);
        let data: ForeignData = serde_json::from_str(json)?;
        data.import(self, progress_fn)
    }
}
