// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::sync::{Arc, Mutex};

use futures::future::AbortHandle;

use super::Backend;
use crate::{
    backend_proto as pb,
    dbcheck::DatabaseCheckProgress,
    i18n::I18n,
    media::sync::MediaSyncProgress,
    sync::{FullSyncProgress, NormalSyncProgress, SyncStage},
};

pub(super) struct ThrottlingProgressHandler {
    pub state: Arc<Mutex<ProgressState>>,
    pub last_update: coarsetime::Instant,
}

impl ThrottlingProgressHandler {
    /// Returns true if should continue.
    pub(super) fn update(&mut self, progress: impl Into<Progress>, throttle: bool) -> bool {
        let now = coarsetime::Instant::now();
        if throttle && now.duration_since(self.last_update).as_f64() < 0.1 {
            return true;
        }
        self.last_update = now;
        let mut guard = self.state.lock().unwrap();
        guard.last_progress.replace(progress.into());
        let want_abort = guard.want_abort;
        guard.want_abort = false;
        !want_abort
    }
}

pub(super) struct ProgressState {
    pub want_abort: bool,
    pub last_progress: Option<Progress>,
}

// fixme: this should support multiple abort handles.
pub(super) type AbortHandleSlot = Arc<Mutex<Option<AbortHandle>>>;

#[derive(Clone, Copy)]
pub(super) enum Progress {
    MediaSync(MediaSyncProgress),
    MediaCheck(u32),
    FullSync(FullSyncProgress),
    NormalSync(NormalSyncProgress),
    DatabaseCheck(DatabaseCheckProgress),
}

pub(super) fn progress_to_proto(progress: Option<Progress>, tr: &I18n) -> pb::Progress {
    let progress = if let Some(progress) = progress {
        match progress {
            Progress::MediaSync(p) => pb::progress::Value::MediaSync(media_sync_progress(p, tr)),
            Progress::MediaCheck(n) => {
                pb::progress::Value::MediaCheck(tr.media_check_checked(n).into())
            }
            Progress::FullSync(p) => pb::progress::Value::FullSync(pb::progress::FullSync {
                transferred: p.transferred_bytes as u32,
                total: p.total_bytes as u32,
            }),
            Progress::NormalSync(p) => {
                let stage = match p.stage {
                    SyncStage::Connecting => tr.sync_syncing(),
                    SyncStage::Syncing => tr.sync_syncing(),
                    SyncStage::Finalizing => tr.sync_checking(),
                }
                .to_string();
                let added = tr
                    .sync_added_updated_count(p.local_update, p.remote_update)
                    .into();
                let removed = tr
                    .sync_media_removed_count(p.local_remove, p.remote_remove)
                    .into();
                pb::progress::Value::NormalSync(pb::progress::NormalSync {
                    stage,
                    added,
                    removed,
                })
            }
            Progress::DatabaseCheck(p) => {
                let mut stage_total = 0;
                let mut stage_current = 0;
                let stage = match p {
                    DatabaseCheckProgress::Integrity => tr.database_check_checking_integrity(),
                    DatabaseCheckProgress::Optimize => tr.database_check_rebuilding(),
                    DatabaseCheckProgress::Cards => tr.database_check_checking_cards(),
                    DatabaseCheckProgress::Notes { current, total } => {
                        stage_total = total;
                        stage_current = current;
                        tr.database_check_checking_notes()
                    }
                    DatabaseCheckProgress::History => tr.database_check_checking_history(),
                }
                .to_string();
                pb::progress::Value::DatabaseCheck(pb::progress::DatabaseCheck {
                    stage,
                    stage_total,
                    stage_current,
                })
            }
        }
    } else {
        pb::progress::Value::None(pb::Empty {})
    };
    pb::Progress {
        value: Some(progress),
    }
}

fn media_sync_progress(p: MediaSyncProgress, tr: &I18n) -> pb::progress::MediaSync {
    pb::progress::MediaSync {
        checked: tr.sync_media_checked_count(p.checked).into(),
        added: tr
            .sync_media_added_count(p.uploaded_files, p.downloaded_files)
            .into(),
        removed: tr
            .sync_media_removed_count(p.uploaded_deletions, p.downloaded_deletions)
            .into(),
    }
}

impl From<FullSyncProgress> for Progress {
    fn from(p: FullSyncProgress) -> Self {
        Progress::FullSync(p)
    }
}

impl From<MediaSyncProgress> for Progress {
    fn from(p: MediaSyncProgress) -> Self {
        Progress::MediaSync(p)
    }
}

impl From<NormalSyncProgress> for Progress {
    fn from(p: NormalSyncProgress) -> Self {
        Progress::NormalSync(p)
    }
}

impl Backend {
    pub(super) fn new_progress_handler(&self) -> ThrottlingProgressHandler {
        {
            let mut guard = self.progress_state.lock().unwrap();
            guard.want_abort = false;
            guard.last_progress = None;
        }
        ThrottlingProgressHandler {
            state: Arc::clone(&self.progress_state),
            last_update: coarsetime::Instant::now(),
        }
    }
}
