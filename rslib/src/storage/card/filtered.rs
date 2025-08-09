// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::card::CardQueue;
use crate::decks::FilteredSearchOrder;
use crate::decks::FilteredSearchTerm;
use crate::scheduler::timing::SchedTimingToday;
use crate::storage::sqlite::SqlSortOrder;

pub(crate) fn order_and_limit_for_search(
    term: &FilteredSearchTerm,
    timing: SchedTimingToday,
    fsrs: bool,
) -> String {
    let temp_string;
    let today = timing.days_elapsed;
    let next_day_at = timing.next_day_at.0;
    let now = timing.now.0;
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
        FilteredSearchOrder::RetrievabilityAscending => {
            temp_string =
                build_retrievability_query(fsrs, today, next_day_at, now, SqlSortOrder::Ascending);
            &temp_string
        }
        FilteredSearchOrder::RetrievabilityDescending => {
            temp_string =
                build_retrievability_query(fsrs, today, next_day_at, now, SqlSortOrder::Descending);
            &temp_string
        }
    };

    format!("{}, fnvhash(c.id, c.mod) limit {}", order, term.limit)
}

fn build_retrievability_query(
    fsrs: bool,
    today: u32,
    next_day_at: i64,
    now: i64,
    order: SqlSortOrder,
) -> String {
    if fsrs {
        format!(
            "extract_fsrs_relative_retrievability(c.data, case when c.odue !=0 then c.odue else c.due end, {today}, ivl, {next_day_at}, {now}) {order}"
        )
    } else {
        format!(
            "
(case when queue={rev_queue} and due <= {today}
then (ivl / cast({today}-due+0.001 as real)) else 100000+due end) {order}",
            rev_queue = CardQueue::Review as i8,
            today = today
        )
    }
}
