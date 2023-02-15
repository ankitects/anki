// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use super::GraphsContext;
use crate::pb::stats::graphs_response::FutureDue;

impl GraphsContext {
    pub(super) fn future_due(&self) -> FutureDue {
        let mut have_backlog = false;
        let mut due_by_day: HashMap<i32, u32> = Default::default();
        for c in &self.cards {
            if c.queue as i8 <= 0 {
                continue;
            }
            // The extra original_due check covers lapsed cards, which have their due date
            // updated on graduation.
            let due = if c.is_filtered() && c.original_due != 0 {
                c.original_due
            } else {
                c.due
            };
            let due_day = if c.is_intraday_learning() {
                let offset = due as i64 - self.next_day_start.0;
                (offset / 86_400) as i32
            } else {
                due - (self.days_elapsed as i32)
            };

            have_backlog |= due_day < 0;
            *due_by_day.entry(due_day).or_default() += 1;
        }
        FutureDue {
            future_due: due_by_day,
            have_backlog,
        }
    }
}
