// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use async_trait::async_trait;
use media::{sanity::MediaSanityCheckResponse, upload::MediaUploadResponse};

use crate::{
    prelude::*,
    sync::{
        collection::{
            changes::{server_apply_changes, ApplyChangesRequest, UnchunkedChanges},
            chunks::{server_apply_chunk, server_chunk, ApplyChunkRequest, Chunk},
            download::server_download,
            finish::server_finish,
            graves::{server_apply_graves, ApplyGravesRequest, Graves},
            meta::{server_meta, MetaRequest, SyncMeta},
            protocol::{EmptyInput, SyncProtocol},
            sanity::{
                server_sanity_check, SanityCheckRequest, SanityCheckResponse, SanityCheckStatus,
            },
            start::{server_start, StartRequest},
            upload::{handle_received_upload, UploadResponse},
        },
        error::{HttpResult, OrHttpErr},
        http_server::SimpleServer,
        login::{HostKeyRequest, HostKeyResponse},
        media,
        media::{
            begin::{SyncBeginRequest, SyncBeginResponse},
            changes::{MediaChangesRequest, MediaChangesResponse},
            download::DownloadFilesRequest,
            protocol::{JsonResult, MediaSyncProtocol},
        },
        request::SyncRequest,
        response::SyncResponse,
    },
};

#[async_trait]
impl SyncProtocol for Arc<SimpleServer> {
    async fn host_key(
        &self,
        req: SyncRequest<HostKeyRequest>,
    ) -> HttpResult<SyncResponse<HostKeyResponse>> {
        self.get_host_key(req.json()?)
    }

    async fn meta(&self, req: SyncRequest<MetaRequest>) -> HttpResult<SyncResponse<SyncMeta>> {
        self.with_authenticated_user(req, |user, req| {
            let req = req.json()?;
            user.with_col(|col| server_meta(req, col))
        })
        .await
        .and_then(SyncResponse::try_from_obj)
    }

    async fn start(&self, req: SyncRequest<StartRequest>) -> HttpResult<SyncResponse<Graves>> {
        self.with_authenticated_user(req, |user, req| {
            let skey = req.skey()?;
            let req = req.json()?;
            user.start_new_sync(skey)?;
            user.with_sync_state(skey, |col, state| server_start(req, col, state))
                .and_then(SyncResponse::try_from_obj)
        })
        .await
    }

    async fn apply_graves(
        &self,
        req: SyncRequest<ApplyGravesRequest>,
    ) -> HttpResult<SyncResponse<()>> {
        self.with_authenticated_user(req, |user, req| {
            let skey = req.skey()?;
            let req = req.json()?;
            user.with_sync_state(skey, |col, state| server_apply_graves(req, col, state))
                .and_then(SyncResponse::try_from_obj)
        })
        .await
    }

    async fn apply_changes(
        &self,
        req: SyncRequest<ApplyChangesRequest>,
    ) -> HttpResult<SyncResponse<UnchunkedChanges>> {
        self.with_authenticated_user(req, |user, req| {
            let skey = req.skey()?;
            let req = req.json()?;
            user.with_sync_state(skey, |col, state| server_apply_changes(req, col, state))
                .and_then(SyncResponse::try_from_obj)
        })
        .await
    }

    async fn chunk(&self, req: SyncRequest<EmptyInput>) -> HttpResult<SyncResponse<Chunk>> {
        self.with_authenticated_user(req, |user, req| {
            let skey = req.skey()?;
            let _ = req.json()?;
            user.with_sync_state(skey, server_chunk)
                .and_then(SyncResponse::try_from_obj)
        })
        .await
    }

    async fn apply_chunk(
        &self,
        req: SyncRequest<ApplyChunkRequest>,
    ) -> HttpResult<SyncResponse<()>> {
        self.with_authenticated_user(req, |user, req| {
            let skey = req.skey()?;
            let req = req.json()?;
            user.with_sync_state(skey, |col, state| server_apply_chunk(req, col, state))
                .and_then(SyncResponse::try_from_obj)
        })
        .await
    }

    async fn sanity_check(
        &self,
        req: SyncRequest<SanityCheckRequest>,
    ) -> HttpResult<SyncResponse<SanityCheckResponse>> {
        self.with_authenticated_user(req, |user, req| {
            let skey = req.skey()?;
            let req = req.json()?;
            let resp = user.with_sync_state(skey, |col, _state| server_sanity_check(req, col))?;
            if resp.status == SanityCheckStatus::Bad {
                // don't wait for an abort to roll back
                let _ = user.col.take();
            }
            SyncResponse::try_from_obj(resp)
        })
        .await
    }

    async fn finish(
        &self,
        req: SyncRequest<EmptyInput>,
    ) -> HttpResult<SyncResponse<TimestampMillis>> {
        self.with_authenticated_user(req, |user, req| {
            let _ = req.json()?;
            let now = user.with_sync_state(req.skey()?, |col, _state| server_finish(col))?;
            user.sync_state = None;
            SyncResponse::try_from_obj(now)
        })
        .await
    }

    async fn abort(&self, req: SyncRequest<EmptyInput>) -> HttpResult<SyncResponse<()>> {
        self.with_authenticated_user(req, |user, req| {
            let _ = req.json()?;
            user.abort_stateful_sync_if_active();
            SyncResponse::try_from_obj(())
        })
        .await
    }

    async fn upload(&self, req: SyncRequest<Vec<u8>>) -> HttpResult<SyncResponse<UploadResponse>> {
        self.with_authenticated_user(req, |user, req| {
            user.abort_stateful_sync_if_active();
            user.ensure_col_open()?;
            handle_received_upload(&mut user.col, req.data).map(SyncResponse::from_upload_response)
        })
        .await
    }

    async fn download(&self, req: SyncRequest<EmptyInput>) -> HttpResult<SyncResponse<Vec<u8>>> {
        self.with_authenticated_user(req, |user, req| {
            let schema_version = req.sync_version.collection_schema();
            let _ = req.json()?;
            user.abort_stateful_sync_if_active();
            user.ensure_col_open()?;
            server_download(&mut user.col, schema_version).map(SyncResponse::from_vec)
        })
        .await
    }
}

#[async_trait]
impl MediaSyncProtocol for Arc<SimpleServer> {
    async fn begin(
        &self,
        req: SyncRequest<SyncBeginRequest>,
    ) -> HttpResult<SyncResponse<JsonResult<SyncBeginResponse>>> {
        let hkey = req.sync_key.clone();
        self.with_authenticated_user(req, |user, req| {
            let req = req.json()?;
            if req.client_version.is_empty() {
                None.or_bad_request("missing client version")?;
            }
            SyncResponse::try_from_obj(JsonResult::ok(SyncBeginResponse {
                usn: user.media.last_usn()?,
                host_key: hkey,
            }))
        })
        .await
    }

    async fn media_changes(
        &self,
        req: SyncRequest<MediaChangesRequest>,
    ) -> HttpResult<SyncResponse<JsonResult<MediaChangesResponse>>> {
        self.with_authenticated_user(req, |user, req| {
            SyncResponse::try_from_obj(JsonResult::ok(
                user.media.media_changes_chunk(req.json()?.last_usn)?,
            ))
        })
        .await
    }

    async fn upload_changes(
        &self,
        req: SyncRequest<Vec<u8>>,
    ) -> HttpResult<SyncResponse<JsonResult<MediaUploadResponse>>> {
        self.with_authenticated_user(req, |user, req| {
            SyncResponse::try_from_obj(JsonResult::ok(
                user.media.process_uploaded_changes(req.data)?,
            ))
        })
        .await
    }

    async fn download_files(
        &self,
        req: SyncRequest<DownloadFilesRequest>,
    ) -> HttpResult<SyncResponse<Vec<u8>>> {
        self.with_authenticated_user(req, |user, req| {
            Ok(SyncResponse::from_vec(
                user.media.zip_files_for_download(req.json()?.files)?,
            ))
        })
        .await
    }

    async fn media_sanity_check(
        &self,
        req: SyncRequest<media::sanity::SanityCheckRequest>,
    ) -> HttpResult<SyncResponse<JsonResult<MediaSanityCheckResponse>>> {
        self.with_authenticated_user(req, |user, req| {
            SyncResponse::try_from_obj(JsonResult::ok(user.media.sanity_check(req.json()?.local)?))
        })
        .await
    }
}
