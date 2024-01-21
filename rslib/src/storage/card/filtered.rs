// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::card::CardQueue;
use crate::decks::FilteredSearchOrder;
use crate::decks::FilteredSearchTerm;
use crate::scheduler::timing::SchedTimingToday;

pub(crate) fn order_and_limit_for_search(
    term: &FilteredSearchTerm,
    timing: SchedTimingToday,
    fsrs: bool,
) -> String {
    let temp_string;
    let today = timing.days_elapsed;
    let order = match term.order() {
        FilteredSearchOrder::OldestReviewedFirst => "(select max(id) from revlog where cid=c.id)",
        FilteredSearchOrder::Random => "random()",
        FilteredSearchOrder::IntervalsAscending => "ivl",
        FilteredSearchOrder::IntervalsDescending => "ivl desc",
        FilteredSearchOrder::Lapses => "lapses desc",
        FilteredSearchOrder::Added => "n.id, c.ord",
        FilteredSearchOrder::ReverseAdded => "n.id desc, c.ord asc",
        FilteredSearchOrder::Due => {
            let current_timestamp = timing.now.0;
            temp_string = format!(
                "(case when c.due > 1000000000 then due else (due - {today}) * 86400 + {current_timestamp} end), c.ord");
            &temp_string
        }
        FilteredSearchOrder::DuePriority => {
            let next_day_at = timing.next_day_at.0;
            temp_string = if fsrs {
                format!(
                    "extract_fsrs_relative_overdueness(c.data, due, {today}, ivl, {next_day_at}) desc"
                )
            } else {
                format!(
                    "
(case when queue={rev_queue} and due <= {today}
then (ivl / cast({today}-due+0.001 as real)) else 100000+due end)",
                    rev_queue = CardQueue::Review as i8,
                    today = today
                )
            };
            &temp_string
        }
    };

    format!("{} limit {}", order, term.limit)
}
