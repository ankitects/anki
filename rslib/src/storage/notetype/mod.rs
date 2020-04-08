// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::{
    err::{AnkiError, DBErrorKind, Result},
    notetype::{NoteType, NoteTypeID},
    timestamp::TimestampSecs,
    types::Usn,
};
use rusqlite::NO_PARAMS;
use std::collections::HashMap;

impl SqliteStorage {
    pub(crate) fn get_all_notetypes(&self) -> Result<HashMap<NoteTypeID, NoteType>> {
        let mut stmt = self.db.prepare("select models from col")?;
        let note_types = stmt
            .query_and_then(NO_PARAMS, |row| -> Result<HashMap<NoteTypeID, NoteType>> {
                let v: HashMap<NoteTypeID, NoteType> =
                    serde_json::from_str(row.get_raw(0).as_str()?)?;
                Ok(v)
            })?
            .next()
            .ok_or_else(|| AnkiError::DBError {
                info: "col table empty".to_string(),
                kind: DBErrorKind::MissingEntity,
            })??;
        Ok(note_types)
    }

    pub(crate) fn set_all_notetypes(
        &self,
        notetypes: HashMap<NoteTypeID, NoteType>,
        _usn: Usn,
        _mtime: TimestampSecs,
    ) -> Result<()> {
        let json = serde_json::to_string(&notetypes)?;
        self.db.execute("update col set models = ?", &[json])?;
        Ok(())
    }
}
