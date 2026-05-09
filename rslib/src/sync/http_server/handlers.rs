// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use async_trait::async_trait;
use media::sanity::MediaSanityCheckResponse;
use media::upload::MediaUploadResponse;

use crate::prelude::*;
use crate::sync::collection::changes::server_apply_changes;
use crate::sync::collection::changes::ApplyChangesRequest;
use crate::sync::collection::changes::UnchunkedChanges;
use crate::sync::collection::chunks::server_apply_chunk;
use crate::sync::collection::chunks::server_chunk;
use crate::sync::collection::chunks::ApplyChunkRequest;
use crate::sync::collection::chunks::Chunk;
use crate::sync::collection::download::server_download;
use crate::sync::collection::finish::server_finish;
use crate::sync::collection::graves::server_apply_graves;
use crate::sync::collection::graves::ApplyGravesRequest;
use crate::sync::collection::graves::Graves;
use crate::sync::collection::meta::server_meta;
use crate::sync::collection::meta::MetaRequest;
use crate::sync::collection::meta::SyncMeta;
use crate::sync::collection::protocol::EmptyInput;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::collection::sanity::server_sanity_check;
use crate::sync::collection::sanity::SanityCheckRequest;
use crate::sync::collection::sanity::SanityCheckResponse;
use crate::sync::collection::sanity::SanityCheckStatus;
use crate::sync::collection::start::server_start;
use crate::sync::collection::start::StartRequest;
use crate::sync::collection::upload::handle_received_upload;
use crate::sync::collection::upload::UploadResponse;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::http_server::SimpleServer;
use crate::sync::login::HostKeyRequest;
use crate::sync::login::HostKeyResponse;
use crate::sync::media;
use crate::sync::media::begin::SyncBeginRequest;
use crate::sync::media::begin::SyncBeginResponse;
use crate::sync::media::changes::MediaChangesRequest;
use crate::sync::media::changes::MediaChangesResponse;
use crate::sync::media::download::DownloadFilesRequest;
use crate::sync::media::protocol::JsonResult;
use crate::sync::media::protocol::MediaSyncProtocol;
use crate::sync::request::SyncRequest;
use crate::sync::response::SyncResponse;

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
            let mut meta = user.with_col(|col| server_meta(req, col))?;
            meta.media_usn = user.media.last_usn()?;
            Ok(meta)
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
