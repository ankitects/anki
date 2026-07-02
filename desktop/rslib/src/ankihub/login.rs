// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::LazyLock;

use regex::Regex;
use reqwest::Client;
use serde;
use serde::Deserialize;
use serde::Serialize;

use crate::ankihub::http_client::HttpAnkiHubClient;
use crate::prelude::*;

#[derive(Serialize, Deserialize, Debug)]
pub struct LoginRequest {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub username: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub email: Option<String>,
    pub password: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct LoginResponse {
    pub token: Option<String>,
}

pub async fn ankihub_login<S: Into<String>>(
    id: S,
    password: S,
    client: Client,
) -> Result<LoginResponse> {
    let client = HttpAnkiHubClient::new("", client);
    static EMAIL_RE: LazyLock<Regex> = LazyLock::new(|| {
        Regex::new(r"^[a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$").unwrap()
    });
    let mut request = LoginRequest {
        username: None,
        email: None,
        password: password.into(),
    };
    let id: String = id.into();
    if EMAIL_RE.is_match(&id) {
        request.email = Some(id);
    } else {
        request.username = Some(id);
    }
    client
        .login(request)
        .await?
        .json::<LoginResponse>()
        .await
        .map_err(|e| e.into())
}

pub async fn ankihub_logout<S: Into<String>>(token: S, client: Client) -> Result<()> {
    let client = HttpAnkiHubClient::new(token, client);
    client.logout().await?;

    Ok(())
}
