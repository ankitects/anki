// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// infallible backend methods still return a result
#![allow(clippy::unnecessary_wraps)]

mod adding;
mod card;
mod cardrendering;
mod collection;
mod config;
mod dbproxy;
mod deckconfig;
mod decks;
mod error;
mod generic;
mod i18n;
mod links;
mod media;
mod notes;
mod notetypes;
mod ops;
mod progress;
mod scheduler;
mod search;
mod stats;
mod sync;
mod tags;

use std::{
    result,
    sync::{Arc, Mutex},
};

use once_cell::sync::OnceCell;
use progress::AbortHandleSlot;
use prost::Message;
use tokio::runtime::{self, Runtime};

use self::{
    card::CardsService,
    cardrendering::CardRenderingService,
    collection::CollectionService,
    config::ConfigService,
    deckconfig::DeckConfigService,
    decks::DecksService,
    i18n::I18nService,
    links::LinksService,
    media::MediaService,
    notes::NotesService,
    notetypes::NotetypesService,
    progress::ProgressState,
    scheduler::SchedulerService,
    search::SearchService,
    stats::StatsService,
    sync::{SyncService, SyncState},
    tags::TagsService,
};
use crate::{
    backend::dbproxy::db_command_bytes,
    backend_proto as pb,
    collection::Collection,
    error::{AnkiError, Result},
    i18n::I18n,
};

pub struct Backend {
    col: Arc<Mutex<Option<Collection>>>,
    tr: I18n,
    server: bool,
    sync_abort: AbortHandleSlot,
    progress_state: Arc<Mutex<ProgressState>>,
    runtime: OnceCell<Runtime>,
    state: Arc<Mutex<BackendState>>,
}

#[derive(Default)]
struct BackendState {
    sync: SyncState,
}

pub fn init_backend(init_msg: &[u8]) -> std::result::Result<Backend, String> {
    let input: pb::BackendInit = match pb::BackendInit::decode(init_msg) {
        Ok(req) => req,
        Err(_) => return Err("couldn't decode init request".into()),
    };

    let tr = I18n::new(&input.preferred_langs);

    Ok(Backend::new(tr, input.server))
}

impl Backend {
    pub fn new(tr: I18n, server: bool) -> Backend {
        Backend {
            col: Arc::new(Mutex::new(None)),
            tr,
            server,
            sync_abort: Arc::new(Mutex::new(None)),
            progress_state: Arc::new(Mutex::new(ProgressState {
                want_abort: false,
                last_progress: None,
            })),
            runtime: OnceCell::new(),
            state: Arc::new(Mutex::new(BackendState::default())),
        }
    }

    pub fn i18n(&self) -> &I18n {
        &self.tr
    }

    pub fn run_method(
        &self,
        service: u32,
        method: u32,
        input: &[u8],
    ) -> result::Result<Vec<u8>, Vec<u8>> {
        pb::ServiceIndex::from_i32(service as i32)
            .ok_or_else(|| AnkiError::invalid_input("invalid service"))
            .and_then(|service| match service {
                pb::ServiceIndex::Scheduler => SchedulerService::run_method(self, method, input),
                pb::ServiceIndex::Decks => DecksService::run_method(self, method, input),
                pb::ServiceIndex::Notes => NotesService::run_method(self, method, input),
                pb::ServiceIndex::Notetypes => NotetypesService::run_method(self, method, input),
                pb::ServiceIndex::Config => ConfigService::run_method(self, method, input),
                pb::ServiceIndex::Sync => SyncService::run_method(self, method, input),
                pb::ServiceIndex::Tags => TagsService::run_method(self, method, input),
                pb::ServiceIndex::DeckConfig => DeckConfigService::run_method(self, method, input),
                pb::ServiceIndex::CardRendering => {
                    CardRenderingService::run_method(self, method, input)
                }
                pb::ServiceIndex::Media => MediaService::run_method(self, method, input),
                pb::ServiceIndex::Stats => StatsService::run_method(self, method, input),
                pb::ServiceIndex::Search => SearchService::run_method(self, method, input),
                pb::ServiceIndex::I18n => I18nService::run_method(self, method, input),
                pb::ServiceIndex::Links => LinksService::run_method(self, method, input),
                pb::ServiceIndex::Collection => CollectionService::run_method(self, method, input),
                pb::ServiceIndex::Cards => CardsService::run_method(self, method, input),
            })
            .map_err(|err| {
                let backend_err = err.into_protobuf(&self.tr);
                let mut bytes = Vec::new();
                backend_err.encode(&mut bytes).unwrap();
                bytes
            })
    }

    pub fn run_db_command_bytes(&self, input: &[u8]) -> std::result::Result<Vec<u8>, Vec<u8>> {
        self.db_command(input).map_err(|err| {
            let backend_err = err.into_protobuf(&self.tr);
            let mut bytes = Vec::new();
            backend_err.encode(&mut bytes).unwrap();
            bytes
        })
    }

    /// If collection is open, run the provided closure while holding
    /// the mutex.
    /// If collection is not open, return an error.
    fn with_col<F, T>(&self, func: F) -> Result<T>
    where
        F: FnOnce(&mut Collection) -> Result<T>,
    {
        func(
            self.col
                .lock()
                .unwrap()
                .as_mut()
                .ok_or(AnkiError::CollectionNotOpen)?,
        )
    }

    fn runtime_handle(&self) -> runtime::Handle {
        self.runtime
            .get_or_init(|| {
                runtime::Builder::new_multi_thread()
                    .worker_threads(1)
                    .enable_all()
                    .build()
                    .unwrap()
            })
            .handle()
            .clone()
    }

    fn db_command(&self, input: &[u8]) -> Result<Vec<u8>> {
        self.with_col(|col| db_command_bytes(col, input))
    }
}
