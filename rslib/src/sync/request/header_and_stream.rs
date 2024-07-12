// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::fmt::Display;
use std::io::Cursor;
use std::io::ErrorKind;
use std::marker::PhantomData;
use std::net::IpAddr;

use axum::http::StatusCode;
use axum_extra::headers::Header;
use axum_extra::headers::HeaderName;
use axum_extra::headers::HeaderValue;
use bytes::Bytes;
use futures::Stream;
use futures::TryStreamExt;
use serde::de::DeserializeOwned;
use serde::Deserialize;
use serde::Serialize;
use tokio::io::AsyncReadExt;
use tokio_util::io::ReaderStream;

use crate::sync::error::HttpError;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::request::SyncRequest;
use crate::sync::request::MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED;
use crate::sync::version::SyncVersion;

impl<T> SyncRequest<T> {
    pub(super) async fn from_header_and_stream<S, E>(
        sync_header: SyncHeader,
        body_stream: S,
        ip: IpAddr,
    ) -> HttpResult<SyncRequest<T>>
    where
        S: Stream<Item = Result<Bytes, E>> + Unpin,
        E: Display,
        T: DeserializeOwned,
    {
        sync_header.sync_version.ensure_supported()?;
        let data = decode_zstd_body_for_server(body_stream).await?;
        Ok(Self {
            sync_key: sync_header.sync_key,
            session_key: sync_header.session_key,
            media_client_version: None,
            data,
            ip,
            json_output_type: PhantomData,
            sync_version: sync_header.sync_version,
            client_version: sync_header.client_ver,
        })
    }
}

/// Enforces max payload size
pub async fn decode_zstd_body_for_server<S, E>(data: S) -> HttpResult<Vec<u8>>
where
    S: Stream<Item = Result<Bytes, E>> + Unpin,
    E: Display,
{
    let reader = tokio_util::io::StreamReader::new(
        data.map_err(|e| std::io::Error::new(ErrorKind::ConnectionAborted, format!("{e}"))),
    );
    let reader = async_compression::tokio::bufread::ZstdDecoder::new(reader);
    let mut buf: Vec<u8> = vec![];
    reader
        .take(*MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED)
        .read_to_end(&mut buf)
        .await
        .or_bad_request("decoding zstd body")?;
    Ok(buf)
}

/// Does not enforce payload size
pub fn decode_zstd_body_stream_for_client<S, E>(data: S) -> impl Stream<Item = HttpResult<Bytes>>
where
    S: Stream<Item = Result<Bytes, E>> + Unpin,
    E: Display,
{
    let reader = tokio_util::io::StreamReader::new(
        data.map_err(|e| std::io::Error::new(ErrorKind::ConnectionAborted, format!("{e}"))),
    );
    let reader = async_compression::tokio::bufread::ZstdDecoder::new(reader);
    ReaderStream::new(reader).map_err(|err| HttpError {
        code: StatusCode::BAD_REQUEST,
        context: "decode zstd body".into(),
        source: Some(Box::new(err) as _),
    })
}

pub fn encode_zstd_body(data: Vec<u8>) -> impl Stream<Item = HttpResult<Bytes>> + Unpin {
    let enc = async_compression::tokio::bufread::ZstdEncoder::new(Cursor::new(data));
    ReaderStream::new(enc).map_err(|err| HttpError {
        code: StatusCode::INTERNAL_SERVER_ERROR,
        context: "encode zstd body".into(),
        source: Some(Box::new(err) as _),
    })
}

pub fn encode_zstd_body_stream<S, E>(data: S) -> impl Stream<Item = HttpResult<Bytes>>
where
    S: Stream<Item = Result<Bytes, E>> + Unpin,
    E: Display,
{
    let reader = tokio_util::io::StreamReader::new(
        data.map_err(|e| std::io::Error::new(ErrorKind::ConnectionAborted, format!("{e}"))),
    );
    let reader = async_compression::tokio::bufread::ZstdEncoder::new(reader);
    ReaderStream::new(reader).map_err(|err| HttpError {
        code: StatusCode::BAD_REQUEST,
        context: "encode zstd body".into(),
        source: Some(Box::new(err) as _),
    })
}

#[derive(Serialize, Deserialize)]
pub struct SyncHeader {
    #[serde(rename = "v")]
    pub sync_version: SyncVersion,
    #[serde(rename = "k")]
    pub sync_key: String,
    #[serde(rename = "c")]
    pub client_ver: String,
    #[serde(rename = "s")]
    pub session_key: String,
}

pub static SYNC_HEADER_NAME: HeaderName = HeaderName::from_static("anki-sync");

impl Header for SyncHeader {
    fn name() -> &'static HeaderName {
        &SYNC_HEADER_NAME
    }

    fn decode<'i, I>(values: &mut I) -> Result<Self, axum_extra::headers::Error>
    where
        Self: Sized,
        I: Iterator<Item = &'i HeaderValue>,
    {
        values
            .next()
            .and_then(|value| value.to_str().ok())
            .and_then(|s| serde_json::from_str(s).ok())
            .ok_or_else(axum_extra::headers::Error::invalid)
    }

    fn encode<E: Extend<HeaderValue>>(&self, _values: &mut E) {
        todo!()
    }
}
