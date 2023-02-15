// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::StateContext;

/// Describes a range of days for which a certain amount of fuzz is applied to
/// the new interval.
struct FuzzRange {
    start: f32,
    end: f32,
    factor: f32,
}

static FUZZ_RANGES: [FuzzRange; 3] = [
    FuzzRange {
        start: 2.5,
        end: 7.0,
        factor: 0.15,
    },
    FuzzRange {
        start: 7.0,
        end: 20.0,
        factor: 0.1,
    },
    FuzzRange {
        start: 20.0,
        end: f32::MAX,
        factor: 0.05,
    },
];

impl<'a> StateContext<'a> {
    /// Apply fuzz, respecting the passed bounds.
    /// Caller must ensure reasonable bounds.
    pub(crate) fn with_review_fuzz(&self, interval: f32, minimum: u32, maximum: u32) -> u32 {
        if let Some(fuzz_factor) = self.fuzz_factor {
            let (lower, upper) = constrained_fuzz_bounds(interval, minimum, maximum);
            (lower as f32 + fuzz_factor * ((1 + upper - lower) as f32)).floor() as u32
        } else {
            (interval.round() as u32).clamp(minimum, maximum)
        }
    }

    pub(crate) fn fuzzed_graduating_interval_good(&self) -> u32 {
        let (minimum, maximum) = self.min_and_max_review_intervals(1);
        self.with_review_fuzz(self.graduating_interval_good as f32, minimum, maximum)
    }

    pub(crate) fn fuzzed_graduating_interval_easy(&self) -> u32 {
        let (minimum, maximum) = self.min_and_max_review_intervals(1);
        self.with_review_fuzz(self.graduating_interval_easy as f32, minimum, maximum)
    }
}

/// Return the bounds of the fuzz range, respecting `minimum` and `maximum`.
/// Ensure the upper bound is larger than the lower bound, if `maximum` allows
/// it and it is larger than 1.
fn constrained_fuzz_bounds(interval: f32, minimum: u32, maximum: u32) -> (u32, u32) {
    let (mut lower, mut upper) = fuzz_bounds(interval);

    // minimum <= maximum and lower <= upper are assumed
    // now ensure minimum <= lower <= upper <= maximum
    lower = lower.clamp(minimum, maximum);
    upper = upper.clamp(minimum, maximum);

    if upper == lower && upper > 2 && upper < maximum {
        upper = lower + 1;
    };

    (lower, upper)
}

fn fuzz_bounds(interval: f32) -> (u32, u32) {
    let delta = fuzz_delta(interval);
    (
        (interval - delta).round() as u32,
        (interval + delta).round() as u32,
    )
}

/// Return the amount of fuzz to apply to the interval in both directions.
/// Short intervals do not get fuzzed. All other intervals get fuzzed by 1 day
/// plus the number of its days in each defined fuzz range multiplied with the
/// given factor.
fn fuzz_delta(interval: f32) -> f32 {
    if interval < 2.5 {
        0.0
    } else {
        FUZZ_RANGES.iter().fold(1.0, |delta, range| {
            delta + range.factor * (interval.min(range.end) - range.start).max(0.0)
        })
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
        assert_lower_middle_upper!(100.0, 101, 1000, 101, 104, 107);
        assert_lower_middle_upper!(100.0, 1, 99, 93, 96, 99);
        assert_lower_middle_upper!(100.0, 97, 103, 97, 100, 103);
    }
}
