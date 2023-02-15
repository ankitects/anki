// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::MutexGuard;

use tracing::error;

use super::progress::Progress;
use super::Backend;
use crate::backend::progress::progress_to_proto;
use crate::collection::CollectionBuilder;
use crate::pb;
pub(super) use crate::pb::collection::collection_service::Service as CollectionService;
use crate::prelude::*;
use crate::storage::SchemaVersion;

impl CollectionService for Backend {
    fn latest_progress(&self, _input: pb::generic::Empty) -> Result<pb::collection::Progress> {
        let progress = self.progress_state.lock().unwrap().last_progress;
        Ok(progress_to_proto(progress, &self.tr))
    }

    fn set_wants_abort(&self, _input: pb::generic::Empty) -> Result<pb::generic::Empty> {
        self.progress_state.lock().unwrap().want_abort = true;
        Ok(().into())
    }

    fn open_collection(
        &self,
        input: pb::collection::OpenCollectionRequest,
    ) -> Result<pb::generic::Empty> {
        let mut guard = self.lock_closed_collection()?;

        let mut builder = CollectionBuilder::new(input.collection_path);
        builder
            .set_force_schema11(input.force_schema11)
            .set_media_paths(input.media_folder_path, input.media_db_path)
            .set_server(self.server)
            .set_tr(self.tr.clone());

        *guard = Some(builder.build()?);

        Ok(().into())
    }

    fn close_collection(
        &self,
        input: pb::collection::CloseCollectionRequest,
    ) -> Result<pb::generic::Empty> {
        let desired_version = if input.downgrade_to_schema11 {
            Some(SchemaVersion::V11)
        } else {
            None
        };

        self.abort_media_sync_and_wait();
        let mut guard = self.lock_open_collection()?;
        let col_inner = guard.take().unwrap();

        if let Err(e) = col_inner.close(desired_version) {
            error!(" failed: {:?}", e);
        }

        Ok(().into())
    }

    fn check_database(
        &self,
        _input: pb::generic::Empty,
    ) -> Result<pb::collection::CheckDatabaseResponse> {
        let mut handler = self.new_progress_handler();
        let progress_fn = move |progress, throttle| {
            handler.update(Progress::DatabaseCheck(progress), throttle);
        };
        self.with_col(|col| {
            col.check_database(progress_fn)
                .map(|problems| pb::collection::CheckDatabaseResponse {
                    problems: problems.to_i18n_strings(&col.tr),
                })
        })
    }

    fn get_undo_status(&self, _input: pb::generic::Empty) -> Result<pb::collection::UndoStatus> {
        self.with_col(|col| Ok(col.undo_status().into_protobuf(&col.tr)))
    }

    fn undo(&self, _input: pb::generic::Empty) -> Result<pb::collection::OpChangesAfterUndo> {
        self.with_col(|col| col.undo().map(|out| out.into_protobuf(&col.tr)))
    }

    fn redo(&self, _input: pb::generic::Empty) -> Result<pb::collection::OpChangesAfterUndo> {
        self.with_col(|col| col.redo().map(|out| out.into_protobuf(&col.tr)))
    }

    fn add_custom_undo_entry(&self, input: pb::generic::String) -> Result<pb::generic::UInt32> {
        self.with_col(|col| Ok(col.add_custom_undo_step(input.val).into()))
    }

    fn merge_undo_entries(&self, input: pb::generic::UInt32) -> Result<pb::collection::OpChanges> {
        let starting_from = input.val as usize;
        self.with_col(|col| col.merge_undoable_ops(starting_from))
            .map(Into::into)
    }

    fn create_backup(
        &self,
        input: pb::collection::CreateBackupRequest,
    ) -> Result<pb::generic::Bool> {
        // lock collection
        let mut col_lock = self.lock_open_collection()?;
        let col = col_lock.as_mut().unwrap();
        // await any previous backup first
        let mut task_lock = self.backup_task.lock().unwrap();
        if let Some(task) = task_lock.take() {
            task.join().unwrap()?;
        }
        // start the new backup
        let created = if let Some(task) = col.maybe_backup(input.backup_folder, input.force)? {
            if input.wait_for_completion {
                drop(col_lock);
                task.join().unwrap()?;
            } else {
                *task_lock = Some(task);
            }
            true
        } else {
            false
        };
        Ok(created.into())
    }

    fn await_backup_completion(&self, _input: pb::generic::Empty) -> Result<pb::generic::Empty> {
        self.await_backup_completion()?;
        Ok(().into())
    }
}

impl Backend {
    pub(super) fn lock_open_collection(&self) -> Result<MutexGuard<Option<Collection>>> {
        let guard = self.col.lock().unwrap();
        guard
            .is_some()
            .then_some(guard)
            .ok_or(AnkiError::CollectionNotOpen)
    }

    pub(super) fn lock_closed_collection(&self) -> Result<MutexGuard<Option<Collection>>> {
        let guard = self.col.lock().unwrap();
        guard
            .is_none()
            .then_some(guard)
            .ok_or(AnkiError::CollectionAlreadyOpen)
    }

    fn await_backup_completion(&self) -> Result<()> {
        if let Some(task) = self.backup_task.lock().unwrap().take() {
            task.join().unwrap()?;
        }
        Ok(())
    }
}
