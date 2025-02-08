// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::marker::PhantomData;
use std::sync::Arc;
use std::sync::Mutex;

use anki_i18n::I18n;
use anki_proto::collection::progress::Value;

use crate::dbcheck::DatabaseCheckProgress;
use crate::error::AnkiError;
use crate::error::Result;
use crate::import_export::ExportProgress;
use crate::import_export::ImportProgress;
use crate::prelude::Collection;
use crate::scheduler::fsrs::memory_state::ComputeMemoryProgress;
use crate::scheduler::fsrs::params::ComputeParamsProgress;
use crate::scheduler::fsrs::retention::ComputeRetentionProgress;
use crate::sync::collection::normal::NormalSyncProgress;
use crate::sync::collection::progress::FullSyncProgress;
use crate::sync::collection::progress::SyncStage;
use crate::sync::media::progress::MediaCheckProgress;
use crate::sync::media::progress::MediaSyncProgress;

/// Stores progress state that can be updated cheaply, and will update a
/// Mutex-protected copy that other threads can check, if more than 0.1
/// secs has elapsed since the previous update.
/// If another thread has set the `want_abort` flag on the shared state,
/// then the next non-throttled update will fail with [AnkiError::Interrupted].
/// Automatically updates the shared state on creation, with the default
/// value for the type.
#[derive(Debug, Default)]
pub struct ThrottlingProgressHandler<P: Into<Progress> + Default> {
    pub(crate) state: P,
    shared_state: Arc<Mutex<ProgressState>>,
    last_shared_update: coarsetime::Instant,
}

impl<P: Into<Progress> + Default + Clone> ThrottlingProgressHandler<P> {
    pub(crate) fn new(shared_state: Arc<Mutex<ProgressState>>) -> Self {
        let initial = P::default();
        {
            let mut guard = shared_state.lock().unwrap();
            guard.last_progress = Some(initial.clone().into());
            guard.want_abort = false;
        }
        Self {
            shared_state,
            state: initial,
            ..Default::default()
        }
    }

    /// Overwrite the currently-stored state. This does not throttle, and should
    /// be used when you want to ensure the UI state gets updated, and
    /// ensure that the abort flag is checked between expensive steps.
    pub(crate) fn set(&mut self, progress: P) -> Result<()> {
        self.update(false, |state| *state = progress)
    }

    /// Mutate the currently-stored state, and maybe update shared state.
    pub(crate) fn update(&mut self, throttle: bool, mutator: impl FnOnce(&mut P)) -> Result<()> {
        mutator(&mut self.state);

        let now = coarsetime::Instant::now();
        if throttle && now.duration_since(self.last_shared_update).as_f64() < 0.1 {
            return Ok(());
        }
        self.last_shared_update = now;

        let mut guard = self.shared_state.lock().unwrap();
        guard.last_progress.replace(self.state.clone().into());

        if std::mem::take(&mut guard.want_abort) {
            Err(AnkiError::Interrupted)
        } else {
            Ok(())
        }
    }

    /// Check the abort flag, and trigger a UI update if it was throttled.
    pub(crate) fn check_cancelled(&mut self) -> Result<()> {
        self.set(self.state.clone())
    }

    /// An alternative to incrementor() below, that can be used across function
    /// calls easily, as it continues from the previous state.
    pub(crate) fn increment(&mut self, accessor: impl Fn(&mut P) -> &mut usize) -> Result<()> {
        let field = accessor(&mut self.state);
        *field += 1;
        if *field % 17 == 0 {
            self.update(true, |_| ())?;
        }
        Ok(())
    }

    /// Returns an [Incrementor] with an `increment()` function for use in
    /// loops.
    pub(crate) fn incrementor<'inc, 'progress: 'inc, 'map: 'inc>(
        &'progress mut self,
        mut count_map: impl 'map + FnMut(usize) -> P,
    ) -> Incrementor<'inc, impl FnMut(usize) -> Result<()> + 'inc> {
        Incrementor::new(move |u| self.update(true, |p| *p = count_map(u)))
    }

    /// Stopgap for returning a progress fn compliant with the media code.
    pub(crate) fn media_db_fn(
        &mut self,
        count_map: impl 'static + Fn(usize) -> P,
    ) -> Result<impl FnMut(usize) -> bool + '_>
    where
        P: Into<Progress>,
    {
        Ok(move |count| self.update(true, |p| *p = count_map(count)).is_ok())
    }
}

#[derive(Default, Debug)]
pub struct ProgressState {
    pub want_abort: bool,
    pub last_progress: Option<Progress>,
}

impl ProgressState {
    pub fn reset(&mut self) {
        self.want_abort = false;
        self.last_progress = None;
    }
}

#[derive(Clone, Copy, Debug)]
pub enum Progress {
    MediaSync(MediaSyncProgress),
    MediaCheck(MediaCheckProgress),
    FullSync(FullSyncProgress),
    NormalSync(NormalSyncProgress),
    DatabaseCheck(DatabaseCheckProgress),
    Import(ImportProgress),
    Export(ExportProgress),
    ComputeParams(ComputeParamsProgress),
    ComputeRetention(ComputeRetentionProgress),
    ComputeMemory(ComputeMemoryProgress),
}

pub(crate) fn progress_to_proto(
    progress: Option<Progress>,
    tr: &I18n,
) -> anki_proto::collection::Progress {
    let progress = if let Some(progress) = progress {
        match progress {
            Progress::MediaSync(p) => Value::MediaSync(media_sync_progress(p, tr)),
            Progress::MediaCheck(n) => Value::MediaCheck(tr.media_check_checked(n.checked).into()),
            Progress::FullSync(p) => Value::FullSync(anki_proto::collection::progress::FullSync {
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
                Value::NormalSync(anki_proto::collection::progress::NormalSync {
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
                Value::DatabaseCheck(anki_proto::collection::progress::DatabaseCheck {
                    stage,
                    stage_total: stage_total as u32,
                    stage_current: stage_current as u32,
                })
            }
            Progress::Import(progress) => Value::Importing(
                match progress {
                    ImportProgress::File => tr.importing_importing_file(),
                    ImportProgress::Media(n) => tr.importing_processed_media_file(n),
                    ImportProgress::MediaCheck(n) => tr.media_check_checked(n),
                    ImportProgress::Notes(n) => tr.importing_processed_notes(n),
                    ImportProgress::Extracting => tr.importing_extracting(),
                    ImportProgress::Gathering => tr.importing_gathering(),
                }
                .into(),
            ),
            Progress::Export(progress) => Value::Exporting(
                match progress {
                    ExportProgress::File => tr.exporting_exporting_file(),
                    ExportProgress::Media(n) => tr.exporting_processed_media_files(n),
                    ExportProgress::Notes(n) => tr.importing_processed_notes(n),
                    ExportProgress::Cards(n) => tr.importing_processed_cards(n),
                    ExportProgress::Gathering => tr.importing_gathering(),
                }
                .into(),
            ),
            Progress::ComputeParams(progress) => {
                Value::ComputeParams(anki_proto::collection::ComputeParamsProgress {
                    current: progress.current_iteration,
                    total: progress.total_iterations,
                    reviews: progress.reviews,
                    current_preset: progress.current_preset,
                    total_presets: progress.total_presets,
                })
            }
            Progress::ComputeRetention(progress) => {
                Value::ComputeRetention(anki_proto::collection::ComputeRetentionProgress {
                    current: progress.current,
                    total: progress.total,
                })
            }
            Progress::ComputeMemory(progress) => {
                Value::ComputeMemory(anki_proto::collection::ComputeMemoryProgress {
                    current_cards: progress.current_cards,
                    total_cards: progress.total_cards,
                    label: tr
                        .deck_config_updating_cards(progress.current_cards, progress.total_cards)
                        .into(),
                })
            }
        }
    } else {
        Value::None(anki_proto::generic::Empty {})
    };
    anki_proto::collection::Progress {
        value: Some(progress),
    }
}

fn media_sync_progress(p: MediaSyncProgress, tr: &I18n) -> anki_proto::sync::MediaSyncProgress {
    anki_proto::sync::MediaSyncProgress {
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

impl From<MediaCheckProgress> for Progress {
    fn from(p: MediaCheckProgress) -> Self {
        Progress::MediaCheck(p)
    }
}

impl From<NormalSyncProgress> for Progress {
    fn from(p: NormalSyncProgress) -> Self {
        Progress::NormalSync(p)
    }
}

impl From<DatabaseCheckProgress> for Progress {
    fn from(p: DatabaseCheckProgress) -> Self {
        Progress::DatabaseCheck(p)
    }
}

impl From<ImportProgress> for Progress {
    fn from(p: ImportProgress) -> Self {
        Progress::Import(p)
    }
}

impl From<ExportProgress> for Progress {
    fn from(p: ExportProgress) -> Self {
        Progress::Export(p)
    }
}

impl From<ComputeParamsProgress> for Progress {
    fn from(p: ComputeParamsProgress) -> Self {
        Progress::ComputeParams(p)
    }
}

impl From<ComputeRetentionProgress> for Progress {
    fn from(p: ComputeRetentionProgress) -> Self {
        Progress::ComputeRetention(p)
    }
}

impl From<ComputeMemoryProgress> for Progress {
    fn from(p: ComputeMemoryProgress) -> Self {
        Progress::ComputeMemory(p)
    }
}

impl Collection {
    pub fn new_progress_handler<P: Into<Progress> + Default + Clone>(
        &self,
    ) -> ThrottlingProgressHandler<P> {
        ThrottlingProgressHandler::new(self.state.progress.clone())
    }

    pub(crate) fn clear_progress(&mut self) {
        self.state.progress.lock().unwrap().reset();
    }
}

pub(crate) struct Incrementor<'f, F: 'f + FnMut(usize) -> Result<()>> {
    update_fn: F,
    count: usize,
    update_interval: usize,
    _phantom: PhantomData<&'f ()>,
}

impl<'f, F: 'f + FnMut(usize) -> Result<()>> Incrementor<'f, F> {
    fn new(update_fn: F) -> Self {
        Self {
            update_fn,
            count: 0,
            update_interval: 17,
            _phantom: PhantomData,
        }
    }

    /// Increments the progress counter, periodically triggering an update.
    /// Returns [AnkiError::Interrupted] if the operation should be cancelled.
    pub(crate) fn increment(&mut self) -> Result<()> {
        self.count += 1;
        if self.count % self.update_interval != 0 {
            return Ok(());
        }
        (self.update_fn)(self.count)
    }

    pub(crate) fn count(&self) -> usize {
        self.count
    }
}
