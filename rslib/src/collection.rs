// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::{AnkiError, Result};
use crate::i18n::I18n;
use crate::log::Logger;
use crate::storage::SqliteStorage;
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
    let storage = SqliteStorage::open_or_create(&col_path, server)?;

    let col = Collection {
        storage,
        col_path,
        media_folder: media_folder.into(),
        media_db: media_db.into(),
        i18n,
        log,
        state: CollectionState {
            task_state: CollectionTaskState::Normal,
        },
    };

    Ok(col)
}

#[derive(Debug)]
pub struct CollectionState {
    task_state: CollectionTaskState,
}

#[derive(Debug, PartialEq)]
pub enum CollectionTaskState {
    Normal,
    // in this state, the DB must not be closed
    MediaSyncRunning,
}

pub struct Collection {
    pub(crate) storage: SqliteStorage,
    #[allow(dead_code)]
    pub(crate) col_path: PathBuf,
    pub(crate) media_folder: PathBuf,
    pub(crate) media_db: PathBuf,
    pub(crate) i18n: I18n,
    pub(crate) log: Logger,
    state: CollectionState,
}

pub(crate) enum CollectionOp {}

impl Collection {
    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned.
    pub(crate) fn transact<F, R>(&mut self, op: Option<CollectionOp>, func: F) -> Result<R>
    where
        F: FnOnce(&mut Collection) -> Result<R>,
    {
        self.storage.begin_rust_trx()?;

        let mut res = func(self);

        if res.is_ok() {
            if let Err(e) = self.storage.mark_modified() {
                res = Err(e);
            } else if let Err(e) = self.storage.commit_rust_op(op) {
                res = Err(e);
            }
        }

        if res.is_err() {
            self.storage.rollback_rust_trx()?;
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
}
