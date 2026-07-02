// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::marker::PhantomData;

use axum::body::Body;
use axum::response::IntoResponse;
use axum::response::Response;
use axum_extra::headers::HeaderName;
use serde::de::DeserializeOwned;
use serde::Serialize;

use crate::prelude::*;
use crate::sync::collection::upload::UploadResponse;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::request::header_and_stream::encode_zstd_body;
use crate::sync::version::SyncVersion;

pub static ORIGINAL_SIZE: HeaderName = HeaderName::from_static("anki-original-size");

/// Stores the data returned from a sync request, and the type
/// it represents. Given a SyncResponse<Foo>, you can get a Foo
/// struct via .json(), except for uploads/downloads.
#[derive(Debug)]
pub struct SyncResponse<T> {
    pub data: Vec<u8>,
    json_output_type: PhantomData<T>,
}

impl<T> SyncResponse<T> {
    pub fn from_vec(data: Vec<u8>) -> SyncResponse<T> {
        SyncResponse {
            data,
            json_output_type: Default::default(),
        }
    }

    pub fn make_response(self, sync_version: SyncVersion) -> Response {
        if sync_version.is_zstd() {
            let header = (&ORIGINAL_SIZE, self.data.len().to_string());
            let body = Body::from_stream(encode_zstd_body(self.data));
            ([header], body).into_response()
        } else {
            self.data.into_response()
        }
    }
}

impl SyncResponse<UploadResponse> {
    // Unfortunately the sync protocol sends this as a bare string
    // instead of JSON.
    pub fn upload_response(&self) -> UploadResponse {
        let resp = String::from_utf8_lossy(&self.data);
        match resp.as_ref() {
            "OK" => UploadResponse::Ok,
            other => UploadResponse::Err(other.into()),
        }
    }

    pub fn from_upload_response(resp: UploadResponse) -> Self {
        let text = match resp {
            UploadResponse::Ok => "OK".into(),
            UploadResponse::Err(other) => other,
        };
        SyncResponse::from_vec(text.into_bytes())
    }
}

impl<T> SyncResponse<T>
where
    T: Serialize,
{
    pub fn try_from_obj(obj: T) -> HttpResult<SyncResponse<T>> {
        let data = serde_json::to_vec(&obj).or_internal_err("couldn't serialize object")?;
        Ok(SyncResponse::from_vec(data))
    }
}

impl<T> SyncResponse<T>
where
    T: DeserializeOwned,
{
    pub fn json(&self) -> Result<T> {
        serde_json::from_slice(&self.data).map_err(Into::into)
    }
}
