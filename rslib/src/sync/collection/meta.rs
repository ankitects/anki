// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use ammonia::Url;
use anki_io::metadata;
use axum::http::StatusCode;
use serde::Deserialize;
use serde::Serialize;
use tracing::debug;
use tracing::info;

use crate::config::SchedulerVersion;
use crate::prelude::*;
use crate::sync::collection::normal::ClientSyncState;
use crate::sync::collection::normal::SyncActionRequired;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::error::HttpError;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::request::IntoSyncRequest;
use crate::sync::request::SyncRequest;
use crate::sync::request::MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED;
use crate::sync::version::SYNC_VERSION_09_V2_SCHEDULER;
use crate::sync::version::SYNC_VERSION_10_V2_TIMEZONE;
use crate::sync::version::SYNC_VERSION_MAX;
use crate::sync::version::SYNC_VERSION_MIN;
use crate::version::sync_client_version;

#[derive(Serialize, Deserialize, Debug, Default)]
pub struct SyncMeta {
    #[serde(rename = "mod")]
    pub modified: TimestampMillis,
    #[serde(rename = "scm")]
    pub schema: TimestampMillis,
    pub usn: Usn,
    #[serde(rename = "ts")]
    pub current_time: TimestampSecs,
    #[serde(rename = "msg")]
    pub server_message: String,
    #[serde(rename = "cont")]
    pub should_continue: bool,
    /// Used by clients prior to sync version 11
    #[serde(rename = "hostNum")]
    pub host_number: u32,
    #[serde(default)]
    pub empty: bool,
    /// This field is not set by col.sync_meta(), and must be filled in
    /// separately.
    pub media_usn: Usn,
    #[serde(skip)]
    pub v2_scheduler_or_later: bool,
    #[serde(skip)]
    pub v2_timezone: bool,
    #[serde(skip)]
    pub collection_bytes: u64,
}

impl SyncMeta {
    pub(in crate::sync) fn compared_to_remote(
        &self,
        remote: SyncMeta,
        new_endpoint: Option<String>,
    ) -> ClientSyncState {
        let local = self;
        let required = if remote.modified == local.modified {
            SyncActionRequired::NoChanges
        } else if remote.schema != local.schema {
            let upload_ok = !local.empty || remote.empty;
            let download_ok = !remote.empty || local.empty;
            SyncActionRequired::FullSyncRequired {
                upload_ok,
                download_ok,
            }
        } else {
            SyncActionRequired::NormalSyncRequired
        };

        ClientSyncState {
            required,
            local_is_newer: local.modified > remote.modified,
            usn_at_last_sync: local.usn,
            server_usn: remote.usn,
            pending_usn: Usn(-1),
            server_message: remote.server_message,
            host_number: remote.host_number,
            new_endpoint,
            server_media_usn: remote.media_usn,
        }
    }
}

impl HttpSyncClient {
    /// Fetch server meta. Returns a new endpoint if one was provided.
    pub(in crate::sync) async fn meta_with_redirect(
        &mut self,
    ) -> Result<(SyncMeta, Option<String>)> {
        let mut new_endpoint = None;
        let response = match self.meta(MetaRequest::request()).await {
            Ok(remote) => remote,
            Err(HttpError {
                code: StatusCode::PERMANENT_REDIRECT,
                context,
                ..
            }) => {
                debug!(endpoint = context, "redirect to new location");
                let url = Url::try_from(context.as_str())
                    .or_bad_request("couldn't parse new location")?;
                new_endpoint = Some(context);
                self.endpoint = url;
                self.meta(MetaRequest::request()).await?
            }
            err => err?,
        };
        let remote = response.json()?;
        Ok((remote, new_endpoint))
    }
}

#[derive(Serialize, Deserialize, Debug)]
pub struct MetaRequest {
    #[serde(rename = "v")]
    pub sync_version: u8,
    #[serde(rename = "cv")]
    pub client_version: String,
}

impl Collection {
    pub fn sync_meta(&self) -> Result<SyncMeta> {
        let stamps = self.storage.get_collection_timestamps()?;
        let collection_bytes = metadata(&self.col_path)?.len();
        Ok(SyncMeta {
            modified: stamps.collection_change,
            schema: stamps.schema_change,
            // server=true is used for the client case as well, as we
            // want the actual usn and not -1
            usn: self.storage.usn(true)?,
            current_time: TimestampSecs::now(),
            server_message: "".into(),
            should_continue: true,
            host_number: 0,
            empty: !self.storage.have_at_least_one_card()?,
            v2_scheduler_or_later: self.scheduler_version() == SchedulerVersion::V2,
            v2_timezone: self.get_creation_utc_offset().is_some(),
            collection_bytes,
            // must be filled in by calling code
            media_usn: Usn(0),
        })
    }
}

pub fn server_meta(req: MetaRequest, col: &mut Collection) -> HttpResult<SyncMeta> {
    if !matches!(req.sync_version, SYNC_VERSION_MIN..=SYNC_VERSION_MAX) {
        return Err(HttpError {
            // old clients expected this code
            code: StatusCode::NOT_IMPLEMENTED,
            context: "unsupported version".into(),
            source: None,
        });
    }
    let mut meta = col.sync_meta().or_internal_err("sync meta")?;
    if meta.collection_bytes > *MAXIMUM_SYNC_PAYLOAD_BYTES_UNCOMPRESSED {
        info!("collection is too large, forcing one-way sync");
        meta.schema = TimestampMillis::now();
    }
    if meta.v2_scheduler_or_later && req.sync_version < SYNC_VERSION_09_V2_SCHEDULER {
        meta.server_message = "Your client does not support the v2 scheduler".into();
        meta.should_continue = false;
    } else if meta.v2_timezone && req.sync_version < SYNC_VERSION_10_V2_TIMEZONE {
        meta.server_message = "Your client does not support the new timezone handling.".into();
        meta.should_continue = false;
    }
    Ok(meta)
}

impl MetaRequest {
    pub fn request() -> SyncRequest<Self> {
        MetaRequest {
            sync_version: SYNC_VERSION_MAX,
            client_version: sync_client_version().into(),
        }
        .try_into_sync_request()
        .expect("infallible meta request")
    }
}
