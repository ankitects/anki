// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod timestamps;
mod transact;
pub(crate) mod undo;

use std::{collections::HashMap, path::PathBuf, sync::Arc};

use crate::{
    browser_table,
    decks::{Deck, DeckId},
    error::Result,
    i18n::I18n,
    log::Logger,
    notetype::{Notetype, NotetypeId},
    scheduler::{queue::CardQueues, SchedulerInfo},
    storage::SqliteStorage,
    types::Usn,
    undo::UndoManager,
};

pub fn open_collection<P: Into<PathBuf>>(
    path: P,
    media_folder: P,
    media_db: P,
    server: bool,
    tr: I18n,
    log: Logger,
) -> Result<Collection> {
    let col_path = path.into();
    let storage = SqliteStorage::open_or_create(&col_path, &tr, server)?;

    let col = Collection {
        storage,
        col_path,
        media_folder: media_folder.into(),
        media_db: media_db.into(),
        tr,
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
    let tr = I18n::template_only();
    open_collection(":memory:", "", "", server, tr, log::terminal()).unwrap()
}

#[derive(Debug, Default)]
pub struct CollectionState {
    pub(crate) undo: UndoManager,
    pub(crate) notetype_cache: HashMap<NotetypeId, Arc<Notetype>>,
    pub(crate) deck_cache: HashMap<DeckId, Arc<Deck>>,
    pub(crate) scheduler_info: Option<SchedulerInfo>,
    pub(crate) card_queues: Option<CardQueues>,
    pub(crate) active_browser_columns: Option<Arc<Vec<browser_table::Column>>>,
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
    pub(crate) tr: I18n,
    pub(crate) log: Logger,
    pub(crate) server: bool,
    pub(crate) state: CollectionState,
}

impl Collection {
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
            col.set_schema_modified()?;
            col.storage
                .set_last_sync(col.storage.get_collection_timestamps()?.schema_change)
        })?;
        self.storage.optimize()
    }

    pub(crate) fn clear_caches(&mut self) {
        self.state.deck_cache.clear();
        self.state.notetype_cache.clear();
    }
}
