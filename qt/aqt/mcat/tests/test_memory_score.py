# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Standalone tests for the honest Memory score (FR-5).

Runnable with plain pytest, no Anki/Qt required:

    pytest qt/aqt/mcat/tests/

or with no pytest at all:

    python qt/aqt/mcat/tests/test_memory_score.py

Covers the three things that matter for honesty:
  * abstain below threshold (each give-up condition, independently),
  * point-estimate math (card-weighted mean recall),
  * the likely-range half-width shrinking as N grows.
"""

from __future__ import annotations

import math
import os
import sys

# Import the pure-logic module directly from the parent `mcat/` dir, so this
# test runs under plain pytest without importing the `aqt` package (which
# needs a full build). This `tests/` dir intentionally has no __init__.py so
# pytest collects the file as a top-level module and never climbs into `aqt`.
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from memory_score import (  # noqa: E402
    MIN_GRADED_REVIEWS,
    MIN_TOPIC_COVERAGE_PCT,
    WALD_Z,
    ConceptMastery,
    compute_memory_score,
    how_sure_indicator,
)

FIXED = "2026-06-30T00:00:00+00:00"


def _mastery(pairs):
    """pairs: list of (cards_total, avg_recall)."""
    return [ConceptMastery(f"c{i}", n, int(n * r), r) for i, (n, r) in enumerate(pairs)]


# --------------------------------------------------------------------------
# Give-up rule: abstain below threshold
# --------------------------------------------------------------------------


def test_abstain_when_too_few_reviews():
    res = compute_memory_score(
        mastery=_mastery([(100, 0.8)]),
        graded_reviews=142,
        topic_coverage_pct=80.0,
        now_iso=FIXED,
    )
    assert res.abstained
    assert res.point_estimate_pct is None
    # Names the failing condition with both numbers.
    assert any("142" in r and "200" in r for r in res.reasons)
    # Coverage condition passed, so it must NOT appear.
    assert not any("coverage" in r.lower() for r in res.reasons)


def test_abstain_when_too_little_coverage():
    res = compute_memory_score(
        mastery=_mastery([(100, 0.8)]),
        graded_reviews=5000,
        topic_coverage_pct=38.0,
        now_iso=FIXED,
    )
    assert res.abstained
    assert any("38" in r and "50" in r for r in res.reasons)
    # Reviews condition passed, so it must NOT appear.
    assert not any("graded reviews" in r.lower() for r in res.reasons)


def test_abstain_names_both_failing_conditions():
    res = compute_memory_score(
        mastery=_mastery([(100, 0.8)]),
        graded_reviews=142,
        topic_coverage_pct=38.0,
        now_iso=FIXED,
    )
    assert res.abstained
    assert len(res.reasons) == 2


def test_abstain_with_zero_contributing_cards():
    res = compute_memory_score(
        mastery=_mastery([(0, 0.0)]),
        graded_reviews=5000,
        topic_coverage_pct=90.0,
        now_iso=FIXED,
    )
    assert res.abstained
    assert any("memory state" in r.lower() for r in res.reasons)


def test_boundary_exactly_at_threshold_does_not_abstain():
    res = compute_memory_score(
        mastery=_mastery([(100, 0.8)]),
        graded_reviews=MIN_GRADED_REVIEWS,
        topic_coverage_pct=MIN_TOPIC_COVERAGE_PCT,
        now_iso=FIXED,
    )
    assert not res.abstained


# --------------------------------------------------------------------------
# Point-estimate math: card-weighted mean recall
# --------------------------------------------------------------------------


def test_point_estimate_is_card_weighted_mean():
    # 300 cards @0.90 and 100 cards @0.50 -> (300*.9 + 100*.5)/400 = 0.80
    res = compute_memory_score(
        mastery=_mastery([(300, 0.90), (100, 0.50)]),
        graded_reviews=1000,
        topic_coverage_pct=70.0,
        now_iso=FIXED,
    )
    assert not res.abstained
    assert res.point_estimate_pct == 80.0
    assert res.cards_contributing == 400


def test_point_estimate_single_concept():
    res = compute_memory_score(
        mastery=_mastery([(250, 0.736)]),
        graded_reviews=1000,
        topic_coverage_pct=70.0,
        now_iso=FIXED,
    )
    assert res.point_estimate_pct == 73.6


def test_concept_counts_reported():
    res = compute_memory_score(
        mastery=_mastery([(300, 0.9), (100, 0.5), (0, 0.0)]),
        graded_reviews=1000,
        topic_coverage_pct=70.0,
        concepts_total=12,
        now_iso=FIXED,
    )
    # Two concepts have contributing cards; K passed explicitly.
    assert res.concepts_covered == 2
    assert res.concepts_total == 12


# --------------------------------------------------------------------------
# Likely range: Wald interval, shrinks as N grows
# --------------------------------------------------------------------------


def _halfwidth(res):
    return (res.range_high_pct - res.range_low_pct) / 2.0


def test_range_matches_wald_formula():
    res = compute_memory_score(
        mastery=_mastery([(400, 0.80)]),
        graded_reviews=1000,
        topic_coverage_pct=70.0,
        now_iso=FIXED,
    )
    p, n = 0.80, 400
    expected_hw = WALD_Z * math.sqrt(p * (1 - p) / n) * 100.0
    assert abs(_halfwidth(res) - expected_hw) < 0.2


def test_range_shrinks_as_n_grows():
    hws = []
    for n in (250, 1000, 4000, 16000):
        res = compute_memory_score(
            mastery=_mastery([(n, 0.80)]),
            graded_reviews=5000,
            topic_coverage_pct=70.0,
            now_iso=FIXED,
        )
        hws.append(_halfwidth(res))
    # Strictly decreasing half-width: more data -> tighter range.
    assert all(hws[i] > hws[i + 1] for i in range(len(hws) - 1))
    # Point estimate unchanged by N (same p).
    # (sanity) doubling N ~ shrinks halfwidth by sqrt(2)
    assert hws[0] / hws[1] == round(hws[0] / hws[1], 6)


def test_range_clamped_to_unit_interval():
    # Extreme p with small N could push bounds outside [0,100]; must clamp.
    res = compute_memory_score(
        mastery=_mastery([(210, 0.99)]),
        graded_reviews=1000,
        topic_coverage_pct=70.0,
        now_iso=FIXED,
    )
    assert 0.0 <= res.range_low_pct <= 100.0
    assert 0.0 <= res.range_high_pct <= 100.0


# --------------------------------------------------------------------------
# How-sure indicator
# --------------------------------------------------------------------------


def test_how_sure_levels():
    assert how_sure_indicator(100, 90.0) == "low"  # too few cards
    assert how_sure_indicator(5000, 40.0) == "low"  # too little coverage
    assert how_sure_indicator(600, 65.0) == "medium"
    assert how_sure_indicator(5000, 85.0) == "high"


def _run_standalone() -> int:
    """Run every ``test_*`` in this module without pytest's package machinery.

    Lets ``python test_memory_score.py`` work on a bare checkout even though
    the file lives inside the ``aqt`` package (whose ``__init__`` needs a full
    build). ``pytest qt/aqt/mcat/tests`` also works via ``tests/conftest.py``.
    """
    tests = {
        name: obj
        for name, obj in sorted(globals().items())
        if name.startswith("test_") and callable(obj)
    }
    failures = 0
    for name, fn in tests.items():
        try:
            fn()
            print(f"PASS {name}")
        except Exception as exc:  # noqa: BLE001
            failures += 1
            print(f"FAIL {name}: {exc!r}")
    print(f"\n{len(tests) - failures} passed, {failures} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(_run_standalone())
