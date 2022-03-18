// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod backup;
pub(crate) mod timestamps;
mod transact;
pub(crate) mod undo;

use std::{collections::HashMap, path::PathBuf, sync::Arc};

use crate::{
    browser_table,
    decks::{Deck, DeckId},
    error::Result,
    i18n::I18n,
    log::{default_logger, Logger},
    notetype::{Notetype, NotetypeId},
    scheduler::{queue::CardQueues, SchedulerInfo},
    storage::{SchemaVersion, SqliteStorage},
    types::Usn,
    undo::UndoManager,
};

#[derive(Default)]
pub struct CollectionBuilder {
    collection_path: Option<PathBuf>,
    media_folder: Option<PathBuf>,
    media_db: Option<PathBuf>,
    server: Option<bool>,
    tr: Option<I18n>,
    log: Option<Logger>,
}

impl CollectionBuilder {
    /// Create a new builder with the provided collection path.
    /// If an in-memory database is desired, used ::default() instead.
    pub fn new(col_path: impl Into<PathBuf>) -> Self {
        let mut builder = Self::default();
        builder.set_collection_path(col_path);
        builder
    }

    pub fn build(&self) -> Result<Collection> {
        let col_path = self
            .collection_path
            .clone()
            .unwrap_or_else(|| PathBuf::from(":memory:"));
        let tr = self.tr.clone().unwrap_or_else(I18n::template_only);
        let server = self.server.unwrap_or_default();
        let media_folder = self.media_folder.clone().unwrap_or_default();
        let media_db = self.media_db.clone().unwrap_or_default();
        let log = self.log.clone().unwrap_or_else(crate::log::terminal);

        let storage = SqliteStorage::open_or_create(&col_path, &tr, server)?;
        let col = Collection {
            storage,
            col_path,
            media_folder,
            media_db,
            tr,
            log,
            server,
            state: CollectionState::default(),
        };

        Ok(col)
    }

    pub fn set_collection_path<P: Into<PathBuf>>(&mut self, collection: P) -> &mut Self {
        self.collection_path = Some(collection.into());
        self
    }

    pub fn set_media_paths<P: Into<PathBuf>>(&mut self, media_folder: P, media_db: P) -> &mut Self {
        self.media_folder = Some(media_folder.into());
        self.media_db = Some(media_db.into());
        self
    }

    pub fn set_server(&mut self, server: bool) -> &mut Self {
        self.server = Some(server);
        self
    }

    pub fn set_tr(&mut self, tr: I18n) -> &mut Self {
        self.tr = Some(tr);
        self
    }

    /// Directly set the logger.
    pub fn set_logger(&mut self, log: Logger) -> &mut Self {
        self.log = Some(log);
        self
    }

    /// Log to the provided file.
    pub fn set_log_file(&mut self, log_file: &str) -> Result<&mut Self, std::io::Error> {
        self.set_logger(default_logger(Some(log_file))?);
        Ok(self)
    }
}

#[cfg(test)]
pub fn open_test_collection() -> Collection {
    CollectionBuilder::default().build().unwrap()
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
    pub fn as_builder(&self) -> CollectionBuilder {
        let mut builder = CollectionBuilder::new(&self.col_path);
        builder
            .set_media_paths(self.media_folder.clone(), self.media_db.clone())
            .set_server(self.server)
            .set_tr(self.tr.clone())
            .set_logger(self.log.clone());
        builder
    }

    pub(crate) fn close(self, desired_version: Option<SchemaVersion>) -> Result<()> {
        self.storage.close(desired_version)
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
