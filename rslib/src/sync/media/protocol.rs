// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use async_trait::async_trait;
use reqwest::Url;
use serde::de::DeserializeOwned;
use serde_derive::{Deserialize, Serialize};
use strum::IntoStaticStr;

use crate::{
    error,
    error::AnkiError,
    sync::{
        collection::protocol::AsSyncEndpoint,
        error::HttpResult,
        media::{
            begin::{SyncBeginRequest, SyncBeginResponse},
            changes::{MediaChangesRequest, MediaChangesResponse},
            download::DownloadFilesRequest,
            sanity::{MediaSanityCheckResponse, SanityCheckRequest},
            upload::MediaUploadResponse,
        },
        request::SyncRequest,
        response::SyncResponse,
    },
};

#[derive(IntoStaticStr, Deserialize, PartialEq, Eq, Debug)]
#[serde(rename_all = "camelCase")]
#[strum(serialize_all = "camelCase")]
pub enum MediaSyncMethod {
    Begin,
    MediaChanges,
    UploadChanges,
    DownloadFiles,
    MediaSanity,
}

impl AsSyncEndpoint for MediaSyncMethod {
    fn as_sync_endpoint(&self, base: &Url) -> Url {
        base.join("msync/").unwrap().join(self.into()).unwrap()
    }
}

#[async_trait]
pub trait MediaSyncProtocol: Send + Sync + 'static {
    async fn begin(
        &self,
        req: SyncRequest<SyncBeginRequest>,
    ) -> HttpResult<SyncResponse<JsonResult<SyncBeginResponse>>>;
    async fn media_changes(
        &self,
        req: SyncRequest<MediaChangesRequest>,
    ) -> HttpResult<SyncResponse<JsonResult<MediaChangesResponse>>>;
    async fn upload_changes(
        &self,
        req: SyncRequest<Vec<u8>>,
    ) -> HttpResult<SyncResponse<JsonResult<MediaUploadResponse>>>;
    async fn download_files(
        &self,
        req: SyncRequest<DownloadFilesRequest>,
    ) -> HttpResult<SyncResponse<Vec<u8>>>;
    async fn media_sanity_check(
        &self,
        req: SyncRequest<SanityCheckRequest>,
    ) -> HttpResult<SyncResponse<JsonResult<MediaSanityCheckResponse>>>;
}

/// Media endpoints wrap their returns in a JSON result, and legacy
/// clients expect it to always have an err field, even if it's empty.
#[derive(Debug, Serialize, Deserialize)]
#[serde(untagged)]
pub enum JsonResult<T> {
    Ok {
        data: T,
        #[serde(default)]
        err: String,
    },
    Err {
        err: String,
    },
}

impl<T> JsonResult<T> {
    pub fn ok(inner: T) -> Self {
        Self::Ok {
            data: inner,
            err: String::new(),
        }
    }
}

impl<T> SyncResponse<JsonResult<T>>
where
    T: DeserializeOwned,
{
    pub fn json_result(&self) -> error::Result<T> {
        match serde_json::from_slice(&self.data)? {
            JsonResult::Ok { data, .. } => Ok(data),
            JsonResult::Err { err } => Err(AnkiError::server_message(err)),
        }
    }
}
