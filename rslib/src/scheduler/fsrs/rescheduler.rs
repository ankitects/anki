// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;

use chrono::Datelike;

use crate::prelude::*;
use crate::scheduler::states::fuzz::constrained_fuzz_bounds;
use crate::scheduler::states::load_balancer::build_easy_days_percentages;
use crate::scheduler::states::load_balancer::calculate_easy_days_modifiers;
use crate::scheduler::states::load_balancer::select_weighted_interval;
use crate::scheduler::states::load_balancer::EasyDay;
use crate::scheduler::states::load_balancer::LoadBalancerInterval;

pub struct Rescheduler {
    today: i32,
    next_day_at: TimestampSecs,
    due_cnt_per_day_by_preset: HashMap<DeckConfigId, HashMap<i32, usize>>,
    due_today_by_preset: HashMap<DeckConfigId, usize>,
    reviewed_today_by_preset: HashMap<DeckConfigId, usize>,
    easy_days_percentages_by_preset: HashMap<DeckConfigId, [EasyDay; 7]>,
}

impl Rescheduler {
    pub fn new(col: &mut Collection) -> Result<Self> {
        let timing = col.timing_today()?;
        let deck_stats = col.storage.get_deck_due_counts()?;
        let deck_map = col.storage.get_decks_map()?;
        let did_to_dcid = deck_map
            .values()
            .filter_map(|deck| Some((deck.id, deck.config_id()?)))
            .collect::<HashMap<_, _>>();

        let mut due_cnt_per_day_by_preset: HashMap<DeckConfigId, HashMap<i32, usize>> =
            HashMap::new();
        for (did, due_date, count) in deck_stats {
            let deck_config_id = did_to_dcid.get(&did).or_not_found(did)?;
            due_cnt_per_day_by_preset
                .entry(*deck_config_id)
                .or_default()
                .entry(due_date)
                .and_modify(|e| *e += count)
                .or_insert(count);
        }

        let today = timing.days_elapsed as i32;
        let due_today_by_preset = due_cnt_per_day_by_preset
            .iter()
            .map(|(deck_config_id, config_dues)| {
                let due_today = config_dues
                    .iter()
                    .filter(|(&due, _)| due <= today)
                    .map(|(_, &count)| count)
                    .sum();
                (*deck_config_id, due_today)
            })
            .collect();

        let next_day_at = timing.next_day_at;
        let reviewed_stats = col.storage.studied_today_by_deck(timing.next_day_at)?;
        let mut reviewed_today_by_preset: HashMap<DeckConfigId, usize> = HashMap::new();
        for (did, count) in reviewed_stats {
            if let Some(&deck_config_id) = &did_to_dcid.get(&did) {
                *reviewed_today_by_preset.entry(deck_config_id).or_default() += count;
            }
        }

        let easy_days_percentages_by_preset =
            build_easy_days_percentages(col.storage.get_deck_config_map()?)?;

        Ok(Self {
            today,
            next_day_at,
            due_cnt_per_day_by_preset,
            due_today_by_preset,
            reviewed_today_by_preset,
            easy_days_percentages_by_preset,
        })
    }

    pub fn update_due_cnt_per_day(
        &mut self,
        due_before: i32,
        due_after: i32,
        deck_config_id: DeckConfigId,
    ) {
        if let Some(counts) = self.due_cnt_per_day_by_preset.get_mut(&deck_config_id) {
            if let Some(count) = counts.get_mut(&due_before) {
                *count -= 1;
            }
            *counts.entry(due_after).or_default() += 1;
        }

        if due_before <= self.today && due_after > self.today {
            if let Some(count) = self.due_today_by_preset.get_mut(&deck_config_id) {
                *count -= 1;
            }
        }
        if due_before > self.today && due_after <= self.today {
            *self.due_today_by_preset.entry(deck_config_id).or_default() += 1;
        }
    }

    fn due_today(&self, deck_config_id: DeckConfigId) -> usize {
        *self.due_today_by_preset.get(&deck_config_id).unwrap_or(&0)
    }

    fn reviewed_today(&self, deck_config_id: DeckConfigId) -> usize {
        *self
            .reviewed_today_by_preset
            .get(&deck_config_id)
            .unwrap_or(&0)
    }

    /// Returns the number of days in the future that a card will be scheduled
    /// for. None if it should not be rescheduled (e.g. if it is overdue).
    pub fn find_interval(
        &self,
        interval: f32,
        minimum_interval: u32,
        maximum_interval: u32,
        days_elapsed: u32,
        deckconfig_id: DeckConfigId,
        fuzz_seed: Option<u64>,
    ) -> Option<u32> {
        let (before_days, after_days) =
            constrained_fuzz_bounds(interval, minimum_interval, maximum_interval);

        // Don't reschedule the card when it's overdue
        if after_days < days_elapsed {
            return None;
        }
        // Don't reschedule the card to the past
        let before_days = before_days.max(days_elapsed);

        // Generate possible intervals and their review counts
        let possible_intervals: Vec<u32> = (before_days..=after_days).collect();
        let review_counts: Vec<usize> = possible_intervals
            .iter()
            .map(|&ivl| {
                if ivl > days_elapsed {
                    let check_due = self.today + ivl as i32 - days_elapsed as i32;
                    *self
                        .due_cnt_per_day_by_preset
                        .get(&deckconfig_id)
                        .and_then(|counts| counts.get(&check_due))
                        .unwrap_or(&0)
                } else {
                    // today's workload is the sum of backlogs, cards due today and cards reviewed
                    // today
                    self.due_today(deckconfig_id) + self.reviewed_today(deckconfig_id)
                }
            })
            .collect();
        let weekdays: Vec<usize> = possible_intervals
            .iter()
            .map(|&ivl| {
                self.next_day_at
                    .adding_secs(days_elapsed as i64 * -86400)
                    .adding_secs((ivl - 1) as i64 * 86400)
                    .local_datetime()
                    .unwrap()
                    .weekday()
                    .num_days_from_monday() as usize
            })
            .collect();

        let easy_days_load = self.easy_days_percentages_by_preset.get(&deckconfig_id)?;
        let easy_days_modifier =
            calculate_easy_days_modifiers(easy_days_load, &weekdays, &review_counts);

        let intervals =
            possible_intervals
                .iter()
                .enumerate()
                .map(|(interval_index, &target_interval)| LoadBalancerInterval {
                    target_interval,
                    review_count: review_counts[interval_index],
                    sibling_modifier: 1.0,
                    easy_days_modifier: easy_days_modifier[interval_index],
                });

        select_weighted_interval(intervals, fuzz_seed)
    }
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;

    use chrono::Timelike;

    use super::*;
    use crate::card::CardQueue;
    use crate::card::CardType;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;
    use crate::tests::NoteAdder;

    fn rescheduler_for_due_counts(
        deck_config_id: DeckConfigId,
        due_counts: &[(i32, usize)],
        reviewed_today: usize,
        easy_days: [EasyDay; 7],
        today: i32,
    ) -> Rescheduler {
        let due_counts_map = due_counts
            .iter()
            .fold(HashMap::new(), |mut acc, &(due, count)| {
                *acc.entry(due).or_default() += count;
                acc
            });
        let due_today = due_counts_map
            .iter()
            .filter(|(due, _)| **due <= today)
            .map(|(_, count)| *count)
            .sum();

        Rescheduler {
            today,
            next_day_at: TimestampSecs(0),
            due_cnt_per_day_by_preset: HashMap::from([(deck_config_id, due_counts_map.clone())]),
            due_today_by_preset: HashMap::from([(deck_config_id, due_today)]),
            reviewed_today_by_preset: HashMap::from([(deck_config_id, reviewed_today)]),
            easy_days_percentages_by_preset: HashMap::from([(deck_config_id, easy_days)]),
        }
    }

    // Invariant: overdue cards are left alone.
    #[test]
    fn rescheduler_find_interval_overdue_card_is_not_rescheduled() {
        let deck_config_id = DeckConfigId(1);
        let rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(5, 1), (6, 1)],
            0,
            [EasyDay::Normal; 7],
            0,
        );

        assert_eq!(
            rescheduler.find_interval(10.0, 1, 10, 20, deck_config_id, Some(1)),
            None
        );
        assert_eq!(
            rescheduler.find_interval(10.0, 1, 11, 20, deck_config_id, Some(1)),
            None
        );
    }

    // Historical regression: fuzz could move an interval backwards
    // during rescheduling. Also checks the invariant that the result always
    // lies within the min/max interval bounds.
    #[test]
    fn rescheduler_find_interval_never_goes_to_the_past_and_stays_in_bounds() {
        let deck_config_id = DeckConfigId(1);
        let rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(5, 1), (6, 1), (7, 1)],
            0,
            [EasyDay::Normal; 7],
            0,
        );

        // (interval, minimum_interval, maximum_interval, days_elapsed)
        let cases = [
            (10.0, 1, 11, 5),
            (4.0, 1, 3, 1),
            (50.0, 10, 90, 5),
            // minimum > maximum
            (10.0, 50, 5, 5),
            (5.0, 1, 5, 5),
            // very large interval, clamped to the maximum
            (1.0e9, 1, 36500, 0),
            (1.0e9, 1, 36500, 20000),
            // very small interval, clamped to the minimum
            (0.1, 1, 36500, 0),
            (0.0, 1, 36500, 0),
            (0.1, 3, 36500, 2),
            // overdue: nothing to reschedule
            (10.0, 1, 11, 100),
        ];

        // Each seed picks a different candidate day, so check many of them.
        for (interval, minimum_interval, maximum_interval, days_elapsed) in cases {
            for seed in 0..50 {
                let result = rescheduler.find_interval(
                    interval,
                    minimum_interval,
                    maximum_interval,
                    days_elapsed,
                    deck_config_id,
                    Some(seed),
                );
                let context = format!(
                    "interval={interval}, minimum={minimum_interval}, \
                     maximum={maximum_interval}, elapsed={days_elapsed}, seed={seed}"
                );

                // The maximum wins over the minimum when they conflict.
                let lower = minimum_interval.min(maximum_interval);
                let upper = maximum_interval;
                if upper < days_elapsed {
                    assert_eq!(result, None, "overdue card was rescheduled: {context}");
                    continue;
                }
                let result = result.unwrap_or_else(|| panic!("expected interval: {context}"));

                assert!(
                    result >= days_elapsed,
                    "interval moved into the past: {result} < {days_elapsed} ({context})"
                );
                assert!(
                    (lower..=upper).contains(&result),
                    "interval {result} out of bounds [{lower}, {upper}] ({context})"
                );
            }
        }
    }

    // Invariant: the same fuzz seed and inputs give the same result.
    #[test]
    fn rescheduler_find_interval_is_deterministic_for_a_seed() {
        let deck_config_id = DeckConfigId(1);
        let rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(5, 1), (6, 1), (7, 1)],
            0,
            [EasyDay::Normal; 7],
            0,
        );

        let first = rescheduler
            .find_interval(10.0, 1, 11, 5, deck_config_id, Some(99))
            .unwrap();
        for _ in 0..10 {
            let second = rescheduler
                .find_interval(10.0, 1, 11, 5, deck_config_id, Some(99))
                .unwrap();
            assert_eq!(first, second);
        }
    }

    // Characterization of the load balancer: documents current
    // behaviour, and may need updating if the weighting is retuned.
    #[test]
    fn rescheduler_find_interval_prefers_lower_load_day() {
        let deck_config_id = DeckConfigId(1);
        let rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(5, 100), (6, 1)],
            0,
            [EasyDay::Normal; 7],
            0,
        );

        let mut high_load_count = 0;
        let mut low_load_count = 0;
        let n = 100;

        for seed in 0..n {
            let selected = rescheduler
                .find_interval(5.5, 5, 6, 0, deck_config_id, Some(seed))
                .unwrap();
            match selected {
                5 => high_load_count += 1,
                6 => low_load_count += 1,
                other => panic!("unexpected interval: {other}"),
            }
        }

        assert!(
            low_load_count > high_load_count,
            "expected the less busy day to be chosen more often \
             (low: {low_load_count}, high: {high_load_count})"
        );
    }

    // Characterization of the load balancer's easy days handling:
    // documents current behaviour, and may need updating if it is retuned.
    #[test]
    fn rescheduler_find_interval_avoids_zero_percent_easy_day_when_other_day_available() {
        let deck_config_id = DeckConfigId(1);
        let blocked_interval = 5;
        // The weekday of an interval depends on the local timezone, so derive it
        // the same way `find_interval` does rather than hardcoding one.
        let blocked_weekday = TimestampSecs(0)
            .adding_secs((blocked_interval - 1) as i64 * 86400)
            .local_datetime()
            .unwrap()
            .weekday()
            .num_days_from_monday() as usize;
        let mut easy_days = [EasyDay::Normal; 7];
        easy_days[blocked_weekday] = EasyDay::Minimum;
        let rescheduler =
            rescheduler_for_due_counts(deck_config_id, &[(5, 1), (6, 1)], 0, easy_days, 0);

        let mut blocked_day_count = 0;
        let mut preferred_day_count = 0;
        let n = 100;

        for seed in 0..n {
            let selected = rescheduler
                .find_interval(5.5, 5, 6, 0, deck_config_id, Some(seed))
                .unwrap();
            match selected {
                5 => blocked_day_count += 1,
                6 => preferred_day_count += 1,
                other => panic!("unexpected interval: {other}"),
            }
        }

        assert_eq!(preferred_day_count, n);
        assert_eq!(blocked_day_count, 0);
    }

    // Historical regression: a missing preset used to panic.
    #[test]
    fn rescheduler_find_interval_returns_none_for_missing_preset() {
        let deck_config_id = DeckConfigId(1);
        let rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(5, 1), (6, 1)],
            0,
            [EasyDay::Normal; 7],
            0,
        );

        assert!(rescheduler
            .find_interval(10.0, 1, 11, 5, DeckConfigId(999), Some(1))
            .is_none());
    }

    // Invariant: counts move between the right days, and `due_today` only
    // changes when a card crosses `today`.
    #[test]
    fn rescheduler_update_due_cnt_per_day_tracks_today_boundaries() {
        let deck_config_id = DeckConfigId(1);
        let due_before = 5;
        let due_after = 8;
        let mut rescheduler = rescheduler_for_due_counts(
            deck_config_id,
            &[(due_before, 1), (due_after, 1)],
            0,
            [EasyDay::Normal; 7],
            due_before,
        );

        assert_eq!(rescheduler.due_today_by_preset[&deck_config_id], 1);
        rescheduler.update_due_cnt_per_day(due_before, due_after, deck_config_id);
        assert_eq!(
            rescheduler.due_cnt_per_day_by_preset[&deck_config_id][&due_before],
            0
        );
        assert_eq!(
            rescheduler.due_cnt_per_day_by_preset[&deck_config_id][&due_after],
            2
        );
        assert_eq!(rescheduler.due_today_by_preset[&deck_config_id], 0);

        rescheduler.update_due_cnt_per_day(due_after, due_before, deck_config_id);
        assert_eq!(
            rescheduler.due_cnt_per_day_by_preset[&deck_config_id][&due_after],
            1
        );
        assert_eq!(
            rescheduler.due_cnt_per_day_by_preset[&deck_config_id][&due_before],
            1
        );
        assert_eq!(rescheduler.due_today_by_preset[&deck_config_id], 1);
    }

    // Invariant: `due_today` only changes when a card crosses `today`; moves
    // that stay on the same side of it leave it alone.
    #[test]
    fn rescheduler_update_due_cnt_per_day_only_changes_due_today_when_crossing_today() {
        let deck_config_id = DeckConfigId(1);
        let today = 5;
        let (overdue, future, later) = (3, 8, 9);

        // (due_before, due_after, expected change in due_today)
        for (due_before, due_after, due_today_delta) in [
            (future, later, 0),
            (overdue, today, 0),
            (today, overdue, 0),
            (overdue, future, -1),
            (today, future, -1),
            (future, today, 1),
            (future, overdue, 1),
        ] {
            let mut rescheduler = rescheduler_for_due_counts(
                deck_config_id,
                &[(overdue, 1), (today, 1), (future, 1), (later, 1)],
                0,
                [EasyDay::Normal; 7],
                today,
            );
            assert_eq!(rescheduler.due_today_by_preset[&deck_config_id], 2);

            rescheduler.update_due_cnt_per_day(due_before, due_after, deck_config_id);

            let context = format!("{due_before} -> {due_after}");
            assert_eq!(
                rescheduler.due_today_by_preset[&deck_config_id] as i32,
                2 + due_today_delta,
                "unexpected due_today for {context}"
            );
            let counts = &rescheduler.due_cnt_per_day_by_preset[&deck_config_id];
            assert_eq!(counts[&due_before], 0, "due_before count for {context}");
            assert_eq!(counts[&due_after], 2, "due_after count for {context}");
        }
    }

    /// Makes the day rollover 12 hours away, so that the day cannot change
    /// while a test is running, however close to a rollover it is started.
    fn collection_with_stable_day() -> Result<Collection> {
        let mut col = Collection::new();
        let local_hour = TimestampSecs::now().local_datetime()?.hour();
        col.set_rollover_for_current_scheduler(((local_hour + 12) % 24) as u8)?;
        Ok(col)
    }

    fn add_review_card(col: &mut Collection, deck_id: DeckId, due: i32) -> Result<Card> {
        let mut note = NoteAdder::basic(col).note();
        col.add_note(&mut note, deck_id)?;
        let mut card = col.storage.all_cards_of_note(note.id)?.pop().unwrap();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.due = due;
        col.storage.update_card(&card)?;
        Ok(card)
    }

    /// Revlog ids are timestamps and must be unique, so the caller picks them.
    fn add_review(col: &mut Collection, card: &Card, id: TimestampMillis) -> Result<()> {
        col.storage.add_revlog_entry(
            &RevlogEntry {
                id: id.into(),
                cid: card.id,
                usn: Usn(0),
                button_chosen: 3,
                interval: 1,
                last_interval: 0,
                ease_factor: 2500,
                taken_millis: 1000,
                review_kind: RevlogReviewKind::Review,
            },
            false,
        )?;
        Ok(())
    }

    // This test will need to be edited if the rescheduler no longer fetches
    // due/reviewed counts from its preset.
    #[test]
    fn new_loads_correct_values_from_collection() -> Result<()> {
        let mut col = collection_with_stable_day()?;
        let today = col.timing_today()?.days_elapsed as i32;

        // Two decks share the default preset, a third one has its own.
        let shared_preset = DeckConfigId(1);
        let mut other_config = DeckConfig {
            name: "Other preset".into(),
            ..Default::default()
        };
        col.add_or_update_deck_config(&mut other_config)?;
        let other_preset = other_config.id;
        let deck_a = DeckId(1);
        let deck_b = col.get_or_create_normal_deck("B")?.id;
        let mut deck_c = col.get_or_create_normal_deck("C")?;
        deck_c.normal_mut()?.config_id = other_preset.0;
        col.update_deck(&mut deck_c)?;
        let deck_c = deck_c.id;

        let overdue = add_review_card(&mut col, deck_a, today - 3)?;
        add_review_card(&mut col, deck_a, today)?;
        add_review_card(&mut col, deck_b, today)?;
        add_review_card(&mut col, deck_b, today + 5)?;
        add_review_card(&mut col, deck_c, today + 5)?;
        let c_today = add_review_card(&mut col, deck_c, today + 1)?;
        // Not a review card, so must not be counted as due.
        let mut new_note = NoteAdder::basic(&mut col).note();
        col.add_note(&mut new_note, deck_a)?;

        // Two reviews in the shared preset (one card reviewed twice), one in the
        // other.
        let now = TimestampMillis::now().0;
        let review_id = |offset: i64| TimestampMillis(now - offset);
        add_review(&mut col, &overdue, review_id(1))?;
        add_review(&mut col, &overdue, review_id(2))?;
        let b_card = add_review_card(&mut col, deck_b, today + 5)?;
        add_review(&mut col, &b_card, review_id(3))?;
        add_review(&mut col, &c_today, review_id(4))?;

        let rescheduler = Rescheduler::new(&mut col)?;

        assert_eq!(rescheduler.today, today);
        // 3 overdue/today in the shared preset, plus the extra future card of B.
        assert_eq!(
            rescheduler.due_cnt_per_day_by_preset[&shared_preset],
            HashMap::from([(today - 3, 1), (today, 2), (today + 5, 2)])
        );
        assert_eq!(
            rescheduler.due_cnt_per_day_by_preset[&other_preset],
            HashMap::from([(today + 5, 1), (today + 1, 1)])
        );
        assert_eq!(rescheduler.due_today_by_preset[&shared_preset], 3);
        assert_eq!(rescheduler.due_today_by_preset[&other_preset], 0);
        assert_eq!(rescheduler.reviewed_today_by_preset[&shared_preset], 2);
        assert_eq!(rescheduler.reviewed_today_by_preset[&other_preset], 1);

        Ok(())
    }
}
