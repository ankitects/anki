// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::card::CardQueue;
use crate::decks::FilteredSearchOrder;
use crate::decks::FilteredSearchTerm;

pub(crate) fn order_and_limit_for_search(
    term: &FilteredSearchTerm,
    today: u32,
    current_timestamp: i64,
    fsrs: bool,
) -> String {
    let temp_string;
    let order = match term.order() {
        FilteredSearchOrder::OldestReviewedFirst => "(select max(id) from revlog where cid=c.id)",
        FilteredSearchOrder::Random => "random()",
        FilteredSearchOrder::IntervalsAscending => "ivl",
        FilteredSearchOrder::IntervalsDescending => "ivl desc",
        FilteredSearchOrder::Lapses => "lapses desc",
        FilteredSearchOrder::Added => "n.id, c.ord",
        FilteredSearchOrder::ReverseAdded => "n.id desc",
        FilteredSearchOrder::Due => {
            temp_string = format!(
                "(case when c.due > 1000000000 then due else (due - {today}) * 86400 + {current_timestamp} end), c.ord");
            &temp_string
        }
        FilteredSearchOrder::DuePriority => {
            temp_string = if fsrs {
                format!("extract_fsrs_relative_overdueness(c.data, due, {today}, ivl) desc")
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
