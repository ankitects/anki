// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! The three scores, as estimators. Nothing here touches the collection, so
//! every gate and every interval is testable on its own.
//!
//! This is a direct implementation of `scoring_reference.py` (report.md §3
//! and §4). Where a number appears below it comes from that specification;
//! none of it is re-derived here and none of it is hand-tuned to taste.

use super::beta::beta_interval;

/// All intervals are 80% central intervals — interpretable as "8 times in
/// 10", and labelled as such wherever they surface.
pub(crate) const LEVEL: f64 = 0.80;
/// z for a two-sided 80% normal interval.
const Z_80: f64 = 1.2816;

// --- give-up thresholds (report.md §4.1) ---------------------------------

/// Memory is gated on **coverage, not width**: past n≈50 the width is pinned
/// by the calibration term and stops responding to sample size, so a width
/// gate cannot tell 58% coverage from 13% (report.md §4.3).
pub(crate) const MEMORY_MIN_COVERAGE: f64 = 0.40;
pub(crate) const MEMORY_MIN_CARDS: usize = 20;
pub(crate) const PERFORMANCE_MAX_WIDTH: f64 = 0.30;
/// A width gate ALONE is defeated by a strong prior — see [PRIOR_KAPPA_CAP]
/// and report.md §4.2. Both this floor and the cap are required.
pub(crate) const PERFORMANCE_MIN_PROBES: usize = 10;
/// κ ≥ 14.8 produces an interval narrower than [PERFORMANCE_MAX_WIDTH] with
/// zero observations, i.e. manufactures confidence out of nothing.
pub(crate) const PRIOR_KAPPA_CAP: f64 = 8.0;
/// Probe evidence half-life. A topic drilled hard in March grows *less*
/// certain by July with no new bad news, which is the honest behaviour.
pub(crate) const PROBE_HALF_LIFE_DAYS: f64 = 60.0;
/// Readiness stays withheld until the outcome set is this large (§5.2).
pub(crate) const READINESS_MIN_CALIBRATION_OUTCOMES: usize = 200;

/// A gated estimate on the 0–1 probability scale. `point` is `None` only when
/// there is no evidence at all to form one.
#[derive(Debug, Clone)]
pub struct Estimate {
    pub point: Option<f64>,
    pub lower: f64,
    pub upper: f64,
    pub reportable: bool,
    /// Why the number is withheld. Empty when it is reportable.
    pub reason: String,
    /// The cheapest specific thing that would unlock it. Computed, never
    /// hand-written (report.md §4.1).
    pub unlock: String,
}

impl Estimate {
    pub fn width(&self) -> f64 {
        self.upper - self.lower
    }
}

// -------------------------------------------------------------------------
// Score 1 — Memory
// -------------------------------------------------------------------------

/// Aggregate FSRS per-card retrievability into a topic-level recall estimate.
///
/// `retrievabilities` holds only cards with a memory state; FSRS returns NULL
/// for never-studied cards, and those are counted in `cards_in_topic` but
/// never imputed to zero (a guess) or dropped (a flattering lie).
///
/// Uncertainty has two terms: sampling — this deck is one sample of the
/// topic's testable facts — and calibration, `calibration_sd`, which is FSRS's
/// own measured prediction error over the user's review log. Coverage is
/// deliberately kept **out** of the interval and used as a gate instead: a
/// narrow interval over 6% of a topic is precise and meaningless.
pub(crate) fn memory_score(
    retrievabilities: &[f64],
    cards_in_topic: usize,
    calibration_sd: f64,
) -> Estimate {
    let n = retrievabilities.len();
    let coverage = if cards_in_topic > 0 {
        n as f64 / cards_in_topic as f64
    } else {
        0.0
    };
    if n == 0 {
        return Estimate {
            point: None,
            lower: 0.0,
            upper: 1.0,
            reportable: false,
            reason: "no studied cards in this topic".into(),
            unlock: memory_unlock(0, cards_in_topic),
        };
    }

    let mean = retrievabilities.iter().sum::<f64>() / n as f64;
    let var_within = if n > 1 {
        retrievabilities
            .iter()
            .map(|r| (r - mean).powi(2))
            .sum::<f64>()
            / (n - 1) as f64
    } else {
        // a single card tells us nothing about spread; assume maximal ignorance
        0.25
    };
    let se = (var_within / n as f64 + calibration_sd * calibration_sd).sqrt();
    let lower = (mean - Z_80 * se).max(0.0);
    let upper = (mean + Z_80 * se).min(1.0);

    let (reportable, reason) = if coverage < MEMORY_MIN_COVERAGE {
        (
            false,
            format!(
                "{:.0}% of this topic's cards have ever been studied (need {:.0}%)",
                coverage * 100.0,
                MEMORY_MIN_COVERAGE * 100.0
            ),
        )
    } else if n < MEMORY_MIN_CARDS {
        (
            false,
            format!("only {n} studied cards (need {MEMORY_MIN_CARDS})"),
        )
    } else {
        (true, String::new())
    };

    Estimate {
        point: Some(mean),
        lower,
        upper,
        reportable,
        reason,
        unlock: if reportable {
            String::new()
        } else {
            memory_unlock(n, cards_in_topic)
        },
    }
}

/// How many more of this topic's existing cards must be studied before the
/// number stops being a claim about a fraction of the topic.
fn memory_unlock(studied: usize, cards_in_topic: usize) -> String {
    let for_coverage = (MEMORY_MIN_COVERAGE * cards_in_topic as f64).ceil() as usize;
    let target = for_coverage.max(MEMORY_MIN_CARDS).min(cards_in_topic);
    if studied >= target {
        // only reachable when the topic has fewer cards than the floor
        return format!(
            "this topic only has {cards_in_topic} cards; it needs at least \
             {MEMORY_MIN_CARDS} before a topic-level number means anything"
        );
    }
    format!(
        "study {} more of this topic's {cards_in_topic} cards",
        target - studied
    )
}

// -------------------------------------------------------------------------
// Score 2 — Performance
// -------------------------------------------------------------------------

/// A topic's probe evidence: `(correct, age_in_days)` per probe answered.
pub(crate) type ProbeOutcomes = Vec<(bool, f64)>;

/// Method-of-moments hierarchical prior over the user's topics, falling back
/// to Beta(2,2) — mean 0.5, effective sample size 4, swamped after ~10 probes.
///
/// κ is **capped**. An uncapped κ ≥ 14.8 produces an 80% interval narrower
/// than [PERFORMANCE_MAX_WIDTH] with zero observations (report.md §4.2), so
/// the system would report a confident number for a topic never probed.
pub(crate) fn empirical_bayes_prior(per_topic: &[(f64, f64)]) -> (f64, f64, String) {
    const MIN_PROBES: f64 = 50.0;
    const MIN_TOPICS: usize = 5;
    let usable: Vec<_> = per_topic.iter().filter(|(_, n)| *n > 0.0).collect();
    let total: f64 = usable.iter().map(|(_, n)| n).sum();
    if total < MIN_PROBES || usable.len() < MIN_TOPICS {
        return (2.0, 2.0, "cold-start Beta(2,2)".into());
    }
    let mu = usable.iter().map(|(y, _)| y).sum::<f64>() / total;
    let k = usable.len() as f64;
    let var = usable
        .iter()
        .map(|(y, n)| (y / n - mu).powi(2))
        .sum::<f64>()
        / (k - 1.0);
    let within = usable.iter().map(|(_, n)| mu * (1.0 - mu) / n).sum::<f64>() / k;
    let between = (var - within).max(1e-6);
    let kappa = (mu * (1.0 - mu) / between - 1.0).clamp(1.0, PRIOR_KAPPA_CAP);
    (
        mu * kappa,
        (1.0 - mu) * kappa,
        format!("empirical-Bayes μ={mu:.2} κ={kappa:.1}"),
    )
}

/// Beta-Binomial posterior over novel-question ability from probe outcomes.
///
/// The prior mean is deliberately **not** tied to the topic's Memory score.
/// If it were, a user with strong recall and zero probes would be shown high
/// novel ability — the precise lie the three-score split exists to expose.
///
/// Older probes are down-weighted `0.5^(age/60d)` and enter as fractional
/// counts (a power posterior), so stale evidence widens the interval.
pub(crate) fn performance_score(
    outcomes: &ProbeOutcomes,
    prior: (f64, f64),
) -> (Estimate, f64, f64, f64) {
    let (mut y, mut n) = (0.0, 0.0);
    for (correct, age_days) in outcomes {
        let w = 0.5f64.powf(age_days / PROBE_HALF_LIFE_DAYS);
        n += w;
        if *correct {
            y += w;
        }
    }
    let (a, b) = (prior.0 + y, prior.1 + (n - y));
    let (lower, upper) = beta_interval(a, b, LEVEL);
    let point = a / (a + b);
    let real_probes = outcomes.len();

    let (reportable, reason) = if real_probes < PERFORMANCE_MIN_PROBES {
        (
            false,
            format!("only {real_probes} probes answered (need {PERFORMANCE_MIN_PROBES})"),
        )
    } else if upper - lower > PERFORMANCE_MAX_WIDTH {
        (
            false,
            format!(
                "the range is still {:.2} wide (need {PERFORMANCE_MAX_WIDTH:.2})",
                upper - lower
            ),
        )
    } else {
        (true, String::new())
    };

    let unlock = if reportable {
        String::new()
    } else {
        match probes_needed(a, b, point, n, real_probes) {
            Some((extra, new_width)) => format!(
                "~{extra} more probes at the current rate narrows this from ±{:.2} to ±{:.2}",
                (upper - lower) / 2.0,
                new_width / 2.0
            ),
            None => "more probes than are reasonably answerable would be needed".into(),
        }
    };

    (
        Estimate {
            point: Some(point),
            lower,
            upper,
            reportable,
            reason,
            unlock,
        },
        n,
        a,
        b,
    )
}

/// The cheapest specific unlock: the smallest number of additional probes at
/// the current posterior rate that brings the interval under threshold and
/// clears the count floor. Binary search — the width is monotone decreasing
/// in the number of observations.
fn probes_needed(
    a: f64,
    b: f64,
    rate: f64,
    n_effective: f64,
    real_probes: usize,
) -> Option<(usize, f64)> {
    let width_after = |extra: usize| {
        let (lo, hi) = beta_interval(
            a + rate * extra as f64,
            b + (1.0 - rate) * extra as f64,
            LEVEL,
        );
        hi - lo
    };
    let clears = |extra: usize| {
        width_after(extra) <= PERFORMANCE_MAX_WIDTH
            && real_probes + extra >= PERFORMANCE_MIN_PROBES
            && n_effective + extra as f64 >= PERFORMANCE_MIN_PROBES as f64
    };
    const MAX: usize = 2000;
    if !clears(MAX) {
        return None;
    }
    let (mut lo, mut hi) = (0usize, MAX);
    while lo < hi {
        let mid = (lo + hi) / 2;
        if clears(mid) {
            hi = mid;
        } else {
            lo = mid + 1;
        }
    }
    Some((lo, width_after(lo)))
}

// -------------------------------------------------------------------------
// Score 3 — Readiness
// -------------------------------------------------------------------------

/// Everything Readiness needs to decide whether it may speak at all.
pub(crate) struct ReadinessInputs {
    /// Ascent readiness forecasts that have since been graded against a real
    /// outcome. There is no such store in the fork, so this is genuinely zero
    /// — but it is counted, not assumed, so the gate stays live if one lands.
    pub calibration_outcomes: usize,
    /// Probe evidence for CARS. Structurally zero: CARS has no foundational
    /// concepts and no content categories, and AAMC states it requires no
    /// specific content knowledge, so no flashcard can supply it.
    pub cars_probes: usize,
}

/// Readiness — the projected 472–528 scaled score.
///
/// ponytail: the Monte Carlo of report.md §3.3 is deliberately **not**
/// implemented. It cannot run without an outcome-fitted ability→scale map,
/// and AAMC does not publish a raw-to-scaled conversion at all. Shipping code
/// that can emit a scaled score before that fit exists is precisely the risk
/// this design guards against. Build it when `calibration_outcomes` can
/// actually reach 200 — the gate below is already waiting for it.
#[derive(Debug, Clone)]
pub struct Readiness {
    pub reportable: bool,
    /// Every reason it is withheld, most fundamental first.
    pub reasons: Vec<String>,
    pub unlock: String,
}

pub(crate) fn readiness(inputs: &ReadinessInputs) -> Readiness {
    let mut reasons = Vec::new();
    if inputs.calibration_outcomes < READINESS_MIN_CALIBRATION_OUTCOMES {
        reasons.push(format!(
            "No exam outcome has ever been checked against an Ascent projection \
             ({} of {READINESS_MIN_CALIBRATION_OUTCOMES} needed). AAMC does not publish a \
             raw-to-scaled conversion — it is re-derived for every test form — so the only \
             honest bridge from an ability estimate to 472–528 is a fit against real results.",
            inputs.calibration_outcomes
        ));
    }
    if inputs.cars_probes == 0 {
        reasons.push(
            "Critical Analysis and Reasoning Skills is 1 of the 4 sections and 25% of the \
             exam, and it is not flashcard-learnable: AAMC states it requires no specific \
             content knowledge, and it has no foundational concepts or content categories. \
             Nothing in your deck can measure it."
                .into(),
        );
    }
    // Not "answer more probes". Report.md §3.6: a 16× increase in probe volume
    // moves the readiness interval by 2 points; shrinking the model-transfer
    // error moves it by 17. Telling a user to study more would be a false
    // promise, and that table is the evidence.
    let unlock = "Answering more probes will not unlock this. Only checking Ascent's \
                  projections against real exam outcomes will."
        .to_string();
    Readiness {
        reportable: reasons.is_empty(),
        reasons,
        unlock,
    }
}

// -------------------------------------------------------------------------
// The load-bearing constraint (report.md §3.5)
// -------------------------------------------------------------------------

/// Put the scores on one yardstick: interval width as a fraction of the
/// quantity's reportable range. Memory and Performance span 0–1; Readiness
/// spans the 56 points of 472–528, and while withheld the whole range is on
/// the table, so its normalised width is 1.
pub(crate) fn readiness_normalised_width(r: &Readiness, hypothetical_points: Option<f64>) -> f64 {
    match (r.reportable, hypothetical_points) {
        (true, Some(points)) => points / (528.0 - 472.0),
        _ => 1.0,
    }
}

/// Uncertainty must compound upward: `u_readiness ≥ u_performance ≥ u_memory`.
/// A design where the topmost number is the most confident inverts the entire
/// product thesis.
///
/// Violations are **reported, never clamped** — a violation is a modelling
/// bug, and clamping would hide the defect while preserving the appearance of
/// correctness.
pub(crate) fn check_compounding(
    memory: &Estimate,
    performance: &Estimate,
    readiness_width: f64,
) -> Vec<String> {
    let (um, up) = (memory.width(), performance.width());
    let mut violations = Vec::new();
    if up < um {
        violations.push(format!(
            "performance ({up:.3}) narrower than memory ({um:.3})"
        ));
    }
    if readiness_width < up {
        violations.push(format!(
            "readiness ({readiness_width:.3}) narrower than performance ({up:.3})"
        ));
    }
    violations
}

#[cfg(test)]
mod test {
    use super::*;

    fn studied(n: usize, mean: f64) -> Vec<f64> {
        // deterministic spread around `mean`, no rng in tests
        (0..n)
            .map(|i| (mean + ((i % 7) as f64 - 3.0) * 0.03).clamp(0.01, 0.999))
            .collect()
    }

    /// Trap 2 (report.md §4.3): past n≈50 the Memory interval stops responding
    /// to sample size, so a width gate is blind to the thing that matters.
    /// Coverage differs by more than 4×; the widths are indistinguishable.
    #[test]
    fn memory_is_gated_on_coverage_not_width() {
        let well_covered = memory_score(&studied(180, 0.86), 310, 0.05);
        let barely_covered = memory_score(&studied(120, 0.86), 900, 0.05);

        assert!(well_covered.reportable);
        assert!(!barely_covered.reportable, "13% coverage must be withheld");
        assert!(
            (barely_covered.width() - well_covered.width()).abs() < 0.02,
            "the whole point: widths {} vs {} are indistinguishable while coverage \
             differs 4x, so a width gate cannot see the difference",
            barely_covered.width(),
            well_covered.width()
        );
        assert!(barely_covered.reason.contains("13%"));
        // the unlock is computed from this topic's own card count
        assert_eq!(
            barely_covered.unlock,
            "study 240 more of this topic's 900 cards"
        );
    }

    #[test]
    fn memory_withholds_when_too_few_cards_studied_even_at_full_coverage() {
        let m = memory_score(&studied(8, 0.9), 8, 0.05);
        assert!(!m.reportable);
        assert!(m.reason.contains("only 8 studied cards"), "{}", m.reason);
    }

    #[test]
    fn memory_never_imputes_unstudied_cards() {
        // 100 cards, none studied: no point estimate at all, not a zero.
        let m = memory_score(&[], 100, 0.05);
        assert!(m.point.is_none());
        assert!(!m.reportable);
    }

    /// Trap 1 (report.md §4.2): a width-only gate is defeated by a strong
    /// prior, which passes it with zero observations. Both the κ cap and the
    /// independent count floor are required; either alone is insufficient.
    #[test]
    fn width_gate_alone_is_defeated_by_a_strong_prior() {
        for kappa in [15.0, 20.0] {
            let (lo, hi) = beta_interval(0.7 * kappa, 0.3 * kappa, LEVEL);
            assert!(
                hi - lo <= PERFORMANCE_MAX_WIDTH,
                "κ={kappa} should defeat a width-only gate; that is why it is capped"
            );
        }
        let (lo, hi) = beta_interval(0.7 * PRIOR_KAPPA_CAP, 0.3 * PRIOR_KAPPA_CAP, LEVEL);
        assert!(
            hi - lo > PERFORMANCE_MAX_WIDTH,
            "the capped prior must still fail the width gate on its own"
        );
        // and the count floor holds independently of the cap
        let (est, ..) = performance_score(&vec![], (0.7 * 20.0, 0.3 * 20.0));
        assert!(
            !est.reportable,
            "zero probes must be withheld even when a strong prior passes the width gate"
        );
    }

    #[test]
    fn empirical_bayes_kappa_is_capped() {
        // ten topics, all at exactly the same rate: between-topic variance
        // collapses and the method of moments wants an enormous κ.
        let records: Vec<(f64, f64)> = (0..10).map(|_| (14.0, 20.0)).collect();
        let (a, b, label) = empirical_bayes_prior(&records);
        assert!(a + b <= PRIOR_KAPPA_CAP + 1e-9, "κ={} uncapped", a + b);
        assert!(label.starts_with("empirical-Bayes"));
    }

    #[test]
    fn empirical_bayes_falls_back_to_cold_start_when_thin() {
        let (a, b, label) = empirical_bayes_prior(&[(3.0, 4.0), (2.0, 3.0)]);
        assert_eq!((a, b), (2.0, 2.0));
        assert_eq!(label, "cold-start Beta(2,2)");
    }

    /// The 5-of-5 trap. Wald reports 1.00 ± 0.00 here.
    #[test]
    fn five_of_five_is_withheld_with_a_computed_unlock() {
        let outcomes: ProbeOutcomes = (0..5).map(|_| (true, 0.0)).collect();
        let (est, ..) = performance_score(&outcomes, (2.0, 2.0));
        assert!(est.point.unwrap() < 0.85, "5/5 must not read as mastery");
        assert!(est.upper < 1.0, "upper bound must stay below certainty");
        assert!(!est.reportable);
        assert!(est.reason.contains("only 5 probes"), "{}", est.reason);
        assert!(
            est.unlock.starts_with("~") && est.unlock.contains("more probes"),
            "unlock must be a computed count, got {:?}",
            est.unlock
        );
    }

    #[test]
    fn stale_probe_evidence_widens_the_interval() {
        let fresh: ProbeOutcomes = (0..40).map(|i| (i % 10 < 7, 20.0)).collect();
        let stale: ProbeOutcomes = fresh.iter().map(|(c, t)| (*c, t + 200.0)).collect();
        let (fresh_est, fresh_n, ..) = performance_score(&fresh, (2.0, 2.0));
        let (stale_est, stale_n, ..) = performance_score(&stale, (2.0, 2.0));
        assert!(stale_n < fresh_n / 5.0, "{stale_n} vs {fresh_n}");
        assert!(stale_est.width() > fresh_est.width());
        assert!(fresh_est.reportable && !stale_est.reportable);
    }

    #[test]
    fn readiness_ships_withheld_for_both_reasons() {
        let r = readiness(&ReadinessInputs {
            calibration_outcomes: 0,
            cars_probes: 0,
        });
        assert!(!r.reportable);
        assert_eq!(r.reasons.len(), 2);
        assert!(r.reasons[0].contains("raw-to-scaled"));
        assert!(r.reasons[1].contains("Critical Analysis"));
        assert!(
            !r.unlock.to_lowercase().contains("study more"),
            "the unlock must not promise that studying unlocks readiness"
        );
    }

    /// The gate is a live check, not a constant: satisfy both preconditions
    /// and it stops withholding.
    #[test]
    fn readiness_gate_is_not_hardcoded() {
        let r = readiness(&ReadinessInputs {
            calibration_outcomes: READINESS_MIN_CALIBRATION_OUTCOMES,
            cars_probes: 40,
        });
        assert!(r.reportable && r.reasons.is_empty());

        // one precondition short is still withheld, with only that reason
        let r = readiness(&ReadinessInputs {
            calibration_outcomes: READINESS_MIN_CALIBRATION_OUTCOMES,
            cars_probes: 0,
        });
        assert_eq!(r.reasons.len(), 1);
    }

    /// Uncertainty compounds upward. Readiness is at least as uncertain as
    /// Performance, which is at least as uncertain as Memory.
    #[test]
    fn uncertainty_compounds_upward() {
        let memory = memory_score(&studied(180, 0.86), 310, 0.05);
        let probes: ProbeOutcomes = (0..40).map(|i| (i % 10 < 7, 20.0)).collect();
        let (performance, ..) = performance_score(&probes, (2.0, 2.0));
        let withheld = readiness(&ReadinessInputs {
            calibration_outcomes: 0,
            cars_probes: 0,
        });
        let ur = readiness_normalised_width(&withheld, None);

        assert!(memory.reportable && performance.reportable);
        assert!(
            performance.width() > memory.width(),
            "performance {} must not be more confident than memory {}",
            performance.width(),
            memory.width()
        );
        assert_eq!(ur, 1.0, "a withheld readiness admits the entire range");
        assert!(
            check_compounding(&memory, &performance, ur).is_empty(),
            "{:?}",
            check_compounding(&memory, &performance, ur)
        );

        // ...and the check is not vacuous. A readiness interval of 9 scaled
        // points is what report.md §3.3 measures when the common-mode transfer
        // term is dropped; it inverts the ordering and must be reported as a
        // violation rather than quietly clamped.
        let inverted = readiness_normalised_width(
            &Readiness {
                reportable: true,
                reasons: vec![],
                unlock: String::new(),
            },
            Some(9.0),
        );
        let violations = check_compounding(&memory, &performance, inverted);
        assert_eq!(violations.len(), 1, "{violations:?}");
        assert!(violations[0].starts_with("readiness"));

        // likewise in the other direction
        let violations = check_compounding(&performance, &memory, 1.0);
        assert_eq!(violations.len(), 1);
        assert!(violations[0].starts_with("performance"));
    }
}
