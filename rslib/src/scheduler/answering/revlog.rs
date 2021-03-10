// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    prelude::*,
    revlog::{RevlogEntry, RevlogReviewKind},
    scheduler::states::{CardState, IntervalKind},
};

pub struct RevlogEntryPartial {
    interval: IntervalKind,
    last_interval: IntervalKind,
    ease_factor: f32,
    review_kind: RevlogReviewKind,
}

impl RevlogEntryPartial {
    /// Returns None in the Preview case, since preview cards do not currently log.
    pub(super) fn maybe_new(
        current: CardState,
        next: CardState,
        ease_factor: f32,
        secs_until_rollover: u32,
    ) -> Option<Self> {
        current.revlog_kind().map(|review_kind| {
            let next_interval = next.interval_kind().maybe_as_days(secs_until_rollover);
            let current_interval = current.interval_kind().maybe_as_days(secs_until_rollover);

            RevlogEntryPartial {
                interval: next_interval,
                last_interval: current_interval,
                ease_factor,
                review_kind,
            }
        })
    }

    pub(super) fn into_revlog_entry(
        self,
        usn: Usn,
        cid: CardID,
        button_chosen: u8,
        answered_at: TimestampMillis,
        taken_millis: u32,
    ) -> RevlogEntry {
        RevlogEntry {
            id: answered_at.into(),
            cid,
            usn,
            button_chosen,
            interval: self.interval.as_revlog_interval(),
            last_interval: self.last_interval.as_revlog_interval(),
            ease_factor: (self.ease_factor * 1000.0).round() as u32,
            taken_millis,
            review_kind: self.review_kind,
        }
    }
}
