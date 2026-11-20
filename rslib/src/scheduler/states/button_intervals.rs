// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Shared interval handling for FSRS-7 and external schedulers such as
//! RWKV-Curve.

use super::fsrs_interval_as_secs;
use super::fuzz::minimum_review_fuzz_interval;
use super::StateContext;

/// When same-day reviews and learning queues are enabled, intervals below
/// 12 hours use the intraday queue. Intervals at or above this boundary are
/// stored in whole days.
pub(crate) const SUB_DAY_LIMIT_DAYS: f32 = 0.5;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum ButtonInterval {
    Secs(u32),
    Days { days: u32, fuzz_delta_days: i32 },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum DayRule {
    Review { previous_interval: u32 },
    Graduating,
}

/// Resolve the four unrounded answer intervals in Again, Hard, Good, Easy
/// order. `None` means a configured learning step decides that button.
pub(crate) fn button_intervals(
    ctx: &StateContext,
    unrounded: [Option<f32>; 4],
    rule: DayRule,
) -> [Option<ButtonInterval>; 4] {
    let sub_day_allowed = ctx.fsrs_uses_short_term_learning_queue();
    let mut previous_secs = 0;
    let mut previous_days: Option<u32> = None;
    let mut out = [None; 4];

    for (index, interval) in unrounded.into_iter().enumerate() {
        let Some(interval) = interval else {
            continue;
        };
        if sub_day_allowed && interval < SUB_DAY_LIMIT_DAYS {
            let secs =
                fsrs_interval_as_secs(interval, ctx.fsrs_minimum_interval_secs).max(previous_secs);
            previous_secs = secs;
            out[index] = Some(ButtonInterval::Secs(secs));
            continue;
        }

        let floor = previous_days.map_or(1, |days| days + 1);
        let (days, fuzz_delta_days) = match rule {
            DayRule::Review { .. } if index == 0 => {
                let (minimum, maximum) =
                    ctx.min_and_max_review_intervals(ctx.minimum_lapse_interval.max(floor));
                let days = interval
                    .clamp(minimum as f32, maximum as f32)
                    .round()
                    .max(1.0) as u32;
                (days, 0)
            }
            DayRule::Review { previous_interval } => {
                let minimum = minimum_review_fuzz_interval(
                    interval,
                    previous_interval,
                    ctx.maximum_review_interval,
                    ctx.review_fuzz_config,
                )
                .max(floor);
                let (minimum, maximum) = ctx.min_and_max_review_intervals(minimum);
                ctx.with_review_fuzz_and_delta(interval, minimum, maximum)
            }
            DayRule::Graduating => {
                let (minimum, maximum) = ctx.min_and_max_review_intervals(floor);
                ctx.with_review_fuzz_and_delta(interval.round().max(1.0), minimum, maximum)
            }
        };
        previous_days = Some(days);
        out[index] = Some(ButtonInterval::Days {
            days,
            fuzz_delta_days,
        });
    }

    out
}

#[cfg(test)]
mod test {
    use fsrs::ItemState;
    use fsrs::MemoryState;
    use fsrs::NextStates;

    use super::*;

    fn ctx() -> StateContext<'static> {
        let mut ctx = StateContext::defaults_for_testing();
        ctx.fsrs_fractional_intervals = true;
        ctx.fsrs_short_term_with_steps_enabled = true;
        ctx.fuzz_factor = None;
        ctx
    }

    fn all(again: f32, hard: f32, good: f32, easy: f32) -> [Option<f32>; 4] {
        [Some(again), Some(hard), Some(good), Some(easy)]
    }

    fn days(days: u32) -> Option<ButtonInterval> {
        Some(ButtonInterval::Days {
            days,
            fuzz_delta_days: 0,
        })
    }

    fn secs(secs: u32) -> Option<ButtonInterval> {
        Some(ButtonInterval::Secs(secs))
    }

    #[test]
    fn day_buttons_form_a_strict_chain() {
        for rule in [
            DayRule::Graduating,
            DayRule::Review {
                previous_interval: 0,
            },
        ] {
            assert_eq!(
                button_intervals(&ctx(), all(3.0, 3.0, 3.0, 3.0), rule),
                [days(3), days(4), days(5), days(6)],
                "{rule:?}"
            );
        }
    }

    #[test]
    fn sub_day_buttons_stay_unrounded_and_ordered() {
        assert_eq!(
            button_intervals(&ctx(), all(0.01, 0.005, 0.25, 0.4), DayRule::Graduating),
            [secs(864), secs(864), secs(21_600), secs(34_560)]
        );
    }

    #[test]
    fn mixed_buttons_chain_within_each_queue() {
        assert_eq!(
            button_intervals(&ctx(), all(0.1, 0.2, 1.2, 1.4), DayRule::Graduating),
            [secs(8_640), secs(17_280), days(1), days(2)]
        );
    }

    #[test]
    fn twelve_hours_or_more_uses_whole_days() {
        for rule in [
            DayRule::Graduating,
            DayRule::Review {
                previous_interval: 1,
            },
        ] {
            assert_eq!(
                button_intervals(&ctx(), all(0.25, 0.5, 0.75, 0.99), rule),
                [secs(21_600), days(1), days(2), days(3)],
                "{rule:?}"
            );
        }
    }

    #[test]
    fn configured_steps_do_not_floor_later_buttons() {
        assert_eq!(
            button_intervals(
                &ctx(),
                [None, None, Some(2.0), Some(2.0)],
                DayRule::Graduating
            ),
            [None, None, days(2), days(3)]
        );
    }

    #[test]
    fn disabled_learning_queues_round_sub_day_intervals_up() {
        let mut ctx = ctx();
        ctx.fsrs_learning_queues_disabled = true;
        let item = ItemState {
            interval: 1.0,
            memory: MemoryState {
                stability: 1.0,
                difficulty: 5.0,
                stability_fast: 1.0,
            },
        };
        ctx.fsrs_next_states = Some(NextStates {
            again: item.clone(),
            hard: item.clone(),
            good: item.clone(),
            easy: item,
        });
        assert_eq!(
            button_intervals(&ctx, all(0.2, 0.3, 0.4, 0.6), DayRule::Graduating),
            [days(1), days(2), days(3), days(4)]
        );
    }

    #[test]
    fn disabled_same_day_reviews_round_fractional_intervals_up() {
        let mut ctx = ctx();
        ctx.fsrs_short_term_with_steps_enabled = false;
        for rule in [
            DayRule::Graduating,
            DayRule::Review {
                previous_interval: 1,
            },
        ] {
            assert_eq!(
                button_intervals(&ctx, all(0.1, 0.2, 0.3, 0.4), rule),
                [days(1), days(2), days(3), days(4)],
                "{rule:?}"
            );
        }
    }

    #[test]
    fn minimum_seconds_floors_sub_day_buttons() {
        let mut ctx = ctx();
        for minimum in [1, 5, 60, 600] {
            ctx.fsrs_minimum_interval_secs = minimum;
            assert_eq!(
                button_intervals(
                    &ctx,
                    [Some(1.0 / 86_400.0), None, None, None],
                    DayRule::Graduating
                ),
                [secs(minimum), None, None, None]
            );
        }
    }

    #[test]
    fn review_again_is_clamped_without_fuzz() {
        let mut ctx = ctx();
        ctx.fuzz_factor = Some(0.99);
        ctx.minimum_lapse_interval = 3;
        let out = button_intervals(
            &ctx,
            all(1.5, 2.0, 30.0, 40.0),
            DayRule::Review {
                previous_interval: 10,
            },
        );
        assert_eq!(out[0], days(3));
        let Some(ButtonInterval::Days { days: hard, .. }) = out[1] else {
            panic!("hard should be in days");
        };
        assert!(hard >= 4);
    }

    #[test]
    fn maximum_interval_caps_every_day_button() {
        let mut ctx = ctx();
        ctx.maximum_review_interval = 5;
        assert_eq!(
            button_intervals(
                &ctx,
                all(10.0, 11.0, 12.0, 13.0),
                DayRule::Review {
                    previous_interval: 30,
                }
            ),
            [days(5), days(5), days(5), days(5)]
        );
    }
}
