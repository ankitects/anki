# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Standalone tests for the Readiness (projected MCAT) score.

Runnable with plain pytest (no Anki/Qt build):

    pytest qt/aqt/mcat/tests/

or with no pytest at all:

    python qt/aqt/mcat/tests/test_readiness.py

Covers the honesty-critical behaviour: abstain below the give-up rule, the
472-528 linear mapping, the range staying on-scale and widening as coverage
falls, and the coverage-driven confidence level.
"""

from __future__ import annotations

import os
import sys

_MCAT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _MCAT_DIR)

from readiness import (  # type: ignore[import-not-found]  # noqa: E402
    MCAT_MAX,
    MCAT_MIN,
    MIN_QUESTION_ATTEMPTS,
    compute_readiness,
    readiness_confidence,
)

FIXED = "2026-06-30T00:00:00+00:00"


def _score(attempts=120, correct=60, concepts=8, coverage=70.0, total=31, best=None):
    return compute_readiness(
        question_attempts=attempts,
        question_correct=correct,
        concepts_with_questions=concepts,
        topic_coverage_pct=coverage,
        concepts_total=total,
        best_next=best,
        now_iso=FIXED,
    )


# --------------------------------------------------------------------------
# Give-up rule
# --------------------------------------------------------------------------


def test_abstain_when_too_few_attempts():
    res = _score(attempts=10, correct=5, concepts=5, coverage=80.0)
    assert res.abstained
    assert res.projected is None
    assert any(str(MIN_QUESTION_ATTEMPTS) in r and "10" in r for r in res.reasons)


def test_abstain_when_too_few_concepts():
    res = _score(attempts=100, correct=50, concepts=1, coverage=80.0)
    assert res.abstained
    assert any("concept" in r.lower() for r in res.reasons)


def test_abstain_when_low_coverage():
    res = _score(attempts=100, correct=50, concepts=5, coverage=30.0)
    assert res.abstained
    assert any("coverage" in r.lower() for r in res.reasons)


def test_abstain_still_reports_best_next():
    res = _score(attempts=0, correct=0, concepts=0, coverage=10.0, best=("6A", "Sensing"))
    assert res.abstained
    assert res.best_next_id == "6A"


# --------------------------------------------------------------------------
# Linear 472-528 mapping
# --------------------------------------------------------------------------


def test_projection_is_linear_on_accuracy():
    # p = 0.5 -> 472 + 28 = 500
    assert _score(attempts=200, correct=100, coverage=100.0, concepts=31).projected == 500
    # p = 1.0 -> 528 (max)
    assert _score(attempts=200, correct=200, coverage=100.0, concepts=31).projected == 528
    # p = 0.0 -> 472 (min)
    assert _score(attempts=200, correct=0, coverage=100.0, concepts=31).projected == 472


def test_range_stays_on_scale():
    res = _score(attempts=40, correct=39, coverage=55.0, concepts=4)
    assert MCAT_MIN <= res.range_low <= res.range_high <= MCAT_MAX


def test_range_widens_with_lower_coverage():
    hi = _score(attempts=300, correct=150, coverage=95.0, concepts=30)
    lo = _score(attempts=300, correct=150, coverage=55.0, concepts=6)
    hi_width = hi.range_high - hi.range_low
    lo_width = lo.range_high - lo.range_low
    assert lo_width > hi_width  # less coverage -> wider band


def test_performance_pct_reported():
    res = _score(attempts=200, correct=130, coverage=80.0, concepts=20)
    assert res.performance_pct == 65.0


# --------------------------------------------------------------------------
# Confidence
# --------------------------------------------------------------------------


def test_confidence_levels():
    assert readiness_confidence(10, 90.0, 90.0) == "low"  # too few attempts
    assert readiness_confidence(5000, 90.0, 40.0) == "low"  # too little topic cov
    assert readiness_confidence(150, 40.0, 65.0) == "medium"
    assert readiness_confidence(400, 70.0, 85.0) == "high"


def test_not_blended_with_memory():
    # Readiness takes no memory/recall input at all -- proof it can't blend.
    import inspect

    params = set(inspect.signature(compute_readiness).parameters)
    assert "avg_recall" not in params and "memory" not in params


def _run_standalone() -> int:
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
