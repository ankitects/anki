// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::future::Future;
use std::time::Duration;

use tokio::select;
use tokio::time::interval;

use crate::progress::ThrottlingProgressHandler;
use crate::sync::collection::progress::FullSyncProgress;
use crate::sync::collection::protocol::EmptyInput;
use crate::sync::collection::protocol::SyncMethod;
use crate::sync::collection::upload::UploadResponse;
use crate::sync::error::HttpResult;
use crate::sync::http_client::io_monitor::IoMonitor;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::request::SyncRequest;
use crate::sync::response::SyncResponse;

impl HttpSyncClient {
    fn full_sync_progress_monitor(
        &self,
        sending: bool,
        mut progress: ThrottlingProgressHandler<FullSyncProgress>,
    ) -> (IoMonitor, impl Future<Output = ()>) {
        let io_monitor = IoMonitor::new();
        let io_monitor2 = io_monitor.clone();
        let update_progress = async move {
            let mut interval = interval(Duration::from_millis(100));
            loop {
                interval.tick().await;
                let (total_bytes, transferred_bytes) = {
                    let guard = io_monitor2.0.lock().unwrap();
                    (
                        if sending {
                            guard.total_bytes_to_send
                        } else {
                            guard.total_bytes_to_receive
                        },
                        if sending {
                            guard.bytes_sent
                        } else {
                            guard.bytes_received
                        },
                    )
                };
                _ = progress.update(false, |p| {
                    p.total_bytes = total_bytes as usize;
                    p.transferred_bytes = transferred_bytes as usize;
                })
            }
        };
        (io_monitor, update_progress)
    }

    pub(in super::super) async fn download_with_progress(
        &self,
        req: SyncRequest<EmptyInput>,
        progress: ThrottlingProgressHandler<FullSyncProgress>,
    ) -> HttpResult<SyncResponse<Vec<u8>>> {
        let (io_monitor, progress_fut) = self.full_sync_progress_monitor(false, progress);
        let output = self.request_ext(SyncMethod::Download, req, io_monitor);
        select! {
            _ = progress_fut => unreachable!(),
            out = output => out
        }
    }

    pub(in super::super) async fn upload_with_progress(
        &self,
        req: SyncRequest<Vec<u8>>,
        progress: ThrottlingProgressHandler<FullSyncProgress>,
    ) -> HttpResult<SyncResponse<UploadResponse>> {
        let (io_monitor, progress_fut) = self.full_sync_progress_monitor(true, progress);
        let output = self.request_ext(SyncMethod::Upload, req, io_monitor);
        select! {
            _ = progress_fut => unreachable!(),
            out = output => out
        }
    }
}
