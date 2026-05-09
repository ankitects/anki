// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::env;

use reqwest::Client;
use reqwest::Response;
use reqwest::Result;
use reqwest::Url;
use serde::Serialize;

use crate::ankihub::login::LoginRequest;

static API_VERSION: &str = "19.0";
static DEFAULT_API_URL: &str = "https://app.ankihub.net/api/";

#[derive(Clone)]
pub struct HttpAnkiHubClient {
    pub token: String,
    pub endpoint: Url,
    client: Client,
}

impl HttpAnkiHubClient {
    pub fn new<S: Into<String>>(token: S, client: Client) -> HttpAnkiHubClient {
        let endpoint = match env::var("ANKIHUB_APP_URL") {
            Ok(url) => {
                if let Ok(u) = Url::try_from(url.as_str()) {
                    u.join("api/").unwrap().to_string()
                } else {
                    DEFAULT_API_URL.to_string()
                }
            }
            Err(_) => DEFAULT_API_URL.to_string(),
        };
        HttpAnkiHubClient {
            token: token.into(),
            endpoint: Url::try_from(endpoint.as_str()).unwrap(),
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
