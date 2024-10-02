// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod added;
mod buttons;
mod card_counts;
mod eases;
mod future_due;
mod hours;
mod intervals;
mod retention;
mod retrievability;
mod reviews;
mod today;

use crate::config::BoolKey;
use crate::config::Weekday;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::search::SortMode;

struct GraphsContext {
    revlog: Vec<RevlogEntry>,
    cards: Vec<Card>,
    next_day_start: TimestampSecs,
    days_elapsed: u32,
    local_offset_secs: i64,
}

impl Collection {
    pub(crate) fn graph_data_for_search(
        &mut self,
        search: &str,
        days: u32,
    ) -> Result<anki_proto::stats::GraphsResponse> {
        let guard = self.search_cards_into_table(search, SortMode::NoOrder)?;
        let all = search.trim().is_empty();
        guard.col.graph_data(all, days)
    }

    fn graph_data(&mut self, all: bool, days: u32) -> Result<anki_proto::stats::GraphsResponse> {
        let timing = self.timing_today()?;
        let revlog_start = if days > 0 {
            timing
                .next_day_at
                .adding_secs(-(((days as i64) + 1) * 86_400))
        } else {
            TimestampSecs(0)
        };
        let offset = self.local_utc_offset_for_user()?;
        let local_offset_secs = offset.local_minus_utc() as i64;
        let revlog = if all {
            self.storage.get_all_revlog_entries(revlog_start)?
        } else {
            self.storage
                .get_revlog_entries_for_searched_cards_after_stamp(revlog_start)?
        };
        let ctx = GraphsContext {
            revlog,
            days_elapsed: timing.days_elapsed,
            cards: self.storage.all_searched_cards()?,
            next_day_start: timing.next_day_at,
            local_offset_secs,
        };
        let (eases, difficulty) = ctx.eases();
        let resp = anki_proto::stats::GraphsResponse {
            added: Some(ctx.added_days()),
            reviews: Some(ctx.review_counts_and_times()),
            true_retention: Some(ctx.calculate_true_retention()),
            future_due: Some(ctx.future_due()),
            intervals: Some(ctx.intervals()),
            stability: Some(ctx.stability()),
            eases: Some(eases),
            difficulty: Some(difficulty),
            today: Some(ctx.today()),
            hours: Some(ctx.hours()),
            buttons: Some(ctx.buttons()),
            card_counts: Some(ctx.card_counts()),
            rollover_hour: self.rollover_for_current_scheduler()? as u32,
            retrievability: Some(ctx.retrievability()),
            fsrs: self.get_config_bool(BoolKey::Fsrs),
        };
        Ok(resp)
    }

    pub(crate) fn get_graph_preferences(&self) -> anki_proto::stats::GraphPreferences {
        anki_proto::stats::GraphPreferences {
            calendar_first_day_of_week: self.get_first_day_of_week() as i32,
            card_counts_separate_inactive: self
                .get_config_bool(BoolKey::CardCountsSeparateInactive),
            browser_links_supported: true,
            future_due_show_backlog: self.get_config_bool(BoolKey::FutureDueShowBacklog),
        }
    }

    pub(crate) fn set_graph_preferences(
        &mut self,
        prefs: anki_proto::stats::GraphPreferences,
    ) -> Result<()> {
        self.set_first_day_of_week(match prefs.calendar_first_day_of_week {
            1 => Weekday::Monday,
            5 => Weekday::Friday,
            6 => Weekday::Saturday,
            _ => Weekday::Sunday,
        })?;
        self.set_config_bool_inner(
            BoolKey::CardCountsSeparateInactive,
            prefs.card_counts_separate_inactive,
        )?;
        self.set_config_bool_inner(BoolKey::FutureDueShowBacklog, prefs.future_due_show_backlog)?;
        Ok(())
    }
}
