// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::marker::PhantomData;

use ammonia::Url;
use async_trait::async_trait;
use serde_derive::{Deserialize, Serialize};
use strum::IntoStaticStr;

use crate::{
    prelude::TimestampMillis,
    sync::{
        collection::{
            changes::{ApplyChangesRequest, UnchunkedChanges},
            chunks::{ApplyChunkRequest, Chunk},
            graves::{ApplyGravesRequest, Graves},
            meta::{MetaRequest, SyncMeta},
            sanity::{SanityCheckRequest, SanityCheckResponse},
            start::StartRequest,
            upload::UploadResponse,
        },
        error::HttpResult,
        login::{HostKeyRequest, HostKeyResponse},
        request::{IntoSyncRequest, SyncRequest},
        response::SyncResponse,
    },
};

#[derive(IntoStaticStr, Deserialize, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase")]
#[strum(serialize_all = "camelCase")]
pub enum SyncMethod {
    HostKey,
    Meta,
    Start,
    ApplyGraves,
    ApplyChanges,
    Chunk,
    ApplyChunk,
    SanityCheck2,
    Finish,
    Abort,
    Upload,
    Download,
}

pub trait AsSyncEndpoint: Into<&'static str> {
    fn as_sync_endpoint(&self, base: &Url) -> Url;
}

impl AsSyncEndpoint for SyncMethod {
    fn as_sync_endpoint(&self, base: &Url) -> Url {
        base.join("sync/").unwrap().join(self.into()).unwrap()
    }
}

#[async_trait]
pub trait SyncProtocol: Send + Sync + 'static {
    async fn host_key(
        &self,
        req: SyncRequest<HostKeyRequest>,
    ) -> HttpResult<SyncResponse<HostKeyResponse>>;
    async fn meta(&self, req: SyncRequest<MetaRequest>) -> HttpResult<SyncResponse<SyncMeta>>;
    async fn start(&self, req: SyncRequest<StartRequest>) -> HttpResult<SyncResponse<Graves>>;
    async fn apply_graves(
        &self,
        req: SyncRequest<ApplyGravesRequest>,
    ) -> HttpResult<SyncResponse<()>>;
    async fn apply_changes(
        &self,
        req: SyncRequest<ApplyChangesRequest>,
    ) -> HttpResult<SyncResponse<UnchunkedChanges>>;
    async fn chunk(&self, req: SyncRequest<EmptyInput>) -> HttpResult<SyncResponse<Chunk>>;
    async fn apply_chunk(
        &self,
        req: SyncRequest<ApplyChunkRequest>,
    ) -> HttpResult<SyncResponse<()>>;
    async fn sanity_check(
        &self,
        req: SyncRequest<SanityCheckRequest>,
    ) -> HttpResult<SyncResponse<SanityCheckResponse>>;
    async fn finish(
        &self,
        req: SyncRequest<EmptyInput>,
    ) -> HttpResult<SyncResponse<TimestampMillis>>;
    async fn abort(&self, req: SyncRequest<EmptyInput>) -> HttpResult<SyncResponse<()>>;
    async fn upload(&self, req: SyncRequest<Vec<u8>>) -> HttpResult<SyncResponse<UploadResponse>>;
    async fn download(&self, req: SyncRequest<EmptyInput>) -> HttpResult<SyncResponse<Vec<u8>>>;
}

/// The sync protocol expects '{}' to be sent in requests without args.
/// Serde serializes/deserializes empty structs as 'null', so we add an empty value
/// to cause it to produce a map instead. This only applies to inputs; empty outputs
/// are returned as ()/null.
#[derive(Serialize, Deserialize, Default)]
#[serde(deny_unknown_fields)]
pub struct EmptyInput {
    #[serde(default)]
    _pad: PhantomData<()>,
}

impl EmptyInput {
    pub(crate) fn request() -> SyncRequest<Self> {
        Self::default()
            .try_into_sync_request()
            // should be infallible
            .expect("empty input into request")
    }
}
