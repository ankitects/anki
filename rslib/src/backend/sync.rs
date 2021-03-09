// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::Arc;

use futures::future::{AbortHandle, AbortRegistration, Abortable};
use slog::warn;

use crate::{
    backend_proto as pb,
    collection::open_collection,
    media::MediaManager,
    prelude::*,
    sync::{
        get_remote_sync_meta, sync_abort, sync_login, FullSyncProgress, NormalSyncProgress,
        SyncActionRequired, SyncAuth, SyncMeta, SyncOutput,
    },
};

use super::{progress::AbortHandleSlot, Backend};

#[derive(Default, Debug)]
pub(super) struct RemoteSyncStatus {
    pub last_check: TimestampSecs,
    pub last_response: pb::sync_status_out::Required,
}

impl RemoteSyncStatus {
    pub(super) fn update(&mut self, required: pb::sync_status_out::Required) {
        self.last_check = TimestampSecs::now();
        self.last_response = required
    }
}

impl From<SyncOutput> for pb::SyncCollectionOut {
    fn from(o: SyncOutput) -> Self {
        pb::SyncCollectionOut {
            host_number: o.host_number,
            server_message: o.server_message,
            required: match o.required {
                SyncActionRequired::NoChanges => {
                    pb::sync_collection_out::ChangesRequired::NoChanges as i32
                }
                SyncActionRequired::FullSyncRequired {
                    upload_ok,
                    download_ok,
                } => {
                    if !upload_ok {
                        pb::sync_collection_out::ChangesRequired::FullDownload as i32
                    } else if !download_ok {
                        pb::sync_collection_out::ChangesRequired::FullUpload as i32
                    } else {
                        pb::sync_collection_out::ChangesRequired::FullSync as i32
                    }
                }
                SyncActionRequired::NormalSyncRequired => {
                    pb::sync_collection_out::ChangesRequired::NormalSync as i32
                }
            },
        }
    }
}

impl From<pb::SyncAuth> for SyncAuth {
    fn from(a: pb::SyncAuth) -> Self {
        SyncAuth {
            hkey: a.hkey,
            host_number: a.host_number,
        }
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
            let log = self.with_col(|col| Ok(col.log.clone()))?;
            warn!(
                log,
                "new sync_abort handle registered, but old one was still present (old sync job might not be cancelled on abort)"
            );
        }
        // Clear the abort handle after the caller is done and drops the guard.
        let guard = scopeguard::guard(Arc::clone(&self.sync_abort), |sync_abort| {
            sync_abort.lock().unwrap().take();
        });
        Ok((guard, abort_reg))
    }

    pub(super) fn sync_media_inner(&self, input: pb::SyncAuth) -> Result<()> {
        // mark media sync as active
        let (abort_handle, abort_reg) = AbortHandle::new_pair();
        {
            let mut guard = self.state.lock().unwrap();
            if guard.media_sync_abort.is_some() {
                // media sync is already active
                return Ok(());
            } else {
                guard.media_sync_abort = Some(abort_handle);
            }
        }

        // get required info from collection
        let mut guard = self.col.lock().unwrap();
        let col = guard.as_mut().unwrap();
        let folder = col.media_folder.clone();
        let db = col.media_db.clone();
        let log = col.log.clone();
        drop(guard);

        // start the sync
        let mut handler = self.new_progress_handler();
        let progress_fn = move |progress| handler.update(progress, true);

        let mgr = MediaManager::new(&folder, &db)?;
        let rt = self.runtime_handle();
        let sync_fut = mgr.sync_media(progress_fn, input.host_number, &input.hkey, log);
        let abortable_sync = Abortable::new(sync_fut, abort_reg);
        let result = rt.block_on(abortable_sync);

        // mark inactive
        self.state.lock().unwrap().media_sync_abort.take();

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
        if let Some(handle) = &guard.media_sync_abort {
            handle.abort();
            self.progress_state.lock().unwrap().want_abort = true;
        }
        drop(guard);

        // block until it aborts
        while self.state.lock().unwrap().media_sync_abort.is_some() {
            std::thread::sleep(std::time::Duration::from_millis(100));
            self.progress_state.lock().unwrap().want_abort = true;
        }
    }

    pub(super) fn sync_login_inner(&self, input: pb::SyncLoginIn) -> Result<pb::SyncAuth> {
        let (_guard, abort_reg) = self.sync_abort_handle()?;

        let rt = self.runtime_handle();
        let sync_fut = sync_login(&input.username, &input.password);
        let abortable_sync = Abortable::new(sync_fut, abort_reg);
        let ret = match rt.block_on(abortable_sync) {
            Ok(sync_result) => sync_result,
            Err(_) => Err(AnkiError::Interrupted),
        };
        ret.map(|a| pb::SyncAuth {
            hkey: a.hkey,
            host_number: a.host_number,
        })
    }

    pub(super) fn sync_status_inner(&self, input: pb::SyncAuth) -> Result<pb::SyncStatusOut> {
        // any local changes mean we can skip the network round-trip
        let req = self.with_col(|col| col.get_local_sync_status())?;
        if req != pb::sync_status_out::Required::NoChanges {
            return Ok(req.into());
        }

        // return cached server response if only a short time has elapsed
        {
            let guard = self.state.lock().unwrap();
            if guard.remote_sync_status.last_check.elapsed_secs() < 300 {
                return Ok(guard.remote_sync_status.last_response.into());
            }
        }

        // fetch and cache result
        let rt = self.runtime_handle();
        let time_at_check_begin = TimestampSecs::now();
        let remote: SyncMeta = rt.block_on(get_remote_sync_meta(input.into()))?;
        let response = self.with_col(|col| col.get_sync_status(remote).map(Into::into))?;

        {
            let mut guard = self.state.lock().unwrap();
            // On startup, the sync status check will block on network access, and then automatic syncing begins,
            // taking hold of the mutex. By the time we reach here, our network status may be out of date,
            // so we discard it if stale.
            if guard.remote_sync_status.last_check < time_at_check_begin {
                guard.remote_sync_status.last_check = time_at_check_begin;
                guard.remote_sync_status.last_response = response;
            }
        }

        Ok(response.into())
    }

    pub(super) fn sync_collection_inner(
        &self,
        input: pb::SyncAuth,
    ) -> Result<pb::SyncCollectionOut> {
        let (_guard, abort_reg) = self.sync_abort_handle()?;

        let rt = self.runtime_handle();
        let input_copy = input.clone();

        let ret = self.with_col(|col| {
            let mut handler = self.new_progress_handler();
            let progress_fn = move |progress: NormalSyncProgress, throttle: bool| {
                handler.update(progress, throttle);
            };

            let sync_fut = col.normal_sync(input.into(), progress_fn);
            let abortable_sync = Abortable::new(sync_fut, abort_reg);

            match rt.block_on(abortable_sync) {
                Ok(sync_result) => sync_result,
                Err(_) => {
                    // if the user aborted, we'll need to clean up the transaction
                    col.storage.rollback_trx()?;
                    // and tell AnkiWeb to clean up
                    let _handle = std::thread::spawn(move || {
                        let _ = rt.block_on(sync_abort(input_copy.hkey, input_copy.host_number));
                    });

                    Err(AnkiError::Interrupted)
                }
            }
        });

        let output: SyncOutput = ret?;
        self.state
            .lock()
            .unwrap()
            .remote_sync_status
            .update(output.required.into());
        Ok(output.into())
    }

    pub(super) fn full_sync_inner(&self, input: pb::SyncAuth, upload: bool) -> Result<()> {
        self.abort_media_sync_and_wait();

        let rt = self.runtime_handle();

        let mut col = self.col.lock().unwrap();
        if col.is_none() {
            return Err(AnkiError::CollectionNotOpen);
        }

        let col_inner = col.take().unwrap();

        let (_guard, abort_reg) = self.sync_abort_handle()?;

        let col_path = col_inner.col_path.clone();
        let media_folder_path = col_inner.media_folder.clone();
        let media_db_path = col_inner.media_db.clone();
        let logger = col_inner.log.clone();

        let mut handler = self.new_progress_handler();
        let progress_fn = move |progress: FullSyncProgress, throttle: bool| {
            handler.update(progress, throttle);
        };

        let result = if upload {
            let sync_fut = col_inner.full_upload(input.into(), Box::new(progress_fn));
            let abortable_sync = Abortable::new(sync_fut, abort_reg);
            rt.block_on(abortable_sync)
        } else {
            let sync_fut = col_inner.full_download(input.into(), Box::new(progress_fn));
            let abortable_sync = Abortable::new(sync_fut, abort_reg);
            rt.block_on(abortable_sync)
        };

        // ensure re-opened regardless of outcome
        col.replace(open_collection(
            col_path,
            media_folder_path,
            media_db_path,
            self.server,
            self.i18n.clone(),
            logger,
        )?);

        match result {
            Ok(sync_result) => {
                if sync_result.is_ok() {
                    self.state
                        .lock()
                        .unwrap()
                        .remote_sync_status
                        .update(pb::sync_status_out::Required::NoChanges);
                }
                sync_result
            }
            Err(_) => Err(AnkiError::Interrupted),
        }
    }
}
