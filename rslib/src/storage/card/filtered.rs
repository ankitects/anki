// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::CardQueue,
    decks::{FilteredSearchOrder, FilteredSearchTerm},
};

pub(crate) fn order_and_limit_for_search(term: &FilteredSearchTerm, today: u32) -> String {
    let temp_string;
    let order = match term.order() {
        FilteredSearchOrder::OldestReviewedFirst => "(select max(id) from revlog where cid=c.id)",
        FilteredSearchOrder::Random => "random()",
        FilteredSearchOrder::IntervalsAscending => "ivl",
        FilteredSearchOrder::IntervalsDescending => "ivl desc",
        FilteredSearchOrder::Lapses => "lapses desc",
        FilteredSearchOrder::Added => "n.id",
        FilteredSearchOrder::ReverseAdded => "n.id desc",
        FilteredSearchOrder::Due => "c.due, c.ord",
        FilteredSearchOrder::DuePriority => {
            temp_string = format!(
                "
(case when queue={rev_queue} and due <= {today}
then (ivl / cast({today}-due+0.001 as real)) else 100000+due end)",
                rev_queue = CardQueue::Review as i8,
                today = today
            );
            &temp_string
        }
    };

    format!("{} limit {}", order, term.limit)
}
