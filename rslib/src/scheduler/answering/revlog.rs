// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::CardAnswer;
use crate::prelude::*;
use crate::revlog::RevlogData;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;
use crate::scheduler::states::CardState;
use crate::scheduler::states::IntervalKind;

pub struct RevlogEntryPartial {
    interval: IntervalKind,
    last_interval: IntervalKind,
    ease_factor: f32,
    review_kind: RevlogReviewKind,
}

impl RevlogEntryPartial {
    pub(super) fn new(
        current: CardState,
        next: CardState,
        ease_factor: f32,
        secs_until_rollover: u32,
    ) -> Self {
        let next_interval = next.interval_kind().maybe_as_days(secs_until_rollover);
        let current_interval = current.interval_kind().maybe_as_days(secs_until_rollover);

        RevlogEntryPartial {
            interval: next_interval,
            last_interval: current_interval,
            ease_factor,
            review_kind: current.revlog_kind(),
        }
    }

    pub(super) fn into_revlog_entry(self, usn: Usn, answer: &CardAnswer) -> RevlogEntry {
        RevlogEntry {
            id: answer.answered_at.into(),
            cid: answer.card_id,
            usn,
            button_chosen: answer.rating.as_number(),
            interval: self.interval.as_revlog_interval(),
            last_interval: self.last_interval.as_revlog_interval(),
            ease_factor: (self.ease_factor * 1000.0).round() as u32,
            taken_millis: answer.milliseconds_taken,
            review_kind: self.review_kind,
            reveal_millis: answer.milliseconds_to_reveal,
            data: RevlogData {
                variant_id: answer.variant_id,
            }
            .to_data_string(),
        }
    }
}
