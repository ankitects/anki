// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{fs, path::PathBuf};

use serde::{Deserialize, Serialize};

use super::{Chunk, Graves, SanityCheckCounts, UnchunkedChanges};
use crate::{backend_proto::sync_server_method_request::Method, prelude::*};
#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
pub enum SyncRequest {
    HostKey(HostKeyRequest),
    Meta(MetaRequest),
    Start(StartRequest),
    ApplyGraves(ApplyGravesRequest),
    ApplyChanges(ApplyChangesRequest),
    Chunk,
    ApplyChunk(ApplyChunkRequest),
    #[serde(rename = "sanityCheck2")]
    SanityCheck(SanityCheckRequest),
    Finish,
    Abort,
    #[serde(rename = "upload")]
    FullUpload(PathBuf),
    #[serde(rename = "download")]
    FullDownload,
}

impl SyncRequest {
    /// Return method name and payload bytes.
    pub(crate) fn into_method_and_data(self) -> Result<(&'static str, Vec<u8>)> {
        use serde_json::to_vec;
        Ok(match self {
            SyncRequest::HostKey(v) => ("hostKey", to_vec(&v)?),
            SyncRequest::Meta(v) => ("meta", to_vec(&v)?),
            SyncRequest::Start(v) => ("start", to_vec(&v)?),
            SyncRequest::ApplyGraves(v) => ("applyGraves", to_vec(&v)?),
            SyncRequest::ApplyChanges(v) => ("applyChanges", to_vec(&v)?),
            SyncRequest::Chunk => ("chunk", b"{}".to_vec()),
            SyncRequest::ApplyChunk(v) => ("applyChunk", to_vec(&v)?),
            SyncRequest::SanityCheck(v) => ("sanityCheck2", to_vec(&v)?),
            SyncRequest::Finish => ("finish", b"{}".to_vec()),
            SyncRequest::Abort => ("abort", b"{}".to_vec()),
            SyncRequest::FullUpload(v) => {
                // fixme: stream in the data instead, in a different call
                ("upload", fs::read(&v)?)
            }
            SyncRequest::FullDownload => ("download", b"{}".to_vec()),
        })
    }

    pub(crate) fn from_method_and_data(method: Method, data: Vec<u8>) -> Result<Self> {
        use serde_json::from_slice;
        Ok(match method {
            Method::HostKey => SyncRequest::HostKey(from_slice(&data)?),
            Method::Meta => SyncRequest::Meta(from_slice(&data)?),
            Method::Start => SyncRequest::Start(from_slice(&data)?),
            Method::ApplyGraves => SyncRequest::ApplyGraves(from_slice(&data)?),
            Method::ApplyChanges => SyncRequest::ApplyChanges(from_slice(&data)?),
            Method::Chunk => SyncRequest::Chunk,
            Method::ApplyChunk => SyncRequest::ApplyChunk(from_slice(&data)?),
            Method::SanityCheck => SyncRequest::SanityCheck(from_slice(&data)?),
            Method::Finish => SyncRequest::Finish,
            Method::Abort => SyncRequest::Abort,
            Method::FullUpload => {
                let path = PathBuf::from(String::from_utf8(data).expect("path was not in utf8"));
                SyncRequest::FullUpload(path)
            }
            Method::FullDownload => SyncRequest::FullDownload,
        })
    }
}

#[derive(Serialize, Deserialize, Debug)]
pub struct HostKeyRequest {
    #[serde(rename = "u")]
    pub username: String,
    #[serde(rename = "p")]
    pub password: String,
}
#[derive(Serialize, Deserialize, Debug)]
pub struct HostKeyResponse {
    pub key: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct MetaRequest {
    #[serde(rename = "v")]
    pub sync_version: u8,
    #[serde(rename = "cv")]
    pub client_version: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct StartRequest {
    #[serde(rename = "minUsn")]
    pub client_usn: Usn,
    #[serde(rename = "lnewer")]
    pub local_is_newer: bool,
    /// Unfortunately AnkiDroid is still using this
    #[serde(rename = "graves", default)]
    pub deprecated_client_graves: Option<Graves>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct ApplyGravesRequest {
    pub chunk: Graves,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct ApplyChangesRequest {
    pub changes: UnchunkedChanges,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct ApplyChunkRequest {
    pub chunk: Chunk,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct SanityCheckRequest {
    pub client: SanityCheckCounts,
}
