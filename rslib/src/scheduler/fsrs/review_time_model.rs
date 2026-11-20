// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::sync::Arc;

use fsrs::SimulatorConfig;
use fsrs::FSRS;
use itertools::Itertools;

use super::legacy_fsrs_params;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;
use crate::scheduler::fsrs::params::reviews_for_fsrs;

pub(crate) const R_BUCKET_COUNT: usize = 20;
pub(crate) const S_BUCKET_COUNT_FOR_UI: usize = 1;

const MAX_TAKEN_MILLIS: u32 = 1_200_000;
const MIN_REPS_FOR_REGRESSION: f32 = 2.0;
const MAX_REPS_FOR_REGRESSION: f32 = 30.0;

pub(crate) fn include_repetitions_in_regression(repetitions: f32) -> bool {
    (MIN_REPS_FOR_REGRESSION..=MAX_REPS_FOR_REGRESSION).contains(&repetitions)
}

pub(crate) fn consume_review_repetition(
    prior_review_repetitions: &mut u32,
    is_review: bool,
) -> Option<f32> {
    if !is_review {
        return None;
    }
    let repetitions = *prior_review_repetitions as f32;
    *prior_review_repetitions += 1;
    Some(repetitions)
}

#[derive(Clone)]
pub(crate) struct HelpMeDecideReviewTimeModel {
    // Per-rating linear models:
    // seconds = a + b * (1 - retrievability) + c * stability + d * repetitions + e * difficulty
    coeffs: [[f32; 5]; 4],
    // per-group fallback if regression is not applicable / prediction is invalid
    group_fallback: [f32; 4],
    // per-group representative stability used for flattened output
    group_mean_stability: [f32; 4],
    // per-group representative repetitions used for flattened output
    group_mean_repetitions: [f32; 4],
    // per-group representative difficulty used for flattened output
    group_mean_difficulty: [f32; 4],
    // sample count per R bucket (all grades combined)
    sample_counts: [u32; R_BUCKET_COUNT],
    // Markov transition probabilities P(next_grade | current_grade), flattened row-wise for UI.
    transition_probs: [[f32; 4]; 4],
    transition_counts: [[u32; 4]; 4],
    // P(Hard/Good/Easy | R bucket) + bucket sample size.
    success_grade_probs_by_r_bucket: [[f32; 3]; R_BUCKET_COUNT],
    success_grade_counts_by_r_bucket: [u32; R_BUCKET_COUNT],
    // predicted next-grade weights from transition matrix steady-state.
    grade_weights: [f32; 4],
}

impl HelpMeDecideReviewTimeModel {
    pub(crate) const AGAIN_GROUP: usize = 0;
    pub(crate) const HARD_GROUP: usize = 1;
    pub(crate) const GOOD_GROUP: usize = 2;
    pub(crate) const EASY_GROUP: usize = 3;

    fn group_index_from_grade(grade: usize) -> Option<usize> {
        if (1..=4).contains(&grade) {
            Some(grade - 1)
        } else {
            None
        }
    }

    pub(crate) fn r_bucket(retrievability: f32) -> usize {
        let clamped = retrievability.clamp(0.0, 1.0);
        let base_index = ((clamped * 100.0).min(99.9999) / 5.0).floor() as usize;
        // Bucket 0 represents [95%,100%], bucket 1 [90%,95%), etc.
        R_BUCKET_COUNT.saturating_sub(1 + base_index)
    }

    pub(crate) fn from_samples(
        samples: &[(f32, f32, f32, f32, usize, f32)],
        transition_counts: [[u32; 4]; 4],
        enforce_monotonic_success_grade_probs: bool,
        default_review_costs: [f32; 4],
    ) -> Self {
        let mut sample_counts = [0u32; R_BUCKET_COUNT];
        let mut success_grade_counts_by_r_bucket = [[0u32; 3]; R_BUCKET_COUNT];
        let mut group_sum = [0.0f32; 4];
        let mut group_count = [0u32; 4];
        let mut sum_y = [0.0f32; 4];
        let mut group_sum_stability = [0.0f32; 4];
        let mut group_sum_repetitions = [0.0f32; 4];
        let mut group_sum_difficulty = [0.0f32; 4];
        let mut sum_x1 = [0.0f32; 4];
        let mut sum_x2 = [0.0f32; 4];
        let mut sum_x3 = [0.0f32; 4];
        let mut sum_x4 = [0.0f32; 4];
        let mut sum_x1x1 = [0.0f32; 4];
        let mut sum_x2x2 = [0.0f32; 4];
        let mut sum_x3x3 = [0.0f32; 4];
        let mut sum_x4x4 = [0.0f32; 4];
        let mut sum_x1x2 = [0.0f32; 4];
        let mut sum_x1x3 = [0.0f32; 4];
        let mut sum_x1x4 = [0.0f32; 4];
        let mut sum_x2x3 = [0.0f32; 4];
        let mut sum_x2x4 = [0.0f32; 4];
        let mut sum_x3x4 = [0.0f32; 4];
        let mut sum_x1y = [0.0f32; 4];
        let mut sum_x2y = [0.0f32; 4];
        let mut sum_x3y = [0.0f32; 4];
        let mut sum_x4y = [0.0f32; 4];

        for (retrievability, stability, repetitions, difficulty, grade, seconds) in samples {
            let Some(group_idx) = Self::group_index_from_grade(*grade) else {
                continue;
            };
            let rb = Self::r_bucket(*retrievability);
            sample_counts[rb] += 1;
            if (2..=4).contains(grade) {
                success_grade_counts_by_r_bucket[rb][*grade - 2] += 1;
            }
            group_sum[group_idx] += *seconds;
            group_count[group_idx] += 1;
            group_sum_stability[group_idx] += *stability;
            group_sum_repetitions[group_idx] += *repetitions;
            group_sum_difficulty[group_idx] += *difficulty;
            let x1 = 1.0 - retrievability.clamp(0.0, 1.0);
            let x2 = stability.max(0.0);
            let x3 = repetitions.max(0.0);
            let x4 = difficulty.max(0.0);
            let y = *seconds;
            sum_x1[group_idx] += x1;
            sum_x2[group_idx] += x2;
            sum_x3[group_idx] += x3;
            sum_x4[group_idx] += x4;
            sum_y[group_idx] += y;
            sum_x1x1[group_idx] += x1 * x1;
            sum_x2x2[group_idx] += x2 * x2;
            sum_x3x3[group_idx] += x3 * x3;
            sum_x4x4[group_idx] += x4 * x4;
            sum_x1x2[group_idx] += x1 * x2;
            sum_x1x3[group_idx] += x1 * x3;
            sum_x1x4[group_idx] += x1 * x4;
            sum_x2x3[group_idx] += x2 * x3;
            sum_x2x4[group_idx] += x2 * x4;
            sum_x3x4[group_idx] += x3 * x4;
            sum_x1y[group_idx] += x1 * y;
            sum_x2y[group_idx] += x2 * y;
            sum_x3y[group_idx] += x3 * y;
            sum_x4y[group_idx] += x4 * y;
        }

        let mut coeffs = [[0.0f32; 5]; 4];
        let mut group_fallback = [0.0f32; 4];
        let mut group_mean_stability = [0.0f32; 4];
        let mut group_mean_repetitions = [0.0f32; 4];
        let mut group_mean_difficulty = [0.0f32; 4];
        for g in 0..4 {
            let fallback = if group_count[g] > 0 {
                group_sum[g] / group_count[g] as f32
            } else {
                default_review_costs[g]
            };
            group_fallback[g] = fallback;
            group_mean_stability[g] = if group_count[g] > 0 {
                group_sum_stability[g] / group_count[g] as f32
            } else {
                0.0
            };
            group_mean_repetitions[g] = if group_count[g] > 0 {
                group_sum_repetitions[g] / group_count[g] as f32
            } else {
                0.0
            };
            group_mean_difficulty[g] = if group_count[g] > 0 {
                group_sum_difficulty[g] / group_count[g] as f32
            } else {
                0.0
            };
            if group_count[g] >= 5 {
                let n = group_count[g] as f32;
                let matrix = [
                    [n, sum_x1[g], sum_x2[g], sum_x3[g], sum_x4[g]],
                    [
                        sum_x1[g],
                        sum_x1x1[g],
                        sum_x1x2[g],
                        sum_x1x3[g],
                        sum_x1x4[g],
                    ],
                    [
                        sum_x2[g],
                        sum_x1x2[g],
                        sum_x2x2[g],
                        sum_x2x3[g],
                        sum_x2x4[g],
                    ],
                    [
                        sum_x3[g],
                        sum_x1x3[g],
                        sum_x2x3[g],
                        sum_x3x3[g],
                        sum_x3x4[g],
                    ],
                    [
                        sum_x4[g],
                        sum_x1x4[g],
                        sum_x2x4[g],
                        sum_x3x4[g],
                        sum_x4x4[g],
                    ],
                ];
                let vector = [sum_y[g], sum_x1y[g], sum_x2y[g], sum_x3y[g], sum_x4y[g]];
                if let Some(solution) = Self::solve_5x5(matrix, vector) {
                    coeffs[g] = solution;
                    continue;
                }
            }
            coeffs[g] = [fallback, 0.0, 0.0, 0.0, 0.0];
        }

        let transition_probs = Self::transition_probabilities_from_counts(transition_counts);
        let grade_weights = Self::stationary_distribution(transition_probs)
            .unwrap_or_else(|| Self::grade_weights_from_counts(group_count));
        let (success_grade_probs_by_r_bucket, success_grade_counts_by_r_bucket) =
            Self::success_grade_probs_by_r_bucket(
                success_grade_counts_by_r_bucket,
                enforce_monotonic_success_grade_probs,
            );
        Self {
            coeffs,
            group_fallback,
            group_mean_stability,
            group_mean_repetitions,
            group_mean_difficulty,
            sample_counts,
            transition_probs,
            transition_counts,
            success_grade_probs_by_r_bucket,
            success_grade_counts_by_r_bucket,
            grade_weights,
        }
    }

    fn grade_weights_from_counts(group_count: [u32; 4]) -> [f32; 4] {
        let total = group_count.iter().sum::<u32>();
        if total == 0 {
            return [0.25; 4];
        }
        group_count.map(|count| count as f32 / total as f32)
    }

    fn transition_probabilities_from_counts(counts: [[u32; 4]; 4]) -> [[f32; 4]; 4] {
        let mut probs = [[0.0; 4]; 4];
        let mut global_next = [0u32; 4];
        for row in counts {
            for (idx, count) in row.into_iter().enumerate() {
                global_next[idx] += count;
            }
        }
        let global_total = global_next.iter().sum::<u32>();
        let global_fallback = if global_total == 0 {
            [0.25; 4]
        } else {
            global_next.map(|count| count as f32 / global_total as f32)
        };

        for row_idx in 0..4 {
            let row_total = counts[row_idx].iter().sum::<u32>();
            if row_total == 0 {
                probs[row_idx] = global_fallback;
            } else {
                probs[row_idx] = counts[row_idx].map(|count| count as f32 / row_total as f32);
            }
        }
        probs
    }

    fn stationary_distribution(transition_probs: [[f32; 4]; 4]) -> Option<[f32; 4]> {
        let mut dist = [0.25f32; 4];
        for _ in 0..128 {
            let mut next = [0.0f32; 4];
            for from in 0..4 {
                for to in 0..4 {
                    next[to] += dist[from] * transition_probs[from][to];
                }
            }
            let sum = next.iter().sum::<f32>();
            if !sum.is_finite() || sum <= f32::EPSILON {
                return None;
            }
            for v in &mut next {
                *v /= sum;
            }
            let delta = (0..4).map(|i| (next[i] - dist[i]).abs()).sum::<f32>();
            dist = next;
            if delta <= 1e-6 {
                break;
            }
        }
        if dist.iter().all(|v| v.is_finite() && *v >= 0.0) {
            Some(dist)
        } else {
            None
        }
    }

    fn success_grade_probs_by_r_bucket(
        counts: [[u32; 3]; R_BUCKET_COUNT],
        enforce_monotonic: bool,
    ) -> ([[f32; 3]; R_BUCKET_COUNT], [u32; R_BUCKET_COUNT]) {
        let mut raw_probs = [[0.0f32; 3]; R_BUCKET_COUNT];
        let mut totals = [0u32; R_BUCKET_COUNT];
        for (rb, row) in counts.into_iter().enumerate() {
            let row_total = row.iter().sum::<u32>();
            totals[rb] = row_total;
            // Laplace smoothing to avoid zero probabilities in geometric blend.
            let denom = row_total as f32 + 3.0;
            raw_probs[rb] = row.map(|count| (count as f32 + 1.0) / denom);
        }

        // Reliability shrinkage for sparse low-R buckets:
        // blend bucket-local estimate with a distance-weighted neighborhood prior.
        const SHRINK_K: f32 = 100.0;
        let mut probs = [[0.0f32; 3]; R_BUCKET_COUNT];
        for rb in 0..R_BUCKET_COUNT {
            let mut prior = [0.0f32; 3];
            let mut prior_weight = 0.0f32;
            for nb in 0..R_BUCKET_COUNT {
                let distance = (rb as i32 - nb as i32).unsigned_abs() as f32;
                let kernel = 1.0 / (1.0 + distance);
                let weight = kernel * (totals[nb] as f32 + 1.0);
                for (g, value) in prior.iter_mut().enumerate() {
                    *value += raw_probs[nb][g] * weight;
                }
                prior_weight += weight;
            }
            if prior_weight > f32::EPSILON {
                for value in &mut prior {
                    *value /= prior_weight;
                }
            } else {
                prior = [1.0 / 3.0; 3];
            }

            let local_weight = totals[rb] as f32 / (totals[rb] as f32 + SHRINK_K);
            let mut blended = [0.0f32; 3];
            for g in 0..3 {
                blended[g] = local_weight * raw_probs[rb][g] + (1.0 - local_weight) * prior[g];
            }
            probs[rb] = Self::normalize_triplet(blended);
        }

        if enforce_monotonic {
            let mut hard = [0.0f32; R_BUCKET_COUNT];
            let mut easy = [0.0f32; R_BUCKET_COUNT];
            let mut weights = [0.0f32; R_BUCKET_COUNT];
            for rb in 0..R_BUCKET_COUNT {
                hard[rb] = probs[rb][0];
                easy[rb] = probs[rb][2];
                weights[rb] = totals[rb] as f32 + 1.0;
            }
            let hard = Self::isotonic_increasing(hard, weights);
            let neg_easy = easy.map(|v| -v);
            let neg_easy = Self::isotonic_increasing(neg_easy, weights);
            let easy = neg_easy.map(|v| -v);
            for rb in 0..R_BUCKET_COUNT {
                let mut h = hard[rb].clamp(0.0, 1.0);
                let mut e = easy[rb].clamp(0.0, 1.0);
                if h + e >= 0.999 {
                    let scale = 0.999 / (h + e);
                    h *= scale;
                    e *= scale;
                }
                let g = 1.0 - h - e;
                probs[rb] = Self::normalize_triplet([h, g.max(0.0), e]);
            }
        }
        (probs, totals)
    }

    fn isotonic_increasing(
        values: [f32; R_BUCKET_COUNT],
        weights: [f32; R_BUCKET_COUNT],
    ) -> [f32; R_BUCKET_COUNT] {
        let mut starts: Vec<usize> = Vec::with_capacity(R_BUCKET_COUNT);
        let mut ends: Vec<usize> = Vec::with_capacity(R_BUCKET_COUNT);
        let mut sums: Vec<f32> = Vec::with_capacity(R_BUCKET_COUNT);
        let mut ws: Vec<f32> = Vec::with_capacity(R_BUCKET_COUNT);

        for i in 0..R_BUCKET_COUNT {
            starts.push(i);
            ends.push(i);
            sums.push(values[i] * weights[i]);
            ws.push(weights[i]);
            while sums.len() >= 2 {
                let n = sums.len();
                let avg_prev = sums[n - 2] / ws[n - 2];
                let avg_curr = sums[n - 1] / ws[n - 1];
                if avg_prev <= avg_curr {
                    break;
                }
                let merged_sum = sums[n - 2] + sums[n - 1];
                let merged_w = ws[n - 2] + ws[n - 1];
                sums[n - 2] = merged_sum;
                ws[n - 2] = merged_w;
                ends[n - 2] = ends[n - 1];
                sums.pop();
                ws.pop();
                starts.pop();
                ends.pop();
            }
        }

        let mut out = [0.0f32; R_BUCKET_COUNT];
        for block in 0..sums.len() {
            let avg = sums[block] / ws[block];
            for value in out.iter_mut().take(ends[block] + 1).skip(starts[block]) {
                *value = avg;
            }
        }
        out
    }

    fn normalize_triplet(values: [f32; 3]) -> [f32; 3] {
        let sum = values.iter().sum::<f32>();
        if !sum.is_finite() || sum <= f32::EPSILON {
            [1.0 / 3.0; 3]
        } else {
            [values[0] / sum, values[1] / sum, values[2] / sum]
        }
    }

    fn blend_weight_for_r_bucket(&self, r_bucket: usize) -> f32 {
        let r_count = self.success_grade_counts_by_r_bucket[r_bucket] as f32;
        let t_count = self
            .transition_counts
            .iter()
            .flatten()
            .copied()
            .sum::<u32>() as f32;
        let r_conf = r_count / (r_count + 20.0);
        let t_conf = t_count / (t_count + 50.0);
        let denom = r_conf + t_conf;
        if denom <= f32::EPSILON {
            0.5
        } else {
            (t_conf / denom).clamp(0.0, 1.0)
        }
    }

    fn solve_5x5(matrix: [[f32; 5]; 5], vector: [f32; 5]) -> Option<[f32; 5]> {
        let mut m = [[0.0f32; 6]; 5];
        for row in 0..5 {
            for col in 0..5 {
                m[row][col] = matrix[row][col];
            }
            m[row][5] = vector[row];
        }

        for pivot in 0..5 {
            let mut best = pivot;
            for row in (pivot + 1)..5 {
                if m[row][pivot].abs() > m[best][pivot].abs() {
                    best = row;
                }
            }
            if m[best][pivot].abs() <= 1e-8 {
                return None;
            }
            if best != pivot {
                m.swap(best, pivot);
            }
            let pivot_value = m[pivot][pivot];
            for value in m[pivot].iter_mut().skip(pivot) {
                *value /= pivot_value;
            }
            let pivot_row = m[pivot];
            for (row, row_values) in m.iter_mut().enumerate() {
                if row == pivot {
                    continue;
                }
                let factor = row_values[pivot];
                if factor.abs() <= f32::EPSILON {
                    continue;
                }
                for (col, value) in row_values.iter_mut().enumerate().skip(pivot) {
                    *value -= factor * pivot_row[col];
                }
            }
        }

        let solution = [m[0][5], m[1][5], m[2][5], m[3][5], m[4][5]];
        if solution.iter().all(|v| v.is_finite()) {
            Some(solution)
        } else {
            None
        }
    }

    fn predict_seconds_for_group(
        &self,
        group_idx: usize,
        retrievability: f32,
        stability: f32,
        repetitions: f32,
        difficulty: f32,
    ) -> f32 {
        let [a, b, c, d, e] = self.coeffs[group_idx];
        let x1 = 1.0 - retrievability.clamp(0.0, 1.0);
        let x2 = stability.max(0.0);
        let x3 = repetitions.max(0.0);
        let x4 = difficulty.max(0.0);
        let predicted = a + b * x1 + c * x2 + d * x3 + e * x4;
        if predicted.is_finite() && predicted > 0.0 {
            predicted
        } else {
            self.group_fallback[group_idx]
        }
    }

    pub(crate) fn review_cost_for_rating(
        &self,
        retrievability: f32,
        stability: f32,
        repetitions: f32,
        difficulty: f32,
        rating: usize,
    ) -> f32 {
        let Some(group_idx) = Self::group_index_from_grade(rating) else {
            return 0.0;
        };
        self.predict_seconds_for_group(
            group_idx,
            retrievability,
            stability,
            repetitions,
            difficulty,
        )
    }

    #[cfg(test)]
    pub(crate) fn cost_for(
        &self,
        retrievability: f32,
        stability: f32,
        repetitions: f32,
        difficulty: f32,
        grade: usize,
    ) -> f32 {
        self.review_cost_for_rating(retrievability, stability, repetitions, difficulty, grade)
    }

    pub(crate) fn coeffs_for_group(&self, group_idx: usize) -> [f32; 5] {
        self.coeffs[group_idx]
    }

    pub(crate) fn grade_flattened(&self) -> [Vec<f32>; 4] {
        let mut again = Vec::with_capacity(R_BUCKET_COUNT * S_BUCKET_COUNT_FOR_UI);
        let mut hard = Vec::with_capacity(R_BUCKET_COUNT * S_BUCKET_COUNT_FOR_UI);
        let mut good = Vec::with_capacity(R_BUCKET_COUNT * S_BUCKET_COUNT_FOR_UI);
        let mut easy = Vec::with_capacity(R_BUCKET_COUNT * S_BUCKET_COUNT_FOR_UI);
        for rb in 0..R_BUCKET_COUNT {
            let retrievability = (1.0 - ((rb as f32 + 0.5) * 0.05)).clamp(0.0, 1.0);
            again.push(self.predict_seconds_for_group(
                Self::AGAIN_GROUP,
                retrievability,
                self.group_mean_stability[Self::AGAIN_GROUP],
                self.group_mean_repetitions[Self::AGAIN_GROUP],
                self.group_mean_difficulty[Self::AGAIN_GROUP],
            ));
            hard.push(self.predict_seconds_for_group(
                Self::HARD_GROUP,
                retrievability,
                self.group_mean_stability[Self::HARD_GROUP],
                self.group_mean_repetitions[Self::HARD_GROUP],
                self.group_mean_difficulty[Self::HARD_GROUP],
            ));
            good.push(self.predict_seconds_for_group(
                Self::GOOD_GROUP,
                retrievability,
                self.group_mean_stability[Self::GOOD_GROUP],
                self.group_mean_repetitions[Self::GOOD_GROUP],
                self.group_mean_difficulty[Self::GOOD_GROUP],
            ));
            easy.push(self.predict_seconds_for_group(
                Self::EASY_GROUP,
                retrievability,
                self.group_mean_stability[Self::EASY_GROUP],
                self.group_mean_repetitions[Self::EASY_GROUP],
                self.group_mean_difficulty[Self::EASY_GROUP],
            ));
        }
        [again, hard, good, easy]
    }

    pub(crate) fn sample_counts_flattened(&self) -> Vec<u32> {
        let mut counts = Vec::with_capacity(R_BUCKET_COUNT * S_BUCKET_COUNT_FOR_UI);
        for rb in 0..R_BUCKET_COUNT {
            counts.push(self.sample_counts[rb]);
        }
        counts
    }

    pub(crate) fn grade_weights(&self) -> [f32; 4] {
        self.grade_weights
    }

    pub(crate) fn transition_probs_flattened(&self) -> Vec<f32> {
        self.transition_probs
            .iter()
            .flat_map(|row| row.iter().copied())
            .collect_vec()
    }

    pub(crate) fn transition_counts_flattened(&self) -> Vec<u32> {
        self.transition_counts
            .iter()
            .flat_map(|row| row.iter().copied())
            .collect_vec()
    }

    pub(crate) fn success_review_rating_prob(&self, fallback: [f32; 3]) -> [f32; 3] {
        let [again, hard, good, easy] = self.grade_weights;
        let success = hard + good + easy;
        if success <= 1e-6 || !again.is_finite() {
            return fallback;
        }
        let mut out = [hard / success, good / success, easy / success];
        let sum = out.iter().sum::<f32>();
        if !sum.is_finite() || sum <= f32::EPSILON {
            fallback
        } else {
            out.iter_mut().for_each(|v| *v /= sum);
            out
        }
    }

    pub(crate) fn success_review_rating_prob_for_retrievability(
        &self,
        retrievability: f32,
        fallback: [f32; 3],
        blend_alpha_override: Option<f32>,
    ) -> [f32; 3] {
        let rb = Self::r_bucket(retrievability);
        let pr = self.success_grade_probs_by_r_bucket[rb];
        let pt = self.success_review_rating_prob(fallback);
        let alpha = blend_alpha_override
            .map(|v| v.clamp(0.0, 1.0))
            .unwrap_or_else(|| self.blend_weight_for_r_bucket(rb));
        let mut scores = [0.0f32; 3];
        for i in 0..3 {
            scores[i] = pr[i].powf(1.0 - alpha) * pt[i].powf(alpha);
        }
        let sum = scores.iter().sum::<f32>();
        if !sum.is_finite() || sum <= f32::EPSILON {
            fallback
        } else {
            scores.map(|v| v / sum)
        }
    }

    pub(crate) fn success_grade_probs_flattened(&self) -> Vec<f32> {
        self.success_grade_probs_by_r_bucket
            .iter()
            .flat_map(|row| row.iter().copied())
            .collect_vec()
    }

    pub(crate) fn success_grade_counts_flattened(&self) -> Vec<u32> {
        self.success_grade_counts_by_r_bucket.to_vec()
    }
}

pub(crate) fn build_help_me_decide_review_time_model_from_revlogs(
    revlogs: &[RevlogEntry],
    params: &[f32],
    next_day_at: TimestampSecs,
    enforce_monotonic_success_grade_probs: bool,
    default_review_costs: [f32; 4],
) -> Result<HelpMeDecideReviewTimeModel> {
    let fsrs = FSRS::new(legacy_fsrs_params(params))?;
    let mut samples = Vec::new();
    let mut transition_counts = [[0u32; 4]; 4];

    for (_cid, group) in &revlogs.iter().cloned().chunk_by(|r| r.cid) {
        let entries = group.collect_vec();
        let Some(output) = reviews_for_fsrs(entries, next_day_at, false, TimestampMillis(0), false)
        else {
            continue;
        };
        if !output.revlogs_complete {
            continue;
        }
        let Some((_, item)) = output.fsrs_items.last() else {
            continue;
        };
        let item = item.clone();
        let states = fsrs.historical_memory_states(item.clone(), None)?;
        if output.filtered_revlogs.len() != item.reviews.len() || states.len() != item.reviews.len()
        {
            continue;
        }

        let mut prior_review_repetitions = output
            .filtered_revlogs
            .first()
            .map(|entry| (entry.review_kind == RevlogReviewKind::Review) as u32)
            .unwrap_or(0);
        let mut previous_review_grade: Option<usize> =
            output.filtered_revlogs.first().and_then(|entry| {
                let grade = entry.button_chosen as usize;
                if entry.review_kind == RevlogReviewKind::Review && (1..=4).contains(&grade) {
                    Some(grade)
                } else {
                    None
                }
            });
        for idx in 1..output.filtered_revlogs.len() {
            let entry = &output.filtered_revlogs[idx];
            let Some(repetitions) = consume_review_repetition(
                &mut prior_review_repetitions,
                entry.review_kind == RevlogReviewKind::Review,
            ) else {
                continue;
            };
            if entry.taken_millis == 0 || entry.taken_millis >= MAX_TAKEN_MILLIS {
                continue;
            }
            let grade = entry.button_chosen as usize;
            if !(1..=4).contains(&grade) {
                continue;
            }
            if let Some(prev_grade) = previous_review_grade {
                transition_counts[prev_grade - 1][grade - 1] += 1;
            }
            previous_review_grade = Some(grade);
            let previous_state = states[idx - 1];
            let retrievability =
                fsrs.current_retrievability(previous_state, item.reviews[idx].delta_t);
            let stability = previous_state.stability;
            let difficulty = previous_state.difficulty;
            if !include_repetitions_in_regression(repetitions) {
                continue;
            }
            let seconds = entry.taken_millis as f32 / 1000.0;
            samples.push((
                retrievability,
                stability,
                repetitions,
                difficulty,
                grade,
                seconds,
            ));
        }
    }

    Ok(HelpMeDecideReviewTimeModel::from_samples(
        &samples,
        transition_counts,
        enforce_monotonic_success_grade_probs,
        default_review_costs,
    ))
}

pub(crate) fn install_review_time_cost_fn(
    config: &mut SimulatorConfig,
    model: Arc<HelpMeDecideReviewTimeModel>,
) {
    config.review_rating_cost_fn = Some(fsrs::ReviewRatingCostFn::new(
        move |card, rating, retrievability| {
            model.review_cost_for_rating(
                retrievability,
                card.stability,
                card.reps as f32,
                card.difficulty,
                rating,
            )
        },
    ));
}
