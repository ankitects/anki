// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{progress::Progress, Backend};
use crate::{
    backend::progress::progress_to_proto,
    backend_proto as pb,
    collection::open_collection,
    log::{self, default_logger},
    prelude::*,
};
pub(super) use pb::collection_service::Service as CollectionService;
use slog::error;

impl CollectionService for Backend {
    fn latest_progress(&self, _input: pb::Empty) -> Result<pb::Progress> {
        let progress = self.progress_state.lock().unwrap().last_progress;
        Ok(progress_to_proto(progress, &self.i18n))
    }

    fn set_wants_abort(&self, _input: pb::Empty) -> Result<pb::Empty> {
        self.progress_state.lock().unwrap().want_abort = true;
        Ok(().into())
    }

    fn open_collection(&self, input: pb::OpenCollectionIn) -> Result<pb::Empty> {
        let mut col = self.col.lock().unwrap();
        if col.is_some() {
            return Err(AnkiError::CollectionAlreadyOpen);
        }

        let mut path = input.collection_path.clone();
        path.push_str(".log");

        let log_path = match input.log_path.as_str() {
            "" => None,
            path => Some(path),
        };
        let logger = default_logger(log_path)?;

        let new_col = open_collection(
            input.collection_path,
            input.media_folder_path,
            input.media_db_path,
            self.server,
            self.i18n.clone(),
            logger,
        )?;

        *col = Some(new_col);

        Ok(().into())
    }

    fn close_collection(&self, input: pb::CloseCollectionIn) -> Result<pb::Empty> {
        self.abort_media_sync_and_wait();

        let mut col = self.col.lock().unwrap();
        if col.is_none() {
            return Err(AnkiError::CollectionNotOpen);
        }

        let col_inner = col.take().unwrap();
        if input.downgrade_to_schema11 {
            let log = log::terminal();
            if let Err(e) = col_inner.close(input.downgrade_to_schema11) {
                error!(log, " failed: {:?}", e);
            }
        }

        Ok(().into())
    }

    fn check_database(&self, _input: pb::Empty) -> Result<pb::CheckDatabaseOut> {
        let mut handler = self.new_progress_handler();
        let progress_fn = move |progress, throttle| {
            handler.update(Progress::DatabaseCheck(progress), throttle);
        };
        self.with_col(|col| {
            col.check_database(progress_fn)
                .map(|problems| pb::CheckDatabaseOut {
                    problems: problems.to_i18n_strings(&col.i18n),
                })
        })
    }

    fn get_undo_status(&self, _input: pb::Empty) -> Result<pb::UndoStatus> {
        self.with_col(|col| Ok(col.undo_status().into_protobuf(&col.i18n)))
    }

    fn undo(&self, _input: pb::Empty) -> Result<pb::UndoStatus> {
        self.with_col(|col| {
            col.undo()?;
            Ok(col.undo_status().into_protobuf(&col.i18n))
        })
    }

    fn redo(&self, _input: pb::Empty) -> Result<pb::UndoStatus> {
        self.with_col(|col| {
            col.redo()?;
            Ok(col.undo_status().into_protobuf(&col.i18n))
        })
    }
}
