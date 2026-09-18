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
            "extract_fsrs_retrievability(c.data, case when c.odue !=0 then c.odue else c.due end, ivl, {today}, {next_day_at}, {now}) {order}"
        )
    } else {
        String::new()
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::prelude::*;

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

    #[test]
    fn simple_orders_map_to_their_sql_fragment_with_fnvhash_suffix() {
        // one contract: each order variant without dynamic timing produces a
        // fixed fragment followed by the tie-breaking fnvhash and limit clause.
        let cases = [
            (FilteredSearchOrder::Random, "random()"),
            (FilteredSearchOrder::IntervalsAscending, "ivl"),
            (FilteredSearchOrder::IntervalsDescending, "ivl desc"),
            (FilteredSearchOrder::Lapses, "lapses desc"),
            (FilteredSearchOrder::Added, "n.id, c.ord"),
            (FilteredSearchOrder::ReverseAdded, "n.id desc, c.ord asc"),
            (
                FilteredSearchOrder::OldestReviewedFirst,
                "(select max(id) from revlog where cid=c.id)",
            ),
        ];
        for (order, fragment) in cases {
            let got = order_and_limit_for_search(&term(order, 10), timing(), false);
            let expected = format!("{fragment}, fnvhash(c.id, c.mod) limit 10");
            assert_eq!(got, expected, "order {order:?}");
        }
    }

    #[test]
    fn limit_is_interpolated_into_the_clause() {
        let got =
            order_and_limit_for_search(&term(FilteredSearchOrder::Random, 30), timing(), false);
        assert_eq!(got, "random(), fnvhash(c.id, c.mod) limit 30");
    }

    #[test]
    fn due_order_builds_case_expression_using_today_and_now() {
        let got = order_and_limit_for_search(&term(FilteredSearchOrder::Due, 5), timing(), false);
        assert_eq!(
            got,
            format!(
                "(case when c.due > 1000000000 then due else (due - {TODAY}) * 86400 + {NOW} end), \
                 c.ord, fnvhash(c.id, c.mod) limit 5"
            )
        );
    }

    #[test]
    fn relative_overdueness_inlines_fsrs_fragment_regardless_of_fsrs_flag() {
        let got = order_and_limit_for_search(
            &term(FilteredSearchOrder::RelativeOverdueness, 5),
            timing(),
            false,
        );
        assert_eq!(
            got,
            format!(
                "extract_fsrs_relative_retrievability(data, case when odue !=0 then odue else due \
                 end, ivl, {TODAY}, {NEXT_DAY_AT}, {NOW}) asc, fnvhash(c.id, c.mod) limit 5"
            )
        );
    }

    #[test]
    fn retrievability_orders_emit_fsrs_clause_when_fsrs_enabled() {
        let asc = order_and_limit_for_search(
            &term(FilteredSearchOrder::RetrievabilityAscending, 5),
            timing(),
            true,
        );
        assert_eq!(
            asc,
            format!(
                "extract_fsrs_retrievability(c.data, case when c.odue !=0 then c.odue else c.due \
                 end, ivl, {TODAY}, {NEXT_DAY_AT}, {NOW}) asc, fnvhash(c.id, c.mod) limit 5"
            )
        );

        let desc = order_and_limit_for_search(
            &term(FilteredSearchOrder::RetrievabilityDescending, 5),
            timing(),
            true,
        );
        assert!(
            desc.starts_with("extract_fsrs_retrievability(") && desc.contains(") desc,"),
            "descending order should use the desc sort direction, got: {desc}"
        );
    }

    #[test]
    fn retrievability_order_leaves_empty_leading_term_when_fsrs_disabled() {
        // build_retrievability_query returns an empty string without FSRS, so the
        // clause degenerates to just the fnvhash tie-breaker and limit.
        let got = order_and_limit_for_search(
            &term(FilteredSearchOrder::RetrievabilityAscending, 5),
            timing(),
            false,
        );
        assert_eq!(got, ", fnvhash(c.id, c.mod) limit 5");
    }

    #[test]
    fn build_retrievability_query_is_empty_without_fsrs() {
        assert_eq!(
            build_retrievability_query(false, TODAY, NEXT_DAY_AT, NOW, SqlSortOrder::Ascending),
            ""
        );
    }
}
