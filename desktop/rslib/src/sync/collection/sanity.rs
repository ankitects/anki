// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize;
use serde::Serialize;
use serde_tuple::Serialize_tuple;
use tracing::debug;
use tracing::info;

use crate::error::SyncErrorKind;
use crate::prelude::*;
use crate::serde::default_on_invalid;
use crate::sync::collection::normal::NormalSyncer;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::request::IntoSyncRequest;

#[derive(Serialize, Deserialize, Debug)]
pub struct SanityCheckResponse {
    pub status: SanityCheckStatus,
    #[serde(rename = "c", default, deserialize_with = "default_on_invalid")]
    pub client: Option<SanityCheckCounts>,
    #[serde(rename = "s", default, deserialize_with = "default_on_invalid")]
    pub server: Option<SanityCheckCounts>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum SanityCheckStatus {
    Ok,
    Bad,
}

#[derive(Serialize_tuple, Deserialize, Debug, PartialEq, Eq)]
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

#[derive(Serialize_tuple, Deserialize, Debug, Default, PartialEq, Eq)]
pub struct SanityCheckDueCounts {
    pub new: u32,
    pub learn: u32,
    pub review: u32,
}

impl NormalSyncer<'_> {
    /// Caller should force full sync after rolling back.
    pub(in crate::sync) async fn sanity_check(&mut self) -> Result<()> {
        let local_counts = self.col.storage.sanity_check_info()?;

        debug!("gathered local counts; waiting for server reply");
        let SanityCheckResponse {
            status,
            client,
            server,
        } = self
            .server
            .sanity_check(
                SanityCheckRequest {
                    client: local_counts,
                }
                .try_into_sync_request()?,
            )
            .await?
            .json()?;
        debug!("got server reply");
        if status != SanityCheckStatus::Ok {
            Err(AnkiError::sync_error(
                "",
                SyncErrorKind::SanityCheckFailed { client, server },
            ))
        } else {
            Ok(())
        }
    }
}

pub fn server_sanity_check(
    SanityCheckRequest { mut client }: SanityCheckRequest,
    col: &mut Collection,
) -> Result<SanityCheckResponse> {
    let mut server = match col.storage.sanity_check_info() {
        Ok(info) => info,
        Err(err) => {
            info!(client_counts=?client, ?err, "sanity check failed");
            return Ok(SanityCheckResponse {
                status: SanityCheckStatus::Bad,
                client: Some(client),
                server: None,
            });
        }
    };

    client.counts = Default::default();
    // clients on schema 17 and below may send duplicate
    // deletion markers, so we can't compare graves until
    // the minimum syncing version is schema 18.
    client.graves = 0;
    server.graves = 0;
    Ok(SanityCheckResponse {
        status: if client == server {
            SanityCheckStatus::Ok
        } else {
            info!(client_counts=?client, server_counts=?server, "sanity check failed");
            SanityCheckStatus::Bad
        },
        client: Some(client),
        server: Some(server),
    })
}

#[derive(Serialize, Deserialize, Debug)]
pub struct SanityCheckRequest {
    pub client: SanityCheckCounts,
}
