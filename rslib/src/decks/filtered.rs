// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use strum::IntoEnumIterator;

use super::{DeckCommon, FilteredDeck, FilteredSearchOrder, FilteredSearchTerm};
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
        filt.preview_delay = 10;
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

impl FilteredSearchOrder {
    pub fn labels(tr: &I18n) -> Vec<String> {
        FilteredSearchOrder::iter().map(|v| v.label(tr)).collect()
    }

    fn label(self, tr: &I18n) -> String {
        match self {
            FilteredSearchOrder::OldestReviewedFirst => tr.decks_oldest_seen_first(),
            FilteredSearchOrder::Random => tr.decks_random(),
            FilteredSearchOrder::IntervalsAscending => tr.decks_increasing_intervals(),
            FilteredSearchOrder::IntervalsDescending => tr.decks_decreasing_intervals(),
            FilteredSearchOrder::Lapses => tr.decks_most_lapses(),
            FilteredSearchOrder::Added => tr.decks_order_added(),
            FilteredSearchOrder::Due => tr.decks_order_due(),
            FilteredSearchOrder::ReverseAdded => tr.decks_latest_added_first(),
            FilteredSearchOrder::DuePriority => tr.decks_relative_overdueness(),
        }
        .into()
    }
}
