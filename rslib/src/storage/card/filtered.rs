// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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
        FilteredSearchOrder::RelativeOverdueness => {
            temp_string =
                format!("extract_fsrs_relative_retrievability(data, case when odue !=0 then odue else due end, ivl, {today}, {next_day_at}, {now}) asc");
            &temp_string
        }
    };

    format!("{order}, fnvhash(c.id, c.mod) limit {}", term.limit)
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
            "extract_fsrs_retrievability(c.data, case when c.odue !=0 then c.odue else c.due end, ivl, {today}, {next_day_at}, {now}) {order}"
        )
    } else {
        // A saved retrievability order can remain after FSRS is disabled. Match
        // the filtered-deck dialog's fallback instead of an empty SQL term.
        "random()".to_string()
    }
}

#[cfg(test)]
mod test {
    use rusqlite::params;

    use super::*;
    use crate::card::FsrsMemoryState;
    use crate::prelude::*;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;
    use crate::search::SortMode;
    use crate::tests::NoteAdder;

    const TODAY: u32 = 100;
    const NEXT_DAY_AT: i64 = 1_600_086_400;
    const NOW: i64 = 1_600_000_000;

    fn timing() -> SchedTimingToday {
        SchedTimingToday {
            now: TimestampSecs(NOW),
            days_elapsed: TODAY,
            next_day_at: TimestampSecs(NEXT_DAY_AT),
        }
    }

    fn term(order: FilteredSearchOrder, limit: u32) -> FilteredSearchTerm {
        FilteredSearchTerm {
            search: String::new(),
            limit,
            order: order as i32,
        }
    }

    /// Adds a single (basic) card and returns its id.
    fn add_card(col: &mut Collection, front: &str) -> CardId {
        let note = NoteAdder::basic(col).fields(&[front, "back"]).add(col);
        col.storage.all_cards_of_note(note.id).unwrap()[0].id
    }

    fn set_card_columns(col: &Collection, cid: CardId, ivl: i32, lapses: u32, due: i32) {
        col.storage
            .db
            .execute(
                "update cards set ivl = ?, lapses = ?, due = ? where id = ?",
                params![ivl, lapses, due, cid],
            )
            .unwrap();
    }

    fn add_review(col: &Collection, cid: CardId, id: i64) {
        let entry = RevlogEntry {
            id: RevlogId(id),
            cid,
            button_chosen: 3,
            review_kind: RevlogReviewKind::Review,
            ..Default::default()
        };
        col.storage.add_revlog_entry(&entry, false).unwrap();
    }

    /// Pulls cards in the order the filtered-deck builder would use.
    fn cards_in_order(
        col: &mut Collection,
        order: FilteredSearchOrder,
        limit: u32,
        fsrs: bool,
    ) -> Vec<CardId> {
        let clause = order_and_limit_for_search(&term(order, limit), timing(), fsrs);
        col.search_cards("", SortMode::Custom(clause)).unwrap()
    }

    #[test]
    fn cards_are_ordered_by_interval_ascending_and_descending() {
        let mut col = Collection::new();
        let small = add_card(&mut col, "small");
        let large = add_card(&mut col, "large");
        let mid = add_card(&mut col, "mid");
        set_card_columns(&col, small, 1, 0, 0);
        set_card_columns(&col, mid, 5, 0, 0);
        set_card_columns(&col, large, 10, 0, 0);

        assert_eq!(
            cards_in_order(&mut col, FilteredSearchOrder::IntervalsAscending, 10, false),
            vec![small, mid, large]
        );
        assert_eq!(
            cards_in_order(
                &mut col,
                FilteredSearchOrder::IntervalsDescending,
                10,
                false
            ),
            vec![large, mid, small]
        );
    }

    #[test]
    fn cards_are_ordered_by_descending_lapses() {
        let mut col = Collection::new();
        let none = add_card(&mut col, "none");
        let most = add_card(&mut col, "most");
        let some = add_card(&mut col, "some");
        set_card_columns(&col, none, 0, 0, 0);
        set_card_columns(&col, some, 0, 1, 0);
        set_card_columns(&col, most, 0, 3, 0);

        assert_eq!(
            cards_in_order(&mut col, FilteredSearchOrder::Lapses, 10, false),
            vec![most, some, none]
        );
    }

    #[test]
    fn cards_are_ordered_by_added_and_reverse_added() {
        let mut col = Collection::new();
        let first = add_card(&mut col, "first");
        let second = add_card(&mut col, "second");
        let third = add_card(&mut col, "third");

        assert_eq!(
            cards_in_order(&mut col, FilteredSearchOrder::Added, 10, false),
            vec![first, second, third]
        );
        assert_eq!(
            cards_in_order(&mut col, FilteredSearchOrder::ReverseAdded, 10, false),
            vec![third, second, first]
        );
    }

    #[test]
    fn due_order_compares_days_and_timestamps_chronologically() {
        let mut col = Collection::new();
        let late = add_card(&mut col, "late");
        let early = add_card(&mut col, "early");
        let middle = add_card(&mut col, "middle");
        // Yesterday, an hour from now, and tomorrow. Sorting raw due values
        // would incorrectly put tomorrow before the timestamp-based card.
        set_card_columns(&col, early, 0, 0, TODAY as i32 - 1);
        set_card_columns(&col, middle, 0, 0, (NOW + 3600) as i32);
        set_card_columns(&col, late, 0, 0, TODAY as i32 + 1);

        assert_eq!(
            cards_in_order(&mut col, FilteredSearchOrder::Due, 10, false),
            vec![early, middle, late]
        );
    }

    #[test]
    fn oldest_reviewed_cards_come_first() {
        let mut col = Collection::new();
        let reviewed_earlier = add_card(&mut col, "earlier");
        let reviewed_later = add_card(&mut col, "later");
        // The first-review order is the reverse of the last-review order.
        add_review(&col, reviewed_later, 1_000_000);
        add_review(&col, reviewed_earlier, 2_000_000);
        add_review(&col, reviewed_earlier, 3_000_000);
        add_review(&col, reviewed_later, 4_000_000);

        assert_eq!(
            cards_in_order(
                &mut col,
                FilteredSearchOrder::OldestReviewedFirst,
                10,
                false
            ),
            vec![reviewed_earlier, reviewed_later]
        );
    }

    #[test]
    fn limit_caps_the_number_of_returned_cards() {
        let mut col = Collection::new();
        let small = add_card(&mut col, "small");
        let mid = add_card(&mut col, "mid");
        let _large = add_card(&mut col, "large");
        set_card_columns(&col, small, 1, 0, 0);
        set_card_columns(&col, mid, 5, 0, 0);
        set_card_columns(&col, _large, 10, 0, 0);

        // only the two smallest intervals are pulled in
        assert_eq!(
            cards_in_order(&mut col, FilteredSearchOrder::IntervalsAscending, 2, false),
            vec![small, mid]
        );
    }

    #[test]
    fn random_order_returns_all_matching_cards() {
        let mut col = Collection::new();
        let mut expected: Vec<CardId> = ["one", "two", "three"]
            .into_iter()
            .map(|front| add_card(&mut col, front))
            .collect();
        expected.sort();

        let mut got = cards_in_order(&mut col, FilteredSearchOrder::Random, 10, false);
        got.sort();

        assert_eq!(got, expected);
    }

    #[test]
    fn fsrs_orders_sort_cards_by_retrievability() {
        let mut col = Collection::new();
        let low = add_card(&mut col, "low");
        let high = add_card(&mut col, "high");
        let medium = add_card(&mut col, "medium");
        // With the same elapsed time, greater stability means greater
        // retrievability. Equal desired retention preserves that ordering
        // for relative overdueness as well.
        for (id, stability) in [(low, 1.0), (medium, 10.0), (high, 100.0)] {
            let mut card = col.storage.get_card(id).unwrap().unwrap();
            card.memory_state = Some(FsrsMemoryState {
                stability,
                difficulty: 5.0,
            });
            card.last_review_time = Some(TimestampSecs(NOW - 10 * 86_400));
            card.desired_retention = Some(0.9);
            col.storage.update_card(&card).unwrap();
        }
        for (order, expected) in [
            (
                FilteredSearchOrder::RetrievabilityAscending,
                vec![low, medium, high],
            ),
            (
                FilteredSearchOrder::RetrievabilityDescending,
                vec![high, medium, low],
            ),
            (
                FilteredSearchOrder::RelativeOverdueness,
                vec![low, medium, high],
            ),
        ] {
            let cards = cards_in_order(&mut col, order, 10, true);
            assert_eq!(cards, expected, "order {order:?}");
        }
    }

    #[test]
    fn retrievability_orders_fall_back_to_random_without_fsrs() {
        let mut col = Collection::new();
        for front in ["one", "two", "three"] {
            add_card(&mut col, front);
        }
        for order in [
            FilteredSearchOrder::RetrievabilityAscending,
            FilteredSearchOrder::RetrievabilityDescending,
        ] {
            // would fail on the old empty-order SQL (leading comma)
            let cards = cards_in_order(&mut col, order, 2, false);
            assert_eq!(cards.len(), 2, "order {order:?}");
        }
    }
}
