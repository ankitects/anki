// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    env,
    io::{prelude::*, Cursor},
    mem::MaybeUninit,
    path::Path,
    time::Duration,
};

use async_trait::async_trait;
use bytes::Bytes;
use flate2::{write::GzEncoder, Compression};
use futures::{Stream, StreamExt};
use lazy_static::lazy_static;
use reqwest::{multipart, Body, Client, Response};
use serde::de::DeserializeOwned;
use tempfile::NamedTempFile;
use tokio_util::io::ReaderStream;

use super::{
    http::{
        ApplyChangesRequest, ApplyChunkRequest, ApplyGravesRequest, HostKeyRequest,
        HostKeyResponse, MetaRequest, SanityCheckRequest, StartRequest, SyncRequest,
    },
    server::SyncServer,
    Chunk, FullSyncProgress, Graves, SanityCheckCounts, SanityCheckResponse, SyncMeta,
    UnchunkedChanges, SYNC_VERSION_MAX,
};
use crate::{error::SyncErrorKind, notes::guid, prelude::*, version::sync_client_version};

lazy_static! {
    // These limits are enforced server-side, but are made adjustable for users
    // who are using a custom sync server.
    static ref MAXIMUM_UPLOAD_MEGS_UNCOMPRESSED: usize = env::var("MAX_UPLOAD_MEGS_UNCOMP")
        .map(|v| v.parse().expect("invalid upload limit"))
        .unwrap_or(250);
    static ref MAXIMUM_UPLOAD_MEGS_COMPRESSED: usize = env::var("MAX_UPLOAD_MEGS_COMP")
        .map(|v| v.parse().expect("invalid upload limit"))
        .unwrap_or(100);
}

pub type FullSyncProgressFn = Box<dyn FnMut(FullSyncProgress, bool) + Send + Sync + 'static>;

pub struct HttpSyncClient {
    hkey: Option<String>,
    skey: String,
    client: Client,
    endpoint: String,
    full_sync_progress_fn: Option<FullSyncProgressFn>,
}

pub struct Timeouts {
    pub connect_secs: u64,
    pub request_secs: u64,
    pub io_secs: u64,
}

impl Timeouts {
    pub fn new() -> Self {
        let io_secs = if std::env::var("LONG_IO_TIMEOUT").is_ok() {
            3600
        } else {
            300
        };
        Timeouts {
            connect_secs: 30,
            /// This is smaller than the I/O limit because it is just a
            /// default - some longer-running requests override it.
            request_secs: 60,
            io_secs,
        }
    }
}

#[async_trait(?Send)]
impl SyncServer for HttpSyncClient {
    async fn meta(&self) -> Result<SyncMeta> {
        let input = SyncRequest::Meta(MetaRequest {
            sync_version: SYNC_VERSION_MAX,
            client_version: sync_client_version().to_string(),
        });
        self.json_request(input).await
    }

    async fn start(
        &mut self,
        client_usn: Usn,
        local_is_newer: bool,
        deprecated_client_graves: Option<Graves>,
    ) -> Result<Graves> {
        let input = SyncRequest::Start(StartRequest {
            client_usn,
            local_is_newer,
            deprecated_client_graves,
        });
        self.json_request(input).await
    }

    async fn apply_graves(&mut self, chunk: Graves) -> Result<()> {
        let input = SyncRequest::ApplyGraves(ApplyGravesRequest { chunk });
        self.json_request(input).await
    }

    async fn apply_changes(&mut self, changes: UnchunkedChanges) -> Result<UnchunkedChanges> {
        let input = SyncRequest::ApplyChanges(ApplyChangesRequest { changes });
        self.json_request(input).await
    }

    async fn chunk(&mut self) -> Result<Chunk> {
        let input = SyncRequest::Chunk;
        self.json_request(input).await
    }

    async fn apply_chunk(&mut self, chunk: Chunk) -> Result<()> {
        let input = SyncRequest::ApplyChunk(ApplyChunkRequest { chunk });
        self.json_request(input).await
    }

    async fn sanity_check(&mut self, client: SanityCheckCounts) -> Result<SanityCheckResponse> {
        let input = SyncRequest::SanityCheck(SanityCheckRequest { client });
        self.json_request(input).await
    }

    async fn finish(&mut self) -> Result<TimestampMillis> {
        let input = SyncRequest::Finish;
        self.json_request(input).await
    }

    async fn abort(&mut self) -> Result<()> {
        let input = SyncRequest::Abort;
        self.json_request(input).await
    }

    async fn full_upload(mut self: Box<Self>, col_path: &Path, _can_consume: bool) -> Result<()> {
        let file = tokio::fs::File::open(col_path).await?;
        let total_bytes = file.metadata().await?.len() as usize;
        check_upload_limit(total_bytes, *MAXIMUM_UPLOAD_MEGS_UNCOMPRESSED)?;
        let compressed_data: Vec<u8> = gzipped_data_from_tokio_file(file).await?;
        let compressed_size = compressed_data.len();
        check_upload_limit(compressed_size, *MAXIMUM_UPLOAD_MEGS_COMPRESSED)?;
        let progress_fn = self
            .full_sync_progress_fn
            .take()
            .expect("progress func was not set");
        let with_progress = ProgressWrapper {
            reader: Cursor::new(compressed_data),
            progress_fn,
            progress: FullSyncProgress {
                transferred_bytes: 0,
                total_bytes: compressed_size,
            },
        };
        let body = Body::wrap_stream(with_progress);
        self.upload_inner(body).await?;

        Ok(())
    }

    /// Download collection into a temporary file, returning it. Caller should
    /// persist the file in the correct path after checking it. Progress func
    /// must be set first. The caller should pass the collection's folder in as
    /// the temp folder if it wishes to atomically .persist() it.
    async fn full_download(
        mut self: Box<Self>,
        col_folder: Option<&Path>,
    ) -> Result<NamedTempFile> {
        let mut temp_file = if let Some(folder) = col_folder {
            NamedTempFile::new_in(folder)
        } else {
            NamedTempFile::new()
        }?;
        let (size, mut stream) = self.download_inner().await?;
        let mut progress = FullSyncProgress {
            transferred_bytes: 0,
            total_bytes: size,
        };
        let mut progress_fn = self
            .full_sync_progress_fn
            .take()
            .expect("progress func was not set");
        while let Some(chunk) = stream.next().await {
            let chunk = chunk?;
            temp_file.write_all(&chunk)?;
            progress.transferred_bytes += chunk.len();
            progress_fn(progress, true);
        }
        progress_fn(progress, false);
        Ok(temp_file)
    }
}

async fn gzipped_data_from_tokio_file(file: tokio::fs::File) -> Result<Vec<u8>> {
    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
    let mut stream = ReaderStream::new(file);
    while let Some(chunk) = stream.next().await {
        let chunk = chunk?;
        encoder.write_all(&chunk)?;
    }
    encoder.finish().map_err(Into::into)
}

fn check_upload_limit(size: usize, limit_mb: usize) -> Result<()> {
    let size_mb = size / 1024 / 1024;
    if size_mb >= limit_mb {
        Err(AnkiError::sync_error(
            format!("{}MB > {}MB", size_mb, limit_mb),
            SyncErrorKind::UploadTooLarge,
        ))
    } else {
        Ok(())
    }
}

impl HttpSyncClient {
    pub fn new(hkey: Option<String>, host_number: u32) -> HttpSyncClient {
        let timeouts = Timeouts::new();
        let client = Client::builder()
            .connect_timeout(Duration::from_secs(timeouts.connect_secs))
            .timeout(Duration::from_secs(timeouts.request_secs))
            .io_timeout(Duration::from_secs(timeouts.io_secs))
            .build()
            .unwrap();
        let skey = guid();
        let endpoint = sync_endpoint(host_number);
        HttpSyncClient {
            hkey,
            skey,
            client,
            endpoint,
            full_sync_progress_fn: None,
        }
    }

    pub fn set_full_sync_progress_fn(&mut self, func: Option<FullSyncProgressFn>) {
        self.full_sync_progress_fn = func;
    }

    async fn json_request<T>(&self, req: SyncRequest) -> Result<T>
    where
        T: DeserializeOwned,
    {
        let (method, req_json) = req.into_method_and_data()?;
        self.request_bytes(method, &req_json, false)
            .await?
            .json()
            .await
            .map_err(Into::into)
    }

    async fn request_bytes(
        &self,
        method: &str,
        req: &[u8],
        timeout_long: bool,
    ) -> Result<Response> {
        let mut gz = GzEncoder::new(Vec::new(), Compression::fast());
        gz.write_all(req)?;
        let part = multipart::Part::bytes(gz.finish()?);
        let resp = self.request(method, part, timeout_long).await?;
        resp.error_for_status().map_err(Into::into)
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

    pub(crate) async fn login<S: Into<String>>(&mut self, username: S, password: S) -> Result<()> {
        let input = SyncRequest::HostKey(HostKeyRequest {
            username: username.into(),
            password: password.into(),
        });
        let output: HostKeyResponse = self.json_request(input).await?;
        self.hkey = Some(output.key);

        Ok(())
    }

    pub(crate) fn hkey(&self) -> &str {
        self.hkey.as_ref().unwrap()
    }

    async fn download_inner(
        &self,
    ) -> Result<(
        usize,
        impl Stream<Item = std::result::Result<Bytes, reqwest::Error>>,
    )> {
        let resp: reqwest::Response = self.request_bytes("download", b"{}", true).await?;
        let len = resp.content_length().unwrap_or_default();
        Ok((len as usize, resp.bytes_stream()))
    }

    async fn upload_inner(&self, body: Body) -> Result<()> {
        let data_part = multipart::Part::stream(body);
        let resp = self.request("upload", data_part, true).await?;
        resp.error_for_status_ref()?;
        let text = resp.text().await?;
        if text != "OK" {
            Err(AnkiError::sync_error(text, SyncErrorKind::Other))
        } else {
            Ok(())
        }
    }
}

use std::pin::Pin;

use futures::{
    ready,
    task::{Context, Poll},
};
use pin_project::pin_project;
use tokio::io::{AsyncRead, ReadBuf};

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
    P: FnMut(FullSyncProgress, bool),
{
    type Item = std::result::Result<Bytes, std::io::Error>;

    fn poll_next(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Option<Self::Item>> {
        let mut buf = [MaybeUninit::<u8>::uninit(); 8192];
        let mut buf = ReadBuf::uninit(&mut buf);
        let this = self.project();
        let res = ready!(this.reader.poll_read(cx, &mut buf));
        match res {
            Ok(()) => {
                let filled = buf.filled().to_vec();
                Poll::Ready(if filled.is_empty() {
                    (this.progress_fn)(*this.progress, false);
                    None
                } else {
                    this.progress.transferred_bytes += filled.len();
                    (this.progress_fn)(*this.progress, true);
                    Some(Ok(Bytes::from(filled)))
                })
            }
            Err(e) => Poll::Ready(Some(Err(e))),
        }
    }
}

fn sync_endpoint(host_number: u32) -> String {
    if let Ok(endpoint) = std::env::var("SYNC_ENDPOINT") {
        endpoint
    } else {
        let suffix = if host_number > 0 {
            format!("{}", host_number)
        } else {
            "".to_string()
        };
        format!("https://sync{}.ankiweb.net/sync/", suffix)
    }
}

#[cfg(test)]
mod test {
    use tokio::runtime::Runtime;

    use super::*;
    use crate::{
        error::{SyncError, SyncErrorKind},
        sync::SanityCheckDueCounts,
    };

    async fn http_client_inner(username: String, password: String) -> Result<()> {
        let mut syncer = Box::new(HttpSyncClient::new(None, 0));

        assert!(matches!(
            syncer.login("nosuchuser", "nosuchpass").await,
            Err(AnkiError::SyncError(SyncError {
                kind: SyncErrorKind::AuthFailed,
                ..
            }))
        ));

        assert!(syncer.login(&username, &password).await.is_ok());

        let _meta = syncer.meta().await?;

        // aborting before a start is a conflict
        assert!(matches!(
            syncer.abort().await,
            Err(AnkiError::SyncError(SyncError {
                kind: SyncErrorKind::Conflict,
                ..
            }))
        ));

        let _graves = syncer.start(Usn(1), true, None).await?;

        // aborting should now work
        syncer.abort().await?;

        // start again, and continue
        let _graves = syncer.start(Usn(1), true, None).await?;

        syncer.apply_graves(Graves::default()).await?;

        let _changes = syncer.apply_changes(UnchunkedChanges::default()).await?;
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

        // failed sanity check will have cleaned up; can't finish
        // syncer.finish().await?;

        syncer.set_full_sync_progress_fn(Some(Box::new(|progress, _throttle| {
            println!("progress: {:?}", progress);
        })));
        let out_path = syncer.full_download(None).await?;

        let mut syncer = Box::new(HttpSyncClient::new(None, 0));
        syncer.set_full_sync_progress_fn(Some(Box::new(|progress, _throttle| {
            println!("progress {:?}", progress);
        })));
        syncer.full_upload(out_path.path(), false).await?;

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

        let rt = Runtime::new().unwrap();
        rt.block_on(http_client_inner(user, pass))
    }
}
