// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod http_client;

use crate::{
    card::{Card, CardQueue, CardType},
    deckconf::DeckConfSchema11,
    decks::DeckSchema11,
    err::SyncErrorKind,
    notes::{guid, Note},
    notetype::{NoteType, NoteTypeSchema11},
    prelude::*,
    tags::{join_tags, split_tags},
    version::sync_client_version,
};
use flate2::write::GzEncoder;
use flate2::Compression;
use futures::StreamExt;
use http_client::HTTPSyncClient;
use itertools::Itertools;
use reqwest::{multipart, Client, Response};
use serde::{de::DeserializeOwned, Deserialize, Serialize};
use serde_json::Value;
use serde_tuple::Serialize_tuple;
use std::io::prelude::*;
use std::{collections::HashMap, path::Path, time::Duration};
use tempfile::NamedTempFile;

#[derive(Default, Debug)]
pub struct SyncProgress {}

#[derive(Serialize, Deserialize, Debug)]
pub struct SyncMeta {
    #[serde(rename = "mod")]
    modified: TimestampMillis,
    #[serde(rename = "scm")]
    schema: TimestampMillis,
    usn: Usn,
    #[serde(rename = "ts")]
    current_time: TimestampSecs,
    #[serde(rename = "msg")]
    server_message: String,
    #[serde(rename = "cont")]
    should_continue: bool,
    #[serde(rename = "hostNum")]
    shard_number: u32,
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct Graves {
    pub(crate) cards: Vec<CardID>,
    pub(crate) decks: Vec<DeckID>,
    pub(crate) notes: Vec<NoteID>,
}

#[derive(Serialize_tuple, Deserialize, Debug, Default)]
pub struct DecksAndConfig {
    decks: Vec<DeckSchema11>,
    config: Vec<DeckConfSchema11>,
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct UnchunkedChanges {
    #[serde(rename = "models")]
    notetypes: Vec<NoteTypeSchema11>,
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
    done: bool,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    revlog: Vec<ReviewLogEntry>,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    cards: Vec<CardEntry>,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    notes: Vec<NoteEntry>,
}

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct ReviewLogEntry {
    pub id: TimestampMillis,
    pub cid: CardID,
    pub usn: Usn,
    pub ease: u8,
    #[serde(rename = "ivl")]
    pub interval: i32,
    #[serde(rename = "lastIvl")]
    pub last_interval: i32,
    pub factor: u32,
    pub time: u32,
    #[serde(rename = "type")]
    pub kind: u8,
}

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct NoteEntry {
    pub id: NoteID,
    pub guid: String,
    #[serde(rename = "mid")]
    pub ntid: NoteTypeID,
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
    pub id: CardID,
    pub nid: NoteID,
    pub did: DeckID,
    pub ord: u16,
    pub mtime: TimestampSecs,
    pub usn: Usn,
    pub ctype: CardType,
    pub queue: CardQueue,
    pub due: i32,
    pub ivl: u32,
    pub factor: u16,
    pub reps: u32,
    pub lapses: u32,
    pub left: u32,
    pub odue: i32,
    pub odid: DeckID,
    pub flags: u8,
    pub data: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct SanityCheckOut {
    status: SanityCheckStatus,
    #[serde(rename = "c")]
    client: Option<SanityCheckCounts>,
    #[serde(rename = "s")]
    server: Option<SanityCheckCounts>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq)]
#[serde(rename_all = "lowercase")]
enum SanityCheckStatus {
    Ok,
    Bad,
}

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct SanityCheckCounts {
    counts: SanityCheckDueCounts,
    cards: u32,
    notes: u32,
    revlog: u32,
    graves: u32,
    #[serde(rename = "models")]
    notetypes: u32,
    decks: u32,
    deck_config: u32,
}

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct SanityCheckDueCounts {
    new: u32,
    learn: u32,
    review: u32,
}

#[derive(Debug, Default)]
pub struct FullSyncProgress {
    transferred_bytes: usize,
    total_bytes: usize,
}

pub enum SyncState {
    NoChanges,
    FullSyncRequired,
    NormalSyncRequired(NormalSyncMeta),
}

pub struct NormalSyncMeta {
    local_is_newer: bool,
    local_usn: Usn,
    remote_usn: Usn,
    server_message: String,
    shard_number: u32,
}

struct SyncDriver<'a> {
    col: &'a mut Collection,
    remote: HTTPSyncClient,
}

impl SyncDriver<'_> {
    async fn from_login<'a>(
        col: &'a mut Collection,
        username: &str,
        password: &str,
    ) -> Result<SyncDriver<'a>> {
        let mut remote = HTTPSyncClient::new(None, "");
        remote.login(username, password).await?;
        Ok(SyncDriver { col, remote })
    }

    fn from_hkey<'a>(
        col: &'a mut Collection,
        hkey: String,
        endpoint_suffix: &str,
    ) -> SyncDriver<'a> {
        SyncDriver {
            col,
            remote: HTTPSyncClient::new(Some(hkey), endpoint_suffix),
        }
    }

    async fn get_sync_state(&self) -> Result<SyncState> {
        let remote: SyncMeta = self.remote.meta().await?;
        if !remote.should_continue {
            return Err(AnkiError::SyncError {
                info: remote.server_message,
                kind: SyncErrorKind::ServerMessage,
            });
        }

        let local = self.col.sync_meta()?;
        if (remote.current_time.0 - local.current_time.0).abs() > 300 {
            return Err(AnkiError::SyncError {
                // fixme: need to rethink error handling; defer translation and pass in time difference
                info: "".into(),
                kind: SyncErrorKind::ClockIncorrect,
            });
        }

        if remote.modified == local.modified {
            return Ok(SyncState::NoChanges);
        }

        if remote.schema != local.schema {
            return Ok(SyncState::FullSyncRequired);
        }

        Ok(SyncState::NormalSyncRequired(NormalSyncMeta {
            local_is_newer: local.modified > remote.modified,
            local_usn: local.usn,
            remote_usn: remote.usn,
            server_message: remote.server_message,
            shard_number: remote.shard_number,
        }))
    }

    /// Sync. Caller must have created a transaction, and should call
    /// abort on
    pub(crate) async fn sync(&mut self, meta: NormalSyncMeta) -> Result<()> {
        self.col.basic_check_for_sync()?;
        self.start_and_process_deletions(&meta).await?;
        self.process_unchunked_changes(meta.remote_usn, meta.local_is_newer)
            .await?;
        self.process_chunks_from_server().await?;
        self.send_chunks_to_server(meta.remote_usn).await?;
        self.sanity_check().await?;
        self.finalize(meta).await?;
        Ok(())
    }

    /// Return the remote client for use in a full sync.
    fn into_remote(self) -> HTTPSyncClient {
        self.remote
    }

    // The following operations assume a transaction has been set up.

    async fn start_and_process_deletions(&self, meta: &NormalSyncMeta) -> Result<()> {
        let removed_on_remote = self
            .remote
            .start(
                meta.local_usn,
                self.col.get_local_mins_west(),
                meta.local_is_newer,
            )
            .await?;

        let mut locally_removed = self.col.storage.take_pending_graves(meta.remote_usn)?;

        while let Some(chunk) = locally_removed.take_chunk() {
            self.remote.apply_graves(chunk).await?;
        }

        self.col.apply_graves(removed_on_remote, meta.local_usn)?;

        Ok(())
    }

    // This was assumed to a cheap operation when originally written - it didn't anticipate
    // the large deck trees and note types some users would create. They should be chunked
    // in the future, like other objects. Syncing tags explicitly is also probably of limited
    // usefulness.
    async fn process_unchunked_changes(
        &mut self,
        remote_usn: Usn,
        local_is_newer: bool,
    ) -> Result<()> {
        let local_changes = self
            .col
            .local_unchunked_changes(remote_usn, local_is_newer)?;
        let remote_changes = self.remote.apply_changes(local_changes).await?;
        self.col.apply_changes(remote_changes, remote_usn)
    }

    async fn process_chunks_from_server(&mut self) -> Result<()> {
        loop {
            let chunk: Chunk = self.remote.chunk().await?;
            let done = chunk.done;
            self.col.apply_chunk(chunk)?;

            if done {
                return Ok(());
            }
        }
    }

    async fn send_chunks_to_server(&self, server_usn: Usn) -> Result<()> {
        loop {
            let chunk: Chunk = self.col.get_chunk(server_usn)?;
            let done = chunk.done;
            self.remote.apply_chunk(chunk).await?;

            if done {
                return Ok(());
            }
        }
    }

    /// Caller should force full sync after rolling back.
    async fn sanity_check(&self) -> Result<()> {
        let local_counts = self.col.sanity_check_info()?;
        let out: SanityCheckOut = self.remote.sanity_check(local_counts).await?;
        if out.status != SanityCheckStatus::Ok {
            Err(AnkiError::SyncError {
                info: String::new(),
                kind: SyncErrorKind::DatabaseCheckRequired,
            })
        } else {
            Ok(())
        }
    }

    async fn finalize(&self, meta: NormalSyncMeta) -> Result<()> {
        let new_server_mtime = self.remote.finish().await?;
        self.col.finalize_sync(meta, new_server_mtime)
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

impl Collection {
    fn sync_meta(&self) -> Result<SyncMeta> {
        Ok(SyncMeta {
            modified: self.storage.get_modified_time()?,
            schema: self.storage.get_schema_mtime()?,
            usn: self.storage.usn(true)?,
            current_time: TimestampSecs::now(),
            server_message: "".into(),
            should_continue: true,
            shard_number: 0,
        })
    }

    fn basic_check_for_sync(&self) -> Result<()> {
        todo!();
    }

    fn apply_graves(&self, graves: Graves, local_usn: Usn) -> Result<()> {
        for nid in graves.notes {
            self.storage.remove_note(nid)?;
            self.storage.add_note_grave(nid, local_usn)?;
        }
        for cid in graves.cards {
            self.storage.remove_card(cid)?;
            self.storage.add_card_grave(cid, local_usn)?;
        }
        for did in graves.decks {
            self.storage.remove_deck(did)?;
            self.storage.add_deck_grave(did, local_usn)?;
        }
        Ok(())
    }

    // Local->remote unchunked changes
    //----------------------------------------------------------------

    fn local_unchunked_changes(
        &self,
        remote_usn: Usn,
        local_is_newer: bool,
    ) -> Result<UnchunkedChanges> {
        let mut changes = UnchunkedChanges {
            notetypes: self.changed_notetypes(remote_usn)?,
            decks_and_config: DecksAndConfig {
                decks: self.changed_decks(remote_usn)?,
                config: self.changed_deck_config(remote_usn)?,
            },
            tags: self.changed_tags(remote_usn)?,
            ..Default::default()
        };
        if local_is_newer {
            changes.config = Some(todo!());
            changes.creation_stamp = Some(self.storage.creation_stamp()?);
        }

        Ok(changes)
    }

    fn changed_notetypes(&self, new_usn: Usn) -> Result<Vec<NoteTypeSchema11>> {
        let ids = self.storage.take_notetype_ids_pending_sync(new_usn)?;
        ids.into_iter()
            .map(|id| self.storage.get_notetype(id).map(|opt| opt.unwrap().into()))
            .collect()
    }

    fn changed_decks(&self, new_usn: Usn) -> Result<Vec<DeckSchema11>> {
        let ids = self.storage.take_deck_ids_pending_sync(new_usn)?;
        ids.into_iter()
            .map(|id| self.storage.get_deck(id).map(|opt| opt.unwrap().into()))
            .collect()
    }

    fn changed_deck_config(&self, new_usn: Usn) -> Result<Vec<DeckConfSchema11>> {
        let ids = self.storage.take_deck_config_ids_pending_sync(new_usn)?;
        ids.into_iter()
            .map(|id| {
                self.storage
                    .get_deck_config(id)
                    .map(|opt| opt.unwrap().into())
            })
            .collect()
    }

    fn changed_tags(&self, new_usn: Usn) -> Result<Vec<String>> {
        self.storage.take_changed_tags(new_usn)
    }

    /// Currently this is all config, as legacy clients overwrite the local items
    /// with the provided value.
    fn changed_config(&self, new_usn: Usn) -> Result<HashMap<String, Value>> {
        let conf = self.storage.get_all_config()?;
        self.storage.clear_config_usns()?;
        Ok(conf)
    }

    // Remote->local unchunked changes
    //----------------------------------------------------------------

    fn apply_changes(&mut self, remote: UnchunkedChanges, remote_usn: Usn) -> Result<()> {
        self.merge_notetypes(remote.notetypes)?;
        self.merge_decks(remote.decks_and_config.decks)?;
        self.merge_deck_config(remote.decks_and_config.config)?;
        self.merge_tags(remote.tags, remote_usn)?;
        if let Some(crt) = remote.creation_stamp {
            self.storage.set_creation_stamp(crt)?;
        }
        if let Some(config) = remote.config {
            self.storage
                .set_all_config(config, remote_usn, TimestampSecs::now())?;
        }

        Ok(())
    }

    fn merge_notetypes(&mut self, notetypes: Vec<NoteTypeSchema11>) -> Result<()> {
        for mut nt in notetypes {
            let nt: NoteType = nt.into();
            let proceed = if let Some(existing_nt) = self.storage.get_notetype(nt.id)? {
                if existing_nt.mtime_secs < nt.mtime_secs {
                    if (existing_nt.fields.len() != nt.fields.len())
                        || (existing_nt.templates.len() != nt.templates.len())
                    {
                        return Err(AnkiError::SyncError {
                            info: "notetype schema changed".into(),
                            kind: SyncErrorKind::ResyncRequired,
                        });
                    }
                    true
                } else {
                    false
                }
            } else {
                true
            };
            if proceed {
                self.storage.add_or_update_notetype(&nt)?;
                self.state.notetype_cache.remove(&nt.id);
            }
        }
        Ok(())
    }

    fn merge_decks(&mut self, decks: Vec<DeckSchema11>) -> Result<()> {
        for mut deck in decks {
            let proceed = if let Some(existing_deck) = self.storage.get_deck(deck.id())? {
                existing_deck.mtime_secs < deck.common().mtime
            } else {
                true
            };
            if proceed {
                let deck = deck.into();
                self.storage.add_or_update_deck(&deck)?;
                self.state.deck_cache.remove(&deck.id);
            }
        }
        Ok(())
    }

    fn merge_deck_config(&self, dconf: Vec<DeckConfSchema11>) -> Result<()> {
        for mut conf in dconf {
            let proceed = if let Some(existing_conf) = self.storage.get_deck_config(conf.id)? {
                existing_conf.mtime_secs < conf.mtime
            } else {
                true
            };
            if proceed {
                let conf = conf.into();
                self.storage.add_or_update_deck_config(&conf)?;
            }
        }
        Ok(())
    }

    fn merge_tags(&self, tags: Vec<String>, new_usn: Usn) -> Result<()> {
        for tag in tags {
            self.register_tag(&tag, new_usn)?;
        }
        Ok(())
    }

    // Remote->local chunks
    //----------------------------------------------------------------

    fn apply_chunk(&mut self, chunk: Chunk) -> Result<()> {
        self.merge_revlog(chunk.revlog)?;
        self.merge_cards(chunk.cards)?;
        self.merge_notes(chunk.notes)
    }

    fn merge_revlog(&self, entries: Vec<ReviewLogEntry>) -> Result<()> {
        for entry in entries {
            self.storage.add_revlog_entry(&entry)?;
        }
        Ok(())
    }

    fn merge_cards(&self, entries: Vec<CardEntry>) -> Result<()> {
        for entry in entries {
            self.add_or_update_card_if_newer(entry)?;
        }
        Ok(())
    }

    fn add_or_update_card_if_newer(&self, entry: CardEntry) -> Result<()> {
        let proceed = if let Some(existing_card) = self.storage.get_card(entry.id)? {
            existing_card.mtime < entry.mtime
        } else {
            true
        };
        if proceed {
            let card = entry.into();
            self.storage.add_or_update_card(&card)?;
        }
        Ok(())
    }

    fn merge_notes(&mut self, entries: Vec<NoteEntry>) -> Result<()> {
        for entry in entries {
            self.add_or_update_note_if_newer(entry)?;
        }
        Ok(())
    }

    fn add_or_update_note_if_newer(&mut self, entry: NoteEntry) -> Result<()> {
        let proceed = if let Some(existing_note) = self.storage.get_note(entry.id)? {
            existing_note.mtime < entry.mtime
        } else {
            true
        };
        if proceed {
            let mut note: Note = entry.into();
            let nt = self
                .get_notetype(note.ntid)?
                .ok_or(AnkiError::invalid_input("note missing notetype"))?;
            note.prepare_for_update(&nt, false)?;
            self.storage.add_or_update_note(&note)?;
        }
        Ok(())
    }

    // Local->remote chunks
    //----------------------------------------------------------------

    fn get_chunk(&self, server_usn: Usn) -> Result<Chunk> {
        let mut chunk = Chunk::default();
        chunk.revlog = self
            .storage
            .take_revlog_pending_sync(server_usn, CHUNK_SIZE)?;
        chunk.cards = self
            .storage
            .take_cards_pending_sync(server_usn, CHUNK_SIZE)?;
        if !chunk.revlog.is_empty() || !chunk.cards.is_empty() {
            return Ok(chunk);
        }

        chunk.notes = self
            .storage
            .take_notes_pending_sync(server_usn, CHUNK_SIZE)?;
        if chunk.notes.is_empty() {
            chunk.done = true;
        }

        Ok(chunk)
    }

    // Final steps
    //----------------------------------------------------------------

    fn sanity_check_info(&self) -> Result<SanityCheckCounts> {
        self.basic_check_for_sync()?;
        todo!();
    }

    fn finalize_sync(&self, meta: NormalSyncMeta, new_server_mtime: TimestampMillis) -> Result<()> {
        self.storage.set_last_sync(new_server_mtime)?;
        let mut usn = meta.remote_usn;
        usn.0 += 1;
        self.storage.set_usn(usn)?;
        self.storage.set_modified_time(new_server_mtime)
    }
}

impl From<CardEntry> for Card {
    fn from(e: CardEntry) -> Self {
        Card {
            id: e.id,
            nid: e.nid,
            did: e.did,
            ord: e.ord,
            mtime: e.mtime,
            usn: e.usn,
            ctype: e.ctype,
            queue: e.queue,
            due: e.due,
            ivl: e.ivl,
            factor: e.factor,
            reps: e.reps,
            lapses: e.lapses,
            left: e.left,
            odue: e.odue,
            odid: e.odid,
            flags: e.flags,
            data: e.data,
        }
    }
}

impl From<Card> for CardEntry {
    fn from(e: Card) -> Self {
        CardEntry {
            id: e.id,
            nid: e.nid,
            did: e.did,
            ord: e.ord,
            mtime: e.mtime,
            usn: e.usn,
            ctype: e.ctype,
            queue: e.queue,
            due: e.due,
            ivl: e.ivl,
            factor: e.factor,
            reps: e.reps,
            lapses: e.lapses,
            left: e.left,
            odue: e.odue,
            odid: e.odid,
            flags: e.flags,
            data: e.data,
        }
    }
}

impl From<NoteEntry> for Note {
    fn from(e: NoteEntry) -> Self {
        Note {
            id: e.id,
            guid: e.guid,
            ntid: e.ntid,
            mtime: e.mtime,
            usn: e.usn,
            tags: split_tags(&e.tags).map(ToString::to_string).collect(),
            fields: e.fields.split('\x1f').map(ToString::to_string).collect(),
            sort_field: None,
            checksum: None,
        }
    }
}

impl From<Note> for NoteEntry {
    fn from(e: Note) -> Self {
        NoteEntry {
            id: e.id,
            guid: e.guid,
            ntid: e.ntid,
            mtime: e.mtime,
            usn: e.usn,
            tags: join_tags(&e.tags),
            fields: e.fields.into_iter().join("\x1f"),
            sfld: String::new(),
            csum: String::new(),
            flags: 0,
            data: String::new(),
        }
    }
}
