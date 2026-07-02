// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::time::Duration;

use axum::body::Body;
use axum::http::Request;
use axum::response::Response;
use axum::Router;
use tower_http::trace::TraceLayer;
use tracing::info_span;
use tracing::Span;

pub fn with_logging_layer(router: Router) -> Router {
    router.layer(
        TraceLayer::new_for_http()
            .make_span_with(|request: &Request<Body>| {
                info_span!(
                    "request",
                    uri = request.uri().path(),
                    ip = tracing::field::Empty,
                    uid = tracing::field::Empty,
                    client = tracing::field::Empty,
                    session = tracing::field::Empty,
                )
            })
            .on_request(())
            .on_response(|response: &Response, latency: Duration, _span: &Span| {
                tracing::info!(
                    elap_ms = latency.as_millis() as u32,
                    httpstatus = response.status().as_u16(),
                    "finished"
                );
            })
            .on_failure(()),
    )
}
