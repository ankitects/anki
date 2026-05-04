// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod extract;
mod logging;
mod routes;
pub mod services;

use std::future::Future;
use std::future::IntoFuture;
use std::net::IpAddr;
use std::net::SocketAddr;
use std::pin::Pin;

use axum::routing::get;
use axum::Router;
use snafu::ResultExt;
use snafu::Whatever;

use crate::api::logging::with_logging_layer;
use crate::api::services::FrontendRouteService;
use crate::backend::Backend;
use crate::config::I32ConfigKey;
use crate::config::StringKey;
use crate::error;
use crate::version::version;
#[derive(Debug)]
pub struct ApiServerConfig {
    pub host: IpAddr,
    pub port: u16,
}

impl Default for ApiServerConfig {
    fn default() -> Self {
        Self {
            host: "127.0.0.1".parse().unwrap(),
            port: 8766,
        }
    }
}

pub struct ApiServer {}

pub type ServerFuture = Pin<Box<dyn Future<Output = error::Result<(), std::io::Error>> + Send>>;

impl ApiServer {
    pub async fn make_server(
        backend: &Backend,
        config: ApiServerConfig,
    ) -> error::Result<(SocketAddr, ServerFuture), Whatever> {
        let router = Router::new().route("/", get(|| async { format!("Anki {}", version()) }));
        let frontend_service = FrontendRouteService::new(
            backend.api_routes.clone(),
            backend.pending_api_requests.clone(),
        );
        let router = router.route_service("/{*path}", frontend_service);
        let router = with_logging_layer(routes::add_routes(backend, router));
        let address = format!("{}:{}", config.host, config.port);
        let listener = tokio::net::TcpListener::bind(&address)
            .await
            .with_whatever_context(|_| format!("couldn't bind to {address}"))?;
        let addr = listener.local_addr().unwrap();
        let backend = backend.clone();
        let future = axum::serve(listener, router)
            .with_graceful_shutdown(async move { backend.wait_for_api_server_shutdown().await })
            .into_future();
        tracing::info!(%addr, "API server started");
        Ok((addr, Box::pin(future)))
    }

    #[tokio::main]
    pub async fn run(backend: &Backend) -> error::Result<(), Whatever> {
        let config = backend
            .with_col(|col| {
                let default_config = ApiServerConfig::default();
                let host: IpAddr = col
                    .get_config_string(StringKey::ApiServerHost)
                    .parse()
                    .unwrap_or(default_config.host);

                let port = {
                    let p = col.get_config_i32(I32ConfigKey::ApiServerPort);
                    if p > 0 {
                        p as u16
                    } else {
                        default_config.port
                    }
                };
                Ok(ApiServerConfig { host, port })
            })
            .with_whatever_context(|e| format!("couldn't get API config: {}", e))?;
        let (_addr, server_fut) = ApiServer::make_server(backend, config).await?;
        server_fut.await.whatever_context("await server")?;
        Ok(())
    }
}
