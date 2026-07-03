// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::sync::sync_status_response;
use tracing::debug;

use crate::error::SyncErrorKind;
use crate::prelude::*;
use crate::sync::collection::meta::SyncMeta;
use crate::sync::collection::normal::ClientSyncState;
use crate::sync::http_client::HttpSyncClient;

impl Collection {
    /// Checks local collection only. If local collection is clean but changes
    /// are pending on AnkiWeb, NoChanges will be returned.
    pub fn sync_status_offline(&mut self) -> Result<sync_status_response::Required> {
        let stamps = self.storage.get_collection_timestamps()?;
        let required = if stamps.schema_changed_since_sync() {
            sync_status_response::Required::FullSync
        } else if stamps.collection_changed_since_sync() {
            sync_status_response::Required::NormalSync
        } else {
            sync_status_response::Required::NoChanges
        };

        Ok(required)
    }
}

/// Should be called if a call to sync_status_offline() returns NoChanges, to
/// check if AnkiWeb has pending changes. Caller should persist new endpoint if
/// returned.
///
/// This routine is outside of the collection, as we don't want to block
/// collection access for a potentially slow network request that happens in the
/// background.
pub async fn online_sync_status_check(
    local: SyncMeta,
    server: &mut HttpSyncClient,
) -> Result<ClientSyncState, AnkiError> {
    let (remote, new_endpoint) = server.meta_with_redirect().await?;
    debug!(?remote, "meta");
    debug!(?local, "meta");
    if !remote.should_continue {
        debug!(remote.server_message, "server says abort");
        return Err(AnkiError::sync_error(
            remote.server_message,
            SyncErrorKind::ServerMessage,
        ));
    }
    let delta = remote.current_time.0 - local.current_time.0;
    if delta.abs() > 300 {
        debug!(delta, "clock off");
        return Err(AnkiError::sync_error("", SyncErrorKind::ClockIncorrect));
    }
    Ok(local.compared_to_remote(remote, new_endpoint))
}
