// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use reqwest::Url;
use serde_derive::{Deserialize, Serialize};

use crate::{
    prelude::*,
    sync::{
        collection::protocol::SyncProtocol, http_client::HttpSyncClient, request::IntoSyncRequest,
    },
};

#[derive(Clone, Default)]
pub struct SyncAuth {
    pub hkey: String,
    pub endpoint: Option<Url>,
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
) -> Result<SyncAuth> {
    let auth = crate::pb::sync::SyncAuth {
        endpoint,
        ..Default::default()
    }
    .try_into()?;
    let client = HttpSyncClient::new(auth);
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
    })
}
