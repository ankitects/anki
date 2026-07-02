// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use rusqlite::Row;

use crate::prelude::*;
use crate::sync::media::changes::MediaChange;
use crate::sync::media::database::server::ServerMediaDatabase;

impl MediaChange {
    fn from_row(row: &Row) -> Result<Self, rusqlite::Error> {
        Ok(Self {
            fname: row.get(0)?,
            usn: row.get(1)?,
            sha1: row.get(2)?,
        })
    }
}
impl ServerMediaDatabase {
    pub fn media_changes_chunk(&self, after_usn: Usn) -> Result<Vec<MediaChange>> {
        Ok(self
            .db
            .prepare_cached(include_str!("changes.sql"))?
            .query_map([after_usn], MediaChange::from_row)?
            .collect::<Result<_, _>>()?)
    }
}
