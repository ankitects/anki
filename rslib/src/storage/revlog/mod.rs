// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::err::Result;
use rusqlite::NO_PARAMS;

impl SqliteStorage {
    pub(crate) fn fix_revlog_properties(&self) -> Result<usize> {
        self.db
            .prepare(include_str!("fix_props.sql"))?
            .execute(NO_PARAMS)
            .map_err(Into::into)
    }

    pub(crate) fn clear_pending_revlog_usns(&self) -> Result<()> {
        self.db
            .prepare("update revlog set usn = 0 where usn = -1")?
            .execute(NO_PARAMS)?;
        Ok(())
    }
}
