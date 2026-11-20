// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use fsrs::CostAdrNextStates;
use fsrs::CostAdrPolicy;
use fsrs::ItemState;
use fsrs::NextStates;

use crate::deckconfig::DeckConfigInner;
use crate::prelude::*;

const COST_ADR_PARAMETER_COUNT: usize = 15;
const COST_WEIGHT_MIN: f32 = 0.0;
const COST_WEIGHT_MAX: f32 = 1024.0;
pub(crate) const DEFAULT_RETENTION_MIN: f32 = 0.30;
pub(crate) const DEFAULT_RETENTION_MAX: f32 = 0.995;

#[derive(Debug, Clone, PartialEq)]
pub(crate) struct DynamicDesiredRetention {
    policy_params: Vec<f32>,
    calibration: Vec<(f32, f32)>,
    fsrs_equivalent_calibration: Vec<(f32, f32)>,
    fixed_target_calibration: Vec<(f32, f32)>,
    retention_min: f32,
    retention_max: f32,
    clamp_target: bool,
    max_interval_days: Option<f32>,
}

pub(crate) struct DynamicDesiredRetentionFields {
    pub policy_params: Vec<f32>,
    pub calibration_weights: Vec<f32>,
    pub calibration_avg_drs: Vec<f32>,
    pub fsrs_equivalent_weights: Vec<f32>,
    pub fsrs_equivalent_drs: Vec<f32>,
    pub fixed_target_weights: Vec<f32>,
    pub fixed_target_drs: Vec<f32>,
    pub retention_min: f32,
    pub retention_max: f32,
    pub clamp_target: bool,
    pub max_interval_days: Option<f32>,
}

#[derive(Debug, Clone)]
pub(crate) struct DynamicDesiredRetentionStates {
    pub states: NextStates,
    pub desired_retentions: [f32; 4],
    pub cost_weight: f32,
}

impl DynamicDesiredRetention {
    pub(crate) fn from_deck_config(config: &DeckConfigInner) -> Result<Option<Self>> {
        if !config.fsrs_dynamic_desired_retention_enabled {
            return Ok(None);
        }

        Self::from_fields(DynamicDesiredRetentionFields {
            policy_params: config.fsrs_dynamic_desired_retention_params.clone(),
            calibration_weights: config.fsrs_dynamic_desired_retention_weights.clone(),
            calibration_avg_drs: config.fsrs_dynamic_desired_retention_avg_drs.clone(),
            fsrs_equivalent_weights: config
                .fsrs_dynamic_desired_retention_fsrs_eq_weights
                .clone(),
            fsrs_equivalent_drs: config.fsrs_dynamic_desired_retention_fsrs_eq_drs.clone(),
            fixed_target_weights: config
                .fsrs_dynamic_desired_retention_fixed_target_weights
                .clone(),
            fixed_target_drs: config
                .fsrs_dynamic_desired_retention_fixed_target_drs
                .clone(),
            retention_min: config.fsrs_dynamic_desired_retention_min,
            retention_max: config.fsrs_dynamic_desired_retention_max,
            clamp_target: config.fsrs_dynamic_desired_retention_clamp,
            max_interval_days: Some(config.maximum_review_interval as f32),
        })
        .map(Some)
    }

    pub(crate) fn from_fields(fields: DynamicDesiredRetentionFields) -> Result<Self> {
        let DynamicDesiredRetentionFields {
            policy_params,
            calibration_weights,
            calibration_avg_drs,
            fsrs_equivalent_weights,
            fsrs_equivalent_drs,
            fixed_target_weights,
            fixed_target_drs,
            retention_min,
            retention_max,
            clamp_target,
            max_interval_days,
        } = fields;
        require!(
            policy_params.len() == COST_ADR_PARAMETER_COUNT,
            "Dynamic DR requires 15 ADR policy parameters"
        );
        require!(
            policy_params.iter().all(|value| value.is_finite()),
            "Dynamic DR parameters must be finite"
        );
        require!(
            calibration_weights.len() == calibration_avg_drs.len()
                && calibration_weights.len() >= 2,
            "Dynamic DR requires matching weight and average DR calibration arrays"
        );

        let mut calibration = calibration_weights
            .into_iter()
            .zip(calibration_avg_drs)
            .collect::<Vec<_>>();
        calibration.sort_by(|a, b| a.0.total_cmp(&b.0));
        require!(
            calibration.iter().all(|(weight, avg_dr)| {
                weight.is_finite()
                    && *weight >= 0.0
                    && avg_dr.is_finite()
                    && (0.0..=1.0).contains(avg_dr)
            }),
            "Dynamic DR calibration values must be finite"
        );
        require!(
            fsrs_equivalent_weights.len() == fsrs_equivalent_drs.len(),
            "Dynamic DR FSRS equivalent calibration arrays must match"
        );
        let mut fsrs_equivalent_calibration = fsrs_equivalent_weights
            .into_iter()
            .zip(fsrs_equivalent_drs)
            .collect::<Vec<_>>();
        fsrs_equivalent_calibration.sort_by(|a, b| a.0.total_cmp(&b.0));
        require!(
            fsrs_equivalent_calibration.iter().all(|(weight, dr)| {
                weight.is_finite() && *weight >= 0.0 && dr.is_finite() && (0.0..=1.0).contains(dr)
            }),
            "Dynamic DR FSRS equivalent calibration values must be finite"
        );
        require!(
            fixed_target_weights.len() == fixed_target_drs.len(),
            "Dynamic DR fixed target calibration arrays must match"
        );
        let mut fixed_target_calibration = fixed_target_weights
            .into_iter()
            .zip(fixed_target_drs)
            .collect::<Vec<_>>();
        fixed_target_calibration.sort_by(|a, b| a.1.total_cmp(&b.1));
        require!(
            fixed_target_calibration.iter().all(|(weight, dr)| {
                weight.is_finite() && *weight >= 0.0 && dr.is_finite() && (0.0..=1.0).contains(dr)
            }),
            "Dynamic DR fixed target calibration values must be finite"
        );
        require!(
            valid_retention_bounds(retention_min, retention_max),
            "Dynamic DR retention bounds must be finite retention values"
        );

        Ok(Self {
            policy_params,
            calibration,
            fsrs_equivalent_calibration,
            fixed_target_calibration,
            retention_min,
            retention_max,
            clamp_target,
            max_interval_days,
        })
    }

    pub(crate) fn cost_weight_for_average_dr(&self, target: f32) -> Result<f32> {
        require!(
            target.is_finite() && (0.0..=1.0).contains(&target),
            "Dynamic DR target must be a retention value"
        );
        require!(
            self.retention_min <= target && target <= self.retention_max,
            "Dynamic DR target is outside the configured retention bounds"
        );

        if !self.fixed_target_calibration.is_empty() {
            return self.cost_weight_for_fixed_target_dr(target);
        }

        if let Some(((left_weight, left_dr), (right_weight, right_dr))) =
            self.calibration_pair_for_target_dr(target)
        {
            return if (left_dr - right_dr).abs() < f32::EPSILON {
                Ok(left_weight)
            } else {
                let t = ((target - left_dr) / (right_dr - left_dr)).clamp(0.0, 1.0);
                let left_log = left_weight.ln_1p();
                let right_log = right_weight.ln_1p();
                Ok((left_log + (right_log - left_log) * t).exp_m1())
            };
        }

        invalid_input!("Dynamic DR target is outside the calibrated average DR range")
    }

    pub(crate) fn target_in_dynamic_dr_range(&self, target: f32) -> Result<bool> {
        require!(
            target.is_finite() && (0.0..=1.0).contains(&target),
            "Dynamic DR target must be a retention value"
        );
        if !(self.retention_min <= target && target <= self.retention_max) {
            return Ok(false);
        }
        if !self.fixed_target_calibration.is_empty() {
            return Ok(self
                .fixed_target_calibration
                .iter()
                .any(|(_, dr)| target < *dr || dynamic_dr_close(target, *dr)));
        }
        Ok(self.calibration_pair_for_target_dr(target).is_some())
    }

    pub(crate) fn scheduling_target(&self, target: f32) -> Result<Option<f32>> {
        require!(
            target.is_finite() && (0.0..=1.0).contains(&target),
            "Dynamic DR target must be a retention value"
        );
        if self.target_in_dynamic_dr_range(target)? {
            return Ok(Some(target));
        }
        if !self.clamp_target {
            return Ok(None);
        }
        let Some((min_target, max_target)) = self.supported_target_range() else {
            return Ok(None);
        };
        let clamped = target.clamp(min_target, max_target);
        if self.retention_min <= clamped
            && clamped <= self.retention_max
            && self.calibration_pair_for_target_dr(clamped).is_some()
        {
            Ok(Some(clamped))
        } else {
            Ok(None)
        }
    }

    pub(crate) fn policy_params(&self) -> &[f32] {
        &self.policy_params
    }

    pub(crate) fn calibration(&self) -> &[(f32, f32)] {
        &self.calibration
    }

    pub(crate) fn fsrs_equivalent_calibration(&self) -> &[(f32, f32)] {
        &self.fsrs_equivalent_calibration
    }

    pub(crate) fn fixed_target_calibration(&self) -> &[(f32, f32)] {
        &self.fixed_target_calibration
    }

    pub(crate) fn retention_min(&self) -> f32 {
        self.retention_min
    }

    pub(crate) fn retention_max(&self) -> f32 {
        self.retention_max
    }

    pub(crate) fn clamp_target(&self) -> bool {
        self.clamp_target
    }

    pub(crate) fn supported_target_range(&self) -> Option<(f32, f32)> {
        if !self.fixed_target_calibration.is_empty() {
            let max_target = self
                .fixed_target_calibration
                .iter()
                .map(|(_, dr)| *dr)
                .max_by(f32::total_cmp)?;
            return Some((self.retention_min, max_target));
        }
        let mut targets = self.target_calibration().iter().map(|(_, dr)| *dr);
        let first = targets.next()?;
        Some(targets.fold((first, first), |(min, max), target| {
            (min.min(target), max.max(target))
        }))
    }

    fn cost_weight_for_fixed_target_dr(&self, target: f32) -> Result<f32> {
        if let Some(weight) = self
            .fixed_target_calibration
            .iter()
            .find_map(|(weight, dr)| {
                (target < *dr || dynamic_dr_close(target, *dr)).then_some(*weight)
            })
        {
            Ok(weight)
        } else {
            invalid_input!("Dynamic DR target is outside the fixed target calibration range")
        }
    }

    fn calibration_pair_for_target_dr(&self, target: f32) -> Option<((f32, f32), (f32, f32))> {
        self.target_calibration().windows(2).find_map(|pair| {
            let left = pair[0];
            let right = pair[1];
            ((left.1 - target) * (right.1 - target) <= 0.0).then_some((left, right))
        })
    }

    fn target_calibration(&self) -> &[(f32, f32)] {
        if self.fsrs_equivalent_calibration.len() >= 2 {
            &self.fsrs_equivalent_calibration
        } else {
            &self.calibration
        }
    }

    pub(crate) fn policy(&self) -> Result<CostAdrPolicy> {
        CostAdrPolicy::new_with_settings(
            self.policy_params.clone(),
            COST_WEIGHT_MIN,
            COST_WEIGHT_MAX,
            self.retention_min,
            self.retention_max,
            self.max_interval_days,
        )
        .map_err(Into::into)
    }

    pub(crate) fn with_max_interval_days(&self, max_interval_days: Option<f32>) -> Self {
        let mut value = self.clone();
        value.max_interval_days = max_interval_days;
        value
    }

    pub(crate) fn next_states(
        &self,
        fsrs: &fsrs::FSRS,
        current_memory_state: Option<fsrs::MemoryState>,
        target_average_dr: f32,
        days_elapsed: f32,
    ) -> Result<DynamicDesiredRetentionStates> {
        let cost_weight = self.cost_weight_for_average_dr(target_average_dr)?;
        let states =
            self.policy()?
                .next_states(fsrs, current_memory_state, cost_weight, days_elapsed)?;
        Ok(dynamic_states_from_cost_adr(states, cost_weight))
    }
}

pub(crate) fn valid_retention_bounds(retention_min: f32, retention_max: f32) -> bool {
    retention_min.is_finite()
        && retention_max.is_finite()
        && 0.0 < retention_min
        && retention_min < retention_max
        && retention_max < 1.0
}

fn dynamic_dr_close(left: f32, right: f32) -> bool {
    let scale = left.abs().max(right.abs()).max(1.0);
    (left - right).abs() <= 1e-6 * scale
}

fn dynamic_states_from_cost_adr(
    states: CostAdrNextStates,
    cost_weight: f32,
) -> DynamicDesiredRetentionStates {
    DynamicDesiredRetentionStates {
        desired_retentions: [
            states.again.desired_retention,
            states.hard.desired_retention,
            states.good.desired_retention,
            states.easy.desired_retention,
        ],
        states: NextStates {
            again: ItemState {
                memory: states.again.memory,
                interval: states.again.interval,
            },
            hard: ItemState {
                memory: states.hard.memory,
                interval: states.hard.interval,
            },
            good: ItemState {
                memory: states.good.memory,
                interval: states.good.interval,
            },
            easy: ItemState {
                memory: states.easy.memory,
                interval: states.easy.interval,
            },
        },
        cost_weight,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::deckconfig::DeckConfig;

    #[test]
    fn disabled_config_has_no_dynamic_dr() -> Result<()> {
        let config = DeckConfig::default();
        assert!(DynamicDesiredRetention::from_deck_config(&config.inner)?.is_none());
        Ok(())
    }

    #[test]
    fn enabled_config_requires_policy_and_calibration() {
        let mut config = DeckConfig::default();
        config.inner.fsrs_dynamic_desired_retention_enabled = true;
        assert!(DynamicDesiredRetention::from_deck_config(&config.inner).is_err());
    }

    #[test]
    fn cost_weight_uses_log_interpolation() -> Result<()> {
        let mut config = DeckConfig::default();
        config.inner.fsrs_dynamic_desired_retention_enabled = true;
        config.inner.fsrs_dynamic_desired_retention_params = vec![0.0; COST_ADR_PARAMETER_COUNT];
        config.inner.fsrs_dynamic_desired_retention_weights = vec![0.0, 15.0];
        config.inner.fsrs_dynamic_desired_retention_avg_drs = vec![0.9, 0.8];
        config.inner.fsrs_dynamic_desired_retention_min = 0.75;
        config.inner.fsrs_dynamic_desired_retention_max = 0.95;

        let dynamic_dr = DynamicDesiredRetention::from_deck_config(&config.inner)?.unwrap();
        assert!((dynamic_dr.cost_weight_for_average_dr(0.85)? - 3.0).abs() < 1e-5);
        assert!(dynamic_dr.target_in_dynamic_dr_range(0.85)?);
        assert!(!dynamic_dr.target_in_dynamic_dr_range(0.70)?);
        assert!(!dynamic_dr.target_in_dynamic_dr_range(0.99)?);
        assert_eq!(dynamic_dr.scheduling_target(0.70)?, None);
        assert_eq!(dynamic_dr.policy()?.retention_min, 0.75);
        assert_eq!(dynamic_dr.policy()?.retention_max, 0.95);
        Ok(())
    }

    #[test]
    fn cost_weight_prefers_fsrs_equivalent_calibration() -> Result<()> {
        let mut config = DeckConfig::default();
        config.inner.fsrs_dynamic_desired_retention_enabled = true;
        config.inner.fsrs_dynamic_desired_retention_params = vec![0.0; COST_ADR_PARAMETER_COUNT];
        config.inner.fsrs_dynamic_desired_retention_weights = vec![0.0, 15.0];
        config.inner.fsrs_dynamic_desired_retention_avg_drs = vec![0.9, 0.8];
        config.inner.fsrs_dynamic_desired_retention_fsrs_eq_weights = vec![0.0, 15.0];
        config.inner.fsrs_dynamic_desired_retention_fsrs_eq_drs = vec![0.95, 0.75];
        config.inner.fsrs_dynamic_desired_retention_min = 0.75;
        config.inner.fsrs_dynamic_desired_retention_max = 0.95;

        let dynamic_dr = DynamicDesiredRetention::from_deck_config(&config.inner)?.unwrap();

        assert!((dynamic_dr.cost_weight_for_average_dr(0.8)? - 7.0).abs() < 1e-5);
        assert!(!dynamic_dr.target_in_dynamic_dr_range(0.74)?);
        Ok(())
    }

    #[test]
    fn cost_weight_prefers_fixed_target_calibration() -> Result<()> {
        let mut config = DeckConfig::default();
        config.inner.fsrs_dynamic_desired_retention_enabled = true;
        config.inner.fsrs_dynamic_desired_retention_params = vec![0.0; COST_ADR_PARAMETER_COUNT];
        config.inner.fsrs_dynamic_desired_retention_weights = vec![0.0, 15.0];
        config.inner.fsrs_dynamic_desired_retention_avg_drs = vec![0.9, 0.8];
        config.inner.fsrs_dynamic_desired_retention_fsrs_eq_weights = vec![0.0, 15.0];
        config.inner.fsrs_dynamic_desired_retention_fsrs_eq_drs = vec![0.95, 0.75];
        config
            .inner
            .fsrs_dynamic_desired_retention_fixed_target_weights = vec![64.0, 16.0];
        config.inner.fsrs_dynamic_desired_retention_fixed_target_drs = vec![0.8, 0.9];
        config.inner.fsrs_dynamic_desired_retention_min = 0.75;
        config.inner.fsrs_dynamic_desired_retention_max = 0.95;

        let dynamic_dr = DynamicDesiredRetention::from_deck_config(&config.inner)?.unwrap();

        assert_eq!(dynamic_dr.cost_weight_for_average_dr(0.79)?, 64.0);
        assert_eq!(dynamic_dr.cost_weight_for_average_dr(0.85)?, 16.0);
        assert_eq!(dynamic_dr.supported_target_range(), Some((0.75, 0.9)));
        assert!(!dynamic_dr.target_in_dynamic_dr_range(0.91)?);
        Ok(())
    }

    #[test]
    fn scheduling_target_can_clamp_to_supported_range() -> Result<()> {
        let mut config = DeckConfig::default();
        config.inner.fsrs_dynamic_desired_retention_enabled = true;
        config.inner.fsrs_dynamic_desired_retention_params = vec![0.0; COST_ADR_PARAMETER_COUNT];
        config.inner.fsrs_dynamic_desired_retention_weights = vec![0.0, 15.0];
        config.inner.fsrs_dynamic_desired_retention_avg_drs = vec![0.9, 0.8];
        config.inner.fsrs_dynamic_desired_retention_min = 0.75;
        config.inner.fsrs_dynamic_desired_retention_max = 0.95;
        config.inner.fsrs_dynamic_desired_retention_clamp = true;

        let dynamic_dr = DynamicDesiredRetention::from_deck_config(&config.inner)?.unwrap();

        assert_eq!(dynamic_dr.scheduling_target(0.70)?, Some(0.8));
        assert_eq!(dynamic_dr.scheduling_target(0.99)?, Some(0.9));
        assert_eq!(dynamic_dr.scheduling_target(0.85)?, Some(0.85));
        Ok(())
    }
}
