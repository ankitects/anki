// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

const DAY: u32 = 60 * 60 * 24;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct LearningSteps<'a> {
    /// The steps in minutes.
    steps: &'a [f32],
}

fn to_secs(v: f32) -> u32 {
    (v * 60.0) as u32
}

impl LearningSteps<'_> {
    /// Takes `steps` as minutes.
    pub(crate) fn new(steps: &[f32]) -> LearningSteps<'_> {
        LearningSteps { steps }
    }

    /// Strip off 'learning today', and ensure index is in bounds.
    fn get_index(self, remaining: u32) -> usize {
        let total = self.steps.len();
        total
            .saturating_sub((remaining % 1000) as usize)
            .min(total.saturating_sub(1))
    }

    fn secs_at_index(&self, index: usize) -> Option<u32> {
        self.steps.get(index).copied().map(to_secs)
    }

    pub(crate) fn again_delay_secs_learn(&self) -> Option<u32> {
        self.secs_at_index(0)
    }

    pub(crate) fn hard_delay_secs(self, remaining: u32) -> Option<u32> {
        let idx = self.get_index(remaining);
        self.secs_at_index(idx)
            // if current is invalid, try first step
            .or_else(|| self.steps.first().copied().map(to_secs))
            .map(|current| {
                if idx == 0 {
                    self.hard_delay_secs_for_first_step(current)
                } else {
                    current
                }
            })
    }

    /// Special case the hard interval for the first step to avoid equality with
    /// the again interval. Also ensure it's smaller than the good interval,
    /// at least with reasonable settings.
    fn hard_delay_secs_for_first_step(self, again_secs: u32) -> u32 {
        if let Some(next) = self.secs_at_index(1) {
            // average of first (again) and second (good) steps
            maybe_round_in_days(again_secs.saturating_add(next) / 2)
        } else {
            // 50% more than the again secs, but at most one day more
            // otherwise, a learning step of 3 days and a graduating interval of 4 days e.g.
            // would lead to the hard interval being larger than the good interval
            let secs = (again_secs.saturating_mul(3) / 2).min(again_secs.saturating_add(DAY));
            maybe_round_in_days(secs)
        }
    }

    pub(crate) fn good_delay_secs(self, remaining: u32) -> Option<u32> {
        let idx = self.get_index(remaining);
        self.secs_at_index(idx + 1)
    }

    pub(crate) fn current_delay_secs(self, remaining: u32) -> u32 {
        let idx = self.get_index(remaining);
        self.secs_at_index(idx).unwrap_or_default()
    }

    pub(crate) fn remaining_for_good(self, remaining: u32) -> u32 {
        let idx = self.get_index(remaining);
        self.steps.len().saturating_sub(idx + 1) as u32
    }

    pub(crate) fn remaining_for_failed(self) -> u32 {
        self.steps.len() as u32
    }

    pub(crate) fn is_empty(&self) -> bool {
        self.steps.is_empty()
    }
}

/// If the given interval in seconds surpasses 1 day, rounds it to a whole
/// number of days. Ensures that the user gets the same results earlier and
/// later in the day. Returns seconds.
fn maybe_round_in_days(secs: u32) -> u32 {
    if secs > DAY {
        ((secs as f32 / DAY as f32).round() as u32).saturating_mul(DAY)
    } else {
        secs
    }
}

#[cfg(test)]
mod test {
    use super::*;

    macro_rules! assert_delay_secs {
        ($steps:expr, $remaining:expr, $again_delay:expr, $hard_delay:expr, $good_delay:expr) => {
            let steps = LearningSteps::new(&$steps);
            assert_eq!(steps.again_delay_secs_learn(), $again_delay);
            assert_eq!(steps.hard_delay_secs($remaining), $hard_delay);
            assert_eq!(steps.good_delay_secs($remaining), $good_delay);
        };
    }

    #[test]
    fn delay_secs() {
        // if no other step, hard delay is 50% above again secs
        assert_delay_secs!([10.0], 1, Some(600), Some(900), None);
        // but at most one day more than again secs
        assert_delay_secs!(
            [(3 * DAY / 60) as f32],
            1,
            Some(3 * DAY),
            Some(4 * DAY),
            None
        );

        assert_delay_secs!([1.0, 10.0], 2, Some(60), Some(330), Some(600));
        assert_delay_secs!([1.0, 10.0], 1, Some(60), Some(600), None);

        assert_delay_secs!([1.0, 10.0, 100.0], 3, Some(60), Some(330), Some(600));
        assert_delay_secs!([1.0, 10.0, 100.0], 2, Some(60), Some(600), Some(6000));
        assert_delay_secs!([1.0, 10.0, 100.0], 1, Some(60), Some(6000), None);
    }

    #[test]
    fn rounding_days() {
        assert_eq!(maybe_round_in_days(DAY - 1), DAY - 1);
        assert_eq!(maybe_round_in_days((1.5 * DAY as f32) as u32), 2 * DAY);
    }
}
