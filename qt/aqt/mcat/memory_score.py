# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Honest, deterministic Memory score (FR-5).

This module computes the Phase-1 *Memory* score: how likely the student is to
recall what they've learned, expressed as a **point estimate + a likely
range** (never a single blended number), with a written **give-up rule** that
makes the app abstain when it lacks the data to back a number up.

Design constraints (from the spec, §4 and §9):

* **Honesty.** Memory is the *only* graded score in Phase 1. Performance and
  Readiness come later and must stay separate -- blending scores is an
  automatic fail. This module computes Memory and nothing else.
* **No AI.** Every value here is a deterministic, reproducible function of the
  inputs. There are no model calls, no randomness, no LLMs.
* **It knows when it doesn't know.** Below the give-up threshold the result
  abstains and explains exactly which condition failed.

The score is a pure function of three documented inputs:

1. **Per-concept mastery** -- from the Rust ``ConceptMastery`` RPC (FR-3),
   surfaced to Python via ``_backend.py``. Per concept:
   ``{cards_total, cards_mastered, avg_recall}`` where ``avg_recall`` is the
   mean FSRS retrievability in [0, 1] and ``mastered`` means R >= 0.9.
2. **graded_reviews** -- the total count of graded reviews available (revlog
   count from the collection).
3. **topic_coverage_pct** -- what fraction of the exam's taxonomy the deck
   covers (FR-2 coverage module), in [0, 100].

This module deliberately has **no dependency on Qt or anki**, so it can be
unit-tested with plain pytest. The Qt surface that displays it lives in
``aqt.mcat.panel``; the live backend wiring lives in
``aqt.mcat.panel._fetch_concept_mastery``.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from datetime import datetime, timezone

# --------------------------------------------------------------------------
# Give-up rule (spec §4 / PRD OD-2). Written down, enforced below.
#
#   Show NO Memory score until BOTH:
#     * graded_reviews   >= MIN_GRADED_REVIEWS   (>= 200), AND
#     * topic_coverage_% >= MIN_TOPIC_COVERAGE_PCT (>= 50%).
#
# Below either line the panel abstains and names the failing condition(s).
# --------------------------------------------------------------------------
MIN_GRADED_REVIEWS = 200
MIN_TOPIC_COVERAGE_PCT = 50.0

# Wald confidence-interval z-score for a ~95% two-sided interval.
WALD_Z = 1.96

# "How sure" thresholds. Documented and deterministic: a function of the
# number of cards contributing (N) and the topic coverage. Both gates must be
# met for a given confidence level; the lower of the two governs.
HOW_SURE_MEDIUM_MIN_CARDS = 500
HOW_SURE_HIGH_MIN_CARDS = 2000
HOW_SURE_MEDIUM_MIN_COVERAGE = 60.0
HOW_SURE_HIGH_MIN_COVERAGE = 80.0


@dataclass(frozen=True)
class ConceptMastery:
    """Per-concept mastery, mirroring the Rust ``ConceptMastery`` RPC row.

    ``avg_recall`` is the mean FSRS retrievability (in [0, 1]) across the
    concept's cards that have a memory state. ``cards_total`` counts the cards
    with a memory state (i.e. cards that actually contribute evidence).
    """

    concept: str
    cards_total: int
    cards_mastered: int
    avg_recall: float


@dataclass(frozen=True)
class MemoryScore:
    """Result of :func:`compute_memory_score`.

    When ``abstained`` is True, the numeric fields are ``None`` and
    ``reasons`` explains exactly which give-up condition(s) failed. When
    ``abstained`` is False, all numeric fields are populated.
    """

    abstained: bool
    # The standard honesty fields (all None when abstained):
    point_estimate_pct: float | None  # point estimate, as a percent
    range_low_pct: float | None  # likely-range lower bound, percent
    range_high_pct: float | None  # likely-range upper bound, percent
    how_sure: str | None  # "low" | "medium" | "high"
    # Always populated context:
    topic_coverage_pct: float
    graded_reviews: int
    cards_contributing: int  # N: cards with a memory state
    concepts_covered: int  # M: concepts with >=1 contributing card
    concepts_total: int  # K: concepts in the taxonomy/input
    last_updated: str  # ISO-8601 UTC timestamp
    reasons: list[str] = field(default_factory=list)
    method: str = ""

    def headline(self) -> str:
        """One-line human summary, honest in both states."""
        if self.abstained:
            return "Memory score unavailable - not enough data yet."
        assert self.point_estimate_pct is not None
        return (
            f"Memory {self.point_estimate_pct:.0f}% "
            f"(likely {self.range_low_pct:.0f}-{self.range_high_pct:.0f}%)"
        )


def _now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _clamp01(x: float) -> float:
    return max(0.0, min(1.0, x))


def how_sure_indicator(cards_contributing: int, topic_coverage_pct: float) -> str:
    """Deterministic "how sure" indicator.

    Derived purely from the amount of evidence (``cards_contributing``, N) and
    how much of the exam the deck covers. A level requires *both* its card
    floor and its coverage floor; we take the lower qualifying level so a huge
    deck that skips most topics is still reported as low-confidence.
    """
    high = (
        cards_contributing >= HOW_SURE_HIGH_MIN_CARDS
        and topic_coverage_pct >= HOW_SURE_HIGH_MIN_COVERAGE
    )
    if high:
        return "high"
    medium = (
        cards_contributing >= HOW_SURE_MEDIUM_MIN_CARDS
        and topic_coverage_pct >= HOW_SURE_MEDIUM_MIN_COVERAGE
    )
    if medium:
        return "medium"
    return "low"


def compute_memory_score(
    mastery: list[ConceptMastery],
    graded_reviews: int,
    topic_coverage_pct: float,
    concepts_total: int | None = None,
    *,
    now_iso: str | None = None,
) -> MemoryScore:
    """Compute the honest Memory score.

    Args:
        mastery: per-concept mastery rows (only concepts the deck covers need
            to appear; rows with ``cards_total == 0`` contribute no evidence).
        graded_reviews: total count of graded reviews available (revlog count).
        topic_coverage_pct: percent of the exam taxonomy the deck covers,
            in [0, 100] (from FR-2).
        concepts_total: size of the full taxonomy (K). Defaults to the number
            of rows supplied if not given.
        now_iso: optional fixed timestamp (for deterministic tests).

    Returns:
        A :class:`MemoryScore`. If the give-up rule is not satisfied the result
        is an *abstain* result whose ``reasons`` name the failing condition(s).

    Method (stated so it can be reproduced and audited):

    * **Point estimate** = card-weighted mean recall across covered concepts:
      ``sum(avg_recall_i * cards_total_i) / sum(cards_total_i)``. This weights
      each concept by how much memory-state evidence it actually has.
    * **Likely range** = a Wald (normal-approximation) interval on the recall
      proportion ``p`` with N = total contributing cards:
      ``halfwidth = z * sqrt(p*(1-p)/N)``, z = 1.96, clamped to [0, 1]. The
      band shrinks as N grows, which is the honest behaviour: more evidence,
      tighter range.
    """
    now = now_iso or _now_iso()
    coverage = float(topic_coverage_pct)

    n = sum(m.cards_total for m in mastery)
    concepts_covered = sum(1 for m in mastery if m.cards_total > 0)
    k = concepts_total if concepts_total is not None else len(mastery)

    # ----- Give-up rule (enforced) -----------------------------------------
    reasons: list[str] = []
    if graded_reviews < MIN_GRADED_REVIEWS:
        reasons.append(
            f"Need {MIN_GRADED_REVIEWS} graded reviews (have {graded_reviews})."
        )
    if coverage < MIN_TOPIC_COVERAGE_PCT:
        reasons.append(
            f"Need {MIN_TOPIC_COVERAGE_PCT:.0f}% topic coverage (have {coverage:.0f}%)."
        )
    # Even if the explicit thresholds pass, we cannot compute a recall mean
    # with zero contributing cards.
    if n <= 0:
        reasons.append("Need at least one card with a memory state (have 0).")

    if reasons:
        return MemoryScore(
            abstained=True,
            point_estimate_pct=None,
            range_low_pct=None,
            range_high_pct=None,
            how_sure=None,
            topic_coverage_pct=coverage,
            graded_reviews=graded_reviews,
            cards_contributing=n,
            concepts_covered=concepts_covered,
            concepts_total=k,
            last_updated=now,
            reasons=reasons,
            method="abstained: give-up rule not satisfied",
        )

    # ----- Point estimate: card-weighted mean recall -----------------------
    weighted = sum(_clamp01(m.avg_recall) * m.cards_total for m in mastery)
    p = _clamp01(weighted / n)

    # ----- Likely range: Wald interval, shrinks with N ---------------------
    halfwidth = WALD_Z * math.sqrt(p * (1.0 - p) / n)
    low = _clamp01(p - halfwidth)
    high = _clamp01(p + halfwidth)

    how_sure = how_sure_indicator(n, coverage)

    reasons = [
        f"Based on {n} cards across {concepts_covered}/{k} concepts.",
        f"Deck covers {coverage:.0f}% of the exam taxonomy.",
        f"{graded_reviews} graded reviews recorded.",
    ]

    return MemoryScore(
        abstained=False,
        point_estimate_pct=round(p * 100.0, 1),
        range_low_pct=round(low * 100.0, 1),
        range_high_pct=round(high * 100.0, 1),
        how_sure=how_sure,
        topic_coverage_pct=coverage,
        graded_reviews=graded_reviews,
        cards_contributing=n,
        concepts_covered=concepts_covered,
        concepts_total=k,
        last_updated=now,
        reasons=reasons,
        method=(
            "point estimate = card-weighted mean FSRS recall; "
            f"range = Wald {WALD_Z}-sigma interval p +/- z*sqrt(p(1-p)/N), "
            f"N={n} cards"
        ),
    )
