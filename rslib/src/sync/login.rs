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

#[cfg(test)]
mod test {
    use std::time::Duration;

    use futures::StreamExt;
    use wiremock::matchers::method;
    use wiremock::matchers::path;
    use wiremock::Mock;
    use wiremock::MockServer;
    use wiremock::ResponseTemplate;

    use super::*;
    use crate::error::SyncError;
    use crate::error::SyncErrorKind;
    use crate::sync::request::header_and_stream::decode_zstd_body_for_server;
    use crate::sync::request::header_and_stream::encode_zstd_body;

    fn unwrap_sync_err_kind(err: AnkiError) -> SyncErrorKind {
        let AnkiError::SyncError {
            source: SyncError { kind, .. },
        } = err
        else {
            panic!("not a sync err: {err:?}");
        };
        kind
    }

    #[test]
    fn host_key_request_uses_short_wire_field_names() {
        // the server speaks the legacy `u`/`p` field names
        let json = serde_json::to_value(HostKeyRequest {
            username: "alice".into(),
            password: "secret".into(),
        })
        .unwrap();
        assert_eq!(json, serde_json::json!({"u": "alice", "p": "secret"}));
    }

    #[tokio::test]
    async fn sync_login_returns_host_key_on_success() {
        let response = br#"{"key":"server-issued-host-key"}"#;
        let mut stream = encode_zstd_body(response.to_vec());
        let mut body = Vec::new();
        while let Some(chunk) = stream.next().await {
            body.extend_from_slice(&chunk.unwrap());
        }
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/sync/hostKey"))
            .respond_with(
                ResponseTemplate::new(200)
                    .insert_header("anki-original-size", response.len().to_string())
                    .set_body_bytes(body),
            )
            .mount(&server)
            .await;

        let auth = sync_login("user", "pass", Some(server.uri()), Client::new())
            .await
            .unwrap();

        assert_eq!(auth.hkey, "server-issued-host-key");
        let requests = server.received_requests().await.unwrap();
        assert_eq!(requests.len(), 1);
        let request_stream =
            futures::stream::iter([Ok::<_, std::io::Error>(requests[0].body.clone().into())]);
        let request_body = decode_zstd_body_for_server(request_stream).await.unwrap();
        assert_eq!(
            serde_json::from_slice::<serde_json::Value>(&request_body).unwrap(),
            serde_json::json!({"u": "user", "p": "pass"})
        );
    }

    #[tokio::test]
    async fn sync_login_maps_forbidden_to_auth_failed() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/sync/hostKey"))
            .respond_with(ResponseTemplate::new(403))
            .mount(&server)
            .await;

        let Err(err) = sync_login("user", "wrong", Some(server.uri()), Client::new()).await else {
            panic!("expected auth failure");
        };

        assert_eq!(unwrap_sync_err_kind(err), SyncErrorKind::AuthFailed);
    }

    #[tokio::test]
    async fn sync_login_maps_server_error_to_server_error_kind() {
        let server = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/sync/hostKey"))
            .respond_with(ResponseTemplate::new(500))
            .mount(&server)
            .await;

        let Err(err) = sync_login("user", "pass", Some(server.uri()), Client::new()).await else {
            panic!("expected server error");
        };

        assert_eq!(unwrap_sync_err_kind(err), SyncErrorKind::ServerError);
    }

    #[tokio::test]
    async fn sync_login_reports_network_error_when_connection_is_closed() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        let endpoint = format!("http://{addr}/");
        let client = Client::builder().no_proxy().build().unwrap();
        let close_connection = async {
            let (connection, _) = listener.accept().await.unwrap();
            drop(connection);
        };

        let (result, ()) = tokio::time::timeout(Duration::from_secs(5), async {
            tokio::join!(
                sync_login("user", "pass", Some(endpoint), client),
                close_connection
            )
        })
        .await
        .expect("login should fail when the server closes the connection");

        let Err(err) = result else {
            panic!("expected network error");
        };

        assert!(
            matches!(err, AnkiError::NetworkError { .. }),
            "expected a network error, got: {err:?}"
        );
    }
}
