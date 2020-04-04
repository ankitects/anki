// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::{AnkiError, Result};
use crate::i18n::I18n;
use crate::log::Logger;
use crate::timestamp::TimestampSecs;
use crate::types::Usn;
use crate::{sched::cutoff::SchedTimingToday, storage::SqliteStorage, undo::UndoManager};
use std::path::PathBuf;

pub fn open_collection<P: Into<PathBuf>>(
    path: P,
    media_folder: P,
    media_db: P,
    server: bool,
    i18n: I18n,
    log: Logger,
) -> Result<Collection> {
    let col_path = path.into();
    let storage = SqliteStorage::open_or_create(&col_path, &i18n)?;

    let col = Collection {
        storage,
        col_path,
        media_folder: media_folder.into(),
        media_db: media_db.into(),
        i18n,
        log,
        server,
        state: CollectionState::default(),
    };

    Ok(col)
}

#[cfg(test)]
pub fn open_test_collection() -> Collection {
    use crate::log;
    let i18n = I18n::new(&[""], "", log::terminal());
    open_collection(":memory:", "", "", false, i18n, log::terminal()).unwrap()
}

#[derive(Debug, Default)]
pub struct CollectionState {
    task_state: CollectionTaskState,
    pub(crate) undo: UndoManager,
    timing_today: Option<SchedTimingToday>,
}

#[derive(Debug, PartialEq)]
pub enum CollectionTaskState {
    Normal,
    // in this state, the DB must not be closed
    MediaSyncRunning,
}

impl Default for CollectionTaskState {
    fn default() -> Self {
        Self::Normal
    }
}

pub struct Collection {
    pub(crate) storage: SqliteStorage,
    #[allow(dead_code)]
    pub(crate) col_path: PathBuf,
    pub(crate) media_folder: PathBuf,
    pub(crate) media_db: PathBuf,
    pub(crate) i18n: I18n,
    pub(crate) log: Logger,
    pub(crate) server: bool,
    pub(crate) state: CollectionState,
}

#[derive(Debug, Clone, PartialEq)]
pub enum CollectionOp {
    UpdateCard,
}

impl Collection {
    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned.
    pub(crate) fn transact<F, R>(&mut self, op: Option<CollectionOp>, func: F) -> Result<R>
    where
        F: FnOnce(&mut Collection) -> Result<R>,
    {
        self.storage.begin_rust_trx()?;
        self.state.undo.begin_step(op);

        let mut res = func(self);

        if res.is_ok() {
            if let Err(e) = self.storage.mark_modified() {
                res = Err(e);
            } else if let Err(e) = self.storage.commit_rust_trx() {
                res = Err(e);
            }
        }

        if res.is_err() {
            self.state.undo.discard_step();
            self.storage.rollback_rust_trx()?;
        } else {
            self.state.undo.end_step();
        }

        res
    }

    pub(crate) fn set_media_sync_running(&mut self) -> Result<()> {
        if self.state.task_state == CollectionTaskState::Normal {
            self.state.task_state = CollectionTaskState::MediaSyncRunning;
            Ok(())
        } else {
            Err(AnkiError::invalid_input("media sync already running"))
        }
    }

    pub(crate) fn set_media_sync_finished(&mut self) -> Result<()> {
        if self.state.task_state == CollectionTaskState::MediaSyncRunning {
            self.state.task_state = CollectionTaskState::Normal;
            Ok(())
        } else {
            Err(AnkiError::invalid_input("media sync not running"))
        }
    }

    pub(crate) fn can_close(&self) -> bool {
        self.state.task_state == CollectionTaskState::Normal
    }

    pub(crate) fn close(self, downgrade: bool) -> Result<()> {
        self.storage.close(downgrade)
    }

    // fixme: invalidate when config changes
    pub fn timing_today(&mut self) -> Result<SchedTimingToday> {
        if let Some(timing) = &self.state.timing_today {
            if timing.next_day_at > TimestampSecs::now().0 {
                return Ok(*timing);
            }
        }
        self.state.timing_today = Some(self.storage.timing_today(self.server)?);
        Ok(self.state.timing_today.clone().unwrap())
    }

    pub(crate) fn usn(&self) -> Result<Usn> {
        // if we cache this in the future, must make sure to invalidate cache when usn bumped in sync.finish()
        self.storage.usn(self.server)
    }

    pub(crate) fn ensure_schema_modified(&self) -> Result<()> {
        if !self.storage.schema_modified()? {
            Err(AnkiError::SchemaChange)
        } else {
            Ok(())
        }
    }

    pub(crate) fn before_upload(&self) -> Result<()> {
        self.storage.clear_tag_usns()?;
        self.storage.clear_deck_conf_usns()?;

        Ok(())
    }
}
