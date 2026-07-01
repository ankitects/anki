# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Honest, deterministic Readiness score -- a projected MCAT score (spec §4).

The spec asks for three *separate* scores (§2, §4):

* **Memory** -- can the student recall a fact now? (``memory_score.py``)
* **Performance** -- can they answer a new exam-style question? (practice-question
  accuracy)
* **Readiness** -- *what score would they get today, on the real scale, and how
  sure are you?* This module.

For the MCAT the readiness scale is the real total range **472–528** (four
sections, each 118–132). This module turns the Performance signal (accuracy on
the concept-coded practice questions) into a projected total, with a likely
range and a confidence level.

Honesty is mandatory (§1, §2, §9). A made-up readiness number is an automatic
fail, so this module:

* is a **documented, deterministic** map -- observed practice-question accuracy
  ``p`` maps linearly onto the 472–528 scale (``472 + 56·p``); no AI, no RNG;
* widens the range when little of the exam is covered (thin evidence → wider
  band), and reports a **confidence** level driven mostly by coverage;
* **abstains** below a written give-up rule (not enough questions/coverage);
* is **never blended** with the Memory score -- the two are computed and shown
  independently;
* is explicit that it is **unvalidated** against real practice tests (spec §9
  Step 4 is future work) and that past-guess calibration is not yet available.
  Per §9, stating this plainly is worth more than a polished number we cannot
  back up.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from datetime import datetime, timezone

# Real MCAT total scale (§5): 4 sections × [118, 132] = [472, 528].
MCAT_MIN = 472
MCAT_MAX = 528
MCAT_SPAN = MCAT_MAX - MCAT_MIN  # 56

WALD_Z = 1.96  # ~95% interval on the accuracy proportion

# --------------------------------------------------------------------------
# Give-up rule (§1/§4/§7c). Readiness needs *performance* evidence, so on top of
# topic coverage it requires a floor of graded practice-question attempts spread
# across several concepts. Below any line the score abstains and says why.
# --------------------------------------------------------------------------
MIN_QUESTION_ATTEMPTS = 30
MIN_CONCEPTS_WITH_QUESTIONS = 3
MIN_TOPIC_COVERAGE_PCT = 50.0

# Range widening: the less of the exam we have evidence for, the less certain
# the projection, so the band grows with (1 - coverage). Two independent gaps
# count -- topic coverage (cards) and question coverage (concepts we've actually
# quizzed) -- each able to add up to this fraction of the full scale.
TOPIC_COVERAGE_WIDEN = 0.15
QUESTION_COVERAGE_WIDEN = 0.15

# Confidence gates (the lower qualifying level governs).
CONF_MEDIUM_MIN_ATTEMPTS = 100
CONF_MEDIUM_MIN_QCOVERAGE = 30.0
CONF_MEDIUM_MIN_TOPIC_COVERAGE = 60.0
CONF_HIGH_MIN_ATTEMPTS = 300
CONF_HIGH_MIN_QCOVERAGE = 60.0
CONF_HIGH_MIN_TOPIC_COVERAGE = 80.0


@dataclass(frozen=True)
class ReadinessScore:
    """Result of :func:`compute_readiness`.

    When ``abstained`` is True the numeric fields are ``None`` and ``reasons``
    names the failing give-up condition(s).
    """

    abstained: bool
    projected: int | None  # projected MCAT total (472–528)
    range_low: int | None
    range_high: int | None
    confidence: str | None  # "low" | "medium" | "high"
    performance_pct: float | None  # practice-question accuracy, percent
    # Always-populated context:
    question_attempts: int
    concepts_with_questions: int
    topic_coverage_pct: float
    concepts_total: int
    best_next_id: str | None
    best_next_name: str | None
    last_updated: str
    reasons: list[str] = field(default_factory=list)
    method: str = ""
    disclaimer: str = ""

    def headline(self) -> str:
        if self.abstained:
            return "Projected MCAT unavailable - not enough question data yet."
        return (
            f"Projected MCAT {self.projected} "
            f"(likely {self.range_low}-{self.range_high})"
        )


def _now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


def _clamp01(x: float) -> float:
    return max(0.0, min(1.0, x))


def readiness_confidence(
    question_attempts: int,
    question_coverage_pct: float,
    topic_coverage_pct: float,
) -> str:
    """Deterministic confidence: needs both its evidence floor and its coverage
    floors; the lower qualifying level governs. Coverage dominates, matching the
    spec's example ('confidence: low, because you have only studied 42% of the
    topics')."""
    if (
        question_attempts >= CONF_HIGH_MIN_ATTEMPTS
        and question_coverage_pct >= CONF_HIGH_MIN_QCOVERAGE
        and topic_coverage_pct >= CONF_HIGH_MIN_TOPIC_COVERAGE
    ):
        return "high"
    if (
        question_attempts >= CONF_MEDIUM_MIN_ATTEMPTS
        and question_coverage_pct >= CONF_MEDIUM_MIN_QCOVERAGE
        and topic_coverage_pct >= CONF_MEDIUM_MIN_TOPIC_COVERAGE
    ):
        return "medium"
    return "low"


def compute_readiness(
    question_attempts: int,
    question_correct: int,
    concepts_with_questions: int,
    topic_coverage_pct: float,
    concepts_total: int,
    best_next: tuple[str, str] | None = None,
    *,
    now_iso: str | None = None,
) -> ReadinessScore:
    """Project an MCAT total from practice-question performance + coverage.

    Args:
        question_attempts: total graded practice-question attempts.
        question_correct: of those, how many were correct.
        concepts_with_questions: distinct concepts with >=1 attempt.
        topic_coverage_pct: percent of the exam taxonomy the deck covers [0,100].
        concepts_total: taxonomy size (denominator for question coverage).
        best_next: ``(concept_id, name)`` of the single highest-NTR concept --
            the spec's required "best next thing to study".
        now_iso: fixed timestamp for deterministic tests.

    Method (documented so it can be reproduced/audited):

    * **Point estimate** = ``472 + 56 · p`` where ``p`` is observed accuracy on
      the practice questions (``correct / attempts``). A student at 50% lands
      mid-scale; 100% → 528; 0% → 472.
    * **Range** = a Wald interval on ``p`` scaled to the 56-point span, *plus* a
      coverage-gap term that widens the band as topic/question coverage falls.
      Clamped to [472, 528].
    * **Confidence** = :func:`readiness_confidence` (coverage-dominated).
    """
    now = now_iso or _now_iso()
    coverage = float(topic_coverage_pct)
    k = concepts_total if concepts_total > 0 else max(concepts_with_questions, 1)
    q_coverage_pct = concepts_with_questions / k * 100.0 if k else 0.0

    disclaimer = (
        "Heuristic projection: in-app question accuracy mapped linearly to the "
        "472-528 scale, widened for low coverage. NOT blended with the Memory "
        "score, and NOT yet validated against real practice tests (spec Step 4 "
        "pending); past-guess calibration is not available yet. Treat as a "
        "rough, honest estimate."
    )

    # ----- Give-up rule -----------------------------------------------------
    reasons: list[str] = []
    if question_attempts < MIN_QUESTION_ATTEMPTS:
        reasons.append(
            f"Need {MIN_QUESTION_ATTEMPTS} practice-question attempts "
            f"(have {question_attempts})."
        )
    if concepts_with_questions < MIN_CONCEPTS_WITH_QUESTIONS:
        reasons.append(
            f"Need questions across {MIN_CONCEPTS_WITH_QUESTIONS} concepts "
            f"(have {concepts_with_questions})."
        )
    if coverage < MIN_TOPIC_COVERAGE_PCT:
        reasons.append(
            f"Need {MIN_TOPIC_COVERAGE_PCT:.0f}% topic coverage (have {coverage:.0f}%)."
        )

    if reasons:
        return ReadinessScore(
            abstained=True,
            projected=None,
            range_low=None,
            range_high=None,
            confidence=None,
            performance_pct=None,
            question_attempts=question_attempts,
            concepts_with_questions=concepts_with_questions,
            topic_coverage_pct=coverage,
            concepts_total=concepts_total,
            best_next_id=best_next[0] if best_next else None,
            best_next_name=best_next[1] if best_next else None,
            last_updated=now,
            reasons=reasons,
            method="abstained: readiness give-up rule not satisfied",
            disclaimer=disclaimer,
        )

    # ----- Point estimate: performance mapped onto the scale ----------------
    p = _clamp01(question_correct / question_attempts)
    projected_f = MCAT_MIN + MCAT_SPAN * p

    # ----- Range: sampling error on p + coverage-gap widening ---------------
    sampling_hw = WALD_Z * math.sqrt(p * (1.0 - p) / question_attempts) * MCAT_SPAN
    coverage_hw = MCAT_SPAN * (
        TOPIC_COVERAGE_WIDEN * (1.0 - _clamp01(coverage / 100.0))
        + QUESTION_COVERAGE_WIDEN * (1.0 - _clamp01(q_coverage_pct / 100.0))
    )
    half = sampling_hw + coverage_hw

    projected = round(projected_f)
    low = max(MCAT_MIN, round(projected_f - half))
    high = min(MCAT_MAX, round(projected_f + half))

    confidence = readiness_confidence(question_attempts, q_coverage_pct, coverage)

    reasons = [
        f"Based on {question_correct}/{question_attempts} practice questions "
        f"correct across {concepts_with_questions}/{k} concepts.",
        f"Deck covers {coverage:.0f}% of the exam taxonomy.",
        "Range widens with missing coverage; confidence is coverage-driven.",
    ]
    if best_next:
        reasons.append(f"Best next thing to study: {best_next[0]} - {best_next[1]}.")

    return ReadinessScore(
        abstained=False,
        projected=projected,
        range_low=low,
        range_high=high,
        confidence=confidence,
        performance_pct=round(p * 100.0, 1),
        question_attempts=question_attempts,
        concepts_with_questions=concepts_with_questions,
        topic_coverage_pct=coverage,
        concepts_total=concepts_total,
        best_next_id=best_next[0] if best_next else None,
        best_next_name=best_next[1] if best_next else None,
        last_updated=now,
        reasons=reasons,
        method=(
            "projected = 472 + 56*p, p = practice-question accuracy; range = "
            f"Wald {WALD_Z}-sigma on p (scaled) + coverage-gap widening; clamped "
            "to [472, 528]"
        ),
        disclaimer=disclaimer,
    )
