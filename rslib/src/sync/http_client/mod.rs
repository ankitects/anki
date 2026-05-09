// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod full_sync;
pub(crate) mod io_monitor;
mod protocol;

use std::time::Duration;

use reqwest::Client;
use reqwest::Error;
use reqwest::StatusCode;
use reqwest::Url;

use crate::notes;
use crate::sync::collection::protocol::AsSyncEndpoint;
use crate::sync::error::HttpError;
use crate::sync::error::HttpResult;
use crate::sync::http_client::io_monitor::IoMonitor;
use crate::sync::login::SyncAuth;
use crate::sync::request::header_and_stream::SyncHeader;
use crate::sync::request::header_and_stream::SYNC_HEADER_NAME;
use crate::sync::request::SyncRequest;
use crate::sync::response::SyncResponse;

#[derive(Clone)]
pub struct HttpSyncClient {
    /// Set to the empty string for initial login
    pub sync_key: String,
    session_key: String,
    client: Client,
    pub endpoint: Url,
    pub io_timeout: Duration,
}

impl HttpSyncClient {
    pub fn new(auth: SyncAuth, client: Client) -> HttpSyncClient {
        let io_timeout = Duration::from_secs(auth.io_timeout_secs.unwrap_or(30) as u64);
        HttpSyncClient {
            sync_key: auth.hkey,
            session_key: simple_session_id(),
            client,
            endpoint: auth
                .endpoint
                .unwrap_or_else(|| Url::try_from("https://sync.ankiweb.net/").unwrap()),
            io_timeout,
        }
    }

    async fn request<I, O>(
        &self,
        method: impl AsSyncEndpoint,
        request: SyncRequest<I>,
    ) -> HttpResult<SyncResponse<O>> {
        self.request_ext(method, request, IoMonitor::new()).await
    }

    async fn request_ext<I, O>(
        &self,
        method: impl AsSyncEndpoint,
        request: SyncRequest<I>,
        io_monitor: IoMonitor,
    ) -> HttpResult<SyncResponse<O>> {
        let header = SyncHeader {
            sync_version: request.sync_version,
            sync_key: self.sync_key.clone(),
            client_ver: request.client_version,
            session_key: self.session_key.clone(),
        };
        let data = request.data;
        let url = method.as_sync_endpoint(&self.endpoint);
        let request = self
            .client
            .post(url)
            .header(&SYNC_HEADER_NAME, serde_json::to_string(&header).unwrap());
        io_monitor
            .zstd_request_with_timeout(request, data, self.io_timeout)
            .await
            .map(SyncResponse::from_vec)
    }

    #[cfg(test)]
    pub(crate) fn endpoint(&self) -> &Url {
        &self.endpoint
    }

    #[cfg(test)]
    pub(crate) fn set_skey(&mut self, skey: String) {
        self.session_key = skey;
    }

    #[cfg(test)]
    pub(crate) fn skey(&self) -> &str {
        &self.session_key
    }
}

impl From<Error> for HttpError {
    fn from(err: Error) -> Self {
        HttpError {
            // we should perhaps make this Optional instead
            code: err.status().unwrap_or(StatusCode::SEE_OTHER),
            context: "from reqwest".into(),
            source: Some(Box::new(err) as _),
        }
    }
}

fn simple_session_id() -> String {
    let table = b"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ\
0123456789";
    notes::to_base_n(rand::random::<u32>() as u64, table)
}
