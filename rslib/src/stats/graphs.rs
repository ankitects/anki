// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, prelude::*, revlog::RevlogEntry};

impl Collection {
    pub(crate) fn graph_data_for_search(
        &mut self,
        search: &str,
        days: u32,
    ) -> Result<pb::GraphsOut> {
        self.search_cards_into_table(search)?;
        let all = search.trim().is_empty();
        self.graph_data(all, days)
    }

    fn graph_data(&self, all: bool, days: u32) -> Result<pb::GraphsOut> {
        let timing = self.timing_today()?;
        let revlog_start = TimestampSecs(if days > 0 {
            timing.next_day_at - (((days as i64) + 1) * 86_400)
        } else {
            0
        });

        let offset = self.local_offset();
        let local_offset_secs = offset.local_minus_utc() as i64;

        let cards = self.storage.all_searched_cards()?;
        let revlog = if all {
            self.storage.get_all_revlog_entries(revlog_start)?
        } else {
            self.storage
                .get_revlog_entries_for_searched_cards(revlog_start)?
        };

        self.clear_searched_cards()?;

        Ok(pb::GraphsOut {
            cards: cards.into_iter().map(Into::into).collect(),
            revlog,
            days_elapsed: timing.days_elapsed,
            next_day_at_secs: timing.next_day_at as u32,
            scheduler_version: self.sched_ver() as u32,
            local_offset_secs: local_offset_secs as u32,
        })
    }
}

impl From<RevlogEntry> for pb::RevlogEntry {
    fn from(e: RevlogEntry) -> Self {
        pb::RevlogEntry {
            id: e.id.0,
            cid: e.cid.0,
            usn: e.usn.0,
            button_chosen: e.button_chosen as u32,
            interval: e.interval,
            last_interval: e.last_interval,
            ease_factor: e.ease_factor,
            taken_millis: e.taken_millis,
            review_kind: e.review_kind as i32,
        }
    }
}
