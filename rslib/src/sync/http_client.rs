// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::*;
use bytes::Bytes;
use futures::Stream;
use reqwest::Body;

static SYNC_VERSION: u8 = 10;
pub struct HTTPSyncClient {
    hkey: Option<String>,
    skey: String,
    client: Client,
    endpoint: String,
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

impl HTTPSyncClient {
    pub fn new<'a>(endpoint_suffix: &str) -> HTTPSyncClient {
        let client = Client::builder()
            .connect_timeout(Duration::from_secs(30))
            .timeout(Duration::from_secs(60))
            .build()
            .unwrap();
        let skey = guid();
        let endpoint = endpoint(&endpoint_suffix);
        HTTPSyncClient {
            hkey: None,
            skey,
            client,
            endpoint,
        }
    }

    async fn json_request<T>(&self, method: &str, json: &T, timeout_long: bool) -> Result<Response>
    where
        T: serde::Serialize,
    {
        let req_json = serde_json::to_vec(json)?;

        let mut gz = GzEncoder::new(Vec::new(), Compression::fast());
        gz.write_all(&req_json)?;
        let part = multipart::Part::bytes(gz.finish()?);

        self.request(method, part, timeout_long).await
    }

    async fn json_request_deserialized<T, T2>(&self, method: &str, json: &T) -> Result<T2>
    where
        T: Serialize,
        T2: DeserializeOwned,
    {
        self.json_request(method, json, false)
            .await?
            .json()
            .await
            .map_err(Into::into)
    }

    async fn request(
        &self,
        method: &str,
        data_part: multipart::Part,
        timeout_long: bool,
    ) -> Result<Response> {
        let data_part = data_part.file_name("data");

        let mut form = multipart::Form::new()
            .part("data", data_part)
            .text("c", "1");
        if let Some(hkey) = &self.hkey {
            form = form.text("k", hkey.clone()).text("s", self.skey.clone());
        }

        let url = format!("{}{}", self.endpoint, method);
        let mut req = self.client.post(&url).multipart(form);

        if timeout_long {
            req = req.timeout(Duration::from_secs(60 * 60));
        }

        req.send().await?.error_for_status().map_err(Into::into)
    }

    pub(crate) async fn login(&mut self, username: &str, password: &str) -> Result<()> {
        let resp: HostKeyOut = self
            .json_request_deserialized("hostKey", &HostKeyIn { username, password })
            .await?;
        self.hkey = Some(resp.key);

        Ok(())
    }

    pub(crate) fn hkey(&self) -> &str {
        self.hkey.as_ref().unwrap()
    }

    pub(crate) async fn meta(&self) -> Result<ServerMeta> {
        let meta_in = MetaIn {
            sync_version: SYNC_VERSION,
            client_version: sync_client_version(),
        };
        self.json_request_deserialized("meta", &meta_in).await
    }

    pub(crate) async fn start(
        &self,
        minimum_usn: Usn,
        minutes_west: i32,
        client_is_newer: bool,
    ) -> Result<Graves> {
        let input = StartIn {
            minimum_usn,
            minutes_west,
            client_is_newer,
            client_graves: None,
        };
        self.json_request_deserialized("start", &input).await
    }

    pub(crate) async fn apply_graves(&self, chunk: Graves) -> Result<()> {
        let input = ApplyGravesIn { chunk };
        let resp = self.json_request("applyGraves", &input, false).await?;
        resp.error_for_status()?;
        Ok(())
    }

    pub(crate) async fn apply_changes(&self, changes: Changes) -> Result<Changes> {
        let input = ApplyChangesIn { changes };
        self.json_request_deserialized("applyChanges", &input).await
    }

    pub(crate) async fn chunk(&self) -> Result<Chunk> {
        self.json_request_deserialized("chunk", &Empty {}).await
    }

    pub(crate) async fn apply_chunk(&self, chunk: Chunk) -> Result<()> {
        let input = ApplyChunkIn { chunk };
        let resp = self.json_request("applyChunk", &input, false).await?;
        resp.error_for_status()?;
        Ok(())
    }

    pub(crate) async fn sanity_check(&self, client: SanityCheckCounts) -> Result<SanityCheckOut> {
        let input = SanityCheckIn { client };
        self.json_request_deserialized("sanityCheck2", &input).await
    }

    pub(crate) async fn finish(&self) -> Result<()> {
        let resp = self.json_request("finish", &Empty {}, false).await?;
        resp.error_for_status()?;
        Ok(())
    }

    pub(crate) async fn abort(&self) -> Result<()> {
        let resp = self.json_request("abort", &Empty {}, false).await?;
        resp.error_for_status()?;
        Ok(())
    }

    async fn download_inner(
        &self,
    ) -> Result<(
        usize,
        impl Stream<Item = std::result::Result<Bytes, reqwest::Error>>,
    )> {
        let resp: reqwest::Response = self.json_request("download", &Empty {}, true).await?;
        let len = resp.content_length().unwrap_or_default();
        Ok((len as usize, resp.bytes_stream()))
    }

    /// Download collection into a temporary file, returning it.
    /// Caller should persist the file in the correct path after checking it.
    pub(crate) async fn download<P>(&self, folder: &Path, progress_fn: P) -> Result<NamedTempFile>
    where
        P: Fn(&FullSyncProgress),
    {
        let mut temp_file = NamedTempFile::new_in(folder)?;
        let (size, mut stream) = self.download_inner().await?;
        let mut progress = FullSyncProgress {
            transferred_bytes: 0,
            total_bytes: size,
        };
        while let Some(chunk) = stream.next().await {
            let chunk = chunk?;
            temp_file.write_all(&chunk)?;
            progress.transferred_bytes += chunk.len();
            progress_fn(&progress);
        }

        Ok(temp_file)
    }

    async fn upload_inner(&self, body: Body) -> Result<()> {
        let data_part = multipart::Part::stream(body);
        let resp = self.request("upload", data_part, true).await?;
        resp.error_for_status()?;
        Ok(())
    }

    pub(crate) async fn upload<P>(&mut self, col_path: &Path, progress_fn: P) -> Result<()>
    where
        P: Fn(&FullSyncProgress) + Send + Sync + 'static,
    {
        let file = tokio::fs::File::open(col_path).await?;
        let total_bytes = file.metadata().await?.len() as usize;
        let wrap1 = ProgressWrapper {
            reader: file,
            progress_fn,
            progress: FullSyncProgress {
                transferred_bytes: 0,
                total_bytes,
            },
        };
        let wrap2 = async_compression::stream::GzipEncoder::new(wrap1);
        let body = Body::wrap_stream(wrap2);
        self.upload_inner(body).await?;

        Ok(())
    }
}

use futures::{
    ready,
    task::{Context, Poll},
};
use pin_project::pin_project;
use std::pin::Pin;
use tokio::io::AsyncRead;

#[pin_project]
struct ProgressWrapper<S, P> {
    #[pin]
    reader: S,
    progress_fn: P,
    progress: FullSyncProgress,
}

impl<S, P> Stream for ProgressWrapper<S, P>
where
    S: AsyncRead,
    P: Fn(&FullSyncProgress),
{
    type Item = std::result::Result<Bytes, std::io::Error>;

    fn poll_next(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
        let mut buf = vec![0; 16 * 1024];
        let this = self.project();
        match ready!(this.reader.poll_read(cx, &mut buf)) {
            Ok(0) => Poll::Ready(None),
            Ok(size) => {
                buf.resize(size, 0);
                this.progress.transferred_bytes += size;
                (this.progress_fn)(&this.progress);
                Poll::Ready(Some(Ok(Bytes::from(buf))))
            }
            Err(e) => Poll::Ready(Some(Err(e))),
        }
    }
}

fn endpoint(suffix: &str) -> String {
    if let Ok(endpoint) = std::env::var("SYNC_ENDPOINT") {
        endpoint
    } else {
        format!("https://sync{}.ankiweb.net/sync/", suffix)
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::err::SyncErrorKind;
    use tokio::runtime::Runtime;

    async fn http_client_inner(username: String, password: String) -> Result<()> {
        let mut syncer = HTTPSyncClient::new("");

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

        let _graves = syncer.start(Usn(1), 0, true).await?;

        // aborting should now work
        syncer.abort().await?;

        // start again, and continue
        let _graves = syncer.start(Usn(0), 0, true).await?;

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

        use tempfile::tempdir;

        let dir = tempdir()?;
        let out_path = syncer
            .download(&dir.path(), |progress| {
                println!("progress: {:?}", progress);
            })
            .await?;

        syncer
            .upload(&out_path.path(), |progress| {
                println!("progress {:?}", progress);
            })
            .await?;

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
        env_logger::init();

        let mut rt = Runtime::new().unwrap();
        rt.block_on(http_client_inner(user, pass))
    }
}
