// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod http_client;

use crate::{
    card::{CardQueue, CardType},
    deckconf::DeckConfSchema11,
    decks::DeckSchema11,
    notes::guid,
    notetype::NoteTypeSchema11,
    prelude::*,
    version::sync_client_version,
};
use flate2::write::GzEncoder;
use flate2::Compression;
use futures::StreamExt;
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
pub struct ServerMeta {
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
    cards: Vec<CardID>,
    decks: Vec<DeckID>,
    notes: Vec<NoteID>,
}

#[derive(Serialize_tuple, Deserialize, Debug, Default)]
pub struct DecksAndConfig {
    decks: Vec<DeckSchema11>,
    config: Vec<DeckConfSchema11>,
}

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct Changes {
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
    id: TimestampMillis,
    cid: CardID,
    usn: Usn,
    ease: u8,
    #[serde(rename = "ivl")]
    interval: i32,
    #[serde(rename = "lastIvl")]
    last_interval: i32,
    factor: u32,
    time: u32,
    #[serde(rename = "type")]
    kind: u8,
}

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct NoteEntry {
    id: NoteID,
    guid: String,
    #[serde(rename = "mid")]
    ntid: NoteTypeID,
    #[serde(rename = "mod")]
    mtime: TimestampSecs,
    usn: Usn,
    tags: String,
    fields: String,
    sfld: String, // always empty
    csum: String, // always empty
    flags: u32,
    data: String,
}

#[derive(Serialize_tuple, Deserialize, Debug)]
pub struct CardEntry {
    id: CardID,
    nid: NoteID,
    did: DeckID,
    ord: u16,
    mtime: TimestampSecs,
    usn: Usn,
    ctype: CardType,
    queue: CardQueue,
    due: i32,
    ivl: u32,
    factor: u16,
    reps: u32,
    lapses: u32,
    left: u32,
    odue: i32,
    odid: DeckID,
    flags: u8,
    data: String,
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
