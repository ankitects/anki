// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    error,
    sync::{
        collection::protocol::{EmptyInput, SyncProtocol},
        http_client::HttpSyncClient,
        login::SyncAuth,
    },
};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SyncStage {
    Connecting,
    Syncing,
    Finalizing,
}

impl Default for SyncStage {
    fn default() -> Self {
        SyncStage::Connecting
    }
}

#[derive(Debug, Default, Clone, Copy)]
pub struct FullSyncProgress {
    pub transferred_bytes: usize,
    pub total_bytes: usize,
}

pub async fn sync_abort(auth: SyncAuth) -> error::Result<()> {
    HttpSyncClient::new(auth)
        .abort(EmptyInput::request())
        .await?
        .json()
}

pub type FullSyncProgressFn = Box<dyn FnMut(FullSyncProgress, bool) + Send + Sync + 'static>;
