// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::MutexGuard;

use anki_proto::generic;
use tracing::error;

use super::Backend;
use crate::collection::CollectionBuilder;
use crate::prelude::*;
use crate::progress::progress_to_proto;
use crate::services::BackendCollectionService;
use crate::storage::SchemaVersion;

impl BackendCollectionService for Backend {
    fn open_collection(&self, input: anki_proto::collection::OpenCollectionRequest) -> Result<()> {
        let mut guard = self.lock_closed_collection()?;

        let mut builder = CollectionBuilder::new(input.collection_path);
        builder
            .set_media_paths(input.media_folder_path, input.media_db_path)
            .set_server(self.server)
            .set_tr(self.tr.clone())
            .set_shared_progress_state(self.progress_state.clone());

        let mut col = builder.build()?;
        if !self.server {
            if let Err(err) = col.repair_foreign_fsrs_memory_states_on_open() {
                tracing::warn!(?err, "repairing foreign FSRS memory states on open failed");
            }
        }
        *guard = Some(col);

        Ok(())
    }

    fn close_collection(
        &self,
        input: anki_proto::collection::CloseCollectionRequest,
    ) -> Result<()> {
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

        Ok(())
    }

    fn create_backup(
        &self,
        input: anki_proto::collection::CreateBackupRequest,
    ) -> Result<generic::Bool> {
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

    fn await_backup_completion(&self) -> Result<()> {
        self.await_backup_completion()?;
        Ok(())
    }

    fn latest_progress(&self) -> Result<anki_proto::collection::Progress> {
        let progress = self.progress_state.lock().unwrap().last_progress.clone();
        Ok(progress_to_proto(progress, &self.tr))
    }

    fn set_wants_abort(&self) -> Result<()> {
        self.progress_state.lock().unwrap().want_abort = true;
        Ok(())
    }
}

impl Backend {
    pub(super) fn lock_open_collection(&self) -> Result<MutexGuard<'_, Option<Collection>>> {
        let guard = self.col.lock().unwrap();
        guard
            .is_some()
            .then_some(guard)
            .ok_or(AnkiError::CollectionNotOpen)
    }

    pub(super) fn lock_closed_collection(&self) -> Result<MutexGuard<'_, Option<Collection>>> {
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

#[cfg(test)]
mod test {
    use anki_proto::collection::CreateBackupRequest;
    use anki_proto::collection::OpenCollectionRequest;
    use rusqlite::OptionalExtension;

    use super::*;
    use crate::storage::FsrsReviewRetrievabilityCacheRow;
    use crate::storage::FsrsReviewRetrievabilitySampleRole;

    #[test]
    fn collection_session_preserves_calibration_cache_after_reopen() -> Result<()> {
        for same_path in [false, true] {
            let dir = tempfile::tempdir().unwrap();
            let backend = Backend::new(I18n::template_only(), false);
            let request = OpenCollectionRequest {
                collection_path: dir.path().join("original.anki2").to_str().unwrap().into(),
                media_folder_path: dir.path().join("media").to_str().unwrap().into(),
                media_db_path: dir.path().join("media.db").to_str().unwrap().into(),
            };
            backend.open_collection(request.clone())?;
            let session = backend.with_col(|col| Ok(col.session_id.clone()))?;
            let row = FsrsReviewRetrievabilityCacheRow {
                revlog_id: 1.into(),
                prediction: 0.25,
                sample_role: FsrsReviewRetrievabilitySampleRole::FinalFit,
                fold_index: -1,
            };
            assert_eq!(
                backend.with_col_in_session(&session, |col| {
                    col.storage
                        .set_fsrs_review_retrievability_predictions(&[row], "test")
                })?,
                1
            );
            backend.close_collection(Default::default())?;
            assert!(matches!(
                backend.with_col_in_session(&session, |_| -> Result<()> {
                    panic!("must not publish into a closed collection")
                }),
                Err(AnkiError::CollectionNotOpen)
            ));
            let request = if same_path {
                request
            } else {
                OpenCollectionRequest {
                    collection_path: dir
                        .path()
                        .join("replacement.anki2")
                        .to_str()
                        .unwrap()
                        .into(),
                    ..request
                }
            };
            backend.open_collection(request)?;
            let error = backend
                .with_col_in_session(&session, |col| {
                    col.storage.set_fsrs_review_retrievability_predictions(
                        &[FsrsReviewRetrievabilityCacheRow {
                            prediction: 0.75,
                            ..row
                        }],
                        "test",
                    )
                })
                .unwrap_err();
            assert!(matches!(error, AnkiError::Interrupted));
            backend.with_col(|col| {
                let prediction: Option<f32> = col.storage.db.query_row(
                    "SELECT prediction FROM search_stats_fsrs_review_retrievability WHERE revlog_id = 1",
                    [],
                    |row| row.get(0),
                ).optional()?;
                assert_eq!(prediction, same_path.then_some(0.25));
                Ok(())
            })?;
        }
        Ok(())
    }

    fn retry_after_failure(wait_for_completion: bool, force: bool, consume_by_next_request: bool) {
        let dir = tempfile::tempdir().unwrap();
        let backend = Backend::new(I18n::template_only(), false);
        *backend.col.lock().unwrap() = Some(
            CollectionBuilder::new(dir.path().join("collection.anki2"))
                .build()
                .unwrap(),
        );
        let backups = dir.path().join("backups");
        std::fs::write(&backups, b"not a directory").unwrap();
        let request = CreateBackupRequest {
            backup_folder: backups.to_str().unwrap().into(),
            force: true,
            wait_for_completion,
        };
        let result = backend.create_backup(request.clone());
        let error = if wait_for_completion {
            result.unwrap_err()
        } else {
            assert!(result.unwrap().val);
            if consume_by_next_request {
                backend.create_backup(request.clone()).unwrap_err()
            } else {
                backend.await_backup_completion().unwrap_err()
            }
        };
        assert!(matches!(error, AnkiError::FileIoError { .. }));
        backend.await_backup_completion().unwrap();
        std::fs::remove_file(&backups).unwrap();
        std::fs::create_dir(&backups).unwrap();

        let request = CreateBackupRequest { force, ..request };
        assert!(backend.create_backup(request.clone()).unwrap().val);
        backend.await_backup_completion().unwrap();
        assert_eq!(std::fs::read_dir(&backups).unwrap().count(), 1);
        assert!(!backend.create_backup(request).unwrap().val);
    }

    #[test]
    fn failed_backups_allow_retry() {
        for wait in [false, true] {
            for force in [false, true] {
                retry_after_failure(wait, force, false);
            }
        }
    }

    #[test]
    fn failed_backup_consumed_by_next_request_allows_retry() {
        retry_after_failure(false, true, true);
    }

    #[test]
    fn backup_completion_does_not_mark_reopened_collection() {
        let dir = tempfile::tempdir().unwrap();
        let backend = Backend::new(I18n::template_only(), false);
        *backend.col.lock().unwrap() = Some(
            CollectionBuilder::new(dir.path().join("collection.anki2"))
                .build()
                .unwrap(),
        );
        let backups = dir.path().join("backups");
        std::fs::create_dir(&backups).unwrap();
        assert!(
            backend
                .create_backup(CreateBackupRequest {
                    backup_folder: backups.to_str().unwrap().into(),
                    force: true,
                    wait_for_completion: false,
                })
                .unwrap()
                .val
        );
        backend.close_collection(Default::default()).unwrap();
        *backend.col.lock().unwrap() = Some(
            CollectionBuilder::new(dir.path().join("collection.anki2"))
                .build()
                .unwrap(),
        );
        backend.await_backup_completion().unwrap();
        assert!(backend
            .col
            .lock()
            .unwrap()
            .as_ref()
            .unwrap()
            .changed_since_last_backup()
            .unwrap());
    }
}
