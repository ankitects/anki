// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod http;
mod http_client;
mod server;

use std::collections::HashMap;

use http_client::HttpSyncClient;
pub use http_client::{FullSyncProgressFn, Timeouts};
use itertools::Itertools;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use serde_tuple::Serialize_tuple;
pub(crate) use server::{LocalServer, SyncServer};

use crate::{
    backend_proto::{sync_status_response, SyncStatusResponse},
    card::{Card, CardQueue, CardType},
    deckconfig::DeckConfSchema11,
    decks::DeckSchema11,
    error::{SyncError, SyncErrorKind},
    notes::Note,
    notetype::{Notetype, NotetypeSchema11},
    prelude::*,
    revlog::RevlogEntry,
    serde::{default_on_invalid, deserialize_int_from_number},
    storage::open_and_check_sqlite_file,
    tags::{join_tags, split_tags, Tag},
};

pub static SYNC_VERSION_MIN: u8 = 7;
pub static SYNC_VERSION_MAX: u8 = 10;

#[derive(Default, Debug, Clone, Copy)]
pub struct NormalSyncProgress {
    pub stage: SyncStage,
    pub local_update: usize,
    pub local_remove: usize,
    pub remote_update: usize,
    pub remote_remove: usize,
}

#[derive(Debug, Clone, Copy, PartialEq)]
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

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct SyncMeta {
    #[serde(rename = "mod")]
    pub modified: TimestampMillis,
    #[serde(rename = "scm")]
    pub schema: TimestampMillis,
    pub usn: Usn,
    #[serde(rename = "ts")]
    pub current_time: TimestampSecs,
    #[serde(rename = "msg")]
    pub server_message: String,
    #[serde(rename = "cont")]
    pub should_continue: bool,
    #[serde(rename = "hostNum")]
    pub host_number: u32,
    #[serde(default)]
    pub empty: bool,
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct Graves {
    pub(crate) cards: Vec<CardId>,
    pub(crate) decks: Vec<DeckId>,
    pub(crate) notes: Vec<NoteId>,
}

#[derive(Serialize_tuple, Deserialize, Debug, Default)]
pub struct DecksAndConfig {
    decks: Vec<DeckSchema11>,
    config: Vec<DeckConfSchema11>,
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct UnchunkedChanges {
    #[serde(rename = "models")]
    notetypes: Vec<NotetypeSchema11>,
    #[serde(rename = "decks")]
    decks_and_config: DecksAndConfig,
    tags: Vec<String>,

    // the following are only sent if local is newer
    #[serde(skip_serializing_if = "Option::is_none", rename = "conf")]
    config: Option<HashMap<String, Value>>,
    #[serde(skip_serializing_if = "Option::is_none", rename = "crt")]
    creation_stamp: Option<TimestampSecs>,
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct Chunk {
    #[serde(default)]
    done: bool,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    revlog: Vec<RevlogEntry>,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    cards: Vec<CardEntry>,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    notes: Vec<NoteEntry>,
}

struct ChunkableIds {
    revlog: Vec<RevlogId>,
    cards: Vec<CardId>,
    notes: Vec<NoteId>,
}

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct NoteEntry {
    pub id: NoteId,
    pub guid: String,
    #[serde(rename = "mid")]
    pub ntid: NotetypeId,
    #[serde(rename = "mod")]
    pub mtime: TimestampSecs,
    pub usn: Usn,
    pub tags: String,
    pub fields: String,
    pub sfld: String, // always empty
    pub csum: String, // always empty
    pub flags: u32,
    pub data: String,
}

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct CardEntry {
    pub id: CardId,
    pub nid: NoteId,
    pub did: DeckId,
    pub ord: u16,
    #[serde(deserialize_with = "deserialize_int_from_number")]
    pub mtime: TimestampSecs,
    pub usn: Usn,
    pub ctype: CardType,
    pub queue: CardQueue,
    #[serde(deserialize_with = "deserialize_int_from_number")]
    pub due: i32,
    #[serde(deserialize_with = "deserialize_int_from_number")]
    pub ivl: u32,
    pub factor: u16,
    pub reps: u32,
    pub lapses: u32,
    pub left: u32,
    #[serde(deserialize_with = "deserialize_int_from_number")]
    pub odue: i32,
    pub odid: DeckId,
    pub flags: u8,
    pub data: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct SanityCheckResponse {
    pub status: SanityCheckStatus,
    #[serde(rename = "c", default, deserialize_with = "default_on_invalid")]
    pub client: Option<SanityCheckCounts>,
    #[serde(rename = "s", default, deserialize_with = "default_on_invalid")]
    pub server: Option<SanityCheckCounts>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum SanityCheckStatus {
    Ok,
    Bad,
}

#[derive(Serialize_tuple, Deserialize, Debug, PartialEq)]
pub struct SanityCheckCounts {
    pub counts: SanityCheckDueCounts,
    pub cards: u32,
    pub notes: u32,
    pub revlog: u32,
    pub graves: u32,
    #[serde(rename = "models")]
    pub notetypes: u32,
    pub decks: u32,
    pub deck_config: u32,
}

#[derive(Serialize_tuple, Deserialize, Debug, Default, PartialEq)]
pub struct SanityCheckDueCounts {
    pub new: u32,
    pub learn: u32,
    pub review: u32,
}

#[derive(Debug, Default, Clone, Copy)]
pub struct FullSyncProgress {
    pub transferred_bytes: usize,
    pub total_bytes: usize,
}

#[derive(PartialEq, Debug, Clone, Copy)]
pub enum SyncActionRequired {
    NoChanges,
    FullSyncRequired { upload_ok: bool, download_ok: bool },
    NormalSyncRequired,
}
#[derive(Debug)]
struct SyncState {
    required: SyncActionRequired,
    local_is_newer: bool,
    usn_at_last_sync: Usn,
    // latest usn, used for adding new items
    latest_usn: Usn,
    // usn to use when locating pending objects
    pending_usn: Usn,
    // usn to replace pending items with - the same as latest_usn in the client case
    new_usn: Option<Usn>,
    server_message: String,
    host_number: u32,
}

#[derive(Debug)]
pub struct SyncOutput {
    pub required: SyncActionRequired,
    pub server_message: String,
    pub host_number: u32,
}

#[derive(Clone)]
pub struct SyncAuth {
    pub hkey: String,
    pub host_number: u32,
}

struct NormalSyncer<'a, F> {
    col: &'a mut Collection,
    remote: Box<dyn SyncServer>,
    progress: NormalSyncProgress,
    progress_fn: F,
}

impl Usn {
    /// Used when gathering pending objects during sync.
    pub(crate) fn pending_object_clause(self) -> &'static str {
        if self.0 == -1 {
            "usn = ?"
        } else {
            "usn >= ?"
        }
    }

    pub(crate) fn is_pending_sync(self, pending_usn: Usn) -> bool {
        if pending_usn.0 == -1 {
            self.0 == -1
        } else {
            self.0 >= pending_usn.0
        }
    }
}

impl SyncMeta {
    fn compared_to_remote(&self, remote: SyncMeta) -> SyncState {
        let local = self;
        let required = if remote.modified == local.modified {
            SyncActionRequired::NoChanges
        } else if remote.schema != local.schema {
            let upload_ok = !local.empty || remote.empty;
            let download_ok = !remote.empty || local.empty;
            SyncActionRequired::FullSyncRequired {
                upload_ok,
                download_ok,
            }
        } else {
            SyncActionRequired::NormalSyncRequired
        };

        SyncState {
            required,
            local_is_newer: local.modified > remote.modified,
            usn_at_last_sync: local.usn,
            latest_usn: remote.usn,
            pending_usn: Usn(-1),
            new_usn: Some(remote.usn),
            server_message: remote.server_message,
            host_number: remote.host_number,
        }
    }
}

impl<F> NormalSyncer<'_, F>
where
    F: FnMut(NormalSyncProgress, bool),
{
    pub fn new(
        col: &mut Collection,
        server: Box<dyn SyncServer>,
        progress_fn: F,
    ) -> NormalSyncer<'_, F>
    where
        F: FnMut(NormalSyncProgress, bool),
    {
        NormalSyncer {
            col,
            remote: server,
            progress: NormalSyncProgress::default(),
            progress_fn,
        }
    }

    fn fire_progress_cb(&mut self, throttle: bool) {
        (self.progress_fn)(self.progress, throttle)
    }

    pub async fn sync(&mut self) -> Result<SyncOutput> {
        debug!(self.col.log, "fetching meta...");
        self.fire_progress_cb(false);
        let state: SyncState = self.get_sync_state().await?;
        debug!(self.col.log, "fetched"; "state"=>?&state);
        match state.required {
            SyncActionRequired::NoChanges => Ok(state.into()),
            SyncActionRequired::FullSyncRequired { .. } => Ok(state.into()),
            SyncActionRequired::NormalSyncRequired => {
                self.col.discard_undo_and_study_queues();
                self.col.storage.begin_trx()?;
                let timing = self.col.timing_today()?;
                self.col.unbury_if_day_rolled_over(timing)?;
                match self.normal_sync_inner(state).await {
                    Ok(success) => {
                        self.col.storage.commit_trx()?;
                        Ok(success)
                    }
                    Err(e) => {
                        self.col.storage.rollback_trx()?;
                        let _ = self.remote.abort().await;

                        if let AnkiError::SyncError(SyncError {
                            info,
                            kind: SyncErrorKind::DatabaseCheckRequired,
                        }) = &e
                        {
                            debug!(self.col.log, "sanity check failed:\n{}", info);
                        }

                        Err(e)
                    }
                }
            }
        }
    }

    async fn get_sync_state(&self) -> Result<SyncState> {
        let remote: SyncMeta = self.remote.meta().await?;
        debug!(self.col.log, "remote {:?}", &remote);
        if !remote.should_continue {
            debug!(self.col.log, "server says abort"; "message"=>&remote.server_message);
            return Err(AnkiError::sync_error(
                remote.server_message,
                SyncErrorKind::ServerMessage,
            ));
        }

        let local = self.col.sync_meta()?;
        debug!(self.col.log, "local {:?}", &local);
        let delta = remote.current_time.0 - local.current_time.0;
        if delta.abs() > 300 {
            debug!(self.col.log, "clock off"; "delta"=>delta);
            return Err(AnkiError::sync_error("", SyncErrorKind::ClockIncorrect));
        }

        Ok(local.compared_to_remote(remote))
    }

    /// Sync. Caller must have created a transaction, and should call
    /// abort on failure.
    async fn normal_sync_inner(&mut self, mut state: SyncState) -> Result<SyncOutput> {
        self.progress.stage = SyncStage::Syncing;
        self.fire_progress_cb(false);

        debug!(self.col.log, "start");
        self.start_and_process_deletions(&state).await?;
        debug!(self.col.log, "unchunked changes");
        self.process_unchunked_changes(&state).await?;
        debug!(self.col.log, "begin stream from server");
        self.process_chunks_from_server(&state).await?;
        debug!(self.col.log, "begin stream to server");
        self.send_chunks_to_server(&state).await?;

        self.progress.stage = SyncStage::Finalizing;
        self.fire_progress_cb(false);

        debug!(self.col.log, "sanity check");
        self.sanity_check().await?;
        debug!(self.col.log, "finalize");
        self.finalize(&state).await?;
        state.required = SyncActionRequired::NoChanges;
        Ok(state.into())
    }

    // The following operations assume a transaction has been set up.

    async fn start_and_process_deletions(&mut self, state: &SyncState) -> Result<()> {
        let remote: Graves = self
            .remote
            .start(state.usn_at_last_sync, state.local_is_newer, None)
            .await?;

        debug!(self.col.log, "removed on remote";
            "cards"=>remote.cards.len(),
            "notes"=>remote.notes.len(),
            "decks"=>remote.decks.len());

        let mut local = self.col.storage.pending_graves(state.pending_usn)?;
        if let Some(new_usn) = state.new_usn {
            self.col.storage.update_pending_grave_usns(new_usn)?;
        }

        debug!(self.col.log, "locally removed  ";
            "cards"=>local.cards.len(),
            "notes"=>local.notes.len(),
            "decks"=>local.decks.len());

        while let Some(chunk) = local.take_chunk() {
            debug!(self.col.log, "sending graves chunk");
            self.progress.local_remove += chunk.cards.len() + chunk.notes.len() + chunk.decks.len();
            self.remote.apply_graves(chunk).await?;
            self.fire_progress_cb(true);
        }

        self.progress.remote_remove = remote.cards.len() + remote.notes.len() + remote.decks.len();
        self.col.apply_graves(remote, state.latest_usn)?;
        self.fire_progress_cb(true);
        debug!(self.col.log, "applied server graves");

        Ok(())
    }

    // This was assumed to a cheap operation when originally written - it didn't anticipate
    // the large deck trees and note types some users would create. They should be chunked
    // in the future, like other objects. Syncing tags explicitly is also probably of limited
    // usefulness.
    async fn process_unchunked_changes(&mut self, state: &SyncState) -> Result<()> {
        debug!(self.col.log, "gathering local changes");
        let local = self.col.local_unchunked_changes(
            state.pending_usn,
            state.new_usn,
            state.local_is_newer,
        )?;

        debug!(self.col.log, "sending";
            "notetypes"=>local.notetypes.len(),
            "decks"=>local.decks_and_config.decks.len(),
            "deck config"=>local.decks_and_config.config.len(),
            "tags"=>local.tags.len(),
        );

        self.progress.local_update += local.notetypes.len()
            + local.decks_and_config.decks.len()
            + local.decks_and_config.config.len()
            + local.tags.len();
        let remote = self.remote.apply_changes(local).await?;
        self.fire_progress_cb(true);

        debug!(self.col.log, "received";
            "notetypes"=>remote.notetypes.len(),
            "decks"=>remote.decks_and_config.decks.len(),
            "deck config"=>remote.decks_and_config.config.len(),
            "tags"=>remote.tags.len(),
        );

        self.progress.remote_update += remote.notetypes.len()
            + remote.decks_and_config.decks.len()
            + remote.decks_and_config.config.len()
            + remote.tags.len();

        self.col.apply_changes(remote, state.latest_usn)?;
        self.fire_progress_cb(true);
        Ok(())
    }

    async fn process_chunks_from_server(&mut self, state: &SyncState) -> Result<()> {
        loop {
            let chunk: Chunk = self.remote.chunk().await?;

            debug!(self.col.log, "received";
                "done"=>chunk.done,
                "cards"=>chunk.cards.len(),
                "notes"=>chunk.notes.len(),
                "revlog"=>chunk.revlog.len(),
            );

            self.progress.remote_update +=
                chunk.cards.len() + chunk.notes.len() + chunk.revlog.len();

            let done = chunk.done;
            self.col.apply_chunk(chunk, state.pending_usn)?;

            self.fire_progress_cb(true);

            if done {
                return Ok(());
            }
        }
    }

    async fn send_chunks_to_server(&mut self, state: &SyncState) -> Result<()> {
        let mut ids = self.col.get_chunkable_ids(state.pending_usn)?;

        loop {
            let chunk: Chunk = self.col.get_chunk(&mut ids, state.new_usn)?;
            let done = chunk.done;

            debug!(self.col.log, "sending";
                "done"=>chunk.done,
                "cards"=>chunk.cards.len(),
                "notes"=>chunk.notes.len(),
                "revlog"=>chunk.revlog.len(),
            );

            self.progress.local_update +=
                chunk.cards.len() + chunk.notes.len() + chunk.revlog.len();

            self.remote.apply_chunk(chunk).await?;

            self.fire_progress_cb(true);

            if done {
                return Ok(());
            }
        }
    }

    /// Caller should force full sync after rolling back.
    async fn sanity_check(&mut self) -> Result<()> {
        let mut local_counts = self.col.storage.sanity_check_info()?;
        self.col.add_due_counts(&mut local_counts.counts)?;

        debug!(
            self.col.log,
            "gathered local counts; waiting for server reply"
        );
        let out: SanityCheckResponse = self.remote.sanity_check(local_counts).await?;
        debug!(self.col.log, "got server reply");
        if out.status != SanityCheckStatus::Ok {
            Err(AnkiError::sync_error(
                format!("local {:?}\nremote {:?}", out.client, out.server),
                SyncErrorKind::DatabaseCheckRequired,
            ))
        } else {
            Ok(())
        }
    }

    async fn finalize(&mut self, state: &SyncState) -> Result<()> {
        let new_server_mtime = self.remote.finish().await?;
        self.col.finalize_sync(state, new_server_mtime)
    }
}

const CHUNK_SIZE: usize = 250;

impl Graves {
    fn take_chunk(&mut self) -> Option<Graves> {
        let mut limit = CHUNK_SIZE;
        let mut out = Graves::default();
        while limit > 0 && !self.cards.is_empty() {
            out.cards.push(self.cards.pop().unwrap());
            limit -= 1;
        }
        while limit > 0 && !self.notes.is_empty() {
            out.notes.push(self.notes.pop().unwrap());
            limit -= 1;
        }
        while limit > 0 && !self.decks.is_empty() {
            out.decks.push(self.decks.pop().unwrap());
            limit -= 1;
        }
        if limit == CHUNK_SIZE {
            None
        } else {
            Some(out)
        }
    }
}

pub async fn sync_login(username: &str, password: &str) -> Result<SyncAuth> {
    let mut remote = HttpSyncClient::new(None, 0);
    remote.login(username, password).await?;
    Ok(SyncAuth {
        hkey: remote.hkey().to_string(),
        host_number: 0,
    })
}

pub async fn sync_abort(hkey: String, host_number: u32) -> Result<()> {
    let mut remote = HttpSyncClient::new(Some(hkey), host_number);
    remote.abort().await
}

pub(crate) async fn get_remote_sync_meta(auth: SyncAuth) -> Result<SyncMeta> {
    let remote = HttpSyncClient::new(Some(auth.hkey), auth.host_number);
    remote.meta().await
}

impl Collection {
    pub fn get_local_sync_status(&mut self) -> Result<sync_status_response::Required> {
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

    pub fn get_sync_status(&self, remote: SyncMeta) -> Result<sync_status_response::Required> {
        Ok(self.sync_meta()?.compared_to_remote(remote).required.into())
    }

    /// Create a new syncing instance. If host_number is unavailable, use 0.
    pub async fn normal_sync<F>(&mut self, auth: SyncAuth, progress_fn: F) -> Result<SyncOutput>
    where
        F: FnMut(NormalSyncProgress, bool),
    {
        NormalSyncer::new(
            self,
            Box::new(HttpSyncClient::new(Some(auth.hkey), auth.host_number)),
            progress_fn,
        )
        .sync()
        .await
    }

    /// Upload collection to AnkiWeb. Caller must re-open afterwards.
    pub async fn full_upload(self, auth: SyncAuth, progress_fn: FullSyncProgressFn) -> Result<()> {
        let mut server = HttpSyncClient::new(Some(auth.hkey), auth.host_number);
        server.set_full_sync_progress_fn(Some(progress_fn));
        self.full_upload_inner(Box::new(server)).await
    }

    pub(crate) async fn full_upload_inner(mut self, server: Box<dyn SyncServer>) -> Result<()> {
        self.before_upload()?;
        let col_path = self.col_path.clone();
        self.close(true)?;
        server.full_upload(&col_path, false).await
    }

    /// Download collection from AnkiWeb. Caller must re-open afterwards.
    pub async fn full_download(
        self,
        auth: SyncAuth,
        progress_fn: FullSyncProgressFn,
    ) -> Result<()> {
        let mut server = HttpSyncClient::new(Some(auth.hkey), auth.host_number);
        server.set_full_sync_progress_fn(Some(progress_fn));
        self.full_download_inner(Box::new(server)).await
    }

    pub(crate) async fn full_download_inner(self, server: Box<dyn SyncServer>) -> Result<()> {
        let col_path = self.col_path.clone();
        let col_folder = col_path
            .parent()
            .ok_or_else(|| AnkiError::invalid_input("couldn't get col_folder"))?;
        self.close(false)?;
        let out_file = server.full_download(Some(col_folder)).await?;
        // check file ok
        let db = open_and_check_sqlite_file(out_file.path())?;
        db.execute_batch("update col set ls=mod")?;
        drop(db);
        // overwrite existing collection atomically
        out_file
            .persist(&col_path)
            .map_err(|e| AnkiError::IoError(format!("download save failed: {}", e)))?;
        Ok(())
    }

    fn sync_meta(&self) -> Result<SyncMeta> {
        let stamps = self.storage.get_collection_timestamps()?;
        Ok(SyncMeta {
            modified: stamps.collection_change,
            schema: stamps.schema_change,
            usn: self.storage.usn(true)?,
            current_time: TimestampSecs::now(),
            server_message: "".into(),
            should_continue: true,
            host_number: 0,
            empty: !self.storage.have_at_least_one_card()?,
        })
    }

    pub fn apply_graves(&self, graves: Graves, latest_usn: Usn) -> Result<()> {
        for nid in graves.notes {
            self.storage.remove_note(nid)?;
            self.storage.add_note_grave(nid, latest_usn)?;
        }
        for cid in graves.cards {
            self.storage.remove_card(cid)?;
            self.storage.add_card_grave(cid, latest_usn)?;
        }
        for did in graves.decks {
            self.storage.remove_deck(did)?;
            self.storage.add_deck_grave(did, latest_usn)?;
        }
        Ok(())
    }

    // Local->remote unchunked changes
    //----------------------------------------------------------------

    fn local_unchunked_changes(
        &mut self,
        pending_usn: Usn,
        new_usn: Option<Usn>,
        local_is_newer: bool,
    ) -> Result<UnchunkedChanges> {
        let mut changes = UnchunkedChanges {
            notetypes: self.changed_notetypes(pending_usn, new_usn)?,
            decks_and_config: DecksAndConfig {
                decks: self.changed_decks(pending_usn, new_usn)?,
                config: self.changed_deck_config(pending_usn, new_usn)?,
            },
            tags: self.changed_tags(pending_usn, new_usn)?,
            ..Default::default()
        };
        if local_is_newer {
            changes.config = Some(self.changed_config()?);
            changes.creation_stamp = Some(self.storage.creation_stamp()?);
        }

        Ok(changes)
    }

    fn changed_notetypes(
        &mut self,
        pending_usn: Usn,
        new_usn: Option<Usn>,
    ) -> Result<Vec<NotetypeSchema11>> {
        let ids = self
            .storage
            .objects_pending_sync("notetypes", pending_usn)?;
        self.storage
            .maybe_update_object_usns("notetypes", &ids, new_usn)?;
        self.state.notetype_cache.clear();
        ids.into_iter()
            .map(|id| {
                self.storage.get_notetype(id).map(|opt| {
                    let mut nt: NotetypeSchema11 = opt.unwrap().into();
                    nt.usn = new_usn.unwrap_or(nt.usn);
                    nt
                })
            })
            .collect()
    }

    fn changed_decks(
        &mut self,
        pending_usn: Usn,
        new_usn: Option<Usn>,
    ) -> Result<Vec<DeckSchema11>> {
        let ids = self.storage.objects_pending_sync("decks", pending_usn)?;
        self.storage
            .maybe_update_object_usns("decks", &ids, new_usn)?;
        self.state.deck_cache.clear();
        ids.into_iter()
            .map(|id| {
                self.storage.get_deck(id).map(|opt| {
                    let mut deck = opt.unwrap();
                    deck.usn = new_usn.unwrap_or(deck.usn);
                    deck.into()
                })
            })
            .collect()
    }

    fn changed_deck_config(
        &self,
        pending_usn: Usn,
        new_usn: Option<Usn>,
    ) -> Result<Vec<DeckConfSchema11>> {
        let ids = self
            .storage
            .objects_pending_sync("deck_config", pending_usn)?;
        self.storage
            .maybe_update_object_usns("deck_config", &ids, new_usn)?;
        ids.into_iter()
            .map(|id| {
                self.storage.get_deck_config(id).map(|opt| {
                    let mut conf: DeckConfSchema11 = opt.unwrap().into();
                    conf.usn = new_usn.unwrap_or(conf.usn);
                    conf
                })
            })
            .collect()
    }

    fn changed_tags(&self, pending_usn: Usn, new_usn: Option<Usn>) -> Result<Vec<String>> {
        let changed = self.storage.tags_pending_sync(pending_usn)?;
        if let Some(usn) = new_usn {
            self.storage.update_tag_usns(&changed, usn)?;
        }
        Ok(changed)
    }

    /// Currently this is all config, as legacy clients overwrite the local items
    /// with the provided value.
    fn changed_config(&self) -> Result<HashMap<String, Value>> {
        let conf = self.storage.get_all_config()?;
        self.storage.clear_config_usns()?;
        Ok(conf)
    }

    // Remote->local unchunked changes
    //----------------------------------------------------------------

    fn apply_changes(&mut self, remote: UnchunkedChanges, latest_usn: Usn) -> Result<()> {
        self.merge_notetypes(remote.notetypes, latest_usn)?;
        self.merge_decks(remote.decks_and_config.decks, latest_usn)?;
        self.merge_deck_config(remote.decks_and_config.config)?;
        self.merge_tags(remote.tags, latest_usn)?;
        if let Some(crt) = remote.creation_stamp {
            self.set_creation_stamp(crt)?;
        }
        if let Some(config) = remote.config {
            self.storage
                .set_all_config(config, latest_usn, TimestampSecs::now())?;
        }

        Ok(())
    }

    fn merge_notetypes(&mut self, notetypes: Vec<NotetypeSchema11>, latest_usn: Usn) -> Result<()> {
        for nt in notetypes {
            let mut nt: Notetype = nt.into();
            let proceed = if let Some(existing_nt) = self.storage.get_notetype(nt.id)? {
                if existing_nt.mtime_secs <= nt.mtime_secs {
                    if (existing_nt.fields.len() != nt.fields.len())
                        || (existing_nt.templates.len() != nt.templates.len())
                    {
                        return Err(AnkiError::sync_error(
                            "notetype schema changed",
                            SyncErrorKind::ResyncRequired,
                        ));
                    }
                    true
                } else {
                    false
                }
            } else {
                true
            };
            if proceed {
                self.ensure_notetype_name_unique(&mut nt, latest_usn)?;
                self.storage.add_or_update_notetype_with_existing_id(&nt)?;
                self.state.notetype_cache.remove(&nt.id);
            }
        }
        Ok(())
    }

    fn merge_decks(&mut self, decks: Vec<DeckSchema11>, latest_usn: Usn) -> Result<()> {
        for deck in decks {
            let proceed = if let Some(existing_deck) = self.storage.get_deck(deck.id())? {
                existing_deck.mtime_secs <= deck.common().mtime
            } else {
                true
            };
            if proceed {
                let mut deck = deck.into();
                self.ensure_deck_name_unique(&mut deck, latest_usn)?;
                self.storage.add_or_update_deck_with_existing_id(&deck)?;
                self.state.deck_cache.remove(&deck.id);
            }
        }
        Ok(())
    }

    fn merge_deck_config(&self, dconf: Vec<DeckConfSchema11>) -> Result<()> {
        for conf in dconf {
            let proceed = if let Some(existing_conf) = self.storage.get_deck_config(conf.id)? {
                existing_conf.mtime_secs <= conf.mtime
            } else {
                true
            };
            if proceed {
                let conf = conf.into();
                self.storage
                    .add_or_update_deck_config_with_existing_id(&conf)?;
            }
        }
        Ok(())
    }

    fn merge_tags(&mut self, tags: Vec<String>, latest_usn: Usn) -> Result<()> {
        for tag in tags {
            self.register_tag(&mut Tag::new(tag, latest_usn))?;
        }
        Ok(())
    }

    // Remote->local chunks
    //----------------------------------------------------------------

    /// pending_usn is used to decide whether the local objects are newer.
    /// If the provided objects are not modified locally, the USN inside
    /// the individual objects is used.
    fn apply_chunk(&mut self, chunk: Chunk, pending_usn: Usn) -> Result<()> {
        self.merge_revlog(chunk.revlog)?;
        self.merge_cards(chunk.cards, pending_usn)?;
        self.merge_notes(chunk.notes, pending_usn)
    }

    fn merge_revlog(&self, entries: Vec<RevlogEntry>) -> Result<()> {
        for entry in entries {
            self.storage.add_revlog_entry(&entry, false)?;
        }
        Ok(())
    }

    fn merge_cards(&self, entries: Vec<CardEntry>, pending_usn: Usn) -> Result<()> {
        for entry in entries {
            self.add_or_update_card_if_newer(entry, pending_usn)?;
        }
        Ok(())
    }

    fn add_or_update_card_if_newer(&self, entry: CardEntry, pending_usn: Usn) -> Result<()> {
        let proceed = if let Some(existing_card) = self.storage.get_card(entry.id)? {
            !existing_card.usn.is_pending_sync(pending_usn) || existing_card.mtime < entry.mtime
        } else {
            true
        };
        if proceed {
            let card = entry.into();
            self.storage.add_or_update_card(&card)?;
        }
        Ok(())
    }

    fn merge_notes(&mut self, entries: Vec<NoteEntry>, pending_usn: Usn) -> Result<()> {
        for entry in entries {
            self.add_or_update_note_if_newer(entry, pending_usn)?;
        }
        Ok(())
    }

    fn add_or_update_note_if_newer(&mut self, entry: NoteEntry, pending_usn: Usn) -> Result<()> {
        let proceed = if let Some(existing_note) = self.storage.get_note(entry.id)? {
            !existing_note.usn.is_pending_sync(pending_usn) || existing_note.mtime < entry.mtime
        } else {
            true
        };
        if proceed {
            let mut note: Note = entry.into();
            let nt = self
                .get_notetype(note.notetype_id)?
                .ok_or_else(|| AnkiError::invalid_input("note missing notetype"))?;
            note.prepare_for_update(&nt, false)?;
            self.storage.add_or_update_note(&note)?;
        }
        Ok(())
    }

    // Local->remote chunks
    //----------------------------------------------------------------

    fn get_chunkable_ids(&self, pending_usn: Usn) -> Result<ChunkableIds> {
        Ok(ChunkableIds {
            revlog: self.storage.objects_pending_sync("revlog", pending_usn)?,
            cards: self.storage.objects_pending_sync("cards", pending_usn)?,
            notes: self.storage.objects_pending_sync("notes", pending_usn)?,
        })
    }

    /// Fetch a chunk of ids from `ids`, returning the referenced objects.
    fn get_chunk(&self, ids: &mut ChunkableIds, new_usn: Option<Usn>) -> Result<Chunk> {
        // get a bunch of IDs
        let mut limit = CHUNK_SIZE as i32;
        let mut revlog_ids = vec![];
        let mut card_ids = vec![];
        let mut note_ids = vec![];
        let mut chunk = Chunk::default();
        while limit > 0 {
            let last_limit = limit;
            if let Some(id) = ids.revlog.pop() {
                revlog_ids.push(id);
                limit -= 1;
            }
            if let Some(id) = ids.notes.pop() {
                note_ids.push(id);
                limit -= 1;
            }
            if let Some(id) = ids.cards.pop() {
                card_ids.push(id);
                limit -= 1;
            }
            if limit == last_limit {
                // all empty
                break;
            }
        }
        if limit > 0 {
            chunk.done = true;
        }

        // remove pending status
        if !self.server {
            self.storage
                .maybe_update_object_usns("revlog", &revlog_ids, new_usn)?;
            self.storage
                .maybe_update_object_usns("cards", &card_ids, new_usn)?;
            self.storage
                .maybe_update_object_usns("notes", &note_ids, new_usn)?;
        }

        // the fetch associated objects, and return
        chunk.revlog = revlog_ids
            .into_iter()
            .map(|id| {
                self.storage.get_revlog_entry(id).map(|e| {
                    let mut e = e.unwrap();
                    e.usn = new_usn.unwrap_or(e.usn);
                    e
                })
            })
            .collect::<Result<_>>()?;
        chunk.cards = card_ids
            .into_iter()
            .map(|id| {
                self.storage.get_card(id).map(|e| {
                    let mut e: CardEntry = e.unwrap().into();
                    e.usn = new_usn.unwrap_or(e.usn);
                    e
                })
            })
            .collect::<Result<_>>()?;
        chunk.notes = note_ids
            .into_iter()
            .map(|id| {
                self.storage.get_note(id).map(|e| {
                    let mut e: NoteEntry = e.unwrap().into();
                    e.usn = new_usn.unwrap_or(e.usn);
                    e
                })
            })
            .collect::<Result<_>>()?;

        Ok(chunk)
    }

    // Final steps
    //----------------------------------------------------------------

    fn add_due_counts(&mut self, counts: &mut SanityCheckDueCounts) -> Result<()> {
        if let Some(tree) = self.current_deck_tree()? {
            counts.new = tree.new_count;
            counts.review = tree.review_count;
            counts.learn = tree.learn_count;
        }
        Ok(())
    }

    fn finalize_sync(&self, state: &SyncState, new_server_mtime: TimestampMillis) -> Result<()> {
        self.storage.set_last_sync(new_server_mtime)?;
        let mut usn = state.latest_usn;
        usn.0 += 1;
        self.storage.set_usn(usn)?;
        self.storage.set_modified_time(new_server_mtime)
    }
}

impl From<CardEntry> for Card {
    fn from(e: CardEntry) -> Self {
        Card {
            id: e.id,
            note_id: e.nid,
            deck_id: e.did,
            template_idx: e.ord,
            mtime: e.mtime,
            usn: e.usn,
            ctype: e.ctype,
            queue: e.queue,
            due: e.due,
            interval: e.ivl,
            ease_factor: e.factor,
            reps: e.reps,
            lapses: e.lapses,
            remaining_steps: e.left,
            original_due: e.odue,
            original_deck_id: e.odid,
            flags: e.flags,
            data: e.data,
        }
    }
}

impl From<Card> for CardEntry {
    fn from(e: Card) -> Self {
        CardEntry {
            id: e.id,
            nid: e.note_id,
            did: e.deck_id,
            ord: e.template_idx,
            mtime: e.mtime,
            usn: e.usn,
            ctype: e.ctype,
            queue: e.queue,
            due: e.due,
            ivl: e.interval,
            factor: e.ease_factor,
            reps: e.reps,
            lapses: e.lapses,
            left: e.remaining_steps,
            odue: e.original_due,
            odid: e.original_deck_id,
            flags: e.flags,
            data: e.data,
        }
    }
}

impl From<NoteEntry> for Note {
    fn from(e: NoteEntry) -> Self {
        let fields = e.fields.split('\x1f').map(ToString::to_string).collect();
        Note::new_from_storage(
            e.id,
            e.guid,
            e.ntid,
            e.mtime,
            e.usn,
            split_tags(&e.tags).map(ToString::to_string).collect(),
            fields,
            None,
            None,
        )
    }
}

impl From<Note> for NoteEntry {
    fn from(e: Note) -> Self {
        NoteEntry {
            id: e.id,
            fields: e.fields().iter().join("\x1f"),
            guid: e.guid,
            ntid: e.notetype_id,
            mtime: e.mtime,
            usn: e.usn,
            tags: join_tags(&e.tags),
            sfld: String::new(),
            csum: String::new(),
            flags: 0,
            data: String::new(),
        }
    }
}

impl From<SyncState> for SyncOutput {
    fn from(s: SyncState) -> Self {
        SyncOutput {
            required: s.required,
            server_message: s.server_message,
            host_number: s.host_number,
        }
    }
}

impl From<sync_status_response::Required> for SyncStatusResponse {
    fn from(r: sync_status_response::Required) -> Self {
        SyncStatusResponse { required: r.into() }
    }
}

impl From<SyncActionRequired> for sync_status_response::Required {
    fn from(r: SyncActionRequired) -> Self {
        match r {
            SyncActionRequired::NoChanges => sync_status_response::Required::NoChanges,
            SyncActionRequired::FullSyncRequired { .. } => sync_status_response::Required::FullSync,
            SyncActionRequired::NormalSyncRequired => sync_status_response::Required::NormalSync,
        }
    }
}

#[cfg(test)]
mod test {
    use std::path::Path;

    use async_trait::async_trait;
    use lazy_static::lazy_static;
    use tempfile::{tempdir, TempDir};
    use tokio::runtime::Runtime;

    use super::{server::LocalServer, *};
    use crate::{
        collection::open_collection, deckconfig::DeckConfig, decks::DeckKind, i18n::I18n, log,
        notetype::all_stock_notetypes, search::SortMode,
    };

    fn norm_progress(_: NormalSyncProgress, _: bool) {}

    fn full_progress(_: FullSyncProgress, _: bool) {}

    #[test]
    /// Run remote tests if hkey provided in environment; otherwise local.
    fn syncing() -> Result<()> {
        let ctx: Box<dyn TestContext> = if let Ok(hkey) = std::env::var("TEST_HKEY") {
            Box::new(RemoteTestContext {
                auth: SyncAuth {
                    hkey,
                    host_number: 0,
                },
            })
        } else {
            Box::new(LocalTestContext {})
        };
        let rt = Runtime::new().unwrap();
        rt.block_on(upload_download(&ctx))?;
        rt.block_on(regular_sync(&ctx))
    }

    fn open_col(dir: &Path, server: bool, fname: &str) -> Result<Collection> {
        let path = dir.join(fname);
        let tr = I18n::template_only();
        open_collection(path, "".into(), "".into(), server, tr, log::terminal())
    }

    #[async_trait(?Send)]
    trait TestContext {
        fn server(&self) -> Box<dyn SyncServer>;

        fn col1(&self) -> Collection {
            open_col(self.dir(), false, "col1.anki2").unwrap()
        }

        fn col2(&self) -> Collection {
            open_col(self.dir(), false, "col2.anki2").unwrap()
        }

        fn dir(&self) -> &Path {
            lazy_static! {
                static ref DIR: TempDir = tempdir().unwrap();
            }
            DIR.path()
        }

        async fn normal_sync(&self, col: &mut Collection) -> SyncOutput {
            NormalSyncer::new(col, self.server(), norm_progress)
                .sync()
                .await
                .unwrap()
        }

        async fn full_upload(&self, col: Collection) {
            col.full_upload_inner(self.server()).await.unwrap()
        }

        async fn full_download(&self, col: Collection) {
            col.full_download_inner(self.server()).await.unwrap()
        }
    }

    // Local specifics
    /////////////////////

    struct LocalTestContext {}

    #[async_trait(?Send)]
    impl TestContext for LocalTestContext {
        fn server(&self) -> Box<dyn SyncServer> {
            let col = open_col(self.dir(), true, "server.anki2").unwrap();
            Box::new(LocalServer::new(col))
        }
    }

    // Remote specifics
    /////////////////////

    struct RemoteTestContext {
        auth: SyncAuth,
    }

    impl RemoteTestContext {
        fn server_inner(&self) -> HttpSyncClient {
            let auth = self.auth.clone();
            HttpSyncClient::new(Some(auth.hkey), auth.host_number)
        }
    }

    #[async_trait(?Send)]
    impl TestContext for RemoteTestContext {
        fn server(&self) -> Box<dyn SyncServer> {
            Box::new(self.server_inner())
        }

        async fn full_upload(&self, col: Collection) {
            let mut server = self.server_inner();
            server.set_full_sync_progress_fn(Some(Box::new(full_progress)));
            col.full_upload_inner(Box::new(server)).await.unwrap()
        }

        async fn full_download(&self, col: Collection) {
            let mut server = self.server_inner();
            server.set_full_sync_progress_fn(Some(Box::new(full_progress)));
            col.full_download_inner(Box::new(server)).await.unwrap()
        }
    }

    // Setup + full syncs
    /////////////////////

    fn col1_setup(col: &mut Collection) {
        let nt = col.get_notetype_by_name("Basic").unwrap().unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "1").unwrap();
        col.add_note(&mut note, DeckId(1)).unwrap();

        // // set our schema time back, so when initial server
        // // col is created, it's not identical
        // col.storage
        //     .db
        //     .execute_batch("update col set scm = 123")
        //     .unwrap()
    }

    #[allow(clippy::borrowed_box)]
    async fn upload_download(ctx: &Box<dyn TestContext>) -> Result<()> {
        let mut col1 = ctx.col1();
        col1_setup(&mut col1);

        let out = ctx.normal_sync(&mut col1).await;
        assert!(matches!(
            out.required,
            SyncActionRequired::FullSyncRequired { .. }
        ));

        ctx.full_upload(col1).await;

        // another collection
        let mut col2 = ctx.col2();

        // won't allow ankiweb clobber
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(
            out.required,
            SyncActionRequired::FullSyncRequired {
                upload_ok: false,
                download_ok: true
            }
        );

        // fetch so we're in sync
        ctx.full_download(col2).await;

        Ok(())
    }

    // Regular syncs
    /////////////////////

    #[allow(clippy::borrowed_box)]
    async fn regular_sync(ctx: &Box<dyn TestContext>) -> Result<()> {
        // add a deck
        let mut col1 = ctx.col1();
        let mut col2 = ctx.col2();

        let mut deck = col1.get_or_create_normal_deck("new deck")?;

        // give it a new option group
        let mut dconf = DeckConfig {
            name: "new dconf".into(),
            ..Default::default()
        };
        col1.add_or_update_deck_config(&mut dconf)?;
        if let DeckKind::Normal(deck) = &mut deck.kind {
            deck.config_id = dconf.id.0;
        }
        col1.add_or_update_deck(&mut deck)?;

        // and a new notetype
        let mut nt = all_stock_notetypes(&col1.tr).remove(0);
        nt.name = "new".into();
        col1.add_notetype(&mut nt, false)?;

        // add another note+card+tag
        let mut note = nt.new_note();
        note.set_field(0, "2")?;
        note.tags.push("tag".into());
        col1.add_note(&mut note, deck.id)?;

        // mock revlog entry
        col1.storage.add_revlog_entry(
            &RevlogEntry {
                id: RevlogId(123),
                cid: CardId(456),
                usn: Usn(-1),
                interval: 10,
                ..Default::default()
            },
            true,
        )?;

        // config + creation
        col1.set_config("test", &"test1")?;
        // bumping this will affect 'last studied at' on decks at the moment
        // col1.storage.set_creation_stamp(TimestampSecs(12345))?;

        // and sync our changes
        let remote_meta = ctx.server().meta().await.unwrap();
        let out = col1.get_sync_status(remote_meta)?;
        assert_eq!(out, sync_status_response::Required::NormalSync);

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        // sync the other collection
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        let ntid = nt.id;
        let deckid = deck.id;
        let dconfid = dconf.id;
        let noteid = note.id;
        let cardid = col1.search_cards(note.id, SortMode::NoOrder)?[0];
        let revlogid = RevlogId(123);

        let compare_sides = |col1: &mut Collection, col2: &mut Collection| -> Result<()> {
            assert_eq!(
                col1.get_notetype(ntid)?.unwrap(),
                col2.get_notetype(ntid)?.unwrap()
            );
            assert_eq!(
                col1.get_deck(deckid)?.unwrap(),
                col2.get_deck(deckid)?.unwrap()
            );
            assert_eq!(
                col1.get_deck_config(dconfid, false)?.unwrap(),
                col2.get_deck_config(dconfid, false)?.unwrap()
            );
            assert_eq!(
                col1.storage.get_note(noteid)?.unwrap(),
                col2.storage.get_note(noteid)?.unwrap()
            );
            assert_eq!(
                col1.storage.get_card(cardid)?.unwrap(),
                col2.storage.get_card(cardid)?.unwrap()
            );
            assert_eq!(
                col1.storage.get_revlog_entry(revlogid)?,
                col2.storage.get_revlog_entry(revlogid)?,
            );
            assert_eq!(
                col1.storage.get_all_config()?,
                col2.storage.get_all_config()?
            );
            assert_eq!(
                col1.storage.creation_stamp()?,
                col2.storage.creation_stamp()?
            );

            // server doesn't send tag usns, so we can only compare tags, not usns,
            // as the usns may not match
            assert_eq!(
                col1.storage
                    .all_tags()?
                    .into_iter()
                    .map(|t| t.name)
                    .collect::<Vec<_>>(),
                col2.storage
                    .all_tags()?
                    .into_iter()
                    .map(|t| t.name)
                    .collect::<Vec<_>>()
            );

            Ok(())
        };

        // make sure everything has been transferred across
        compare_sides(&mut col1, &mut col2)?;

        // make some modifications
        let mut note = col2.storage.get_note(note.id)?.unwrap();
        note.set_field(1, "new")?;
        note.tags.push("tag2".into());
        col2.update_note(&mut note)?;

        col2.get_and_update_card(cardid, |card| {
            card.queue = CardQueue::Review;
            Ok(())
        })?;

        let mut deck = col2.storage.get_deck(deck.id)?.unwrap();
        deck.name = NativeDeckName::from_native_str("newer");
        col2.add_or_update_deck(&mut deck)?;

        let mut nt = col2.storage.get_notetype(nt.id)?.unwrap();
        nt.name = "newer".into();
        col2.update_notetype(&mut nt, false)?;

        // sync the changes back
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);
        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        // should still match
        compare_sides(&mut col1, &mut col2)?;

        // deletions should sync too
        for table in &["cards", "notes", "decks"] {
            assert_eq!(
                col1.storage
                    .db_scalar::<u8>(&format!("select count() from {}", table))?,
                2
            );
        }

        // fixme: inconsistent usn arg
        col1.remove_cards_and_orphaned_notes(&[cardid])?;
        let usn = col1.usn()?;
        col1.remove_note_only_undoable(noteid, usn)?;
        col1.remove_decks_and_child_decks(&[deckid])?;

        let out = ctx.normal_sync(&mut col1).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);
        let out = ctx.normal_sync(&mut col2).await;
        assert_eq!(out.required, SyncActionRequired::NoChanges);

        for table in &["cards", "notes", "decks"] {
            assert_eq!(
                col2.storage
                    .db_scalar::<u8>(&format!("select count() from {}", table))?,
                1
            );
        }

        // removing things like a notetype forces a full sync
        col2.remove_notetype(ntid)?;
        let out = ctx.normal_sync(&mut col2).await;
        assert!(matches!(
            out.required,
            SyncActionRequired::FullSyncRequired { .. }
        ));
        Ok(())
    }

    // Helper to reproduce issues with a copy of the client and server collections.
    // #[test]
    // fn repro_test() {
    //     let rt = Runtime::new().unwrap();
    //     rt.block_on(repro_test_inner()).unwrap();
    // }

    // async fn repro_test_inner() -> Result<()> {
    //     let client_fname = "/path/to/collection1.anki2";
    //     let server_fname = "/path/to/collection2.anki2";

    //     use std::env::temp_dir;
    //     use std::fs;
    //     use tempfile::NamedTempFile;

    //     let client_col_file = NamedTempFile::new()?;
    //     let client_col_name = client_col_file
    //         .path()
    //         .file_name()
    //         .unwrap()
    //         .to_string_lossy();
    //     fs::copy(client_fname, client_col_file.path())?;
    //     let server_col_file = NamedTempFile::new()?;
    //     let server_col_name = server_col_file
    //         .path()
    //         .file_name()
    //         .unwrap()
    //         .to_string_lossy();
    //     fs::copy(server_fname, server_col_file.path())?;
    //     let dir = temp_dir();
    //     let server = Box::new(LocalServer::new(open_col(&dir, true, &server_col_name)?));
    //     let mut client_col = open_col(&dir, false, &client_col_name)?;
    //     NormalSyncer::new(&mut client_col, server, norm_progress)
    //         .sync()
    //         .await
    //         .unwrap();

    //     Ok(())
    // }
}
