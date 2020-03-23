// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::err::{AnkiError, Result};
use crate::i18n::I18n;
use crate::log::Logger;
use crate::storage::{SqliteStorage, StorageContext};
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
    let storage = SqliteStorage::open_or_create(&col_path)?;

    let col = Collection {
        storage,
        col_path,
        media_folder: media_folder.into(),
        media_db: media_db.into(),
        server,
        i18n,
        log,
        state: CollectionState::Normal,
    };

    Ok(col)
}

#[derive(Debug, PartialEq)]
pub enum CollectionState {
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
    pub(crate) server: bool,
    pub(crate) i18n: I18n,
    pub(crate) log: Logger,
    state: CollectionState,
}

pub(crate) enum CollectionOp {}

pub(crate) struct RequestContext<'a> {
    pub storage: StorageContext<'a>,
    pub i18n: &'a I18n,
    pub log: &'a Logger,
    pub should_commit: bool,
}

impl Collection {
    /// Call the provided closure with a RequestContext that exists for
    /// the duration of the call. The request will cache prepared sql
    /// statements, so should be passed down the call tree.
    ///
    /// This function should be used for read-only requests. To mutate
    /// the database, use transact() instead.
    pub(crate) fn with_ctx<F, R>(&self, func: F) -> Result<R>
    where
        F: FnOnce(&mut RequestContext) -> Result<R>,
    {
        let mut ctx = RequestContext {
            storage: self.storage.context(self.server),
            i18n: &self.i18n,
            log: &self.log,
            should_commit: true,
        };
        func(&mut ctx)
    }

    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned.
    pub(crate) fn transact<F, R>(&self, op: Option<CollectionOp>, func: F) -> Result<R>
    where
        F: FnOnce(&mut RequestContext) -> Result<R>,
    {
        self.with_ctx(|ctx| {
            ctx.storage.begin_rust_trx()?;

            let mut res = func(ctx);

            if res.is_ok() && ctx.should_commit {
                if let Err(e) = ctx.storage.mark_modified() {
                    res = Err(e);
                } else if let Err(e) = ctx.storage.commit_rust_op(op) {
                    res = Err(e);
                }
            }

            if res.is_err() || !ctx.should_commit {
                ctx.storage.rollback_rust_trx()?;
            }

            res
        })
    }

    pub(crate) fn set_media_sync_running(&mut self) -> Result<()> {
        if self.state == CollectionState::Normal {
            self.state = CollectionState::MediaSyncRunning;
            Ok(())
        } else {
            Err(AnkiError::invalid_input("media sync already running"))
        }
    }

    pub(crate) fn set_media_sync_finished(&mut self) -> Result<()> {
        if self.state == CollectionState::MediaSyncRunning {
            self.state = CollectionState::Normal;
            Ok(())
        } else {
            Err(AnkiError::invalid_input("media sync not running"))
        }
    }

    pub(crate) fn can_close(&self) -> bool {
        self.state == CollectionState::Normal
    }
}
