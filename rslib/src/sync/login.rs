// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use reqwest::Client;
use reqwest::Url;
use serde::Deserialize;
use serde::Serialize;

use crate::prelude::*;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::request::IntoSyncRequest;

#[derive(Clone, Default)]
pub struct SyncAuth {
    pub hkey: String,
    pub endpoint: Option<Url>,
    pub io_timeout_secs: Option<u32>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct HostKeyRequest {
    #[serde(rename = "u")]
    pub username: String,
    #[serde(rename = "p")]
    pub password: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct HostKeyResponse {
    pub key: String,
}

pub async fn sync_login<S: Into<String>>(
    username: S,
    password: S,
    endpoint: Option<String>,
    client: Client,
) -> Result<SyncAuth> {
    let auth = anki_proto::sync::SyncAuth {
        endpoint,
        ..Default::default()
    }
    .try_into()?;
    let client = HttpSyncClient::new(auth, client);
    let resp = client
        .host_key(
            HostKeyRequest {
                username: username.into(),
                password: password.into(),
            }
            .try_into_sync_request()?,
        )
        .await?
        .json()?;
    Ok(SyncAuth {
        hkey: resp.key,
        endpoint: None,
        io_timeout_secs: None,
    })
}
