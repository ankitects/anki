// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs;
use std::io::Write;

use anki_io::atomic_rename;
use anki_io::new_tempfile_in_parent_of;
use anki_io::write_file;
use axum::response::IntoResponse;
use axum::response::Response;
use flate2::write::GzEncoder;
use flate2::Compression;
use futures::StreamExt;
use reqwest::Client;
use tokio_util::io::ReaderStream;

use crate::collection::CollectionBuilder;
use crate::error::SyncErrorKind;
use crate::prelude::*;
use crate::storage::SchemaVersion;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::login::SyncAuth;
use crate::sync::request::IntoSyncRequest;
use crate::sync::request::MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED;

/// Old clients didn't display a useful message on HTTP 400, and were expected
/// to show the error message returned by the server.
pub const CORRUPT_MESSAGE: &str =
    "Your upload was corrupt. Please use Check Database, or restore from backup.";

impl Collection {
    /// Upload collection to AnkiWeb. Caller must re-open afterwards.
    pub async fn full_upload(self, auth: SyncAuth, client: Client) -> Result<()> {
        self.full_upload_with_server(HttpSyncClient::new(auth, client))
            .await
    }

    // pub for tests
    pub(super) async fn full_upload_with_server(mut self, server: HttpSyncClient) -> Result<()> {
        self.before_upload()?;
        let col_path = self.col_path.clone();
        let progress = self.new_progress_handler();
        self.close(Some(SchemaVersion::V18))?;
        let col_data = fs::read(&col_path)?;

        let total_bytes = col_data.len();
        if server.endpoint.as_str().contains("ankiweb") {
            check_upload_limit(
                total_bytes,
                *MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED as usize,
            )?;
        }

        match server
            .upload_with_progress(col_data.try_into_sync_request()?, progress)
            .await?
            .upload_response()
        {
            UploadResponse::Ok => Ok(()),
            UploadResponse::Err(msg) => {
                Err(AnkiError::sync_error(msg, SyncErrorKind::ServerMessage))
            }
        }
    }
}

/// Collection must already be open, and will be replaced on success.
pub fn handle_received_upload(
    col: &mut Option<Collection>,
    new_data: Vec<u8>,
) -> HttpResult<UploadResponse> {
    let max_bytes = *MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED as usize;
    if new_data.len() >= max_bytes {
        return Ok(UploadResponse::Err("collection exceeds size limit".into()));
    }
    let path = col
        .as_ref()
        .or_internal_err("col was closed")?
        .col_path
        .clone();
    // write to temp file
    let temp_file = new_tempfile_in_parent_of(&path).or_internal_err("temp file")?;
    write_file(temp_file.path(), &new_data).or_internal_err("temp file")?;
    // check the collection is valid
    if let Err(err) = CollectionBuilder::new(temp_file.path())
        .set_check_integrity(true)
        .build()
    {
        tracing::info!(?err, "uploaded file was corrupt/failed to open");
        return Ok(UploadResponse::Err(CORRUPT_MESSAGE.into()));
    }
    // close collection and rename
    if let Some(col) = col.take() {
        col.close(None)
            .or_internal_err("closing current collection")?;
    }
    atomic_rename(temp_file, &path, true).or_internal_err("rename upload")?;
    Ok(UploadResponse::Ok)
}

impl IntoResponse for UploadResponse {
    fn into_response(self) -> Response {
        match self {
            // the legacy protocol expects this exact string
            UploadResponse::Ok => "OK".to_string(),
            UploadResponse::Err(e) => e,
        }
        .into_response()
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum UploadResponse {
    Ok,
    Err(String),
}

pub fn check_upload_limit(size: usize, limit: usize) -> Result<()> {
    let size_of_one_mb: f64 = 1024.0 * 1024.0;
    let collection_size_in_mb: f64 = size as f64 / size_of_one_mb;
    let limit_size_in_mb: f64 = limit as f64 / size_of_one_mb;

    if size >= limit {
        Err(AnkiError::sync_error(
            format!("{collection_size_in_mb:.2} MB > {limit_size_in_mb:.2} MB"),
            SyncErrorKind::UploadTooLarge,
        ))
    } else {
        Ok(())
    }
}

pub async fn gzipped_data_from_vec(vec: Vec<u8>) -> Result<Vec<u8>> {
    let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
    let mut stream = ReaderStream::new(&vec[..]);
    while let Some(chunk) = stream.next().await {
        let chunk = chunk?;
        encoder.write_all(&chunk)?;
    }
    encoder.finish().map_err(Into::into)
}
