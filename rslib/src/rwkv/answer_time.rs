// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Answer-time prediction for the small model's optional answer-time head
//! (`time_head.f32.bin` next to the weights).
//!
//! The head reads the frozen trunk at a query plus 26 causal features of the
//! history before it. [`AnswerTimeHistory`] computes those features with the
//! exact rule of the release package's reference (`answer_time_numpy.py`):
//! the card's last three recorded answer times, its review count and last
//! grade, the user's running median time per grade, the user's previous time
//! and running average, elapsed days and the user's review index. Only
//! reviews with a recorded time (duration > 0) enter the time statistics.
//! The output is a distribution over 16 log-time bins per grade.

use std::collections::BTreeMap;
use std::collections::HashMap;

/// Number of explicit history features the head reads.
pub const ANSWER_TIME_FEATURES: usize = 26;
/// Log-time bins per grade.
pub const ANSWER_TIME_BINS: usize = 16;
/// Bins 0..=6 end at 3,891 ms ("fast").
pub const ANSWER_TIME_FAST_BINS: usize = 7;
/// Index of the elapsed-days feature; the next one is the new-card flag.
pub(crate) const ANSWER_TIME_ELAPSED_FEATURE: usize = 23;
/// The only answer-time head file format this runtime reads.
pub(crate) const ANSWER_TIME_FORMAT_VERSION: f32 = 1.0;

/// ln 8 s: the centre of every ln-time feature (Python `math.log(8.0)`).
const LN8: f64 = 2.0794415416798357;

fn ln_seconds(duration_millis: i64) -> f64 {
    (duration_millis.max(1) as f64 / 1000.0).ln()
}

/// Exact median of the recorded ln times, kept as counts of integer
/// durations (ln time is monotonic in the duration, so the order statistics
/// are those of the durations). Matches the reference's two-heap median: an
/// even count averages the two middle ln values.
#[derive(Clone, Default)]
struct DurationMedian {
    counts: BTreeMap<i64, u64>,
    total: u64,
}

impl DurationMedian {
    fn push(&mut self, duration_millis: i64) {
        *self.counts.entry(duration_millis).or_default() += 1;
        self.total += 1;
    }

    fn median(&self) -> Option<f64> {
        if self.total == 0 {
            return None;
        }
        // The middle order statistics (the same one for an odd count).
        let lower = (self.total - 1) / 2;
        let upper = self.total / 2;
        let mut seen = 0;
        let mut lower_value = None;
        for (&duration, &count) in &self.counts {
            seen += count;
            if lower_value.is_none() && seen > lower {
                lower_value = Some(duration);
            }
            if seen > upper {
                let low = ln_seconds(lower_value.unwrap_or(duration));
                return Some(if lower == upper {
                    low
                } else {
                    (low + ln_seconds(duration)) / 2.0
                });
            }
        }
        None
    }
}

#[derive(Clone, Default)]
struct CardTimes {
    /// Most recent first.
    last: [f64; 3],
    len: usize,
    reviews: u64,
    last_grade: i64,
}

/// The user's review history as the answer-time head sees it. Push every
/// review of the canonical RWKV history in order, then ask for the features
/// of a query.
#[derive(Clone)]
pub struct AnswerTimeHistory {
    per_grade: [DurationMedian; 4],
    every: DurationMedian,
    grade_counts: [u64; 4],
    cards: HashMap<i64, CardTimes>,
    previous: Option<f64>,
    average: f64,
    reviews: u64,
}

impl Default for AnswerTimeHistory {
    fn default() -> Self {
        Self {
            per_grade: Default::default(),
            every: DurationMedian::default(),
            grade_counts: [0; 4],
            cards: HashMap::new(),
            previous: None,
            average: LN8,
            reviews: 0,
        }
    }
}

impl AnswerTimeHistory {
    pub fn new() -> Self {
        Self::default()
    }

    /// Number of reviews pushed so far.
    pub fn reviews(&self) -> u64 {
        self.reviews
    }

    /// Adds one answered review (grade 1..=4, duration in milliseconds; 0
    /// means no recorded time).
    pub fn push(&mut self, card_id: i64, grade: i64, duration_millis: i64) {
        let card = self.cards.entry(card_id).or_default();
        card.reviews += 1;
        card.last_grade = grade;
        self.reviews += 1;
        if duration_millis <= 0 {
            return;
        }
        let x = ln_seconds(duration_millis);
        // The reference pushes a time with an unknown grade into the
        // all-grade median twice; real histories only have grades 1..=4.
        if (1..=4).contains(&grade) {
            self.per_grade[grade as usize - 1].push(duration_millis);
            self.grade_counts[grade as usize - 1] += 1;
        } else {
            self.every.push(duration_millis);
        }
        self.every.push(duration_millis);
        card.last = [x, card.last[0], card.last[1]];
        card.len = (card.len + 1).min(3);
        self.previous = Some(x);
        self.average = 0.9 * self.average + 0.1 * x;
    }

    /// The 26 features of a query on `card_id` after every pushed review.
    /// `elapsed_days` is the query's (negative for a card's first review).
    pub fn features(&self, card_id: i64, elapsed_days: i64) -> [f32; ANSWER_TIME_FEATURES] {
        let mut row = [0.0_f64; ANSWER_TIME_FEATURES];
        let card = self.cards.get(&card_id).cloned().unwrap_or_default();
        for k in 0..3 {
            if k < card.len {
                row[k] = card.last[k] - LN8;
            } else {
                row[3 + k] = 1.0;
            }
        }
        row[6] = (card.reviews as f64).ln_1p() / 5.0;
        if (1..=4).contains(&card.last_grade) {
            row[6 + card.last_grade as usize] = 1.0;
        }
        let fallback = self.every.median().unwrap_or(LN8);
        for grade in 0..4 {
            row[11 + grade] = self.per_grade[grade].median().unwrap_or(fallback) - LN8;
            row[15 + grade] = (self.grade_counts[grade] as f64).ln_1p() / 10.0;
        }
        row[19] = fallback - LN8;
        match self.previous {
            Some(previous) => row[20] = previous - LN8,
            None => row[21] = 1.0,
        }
        row[22] = self.average - LN8;
        row[ANSWER_TIME_ELAPSED_FEATURE] = (elapsed_days.max(0) as f64).ln_1p() / 3.0;
        row[ANSWER_TIME_ELAPSED_FEATURE + 1] = if elapsed_days < 0 { 1.0 } else { 0.0 };
        row[25] = (self.reviews as f64).ln_1p() / 10.0;
        row.map(|value| value as f32)
    }
}

/// Sets the query's own elapsed-days features (23 and 24) of a history row.
pub(crate) fn set_answer_time_elapsed(features: &mut [f32], elapsed_days: i64) {
    features[ANSWER_TIME_ELAPSED_FEATURE] = ((elapsed_days.max(0) as f64).ln_1p() / 3.0) as f32;
    features[ANSWER_TIME_ELAPSED_FEATURE + 1] = if elapsed_days < 0 { 1.0 } else { 0.0 };
}

/// Upper edges of bins 0..=14 in milliseconds; bin 15 is 60 s or more.
pub fn answer_time_bin_edges_millis() -> [f64; ANSWER_TIME_BINS - 1] {
    std::array::from_fn(|k| 500.0 * 120.0_f64.powf(k as f64 / 14.0))
}

/// The answer-time head's prediction for one query.
#[derive(Clone, Debug, PartialEq)]
pub struct AnswerTimePrediction {
    /// Per grade (Again, Hard, Good, Easy): the probability of each bin.
    pub probabilities: [[f32; ANSWER_TIME_BINS]; 4],
    /// The instant head's grade probabilities at the same query.
    pub button_probabilities: [f32; 4],
}

impl AnswerTimePrediction {
    pub(crate) fn from_logits(logits: &[f32], button_probabilities: [f32; 4]) -> Self {
        let probabilities = std::array::from_fn(|grade| {
            let logits = &logits[grade * ANSWER_TIME_BINS..(grade + 1) * ANSWER_TIME_BINS];
            let max = logits.iter().copied().fold(f32::NEG_INFINITY, f32::max);
            let exp: Vec<f64> = logits
                .iter()
                .map(|&value| ((value - max) as f64).exp())
                .collect();
            let sum: f64 = exp.iter().sum();
            std::array::from_fn(|bin| (exp[bin] / sum) as f32)
        });
        Self {
            probabilities,
            button_probabilities,
        }
    }

    /// Median answer time in seconds if the answer is `grade` (0 = Again).
    pub fn median_seconds(&self, grade: usize) -> f32 {
        median_seconds(&self.probabilities[grade].map(f64::from))
    }

    /// Median answer time in seconds over the grades, weighted by the
    /// instant head's grade probabilities.
    pub fn mixed_median_seconds(&self) -> f32 {
        let mixed: [f64; ANSWER_TIME_BINS] = std::array::from_fn(|bin| {
            (0..4)
                .map(|grade| {
                    f64::from(self.button_probabilities[grade])
                        * f64::from(self.probabilities[grade][bin])
                })
                .sum()
        });
        median_seconds(&mixed)
    }

    /// Probability of an answer under 3.891 s if the answer is `grade`.
    pub fn fast_probability(&self, grade: usize) -> f32 {
        self.probabilities[grade][..ANSWER_TIME_FAST_BINS]
            .iter()
            .sum()
    }
}

/// The reference's `median_log_s`: linear in ln time inside the bin where
/// the cumulative probability crosses 0.5; bin 0 starts at 0.25 s and the
/// last bin is its lower edge (60 s).
fn median_seconds(probabilities: &[f64; ANSWER_TIME_BINS]) -> f32 {
    let edges = answer_time_bin_edges_millis().map(|edge| (edge / 1000.0).ln());
    let total: f64 = probabilities.iter().sum();
    let mut before = 0.0;
    let mut bin = ANSWER_TIME_BINS - 1;
    for (index, &probability) in probabilities.iter().enumerate() {
        if (before + probability) / total >= 0.5 {
            bin = index;
            break;
        }
        before += probability;
    }
    let low = if bin == 0 {
        0.25_f64.ln()
    } else {
        edges[bin - 1]
    };
    let high = if bin == ANSWER_TIME_BINS - 1 {
        edges[ANSWER_TIME_BINS - 2]
    } else {
        edges[bin]
    };
    let probability = probabilities[bin] / total;
    let fraction = ((0.5 - before / total) / probability.max(1e-12)).clamp(0.0, 1.0);
    (low + fraction * (high - low)).exp() as f32
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn answer_time_history_matches_the_reference_rule() {
        let mut history = AnswerTimeHistory::new();
        let empty = history.features(1, -1);
        assert_eq!(&empty[3..6], &[1.0, 1.0, 1.0]);
        assert_eq!(empty[21], 1.0);
        assert_eq!(empty[24], 1.0);
        assert_eq!(empty[11], 0.0);
        history.push(1, 3, 4000);
        history.push(2, 1, 0);
        history.push(1, 3, 10000);
        history.push(2, 3, 2000);
        let row = history.features(1, 2);
        let ln = |ms: f64| (ms / 1000.0).ln() - LN8;
        assert!((row[0] as f64 - ln(10000.0)).abs() < 1e-6);
        assert!((row[1] as f64 - ln(4000.0)).abs() < 1e-6);
        assert_eq!(row[5], 1.0);
        assert_eq!(row[9], 1.0);
        // Good median of 4 s, 10 s and 2 s is 4 s; Again has no recorded time
        // and falls back to the all-grade median (also 4 s).
        assert!((row[13] as f64 - ln(4000.0)).abs() < 1e-6);
        assert!((row[11] as f64 - ln(4000.0)).abs() < 1e-6);
        assert!((row[20] as f64 - ln(2000.0)).abs() < 1e-6);
        assert!((row[25] as f64 - (4.0_f64).ln_1p() / 10.0).abs() < 1e-7);
        history.push(3, 3, 6000);
        // Even count: mean of the two middle ln values (4 s and 6 s).
        let even = history.features(1, 2)[13] as f64;
        assert!((even - ((ln(4000.0) + ln(6000.0)) / 2.0)).abs() < 1e-6);
    }

    #[test]
    fn answer_time_median_and_fast_probability() {
        let mut logits = vec![0.0; 64];
        // Good: all mass in bin 6 (3,034..3,891 ms): median at its centre.
        logits[2 * ANSWER_TIME_BINS + 6] = 50.0;
        let prediction = AnswerTimePrediction::from_logits(&logits, [0.0, 0.0, 1.0, 0.0]);
        let edges = answer_time_bin_edges_millis();
        let centre = ((edges[5] / 1000.0).ln() + (edges[6] / 1000.0).ln()) / 2.0;
        assert!((prediction.median_seconds(2) as f64 - centre.exp()).abs() < 1e-4);
        assert!((prediction.fast_probability(2) - 1.0).abs() < 1e-6);
        assert!((prediction.mixed_median_seconds() - prediction.median_seconds(2)).abs() < 1e-6);
        // Uniform Again: 7 of 16 bins are fast.
        assert!((prediction.fast_probability(0) - 7.0 / 16.0).abs() < 1e-6);
    }
}
