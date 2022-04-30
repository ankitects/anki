// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod gather;
mod insert;
pub mod package;

use crate::prelude::*;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ImportProgress {
    File,
    Media(usize),
    MediaCheck(usize),
    Notes(usize),
}

/// Wrapper around a progress function, usually passed by the [crate::backend::Backend],
/// to make repeated calls more ergonomic.
pub(crate) struct IncrementableProgress<P> {
    progress_fn: Box<dyn FnMut(P, bool) -> bool>,
    count_map: Option<Box<dyn FnMut(usize) -> P>>,
    count: usize,
    update_interval: usize,
}

impl<P> IncrementableProgress<P> {
    /// `progress_fn: (progress, throttle) -> should_continue`
    pub(crate) fn new(progress_fn: impl 'static + FnMut(P, bool) -> bool) -> Self {
        Self {
            progress_fn: Box::new(progress_fn),
            count_map: None,
            count: 0,
            update_interval: 17,
        }
    }

    /// Resets the count, and defines how it should be mapped to a progress value
    /// in the future.
    pub(crate) fn set_count_map(&mut self, count_map: impl 'static + FnMut(usize) -> P) {
        self.count_map = Some(Box::new(count_map));
        self.count = 0;
    }

    /// Increment the progress counter, periodically triggering an update.
    /// Returns [AnkiError::Interrupted] if the operation should be cancelled.
    /// Must have called `set_count_map()` before calling this.
    pub(crate) fn increment(&mut self) -> Result<()> {
        self.count += 1;
        if self.count % self.update_interval != 0 {
            return Ok(());
        }
        let progress = self.mapped_progress()?;
        self.update(progress, true)
    }

    /// Manually trigger an update.
    /// Returns [AnkiError::Interrupted] if the operation should be cancelled.
    pub(crate) fn call(&mut self, progress: P) -> Result<()> {
        self.update(progress, false)
    }

    fn mapped_progress(&mut self) -> Result<P> {
        if let Some(count_map) = self.count_map.as_mut() {
            Ok(count_map(self.count))
        } else {
            Err(AnkiError::invalid_input("count_map not set"))
        }
    }

    fn update(&mut self, progress: P, throttle: bool) -> Result<()> {
        if (self.progress_fn)(progress, throttle) {
            Ok(())
        } else {
            Err(AnkiError::Interrupted)
        }
    }

    /// Stopgap for returning a progress fn compliant with the media code.
    pub(crate) fn media_db_fn(
        &mut self,
        count_map: impl 'static + Fn(usize) -> P,
    ) -> Result<impl FnMut(usize) -> bool + '_> {
        Ok(move |count| (self.progress_fn)(count_map(count), true))
    }
}

impl IncrementableProgress<usize> {
    /// Allows incrementing without a map, if the progress is of type [usize].
    pub(crate) fn increment_flat(&mut self) -> Result<()> {
        self.count += 1;
        if self.count % 17 == 0 {
            self.update(self.count, true)
        } else {
            Ok(())
        }
    }
}
