// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use strum::IntoEnumIterator;

use super::DeckCommon;
use super::FilteredDeck;
use super::FilteredSearchOrder;
use super::FilteredSearchTerm;
use crate::prelude::*;

impl Deck {
    pub fn new_filtered() -> Deck {
        let mut filt = FilteredDeck::default();
        filt.search_terms.push(FilteredSearchTerm {
            search: "".into(),
            limit: 100,
            order: FilteredSearchOrder::Random as i32,
        });
        filt.search_terms.push(FilteredSearchTerm {
            search: "".into(),
            limit: 20,
            order: FilteredSearchOrder::Due as i32,
        });
        filt.preview_again_secs = 60;
        filt.preview_hard_secs = 600;
        filt.reschedule = true;
        Deck {
            id: DeckId(0),
            name: NativeDeckName::from_native_str(""),
            mtime_secs: TimestampSecs(0),
            usn: Usn(0),
            common: DeckCommon {
                study_collapsed: true,
                browser_collapsed: true,
                ..Default::default()
            },
            kind: DeckKind::Filtered(filt),
        }
    }

    pub(crate) fn is_filtered(&self) -> bool {
        matches!(self.kind, DeckKind::Filtered(_))
    }
}

pub fn search_order_labels(tr: &I18n) -> Vec<String> {
    FilteredSearchOrder::iter()
        .map(|v| search_order_label(v, tr))
        .collect()
}

fn search_order_label(order: FilteredSearchOrder, tr: &I18n) -> String {
    match order {
        FilteredSearchOrder::OldestReviewedFirst => tr.decks_oldest_seen_first(),
        FilteredSearchOrder::Random => tr.decks_random(),
        FilteredSearchOrder::IntervalsAscending => tr.decks_increasing_intervals(),
        FilteredSearchOrder::IntervalsDescending => tr.decks_decreasing_intervals(),
        FilteredSearchOrder::Lapses => tr.decks_most_lapses(),
        FilteredSearchOrder::Added => tr.decks_order_added(),
        FilteredSearchOrder::Due => tr.decks_order_due(),
        FilteredSearchOrder::ReverseAdded => tr.decks_latest_added_first(),
        FilteredSearchOrder::RetrievabilityAscending => {
            tr.deck_config_sort_order_retrievability_ascending()
        }
        FilteredSearchOrder::RetrievabilityDescending => {
            tr.deck_config_sort_order_retrievability_descending()
        }
        FilteredSearchOrder::RelativeOverdueness => tr.decks_relative_overdueness(),
    }
    .into()
}
