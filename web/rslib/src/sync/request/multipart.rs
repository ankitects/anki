// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::io::Read;
use std::marker::PhantomData;
use std::net::IpAddr;

use axum::extract::Multipart;
use bytes::Buf;
use bytes::Bytes;
use flate2::read::GzDecoder;
use tokio::task::spawn_blocking;

use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::request::SyncRequest;
use crate::sync::request::MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED;
use crate::sync::version::SyncVersion;
use crate::sync::version::SYNC_VERSION_10_V2_TIMEZONE;

impl<T> SyncRequest<T> {
    pub(super) async fn from_multipart(
        mut multi: Multipart,
        ip: IpAddr,
    ) -> HttpResult<SyncRequest<T>> {
        let mut host_key = String::new();
        let mut session_key = String::new();
        let mut media_client_version = None;
        let mut compressed = false;
        let mut data = None;
        while let Some(field) = multi
            .next_field()
            .await
            .or_bad_request("invalid multipart")?
        {
            match field.name() {
                Some("c") => {
                    // normal syncs should always be compressed, but media syncs may compress the
                    // zip instead
                    let c = field.text().await.or_bad_request("malformed c")?;
                    compressed = c != "0";
                }
                Some("k") | Some("sk") => {
                    host_key = field.text().await.or_bad_request("malformed (s)k")?;
                }
                Some("s") => session_key = field.text().await.or_bad_request("malformed s")?,
                Some("v") => {
                    media_client_version = Some(field.text().await.or_bad_request("malformed v")?)
                }
                Some("data") => {
                    data = Some(
                        field
                            .bytes()
                            .await
                            .or_bad_request("missing data for multi")?,
                    )
                }
                _ => {}
            }
        }
        let data = {
            let data = data.unwrap_or_default();
            if data.is_empty() {
                // AnkiDroid omits 'data' when downloading
                b"{}".to_vec()
            } else if compressed {
                decode_gzipped_data(data).await?
            } else {
                data.to_vec()
            }
        };
        Ok(Self {
            ip,
            sync_key: host_key,
            session_key,
            media_client_version,
            data,
            json_output_type: PhantomData,
            // may be lower - the old protocol didn't provide the version on every request
            sync_version: SyncVersion(SYNC_VERSION_10_V2_TIMEZONE),
            client_version: String::new(),
        })
    }
}

pub async fn decode_gzipped_data(data: Bytes) -> HttpResult<Vec<u8>> {
    // actix uses this threshold, so presumably they've measured
    if data.len() < 2049 {
        decode_gzipped_data_inner(data)
    } else {
        spawn_blocking(move || decode_gzipped_data_inner(data))
            .await
            .or_internal_err("decode gzip join")?
    }
}

fn decode_gzipped_data_inner(data: Bytes) -> HttpResult<Vec<u8>> {
    let mut gz = GzDecoder::new(data.reader()).take(*MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED);
    let mut data = Vec::new();
    gz.read_to_end(&mut data).or_bad_request("invalid gzip")?;
    Ok(data)
}
