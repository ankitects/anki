// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{fs, path::Path};

use crate::{
    prelude::*,
    storage::open_and_check_sqlite_file,
    sync::{
        Chunk, Graves, SanityCheckCounts, SanityCheckOut, SanityCheckStatus, SyncMeta,
        UnchunkedChanges, Usn,
    },
};
use async_trait::async_trait;
use tempfile::NamedTempFile;

use super::ChunkableIDs;
#[async_trait(?Send)]
pub trait SyncServer {
    async fn meta(&self) -> Result<SyncMeta>;
    async fn start(
        &mut self,
        client_usn: Usn,
        local_is_newer: bool,
        deprecated_client_graves: Option<Graves>,
    ) -> Result<Graves>;
    async fn apply_graves(&mut self, client_chunk: Graves) -> Result<()>;
    async fn apply_changes(&mut self, client_changes: UnchunkedChanges)
        -> Result<UnchunkedChanges>;
    async fn chunk(&mut self) -> Result<Chunk>;
    async fn apply_chunk(&mut self, client_chunk: Chunk) -> Result<()>;
    async fn sanity_check(&mut self, client: SanityCheckCounts) -> Result<SanityCheckOut>;
    async fn finish(&mut self) -> Result<TimestampMillis>;
    async fn abort(&mut self) -> Result<()>;

    /// If `can_consume` is true, the local server will move or remove the file, instead
    /// creating a copy. The remote server ignores this argument.
    async fn full_upload(self: Box<Self>, col_path: &Path, can_consume: bool) -> Result<()>;
    async fn full_download(self: Box<Self>) -> Result<NamedTempFile>;
}

pub struct LocalServer {
    col: Collection,

    // The current sync protocol is stateful, so unfortunately we need to
    // retain a bunch of information across requests. These are set either
    // on start, or on subsequent methods.
    server_usn: Usn,
    client_usn: Usn,
    /// Only used to determine whether we should send our
    /// config to client.
    client_is_newer: bool,
    /// Set on the first call to chunk()
    server_chunk_ids: Option<ChunkableIDs>,
}

impl LocalServer {
    #[allow(dead_code)]
    pub fn new(col: Collection) -> LocalServer {
        assert!(col.server);
        LocalServer {
            col,
            server_usn: Usn(0),
            client_usn: Usn(0),
            client_is_newer: false,
            server_chunk_ids: None,
        }
    }

    /// Consumes self and returns the stored collection. If a sync has begun, caller must ensure they
    /// call .finish() or .abort() before calling this.
    pub fn into_col(self) -> Collection {
        self.col
    }
}

#[async_trait(?Send)]
impl SyncServer for LocalServer {
    async fn meta(&self) -> Result<SyncMeta> {
        Ok(SyncMeta {
            modified: self.col.storage.get_modified_time()?,
            schema: self.col.storage.get_schema_mtime()?,
            usn: self.col.storage.usn(true)?,
            current_time: TimestampSecs::now(),
            server_message: String::new(),
            should_continue: true,
            host_number: 0,
            empty: !self.col.storage.have_at_least_one_card()?,
        })
    }

    async fn start(
        &mut self,
        client_usn: Usn,
        client_is_newer: bool,
        deprecated_client_graves: Option<Graves>,
    ) -> Result<Graves> {
        self.server_usn = self.col.usn()?;
        self.client_usn = client_usn;
        self.client_is_newer = client_is_newer;

        self.col.storage.begin_rust_trx()?;

        // make sure any pending cards have been unburied first if necessary
        let timing = self.col.timing_today()?;
        self.col.unbury_if_day_rolled_over(timing)?;

        // fetch local graves
        let server_graves = self.col.storage.pending_graves(client_usn)?;
        // handle AnkiDroid using old protocol
        if let Some(graves) = deprecated_client_graves {
            self.col.apply_graves(graves, self.server_usn)?;
        }

        Ok(server_graves)
    }

    async fn apply_graves(&mut self, client_chunk: Graves) -> Result<()> {
        self.col.apply_graves(client_chunk, self.server_usn)
    }

    async fn apply_changes(
        &mut self,
        client_changes: UnchunkedChanges,
    ) -> Result<UnchunkedChanges> {
        let server_changes =
            self.col
                .local_unchunked_changes(self.client_usn, None, !self.client_is_newer)?;
        self.col.apply_changes(client_changes, self.server_usn)?;
        Ok(server_changes)
    }

    async fn chunk(&mut self) -> Result<Chunk> {
        if self.server_chunk_ids.is_none() {
            self.server_chunk_ids = Some(self.col.get_chunkable_ids(self.client_usn)?);
        }

        self.col
            .get_chunk(self.server_chunk_ids.as_mut().unwrap(), None)
    }

    async fn apply_chunk(&mut self, client_chunk: Chunk) -> Result<()> {
        self.col.apply_chunk(client_chunk, self.client_usn)
    }

    async fn sanity_check(&mut self, mut client: SanityCheckCounts) -> Result<SanityCheckOut> {
        client.counts = Default::default();
        let server = self.col.storage.sanity_check_info()?;
        Ok(SanityCheckOut {
            status: if client == server {
                SanityCheckStatus::Ok
            } else {
                SanityCheckStatus::Bad
            },
            client: Some(client),
            server: Some(server),
        })
    }

    async fn finish(&mut self) -> Result<TimestampMillis> {
        let now = TimestampMillis::now();
        self.col.storage.set_modified_time(now)?;
        self.col.storage.set_last_sync(now)?;
        self.col.storage.increment_usn()?;
        self.col.storage.commit_rust_trx()?;
        Ok(now)
    }

    async fn abort(&mut self) -> Result<()> {
        self.col.storage.rollback_rust_trx()
    }

    /// `col_path` should point to the uploaded file, and the caller is
    /// responsible for imposing limits on its size if it wishes.
    /// If `can_consume` is true, the provided file will be moved into place,
    /// or removed on failure. If false, the original will be left alone.
    async fn full_upload(
        mut self: Box<Self>,
        mut col_path: &Path,
        can_consume: bool,
    ) -> Result<()> {
        // create a copy if necessary
        let new_file: NamedTempFile;
        if !can_consume {
            new_file = NamedTempFile::new()?;
            fs::copy(col_path, &new_file.path())?;
            col_path = new_file.path();
        }

        open_and_check_sqlite_file(col_path).map_err(|check_err| {
            match fs::remove_file(col_path) {
                Ok(_) => check_err,
                Err(remove_err) => remove_err.into(),
            }
        })?;

        let target_col_path = self.col.col_path.clone();
        self.col.close(false)?;
        fs::rename(col_path, &target_col_path).map_err(Into::into)
    }

    async fn full_download(mut self: Box<Self>) -> Result<NamedTempFile> {
        // bump usn/mod & close
        self.col.transact(None, |col| col.storage.increment_usn())?;
        let col_path = self.col.col_path.clone();
        self.col.close(true)?;

        // copy file and return path
        let temp_file = NamedTempFile::new()?;
        fs::copy(&col_path, temp_file.path())?;

        Ok(temp_file)
    }
}
