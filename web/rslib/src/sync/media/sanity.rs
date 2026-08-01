// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize;
use serde::Serialize;

#[derive(Serialize, Deserialize)]
pub struct SanityCheckRequest {
    pub local: u32,
}

#[derive(Serialize, Deserialize, Eq, PartialEq, Debug)]
pub enum MediaSanityCheckResponse {
    #[serde(rename = "OK")]
    Ok,
    #[serde(rename = "mediaSanity")]
    SanityCheckFailed,
}
