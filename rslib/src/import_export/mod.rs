// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod gather;
mod insert;
pub mod package;

use std::marker::PhantomData;

use crate::prelude::*;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ImportProgress {
    File,
    Extracting,
    Gathering,
    Media(usize),
    MediaCheck(usize),
    Notes(usize),
}

/// Wrapper around a progress function, usually passed by the [crate::backend::Backend],
/// to make repeated calls more ergonomic.
pub(crate) struct IncrementableProgress<P>(Box<dyn FnMut(P, bool) -> bool>);

impl<P> IncrementableProgress<P> {
    /// `progress_fn: (progress, throttle) -> should_continue`
    pub(crate) fn new(progress_fn: impl 'static + FnMut(P, bool) -> bool) -> Self {
        Self(Box::new(progress_fn))
    }

    /// Returns an [Incrementor] with an `increment()` function for use in loops.
    pub(crate) fn incrementor<'inc, 'progress: 'inc, 'map: 'inc>(
        &'progress mut self,
        mut count_map: impl 'map + FnMut(usize) -> P,
    ) -> Incrementor<'inc, impl FnMut(usize) -> Result<()> + 'inc> {
        Incrementor::new(move |u| self.update(count_map(u), true))
    }

    /// Manually triggers an update.
    /// Returns [AnkiError::Interrupted] if the operation should be cancelled.
    pub(crate) fn call(&mut self, progress: P) -> Result<()> {
        self.update(progress, false)
    }

    fn update(&mut self, progress: P, throttle: bool) -> Result<()> {
        if (self.0)(progress, throttle) {
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
        Ok(move |count| (self.0)(count_map(count), true))
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
}
