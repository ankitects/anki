// Copyright: Anki Software, LLC and contributors
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
use crate::revlog::RevlogId;
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
    remote_collection_changed: bool,
    remote_review_ids: Vec<RevlogId>,
    remote_non_review_collection_changed: bool,
    pub(in crate::sync) rwkv_mirrors_need_upload: bool,
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
            remote_collection_changed: false,
            remote_review_ids: vec![],
            remote_non_review_collection_changed: false,
            rwkv_mirrors_need_upload: false,
        }
    }

    pub async fn sync(&mut self) -> error::Result<SyncOutput> {
        self.rwkv_mirrors_need_upload = false;
        let first = self.sync_once().await?;
        if self.rwkv_mirrors_need_upload && first.required == SyncActionRequired::NoChanges {
            // Send boundaries recovered from a stale legacy config snapshot
            // back to the server before reporting the sync as complete.
            self.rwkv_mirrors_need_upload = false;
            let mut second = self.sync_once().await?;
            if second.server_message.is_empty() {
                second.server_message = first.server_message;
            }
            if second.new_endpoint.is_none() {
                second.new_endpoint = first.new_endpoint;
            }
            second.remote_collection_changed |= first.remote_collection_changed;
            second.remote_non_review_collection_changed |=
                first.remote_non_review_collection_changed;
            let mut review_ids = first.remote_review_ids;
            review_ids.extend(second.remote_review_ids);
            second.remote_review_ids = review_ids;
            return Ok(second);
        }
        Ok(first)
    }

    async fn sync_once(&mut self) -> error::Result<SyncOutput> {
        debug!("fetching meta...");
        let local = self.col.sync_meta()?;
        let local_bytes = local.collection_bytes;
        let limit = *MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED;
        if self.server.endpoint.as_str().contains("ankiweb") && local.collection_bytes > limit {
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
        let foreign_cards = self.col.storage.card_ids_with_foreign_fsrs_state()?;
        match self
            .col
            .repair_foreign_fsrs_memory_states_inner(foreign_cards)
        {
            Ok(cards) => debug!(cards, "repaired foreign FSRS memory states"),
            Err(err) => tracing::warn!(?err, "repairing foreign FSRS memory states failed"),
        }
        debug!("begin stream to server");
        self.send_chunks_to_server(&state).await?;

        self.progress
            .update(false, |p| p.stage = SyncStage::Finalizing)?;

        debug!("sanity check");
        self.sanity_check().await?;
        debug!("finalize");
        self.finalize(&state).await?;
        if self.rwkv_mirrors_need_upload {
            self.col.set_modified_after_last_sync()?;
        }
        state.required = SyncActionRequired::NoChanges;
        let mut output: SyncOutput = state.into();
        output.remote_collection_changed = self.remote_collection_changed;
        output.remote_review_ids = std::mem::take(&mut self.remote_review_ids);
        output.remote_non_review_collection_changed = self.remote_non_review_collection_changed;
        Ok(output)
    }

    pub(in crate::sync) fn mark_remote_non_review_collection_changed(&mut self, changed: bool) {
        self.remote_non_review_collection_changed |= changed;
    }

    pub(in crate::sync) fn mark_remote_collection_changed(&mut self, changed: bool) {
        self.remote_collection_changed |= changed;
    }

    pub(in crate::sync) fn record_remote_review(&mut self, review_id: RevlogId) {
        self.remote_review_ids.push(review_id);
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
    pub remote_collection_changed: bool,
    pub remote_review_ids: Vec<RevlogId>,
    pub remote_non_review_collection_changed: bool,
}

impl From<ClientSyncState> for SyncOutput {
    fn from(s: ClientSyncState) -> Self {
        SyncOutput {
            required: s.required,
            server_message: s.server_message,
            host_number: s.host_number,
            new_endpoint: s.new_endpoint,
            server_media_usn: s.server_media_usn,
            remote_collection_changed: false,
            remote_review_ids: vec![],
            remote_non_review_collection_changed: false,
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
