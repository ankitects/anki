// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::log::Logger;
use crate::types::Usn;
use crate::{
    decks::{Deck, DeckID},
    notetype::{NoteType, NoteTypeID},
    prelude::*,
    storage::SqliteStorage,
    undo::UndoManager,
};
use crate::{err::Result, scheduler::queue::CardQueues};
use crate::{i18n::I18n, ops::StateChanges};
use std::{collections::HashMap, path::PathBuf, sync::Arc};

pub fn open_collection<P: Into<PathBuf>>(
    path: P,
    media_folder: P,
    media_db: P,
    server: bool,
    i18n: I18n,
    log: Logger,
) -> Result<Collection> {
    let col_path = path.into();
    let storage = SqliteStorage::open_or_create(&col_path, &i18n, server)?;

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

// We need to make a Builder for Collection in the future.

#[cfg(test)]
pub fn open_test_collection() -> Collection {
    use crate::config::SchedulerVersion;
    let mut col = open_test_collection_with_server(false);
    // our unit tests assume v2 is the default, but at the time of writing v1
    // is still the default
    col.set_scheduler_version_config_key(SchedulerVersion::V2)
        .unwrap();
    col
}

#[cfg(test)]
pub fn open_test_collection_with_server(server: bool) -> Collection {
    use crate::log;
    let i18n = I18n::new(&[""], "", log::terminal());
    open_collection(":memory:", "", "", server, i18n, log::terminal()).unwrap()
}

#[derive(Debug, Default)]
pub struct CollectionState {
    pub(crate) undo: UndoManager,
    pub(crate) notetype_cache: HashMap<NoteTypeID, Arc<NoteType>>,
    pub(crate) deck_cache: HashMap<DeckID, Arc<Deck>>,
    pub(crate) card_queues: Option<CardQueues>,
    /// True if legacy Python code has executed SQL that has modified the
    /// database, requiring modification time to be bumped.
    pub(crate) modified_by_dbproxy: bool,
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

impl Collection {
    fn transact_inner<F, R>(&mut self, op: Option<Op>, func: F) -> Result<OpOutput<R>>
    where
        F: FnOnce(&mut Collection) -> Result<R>,
    {
        self.storage.begin_rust_trx()?;
        self.begin_undoable_operation(op);

        let mut res = func(self);

        if res.is_ok() {
            if let Err(e) = self.storage.set_modified() {
                res = Err(e);
            } else if let Err(e) = self.storage.commit_rust_trx() {
                res = Err(e);
            }
        }

        match res {
            Ok(output) => {
                let changes = if op.is_some() {
                    let changes = self.op_changes()?;
                    self.maybe_clear_study_queues_after_op(changes);
                    self.maybe_coalesce_note_undo_entry(changes);
                    changes
                } else {
                    self.clear_study_queues();
                    // dummy value, not used by transact_no_undo(). only required
                    // until we can migrate all the code to undoable ops
                    OpChanges {
                        op: Op::SetFlag,
                        changes: StateChanges::default(),
                    }
                };
                self.end_undoable_operation();
                Ok(OpOutput { output, changes })
            }
            Err(err) => {
                self.discard_undo_and_study_queues();
                self.storage.rollback_rust_trx()?;
                Err(err)
            }
        }
    }

    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned. Records undo state, and returns changes.
    pub(crate) fn transact<F, R>(&mut self, op: Op, func: F) -> Result<OpOutput<R>>
    where
        F: FnOnce(&mut Collection) -> Result<R>,
    {
        self.transact_inner(Some(op), func)
    }

    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned.
    pub(crate) fn transact_no_undo<F, R>(&mut self, func: F) -> Result<R>
    where
        F: FnOnce(&mut Collection) -> Result<R>,
    {
        self.transact_inner(None, func).map(|out| out.output)
    }

    pub(crate) fn close(self, downgrade: bool) -> Result<()> {
        self.storage.close(downgrade)
    }

    pub(crate) fn usn(&self) -> Result<Usn> {
        // if we cache this in the future, must make sure to invalidate cache when usn bumped in sync.finish()
        self.storage.usn(self.server)
    }

    /// Prepare for upload. Caller should not create transaction.
    pub(crate) fn before_upload(&mut self) -> Result<()> {
        self.transact_no_undo(|col| {
            col.storage.clear_all_graves()?;
            col.storage.clear_pending_note_usns()?;
            col.storage.clear_pending_card_usns()?;
            col.storage.clear_pending_revlog_usns()?;
            col.storage.clear_tag_usns()?;
            col.storage.clear_deck_conf_usns()?;
            col.storage.clear_deck_usns()?;
            col.storage.clear_notetype_usns()?;
            col.storage.increment_usn()?;
            col.storage.set_schema_modified()?;
            col.storage.set_last_sync(col.storage.get_schema_mtime()?)
        })?;
        self.storage.optimize()
    }

    pub(crate) fn clear_caches(&mut self) {
        self.state.deck_cache.clear();
        self.state.notetype_cache.clear();
    }
}
