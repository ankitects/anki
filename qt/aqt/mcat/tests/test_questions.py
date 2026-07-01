# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Standalone tests for the concept-coded practice questions (FR-3 "Applying").

Runnable with plain pytest (no Anki/Qt build needed):

    pytest qt/aqt/mcat/tests/

or with no pytest at all:

    python qt/aqt/mcat/tests/test_questions.py

Covers the three things that matter:
  * the bank loads and every question is coded to a real taxonomy concept,
  * attempt storage accumulates and stays self-consistent (correct <= attempts),
  * malformed/garbage stored config never crashes the reader.
"""

from __future__ import annotations

import json
import os
import sys

# Import the pure module directly from the parent `mcat/` dir so this runs
# under plain pytest without importing the `aqt` package (which needs a build).
_MCAT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, _MCAT_DIR)

from questions import (  # type: ignore[import-not-found]  # noqa: E402
    _normalize,
    concept_question_stats,
    get_stats,
    load_questions,
    record_attempt,
    reset_stats,
)

_REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(_MCAT_DIR)))


class _FakeCol:
    """Minimal stand-in for the collection's config get/set."""

    def __init__(self) -> None:
        self._cfg: dict = {}

    def get_config(self, key, default=None):
        return self._cfg.get(key, default)

    def set_config(self, key, val) -> None:
        self._cfg[key] = val


def _taxonomy_ids() -> set[str]:
    path = os.path.join(_REPO_ROOT, "mcat", "taxonomy.json")
    data = json.loads(open(path, encoding="utf-8").read())
    return {c["id"] for c in data.get("concepts", [])}


# --------------------------------------------------------------------------
# Bank loads and is coded to real concepts
# --------------------------------------------------------------------------


def test_bank_loads_and_is_nonempty():
    qs = load_questions()
    assert len(qs) >= 10


def test_every_question_codes_to_a_real_concept():
    ids = _taxonomy_ids()
    for q in load_questions():
        assert q.concept_id in ids, f"{q.id} -> unknown concept {q.concept_id}"


def test_answer_index_in_range_and_gradable():
    for q in load_questions():
        assert 0 <= q.answer_index < len(q.choices)
        assert q.is_correct(q.answer_index)
        assert not q.is_correct((q.answer_index + 1) % len(q.choices))


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


def test_reset_clears_stats():
    col = _FakeCol()
    record_attempt(col, "1A", correct=True)
    reset_stats(col)
    assert get_stats(col) == {}


def test_concept_question_stats_accuracy():
    col = _FakeCol()
    for _ in range(3):
        record_attempt(col, "1A", correct=True)
    record_attempt(col, "1A", correct=False)
    (row,) = [r for r in concept_question_stats(col) if r.concept_id == "1A"]
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
