// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#![cfg(test)]

use std::fs;
use std::net::IpAddr;
use std::thread::sleep;
use std::time::Duration;

use nom::AsBytes;
use reqwest::multipart;
use reqwest::Client;

use crate::error::Result;
use crate::media::MediaManager;
use crate::prelude::AnkiError;
use crate::progress::ThrottlingProgressHandler;
use crate::sync::collection::protocol::AsSyncEndpoint;
use crate::sync::collection::tests::with_active_server;
use crate::sync::collection::tests::SyncTestContext;
use crate::sync::media::begin::SyncBeginQuery;
use crate::sync::media::begin::SyncBeginRequest;
use crate::sync::media::progress::MediaSyncProgress;
use crate::sync::media::protocol::MediaSyncMethod;
use crate::sync::media::protocol::MediaSyncProtocol;
use crate::sync::media::sanity::MediaSanityCheckResponse;
use crate::sync::media::sanity::SanityCheckRequest;
use crate::sync::media::syncer::MediaSyncer;
use crate::sync::media::zip::zip_files_for_upload;
use crate::sync::request::IntoSyncRequest;
use crate::sync::request::SyncRequest;
use crate::sync::version::SyncVersion;
use crate::version::sync_client_version;

/// Older Rust versions sent hkey/version in GET query string.
#[tokio::test]
async fn begin_supports_get() -> Result<()> {
    with_active_server(|client_| async move {
        let url = client_.endpoint().join("msync/begin").unwrap();
        let client = Client::new();
        client
            .get(url)
            .query(&SyncBeginQuery {
                host_key: client_.sync_key.clone(),
                client_version: sync_client_version().into(),
            })
            .send()
            .await?
            .error_for_status()?;
        Ok(())
    })
    .await
}

/// Older clients used a `v` variable in the begin multipart instead of placing
/// the version in the JSON payload.
#[tokio::test]
async fn begin_supports_version_in_form() -> Result<()> {
    with_active_server(|client_| async move {
        let url = MediaSyncMethod::Begin.as_sync_endpoint(client_.endpoint());
        let client = Client::new();

        let form = multipart::Form::new()
            .text("c", "0")
            .text("v", "client")
            .text("k", client_.sync_key.clone());
        client
            .post(url)
            .multipart(form)
            .send()
            .await?
            .error_for_status()?;
        Ok(())
    })
    .await
}

/// Older clients sent key in `sk` multipart variable for non-begin requests.
#[tokio::test]
async fn legacy_session_key_works() -> Result<()> {
    with_active_server(|client_| async move {
        let url = MediaSyncMethod::MediaChanges.as_sync_endpoint(client_.endpoint());
        let client = Client::new();

        let form = multipart::Form::new()
            .text("c", "0")
            .text("v", "client")
            .text("sk", client_.sync_key.clone())
            .part(
                "data",
                multipart::Part::bytes(b"{\"lastUsn\": 0}".as_bytes()),
            );
        client
            .post(url)
            .multipart(form)
            .send()
            .await?
            .error_for_status()?;
        Ok(())
    })
    .await
}

#[tokio::test]
async fn sanity_check() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client.clone());
        let media1 = ctx.media1();
        ctx.sync_media1().await?;
        // may be non-zero when testing on external endpoint
        let starting_file_count = fs::read_dir(&media1.media_folder).unwrap().count() as u32;
        let resp = client
            .media_sanity_check(
                SanityCheckRequest {
                    local: starting_file_count,
                }
                .try_into_sync_request()?,
            )
            .await?
            .json_result()?;
        assert_eq!(resp, MediaSanityCheckResponse::Ok);
        let resp = client
            .media_sanity_check(
                SanityCheckRequest {
                    local: starting_file_count + 1,
                }
                .try_into_sync_request()?,
            )
            .await?
            .json_result()?;
        assert_eq!(resp, MediaSanityCheckResponse::SanityCheckFailed);
        Ok(())
    })
    .await
}

fn ignore_progress() -> ThrottlingProgressHandler<MediaSyncProgress> {
    ThrottlingProgressHandler::new(Default::default())
}

impl SyncTestContext {
    fn media1(&self) -> MediaManager {
        self.col1().media().unwrap()
    }

    fn media2(&self) -> MediaManager {
        self.col2().media().unwrap()
    }

    async fn sync_media1(&self) -> Result<()> {
        let mut syncer =
            MediaSyncer::new(self.media1(), ignore_progress(), self.client.clone()).unwrap();
        syncer.sync(None).await
    }

    async fn sync_media2(&self) -> Result<()> {
        let mut syncer =
            MediaSyncer::new(self.media2(), ignore_progress(), self.client.clone()).unwrap();
        syncer.sync(None).await
    }

    /// As local change detection depends on a millisecond timestamp,
    /// we need to wait a little while between steps to ensure changes are
    /// observed. Theoretically 1ms should suffice, but I was seeing flaky
    /// tests on a ZFS system with the delay set to a few milliseconds.
    fn sleep(&self) {
        sleep(Duration::from_millis(10))
    }
}

#[tokio::test]
async fn media_roundtrip() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client.clone());
        let media1 = ctx.media1();
        let media2 = ctx.media2();
        ctx.sync_media1().await?;
        ctx.sync_media2().await?;
        ctx.sleep();
        // may be non-zero when testing on external endpoint
        let starting_file_count = fs::read_dir(&media1.media_folder).unwrap().count();
        // add some files
        fs::write(media1.media_folder.join("manual1"), "manual1").unwrap();
        media1.add_file("auto1", b"auto1").unwrap();
        fs::write(media1.media_folder.join("manual2"), "manual2").unwrap();
        // sync to server and then other client
        ctx.sync_media1().await?;
        ctx.sync_media2().await?;
        // modify a file and remove the other
        ctx.sleep();
        fs::write(media2.media_folder.join("manual1"), "changed1").unwrap();
        fs::remove_file(media2.media_folder.join("manual2")).unwrap();
        ctx.sync_media2().await?;
        ctx.sync_media1().await?;
        assert_eq!(
            fs::read_to_string(media1.media_folder.join("manual1")).unwrap(),
            "changed1"
        );
        // remove remaining files
        ctx.sleep();
        fs::remove_file(media1.media_folder.join("manual1")).unwrap();
        fs::remove_file(media2.media_folder.join("auto1")).unwrap();
        ctx.sync_media1().await?;
        ctx.sync_media2().await?;
        ctx.sync_media1().await?;
        assert_eq!(
            fs::read_dir(media1.media_folder).unwrap().count(),
            starting_file_count
        );
        assert_eq!(
            fs::read_dir(media2.media_folder).unwrap().count(),
            starting_file_count
        );
        Ok(())
    })
    .await
}

#[tokio::test]
async fn parallel_requests() -> Result<()> {
    with_active_server(|client| async move {
        let ctx = SyncTestContext::new(client.clone());
        let media1 = ctx.media1();
        let media2 = ctx.media2();
        ctx.sleep();
        // multiple clients should be able to add the same file
        media1.add_file("auto", b"auto").unwrap();
        media2.add_file("auto", b"auto").unwrap();
        ctx.sync_media1().await?;
        // Normally the second client would notice the addition of the file when
        // fetching changes from the server; here we manually upload the change to
        // simulate two parallel syncs going on.
        let get_usn = || async {
            Ok::<_, AnkiError>(
                ctx.client
                    .begin(
                        SyncBeginRequest {
                            client_version: "x".into(),
                        }
                        .try_into_sync_request()?,
                    )
                    .await?
                    .json_result()?
                    .usn,
            )
        };
        let start_usn = get_usn().await?;
        let zip_data = zip_files_for_upload(vec![("auto".into(), Some(b"auto".to_vec()))])?;
        client
            .upload_changes(SyncRequest::from_data(
                zip_data,
                ctx.client.sync_key.clone(),
                String::new(),
                IpAddr::from([0, 0, 0, 0]),
                SyncVersion::latest(),
            ))
            .await?;
        let end_usn = get_usn().await?;
        assert_eq!(start_usn, end_usn);
        // Parallel deletions should work too
        media1.remove_files(&["auto"])?;
        media2.remove_files(&["auto"])?;
        ctx.sync_media1().await?;
        let start_usn = get_usn().await?;
        let zip_data = zip_files_for_upload(vec![("auto".into(), None)])?;
        client
            .upload_changes(SyncRequest::from_data(
                zip_data,
                ctx.client.sync_key.clone(),
                String::new(),
                IpAddr::from([0, 0, 0, 0]),
                SyncVersion::latest(),
            ))
            .await?;
        let end_usn = get_usn().await?;
        assert_eq!(start_usn, end_usn);
        // In the case of differing content, server (first sync) content wins
        media1.add_file("diff", b"1").unwrap();
        media2.add_file("diff", b"2").unwrap();
        ctx.sync_media1().await?;
        ctx.sync_media2().await?;
        assert_eq!(
            fs::read_to_string(media1.media_folder.join("diff")).unwrap(),
            "1"
        );
        assert_eq!(
            fs::read_to_string(media2.media_folder.join("diff")).unwrap(),
            "1"
        );
        Ok(())
    })
    .await
}
