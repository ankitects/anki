// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use reqwest::Client;
use reqwest::Response;
use reqwest::Result;
use reqwest::Url;
use serde::Serialize;

use crate::ankihub::login::LoginRequest;

static API_VERSION: &str = "18.0";

#[derive(Clone)]
pub struct HttpAnkiHubClient {
    pub token: String,
    pub endpoint: Url,
    client: Client,
}

impl HttpAnkiHubClient {
    pub fn new<S: Into<String>>(token: S, client: Client) -> HttpAnkiHubClient {
        HttpAnkiHubClient {
            token: token.into(),
            endpoint: Url::try_from("https://app.ankihub.net/api/").unwrap(),
            client,
        }
    }

    async fn request<T: Serialize + ?Sized>(&self, method: &str, data: &T) -> Result<Response> {
        let url = self.endpoint.join(method).unwrap();
        let mut builder = self.client.post(url).header(
            reqwest::header::ACCEPT,
            format!("application/json; version={API_VERSION}"),
        );
        if !self.token.is_empty() {
            builder = builder.header("Authorization", format!("Token {}", self.token));
        }
        builder.json(&data).send().await
    }

    pub async fn login(&self, data: LoginRequest) -> Result<Response> {
        self.request("login/", &data).await
    }

    pub async fn logout(&self) -> Result<Response> {
        self.request("logout/", "").await
    }
}
