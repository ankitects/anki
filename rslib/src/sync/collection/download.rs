// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_io::atomic_rename;
use anki_io::new_tempfile_in_parent_of;
use anki_io::read_file;
use anki_io::write_file;
use reqwest::Client;

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
        let out_data = server
            .download_with_progress(EmptyInput::request(), progress)
            .await?
            .data;
        // check file ok
        let temp_file = new_tempfile_in_parent_of(&col_path)?;
        write_file(temp_file.path(), out_data)?;
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
