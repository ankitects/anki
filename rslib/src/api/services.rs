// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::convert::Infallible;
use std::future::Future;
use std::pin::Pin;
use std::sync::Arc;
use std::sync::Mutex;
use std::task::Context;
use std::task::Poll;
use std::time::Duration;

use axum::body::to_bytes;
use axum::extract::Request;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::response::Response;
use bytes::Bytes;
use hyper::Method;
use tokio::sync::oneshot;
use tower_service::Service;

static FRONTEND_REQUST_TIMEOUT_SECONDS: u64 = 30;

#[derive(Debug)]
pub struct FrontendRequest {
    pub method: Method,
    pub path: String,
    pub body: Vec<u8>,
}

pub type PendingFrontendRequest = (
    Option<FrontendRequest>,
    oneshot::Sender<anki_proto::api::ApiResponse>,
);

#[derive(Debug, Clone)]
pub struct FrontendRouteService {
    routes: Arc<Mutex<Vec<String>>>,
    pending_requests: Arc<Mutex<HashMap<u64, PendingFrontendRequest>>>,
    current_id: u64,
}

impl FrontendRouteService {
    pub fn new(
        routes: Arc<Mutex<Vec<String>>>,
        pending_requests: Arc<Mutex<HashMap<u64, PendingFrontendRequest>>>,
    ) -> Self {
        Self {
            routes,
            pending_requests,
            current_id: 0,
        }
    }
}

impl Service<Request> for FrontendRouteService {
    type Response = Response;
    type Error = Infallible;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;

    fn poll_ready(&mut self, _cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn call(&mut self, req: Request) -> Self::Future {
        let (parts, body) = req.into_parts();
        let method = parts.method;
        let path = parts.uri.path().trim_start_matches("/").to_string();
        let routes = self.routes.clone();
        let pending_requests = self.pending_requests.clone();
        self.current_id += 1;
        let request_id = self.current_id;
        let fut = async move {
            if !routes.lock().unwrap().contains(&path) {
                return Ok(StatusCode::BAD_REQUEST.into_response());
            }
            let body_bytes = to_bytes(body, usize::MAX).await.unwrap_or_default();
            let (sender, receiver) = oneshot::channel();
            pending_requests.lock().unwrap().insert(
                request_id,
                (
                    Some(FrontendRequest {
                        method,
                        path,
                        body: body_bytes.into(),
                    }),
                    sender,
                ),
            );
            match tokio::time::timeout(
                Duration::from_secs(FRONTEND_REQUST_TIMEOUT_SECONDS),
                receiver,
            )
            .await
            {
                Ok(recv_result) => match recv_result {
                    Ok(response) => {
                        let body = Bytes::from(response.body);
                        Ok(body.into_response())
                    }
                    Err(_) => Ok(StatusCode::INTERNAL_SERVER_ERROR.into_response()),
                },
                Err(_) => Ok(StatusCode::REQUEST_TIMEOUT.into_response()),
            }
        };
        Box::pin(fut)
    }
}
