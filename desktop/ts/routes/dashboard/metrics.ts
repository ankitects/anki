// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// CFA Speedrun: the dashboard's gauge math. Pure functions over the per-reading
// numbers returned by the backend; keeps all CFA-specific policy (topic weights,
// transfer factors, MPS band, give-up thresholds) in one place. See DASHBOARD.md.

import { topicOf } from "../concept-graph/topics";
import { TOPIC_WEIGHTS } from "./cfa_weights_2026";

/** The 10 CFA Level I topic areas, in exam order. */
export const TOPICS = [
    "Ethical & Professional Standards",
    "Quantitative Methods",
    "Economics",
    "Financial Statement Analysis",
    "Corporate Issuers",
    "Equity Investments",
    "Fixed Income",
    "Derivatives",
    "Alternative Investments",
    "Portfolio Management",
];

/**
 * Exam weight for a topic = midpoint of the official (min,max) range (R26/C12).
 * The (max-min) spread lives in `cfa_weights_2026` for callers that carry it as
 * uncertainty. Versioned by exam year; unknown topics weigh 0.
 */
function weightOf(topic: string): number {
    return TOPIC_WEIGHTS[topic]?.mid ?? 0;
}

// How much recall is expected to transfer to exam questions (documented guesses;
// replaced by a measured model in Phase 2).
const TRANSFER: Record<string, number> = {
    "Ethical & Professional Standards": 0.9,
    "Quantitative Methods": 0.65,
    Economics: 0.75,
    "Financial Statement Analysis": 0.75,
    "Corporate Issuers": 0.75,
    "Equity Investments": 0.75,
    "Fixed Income": 0.65,
    Derivatives: 0.6,
    "Alternative Investments": 0.85,
    "Portfolio Management": 0.75,
};

/**
 * Minimum-passing-score band on the readiness (P(pass)) scale. CFA never
 * publishes the MPS, so this is a wide, configurable band (defaults here;
 * Phase 3 calibrates it against held-out mocks).
 */
export interface MpsBand {
    low: number;
    center: number;
    high: number;
}
export const DEFAULT_MPS: MpsBand = { low: 0.6, center: 0.65, high: 0.7 };

const LOGISTIC_K = 14;
const TARGET = 0.8;
const DEFAULT_TRANSFER = 0.7;
const PERF_UNCERTAINTY = 0.15; // extra band because the transfer factor is a guess
const Z = 1.64; // ~90% interval

// Answer-accuracy signal. FSRS retrievability alone is a "freshness" number: it
// resets to ~100% the moment a card is reviewed and only dips below the desired
// retention once a card is overdue — so a topic you keep failing still reads as
// full memory right after studying. We blend in how you actually *graded* the
// cards (from the review log) so repeated misses/hards visibly lower Memory and
// pull concept-graph nodes toward "weak".
//
// A graded review scores: Again = 0 (miss), Hard = HARD_CREDIT (weak recall),
// Good/Easy = 1 (full). Accuracy is the mean score over a tag's graded reviews.
export const HARD_CREDIT = 0.5;
// How strongly poor accuracy discounts retrievability. 0 = ignore accuracy (the
// old freshness-only behaviour); 1 = a topic answered entirely wrong reads as 0
// memory regardless of how recently it was reviewed. At the default 0.5, a
// just-reviewed topic drops below the 90% "high recall" line once accuracy falls
// under ~80%, and into "weak" (<70%) under ~40%.
export const ACCURACY_WEIGHT = 0.5;

/**
 * Readiness give-up rule (R1). The gauge ABSTAINS unless every threshold is met:
 * a pass-probability shown on thin data is "a guess in a nice font" and an
 * automatic fail (R10). These are the honest, shipped/graded defaults.
 */
export interface ReadinessThresholds {
    minGradedReviews: number;
    minCoverage: number;
    minHeldOutProbes: number;
}
export const HONEST_THRESHOLDS: ReadinessThresholds = {
    minGradedReviews: 300,
    minCoverage: 0.7,
    minHeldOutProbes: 50,
};
/**
 * Relaxed thresholds for DEVELOPMENT/TESTING only — to exercise the readiness
 * pipeline end-to-end (the reason the original 15/1% values existed). Anything
 * shown under these is flagged `testMode` and labelled "test data", never a real
 * prediction. The default build uses HONEST_THRESHOLDS and abstains.
 */
export const TEST_THRESHOLDS: ReadinessThresholds = {
    minGradedReviews: 1,
    minCoverage: 0,
    minHeldOutProbes: 0,
};

// Back-compat exports (the honest defaults).
export const MIN_GRADED_REVIEWS = HONEST_THRESHOLDS.minGradedReviews;
export const MIN_COVERAGE = HONEST_THRESHOLDS.minCoverage;

export interface DashboardOptions {
    /** Count of graded held-out application-MCQ probe items (the 30×2 set). */
    heldOutProbeItems?: number;
    /**
     * Force the gauge on with relaxed thresholds for dev/testing; the output is
     * flagged `testMode` so the UI can label it "test data — not a prediction".
     */
    testMode?: boolean;
    /** Override the give-up thresholds (else honest, or test when testMode). */
    thresholds?: ReadinessThresholds;
    /** Override the MPS band. */
    mps?: MpsBand;
}

/** Graded-review counts (from the review log) used for the accuracy signal. */
export interface GradeCounts {
    gradedReviews: number;
    againReviews: number;
    hardReviews: number;
}

export interface TagStat {
    tag: string;
    total: number;
    studied: number;
    meanRetrievability: number;
    reviewed: number;
    seen: number;
    gradedReviews: number;
    againReviews: number;
    hardReviews: number;
}

export interface Estimate {
    point: number;
    low: number;
    high: number;
}

export interface SubjectRow {
    topic: string;
    weight: number;
    total: number;
    studied: number;
    seen: number;
    reviewed: number;
    inDeck: boolean;
    covered: boolean;
    /** FSRS retrievability discounted by answer accuracy (the shown Memory). */
    memory: number | null;
    /** Raw FSRS retrievability before the accuracy discount (null if unstudied). */
    rawMemory: number | null;
    /** Answer-accuracy score in [0,1], or null with no graded reviews. */
    accuracy: number | null;
    performance: number | null;
}

export type Confidence = "low" | "medium" | "high";

export interface Readiness {
    abstained: boolean;
    /** Human-readable reason the gauge abstains, or the test-data warning; names
     *  the missing input per the honesty rule. Null when a real number is shown. */
    reason: string | null;
    /** True when relaxed dev thresholds produced this number (label it!). */
    testMode: boolean;
    pPass: Estimate | null;
    confidence: Confidence;
}

export interface Dashboard {
    subjects: SubjectRow[];
    coverage: number;
    deckCoverage: number;
    gradedReviews: number;
    hasFsrsData: boolean;
    memory: Estimate | null;
    performance: Estimate | null;
    readiness: Readiness;
    bestNext: string | null;
}

interface TopicAcc {
    total: number;
    studied: number;
    reviewed: number;
    seen: number;
    retrSum: number;
    gradedReviews: number;
    againReviews: number;
    hardReviews: number;
}

function clamp01(x: number): number {
    return Math.max(0, Math.min(1, x));
}

function logistic(x: number): number {
    return 1 / (1 + Math.exp(-x));
}

/**
 * Recall-accuracy score in [0,1] from graded reviews (Again = 0,
 * Hard = `HARD_CREDIT`, Good/Easy = 1). Returns null when there are no graded
 * reviews, so callers keep the pure-retrievability value rather than penalising
 * a topic we have no answer history for.
 */
export function accuracyScore(g: GradeCounts): number | null {
    if (g.gradedReviews <= 0) {
        return null;
    }
    const good = g.gradedReviews - g.againReviews - g.hardReviews; // Good + Easy
    const score = (good + HARD_CREDIT * g.hardReviews) / g.gradedReviews;
    return clamp01(score);
}

/**
 * Blend FSRS retrievability `r` with answer accuracy so repeated misses/hards
 * lower Memory (and redden concept-graph nodes). Monotonic and never *raises*
 * `r`; with no graded reviews it returns `r` unchanged. Shared by the dashboard
 * gauges and the concept graph so both colour consistently.
 */
export function accuracyAdjustedMemory(r: number, g: GradeCounts): number {
    const accuracy = accuracyScore(g);
    if (accuracy == null) {
        return r;
    }
    return clamp01(r * (1 - ACCURACY_WEIGHT * (1 - accuracy)));
}

/** Binomial-style standard-error band for a proportion-like mean. */
function band(mean: number, n: number): Estimate {
    if (n <= 0) {
        return { point: mean, low: 0, high: 1 };
    }
    const se = Math.sqrt(Math.max(mean * (1 - mean), 0) / n);
    return { point: mean, low: clamp01(mean - Z * se), high: clamp01(mean + Z * se) };
}

function blank(): TopicAcc {
    return {
        total: 0,
        studied: 0,
        reviewed: 0,
        seen: 0,
        retrSum: 0,
        gradedReviews: 0,
        againReviews: 0,
        hardReviews: 0,
    };
}

function rowFor(topic: string, a: TopicAcc): SubjectRow {
    let rawMemory: number | null = null;
    let memory: number | null = null;
    const accuracy = accuracyScore(a);
    if (a.studied > 0) {
        // FSRS available: mean retrievability over studied cards, then discounted
        // by how accurately the student has actually answered this topic so that
        // failed/hard cards pull the number down instead of reading as fresh.
        rawMemory = a.retrSum / a.studied;
        memory = accuracyAdjustedMemory(rawMemory, a);
    }
    // C11: with no FSRS memory state we ABSTAIN (memory stays null) rather than
    // compute a reviewed/seen graduation proxy — that proxy is not a recall
    // probability and would make the gauges misleading. "covered" below still
    // tracks study progress from reviews, so coverage is unaffected.
    const transfer = TRANSFER[topic] ?? DEFAULT_TRANSFER;
    return {
        topic,
        weight: weightOf(topic),
        total: a.total,
        studied: a.studied,
        seen: a.seen,
        reviewed: a.reviewed,
        inDeck: a.total > 0,
        // "covered" means the student has actually studied cards here — based on
        // reviews, not FSRS, so it reflects progress even without FSRS.
        covered: a.seen > 0,
        memory,
        rawMemory,
        accuracy,
        performance: memory != null ? clamp01(memory * transfer) : null,
    };
}

export function computeDashboard(
    tags: TagStat[],
    gradedReviews: number,
    options: DashboardOptions = {},
): Dashboard {
    const thresholds =
        options.thresholds ?? (options.testMode ? TEST_THRESHOLDS : HONEST_THRESHOLDS);
    const heldOutProbeItems = options.heldOutProbeItems ?? 0;
    const mps = options.mps ?? DEFAULT_MPS;

    const acc = new Map<string, TopicAcc>();
    for (const t of TOPICS) {
        acc.set(t, blank());
    }
    for (const tag of tags) {
        const topic = topicOf(tag.tag);
        if (!acc.has(topic)) {
            acc.set(topic, blank());
        }
        const a = acc.get(topic)!;
        a.total += tag.total;
        a.studied += tag.studied;
        a.reviewed += tag.reviewed;
        a.seen += tag.seen;
        a.retrSum += tag.meanRetrievability * tag.studied;
        a.gradedReviews += tag.gradedReviews;
        a.againReviews += tag.againReviews;
        a.hardReviews += tag.hardReviews;
    }

    const subjects: SubjectRow[] = TOPICS.map((t) => rowFor(t, acc.get(t)!));
    // fold any unknown tags into a weight-0 "Other" row when present
    for (const [topic, a] of acc) {
        if (!TOPICS.includes(topic) && a.total > 0) {
            subjects.push(rowFor(topic, a));
        }
    }

    const totalWeight = TOPICS.reduce((s, t) => s + weightOf(t), 0);
    const coveredWeight = subjects
        .filter((r) => r.covered)
        .reduce((s, r) => s + r.weight, 0);
    const inDeckWeight = subjects
        .filter((r) => r.inDeck)
        .reduce((s, r) => s + r.weight, 0);
    const coverage = totalWeight > 0 ? coveredWeight / totalWeight : 0;
    const deckCoverage = totalWeight > 0 ? inDeckWeight / totalWeight : 0;

    // exam-weighted aggregates over subjects that have data
    let wSum = 0;
    let memWSum = 0;
    let perfWSum = 0;
    let totalSample = 0;
    for (const r of subjects) {
        if (r.memory != null && r.weight > 0) {
            wSum += r.weight;
            memWSum += r.weight * r.memory;
            perfWSum += r.weight * (r.performance ?? 0);
            totalSample += r.studied > 0 ? r.studied : r.seen;
        }
    }

    const memory = wSum > 0 ? band(memWSum / wSum, totalSample) : null;
    let performance: Estimate | null = null;
    if (wSum > 0) {
        const base = band(perfWSum / wSum, totalSample);
        performance = {
            point: base.point,
            low: clamp01(base.low - PERF_UNCERTAINTY),
            high: clamp01(base.high + PERF_UNCERTAINTY),
        };
    }

    // Readiness give-up rule (R1): abstain unless every gate passes, and NAME the
    // missing input (honesty rule). The band/point math (Beta-Binomial, Platt,
    // calibration) is Phase 3; until then the gauge abstains by default, which is
    // the honest, rubric-safe behaviour.
    let abstained = false;
    let reason: string | null = null;
    if (performance == null) {
        abstained = true;
        reason = "No FSRS memory signal yet — enable and optimize FSRS.";
    } else if (gradedReviews < thresholds.minGradedReviews) {
        abstained = true;
        reason = `Not enough data: need ≥${thresholds.minGradedReviews} graded reviews (have ${gradedReviews}).`;
    } else if (coverage < thresholds.minCoverage) {
        abstained = true;
        reason =
            `Not enough data: need ≥${Math.round(thresholds.minCoverage * 100)}% topic coverage ` +
            `(have ${Math.round(coverage * 100)}%).`;
    } else if (heldOutProbeItems < thresholds.minHeldOutProbes) {
        abstained = true;
        reason =
            `Not enough data: need ≥${thresholds.minHeldOutProbes} held-out probe items ` +
            `(have ${heldOutProbeItems}).`;
    }

    let pPass: Estimate | null = null;
    if (!abstained && performance != null) {
        pPass = {
            point: logistic(LOGISTIC_K * (performance.point - mps.center)),
            // coverage penalty pulls the low bound down for unstudied material
            low: logistic(LOGISTIC_K * (performance.low * coverage - mps.high)),
            high: logistic(LOGISTIC_K * (performance.high - mps.low)),
        };
        if (options.testMode) {
            reason = "TEST DATA — pipeline check only, not a real prediction.";
        }
    }

    let confidence: Confidence = "low";
    if (coverage >= 0.8) {
        confidence = "high";
    } else if (coverage >= 0.5) {
        confidence = "medium";
    }

    // biggest weighted gap to the target (unstudied high-weight topics rank high)
    let bestNext: string | null = null;
    let bestGap = -1;
    for (const r of subjects) {
        if (r.weight <= 0) {
            continue;
        }
        const gap = r.weight * (TARGET - (r.performance ?? 0));
        if (gap > bestGap) {
            bestGap = gap;
            bestNext = r.topic;
        }
    }

    return {
        subjects,
        coverage,
        deckCoverage,
        gradedReviews,
        hasFsrsData: subjects.some((r) => r.studied > 0),
        memory,
        performance,
        readiness: {
            abstained,
            reason,
            testMode: options.testMode ?? false,
            pPass,
            confidence,
        },
        bestNext,
    };
}
