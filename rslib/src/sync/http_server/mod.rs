// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod handlers;
mod logging;
mod media_manager;
mod routes;
mod user;

use std::collections::HashMap;
use std::future::Future;
use std::net::IpAddr;
use std::net::SocketAddr;
use std::net::TcpListener;
use std::path::Path;
use std::path::PathBuf;
use std::pin::Pin;
use std::sync::Arc;
use std::sync::Mutex;

use anki_io::create_dir_all;
use axum::extract::DefaultBodyLimit;
use axum::Router;
use axum_client_ip::SecureClientIpSource;
use snafu::whatever;
use snafu::OptionExt;
use snafu::ResultExt;
use snafu::Whatever;
use tracing::Span;

use crate::error;
use crate::media::files::sha1_of_data;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::http_server::logging::with_logging_layer;
use crate::sync::http_server::media_manager::ServerMediaManager;
use crate::sync::http_server::routes::collection_sync_router;
use crate::sync::http_server::routes::media_sync_router;
use crate::sync::http_server::user::User;
use crate::sync::login::HostKeyRequest;
use crate::sync::login::HostKeyResponse;
use crate::sync::request::SyncRequest;
use crate::sync::request::MAXIMUM_SYNC_PAYLOAD_BYTES;
use crate::sync::response::SyncResponse;

pub struct SimpleServer {
    state: Mutex<SimpleServerInner>,
}

pub struct SimpleServerInner {
    /// hkey->user
    users: HashMap<String, User>,
}

#[derive(serde::Deserialize, Debug)]
pub struct SyncServerConfig {
    #[serde(default = "default_host")]
    pub host: IpAddr,
    #[serde(default = "default_port")]
    pub port: u16,
    #[serde(default = "default_base", rename = "base")]
    pub base_folder: PathBuf,
    #[serde(default = "default_ip_header")]
    pub ip_header: SecureClientIpSource,
}

fn default_host() -> IpAddr {
    "0.0.0.0".parse().unwrap()
}

fn default_port() -> u16 {
    8080
}

fn default_base() -> PathBuf {
    dirs::home_dir()
        .unwrap_or_else(|| panic!("Unable to determine home folder; please set SYNC_BASE"))
        .join(".syncserver")
}

pub fn default_ip_header() -> SecureClientIpSource {
    SecureClientIpSource::ConnectInfo
}

impl SimpleServerInner {
    fn new_from_env(base_folder: &Path) -> error::Result<Self, Whatever> {
        let mut idx = 1;
        let mut users: HashMap<String, User> = Default::default();
        loop {
            let envvar = format!("SYNC_USER{idx}");
            match std::env::var(&envvar) {
                Ok(val) => {
                    let hkey = derive_hkey(&val);
                    let (name, _) = val.split_once(':').with_whatever_context(|| {
                        format!("{envvar} should be in 'username:password' format.")
                    })?;
                    let folder = base_folder.join(name);
                    create_dir_all(&folder).whatever_context("creating SYNC_BASE")?;
                    let media =
                        ServerMediaManager::new(&folder).whatever_context("opening media")?;
                    users.insert(
                        hkey,
                        User {
                            name: name.into(),
                            col: None,
                            sync_state: None,
                            media,
                            folder,
                        },
                    );
                    idx += 1;
                }
                Err(_) => break,
            }
        }
        if users.is_empty() {
            whatever!("No users defined; SYNC_USER1 env var should be set.");
        }
        Ok(Self { users })
    }
}

// This is not what AnkiWeb does, but should suffice for this use case.
fn derive_hkey(user_and_pass: &str) -> String {
    hex::encode(sha1_of_data(user_and_pass.as_bytes()))
}

impl SimpleServer {
    pub(in crate::sync) async fn with_authenticated_user<F, I, O>(
        &self,
        req: SyncRequest<I>,
        op: F,
    ) -> HttpResult<O>
    where
        F: FnOnce(&mut User, SyncRequest<I>) -> HttpResult<O>,
    {
        let mut state = self.state.lock().unwrap();
        let user = state
            .users
            .get_mut(&req.sync_key)
            .or_forbidden("invalid hkey")?;
        Span::current().record("uid", &user.name);
        Span::current().record("client", &req.client_version);
        Span::current().record("session", &req.session_key);
        op(user, req)
    }

    pub(in crate::sync) fn get_host_key(
        &self,
        request: HostKeyRequest,
    ) -> HttpResult<SyncResponse<HostKeyResponse>> {
        let state = self.state.lock().unwrap();
        let key = derive_hkey(&format!("{}:{}", request.username, request.password));
        if state.users.contains_key(&key) {
            SyncResponse::try_from_obj(HostKeyResponse { key })
        } else {
            None.or_forbidden("invalid user/pass in get_host_key")
        }
    }

    pub fn new(base_folder: &Path) -> error::Result<Self, Whatever> {
        let inner = SimpleServerInner::new_from_env(base_folder)?;
        Ok(SimpleServer {
            state: Mutex::new(inner),
        })
    }

    pub fn make_server(
        config: SyncServerConfig,
    ) -> error::Result<(SocketAddr, ServerFuture), Whatever> {
        let server = Arc::new(
            SimpleServer::new(&config.base_folder).whatever_context("unable to create server")?,
        );
        let address = &format!("{}:{}", config.host, config.port);
        let listener = TcpListener::bind(address)
            .with_whatever_context(|_| format!("couldn't bind to {address}"))?;
        let addr = listener.local_addr().unwrap();
        let server = with_logging_layer(
            Router::new()
                .nest("/sync", collection_sync_router())
                .nest("/msync", media_sync_router())
                .with_state(server)
                .layer(DefaultBodyLimit::max(*MAXIMUM_SYNC_PAYLOAD_BYTES))
                .layer(config.ip_header.into_extension()),
        );
        let future = axum::Server::from_tcp(listener)
            .whatever_context("listen failed")?
            .serve(server.into_make_service_with_connect_info::<SocketAddr>())
            .with_graceful_shutdown(async {
                let _ = tokio::signal::ctrl_c().await;
            });
        tracing::info!(%addr, "listening");
        Ok((addr, Box::pin(future)))
    }

    #[snafu::report]
    #[tokio::main]
    pub async fn run() -> error::Result<(), Whatever> {
        let config = envy::prefixed("SYNC_")
            .from_env::<SyncServerConfig>()
            .whatever_context("reading SYNC_* env vars")?;
        let (_addr, server_fut) = SimpleServer::make_server(config)?;
        server_fut.await.whatever_context("await server")?;
        Ok(())
    }
}

pub type ServerFuture = Pin<Box<dyn Future<Output = error::Result<(), hyper::Error>> + Send>>;
