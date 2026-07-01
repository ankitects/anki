# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Standalone tests for practice-test ingestion (the kernel's input signal).

Runnable with plain pytest (no Anki/Qt build needed):

    pytest qt/aqt/mcat/tests/

or with no pytest at all:

    python qt/aqt/mcat/tests/test_practice_tests.py

Covers the things that matter for ingestion:
  * concept choices load from the taxonomy,
  * a practice-test CSV parses tolerantly (bad rows skipped, not fatal), and
    every parsed concept is a real taxonomy concept,
  * attempt storage accumulates, batches, and stays self-consistent, and
  * malformed/garbage stored config never crashes the reader.
"""

from __future__ import annotations

import os
import sys

# Import the pure module directly from the parent `mcat/` dir so this runs
# under plain pytest without importing the `aqt` package (which needs a build).
_MCAT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _MCAT_DIR)

from practice_tests import (  # type: ignore[import-not-found]  # noqa: E402
    IngestedAttempt,
    _normalize,
    concept_attempt_stats,
    get_stats,
    load_concept_choices,
    parse_practice_test_csv,
    record_attempt,
    record_batch,
    reset_stats,
)


class _FakeCol:
    """Minimal stand-in for the collection's config get/set."""

    def __init__(self) -> None:
        self._cfg: dict = {}

    def get_config(self, key, default=None):
        return self._cfg.get(key, default)

    def set_config(self, key, val) -> None:
        self._cfg[key] = val


def _taxonomy_ids() -> set[str]:
    return {c.concept_id for c in load_concept_choices()}


# --------------------------------------------------------------------------
# Concept choices load from the taxonomy
# --------------------------------------------------------------------------


def test_concept_choices_load_and_are_labelled():
    choices = load_concept_choices()
    assert len(choices) >= 10
    c = choices[0]
    assert c.concept_id and c.name
    assert c.label().startswith(c.concept_id)


# --------------------------------------------------------------------------
# CSV parsing
# --------------------------------------------------------------------------


def test_parse_csv_basic():
    ids = sorted(_taxonomy_ids())
    a, b = ids[0], ids[1]
    text = f"question,concept,correct\nQ1,{a},right\nQ2,{b},wrong\n"
    attempts, warnings = parse_practice_test_csv(text)
    assert warnings == []
    assert attempts == [
        IngestedAttempt(concept_id=a, correct=True, label="Q1"),
        IngestedAttempt(concept_id=b, correct=False, label="Q2"),
    ]


def test_parse_csv_is_case_insensitive_and_flexible_headers():
    a = sorted(_taxonomy_ids())[0]
    # Alternative header names + varied truthy tokens, header in mixed case.
    text = f"Concept_ID,Result\n{a},1\n{a},0\n{a},Correct\n"
    attempts, warnings = parse_practice_test_csv(text)
    assert warnings == []
    assert [x.correct for x in attempts] == [True, False, True]


def test_parse_csv_skips_bad_rows_without_crashing():
    a = sorted(_taxonomy_ids())[0]
    text = (
        "concept,correct\n"
        f"{a},yes\n"
        "NOTACONCEPT,yes\n"  # unknown concept -> skipped
        f",yes\n"  # missing concept -> skipped
        f"{a},maybe\n"  # unrecognised result -> skipped
    )
    attempts, warnings = parse_practice_test_csv(text)
    assert len(attempts) == 1
    assert len(warnings) == 3


def test_parse_csv_missing_columns_reports_error():
    attempts, warnings = parse_practice_test_csv("foo,bar\n1,2\n")
    assert attempts == []
    assert warnings and "concept" in warnings[0].lower()


def test_every_parsed_concept_is_real():
    ids = _taxonomy_ids()
    a = sorted(ids)[0]
    attempts, _ = parse_practice_test_csv(f"concept,correct\n{a},right\n")
    for x in attempts:
        assert x.concept_id in ids


# --------------------------------------------------------------------------
# Attempt storage
# --------------------------------------------------------------------------


def test_record_attempt_accumulates():
    col = _FakeCol()
    record_attempt(col, "1A", correct=True)
    record_attempt(col, "1A", correct=False)
    record_attempt(col, "4C", correct=True)
    stats = get_stats(col)
    assert stats["1A"] == {"attempts": 2, "correct": 1}
    assert stats["4C"] == {"attempts": 1, "correct": 1}


def test_record_batch_writes_once_and_accumulates():
    col = _FakeCol()
    record_batch(
        col,
        [
            ("1A", True),
            ("1A", False),
            IngestedAttempt("4C", True, "Q3"),
        ],
    )
    stats = get_stats(col)
    assert stats["1A"] == {"attempts": 2, "correct": 1}
    assert stats["4C"] == {"attempts": 1, "correct": 1}


def test_reset_clears_stats():
    col = _FakeCol()
    record_attempt(col, "1A", correct=True)
    reset_stats(col)
    assert get_stats(col) == {}


def test_concept_attempt_stats_accuracy():
    col = _FakeCol()
    for _ in range(3):
        record_attempt(col, "1A", correct=True)
    record_attempt(col, "1A", correct=False)
    (row,) = [r for r in concept_attempt_stats(col) if r.concept_id == "1A"]
    assert row.attempts == 4
    assert row.correct == 3
    assert abs(row.accuracy - 0.75) < 1e-9


# --------------------------------------------------------------------------
# Robustness: garbage config can't crash the reader
# --------------------------------------------------------------------------


def test_normalize_tolerates_garbage():
    assert _normalize(None) == {}
    assert _normalize("nonsense") == {}
    assert _normalize({"1A": "bad"}) == {}


def test_normalize_clamps_correct_to_attempts():
    # correct > attempts is impossible; it must be clamped down.
    out = _normalize({"1A": {"attempts": 2, "correct": 9}})
    assert out["1A"] == {"attempts": 2, "correct": 2}
    # negative values floored at 0.
    out = _normalize({"1B": {"attempts": -5, "correct": -1}})
    assert out["1B"] == {"attempts": 0, "correct": 0}


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
