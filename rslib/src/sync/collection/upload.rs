// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fs;

use anki_io::atomic_rename;
use anki_io::new_tempfile_in_parent_of;
use anki_io::write_file;
use axum::response::IntoResponse;
use axum::response::Response;
use reqwest::Client;

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

#[cfg(test)]
mod test {
    use futures::StreamExt;
    use tempfile::tempdir;

    use super::*;
    use crate::error::SyncError;

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
    fn check_upload_limit_allows_size_below_limit() {
        assert!(check_upload_limit(99, 100).is_ok());
    }

    #[test]
    fn check_upload_limit_errors_when_size_equals_limit() {
        // the boundary is inclusive: `size >= limit` is rejected
        let err = check_upload_limit(100, 100).unwrap_err();
        assert_eq!(unwrap_sync_err_kind(err), SyncErrorKind::UploadTooLarge);
    }

    #[test]
    fn check_upload_limit_reports_sizes_in_mb_when_over_limit() {
        let one_mb = 1024 * 1024;
        let err = check_upload_limit(2 * one_mb, one_mb).unwrap_err();
        let AnkiError::SyncError {
            source: SyncError { info, kind },
        } = err
        else {
            panic!()
        };
        assert_eq!(kind, SyncErrorKind::UploadTooLarge);
        // user-facing message compares the collection size to the limit
        assert_eq!(info, "2.00 MB > 1.00 MB");
    }

    /// Builds a valid, closed collection on disk and returns its bytes, so we
    /// can feed a realistic upload to `handle_received_upload`.
    fn valid_collection_bytes(path: &std::path::Path) -> Vec<u8> {
        let mut col = CollectionBuilder::new(path).build().unwrap();
        let nt = col.get_notetype_by_name("Basic").unwrap().unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "uploaded").unwrap();
        col.add_note(&mut note, DeckId(1)).unwrap();
        col.close(None).unwrap();
        fs::read(path).unwrap()
    }

    #[test]
    fn handle_received_upload_replaces_collection_with_valid_data() {
        let dir = tempdir().unwrap();
        let source_bytes = valid_collection_bytes(&dir.path().join("source.anki2"));

        // an empty target collection that will be overwritten
        let target_path = dir.path().join("target.anki2");
        let target = CollectionBuilder::new(&target_path).build().unwrap();
        let mut col = Some(target);

        let resp = handle_received_upload(&mut col, source_bytes).unwrap();

        assert_eq!(resp, UploadResponse::Ok);
        // the current collection is consumed on success
        assert!(col.is_none());
        // the replacement collection is the uploaded one (1 note)
        let reopened = CollectionBuilder::new(&target_path).build().unwrap();
        assert_eq!(
            reopened
                .storage
                .db_scalar::<u8>("select count() from notes")
                .unwrap(),
            1
        );
    }

    #[test]
    fn handle_received_upload_returns_corrupt_message_for_invalid_bytes() {
        let dir = tempdir().unwrap();
        let target_path = dir.path().join("target.anki2");
        let mut target = CollectionBuilder::new(&target_path).build().unwrap();
        let nt = target.get_notetype_by_name("Basic").unwrap().unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "existing note").unwrap();
        target.add_note(&mut note, DeckId(1)).unwrap();
        let mut col = Some(target);

        let resp = handle_received_upload(&mut col, b"not a collection".to_vec()).unwrap();

        assert_eq!(resp, UploadResponse::Err(CORRUPT_MESSAGE.into()));
        let target = col.expect("a rejected upload must leave the collection open");
        assert_eq!(target.storage.get_note(note.id).unwrap().unwrap(), note);
        target.close(None).unwrap();
        let reopened = CollectionBuilder::new(&target_path).build().unwrap();
        assert_eq!(reopened.storage.get_note(note.id).unwrap().unwrap(), note);
    }

    #[test]
    fn handle_received_upload_errors_when_collection_is_closed() {
        let dir = tempdir().unwrap();
        let source_bytes = valid_collection_bytes(&dir.path().join("source.anki2"));

        let mut col: Option<Collection> = None;
        let err = handle_received_upload(&mut col, source_bytes).unwrap_err();

        assert_eq!(err.code, axum::http::StatusCode::INTERNAL_SERVER_ERROR);
    }

    /// zstd-encodes a body the way the real server does, so the client's
    /// response decoder accepts it.
    async fn zstd_body(data: &[u8]) -> Vec<u8> {
        use crate::sync::request::header_and_stream::encode_zstd_body;
        let mut stream = encode_zstd_body(data.to_vec());
        let mut out = Vec::new();
        while let Some(chunk) = stream.next().await {
            out.extend_from_slice(&chunk.unwrap());
        }
        out
    }

    #[tokio::test]
    async fn full_upload_surfaces_server_rejection_as_server_message() {
        use reqwest::Url;
        use wiremock::matchers::method;
        use wiremock::matchers::path;
        use wiremock::Mock;
        use wiremock::MockServer;
        use wiremock::ResponseTemplate;

        // server accepts the upload but rejects it with a message instead of "OK"
        let rejection = b"server refused the upload";
        let mock = MockServer::start().await;
        Mock::given(method("POST"))
            .and(path("/sync/upload"))
            .respond_with(
                ResponseTemplate::new(200)
                    .insert_header("anki-original-size", rejection.len().to_string())
                    .set_body_bytes(zstd_body(rejection).await),
            )
            .mount(&mock)
            .await;

        let dir = tempdir().unwrap();
        let col = CollectionBuilder::new(dir.path().join("col.anki2"))
            .with_desktop_media_paths()
            .build()
            .unwrap();
        let auth = SyncAuth {
            hkey: "k".into(),
            endpoint: Some(Url::try_from(format!("{}/", mock.uri()).as_str()).unwrap()),
            io_timeout_secs: None,
        };
        let err = col.full_upload(auth, Client::new()).await.unwrap_err();

        let AnkiError::SyncError {
            source: SyncError { info, kind },
        } = err
        else {
            panic!("expected sync error");
        };
        assert_eq!(kind, SyncErrorKind::ServerMessage);
        assert_eq!(info, String::from_utf8_lossy(rejection));
    }

    #[tokio::test]
    async fn upload_response_preserves_legacy_http_body() {
        for (upload_response, expected_body) in [
            (UploadResponse::Ok, "OK"),
            (UploadResponse::Err(CORRUPT_MESSAGE.into()), CORRUPT_MESSAGE),
        ] {
            let response = upload_response.into_response();

            // Legacy clients expect HTTP 200 even when the upload is rejected.
            assert_eq!(response.status(), axum::http::StatusCode::OK);
            let body = axum::body::to_bytes(response.into_body(), 1024)
                .await
                .unwrap();
            assert_eq!(body.as_ref(), expected_body.as_bytes());
        }
    }
}
