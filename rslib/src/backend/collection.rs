// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;

use slog::error;

use super::{progress::Progress, Backend};
pub(super) use crate::backend_proto::collection_service::Service as CollectionService;
use crate::{
    backend::progress::progress_to_proto,
    backend_proto::{self as pb, preferences::Backups},
    collection::{
        backup::{self, ImportProgress},
        CollectionBuilder,
    },
    log::{self},
    prelude::*,
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
        let mut col = self.col.lock().unwrap();
        if col.is_some() {
            return Err(AnkiError::CollectionAlreadyOpen);
        }

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

        *col = Some(builder.build()?);

        Ok(().into())
    }

    fn close_collection(&self, input: pb::CloseCollectionRequest) -> Result<pb::Empty> {
        self.abort_media_sync_and_wait();

        let mut col = self.col.lock().unwrap();
        if col.is_none() {
            return Err(AnkiError::CollectionNotOpen);
        }

        let mut col_inner = col.take().unwrap();
        let limits = col_inner.get_backups();
        let col_path = std::mem::take(&mut col_inner.col_path);

        if input.downgrade_to_schema11 {
            let log = log::terminal();
            if let Err(e) = col_inner.close(input.downgrade_to_schema11) {
                error!(log, " failed: {:?}", e);
            }
        }

        if let Some(backup_folder) = input.backup_folder {
            self.start_backup(
                col_path,
                backup_folder,
                limits,
                input.minimum_backup_interval,
            )?;
        }

        Ok(().into())
    }

    fn restore_backup(&self, input: pb::RestoreBackupRequest) -> Result<pb::String> {
        let col = self.col.lock().unwrap();
        if col.is_some() {
            Err(AnkiError::CollectionAlreadyOpen)
        } else {
            let mut handler = self.new_progress_handler();
            let progress_fn = move |progress| {
                let throttle = matches!(progress, ImportProgress::Media(_));
                if handler.update(Progress::Import(progress), throttle) {
                    Ok(())
                } else {
                    Err(AnkiError::Interrupted)
                }
            };

            backup::restore_backup(
                progress_fn,
                &input.col_path,
                &input.backup_path,
                &input.media_folder,
                &self.tr,
            )
            .map(Into::into)
        }
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

    fn await_backup_completion(&self, _input: pb::Empty) -> Result<pb::Empty> {
        self.await_backup_completion();
        Ok(().into())
    }
}

impl Backend {
    fn await_backup_completion(&self) {
        if let Some(task) = self.backup_task.lock().unwrap().take() {
            task.join().unwrap();
        }
    }

    fn start_backup(
        &self,
        col_path: impl AsRef<Path>,
        backup_folder: impl AsRef<Path> + Send + 'static,
        limits: Backups,
        minimum_backup_interval: Option<u64>,
    ) -> Result<()> {
        self.await_backup_completion();
        *self.backup_task.lock().unwrap() = backup::backup(
            col_path,
            backup_folder,
            limits,
            minimum_backup_interval,
            self.log.clone(),
        )?;

        Ok(())
    }
}
