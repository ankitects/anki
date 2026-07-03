// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize;
use serde::Serialize;

use crate::prelude::*;

// The old Rust code sent the host key in a query string
#[derive(Debug, Serialize, Deserialize)]
pub struct SyncBeginQuery {
    #[serde(rename = "k")]
    pub host_key: String,
    #[serde(rename = "v")]
    pub client_version: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct SyncBeginRequest {
    /// Older clients provide this in the multipart wrapper; our router will
    /// inject the value in if necessary. The route handler should check that
    /// a value has actually been provided.
    #[serde(rename = "v", default)]
    pub client_version: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct SyncBeginResponse {
    pub usn: Usn,
    /// The server used to send back a session key used for following requests,
    /// but this is no longer required. To avoid breaking older clients, the
    /// host key is returned in its place.
    #[serde(rename = "sk")]
    pub host_key: String,
}
