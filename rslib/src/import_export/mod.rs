// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod gather;
mod insert;
pub mod package;

use crate::prelude::*;

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ImportProgress {
    Collection,
    Media(usize),
    MediaCheck(usize),
    Notes(usize),
}

pub(crate) struct IncrementalProgress<F: FnMut(usize) -> Result<()>> {
    progress_fn: F,
    counter: usize,
}

impl<F: FnMut(usize) -> Result<()>> IncrementalProgress<F> {
    pub(crate) fn new(progress_fn: F) -> Self {
        Self {
            progress_fn,
            counter: 0,
        }
    }

    pub(crate) fn increment(&mut self) -> Result<()> {
        self.counter += 1;
        if self.counter % 17 == 0 {
            (self.progress_fn)(self.counter)
        } else {
            Ok(())
        }
    }
}
