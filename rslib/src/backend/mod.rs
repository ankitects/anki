// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod adding;
mod ankidroid;
mod ankihub;
mod ankiweb;
mod card_rendering;
mod collection;
mod config;
pub(crate) mod dbproxy;
mod error;
mod i18n;
mod import_export;
mod ops;
mod sync;

use std::ops::Deref;
use std::result;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::OnceLock;
use std::thread::JoinHandle;

use futures::future::AbortHandle;
use prost::Message;
use reqwest::Client;
use tokio::runtime;
use tokio::runtime::Runtime;
use tokio::sync::oneshot::Receiver;
use tokio::sync::oneshot::Sender;

use crate::backend::dbproxy::db_command_bytes;
use crate::backend::sync::SyncState;
use crate::prelude::*;
use crate::progress::Progress;
use crate::progress::ProgressState;
use crate::progress::ThrottlingProgressHandler;

#[derive(Clone)]
#[repr(transparent)]
pub struct Backend(Arc<BackendInner>);

impl Deref for Backend {
    type Target = BackendInner;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

pub struct BackendInner {
    col: Mutex<Option<Collection>>,
    pub(crate) tr: I18n,
    server: bool,
    sync_abort: Mutex<Option<AbortHandle>>,
    progress_state: Arc<Mutex<ProgressState>>,
    runtime: OnceLock<Runtime>,
    state: Mutex<BackendState>,
    backup_task: Mutex<Option<JoinHandle<Result<()>>>>,
    media_sync_task: Mutex<Option<JoinHandle<Result<()>>>>,
    web_client: Mutex<Option<Client>>,
}

struct BackendState {
    sync: SyncState,
    shutdown_signal_sender: Option<Sender<()>>,
    shutdown_signal_receiver: Option<Receiver<()>>,
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
        let (shutdown_signal_sender, shutdown_signal_receiver) = tokio::sync::oneshot::channel();
        Backend(Arc::new(BackendInner {
            col: Mutex::new(None),
            tr,
            server,
            sync_abort: Mutex::new(None),
            progress_state: Arc::new(Mutex::new(ProgressState {
                want_abort: false,
                last_progress: None,
            })),
            runtime: OnceLock::new(),
            state: Mutex::new(BackendState {
                sync: SyncState::default(),
                shutdown_signal_sender: Some(shutdown_signal_sender),
                shutdown_signal_receiver: Some(shutdown_signal_receiver),
            }),
            backup_task: Mutex::new(None),
            media_sync_task: Mutex::new(None),
            web_client: Mutex::new(None),
        }))
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

    #[cfg(feature = "rustls")]
    fn set_custom_certificate_inner(&self, cert_str: String) -> Result<()> {
        use std::io::Cursor;
        use std::io::Read;

        use reqwest::Certificate;

        let mut web_client = self.web_client.lock().unwrap();

        if cert_str.is_empty() {
            let _ = web_client.insert(Client::builder().http1_only().build().unwrap());
            return Ok(());
        }

        if rustls_pemfile::read_all(Cursor::new(cert_str.as_bytes()).by_ref()).count() != 1 {
            return Err(AnkiError::InvalidCertificateFormat);
        }

        if let Ok(certificate) = Certificate::from_pem(cert_str.as_bytes()) {
            if let Ok(new_client) = Client::builder()
                .use_rustls_tls()
                .add_root_certificate(certificate)
                .http1_only()
                .build()
            {
                let _ = web_client.insert(new_client);
                return Ok(());
            }
        }

        Err(AnkiError::InvalidCertificateFormat)
    }

    fn web_client(&self) -> Client {
        // currently limited to http1, as nginx doesn't support http2 proxies
        let mut web_client = self.web_client.lock().unwrap();

        web_client
            .get_or_insert_with(|| Client::builder().http1_only().build().unwrap())
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

    pub fn set_shutdown_signal(&self) -> Result<(), String> {
        if let Some(sender) = self.state.lock().unwrap().shutdown_signal_sender.take() {
            sender
                .send(())
                .map_err(|_| "Failed to set shutdown signal")?;
        }
        Ok(())
    }

    pub async fn wait_for_shutdown(&self) {
        let receiver_option = {
            let mut guard = self.state.lock().unwrap();
            guard.shutdown_signal_receiver.take()
        };
        if let Some(receiver) = receiver_option {
            receiver.await.unwrap();
        }
    }
}
