// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_io::atomic_rename;
use anki_io::new_tempfile_in_parent_of;
use anki_io::read_file;
use reqwest::Client;
use tokio::io::BufWriter;

use crate::collection::CollectionBuilder;
use crate::prelude::*;
use crate::storage::SchemaVersion;
use crate::sync::collection::protocol::EmptyInput;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::login::SyncAuth;

impl Collection {
    /// Download collection from AnkiWeb. Caller must re-open afterwards.
    pub async fn full_download(self, auth: SyncAuth, client: Client) -> Result<()> {
        self.full_download_with_server(HttpSyncClient::new(auth, client))
            .await
    }

    // pub for tests
    pub(super) async fn full_download_with_server(self, server: HttpSyncClient) -> Result<()> {
        let col_path = self.col_path.clone();
        let _col_folder = col_path.parent().or_invalid("couldn't get col_folder")?;
        let progress = self.new_progress_handler();
        self.close(None)?;
        // stream straight to disk, so large collections don't need to fit in memory
        let temp_file = new_tempfile_in_parent_of(&col_path)?;
        {
            let file = tokio::fs::File::from_std(temp_file.as_file().try_clone()?);
            let mut writer = BufWriter::with_capacity(1024 * 1024, file);
            server
                .download_into_with_progress(EmptyInput::request(), progress, &mut writer)
                .await?;
        }
        // check file ok
        let col = CollectionBuilder::new(temp_file.path())
            .set_check_integrity(true)
            .build()?;
        col.storage.db.execute_batch("update col set ls=mod")?;
        col.close(None)?;
        atomic_rename(temp_file, &col_path, true)?;
        Ok(())
    }
}

pub fn server_download(
    col: &mut Option<Collection>,
    schema_version: SchemaVersion,
) -> HttpResult<Vec<u8>> {
    let col_path = {
        let mut col = col.take().or_internal_err("take col")?;
        let path = col.col_path.clone();
        col.transact_no_undo(|col| col.storage.increment_usn())
            .or_internal_err("incr usn")?;
        col.close(Some(schema_version)).or_internal_err("close")?;
        path
    };
    let data = read_file(col_path).or_internal_err("read col")?;
    Ok(data)
}

#[cfg(test)]
mod test {
    use std::collections::BTreeSet;

    use futures::TryStreamExt;
    use tempfile::tempdir;
    use wiremock::matchers::method;
    use wiremock::matchers::path;
    use wiremock::Mock;
    use wiremock::MockServer;
    use wiremock::ResponseTemplate;

    use super::*;
    use crate::sync::request::header_and_stream::encode_zstd_body;
    use crate::sync::response::ORIGINAL_SIZE;

    #[tokio::test]
    async fn invalid_full_download_preserves_the_local_collection() -> Result<()> {
        let dir = tempdir()?;
        let col_path = dir.path().join("collection.anki2");
        let col = CollectionBuilder::new(&col_path).build()?;
        col.close(None)?;
        let original = read_file(&col_path)?;
        let files = || {
            std::fs::read_dir(dir.path())?
                .map(|entry| entry.map(|entry| entry.file_name()))
                .collect::<std::io::Result<BTreeSet<_>>>()
        };
        let original_files = files()?;
        let col = CollectionBuilder::new(&col_path).build()?;
        let mock_server = MockServer::start().await;
        let body = b"not a collection".to_vec();
        let compressed: Vec<_> = encode_zstd_body(body.clone()).try_collect().await.unwrap();
        Mock::given(method("POST"))
            .and(path("/sync/download"))
            .respond_with(
                ResponseTemplate::new(200)
                    .insert_header(ORIGINAL_SIZE.as_str(), body.len().to_string())
                    .set_body_bytes(compressed.concat()),
            )
            .expect(1)
            .mount(&mock_server)
            .await;
        let server = HttpSyncClient::new(
            SyncAuth {
                hkey: "test".into(),
                endpoint: Some(mock_server.uri().parse().unwrap()),
                io_timeout_secs: None,
            },
            Client::new(),
        );

        assert!(col.full_download_with_server(server).await.is_err());
        assert_eq!(read_file(&col_path)?, original);
        CollectionBuilder::new(&col_path).build()?.close(None)?;
        assert_eq!(files()?, original_files);
        Ok(())
    }
}
