// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{path::PathBuf, sync::MutexGuard};

use tokio::runtime::Runtime;

use crate::{
    backend::{Backend, BackendState},
    error::SyncErrorKind,
    prelude::*,
    sync::{
        http::{
            ApplyChangesRequest, ApplyChunkRequest, ApplyGravesRequest, HostKeyRequest,
            HostKeyResponse, MetaRequest, SanityCheckRequest, StartRequest, SyncRequest,
        },
        Chunk, Graves, LocalServer, SanityCheckResponse, SanityCheckStatus, SyncMeta, SyncServer,
        UnchunkedChanges, SYNC_VERSION_MAX, SYNC_VERSION_MIN,
    },
};

impl Backend {
    fn with_sync_server<F, T>(&self, func: F) -> Result<T>
    where
        F: FnOnce(&mut LocalServer) -> Result<T>,
    {
        let mut state_guard = self.state.lock().unwrap();
        let out = func(
            state_guard
                .sync
                .http_sync_server
                .as_mut()
                .ok_or_else(|| AnkiError::sync_error("", SyncErrorKind::SyncNotStarted))?,
        );
        if out.is_err() {
            self.abort_and_restore_collection(Some(state_guard))
        }
        out
    }

    /// Gives out a dummy hkey - auth should be implemented at a higher layer.
    fn host_key(&self, _input: HostKeyRequest) -> Result<HostKeyResponse> {
        Ok(HostKeyResponse {
            key: "unimplemented".into(),
        })
    }

    fn meta(&self, input: MetaRequest) -> Result<SyncMeta> {
        if input.sync_version < SYNC_VERSION_MIN || input.sync_version > SYNC_VERSION_MAX {
            return Ok(SyncMeta {
                server_message: "Your Anki version is either too old, or too new.".into(),
                should_continue: false,
                ..Default::default()
            });
        }
        let server = self.col_into_server()?;
        let rt = Runtime::new().unwrap();
        let meta = rt.block_on(server.meta())?;
        self.server_into_col(server);

        Ok(meta)
    }

    /// Takes the collection from the backend, places it into a server, and returns it.
    fn col_into_server(&self) -> Result<LocalServer> {
        self.col
            .lock()
            .unwrap()
            .take()
            .map(LocalServer::new)
            .ok_or(AnkiError::CollectionNotOpen)
    }

    fn server_into_col(&self, server: LocalServer) {
        let col = server.into_col();
        let mut col_guard = self.col.lock().unwrap();
        assert!(col_guard.replace(col).is_none());
    }

    fn take_server(&self, state_guard: Option<MutexGuard<BackendState>>) -> Result<LocalServer> {
        let mut state_guard = state_guard.unwrap_or_else(|| self.state.lock().unwrap());
        state_guard
            .sync
            .http_sync_server
            .take()
            .ok_or_else(|| AnkiError::sync_error("", SyncErrorKind::SyncNotStarted))
    }

    fn start(&self, input: StartRequest) -> Result<Graves> {
        // place col into new server
        let server = self.col_into_server()?;
        let mut state_guard = self.state.lock().unwrap();
        assert!(state_guard.sync.http_sync_server.replace(server).is_none());
        drop(state_guard);

        self.with_sync_server(|server| {
            let rt = Runtime::new().unwrap();
            rt.block_on(server.start(
                input.client_usn,
                input.local_is_newer,
                input.deprecated_client_graves,
            ))
        })
    }

    fn apply_graves(&self, input: ApplyGravesRequest) -> Result<()> {
        self.with_sync_server(|server| {
            let rt = Runtime::new().unwrap();
            rt.block_on(server.apply_graves(input.chunk))
        })
    }

    fn apply_changes(&self, input: ApplyChangesRequest) -> Result<UnchunkedChanges> {
        self.with_sync_server(|server| {
            let rt = Runtime::new().unwrap();
            rt.block_on(server.apply_changes(input.changes))
        })
    }

    fn chunk(&self) -> Result<Chunk> {
        self.with_sync_server(|server| {
            let rt = Runtime::new().unwrap();
            rt.block_on(server.chunk())
        })
    }

    fn apply_chunk(&self, input: ApplyChunkRequest) -> Result<()> {
        self.with_sync_server(|server| {
            let rt = Runtime::new().unwrap();
            rt.block_on(server.apply_chunk(input.chunk))
        })
    }

    fn sanity_check(&self, input: SanityCheckRequest) -> Result<SanityCheckResponse> {
        self.with_sync_server(|server| {
            let rt = Runtime::new().unwrap();
            rt.block_on(server.sanity_check(input.client))
        })
        .map(|out| {
            if out.status != SanityCheckStatus::Ok {
                // sanity check failures are an implicit abort
                self.abort_and_restore_collection(None);
            }
            out
        })
    }

    fn finish(&self) -> Result<TimestampMillis> {
        let out = self.with_sync_server(|server| {
            let rt = Runtime::new().unwrap();
            rt.block_on(server.finish())
        });
        self.server_into_col(self.take_server(None)?);
        out
    }

    fn abort(&self) -> Result<()> {
        self.abort_and_restore_collection(None);
        Ok(())
    }

    fn abort_and_restore_collection(&self, state_guard: Option<MutexGuard<BackendState>>) {
        if let Ok(mut server) = self.take_server(state_guard) {
            let rt = Runtime::new().unwrap();
            // attempt to roll back
            if let Err(abort_err) = rt.block_on(server.abort()) {
                println!("abort failed: {:?}", abort_err);
            }
            self.server_into_col(server);
        }
    }

    /// Caller must re-open collection after this request. Provided file will be
    /// consumed.
    fn upload(&self, input: PathBuf) -> Result<()> {
        // spool input into a file
        let server = Box::new(self.col_into_server()?);
        // then process upload
        let rt = Runtime::new().unwrap();
        rt.block_on(server.full_upload(&input, true))
    }

    /// Caller must re-open collection after this request, and is responsible
    /// for cleaning up the returned file.
    fn download(&self) -> Result<Vec<u8>> {
        let server = Box::new(self.col_into_server()?);
        let rt = Runtime::new().unwrap();
        let file = rt.block_on(server.full_download(None))?;
        let path = file.into_temp_path().keep()?;
        Ok(path.to_str().expect("path was not in utf8").into())
    }

    pub(crate) fn sync_server_method_inner(&self, req: SyncRequest) -> Result<Vec<u8>> {
        use serde_json::to_vec;
        match req {
            SyncRequest::HostKey(v) => to_vec(&self.host_key(v)?),
            SyncRequest::Meta(v) => to_vec(&self.meta(v)?),
            SyncRequest::Start(v) => to_vec(&self.start(v)?),
            SyncRequest::ApplyGraves(v) => to_vec(&self.apply_graves(v)?),
            SyncRequest::ApplyChanges(v) => to_vec(&self.apply_changes(v)?),
            SyncRequest::Chunk => to_vec(&self.chunk()?),
            SyncRequest::ApplyChunk(v) => to_vec(&self.apply_chunk(v)?),
            SyncRequest::SanityCheck(v) => to_vec(&self.sanity_check(v)?),
            SyncRequest::Finish => to_vec(&self.finish()?),
            SyncRequest::Abort => to_vec(&self.abort()?),
            SyncRequest::FullUpload(v) => to_vec(&self.upload(v)?),
            SyncRequest::FullDownload => return self.download(),
        }
        .map_err(Into::into)
    }
}
