// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub mod header_and_stream;
mod multipart;

use std::any::Any;
use std::env;
use std::marker::PhantomData;
use std::net::IpAddr;
use std::sync::LazyLock;

use axum::body::Body;
use axum::extract::FromRequest;
use axum::extract::Multipart;
use axum::http::Request;
use axum::http::StatusCode;
use axum::RequestPartsExt;
use axum_client_ip::ClientIp;
use axum_extra::TypedHeader;
use header_and_stream::SyncHeader;
use serde::de::DeserializeOwned;
use serde::Serialize;
use serde_json::Error;
use tracing::Span;

use crate::sync::error::HttpError;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::version::SyncVersion;
use crate::version::sync_client_version_short;

/// Stores the bytes of a sync request, the associated type they
/// represent, and authentication info provided in headers/multipart
/// forms. For a SyncRequest<Foo>, you can call .json() to get a Foo
/// struct from the bytes.
#[derive(Clone)]
pub struct SyncRequest<T> {
    pub data: Vec<u8>,
    json_output_type: PhantomData<T>,
    pub sync_version: SyncVersion,
    /// empty with older clients
    pub client_version: String,
    pub ip: IpAddr,
    /// Non-empty on every non-login request.
    pub sync_key: String,
    /// May not be set on some requests by legacy clients. Used by stateful sync
    /// methods to check for concurrent access.
    pub session_key: String,
    /// Set by legacy clients when posting to msync/begin
    pub media_client_version: Option<String>,
}

impl<T> SyncRequest<T>
where
    T: DeserializeOwned,
{
    pub fn from_data(
        data: Vec<u8>,
        host_key: String,
        session_key: String,
        ip: IpAddr,
        sync_version: SyncVersion,
    ) -> SyncRequest<T> {
        SyncRequest {
            data,
            json_output_type: Default::default(),
            ip,
            sync_key: host_key,
            session_key,
            media_client_version: None,
            sync_version,
            client_version: String::new(),
        }
    }

    /// Given a generic Self<Vec<u8>>, infer the actual type based on context.
    pub fn into_output_type<O>(self) -> SyncRequest<O> {
        SyncRequest {
            data: self.data,
            json_output_type: PhantomData,
            ip: self.ip,
            sync_key: self.sync_key,
            session_key: self.session_key,
            media_client_version: self.media_client_version,
            sync_version: self.sync_version,
            client_version: self.client_version,
        }
    }

    pub fn json(&self) -> HttpResult<T> {
        serde_json::from_slice(&self.data).or_bad_request("invalid json")
    }

    pub fn skey(&self) -> HttpResult<&str> {
        if self.session_key.is_empty() {
            None.or_bad_request("missing skey")?;
        }
        Ok(&self.session_key)
    }
}

impl<S, T> FromRequest<S> for SyncRequest<T>
where
    S: Send + Sync,
    T: DeserializeOwned,
{
    type Rejection = HttpError;

    async fn from_request(req: Request<Body>, state: &S) -> Result<Self, Self::Rejection> {
        let (mut parts, body) = req.into_parts();

        let ip = parts
            .extract::<ClientIp>()
            .await
            .map_err(|_| {
                HttpError::new_without_source(StatusCode::INTERNAL_SERVER_ERROR, "missing ip")
            })?
            .0;
        Span::current().record("ip", ip.to_string());

        let sync_header: Option<TypedHeader<SyncHeader>> =
            parts.extract().await.or_bad_request("bad sync header")?;
        let req = Request::from_parts(parts, body);

        if let Some(TypedHeader(sync_header)) = sync_header {
            let stream = Body::from_request(req, state)
                .await
                .expect("infallible")
                .into_data_stream();
            SyncRequest::from_header_and_stream(sync_header, stream, ip).await
        } else {
            let multi = Multipart::from_request(req, state)
                .await
                .or_bad_request("multipart")?;
            SyncRequest::from_multipart(multi, ip).await
        }
    }
}

pub trait IntoSyncRequest {
    fn try_into_sync_request(self) -> Result<SyncRequest<Self>, serde_json::Error>
    where
        Self: Sized + 'static;
}

impl<T> IntoSyncRequest for T
where
    T: Serialize,
{
    fn try_into_sync_request(self) -> Result<SyncRequest<Self>, Error>
    where
        Self: Sized + 'static,
    {
        // A not-very-elegant workaround for the fact that a separate impl for vec<u8>
        // would conflict with this generic one.
        let is_data = (&self as &dyn Any).is::<Vec<u8>>();
        let data = if is_data {
            let boxed_self = (Box::new(self) as Box<dyn Any>)
                .downcast::<Vec<u8>>()
                .unwrap();
            *boxed_self
        } else {
            serde_json::to_vec(&self)?
        };
        Ok(SyncRequest {
            data,
            json_output_type: PhantomData,
            ip: IpAddr::from([0, 0, 0, 0]),
            media_client_version: None,
            sync_version: SyncVersion::latest(),
            client_version: sync_client_version_short().to_string(),
            // injected by client.request()
            sync_key: String::new(),
            session_key: String::new(),
        })
    }
}

pub static MAXIMUM_SYNC_PAYLOAD_BYTES: LazyLock<usize> = LazyLock::new(|| {
    env::var("MAX_SYNC_PAYLOAD_MEGS")
        .map(|v| v.parse().expect("invalid upload limit"))
        .unwrap_or(100)
        * 1024
        * 1024
});
/// Client ignores this when a non-AnkiWeb endpoint is configured. Controls the
/// maximum size of a payload after decompression, which effectively limits the
/// how large a collection file can be uploaded.
pub static MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED: LazyLock<u64> =
    LazyLock::new(|| (*MAXIMUM_SYNC_PAYLOAD_BYTES * 3) as u64);
