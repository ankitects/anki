// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize;
use serde::Serialize;

use super::StateContext;
use crate::collection::Collection;
use crate::deckconfig::DEFAULT_REVIEW_FUZZ_BASE;
use crate::deckconfig::DEFAULT_REVIEW_FUZZ_ENABLED;
use crate::deckconfig::DEFAULT_REVIEW_FUZZ_FACTOR_LONG;
use crate::deckconfig::DEFAULT_REVIEW_FUZZ_FACTOR_MID;
use crate::deckconfig::DEFAULT_REVIEW_FUZZ_FACTOR_SHORT;
use crate::prelude::*;

/// Describes a range of days for which a certain amount of fuzz is applied to
/// the new interval.
struct FuzzRange {
    start: f32,
    end: f32,
}

static FUZZ_RANGES: [FuzzRange; 3] = [
    FuzzRange {
        start: 2.5,
        end: 7.0,
    },
    FuzzRange {
        start: 7.0,
        end: 20.0,
    },
    FuzzRange {
        start: 20.0,
        end: f32::MAX,
    },
];

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ReviewFuzzConfig {
    pub base: f32,
    pub factor_short: f32,
    pub factor_mid: f32,
    pub factor_long: f32,
}

impl Default for ReviewFuzzConfig {
    fn default() -> Self {
        Self {
            base: DEFAULT_REVIEW_FUZZ_BASE,
            factor_short: DEFAULT_REVIEW_FUZZ_FACTOR_SHORT,
            factor_mid: DEFAULT_REVIEW_FUZZ_FACTOR_MID,
            factor_long: DEFAULT_REVIEW_FUZZ_FACTOR_LONG,
        }
    }
}

impl ReviewFuzzConfig {
    pub fn none() -> Self {
        Self {
            base: 0.0,
            factor_short: 0.0,
            factor_mid: 0.0,
            factor_long: 0.0,
        }
    }

    fn factors(self) -> [f32; 3] {
        [self.factor_short, self.factor_mid, self.factor_long]
    }
}

const REVIEW_FUZZ_CONFIG_KEY: &str = "reviewFuzz";

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct StoredReviewFuzzConfig {
    pub enabled: bool,
    pub base: f32,
    pub factor_short: f32,
    pub factor_mid: f32,
    pub factor_long: f32,
}

impl Default for StoredReviewFuzzConfig {
    fn default() -> Self {
        Self {
            enabled: DEFAULT_REVIEW_FUZZ_ENABLED,
            base: DEFAULT_REVIEW_FUZZ_BASE,
            factor_short: DEFAULT_REVIEW_FUZZ_FACTOR_SHORT,
            factor_mid: DEFAULT_REVIEW_FUZZ_FACTOR_MID,
            factor_long: DEFAULT_REVIEW_FUZZ_FACTOR_LONG,
        }
    }
}

impl StoredReviewFuzzConfig {
    pub fn review_fuzz_config(self) -> ReviewFuzzConfig {
        if self.enabled {
            ReviewFuzzConfig {
                base: self.base,
                factor_short: self.factor_short,
                factor_mid: self.factor_mid,
                factor_long: self.factor_long,
            }
        } else {
            ReviewFuzzConfig::none()
        }
    }
}

impl StateContext<'_> {
    /// Apply fuzz, respecting the passed bounds.
    #[cfg(test)]
    pub(crate) fn with_review_fuzz(&self, interval: f32, minimum: u32, maximum: u32) -> u32 {
        self.with_review_fuzz_and_delta(interval, minimum, maximum)
            .0
    }

    /// Apply fuzz, respecting the passed bounds, and return the difference
    /// from the unfuzzed interval.
    pub(crate) fn with_review_fuzz_and_delta(
        &self,
        interval: f32,
        minimum: u32,
        maximum: u32,
    ) -> (u32, i32) {
        let minimum = minimum.min(maximum);
        let unfuzzed = (interval.round() as u32).clamp(minimum, maximum);
        self.load_balancer_ctx
            .as_ref()
            .and_then(|load_balancer_ctx| {
                load_balancer_ctx.find_interval(interval, minimum, maximum)
            })
            .map(|fuzzed| (fuzzed, fuzzed as i32 - unfuzzed as i32))
            .unwrap_or_else(|| {
                with_review_fuzz_and_delta(
                    self.fuzz_factor,
                    interval,
                    minimum,
                    maximum,
                    self.review_fuzz_config,
                )
            })
    }
}

impl Collection {
    pub(crate) fn stored_review_fuzz_config(&self) -> StoredReviewFuzzConfig {
        self.get_config_optional(REVIEW_FUZZ_CONFIG_KEY)
            .unwrap_or_default()
    }

    pub(crate) fn review_fuzz_config(&self) -> ReviewFuzzConfig {
        self.stored_review_fuzz_config().review_fuzz_config()
    }

    pub(crate) fn set_stored_review_fuzz_config(
        &mut self,
        config: StoredReviewFuzzConfig,
    ) -> Result<bool> {
        self.set_config(REVIEW_FUZZ_CONFIG_KEY, &config)
    }

    /// Used for FSRS add-on.
    pub(crate) fn get_fuzz_delta(&self, card_id: CardId, interval: u32) -> Result<i32> {
        let card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
        let deck = self
            .storage
            .get_deck(card.deck_id)?
            .or_not_found(card.deck_id)?;
        let config = self.home_deck_config(deck.config_id(), card.original_deck_id)?;
        let fuzzed = with_review_fuzz(
            card.get_fuzz_factor(true),
            interval as f32,
            1,
            config.inner.maximum_review_interval,
            self.review_fuzz_config(),
        );
        Ok((fuzzed as i32) - (interval as i32))
    }
}

pub(crate) fn with_review_fuzz(
    fuzz_factor: Option<f32>,
    interval: f32,
    minimum: u32,
    maximum: u32,
    review_fuzz_config: ReviewFuzzConfig,
) -> u32 {
    with_review_fuzz_and_delta(fuzz_factor, interval, minimum, maximum, review_fuzz_config).0
}

pub(crate) fn with_review_fuzz_and_delta(
    fuzz_factor: Option<f32>,
    interval: f32,
    minimum: u32,
    maximum: u32,
    review_fuzz_config: ReviewFuzzConfig,
) -> (u32, i32) {
    let minimum = minimum.min(maximum);
    let unfuzzed = (interval.round() as u32).clamp(minimum, maximum);
    if let Some(fuzz_factor) = fuzz_factor {
        let (lower, upper) =
            constrained_fuzz_bounds(interval, minimum, maximum, review_fuzz_config);
        let fuzzed = (lower as f32 + fuzz_factor * ((1 + upper - lower) as f32)).floor() as u32;
        (fuzzed, fuzzed as i32 - unfuzzed as i32)
    } else {
        (unfuzzed, 0)
    }
}

/// Return the minimum interval to use when fuzzing a review/reschedule
/// interval, based on the previously scheduled interval.
pub(crate) fn minimum_review_fuzz_interval(
    interval: f32,
    previous_interval: u32,
    maximum_interval: u32,
    review_fuzz_config: ReviewFuzzConfig,
) -> u32 {
    let rounded = interval.round() as u32;
    let (_, upper) = constrained_fuzz_bounds(interval, 1, maximum_interval, review_fuzz_config);

    if rounded > previous_interval {
        previous_interval + 1
    } else if previous_interval <= upper {
        // interval may not have grown much, but don't let fuzz reduce it
        previous_interval
    } else {
        // interval shrunk, possibly due to changed FSRS params or DR
        0
    }
}

/// Return the bounds of the fuzz range, respecting `minimum` and `maximum`.
/// Ensure the upper bound is larger than the lower bound, if `maximum` allows
/// it and it is larger than 1.
pub(crate) fn constrained_fuzz_bounds(
    interval: f32,
    minimum: u32,
    maximum: u32,
    review_fuzz_config: ReviewFuzzConfig,
) -> (u32, u32) {
    let minimum = minimum.min(maximum);
    let interval = interval.clamp(minimum as f32, maximum as f32);
    let (mut lower, mut upper) = fuzz_bounds(interval, review_fuzz_config);

    // minimum <= maximum and lower <= upper are assumed
    // now ensure minimum <= lower <= upper <= maximum
    lower = lower.clamp(minimum, maximum);
    upper = upper.clamp(minimum, maximum);

    if upper == lower && upper > 2 && upper < maximum {
        upper = lower + 1;
    };

    (lower, upper)
}

pub(crate) fn fuzz_bounds(interval: f32, review_fuzz_config: ReviewFuzzConfig) -> (u32, u32) {
    let delta = fuzz_delta(interval, review_fuzz_config);
    (
        (interval - delta).round() as u32,
        (interval + delta).round() as u32,
    )
}

/// Return the amount of fuzz to apply to the interval in both directions.
/// Short intervals do not get fuzzed. All other intervals get fuzzed by the
/// configured base amount plus the number of its days in each defined fuzz
/// range multiplied with the configured factor for that range.
fn fuzz_delta(interval: f32, review_fuzz_config: ReviewFuzzConfig) -> f32 {
    if interval < 2.5 {
        0.0
    } else {
        FUZZ_RANGES.iter().zip(review_fuzz_config.factors()).fold(
            review_fuzz_config.base,
            |delta, (range, factor)| {
                delta + factor * (interval.min(range.end) - range.start).max(0.0)
            },
        )
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn with_review_fuzz() {
        let mut ctx = StateContext::defaults_for_testing();

        // no fuzz
        assert_eq!(ctx.with_review_fuzz(1.5, 1, 100), 2);
        assert_eq!(ctx.with_review_fuzz(0.1, 1, 100), 1);
        assert_eq!(ctx.with_review_fuzz(101.0, 1, 100), 100);

        macro_rules! assert_lower_middle_upper {
            ($interval:expr, $minimum:expr, $maximum:expr, $lower:expr, $middle:expr, $upper:expr) => {{
                ctx.fuzz_factor = Some(0.0);
                assert_eq!(ctx.with_review_fuzz($interval, $minimum, $maximum), $lower);
                ctx.fuzz_factor = Some(0.5);
                assert_eq!(ctx.with_review_fuzz($interval, $minimum, $maximum), $middle);
                ctx.fuzz_factor = Some(0.99);
                assert_eq!(ctx.with_review_fuzz($interval, $minimum, $maximum), $upper);
            }};
        }

        // no fuzzing for an interval of 1-2.49
        assert_lower_middle_upper!(1.0, 1, 1000, 1, 1, 1);
        assert_lower_middle_upper!(2.49, 1, 1000, 2, 2, 2);

        // 1 day for intervals >= 2.5
        assert_lower_middle_upper!(2.5, 1, 1000, 2, 3, 4);
        // ... plus 0.15 for every day in the range 2.5-7
        assert_lower_middle_upper!(7.0, 1, 1000, 5, 7, 9);
        // ... plus 0.1 for every day in the range 7-20
        assert_lower_middle_upper!(17.0, 1, 1000, 14, 17, 20);
        // ... plus 0.05 for every day above 20
        assert_lower_middle_upper!(37.0, 1, 1000, 33, 37, 41);

        // ensure fuzz range of at least 2, if allowed
        assert_lower_middle_upper!(2.0, 2, 1000, 2, 2, 2);
        assert_lower_middle_upper!(2.0, 3, 1000, 3, 4, 4);
        assert_lower_middle_upper!(2.0, 3, 3, 3, 3, 3);

        // fuzz range transitions
        assert_lower_middle_upper!(6.9, 3, 1000, 5, 7, 9);
        assert_lower_middle_upper!(7.0, 3, 1000, 5, 7, 9);
        assert_lower_middle_upper!(7.1, 3, 1000, 5, 7, 9);
        assert_lower_middle_upper!(19.9, 3, 1000, 17, 20, 23);
        assert_lower_middle_upper!(20.0, 3, 1000, 17, 20, 23);
        assert_lower_middle_upper!(20.1, 3, 1000, 17, 20, 23);

        // respect limits and preserve uniform distribution of valid intervals
        assert_lower_middle_upper!(100.0, 101, 1000, 101, 105, 108);
        assert_lower_middle_upper!(100.0, 1, 99, 92, 96, 99);
        assert_lower_middle_upper!(100.0, 97, 103, 97, 100, 103);
    }

    #[test]
    fn invalid_values_will_not_panic() {
        constrained_fuzz_bounds(1.0, 3, 2, ReviewFuzzConfig::default());
    }

    #[test]
    fn configurable_review_fuzz_changes_bounds() {
        let wider = ReviewFuzzConfig {
            base: 2.0,
            factor_short: 0.3,
            factor_mid: 0.2,
            factor_long: 0.1,
        };
        assert_eq!(fuzz_bounds(7.0, wider), (4, 10));
        assert_eq!(constrained_fuzz_bounds(100.0, 1, 1000, wider), (86, 114));
    }

    #[test]
    fn zero_review_fuzz_disables_delta() {
        let none = ReviewFuzzConfig {
            base: 0.0,
            factor_short: 0.0,
            factor_mid: 0.0,
            factor_long: 0.0,
        };
        assert_eq!(fuzz_bounds(7.0, none), (7, 7));
        assert_eq!(fuzz_bounds(37.0, none), (37, 37));
    }

    #[test]
    fn fuzz_delta_matches_selected_interval() {
        let (interval, delta) =
            with_review_fuzz_and_delta(Some(0.99), 7.0, 1, 1000, ReviewFuzzConfig::default());

        assert_eq!(interval, 9);
        assert_eq!(delta, 2);
    }

    #[test]
    fn collection_review_fuzz_defaults_to_anki_values() {
        let col = Collection::new();

        assert_eq!(col.review_fuzz_config(), ReviewFuzzConfig::default());
        assert_eq!(
            col.stored_review_fuzz_config(),
            StoredReviewFuzzConfig::default()
        );
    }

    #[test]
    fn collection_review_fuzz_can_be_disabled() -> Result<()> {
        let mut col = Collection::new();
        let stored = StoredReviewFuzzConfig {
            enabled: false,
            base: 3.0,
            factor_short: 0.4,
            factor_mid: 0.3,
            factor_long: 0.2,
        };

        col.set_stored_review_fuzz_config(stored)?;

        assert_eq!(col.stored_review_fuzz_config(), stored);
        assert_eq!(col.review_fuzz_config(), ReviewFuzzConfig::none());
        Ok(())
    }

    #[test]
    fn minimum_review_fuzz_interval_preserves_previous_only_within_range() {
        assert_eq!(
            minimum_review_fuzz_interval(2.7269483, 4, 36500, ReviewFuzzConfig::default()),
            4
        );
        assert_eq!(
            minimum_review_fuzz_interval(2.7269483, 5, 36500, ReviewFuzzConfig::default()),
            0
        );
        assert_eq!(
            minimum_review_fuzz_interval(4.591988, 4, 36500, ReviewFuzzConfig::default()),
            5
        );
    }
}
