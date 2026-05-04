// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::marker::PhantomData;

use axum::extract::rejection::BytesRejection;
use axum::extract::FromRequest;
use axum::http::header;
use axum::http::HeaderValue;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::response::Response;
use bytes::Bytes;
use prost::Message;
use serde::de::DeserializeOwned;
use serde::Serialize;

#[derive(Debug, Clone, Copy)]
pub enum ApiContentType {
    Json,
    Proto,
}

const fn header_value_from_content_type(content_type: ApiContentType) -> HeaderValue {
    HeaderValue::from_static(match content_type {
        ApiContentType::Json => "application/json",
        ApiContentType::Proto => "application/protobuf",
    })
}

impl From<ApiContentType> for HeaderValue {
    fn from(value: ApiContentType) -> Self {
        header_value_from_content_type(value)
    }
}

// Credit: Content negotiation logic is adapted from the
// axum_content_negotiation crate

static DEFAULT_CONTENT_TYPE: HeaderValue = header_value_from_content_type(ApiContentType::Json);

trait SupportedEncodingExt {
    fn supported_encoding(&self) -> Option<ApiContentType>;
}

impl SupportedEncodingExt for &[u8] {
    fn supported_encoding(&self) -> Option<ApiContentType> {
        match *self {
            b"application/json" => Some(ApiContentType::Json),
            b"application/protobuf" => Some(ApiContentType::Proto),
            b"*/*" => Some(ApiContentType::Json),
            _ => None,
        }
    }
}

trait AcceptExt {
    fn negotiate(&self) -> Option<ApiContentType>;
}

impl AcceptExt for axum::http::HeaderMap {
    fn negotiate(&self) -> Option<ApiContentType> {
        let accept = self.get(header::ACCEPT).unwrap_or(&DEFAULT_CONTENT_TYPE);
        let precise_mime = accept.as_bytes().supported_encoding();
        // Avoid iterations and splits if it's an exact match
        if precise_mime.is_some() {
            return precise_mime;
        }

        accept
            .to_str()
            .ok()?
            .split(',')
            .map(str::trim)
            .filter_map(|s| {
                let mut segments = s.split(';').map(str::trim);
                let mime = segments.next().unwrap_or(s);

                // See if it's a type we support
                let mime_type = mime.as_bytes().supported_encoding()?;

                // If we support it, parse or default the q value
                let q = segments
                    .find_map(|s| {
                        let value = s.strip_prefix("q=")?;
                        Some(value.parse::<f32>().unwrap_or(0.0))
                    })
                    .unwrap_or(1.0);
                Some((mime_type, q))
            })
            .min_by(|(_, a), (_, b)| b.total_cmp(a))
            .map(|(mime, _)| mime)
    }
}

pub enum ApiRejection {
    ContentType,
    Bytes(BytesRejection),
    ProstDecode(prost::DecodeError),
    SerdeDecode(serde_json::Error),
}

impl IntoResponse for ApiRejection {
    fn into_response(self) -> Response {
        let body = match self {
            ApiRejection::ContentType => (
                StatusCode::NOT_ACCEPTABLE,
                "Invalid content type".to_string(),
            ),
            ApiRejection::Bytes(rejection) => (rejection.status(), rejection.body_text()),
            ApiRejection::ProstDecode(err) => (StatusCode::INTERNAL_SERVER_ERROR, err.to_string()),
            ApiRejection::SerdeDecode(err) => (StatusCode::INTERNAL_SERVER_ERROR, err.to_string()),
        };
        body.into_response()
    }
}

impl From<axum::extract::rejection::BytesRejection> for ApiRejection {
    fn from(value: axum::extract::rejection::BytesRejection) -> Self {
        Self::Bytes(value)
    }
}

impl From<prost::DecodeError> for ApiRejection {
    fn from(value: prost::DecodeError) -> Self {
        Self::ProstDecode(value)
    }
}

impl From<serde_json::Error> for ApiRejection {
    fn from(value: serde_json::Error) -> Self {
        Self::SerdeDecode(value)
    }
}

pub struct ApiRequest<T> {
    bytes: Bytes,
    pub content_type: ApiContentType,
    _phantom: PhantomData<T>,
}

impl<T> ApiRequest<T>
where
    T: Default + Clone + Message + DeserializeOwned,
{
    pub fn data(self) -> Result<(ApiContentType, T), ApiRejection> {
        Ok((
            self.content_type,
            match self.content_type {
                ApiContentType::Json => serde_json::from_slice(&self.bytes)?,
                ApiContentType::Proto => T::decode(self.bytes)?,
            },
        ))
    }
}

impl<T, S> FromRequest<S> for ApiRequest<T>
where
    S: Send + Sync,
{
    type Rejection = ApiRejection;

    async fn from_request(req: axum::extract::Request, state: &S) -> Result<Self, Self::Rejection> {
        let accept = req.headers().negotiate();
        let Some(content_type) = accept else {
            return Err(ApiRejection::ContentType);
        };
        let bytes = Bytes::from_request(req, state).await?;

        Ok(Self {
            bytes,
            content_type,
            _phantom: PhantomData,
        })
    }
}

pub struct ApiResponse<T> {
    bytes: Bytes,
    content_type: ApiContentType,
    _phantom: PhantomData<T>,
}

impl<T> ApiResponse<T> {
    pub fn new(bytes: Bytes, content_type: ApiContentType) -> Self {
        Self {
            bytes,
            content_type,
            _phantom: PhantomData,
        }
    }
}

impl<T> IntoResponse for ApiResponse<T>
where
    T: Default + Send + Sync + Message + Serialize,
{
    fn into_response(self) -> Response {
        let mut bytes = self.bytes;
        if matches!(self.content_type, ApiContentType::Json) {
            match T::decode(bytes).map_err(ApiRejection::from) {
                Ok(data) => match serde_json::to_vec(&data).map_err(ApiRejection::from) {
                    Ok(v) => {
                        bytes = Bytes::copy_from_slice(v.as_slice());
                    }
                    Err(e) => return e.into_response(),
                },
                Err(e) => return e.into_response(),
            }
        }
        (
            axum::http::StatusCode::OK,
            [(header::CONTENT_TYPE, HeaderValue::from(self.content_type))],
            bytes,
        )
            .into_response()
    }
}
