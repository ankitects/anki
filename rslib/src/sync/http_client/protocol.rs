// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use async_trait::async_trait;

use crate::prelude::TimestampMillis;
use crate::progress::ThrottlingProgressHandler;
use crate::sync::collection::changes::ApplyChangesRequest;
use crate::sync::collection::changes::UnchunkedChanges;
use crate::sync::collection::chunks::ApplyChunkRequest;
use crate::sync::collection::chunks::Chunk;
use crate::sync::collection::graves::ApplyGravesRequest;
use crate::sync::collection::graves::Graves;
use crate::sync::collection::meta::MetaRequest;
use crate::sync::collection::meta::SyncMeta;
use crate::sync::collection::protocol::EmptyInput;
use crate::sync::collection::protocol::SyncMethod;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::collection::sanity::SanityCheckRequest;
use crate::sync::collection::sanity::SanityCheckResponse;
use crate::sync::collection::start::StartRequest;
use crate::sync::collection::upload::UploadResponse;
use crate::sync::error::HttpResult;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::login::HostKeyRequest;
use crate::sync::login::HostKeyResponse;
use crate::sync::media::begin::SyncBeginRequest;
use crate::sync::media::begin::SyncBeginResponse;
use crate::sync::media::changes::MediaChangesRequest;
use crate::sync::media::changes::MediaChangesResponse;
use crate::sync::media::download::DownloadFilesRequest;
use crate::sync::media::protocol::JsonResult;
use crate::sync::media::protocol::MediaSyncMethod;
use crate::sync::media::protocol::MediaSyncProtocol;
use crate::sync::media::sanity;
use crate::sync::media::upload;
use crate::sync::request::SyncRequest;
use crate::sync::response::SyncResponse;

#[async_trait]
impl SyncProtocol for HttpSyncClient {
    async fn host_key(
        &self,
        req: SyncRequest<HostKeyRequest>,
    ) -> HttpResult<SyncResponse<HostKeyResponse>> {
        self.request(SyncMethod::HostKey, req).await
    }

    async fn meta(&self, req: SyncRequest<MetaRequest>) -> HttpResult<SyncResponse<SyncMeta>> {
        self.request(SyncMethod::Meta, req).await
    }

    async fn start(&self, req: SyncRequest<StartRequest>) -> HttpResult<SyncResponse<Graves>> {
        self.request(SyncMethod::Start, req).await
    }

    async fn apply_graves(
        &self,
        req: SyncRequest<ApplyGravesRequest>,
    ) -> HttpResult<SyncResponse<()>> {
        self.request(SyncMethod::ApplyGraves, req).await
    }

    async fn apply_changes(
        &self,
        req: SyncRequest<ApplyChangesRequest>,
    ) -> HttpResult<SyncResponse<UnchunkedChanges>> {
        self.request(SyncMethod::ApplyChanges, req).await
    }

    async fn chunk(&self, req: SyncRequest<EmptyInput>) -> HttpResult<SyncResponse<Chunk>> {
        self.request(SyncMethod::Chunk, req).await
    }

    async fn apply_chunk(
        &self,
        req: SyncRequest<ApplyChunkRequest>,
    ) -> HttpResult<SyncResponse<()>> {
        self.request(SyncMethod::ApplyChunk, req).await
    }

    async fn sanity_check(
        &self,
        req: SyncRequest<SanityCheckRequest>,
    ) -> HttpResult<SyncResponse<SanityCheckResponse>> {
        self.request(SyncMethod::SanityCheck2, req).await
    }

    async fn finish(
        &self,
        req: SyncRequest<EmptyInput>,
    ) -> HttpResult<SyncResponse<TimestampMillis>> {
        self.request(SyncMethod::Finish, req).await
    }

    async fn abort(&self, req: SyncRequest<EmptyInput>) -> HttpResult<SyncResponse<()>> {
        self.request(SyncMethod::Abort, req).await
    }

    async fn upload(&self, req: SyncRequest<Vec<u8>>) -> HttpResult<SyncResponse<UploadResponse>> {
        self.upload_with_progress(req, ThrottlingProgressHandler::default())
            .await
    }

    async fn download(&self, req: SyncRequest<EmptyInput>) -> HttpResult<SyncResponse<Vec<u8>>> {
        self.download_with_progress(req, ThrottlingProgressHandler::default())
            .await
    }
}

#[async_trait]
impl MediaSyncProtocol for HttpSyncClient {
    async fn begin(
        &self,
        req: SyncRequest<SyncBeginRequest>,
    ) -> HttpResult<SyncResponse<JsonResult<SyncBeginResponse>>> {
        self.request(MediaSyncMethod::Begin, req).await
    }

    async fn media_changes(
        &self,
        req: SyncRequest<MediaChangesRequest>,
    ) -> HttpResult<SyncResponse<JsonResult<MediaChangesResponse>>> {
        self.request(MediaSyncMethod::MediaChanges, req).await
    }

    async fn upload_changes(
        &self,
        req: SyncRequest<Vec<u8>>,
    ) -> HttpResult<SyncResponse<JsonResult<upload::MediaUploadResponse>>> {
        self.request(MediaSyncMethod::UploadChanges, req).await
    }

    async fn download_files(
        &self,
        req: SyncRequest<DownloadFilesRequest>,
    ) -> HttpResult<SyncResponse<Vec<u8>>> {
        self.request(MediaSyncMethod::DownloadFiles, req).await
    }

    async fn media_sanity_check(
        &self,
        req: SyncRequest<sanity::SanityCheckRequest>,
    ) -> HttpResult<SyncResponse<JsonResult<sanity::MediaSanityCheckResponse>>> {
        self.request(MediaSyncMethod::MediaSanity, req).await
    }
}
