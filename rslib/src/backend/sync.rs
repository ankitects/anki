// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::sync::sync_status_response::Required;
use anki_proto::sync::MediaSyncStatusResponse;
use anki_proto::sync::SyncStatusResponse;
use futures::future::AbortHandle;
use futures::future::AbortRegistration;
use futures::future::Abortable;
use reqwest::Url;

use super::Backend;
use crate::prelude::*;
use crate::services::BackendCollectionService;
use crate::sync::collection::normal::ClientSyncState;
use crate::sync::collection::normal::SyncActionRequired;
use crate::sync::collection::normal::SyncOutput;
use crate::sync::collection::progress::sync_abort;
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

impl From<SyncOutput> for anki_proto::sync::SyncCollectionResponse {
    fn from(o: SyncOutput) -> Self {
        anki_proto::sync::SyncCollectionResponse {
            host_number: o.host_number,
            server_message: o.server_message,
            new_endpoint: o.new_endpoint,
            required: match o.required {
                SyncActionRequired::NoChanges => {
                    anki_proto::sync::sync_collection_response::ChangesRequired::NoChanges as i32
                }
                SyncActionRequired::FullSyncRequired {
                    upload_ok,
                    download_ok,
                } => {
                    if !upload_ok {
                        anki_proto::sync::sync_collection_response::ChangesRequired::FullDownload
                            as i32
                    } else if !download_ok {
                        anki_proto::sync::sync_collection_response::ChangesRequired::FullUpload
                            as i32
                    } else {
                        anki_proto::sync::sync_collection_response::ChangesRequired::FullSync as i32
                    }
                }
                SyncActionRequired::NormalSyncRequired => {
                    anki_proto::sync::sync_collection_response::ChangesRequired::NormalSync as i32
                }
            },
            server_media_usn: o.server_media_usn.0,
        }
    }
}

impl TryFrom<anki_proto::sync::SyncAuth> for SyncAuth {
    type Error = AnkiError;

    fn try_from(value: anki_proto::sync::SyncAuth) -> std::result::Result<Self, Self::Error> {
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
                        .and_then(|x| x.join("./"))
                        .or_invalid("Invalid sync server specified. Please check the preferences.")
                })
                .transpose()?,
            io_timeout_secs: value.io_timeout_secs,
        })
    }
}

impl crate::services::BackendSyncService for Backend {
    fn sync_media(&self, input: anki_proto::sync::SyncAuth) -> Result<()> {
        let auth = input.try_into()?;
        self.sync_media_in_background(auth, None).map(Into::into)
    }

    fn media_sync_status(&self) -> Result<MediaSyncStatusResponse> {
        self.get_media_sync_status()
    }

    fn abort_sync(&self) -> Result<()> {
        if let Some(handle) = self.sync_abort.lock().unwrap().take() {
            handle.abort();
        }
        Ok(())
    }

    /// Abort the media sync. Does not wait for completion.
    fn abort_media_sync(&self) -> Result<()> {
        let guard = self.state.lock().unwrap();
        if let Some(handle) = &guard.sync.media_sync_abort {
            handle.abort();
        }
        Ok(())
    }

    fn sync_login(
        &self,
        input: anki_proto::sync::SyncLoginRequest,
    ) -> Result<anki_proto::sync::SyncAuth> {
        self.sync_login_inner(input)
    }

    fn sync_status(
        &self,
        input: anki_proto::sync::SyncAuth,
    ) -> Result<anki_proto::sync::SyncStatusResponse> {
        self.sync_status_inner(input)
    }

    fn sync_collection(
        &self,
        input: anki_proto::sync::SyncCollectionRequest,
    ) -> Result<anki_proto::sync::SyncCollectionResponse> {
        self.sync_collection_inner(input)
    }

    fn full_upload_or_download(
        &self,
        input: anki_proto::sync::FullUploadOrDownloadRequest,
    ) -> Result<()> {
        self.full_sync_inner(
            input.auth.or_invalid("missing auth")?,
            input.server_usn.map(Usn),
            input.upload,
        )?;
        Ok(())
    }

    fn set_custom_certificate(
        &self,
        _input: anki_proto::generic::String,
    ) -> Result<anki_proto::generic::Bool> {
        #[cfg(feature = "rustls")]
        return Ok(self.set_custom_certificate_inner(_input.val).is_ok().into());
        #[cfg(not(feature = "rustls"))]
        return Ok(false.into());
    }
}

impl Backend {
    /// Return a handle for regular (non-media) syncing.
    #[allow(clippy::type_complexity)]
    fn sync_abort_handle(
        &self,
    ) -> Result<(
        scopeguard::ScopeGuard<Backend, impl FnOnce(Backend)>,
        AbortRegistration,
    )> {
        let (abort_handle, abort_reg) = AbortHandle::new_pair();
        // Register the new abort_handle.
        self.sync_abort.lock().unwrap().replace(abort_handle);
        // Clear the abort handle after the caller is done and drops the guard.
        let guard = scopeguard::guard(self.clone(), |backend| {
            backend.sync_abort.lock().unwrap().take();
        });
        Ok((guard, abort_reg))
    }

    pub(super) fn sync_media_in_background(
        &self,
        auth: SyncAuth,
        server_usn: Option<Usn>,
    ) -> Result<()> {
        let mut task = self.media_sync_task.lock().unwrap();
        if let Some(handle) = &*task {
            if !handle.is_finished() {
                // already running
                return Ok(());
            } else {
                // clean up
                task.take();
            }
        }
        let backend = self.clone();
        *task = Some(std::thread::spawn(move || {
            backend.sync_media_blocking(auth, server_usn)
        }));
        Ok(())
    }

    /// True if active. Will throw if terminated with error.
    fn get_media_sync_status(&self) -> Result<MediaSyncStatusResponse> {
        let mut task = self.media_sync_task.lock().unwrap();
        let active = if let Some(handle) = &*task {
            if !handle.is_finished() {
                true
            } else {
                match task.take().unwrap().join() {
                    Ok(inner_result) => inner_result?,
                    Err(panic) => invalid_input!("{:?}", panic),
                };
                false
            }
        } else {
            false
        };
        let progress = self.latest_progress()?;
        let progress = if let Some(anki_proto::collection::progress::Value::MediaSync(progress)) =
            progress.value
        {
            Some(progress)
        } else {
            None
        };
        Ok(MediaSyncStatusResponse { active, progress })
    }

    pub(super) fn sync_media_blocking(
        &self,
        auth: SyncAuth,
        server_usn: Option<Usn>,
    ) -> Result<()> {
        // abort handle
        let (abort_handle, abort_reg) = AbortHandle::new_pair();
        self.state.lock().unwrap().sync.media_sync_abort = Some(abort_handle);

        // start the sync
        let (mgr, progress) = {
            let mut col = self.col.lock().unwrap();
            let col = col.as_mut().unwrap();
            (col.media()?, col.new_progress_handler())
        };
        let rt = self.runtime_handle();
        let sync_fut = mgr.sync_media(progress, auth, self.web_client().clone(), server_usn);
        let abortable_sync = Abortable::new(sync_fut, abort_reg);
        let result = rt.block_on(abortable_sync);

        // clean up the handle
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
        input: anki_proto::sync::SyncLoginRequest,
    ) -> Result<anki_proto::sync::SyncAuth> {
        let (_guard, abort_reg) = self.sync_abort_handle()?;

        let rt = self.runtime_handle();
        let sync_fut = sync_login(
            input.username,
            input.password,
            input.endpoint.clone(),
            self.web_client(),
        );
        let abortable_sync = Abortable::new(sync_fut, abort_reg);
        let ret = match rt.block_on(abortable_sync) {
            Ok(sync_result) => sync_result,
            Err(_) => Err(AnkiError::Interrupted),
        };
        ret.map(|a| anki_proto::sync::SyncAuth {
            hkey: a.hkey,
            endpoint: input.endpoint,
            io_timeout_secs: None,
        })
    }

    pub(super) fn sync_status_inner(
        &self,
        input: anki_proto::sync::SyncAuth,
    ) -> Result<anki_proto::sync::SyncStatusResponse> {
        // any local changes mean we can skip the network round-trip
        let req = self.with_col(|col| col.sync_status_offline())?;
        if req != Required::NoChanges {
            return Ok(status_response_from_required(req));
        }

        // return cached server response if only a short time has elapsed
        {
            let guard = self.state.lock().unwrap();
            if guard.sync.remote_sync_status.last_check.elapsed_secs() < 300 {
                return Ok(status_response_from_required(
                    guard.sync.remote_sync_status.last_response,
                ));
            }
        }

        // fetch and cache result
        let auth = input.try_into()?;
        let rt = self.runtime_handle();
        let time_at_check_begin = TimestampSecs::now();
        let local = self.with_col(|col| col.sync_meta())?;
        let mut client = HttpSyncClient::new(auth, self.web_client());
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
        input: anki_proto::sync::SyncCollectionRequest,
    ) -> Result<anki_proto::sync::SyncCollectionResponse> {
        let auth: SyncAuth = input.auth.or_invalid("missing auth")?.try_into()?;
        let (_guard, abort_reg) = self.sync_abort_handle()?;

        let rt = self.runtime_handle();
        let client = self.web_client();
        let auth2 = auth.clone();

        let ret = self.with_col(|col| {
            let sync_fut = col.normal_sync(auth.clone(), client.clone());
            let abortable_sync = Abortable::new(sync_fut, abort_reg);

            match rt.block_on(abortable_sync) {
                Ok(sync_result) => sync_result,
                Err(_) => {
                    // if the user aborted, we'll need to clean up the transaction
                    col.storage.rollback_trx()?;
                    // and tell AnkiWeb to clean up
                    let _handle = std::thread::spawn(move || {
                        let _ = rt.block_on(sync_abort(auth, client));
                    });

                    Err(AnkiError::Interrupted)
                }
            }
        });

        let output: SyncOutput = ret?;

        if input.sync_media
            && !matches!(output.required, SyncActionRequired::FullSyncRequired { .. })
        {
            self.sync_media_in_background(auth2, Some(output.server_media_usn))?;
        }

        self.state
            .lock()
            .unwrap()
            .sync
            .remote_sync_status
            .update(output.required.into());
        Ok(output.into())
    }

    pub(super) fn full_sync_inner(
        &self,
        input: anki_proto::sync::SyncAuth,
        server_usn: Option<Usn>,
        upload: bool,
    ) -> Result<()> {
        let auth: SyncAuth = input.try_into()?;
        let auth2 = auth.clone();
        self.abort_media_sync_and_wait();

        let rt = self.runtime_handle();

        let mut col = self.col.lock().unwrap();
        if col.is_none() {
            return Err(AnkiError::CollectionNotOpen);
        }

        let col_inner = col.take().unwrap();

        let (_guard, abort_reg) = self.sync_abort_handle()?;

        let mut builder = col_inner.as_builder();

        let result = if upload {
            let sync_fut = col_inner.full_upload(auth, self.web_client().clone());
            let abortable_sync = Abortable::new(sync_fut, abort_reg);
            rt.block_on(abortable_sync)
        } else {
            let sync_fut = col_inner.full_download(auth, self.web_client().clone());
            let abortable_sync = Abortable::new(sync_fut, abort_reg);
            rt.block_on(abortable_sync)
        };

        // ensure re-opened regardless of outcome
        col.replace(builder.build()?);

        let result = match result {
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
        };

        if result.is_ok() && server_usn.is_some() {
            self.sync_media_in_background(auth2, server_usn)?;
        }

        result
    }
}

fn status_response_from_required(required: Required) -> SyncStatusResponse {
    SyncStatusResponse {
        required: required.into(),
        new_endpoint: None,
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
