// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use futures::future::AbortHandle;
use futures::future::AbortRegistration;
use futures::future::Abortable;
use pb::sync::sync_status_response::Required;
use reqwest::Url;
use tracing::warn;

use super::progress::AbortHandleSlot;
use super::Backend;
use crate::pb;
pub(super) use crate::pb::sync::sync_service::Service as SyncService;
use crate::pb::sync::SyncStatusResponse;
use crate::prelude::*;
use crate::sync::collection::normal::ClientSyncState;
use crate::sync::collection::normal::NormalSyncProgress;
use crate::sync::collection::normal::SyncActionRequired;
use crate::sync::collection::normal::SyncOutput;
use crate::sync::collection::progress::sync_abort;
use crate::sync::collection::progress::FullSyncProgress;
use crate::sync::collection::status::online_sync_status_check;
use crate::sync::http_client::HttpSyncClient;
use crate::sync::login::sync_login;
use crate::sync::login::SyncAuth;

#[derive(Default)]
pub(super) struct SyncState {
    remote_sync_status: RemoteSyncStatus,
    media_sync_abort: Option<AbortHandle>,
}

#[derive(Default, Debug)]
pub(super) struct RemoteSyncStatus {
    pub last_check: TimestampSecs,
    pub last_response: Required,
}

impl RemoteSyncStatus {
    pub(super) fn update(&mut self, required: Required) {
        self.last_check = TimestampSecs::now();
        self.last_response = required
    }
}

impl From<SyncOutput> for pb::sync::SyncCollectionResponse {
    fn from(o: SyncOutput) -> Self {
        pb::sync::SyncCollectionResponse {
            host_number: o.host_number,
            server_message: o.server_message,
            new_endpoint: o.new_endpoint,
            required: match o.required {
                SyncActionRequired::NoChanges => {
                    pb::sync::sync_collection_response::ChangesRequired::NoChanges as i32
                }
                SyncActionRequired::FullSyncRequired {
                    upload_ok,
                    download_ok,
                } => {
                    if !upload_ok {
                        pb::sync::sync_collection_response::ChangesRequired::FullDownload as i32
                    } else if !download_ok {
                        pb::sync::sync_collection_response::ChangesRequired::FullUpload as i32
                    } else {
                        pb::sync::sync_collection_response::ChangesRequired::FullSync as i32
                    }
                }
                SyncActionRequired::NormalSyncRequired => {
                    pb::sync::sync_collection_response::ChangesRequired::NormalSync as i32
                }
            },
        }
    }
}

impl TryFrom<pb::sync::SyncAuth> for SyncAuth {
    type Error = AnkiError;

    fn try_from(value: pb::sync::SyncAuth) -> std::result::Result<Self, Self::Error> {
        Ok(SyncAuth {
            hkey: value.hkey,
            endpoint: value
                .endpoint
                .map(|v| {
                    Url::try_from(v.as_str())
                        // Without the next line, incomplete URLs like computer.local without the http://
                        // are detected but URLs like computer.local:8000 are not.
                        // By calling join() now, these URLs are detected too and later code that
                        // uses and unwraps the result of join() doesn't panic
                        .and_then(|x| x.join("/"))
                        .or_invalid("Invalid sync server specified. Please check the preferences.")
                })
                .transpose()?,
            io_timeout_secs: value.io_timeout_secs,
        })
    }
}

impl SyncService for Backend {
    fn sync_media(&self, input: pb::sync::SyncAuth) -> Result<pb::generic::Empty> {
        self.sync_media_inner(input).map(Into::into)
    }

    fn abort_sync(&self, _input: pb::generic::Empty) -> Result<pb::generic::Empty> {
        if let Some(handle) = self.sync_abort.lock().unwrap().take() {
            handle.abort();
        }
        Ok(().into())
    }

    /// Abort the media sync. Does not wait for completion.
    fn abort_media_sync(&self, _input: pb::generic::Empty) -> Result<pb::generic::Empty> {
        let guard = self.state.lock().unwrap();
        if let Some(handle) = &guard.sync.media_sync_abort {
            handle.abort();
        }
        Ok(().into())
    }

    fn sync_login(&self, input: pb::sync::SyncLoginRequest) -> Result<pb::sync::SyncAuth> {
        self.sync_login_inner(input)
    }

    fn sync_status(&self, input: pb::sync::SyncAuth) -> Result<pb::sync::SyncStatusResponse> {
        self.sync_status_inner(input)
    }

    fn sync_collection(
        &self,
        input: pb::sync::SyncAuth,
    ) -> Result<pb::sync::SyncCollectionResponse> {
        self.sync_collection_inner(input)
    }

    fn full_upload(&self, input: pb::sync::SyncAuth) -> Result<pb::generic::Empty> {
        self.full_sync_inner(input, true)?;
        Ok(().into())
    }

    fn full_download(&self, input: pb::sync::SyncAuth) -> Result<pb::generic::Empty> {
        self.full_sync_inner(input, false)?;
        Ok(().into())
    }
}

impl Backend {
    fn sync_abort_handle(
        &self,
    ) -> Result<(
        scopeguard::ScopeGuard<AbortHandleSlot, impl FnOnce(AbortHandleSlot)>,
        AbortRegistration,
    )> {
        let (abort_handle, abort_reg) = AbortHandle::new_pair();

        // Register the new abort_handle.
        let old_handle = self.sync_abort.lock().unwrap().replace(abort_handle);
        if old_handle.is_some() {
            // NOTE: In the future we would ideally be able to handle multiple
            //       abort handles by just iterating over them all in
            //       abort_sync). But for now, just log a warning if there was
            //       already one present -- but don't abort it either.
            warn!(
                "new sync_abort handle registered, but old one was still present (old sync job might not be cancelled on abort)"
            );
        }
        // Clear the abort handle after the caller is done and drops the guard.
        let guard = scopeguard::guard(Arc::clone(&self.sync_abort), |sync_abort| {
            sync_abort.lock().unwrap().take();
        });
        Ok((guard, abort_reg))
    }

    pub(super) fn sync_media_inner(&self, auth: pb::sync::SyncAuth) -> Result<()> {
        let auth = auth.try_into()?;
        // mark media sync as active
        let (abort_handle, abort_reg) = AbortHandle::new_pair();
        {
            let mut guard = self.state.lock().unwrap();
            if guard.sync.media_sync_abort.is_some() {
                // media sync is already active
                return Ok(());
            } else {
                guard.sync.media_sync_abort = Some(abort_handle);
            }
        }

        // start the sync
        let mgr = self.col.lock().unwrap().as_mut().unwrap().media()?;
        let mut handler = self.new_progress_handler();
        let progress_fn = move |progress| handler.update(progress, true);

        let rt = self.runtime_handle();
        let sync_fut = mgr.sync_media(progress_fn, auth);
        let abortable_sync = Abortable::new(sync_fut, abort_reg);
        let result = rt.block_on(abortable_sync);

        // mark inactive
        self.state.lock().unwrap().sync.media_sync_abort.take();

        // return result
        match result {
            Ok(sync_result) => sync_result,
            Err(_) => {
                // aborted sync
                Err(AnkiError::Interrupted)
            }
        }
    }

    /// Abort the media sync. Won't return until aborted.
    pub(super) fn abort_media_sync_and_wait(&self) {
        let guard = self.state.lock().unwrap();
        if let Some(handle) = &guard.sync.media_sync_abort {
            handle.abort();
            self.progress_state.lock().unwrap().want_abort = true;
        }
        drop(guard);

        // block until it aborts
        while self.state.lock().unwrap().sync.media_sync_abort.is_some() {
            std::thread::sleep(std::time::Duration::from_millis(100));
            self.progress_state.lock().unwrap().want_abort = true;
        }
    }

    pub(super) fn sync_login_inner(
        &self,
        input: pb::sync::SyncLoginRequest,
    ) -> Result<pb::sync::SyncAuth> {
        let (_guard, abort_reg) = self.sync_abort_handle()?;

        let rt = self.runtime_handle();
        let sync_fut = sync_login(input.username, input.password, input.endpoint);
        let abortable_sync = Abortable::new(sync_fut, abort_reg);
        let ret = match rt.block_on(abortable_sync) {
            Ok(sync_result) => sync_result,
            Err(_) => Err(AnkiError::Interrupted),
        };
        ret.map(|a| pb::sync::SyncAuth {
            hkey: a.hkey,
            endpoint: None,
            io_timeout_secs: None,
        })
    }

    pub(super) fn sync_status_inner(
        &self,
        input: pb::sync::SyncAuth,
    ) -> Result<pb::sync::SyncStatusResponse> {
        // any local changes mean we can skip the network round-trip
        let req = self.with_col(|col| col.sync_status_offline())?;
        if req != Required::NoChanges {
            return Ok(req.into());
        }

        // return cached server response if only a short time has elapsed
        {
            let guard = self.state.lock().unwrap();
            if guard.sync.remote_sync_status.last_check.elapsed_secs() < 300 {
                return Ok(guard.sync.remote_sync_status.last_response.into());
            }
        }

        // fetch and cache result
        let auth = input.try_into()?;
        let rt = self.runtime_handle();
        let time_at_check_begin = TimestampSecs::now();
        let local = self.with_col(|col| col.sync_meta())?;
        let mut client = HttpSyncClient::new(auth);
        let state = rt.block_on(online_sync_status_check(local, &mut client))?;
        {
            let mut guard = self.state.lock().unwrap();
            // On startup, the sync status check will block on network access, and then
            // automatic syncing begins, taking hold of the mutex. By the time
            // we reach here, our network status may be out of date,
            // so we discard it if stale.
            if guard.sync.remote_sync_status.last_check < time_at_check_begin {
                guard.sync.remote_sync_status.last_check = time_at_check_begin;
                guard.sync.remote_sync_status.last_response = state.required.into();
            }
        }

        Ok(state.into())
    }

    pub(super) fn sync_collection_inner(
        &self,
        input: pb::sync::SyncAuth,
    ) -> Result<pb::sync::SyncCollectionResponse> {
        let auth: SyncAuth = input.try_into()?;
        let (_guard, abort_reg) = self.sync_abort_handle()?;

        let rt = self.runtime_handle();

        let ret = self.with_col(|col| {
            let mut handler = self.new_progress_handler();
            let progress_fn = move |progress: NormalSyncProgress, throttle: bool| {
                handler.update(progress, throttle);
            };

            let sync_fut = col.normal_sync(auth.clone(), progress_fn);
            let abortable_sync = Abortable::new(sync_fut, abort_reg);

            match rt.block_on(abortable_sync) {
                Ok(sync_result) => sync_result,
                Err(_) => {
                    // if the user aborted, we'll need to clean up the transaction
                    col.storage.rollback_trx()?;
                    // and tell AnkiWeb to clean up
                    let _handle = std::thread::spawn(move || {
                        let _ = rt.block_on(sync_abort(auth));
                    });

                    Err(AnkiError::Interrupted)
                }
            }
        });

        let output: SyncOutput = ret?;
        self.state
            .lock()
            .unwrap()
            .sync
            .remote_sync_status
            .update(output.required.into());
        Ok(output.into())
    }

    pub(super) fn full_sync_inner(&self, input: pb::sync::SyncAuth, upload: bool) -> Result<()> {
        let auth = input.try_into()?;
        self.abort_media_sync_and_wait();

        let rt = self.runtime_handle();

        let mut col = self.col.lock().unwrap();
        if col.is_none() {
            return Err(AnkiError::CollectionNotOpen);
        }

        let col_inner = col.take().unwrap();

        let (_guard, abort_reg) = self.sync_abort_handle()?;

        let builder = col_inner.as_builder();

        let mut handler = self.new_progress_handler();
        let progress_fn = Box::new(move |progress: FullSyncProgress, throttle: bool| {
            handler.update(progress, throttle);
        });

        let result = if upload {
            let sync_fut = col_inner.full_upload(auth, progress_fn);
            let abortable_sync = Abortable::new(sync_fut, abort_reg);
            rt.block_on(abortable_sync)
        } else {
            let sync_fut = col_inner.full_download(auth, progress_fn);
            let abortable_sync = Abortable::new(sync_fut, abort_reg);
            rt.block_on(abortable_sync)
        };

        // ensure re-opened regardless of outcome
        col.replace(builder.build()?);

        match result {
            Ok(sync_result) => {
                if sync_result.is_ok() {
                    self.state
                        .lock()
                        .unwrap()
                        .sync
                        .remote_sync_status
                        .update(Required::NoChanges);
                }
                sync_result
            }
            Err(_) => Err(AnkiError::Interrupted),
        }
    }
}

impl From<Required> for SyncStatusResponse {
    fn from(r: Required) -> Self {
        SyncStatusResponse {
            required: r.into(),
            new_endpoint: None,
        }
    }
}

impl From<ClientSyncState> for SyncStatusResponse {
    fn from(r: ClientSyncState) -> Self {
        SyncStatusResponse {
            required: Required::from(r.required).into(),
            new_endpoint: r.new_endpoint,
        }
    }
}

impl From<SyncActionRequired> for Required {
    fn from(r: SyncActionRequired) -> Self {
        match r {
            SyncActionRequired::NoChanges => Required::NoChanges,
            SyncActionRequired::FullSyncRequired { .. } => Required::FullSync,
            SyncActionRequired::NormalSyncRequired => Required::NormalSync,
        }
    }
}
