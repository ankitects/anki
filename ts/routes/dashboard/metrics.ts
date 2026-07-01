// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// CFA Speedrun: the dashboard's gauge math. Pure functions over the per-reading
// numbers returned by the backend; keeps all CFA-specific policy (topic weights,
// transfer factors, MPS band, give-up thresholds) in one place. See DASHBOARD.md.

import { topicOf } from "../concept-graph/topics";

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

// Official L1 exam weights (%), documented defaults (published as ranges).
const WEIGHT: Record<string, number> = {
    "Ethical & Professional Standards": 15,
    "Quantitative Methods": 10,
    Economics: 10,
    "Financial Statement Analysis": 15,
    "Corporate Issuers": 10,
    "Equity Investments": 11,
    "Fixed Income": 11,
    Derivatives: 6,
    "Alternative Investments": 6,
    "Portfolio Management": 6,
};

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

const MPS_LOW = 0.6;
const MPS_HIGH = 0.7;
const MPS_CENTER = 0.65;
const LOGISTIC_K = 14;
const TARGET = 0.8;
const DEFAULT_TRANSFER = 0.7;
const PERF_UNCERTAINTY = 0.15; // extra band because the transfer factor is a guess
const Z = 1.64; // ~90% interval

export const MIN_GRADED_REVIEWS = 15;
export const MIN_COVERAGE = 0.01;

export interface TagStat {
    tag: string;
    total: number;
    studied: number;
    meanRetrievability: number;
    reviewed: number;
    seen: number;
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
    memory: number | null;
    performance: number | null;
}

export type Confidence = "low" | "medium" | "high";

export interface Dashboard {
    subjects: SubjectRow[];
    coverage: number;
    deckCoverage: number;
    gradedReviews: number;
    hasFsrsData: boolean;
    memory: Estimate | null;
    performance: Estimate | null;
    readiness: {
        abstained: boolean;
        pPass: Estimate | null;
        confidence: Confidence;
    };
    bestNext: string | null;
}

interface TopicAcc {
    total: number;
    studied: number;
    reviewed: number;
    seen: number;
    retrSum: number;
}

function clamp01(x: number): number {
    return Math.max(0, Math.min(1, x));
}

function logistic(x: number): number {
    return 1 / (1 + Math.exp(-x));
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
    return { total: 0, studied: 0, reviewed: 0, seen: 0, retrSum: 0 };
}

function rowFor(topic: string, a: TopicAcc): SubjectRow {
    let memory: number | null = null;
    if (a.studied > 0) {
        // FSRS available: mean retrievability over studied cards.
        memory = a.retrSum / a.studied;
    } else if (a.seen > 0) {
        // No FSRS memory state: rough estimate from study progress (fraction of
        // studied cards that have graduated to review).
        memory = a.reviewed / a.seen;
    }
    const transfer = TRANSFER[topic] ?? DEFAULT_TRANSFER;
    return {
        topic,
        weight: WEIGHT[topic] ?? 0,
        total: a.total,
        studied: a.studied,
        seen: a.seen,
        reviewed: a.reviewed,
        inDeck: a.total > 0,
        // "covered" means the student has actually studied cards here — based on
        // reviews, not FSRS, so it reflects progress even without FSRS.
        covered: a.seen > 0,
        memory,
        performance: memory != null ? clamp01(memory * transfer) : null,
    };
}

export function computeDashboard(tags: TagStat[], gradedReviews: number): Dashboard {
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
    }

    const subjects: SubjectRow[] = TOPICS.map((t) => rowFor(t, acc.get(t)!));
    // fold any unknown tags into a weight-0 "Other" row when present
    for (const [topic, a] of acc) {
        if (!TOPICS.includes(topic) && a.total > 0) {
            subjects.push(rowFor(topic, a));
        }
    }

    const totalWeight = TOPICS.reduce((s, t) => s + (WEIGHT[t] ?? 0), 0);
    const coveredWeight = subjects
        .filter((r) => r.covered)
        .reduce((s, r) => s + r.weight, 0);
    const inDeckWeight = subjects.filter((r) => r.inDeck).reduce((s, r) => s + r.weight, 0);
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

    const abstained = performance == null
        || gradedReviews < MIN_GRADED_REVIEWS
        || coverage < MIN_COVERAGE;

    let pPass: Estimate | null = null;
    if (!abstained && performance != null) {
        pPass = {
            point: logistic(LOGISTIC_K * (performance.point - MPS_CENTER)),
            // coverage penalty pulls the low bound down for unstudied material
            low: logistic(LOGISTIC_K * (performance.low * coverage - MPS_HIGH)),
            high: logistic(LOGISTIC_K * (performance.high - MPS_LOW)),
        };
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
        readiness: { abstained, pPass, confidence },
        bestNext,
    };
}
