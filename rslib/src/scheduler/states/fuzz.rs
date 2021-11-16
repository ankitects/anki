// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::StateContext;

impl<'a> StateContext<'a> {
    /// Apply fuzz, respecting the passed bounds.
    /// Caller must ensure reasonable bounds.
    pub(crate) fn with_review_fuzz(&self, interval: f32, minimum: u32, maximum: u32) -> u32 {
        if let Some(fuzz_factor) = self.fuzz_factor {
            let (lower, upper) = constrained_fuzz_bounds(interval, minimum, maximum);
            (lower as f32 + fuzz_factor * ((1 + upper - lower) as f32)).floor() as u32
        } else {
            (interval.round() as u32).max(minimum).min(maximum)
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
    lower = lower.max(minimum).min(maximum);
    upper = upper.max(minimum).min(maximum);

    if upper == lower && upper > 2 && upper < maximum {
        upper = lower + 1;
    };

    (lower, upper)
}

fn fuzz_bounds(interval: f32) -> (u32, u32) {
    if interval < 2.5 {
        let rounded = interval.round().max(1.0) as u32;
        (rounded, rounded)
    } else if interval < 7.0 {
        fuzz_range(interval, 0.25, 0.0)
    } else if interval < 30.0 {
        fuzz_range(interval, 0.15, 2.0)
    } else {
        fuzz_range(interval, 0.05, 4.0)
    }
}

fn fuzz_range(interval: f32, factor: f32, minimum: f32) -> (u32, u32) {
    let delta = (interval * factor).max(minimum).max(1.0);
    (
        (interval - delta).round() as u32,
        (interval + delta).round() as u32,
    )
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
        // 25%, 15%, 5% percent fuzz, but at least 1, 2, 4
        assert_lower_middle_upper!(5.0, 1, 1000, 4, 5, 6);
        assert_lower_middle_upper!(20.0, 1, 1000, 17, 20, 23);
        assert_lower_middle_upper!(100.0, 1, 1000, 95, 100, 105);

        // ensure fuzz range of at least 2, if allowed
        assert_lower_middle_upper!(2.0, 2, 1000, 2, 2, 2);
        assert_lower_middle_upper!(2.0, 3, 1000, 3, 4, 4);
        assert_lower_middle_upper!(2.0, 3, 3, 3, 3, 3);

        // fuzz bracket transitions
        assert_lower_middle_upper!(6.9, 3, 1000, 5, 7, 9);
        assert_lower_middle_upper!(7.0, 3, 1000, 5, 7, 9);
        assert_lower_middle_upper!(7.1, 3, 1000, 5, 7, 9);
        assert_lower_middle_upper!(29.9, 3, 1000, 25, 30, 34);
        assert_lower_middle_upper!(30.0, 3, 1000, 26, 30, 34);
        assert_lower_middle_upper!(30.1, 3, 1000, 26, 30, 34);

        // respect limits and preserve uniform distribution of valid intervals
        assert_lower_middle_upper!(100.0, 101, 1000, 101, 103, 105);
        assert_lower_middle_upper!(100.0, 1, 99, 95, 97, 99);
        assert_lower_middle_upper!(100.0, 97, 103, 97, 100, 103);
    }
}
