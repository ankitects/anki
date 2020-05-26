// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::*;

static SYNC_VERSION: u8 = 10;
pub struct HTTPSyncClient<'a> {
    hkey: Option<String>,
    skey: String,
    client: Client,
    endpoint: &'a str,
}

#[derive(Serialize)]
struct HostKeyIn<'a> {
    #[serde(rename = "u")]
    username: &'a str,
    #[serde(rename = "p")]
    password: &'a str,
}
#[derive(Deserialize)]
struct HostKeyOut {
    key: String,
}

#[derive(Serialize)]
struct MetaIn<'a> {
    #[serde(rename = "v")]
    sync_version: u8,
    #[serde(rename = "cv")]
    client_version: &'a str,
}

#[derive(Serialize, Deserialize, Debug)]
struct StartIn {
    #[serde(rename = "minUsn")]
    minimum_usn: Usn,
    #[serde(rename = "offset")]
    minutes_west: i32,
    // only used to modify behaviour of changes()
    #[serde(rename = "lnewer")]
    client_is_newer: bool,
    // used by 2.0 clients
    #[serde(skip_serializing_if = "Option::is_none")]
    client_graves: Option<Graves>,
}

#[derive(Serialize, Deserialize, Debug)]
struct ApplyGravesIn {
    chunk: Graves,
}

#[derive(Serialize, Deserialize, Debug)]
struct ApplyChangesIn {
    changes: Changes,
}

#[derive(Serialize, Deserialize, Debug)]
struct ApplyChunkIn {
    chunk: Chunk,
}

#[derive(Serialize, Deserialize, Debug)]
struct SanityCheckIn {
    client: SanityCheckCounts,
}

#[derive(Serialize)]
struct Empty {}

impl HTTPSyncClient<'_> {
    pub fn new<'a>(endpoint: &'a str) -> HTTPSyncClient<'a> {
        let client = Client::builder()
            .connect_timeout(Duration::from_secs(30))
            .timeout(Duration::from_secs(60))
            .build()
            .unwrap();
        let skey = guid();
        HTTPSyncClient {
            hkey: None,
            skey,
            client,
            endpoint,
        }
    }

    async fn json_request<T>(&self, method: &str, json: &T) -> Result<Response>
    where
        T: serde::Serialize,
    {
        let req_json = serde_json::to_vec(json)?;

        let mut gz = GzEncoder::new(Vec::new(), Compression::fast());
        gz.write_all(&req_json)?;
        let part = multipart::Part::bytes(gz.finish()?);

        self.request(method, part).await
    }

    async fn json_request_deserialized<T, T2>(&self, method: &str, json: &T) -> Result<T2>
    where
        T: Serialize,
        T2: DeserializeOwned,
    {
        self.json_request(method, json)
            .await?
            .json()
            .await
            .map_err(Into::into)
    }

    async fn request(&self, method: &str, data_part: multipart::Part) -> Result<Response> {
        let data_part = data_part.file_name("data");

        let mut form = multipart::Form::new()
            .part("data", data_part)
            .text("c", "1");
        if let Some(hkey) = &self.hkey {
            form = form.text("k", hkey.clone()).text("s", self.skey.clone());
        }

        let url = format!("{}{}", self.endpoint, method);
        let req = self.client.post(&url).multipart(form);

        req.send().await?.error_for_status().map_err(Into::into)
    }

    async fn login(&mut self, username: &str, password: &str) -> Result<()> {
        let resp: HostKeyOut = self
            .json_request_deserialized("hostKey", &HostKeyIn { username, password })
            .await?;
        self.hkey = Some(resp.key);

        Ok(())
    }

    pub(crate) fn hkey(&self) -> &str {
        self.hkey.as_ref().unwrap()
    }

    async fn meta(&mut self) -> Result<ServerMeta> {
        let meta_in = MetaIn {
            sync_version: SYNC_VERSION,
            client_version: sync_client_version(),
        };
        self.json_request_deserialized("meta", &meta_in).await
    }

    async fn start(&mut self, input: &StartIn) -> Result<Graves> {
        self.json_request_deserialized("start", input).await
    }

    async fn apply_graves(&mut self, chunk: Graves) -> Result<()> {
        let input = ApplyGravesIn { chunk };
        let resp = self.json_request("applyGraves", &input).await?;
        resp.error_for_status()?;
        Ok(())
    }

    async fn apply_changes(&mut self, changes: Changes) -> Result<Changes> {
        let input = ApplyChangesIn { changes };
        self.json_request_deserialized("applyChanges", &input).await
    }

    async fn chunk(&mut self) -> Result<Chunk> {
        self.json_request_deserialized("chunk", &Empty {}).await
    }

    async fn apply_chunk(&mut self, chunk: Chunk) -> Result<()> {
        let input = ApplyChunkIn { chunk };
        let resp = self.json_request("applyChunk", &input).await?;
        resp.error_for_status()?;
        Ok(())
    }

    async fn sanity_check(&mut self, client: SanityCheckCounts) -> Result<SanityCheckOut> {
        let input = SanityCheckIn { client };
        self.json_request_deserialized("sanityCheck2", &input).await
    }

    async fn finish(&mut self) -> Result<()> {
        let resp = self.json_request("finish", &Empty {}).await?;
        resp.error_for_status()?;
        Ok(())
    }

    async fn abort(&mut self) -> Result<()> {
        let resp = self.json_request("abort", &Empty {}).await?;
        resp.error_for_status()?;
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::err::SyncErrorKind;
    use tokio::runtime::Runtime;

    static ENDPOINT: &'static str = "https://sync.ankiweb.net/sync/";

    async fn http_client_inner(username: String, password: String) -> Result<()> {
        let mut syncer = HTTPSyncClient::new(ENDPOINT);

        assert!(matches!(
            syncer.login("nosuchuser", "nosuchpass").await,
            Err(AnkiError::SyncError {
                kind: SyncErrorKind::AuthFailed,
                ..
            })
        ));

        assert!(syncer.login(&username, &password).await.is_ok());

        syncer.meta().await?;

        // aborting before a start is a conflict
        assert!(matches!(
            syncer.abort().await,
            Err(AnkiError::SyncError {
                kind: SyncErrorKind::Conflict,
                ..
            })
        ));

        let input = StartIn {
            minimum_usn: Usn(0),
            minutes_west: 0,
            client_is_newer: true,
            client_graves: None,
        };

        let _graves = syncer.start(&input).await?;

        // aborting should now work
        syncer.abort().await?;

        // start again, and continue
        let _graves = syncer.start(&input).await?;

        syncer.apply_graves(Graves::default()).await?;

        let _changes = syncer.apply_changes(Changes::default()).await?;
        let _chunk = syncer.chunk().await?;
        syncer
            .apply_chunk(Chunk {
                done: true,
                ..Default::default()
            })
            .await?;

        let _out = syncer
            .sanity_check(SanityCheckCounts {
                counts: SanityCheckDueCounts {
                    new: 0,
                    learn: 0,
                    review: 0,
                },
                cards: 0,
                notes: 0,
                revlog: 0,
                graves: 0,
                notetypes: 0,
                decks: 0,
                deck_config: 0,
            })
            .await?;

        syncer.finish().await?;

        Ok(())
    }

    #[test]
    fn http_client() -> Result<()> {
        let user = match std::env::var("TEST_SYNC_USER") {
            Ok(s) => s,
            Err(_) => {
                return Ok(());
            }
        };
        let pass = std::env::var("TEST_SYNC_PASS").unwrap();

        let mut rt = Runtime::new().unwrap();
        rt.block_on(http_client_inner(user, pass))
    }
}
