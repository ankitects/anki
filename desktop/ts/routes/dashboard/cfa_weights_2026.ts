// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

// CFA Speedrun (R26/C12): official CFA Level I topic-weight RANGES, versioned by
// exam year. CFA Institute publishes weights as ranges, not points, so we store
// (min, max) and a precomputed midpoint. Allocate/aggregate by `mid`; carry
// (max - min) as uncertainty. Replaces the earlier hardcoded single-point (and
// partly stale) weights. Source: CFA Institute Level I exam page (2026 outline).

export interface TopicWeight {
    /** Lower bound of the official weight range, in percent. */
    min: number;
    /** Upper bound of the official weight range, in percent. */
    max: number;
    /** Midpoint of the range — the point value used for weighting. */
    mid: number;
}

/** The exam-outline year these weights are taken from. */
export const EXAM_YEAR = 2026;

export const TOPIC_WEIGHTS: Record<string, TopicWeight> = {
    "Ethical & Professional Standards": { min: 15, max: 20, mid: 17.5 },
    "Quantitative Methods": { min: 6, max: 9, mid: 7.5 },
    Economics: { min: 6, max: 9, mid: 7.5 },
    "Financial Statement Analysis": { min: 11, max: 14, mid: 12.5 },
    "Corporate Issuers": { min: 6, max: 9, mid: 7.5 },
    "Equity Investments": { min: 11, max: 14, mid: 12.5 },
    "Fixed Income": { min: 11, max: 14, mid: 12.5 },
    Derivatives: { min: 5, max: 8, mid: 6.5 },
    "Alternative Investments": { min: 7, max: 10, mid: 8.5 },
    "Portfolio Management": { min: 8, max: 12, mid: 10 },
};
