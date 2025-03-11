// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use reqwest::Client;
use tracing::debug;

use crate::collection::Collection;
use crate::error;
use crate::error::AnkiError;
use crate::error::SyncError;
use crate::error::SyncErrorKind;
use crate::prelude::Usn;
use crate::progress::ThrottlingProgressHandler;
use crate::sync::collection::progress::SyncStage;
use crate::sync::collection::protocol::EmptyInput;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::collection::status::online_sync_status_check;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::login::SyncAuth;
use crate::sync::request::MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED;

pub struct NormalSyncer<'a> {
    pub(in crate::sync) col: &'a mut Collection,
    pub(in crate::sync) server: HttpSyncClient,
    pub(in crate::sync) progress: ThrottlingProgressHandler<NormalSyncProgress>,
}

#[derive(Default, Debug, Clone, Copy)]
pub struct NormalSyncProgress {
    pub stage: SyncStage,
    pub local_update: usize,
    pub local_remove: usize,
    pub remote_update: usize,
    pub remote_remove: usize,
}

#[derive(PartialEq, Eq, Debug, Clone, Copy)]
pub enum SyncActionRequired {
    NoChanges,
    FullSyncRequired { upload_ok: bool, download_ok: bool },
    NormalSyncRequired,
}

#[derive(Debug)]
pub struct ClientSyncState {
    pub required: SyncActionRequired,
    pub server_message: String,
    pub host_number: u32,
    pub new_endpoint: Option<String>,

    pub(in crate::sync) local_is_newer: bool,
    pub(in crate::sync) usn_at_last_sync: Usn,
    // latest server usn; local -1 entries will be rewritten to this
    pub(in crate::sync) server_usn: Usn,
    // -1 in client case; used to locate pending entries
    pub(in crate::sync) pending_usn: Usn,
    pub(in crate::sync) server_media_usn: Usn,
}

impl NormalSyncer<'_> {
    pub fn new(col: &mut Collection, server: HttpSyncClient) -> NormalSyncer<'_> {
        NormalSyncer {
            progress: col.new_progress_handler(),
            col,
            server,
        }
    }

    pub async fn sync(&mut self) -> error::Result<SyncOutput> {
        debug!("fetching meta...");
        let local = self.col.sync_meta()?;
        let local_bytes = local.collection_bytes;
        let limit = *MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED;
        if local.collection_bytes > limit {
            return Err(AnkiError::sync_error(
                format!("{local_bytes} > {limit}"),
                SyncErrorKind::UploadTooLarge,
            ));
        }
        let state = online_sync_status_check(local, &mut self.server).await?;
        debug!(?state, "fetched");
        match state.required {
            SyncActionRequired::NoChanges => Ok(state.into()),
            SyncActionRequired::FullSyncRequired { .. } => Ok(state.into()),
            SyncActionRequired::NormalSyncRequired => {
                self.col.discard_undo_and_study_queues();
                let timing = self.col.timing_today()?;
                self.col.unbury_if_day_rolled_over(timing)?;
                self.col.storage.begin_trx()?;
                match self.normal_sync_inner(state).await {
                    Ok(success) => {
                        self.col.storage.commit_trx()?;
                        Ok(success)
                    }
                    Err(e) => {
                        self.col.storage.rollback_trx()?;

                        let _ = self.server.abort(EmptyInput::request()).await;

                        if let AnkiError::SyncError {
                            source:
                                SyncError {
                                    kind: SyncErrorKind::SanityCheckFailed { client, server },
                                    ..
                                },
                        } = &e
                        {
                            debug!(client_counts=?client, server_counts=?server, "sanity check failed");
                            self.col.set_schema_modified()?;
                        }

                        Err(e)
                    }
                }
            }
        }
    }

    /// Sync. Caller must have created a transaction, and should call
    /// abort on failure.
    async fn normal_sync_inner(&mut self, mut state: ClientSyncState) -> error::Result<SyncOutput> {
        self.progress
            .update(false, |p| p.stage = SyncStage::Syncing)?;

        debug!("start");
        self.start_and_process_deletions(&state).await?;
        debug!("unchunked changes");
        self.process_unchunked_changes(&state).await?;
        debug!("begin stream from server");
        self.process_chunks_from_server(&state).await?;
        debug!("begin stream to server");
        self.send_chunks_to_server(&state).await?;

        self.progress
            .update(false, |p| p.stage = SyncStage::Finalizing)?;

        debug!("sanity check");
        self.sanity_check().await?;
        debug!("finalize");
        self.finalize(&state).await?;
        state.required = SyncActionRequired::NoChanges;
        Ok(state.into())
    }
}

#[derive(Debug)]
pub struct SyncOutput {
    pub required: SyncActionRequired,
    pub server_message: String,
    pub host_number: u32,
    pub new_endpoint: Option<String>,
    #[allow(unused)]
    pub(crate) server_media_usn: Usn,
}

impl From<ClientSyncState> for SyncOutput {
    fn from(s: ClientSyncState) -> Self {
        SyncOutput {
            required: s.required,
            server_message: s.server_message,
            host_number: s.host_number,
            new_endpoint: s.new_endpoint,
            server_media_usn: s.server_media_usn,
        }
    }
}

impl Collection {
    pub async fn normal_sync(
        &mut self,
        auth: SyncAuth,
        client: Client,
    ) -> error::Result<SyncOutput> {
        NormalSyncer::new(self, HttpSyncClient::new(auth, client))
            .sync()
            .await
    }
}
