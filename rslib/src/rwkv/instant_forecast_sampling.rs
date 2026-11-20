// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Approximate strata of aggregate answer outcomes for the fast forecast.

use super::simulation_unit_hash;

pub(super) const PREVIEW_LIMIT: usize = 512;

/// Draw shared day-to-day variation, conditioned approximately on a low,
/// central, or high aggregate outcome over the whole forecast, and return the
/// final per-day z targets (already scaled by `noise_scale`).
///
/// Every day has the same weight. A simulated day lasts until the queue is
/// empty or the limits are hit, so the length of the sampled historical
/// session does not predict that day's answer count; weighting days by it
/// (as an earlier version did) was therefore not justified. With equal
/// weights the noise sums to exactly zero, and the aggregate shift is
/// `(stratum - 1) * sqrt(noise_scale^2 + bias_kappa^2)` in units of the
/// model's aggregate sigma, i.e. one true aggregate standard deviation when the
/// calibration is right.
///
/// The shift is spread over days with a cumulative envelope
/// `c_i = sqrt(noise_scale^2 * i + bias_kappa^2 * i^2 / days)`: the cumulative
/// separation after `t` days equals the pointwise one-sigma spread of the
/// cumulative residual. A linear allocation would make the bands too narrow
/// early in the horizon by a factor of `sqrt(t / days)`. The `bias_kappa` term
/// is a systematic (perfectly day-correlated) recall error, whose cumulative
/// effect grows linearly in the number of days.
pub(super) fn day_deviations(
    days: usize,
    seed: u32,
    stratum: usize,
    noise_scale: f64,
    bias_kappa: f64,
) -> Vec<f64> {
    let z = (0..days)
        .map(|day| {
            let u1 =
                simulation_unit_hash(i64::from(seed), day as i64, 0x1A63).max(f64::MIN_POSITIVE);
            let u2 = simulation_unit_hash(i64::from(seed), day as i64, 0x6DB5);
            (-2.0 * u1.ln()).sqrt() * (std::f64::consts::TAU * u2).cos()
        })
        .collect::<Vec<_>>();
    let mean = z.iter().sum::<f64>() / days.max(1) as f64;
    let envelope = |i: usize| {
        (noise_scale * noise_scale * i as f64
            + bias_kappa * bias_kappa * (i * i) as f64 / days.max(1) as f64)
            .sqrt()
    };
    let desired = stratum as f64 - 1.0;
    z.into_iter()
        .enumerate()
        .map(|(day, z)| noise_scale * (z - mean) + desired * (envelope(day + 1) - envelope(day)))
        .collect()
}

/// Approximate sequential conditioning on a target aggregate deviation.
/// The queue continues to use the unconditioned model probabilities.
pub(super) struct AnswerStratum {
    deviation: f64,
    expected_answers: usize,
    preview_variance: f64,
    observed: usize,
    observed_variance: f64,
    residual: f64,
}

impl AnswerStratum {
    pub(super) fn new(deviation: f64, expected_answers: usize, recalls: &[f32]) -> Self {
        let preview_variance = recalls
            .iter()
            .map(|r| {
                let p = f64::from(r.clamp(0.0, 1.0));
                p * (1.0 - p)
            })
            .sum::<f64>()
            / recalls.len().max(1) as f64;
        Self {
            deviation,
            expected_answers,
            preview_variance,
            observed: 0,
            observed_variance: 0.0,
            residual: 0.0,
        }
    }

    /// Conditional probability for one answer. The adjustment changes with
    /// observed outcomes and the remaining work; it is not a fixed R offset.
    pub(super) fn probability(&self, recall: f32, queued: usize) -> f32 {
        let p = f64::from(recall.clamp(0.0, 1.0));
        if p == 0.0 || p == 1.0 {
            return p as f32;
        }
        let remaining = self
            .expected_answers
            .saturating_sub(self.observed)
            .max(queued)
            .max(1) as f64;
        // Retain a small preview prior while adapting to the actual predictions.
        let variance_per_answer =
            (16.0 * self.preview_variance + self.observed_variance) / (16.0 + self.observed as f64);
        let remaining_variance = remaining * variance_per_answer;
        let target = self.deviation * (self.observed_variance + remaining_variance).sqrt();
        let tilt = ((target - self.residual) / remaining_variance.max(1.0)).clamp(-6.0, 6.0);
        (1.0 / (1.0 + (-((p / (1.0 - p)).ln() + tilt)).exp())) as f32
    }

    pub(super) fn observe(&mut self, recall: f32, success: bool) {
        let p = f64::from(recall.clamp(0.0, 1.0));
        self.observed += 1;
        self.observed_variance += p * (1.0 - p);
        self.residual += f64::from(u8::from(success)) - p;
    }

    pub(super) fn log_summary(&self, path: usize, day: usize) {
        let observed_z = if self.observed_variance > 0.0 {
            self.residual / self.observed_variance.sqrt()
        } else {
            0.0
        };
        tracing::debug!(
            path,
            day,
            answers = self.observed,
            expected_answers = self.expected_answers,
            target_z = self.deviation,
            observed_z,
            "RWKV Instant forecast answer stratum"
        );
    }
}

#[cfg(test)]
mod tests {
    use super::super::workload_comparison::paired_grade;
    use super::*;
    use crate::rwkv::RwkvWorkloadReviewModel;

    #[test]
    fn daily_deviations_have_the_requested_aggregate() {
        let days = 30;
        for (noise_scale, kappa) in [(1.0, 0.0), (0.8, 0.0), (1.2, 0.5), (0.0, 0.7)] {
            let paths = (0..3)
                .map(|stratum| day_deviations(days, 42, stratum, noise_scale, kappa))
                .collect::<Vec<_>>();
            let sigma = (noise_scale * noise_scale + kappa * kappa).sqrt();
            for (stratum, path) in paths.iter().enumerate() {
                assert_eq!(path.len(), days);
                let aggregate = path.iter().sum::<f64>() / (days as f64).sqrt();
                assert!(
                    (aggregate - (stratum as f64 - 1.0) * sigma).abs() < 1e-10,
                    "{noise_scale} {kappa} {stratum} {aggregate}"
                );
            }
            assert_eq!(paths[1], day_deviations(days, 42, 1, noise_scale, kappa));
            if noise_scale > 0.0 {
                assert_ne!(paths[1], day_deviations(days, 43, 1, noise_scale, kappa));
            }
        }
        let central = day_deviations(days, 42, 1, 1.0, 0.0);
        assert!(central.iter().any(|z| *z < 0.0) && central.iter().any(|z| *z > 0.0));
    }

    #[test]
    fn strata_share_identical_noise() {
        let days = 20;
        let low = day_deviations(days, 7, 0, 1.1, 0.3);
        let central = day_deviations(days, 7, 1, 1.1, 0.3);
        let high = day_deviations(days, 7, 2, 1.1, 0.3);
        let envelope =
            |i: usize| (1.1_f64 * 1.1 * i as f64 + 0.3 * 0.3 * (i * i) as f64 / days as f64).sqrt();
        for day in 0..days {
            let shift = envelope(day + 1) - envelope(day);
            assert!((central[day] - low[day] - shift).abs() < 1e-12);
            assert!((high[day] - central[day] - shift).abs() < 1e-12);
        }
    }

    #[test]
    fn cumulative_shift_follows_the_pointwise_envelope() {
        let days = 40;
        let central = day_deviations(days, 9, 1, 0.9, 0.0);
        let high = day_deviations(days, 9, 2, 0.9, 0.0);
        let mut cumulative = 0.0;
        for t in 1..=days {
            cumulative += high[t - 1] - central[t - 1];
            let expected = (0.9_f64 * 0.9 * t as f64).sqrt();
            assert!((cumulative - expected).abs() < 1e-10, "{t}");
        }
        // The bias term makes the final cumulative separation larger.
        let biased = day_deviations(days, 9, 2, 0.9, 0.5);
        let biased_central = day_deviations(days, 9, 1, 0.9, 0.5);
        let biased_total = biased
            .iter()
            .zip(&biased_central)
            .map(|(a, b)| a - b)
            .sum::<f64>();
        assert!(biased_total > cumulative);
        assert!(
            (biased_total - (0.9_f64 * 0.9 * days as f64 + 0.25 * days as f64).sqrt()).abs()
                < 1e-10
        );
    }

    #[test]
    fn conditions_mixed_answers_on_their_aggregate_residual() {
        let recalls = (0..200)
            .map(|i| if i % 2 == 0 { 0.8 } else { 0.6 })
            .collect::<Vec<_>>();
        let model = RwkvWorkloadReviewModel {
            grade_seconds: [1.0; 4],
            bucket_probabilities: (0..20).map(|b| (b, [0.0, 0.0, 1.0, 0.0])).collect(),
        };
        let outcomes = [-1.0, 0.0, 1.0].map(|z| {
            let mut stratum = AnswerStratum::new(z, recalls.len(), &recalls);
            let mut successes = 0;
            for (i, &r) in recalls.iter().enumerate() {
                let p = stratum.probability(r, recalls.len() - i);
                let success = paired_grade(&model, p, i as i64, 0, 42) != 1;
                successes += usize::from(success);
                stratum.observe(r, success);
            }
            (
                stratum.residual / stratum.observed_variance.sqrt(),
                successes,
            )
        });
        assert!(
            outcomes[0].0 < outcomes[1].0 && outcomes[1].0 < outcomes[2].0,
            "{outcomes:?}"
        );
        for (index, (z, successes)) in outcomes.iter().enumerate() {
            assert!((z - (index as f64 - 1.0)).abs() < 0.6, "{outcomes:?}");
            assert!((1..recalls.len()).contains(successes));
        }
        let sampler = AnswerStratum::new(1.0, 2, &[0.0, 1.0]);
        assert_eq!(sampler.probability(0.0, 2), 0.0);
        assert_eq!(sampler.probability(1.0, 2), 1.0);
    }
}
