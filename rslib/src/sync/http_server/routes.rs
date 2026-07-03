// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use axum::extract::Path;
use axum::extract::Query;
use axum::extract::State;
use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::response::Response;
use axum::routing::get;
use axum::routing::post;
use axum::Router;

use crate::sync::collection::protocol::SyncMethod;
use crate::sync::collection::protocol::SyncProtocol;
use crate::sync::error::HttpResult;
use crate::sync::error::OrHttpErr;
use crate::sync::media::begin::SyncBeginQuery;
use crate::sync::media::begin::SyncBeginRequest;
use crate::sync::media::protocol::MediaSyncMethod;
use crate::sync::media::protocol::MediaSyncProtocol;
use crate::sync::request::IntoSyncRequest;
use crate::sync::request::SyncRequest;
use crate::sync::version::SyncVersion;

macro_rules! sync_method {
    ($server:ident, $req:ident, $method:ident) => {{
        let sync_version = $req.sync_version;
        let obj = $server.$method($req.into_output_type()).await?;
        obj.make_response(sync_version)
    }};
}

async fn sync_handler<P: SyncProtocol>(
    Path(method): Path<SyncMethod>,
    State(server): State<P>,
    request: SyncRequest<Vec<u8>>,
) -> HttpResult<Response> {
    Ok(match method {
        SyncMethod::HostKey => sync_method!(server, request, host_key),
        SyncMethod::Meta => sync_method!(server, request, meta),
        SyncMethod::Start => sync_method!(server, request, start),
        SyncMethod::ApplyGraves => sync_method!(server, request, apply_graves),
        SyncMethod::ApplyChanges => sync_method!(server, request, apply_changes),
        SyncMethod::Chunk => sync_method!(server, request, chunk),
        SyncMethod::ApplyChunk => sync_method!(server, request, apply_chunk),
        SyncMethod::SanityCheck2 => sync_method!(server, request, sanity_check),
        SyncMethod::Finish => sync_method!(server, request, finish),
        SyncMethod::Abort => sync_method!(server, request, abort),
        SyncMethod::Upload => sync_method!(server, request, upload),
        SyncMethod::Download => sync_method!(server, request, download),
    })
}

pub fn collection_sync_router<P: SyncProtocol + Clone>() -> Router<P> {
    Router::new().route("/{method}", post(sync_handler::<P>))
}

/// The Rust code used to send a GET with query params, which was inconsistent
/// with the rest of our code - map the request into our standard structure.
async fn media_begin_get<P: MediaSyncProtocol>(
    Query(req): Query<SyncBeginQuery>,
    server: State<P>,
) -> HttpResult<Response> {
    let host_key = req.host_key;
    let mut req = SyncBeginRequest {
        client_version: req.client_version,
    }
    .try_into_sync_request()
    .or_bad_request("convert begin")?;
    req.sync_key = host_key;
    req.sync_version = SyncVersion::multipart();
    media_begin_post(server, req).await
}

/// Older clients would send client info in the multipart instead of the inner
/// JSON; Inject it into the json if provided.
async fn media_begin_post<P: MediaSyncProtocol>(
    server: State<P>,
    mut req: SyncRequest<SyncBeginRequest>,
) -> HttpResult<Response> {
    if let Some(ver) = &req.media_client_version {
        req.data = serde_json::to_vec(&SyncBeginRequest {
            client_version: ver.clone(),
        })
        .or_internal_err("serialize begin request")?;
    }
    media_sync_handler(Path(MediaSyncMethod::Begin), server, req.into_output_type()).await
}

pub async fn health_check_handler() -> impl IntoResponse {
    StatusCode::OK
}

async fn media_sync_handler<P: MediaSyncProtocol>(
    Path(method): Path<MediaSyncMethod>,
    State(server): State<P>,
    request: SyncRequest<Vec<u8>>,
) -> HttpResult<Response> {
    Ok(match method {
        MediaSyncMethod::Begin => sync_method!(server, request, begin),
        MediaSyncMethod::MediaChanges => sync_method!(server, request, media_changes),
        MediaSyncMethod::UploadChanges => sync_method!(server, request, upload_changes),
        MediaSyncMethod::DownloadFiles => sync_method!(server, request, download_files),
        MediaSyncMethod::MediaSanity => sync_method!(server, request, media_sanity_check),
    })
}

pub fn media_sync_router<P: MediaSyncProtocol + Clone>() -> Router<P> {
    Router::new()
        .route(
            "/begin",
            get(media_begin_get::<P>).post(media_begin_post::<P>),
        )
        .route("/{method}", post(media_sync_handler::<P>))
}
