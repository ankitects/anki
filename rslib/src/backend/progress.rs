// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use futures::future::AbortHandle;
use std::sync::{Arc, Mutex};

use crate::{
    backend_proto as pb,
    dbcheck::DatabaseCheckProgress,
    i18n::{tr_args, I18n, TR},
    media::sync::MediaSyncProgress,
    sync::{FullSyncProgress, NormalSyncProgress, SyncStage},
};

use super::Backend;

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

pub(super) fn progress_to_proto(progress: Option<Progress>, i18n: &I18n) -> pb::Progress {
    let progress = if let Some(progress) = progress {
        match progress {
            Progress::MediaSync(p) => pb::progress::Value::MediaSync(media_sync_progress(p, i18n)),
            Progress::MediaCheck(n) => {
                let s = i18n.trn(TR::MediaCheckChecked, tr_args!["count"=>n]);
                pb::progress::Value::MediaCheck(s)
            }
            Progress::FullSync(p) => pb::progress::Value::FullSync(pb::progress::FullSync {
                transferred: p.transferred_bytes as u32,
                total: p.total_bytes as u32,
            }),
            Progress::NormalSync(p) => {
                let stage = match p.stage {
                    SyncStage::Connecting => i18n.tr(TR::SyncSyncing),
                    SyncStage::Syncing => i18n.tr(TR::SyncSyncing),
                    SyncStage::Finalizing => i18n.tr(TR::SyncChecking),
                }
                .to_string();
                let added = i18n.trn(
                    TR::SyncAddedUpdatedCount,
                    tr_args![
                            "up"=>p.local_update, "down"=>p.remote_update],
                );
                let removed = i18n.trn(
                    TR::SyncMediaRemovedCount,
                    tr_args![
                            "up"=>p.local_remove, "down"=>p.remote_remove],
                );
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
                    DatabaseCheckProgress::Integrity => i18n.tr(TR::DatabaseCheckCheckingIntegrity),
                    DatabaseCheckProgress::Optimize => i18n.tr(TR::DatabaseCheckRebuilding),
                    DatabaseCheckProgress::Cards => i18n.tr(TR::DatabaseCheckCheckingCards),
                    DatabaseCheckProgress::Notes { current, total } => {
                        stage_total = total;
                        stage_current = current;
                        i18n.tr(TR::DatabaseCheckCheckingNotes)
                    }
                    DatabaseCheckProgress::History => i18n.tr(TR::DatabaseCheckCheckingHistory),
                }
                .to_string();
                pb::progress::Value::DatabaseCheck(pb::progress::DatabaseCheck {
                    stage,
                    stage_current,
                    stage_total,
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

fn media_sync_progress(p: MediaSyncProgress, i18n: &I18n) -> pb::progress::MediaSync {
    pb::progress::MediaSync {
        checked: i18n.trn(TR::SyncMediaCheckedCount, tr_args!["count"=>p.checked]),
        added: i18n.trn(
            TR::SyncMediaAddedCount,
            tr_args!["up"=>p.uploaded_files,"down"=>p.downloaded_files],
        ),
        removed: i18n.trn(
            TR::SyncMediaRemovedCount,
            tr_args!["up"=>p.uploaded_deletions,"down"=>p.downloaded_deletions],
        ),
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
