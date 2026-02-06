// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::future::Future;
use std::future::IntoFuture;
use std::net::SocketAddr;
use std::pin::Pin;

use axum::routing::get;
use axum::Router;
use snafu::ResultExt;
use snafu::Whatever;

use crate::error;

pub struct ApiServer {}

pub type ServerFuture = Pin<Box<dyn Future<Output = error::Result<(), std::io::Error>> + Send>>;

impl ApiServer {
    pub async fn make_server() -> error::Result<(SocketAddr, ServerFuture), Whatever> {
        let app = Router::new().route("/", get(|| async { "Hello, World!" }));
        let address = "0.0.0.0:8766";
        let listener = tokio::net::TcpListener::bind(address)
            .await
            .with_whatever_context(|_| format!("couldn't bind to {address}"))?;
        let addr = listener.local_addr().unwrap();
        let future = axum::serve(listener, app)
            .with_graceful_shutdown(async {
                let _ = tokio::signal::ctrl_c().await;
            })
            .into_future();

        Ok((addr, Box::pin(future)))
    }

    #[snafu::report]
    #[tokio::main]
    pub async fn run() -> error::Result<(), Whatever> {
        let (_addr, server_fut) = ApiServer::make_server().await?;
        server_fut.await.whatever_context("await server")?;
        Ok(())
    }
}
