// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    fmt::Display,
    io::{Cursor, ErrorKind},
    marker::PhantomData,
    net::IpAddr,
};

use axum::{
    extract::BodyStream,
    headers::{Header, HeaderName, HeaderValue},
    http::StatusCode,
};
use bytes::Bytes;
use futures::{Stream, TryStreamExt};
use serde::de::DeserializeOwned;
use serde_derive::{Deserialize, Serialize};
use tokio::io::AsyncReadExt;
use tokio_util::io::ReaderStream;

use crate::sync::{
    error::{HttpResult, HttpSnafu, OrHttpErr},
    request::{SyncRequest, MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED},
    version::SyncVersion,
};

impl<T> SyncRequest<T> {
    pub(super) async fn from_header_and_stream(
        sync_header: SyncHeader,
        body_stream: BodyStream,
        ip: IpAddr,
    ) -> HttpResult<SyncRequest<T>>
    where
        T: DeserializeOwned,
    {
        sync_header.sync_version.ensure_supported()?;
        let data = decode_zstd_body(body_stream).await?;
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

pub async fn decode_zstd_body<S, E>(data: S) -> HttpResult<Vec<u8>>
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

pub fn decode_zstd_body_stream<S, E>(data: S) -> impl Stream<Item = HttpResult<Bytes>>
where
    S: Stream<Item = Result<Bytes, E>> + Unpin,
    E: Display,
{
    let reader = tokio_util::io::StreamReader::new(
        data.map_err(|e| std::io::Error::new(ErrorKind::ConnectionAborted, format!("{e}"))),
    );
    let reader = async_compression::tokio::bufread::ZstdDecoder::new(reader);
    ReaderStream::new(reader.take(*MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED)).map_err(|err| {
        HttpSnafu {
            code: StatusCode::BAD_REQUEST,
            context: "decode zstd body",
            source: Some(Box::new(err) as _),
        }
        .build()
    })
}

pub fn encode_zstd_body(data: Vec<u8>) -> impl Stream<Item = HttpResult<Bytes>> + Unpin {
    let enc = async_compression::tokio::bufread::ZstdEncoder::new(Cursor::new(data));
    ReaderStream::new(enc).map_err(|err| {
        HttpSnafu {
            code: StatusCode::INTERNAL_SERVER_ERROR,
            context: "encode zstd body",
            source: Some(Box::new(err) as _),
        }
        .build()
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
    ReaderStream::new(reader).map_err(|err| {
        HttpSnafu {
            code: StatusCode::BAD_REQUEST,
            context: "encode zstd body",
            source: Some(Box::new(err) as _),
        }
        .build()
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

    fn decode<'i, I>(values: &mut I) -> Result<Self, axum::headers::Error>
    where
        Self: Sized,
        I: Iterator<Item = &'i HeaderValue>,
    {
        values
            .next()
            .and_then(|value| value.to_str().ok())
            .and_then(|s| serde_json::from_str(s).ok())
            .ok_or_else(axum::headers::Error::invalid)
    }

    fn encode<E: Extend<HeaderValue>>(&self, _values: &mut E) {
        todo!()
    }
}
