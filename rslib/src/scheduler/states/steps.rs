// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

const DEFAULT_SECS_IF_MISSING: u32 = 60;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct LearningSteps<'a> {
    steps: &'a [f32],
}

fn to_secs(v: f32) -> u32 {
    (v * 60.0) as u32
}

impl<'a> LearningSteps<'a> {
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

    /// Cards in learning must always have at least one learning step.
    pub(crate) fn again_delay_secs_learn(&self) -> u32 {
        self.secs_at_index(0).unwrap_or(DEFAULT_SECS_IF_MISSING)
    }

    pub(crate) fn again_delay_secs_relearn(&self) -> Option<u32> {
        self.secs_at_index(0)
    }

    // fixme: the logic here is not ideal, but tries to match
    // the current python code

    pub(crate) fn hard_delay_secs(self, remaining: u32) -> Option<u32> {
        let idx = self.get_index(remaining);
        if let Some(current) = self
            .secs_at_index(idx)
            // if current is invalid, try first step
            .or_else(|| self.steps.first().copied().map(to_secs))
        {
            let next = if self.steps.len() > 1 {
                self.secs_at_index(idx + 1).unwrap_or(60)
            } else {
                current * 2
            }
            .max(current);

            Some((current + next) / 2)
        } else {
            None
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
}
