// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::CardQueue,
    prelude::*,
    scheduler::states::{CardState, IntervalKind, PreviewState},
};

use super::{CardStateUpdater, RevlogEntryPartial};

impl CardStateUpdater {
    // fixme: check learning card moved into preview
    // restores correctly in both learn and day-learn case
    pub(super) fn apply_preview_state(
        &mut self,
        current: CardState,
        next: PreviewState,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.ensure_filtered()?;
        self.card.queue = CardQueue::PreviewRepeat;

        let interval = next.interval_kind();
        match interval {
            IntervalKind::InSecs(secs) => {
                self.card.due = self.now.0 as i32 + secs as i32;
            }
            IntervalKind::InDays(_days) => {
                // unsupported
            }
        }

        Ok(RevlogEntryPartial::maybe_new(
            current,
            next.into(),
            0.0,
            self.secs_until_rollover(),
        ))
    }
}
