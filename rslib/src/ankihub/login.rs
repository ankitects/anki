// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use reqwest::Client;
use serde::Deserialize;
use serde::Serialize;

use crate::ankihub::http_client::HttpAnkiHubClient;
use crate::prelude::*;

#[derive(Serialize, Deserialize, Debug)]
pub struct LoginRequest {
    // FIXME: we need to pass either `username` or `email`
    pub username: String,
    pub password: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct LoginResponse {
    pub token: Option<String>,
    pub non_field_errors: Option<Vec<String>>,
}

pub async fn ankihub_login<S: Into<String>>(
    username: S,
    password: S,
    client: Client,
) -> Result<LoginResponse> {
    let client = HttpAnkiHubClient::new("", client);
    client
        .login(LoginRequest {
            username: username.into(),
            password: password.into(),
        })
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
