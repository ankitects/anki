use super::{Chunk, Graves, SanityCheckCounts, UnchunkedChanges};
use crate::prelude::*;
use serde::{Deserialize, Serialize};
#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
pub enum SyncRequest {
    HostKey(HostKeyIn),
    Meta(MetaIn),
    Start(StartIn),
    ApplyGraves(ApplyGravesIn),
    ApplyChanges(ApplyChangesIn),
    Chunk,
    ApplyChunk(ApplyChunkIn),
    #[serde(rename = "sanityCheck2")]
    SanityCheck(SanityCheckIn),
    Finish,
    Abort,
}

impl SyncRequest {
    /// Return method name and payload bytes.
    pub(crate) fn to_method_and_json(&self) -> Result<(&'static str, Vec<u8>)> {
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
        })
    }
}

#[derive(Serialize, Deserialize, Debug)]
pub struct HostKeyIn {
    #[serde(rename = "u")]
    pub username: String,
    #[serde(rename = "p")]
    pub password: String,
}
#[derive(Serialize, Deserialize, Debug)]
pub struct HostKeyOut {
    pub key: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct MetaIn {
    #[serde(rename = "v")]
    pub sync_version: u8,
    #[serde(rename = "cv")]
    pub client_version: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct StartIn {
    #[serde(rename = "minUsn")]
    pub client_usn: Usn,
    #[serde(rename = "lnewer")]
    pub local_is_newer: bool,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct ApplyGravesIn {
    pub chunk: Graves,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct ApplyChangesIn {
    pub changes: UnchunkedChanges,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct ApplyChunkIn {
    pub chunk: Chunk,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct SanityCheckIn {
    pub client: SanityCheckCounts,
    pub full: bool,
}
