// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::future::Future;
use std::time::Duration;

use tokio::select;
use tokio::time::interval;

use crate::sync::collection::progress::FullSyncProgress;
use crate::sync::collection::progress::FullSyncProgressFn;
use crate::sync::collection::protocol::EmptyInput;
use crate::sync::collection::protocol::SyncMethod;
use crate::sync::collection::upload::UploadResponse;
use crate::sync::error::HttpResult;
use crate::sync::http_client::io_monitor::IoMonitor;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::request::SyncRequest;
use crate::sync::response::SyncResponse;

impl HttpSyncClient {
    pub fn set_full_sync_progress_fn(&mut self, func: Option<FullSyncProgressFn>) {
        *self.full_sync_progress_fn.lock().unwrap() = func;
    }

    fn full_sync_progress_monitor(&self, sending: bool) -> (IoMonitor, impl Future<Output = ()>) {
        let mut progress = FullSyncProgress {
            transferred_bytes: 0,
            total_bytes: 0,
        };
        let mut progress_fn = self
            .full_sync_progress_fn
            .lock()
            .unwrap()
            .take()
            .expect("progress func was not set");
        let io_monitor = IoMonitor::new();
        let io_monitor2 = io_monitor.clone();
        let update_progress = async move {
            let mut interval = interval(Duration::from_millis(100));
            loop {
                interval.tick().await;
                let guard = io_monitor2.0.lock().unwrap();
                progress.total_bytes = if sending {
                    guard.total_bytes_to_send
                } else {
                    guard.total_bytes_to_receive
                } as usize;
                progress.transferred_bytes = if sending {
                    guard.bytes_sent
                } else {
                    guard.bytes_received
                } as usize;
                progress_fn(progress, true)
            }
        };
        (io_monitor, update_progress)
    }

    pub(super) async fn download_inner(
        &self,
        req: SyncRequest<EmptyInput>,
    ) -> HttpResult<SyncResponse<Vec<u8>>> {
        let (io_monitor, progress_fut) = self.full_sync_progress_monitor(false);
        let output = self.request_ext(SyncMethod::Download, req, io_monitor);
        select! {
            _ = progress_fut => unreachable!(),
            out = output => out
        }
    }

    pub(super) async fn upload_inner(
        &self,
        req: SyncRequest<Vec<u8>>,
    ) -> HttpResult<SyncResponse<UploadResponse>> {
        let (io_monitor, progress_fut) = self.full_sync_progress_monitor(true);
        let output = self.request_ext(SyncMethod::Upload, req, io_monitor);
        select! {
            _ = progress_fut => unreachable!(),
            out = output => out
        }
    }
}
