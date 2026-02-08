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

use crate::backend::Backend;
use crate::error;

mod routes;

pub struct ApiServer {}

pub type ServerFuture = Pin<Box<dyn Future<Output = error::Result<(), std::io::Error>> + Send>>;

impl ApiServer {
    pub async fn make_server<'a: 'static>(
        backend: &'a Backend,
    ) -> error::Result<(SocketAddr, ServerFuture), Whatever> {
        let router = Router::new().route("/", get(|| async { "Hello, World!" }));
        let router = routes::add_routes(backend, router);
        let address = "0.0.0.0:8766";
        let listener = tokio::net::TcpListener::bind(address)
            .await
            .with_whatever_context(|_| format!("couldn't bind to {address}"))?;
        let addr = listener.local_addr().unwrap();
        let future = axum::serve(listener, router)
            .with_graceful_shutdown(async { backend.wait_for_shutdown().await })
            .into_future();

        Ok((addr, Box::pin(future)))
    }

    #[tokio::main]
    pub async fn run<'a: 'static>(backend: &'a Backend) -> error::Result<(), Whatever> {
        let (_addr, server_fut) = ApiServer::make_server(backend).await?;
        server_fut.await.whatever_context("await server")?;
        Ok(())
    }
}
