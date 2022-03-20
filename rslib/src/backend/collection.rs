// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{path::Path, sync::MutexGuard};

use slog::error;

use super::{progress::Progress, Backend};
pub(super) use crate::backend_proto::collection_service::Service as CollectionService;
use crate::{
    backend::progress::progress_to_proto,
    backend_proto::{self as pb, preferences::BackupLimits},
    collection::{backup, CollectionBuilder},
    prelude::*,
    storage::SchemaVersion,
};

impl CollectionService for Backend {
    fn latest_progress(&self, _input: pb::Empty) -> Result<pb::Progress> {
        let progress = self.progress_state.lock().unwrap().last_progress;
        Ok(progress_to_proto(progress, &self.tr))
    }

    fn set_wants_abort(&self, _input: pb::Empty) -> Result<pb::Empty> {
        self.progress_state.lock().unwrap().want_abort = true;
        Ok(().into())
    }

    fn open_collection(&self, input: pb::OpenCollectionRequest) -> Result<pb::Empty> {
        let mut guard = self.lock_closed_collection()?;

        let mut builder = CollectionBuilder::new(input.collection_path);
        builder
            .set_media_paths(input.media_folder_path, input.media_db_path)
            .set_server(self.server)
            .set_tr(self.tr.clone());
        if !input.log_path.is_empty() {
            builder.set_log_file(&input.log_path)?;
        } else {
            builder.set_logger(self.log.clone());
        }

        *guard = Some(builder.build()?);

        Ok(().into())
    }

    fn close_collection(&self, input: pb::CloseCollectionRequest) -> Result<pb::Empty> {
        let desired_version = if input.downgrade_to_schema11 {
            Some(SchemaVersion::V11)
        } else {
            None
        };

        self.abort_media_sync_and_wait();
        let mut guard = self.lock_open_collection()?;
        let col_inner = guard.take().unwrap();

        if let Err(e) = col_inner.close(desired_version) {
            error!(self.log, " failed: {:?}", e);
        }

        Ok(().into())
    }

    fn check_database(&self, _input: pb::Empty) -> Result<pb::CheckDatabaseResponse> {
        let mut handler = self.new_progress_handler();
        let progress_fn = move |progress, throttle| {
            handler.update(Progress::DatabaseCheck(progress), throttle);
        };
        self.with_col(|col| {
            col.check_database(progress_fn)
                .map(|problems| pb::CheckDatabaseResponse {
                    problems: problems.to_i18n_strings(&col.tr),
                })
        })
    }

    fn get_undo_status(&self, _input: pb::Empty) -> Result<pb::UndoStatus> {
        self.with_col(|col| Ok(col.undo_status().into_protobuf(&col.tr)))
    }

    fn undo(&self, _input: pb::Empty) -> Result<pb::OpChangesAfterUndo> {
        self.with_col(|col| col.undo().map(|out| out.into_protobuf(&col.tr)))
    }

    fn redo(&self, _input: pb::Empty) -> Result<pb::OpChangesAfterUndo> {
        self.with_col(|col| col.redo().map(|out| out.into_protobuf(&col.tr)))
    }

    fn add_custom_undo_entry(&self, input: pb::String) -> Result<pb::UInt32> {
        self.with_col(|col| Ok(col.add_custom_undo_step(input.val).into()))
    }

    fn merge_undo_entries(&self, input: pb::UInt32) -> Result<pb::OpChanges> {
        let starting_from = input.val as usize;
        self.with_col(|col| col.merge_undoable_ops(starting_from))
            .map(Into::into)
    }

    fn create_backup(&self, input: pb::CreateBackupRequest) -> Result<pb::Empty> {
        self.lock_open_collection()?
            .as_ref()
            .map(|col| {
                let limits = col.get_backup_limits();
                col.storage.checkpoint()?;
                self.start_backup(
                    &col.col_path,
                    input.backup_folder,
                    limits,
                    input.minimum_backup_interval,
                )
            })
            .unwrap()
            .and_then(|_| {
                if input.wait_for_completion {
                    self.await_backup_completion()?
                }
                Ok(().into())
            })
    }

    fn await_backup_completion(&self, _input: pb::Empty) -> Result<pb::Empty> {
        self.await_backup_completion()?;
        Ok(().into())
    }
}

impl Backend {
    pub(super) fn lock_open_collection(&self) -> Result<MutexGuard<Option<Collection>>> {
        let guard = self.col.lock().unwrap();
        guard
            .is_some()
            .then(|| guard)
            .ok_or(AnkiError::CollectionNotOpen)
    }

    pub(super) fn lock_closed_collection(&self) -> Result<MutexGuard<Option<Collection>>> {
        let guard = self.col.lock().unwrap();
        guard
            .is_none()
            .then(|| guard)
            .ok_or(AnkiError::CollectionAlreadyOpen)
    }

    fn await_backup_completion(&self) -> Result<()> {
        if let Some(task) = self.backup_task.lock().unwrap().take() {
            task.join().unwrap()?;
        }
        Ok(())
    }

    fn start_backup(
        &self,
        col_path: impl AsRef<Path>,
        backup_folder: impl AsRef<Path> + Send + 'static,
        limits: BackupLimits,
        minimum_backup_interval: Option<u64>,
    ) -> Result<()> {
        let mut task_lock = self.backup_task.lock().unwrap();
        if let Some(task) = task_lock.take() {
            task.join().unwrap()?;
        }
        *task_lock = backup::backup(
            col_path,
            backup_folder,
            limits,
            minimum_backup_interval,
            self.log.clone(),
            self.tr.clone(),
        )?;

        Ok(())
    }
}
