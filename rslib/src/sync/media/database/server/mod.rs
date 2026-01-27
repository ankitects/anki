// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod entry;
pub mod meta;

use std::path::Path;

use rusqlite::Connection;

use crate::prelude::*;
use crate::sync::media::SYNC_DB_BUSY_TIMEOUT_SECS;

pub struct ServerMediaDatabase {
    pub db: Connection,
}

impl ServerMediaDatabase {
    pub fn new(path: &Path) -> Result<Self> {
        Ok(Self {
            db: open_or_create_db(path)?,
        })
    }
}

fn open_or_create_db(path: &Path) -> Result<Connection> {
    let db = Connection::open(path)?;
    db.busy_timeout(std::time::Duration::from_secs(*SYNC_DB_BUSY_TIMEOUT_SECS))?;
    db.pragma_update(None, "locking_mode", "exclusive")?;
    db.pragma_update(None, "journal_mode", "wal")?;
    let ver: u32 = db.query_row("select user_version from pragma_user_version", [], |r| {
        r.get(0)
    })?;
    if ver < 3 {
        db.execute_batch(include_str!("schema_v3.sql"))?;
    }
    if ver < 4 {
        db.execute_batch(include_str!("schema_v4.sql"))?;
    }
    Ok(db)
}
