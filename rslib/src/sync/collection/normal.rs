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
        self.col.upgrade_empty_fsrs_presets()?;
        self.col.repair_incomplete_fsrs7_states()?;
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
                        // Upgrade newly received empty presets only after the
                        // sync transaction completes. The resulting -1 USNs
                        // remain pending for the next upload.
                        self.col.upgrade_empty_fsrs_presets()?;
                        self.col.repair_incomplete_fsrs7_states()?;
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

#[cfg(test)]
mod tests {
    use super::*;
    use crate::card::CardQueue;
    use crate::card::CardType;
    use crate::card::FsrsMemoryState;
    use crate::collection::CollectionBuilder;
    use crate::prelude::*;
    use crate::sync::collection::tests::with_active_server;
    use crate::sync::collection::tests::SyncTestContext;
    use crate::tests::NoteAdder;

    #[tokio::test]
    async fn full_download_upgrades_empty_presets_only_after_validation() -> Result<()> {
        with_active_server(|client| async move {
            let ctx = SyncTestContext::new(client);
            let mut source = ctx.col1();
            NoteAdder::basic(&mut source).add(&mut source);
            let mut config = source.get_deck_config(DeckConfigId(1), false)?.unwrap();
            config.clear_fsrs_params();
            source.add_or_update_deck_config(&mut config)?;
            source.full_upload_with_server(ctx.client.clone()).await?;
            ctx.col2()
                .full_download_with_server(ctx.client.clone())
                .await?;

            let downloaded = CollectionBuilder::new(ctx.folder.path().join("col2.anki2"))
                .set_skip_fsrs_defaults_upgrade()
                .build()?;
            assert!(downloaded
                .get_deck_config(DeckConfigId(1), false)?
                .unwrap()
                .has_empty_fsrs_params());
            let stamps = downloaded.storage.get_collection_timestamps()?;
            assert_eq!(stamps.collection_change, stamps.last_sync);
            downloaded.close(None)?;

            let mut target = ctx.col2();
            let preset = target.get_deck_config(DeckConfigId(1), false)?.unwrap();
            assert_eq!(preset.inner.fsrs_params_7, fsrs::DEFAULT_PARAMETERS);
            assert_eq!(preset.usn, Usn(-1));
            assert_eq!(
                target.sync_status_offline()?,
                anki_proto::sync::sync_status_response::Required::NormalSync
            );
            NormalSyncer::new(&mut target, ctx.client.clone())
                .sync()
                .await?;
            assert_ne!(
                target.get_deck_config(DeckConfigId(1), false)?.unwrap().usn,
                Usn(-1)
            );
            Ok(())
        })
        .await
    }

    #[tokio::test]
    async fn normal_sync_upgrades_received_empty_presets_and_uploads_rebuilt_state() -> Result<()> {
        with_active_server(|client| async move {
            let ctx = SyncTestContext::new(client);
            let mut source = ctx.col1();
            let note = NoteAdder::basic(&mut source).add(&mut source);
            let mut card = source.storage.all_cards_of_note(note.id)?.remove(0);
            card.ctype = CardType::Review;
            card.queue = CardQueue::Review;
            card.interval = 20;
            card.due = 100;
            card.memory_state = Some(FsrsMemoryState {
                stability: 10.0,
                stability_internal: 10.0,
                stability_fast: None,
                difficulty: 5.0,
            });
            source.storage.update_card(&card)?;
            source.full_upload_with_server(ctx.client.clone()).await?;
            ctx.col2()
                .full_download_with_server(ctx.client.clone())
                .await?;
            let mut source = ctx.col1();
            let mut target = ctx.col2();

            // Simulate an older client's normal sync, without the new migration
            // hooks that surround the unchanged protocol exchange.
            let mut config = source.get_deck_config(DeckConfigId(1), false)?.unwrap();
            config.clear_fsrs_params();
            source.add_or_update_deck_config(&mut config)?;
            let mut sender = NormalSyncer::new(&mut source, ctx.client.clone());
            let state =
                online_sync_status_check(sender.col.sync_meta()?, &mut sender.server).await?;
            sender.col.storage.begin_trx()?;
            sender.normal_sync_inner(state).await?;
            sender.col.storage.commit_trx()?;

            NormalSyncer::new(&mut target, ctx.client.clone())
                .sync()
                .await?;
            let upgraded = target.storage.get_card(card.id)?.unwrap();
            assert!(upgraded.memory_state.unwrap().stability_fast.is_some());
            assert_eq!((upgraded.due, upgraded.interval), (100, 20));
            assert_eq!(upgraded.usn, Usn(-1));
            let preset = target.get_deck_config(DeckConfigId(1), false)?.unwrap();
            assert_eq!(preset.inner.fsrs_params_7, fsrs::DEFAULT_PARAMETERS);
            assert_eq!(preset.usn, Usn(-1));
            assert_eq!(
                target.sync_status_offline()?,
                anki_proto::sync::sync_status_response::Required::NormalSync
            );

            NormalSyncer::new(&mut target, ctx.client.clone())
                .sync()
                .await?;
            assert_ne!(target.storage.get_card(card.id)?.unwrap().usn, Usn(-1));
            assert_ne!(
                target.get_deck_config(DeckConfigId(1), false)?.unwrap().usn,
                Usn(-1)
            );
            assert_eq!(
                target.sync_status_offline()?,
                anki_proto::sync::sync_status_response::Required::NoChanges
            );
            Ok(())
        })
        .await
    }
}
