// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Backend;
use crate::prelude::*;

impl crate::services::BackendApiService for Backend {
    fn register_api_route(&self, input: anki_proto::generic::String) -> Result<()> {
        self.register_api_route(input.val);

        Ok(())
    }

    fn get_pending_api_requests(&self) -> Result<anki_proto::api::ApiRequests> {
        if !self.is_api_server_running() {
            return Err(AnkiError::ApiServerNotRunning);
        }
        Ok(anki_proto::api::ApiRequests {
            requests: self
                .get_pending_api_requests()
                .into_iter()
                .map(|(k, v)| anki_proto::api::ApiRequest {
                    id: k,
                    method: v.method.to_string(),
                    path: v.path,
                    body: v.body,
                })
                .collect(),
        })
    }

    fn send_api_response(&self, input: anki_proto::api::ApiResponse) -> Result<()> {
        if let Some(entry) = self.pending_api_requests.lock().unwrap().remove(&input.id) {
            let _ = entry.1.send(input);
        }

        Ok(())
    }
}
