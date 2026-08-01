// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize;
use serde::Deserializer;
use serde::Serialize;
use tracing::debug;

use crate::prelude::*;
use crate::sync::collection::chunks::ChunkableIds;
use crate::sync::collection::graves::ApplyGravesRequest;
use crate::sync::collection::graves::Graves;
use crate::sync::collection::normal::ClientSyncState;
use crate::sync::collection::normal::NormalSyncer;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::request::IntoSyncRequest;

impl NormalSyncer<'_> {
    pub(in crate::sync) async fn start_and_process_deletions(
        &mut self,
        state: &ClientSyncState,
    ) -> Result<()> {
        let remote: Graves = self
            .server
            .start(
                StartRequest {
                    client_usn: state.usn_at_last_sync,
                    local_is_newer: state.local_is_newer,
                    deprecated_client_graves: None,
                }
                .try_into_sync_request()?,
            )
            .await?
            .json()?;

        debug!(
            cards = remote.cards.len(),
            notes = remote.notes.len(),
            decks = remote.decks.len(),
            "removed on remote"
        );

        let mut local = self.col.storage.pending_graves(state.pending_usn)?;
        self.col
            .storage
            .update_pending_grave_usns(state.server_usn)?;

        debug!(
            cards = local.cards.len(),
            notes = local.notes.len(),
            decks = local.decks.len(),
            "locally removed  "
        );

        while let Some(chunk) = local.take_chunk() {
            debug!("sending graves chunk");
            self.progress.update(false, |p| {
                p.local_remove += chunk.cards.len() + chunk.notes.len() + chunk.decks.len()
            })?;
            self.server
                .apply_graves(ApplyGravesRequest { chunk }.try_into_sync_request()?)
                .await?;
            self.progress.check_cancelled()?;
        }

        self.progress.update(false, |p| {
            p.remote_remove = remote.cards.len() + remote.notes.len() + remote.decks.len()
        })?;
        self.col.apply_graves(remote, state.server_usn)?;
        self.progress.check_cancelled()?;
        debug!("applied server graves");

        Ok(())
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct StartRequest {
    #[serde(rename = "minUsn")]
    pub client_usn: Usn,
    #[serde(rename = "lnewer")]
    pub local_is_newer: bool,
    /// Used by old clients, and still used by AnkiDroid.
    #[serde(rename = "graves", default, deserialize_with = "legacy_graves")]
    pub deprecated_client_graves: Option<Graves>,
}

pub fn server_start(
    req: StartRequest,
    col: &mut Collection,
    state: &mut ServerSyncState,
) -> Result<Graves> {
    state.server_usn = col.usn()?;
    state.client_usn = req.client_usn;
    state.client_is_newer = req.local_is_newer;

    col.discard_undo_and_study_queues();
    col.storage.begin_rust_trx()?;

    // make sure any pending cards have been unburied first if necessary
    let timing = col.timing_today()?;
    col.unbury_if_day_rolled_over(timing)?;

    // fetch local graves
    let server_graves = col.storage.pending_graves(state.client_usn)?;
    // handle AnkiDroid using old protocol
    if let Some(graves) = req.deprecated_client_graves {
        col.apply_graves(graves, state.server_usn)?;
    }

    Ok(server_graves)
}

/// The current sync protocol is stateful, so unfortunately we need to
/// retain a bunch of information across requests. These are set either
/// on start, or on subsequent methods.
pub struct ServerSyncState {
    /// The session key. This is sent on every http request, but is ignored for
    /// methods where there is not active sync state.
    pub skey: String,

    pub(in crate::sync) server_usn: Usn,
    pub(in crate::sync) client_usn: Usn,
    /// Only used to determine whether we should send our
    /// config to client.
    pub(in crate::sync) client_is_newer: bool,
    /// Set on the first call to chunk()
    pub(in crate::sync) server_chunk_ids: Option<ChunkableIds>,
}

impl ServerSyncState {
    pub fn new(skey: impl Into<String>) -> Self {
        Self {
            skey: skey.into(),
            server_usn: Default::default(),
            client_usn: Default::default(),
            client_is_newer: false,
            server_chunk_ids: None,
        }
    }
}

pub(crate) fn legacy_graves<'de, D>(deserializer: D) -> Result<Option<Graves>, D::Error>
where
    D: Deserializer<'de>,
{
    #[derive(Deserialize)]
    #[serde(untagged)]
    enum GraveType {
        Normal(Graves),
        Legacy(StringGraves),
        Null,
    }
    match GraveType::deserialize(deserializer)? {
        GraveType::Normal(normal) => Ok(Some(normal)),
        GraveType::Legacy(stringly) => Ok(Some(Graves {
            cards: string_list_to_ids(stringly.cards)?,
            decks: string_list_to_ids(stringly.decks)?,
            notes: string_list_to_ids(stringly.notes)?,
        })),
        GraveType::Null => Ok(None),
    }
}

// old AnkiMobile versions
#[derive(Deserialize)]
struct StringGraves {
    cards: Vec<String>,
    decks: Vec<String>,
    notes: Vec<String>,
}

fn string_list_to_ids<T, E>(list: Vec<String>) -> Result<Vec<T>, E>
where
    T: From<i64>,
    E: serde::de::Error,
{
    list.into_iter()
        .map(|s| {
            s.parse::<i64>()
                .map_err(serde::de::Error::custom)
                .map(Into::into)
        })
        .collect::<Result<Vec<T>, E>>()
}
