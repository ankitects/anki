// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod adding;
mod ankidroid;
mod card_rendering;
mod collection;
mod config;
pub(crate) mod dbproxy;
mod error;
mod i18n;
mod import_export;
mod ops;
mod sync;

use std::result;
use std::sync::Arc;
use std::sync::Mutex;
use std::thread::JoinHandle;

use once_cell::sync::OnceCell;
use prost::Message;
use tokio::runtime;
use tokio::runtime::Runtime;

use crate::backend::dbproxy::db_command_bytes;
use crate::backend::sync::SyncState;
use crate::prelude::*;
use crate::progress::AbortHandleSlot;
use crate::progress::Progress;
use crate::progress::ProgressState;
use crate::progress::ThrottlingProgressHandler;

pub struct Backend {
    col: Arc<Mutex<Option<Collection>>>,
    pub(crate) tr: I18n,
    server: bool,
    sync_abort: AbortHandleSlot,
    progress_state: Arc<Mutex<ProgressState>>,
    runtime: OnceCell<Runtime>,
    state: Arc<Mutex<BackendState>>,
    backup_task: Arc<Mutex<Option<JoinHandle<Result<()>>>>>,
}

#[derive(Default)]
struct BackendState {
    sync: SyncState,
}

pub fn init_backend(init_msg: &[u8]) -> result::Result<Backend, String> {
    let input: anki_proto::backend::BackendInit =
        match anki_proto::backend::BackendInit::decode(init_msg) {
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
            backup_task: Arc::new(Mutex::new(None)),
        }
    }

    pub fn i18n(&self) -> &I18n {
        &self.tr
    }

    pub fn run_db_command_bytes(&self, input: &[u8]) -> result::Result<Vec<u8>, Vec<u8>> {
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
    pub(crate) fn with_col<F, T>(&self, func: F) -> Result<T>
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

    /// Useful for operations that function with a closed collection, such as
    /// a colpkg import. For collection operations, you can use
    /// [Collection::new_progress_handler] instead.
    pub(crate) fn new_progress_handler<P: Into<Progress> + Default + Clone>(
        &self,
    ) -> ThrottlingProgressHandler<P> {
        ThrottlingProgressHandler::new(self.progress_state.clone())
    }
}
