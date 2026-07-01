# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Concept-coded practice questions -- the small "Applying" signal (Phase 1).

The MCAT prep research splits study into *understanding -> memorizing ->
applying*. Phase 1 builds the memorizing engine; this module pulls a **small,
deliberate slice of "applying" forward**: a set of concept-coded, exam-style
questions whose results feed the per-concept **NTR** signal.

What this does and does not touch:

* It **feeds NTR** (review prioritization, FR-3). Getting a concept's questions
  wrong raises that concept's NTR; getting them right lowers it -- exactly the
  blend implemented in the Rust engine's ``weakness``.
* It deliberately does **not** feed the displayed **Memory** score, which stays
  a pure function of FSRS card recall. Keeping question performance out of
  Memory preserves the spec's no-score-blending rule; a separate Performance
  score over questions is still deferred to a later phase.

Everything here is deterministic -- no AI. The question bank is a static JSON
file (``mcat/questions.json``); attempt counts persist in the collection
config so they survive restarts and can be handed to the Rust RPC each time the
dashboard or queue is computed.

This module has **no hard Qt dependency** so it can be unit-tested with plain
pytest. The Qt quiz surface lives in :mod:`aqt.mcat.quiz`.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

# Collection-config key under which per-concept attempt tallies are stored. The
# value is ``{concept_id: {"attempts": int, "correct": int}}``.
CONFIG_KEY = "mcatQuestionStats"


@dataclass(frozen=True)
class Question:
    """A single concept-coded practice question.

    ``concept_id`` matches a rule id in ``mcat/taxonomy.json`` (e.g. "1A"); that
    is the link that lets an answer move the right concept's NTR.
    """

    id: str
    concept_id: str
    section: str
    stem: str
    choices: tuple[str, ...]
    answer_index: int
    explanation: str

    def is_correct(self, choice_index: int) -> bool:
        return choice_index == self.answer_index


@dataclass(frozen=True)
class ConceptQuestionStat:
    """Aggregated attempts/correct for one concept (mirrors the proto row)."""

    concept_id: str
    attempts: int
    correct: int

    @property
    def accuracy(self) -> float:
        return self.correct / self.attempts if self.attempts else 0.0


def _questions_path() -> Path:
    """Locate ``mcat/questions.json`` (honours ``MCAT_QUESTIONS_PATH``)."""
    override = os.environ.get("MCAT_QUESTIONS_PATH")
    if override:
        return Path(override)
    return Path(__file__).resolve().parents[3] / "mcat" / "questions.json"


@lru_cache(maxsize=1)
def load_questions() -> tuple[Question, ...]:
    """Load and validate the question bank.

    Raises ``FileNotFoundError`` if the bank is missing so callers can fall back
    rather than silently presenting an empty quiz. Malformed entries raise so
    the data is caught in development, not at the student.
    """
    data = json.loads(_questions_path().read_text(encoding="utf-8"))
    out: list[Question] = []
    for q in data.get("questions", []):
        choices = tuple(q["choices"])
        answer_index = int(q["answer_index"])
        if not (0 <= answer_index < len(choices)):
            raise ValueError(f"question {q.get('id')!r}: answer_index out of range")
        out.append(
            Question(
                id=q["id"],
                concept_id=q["concept_id"],
                section=q.get("section", ""),
                stem=q["stem"],
                choices=choices,
                answer_index=answer_index,
                explanation=q.get("explanation", ""),
            )
        )
    return tuple(out)


# --------------------------------------------------------------------------
# Attempt storage. Kept tiny and side-effect-isolated: the only thing that
# touches the collection is get/set of one JSON config blob.
# --------------------------------------------------------------------------


def _normalize(raw: object) -> dict[str, dict[str, int]]:
    """Coerce a stored config blob into a clean ``{cid: {attempts, correct}}``.

    Tolerates missing/garbage shapes (returns what it can), so a hand-edited or
    older config can never crash the quiz or the dashboard.
    """
    out: dict[str, dict[str, int]] = {}
    if not isinstance(raw, dict):
        return out
    for cid, val in raw.items():
        if not isinstance(val, dict):
            continue
        attempts = int(val.get("attempts", 0) or 0)
        correct = int(val.get("correct", 0) or 0)
        # Clamp to a sane, self-consistent range.
        attempts = max(0, attempts)
        correct = max(0, min(correct, attempts))
        out[str(cid)] = {"attempts": attempts, "correct": correct}
    return out


def get_stats(col) -> dict[str, dict[str, int]]:
    """Read the stored per-concept attempt tallies from the collection."""
    return _normalize(col.get_config(CONFIG_KEY, default={}))


def record_attempt(col, concept_id: str, correct: bool) -> dict[str, dict[str, int]]:
    """Persist one graded attempt for ``concept_id`` and return the new tallies."""
    stats = get_stats(col)
    entry = stats.setdefault(concept_id, {"attempts": 0, "correct": 0})
    entry["attempts"] += 1
    if correct:
        entry["correct"] += 1
    col.set_config(CONFIG_KEY, stats)
    return stats


def reset_stats(col) -> None:
    """Clear all stored question attempts (used by the quiz's reset action)."""
    col.set_config(CONFIG_KEY, {})


def concept_question_stats(col) -> list[ConceptQuestionStat]:
    """Return per-concept aggregates as dataclasses (for display/inspection)."""
    return [
        ConceptQuestionStat(cid, v["attempts"], v["correct"])
        for cid, v in sorted(get_stats(col).items())
    ]


def question_stat_protos(col):
    """Build the ``ConceptQuestionStat`` protos to hand to the Rust RPC.

    Imported lazily so this module stays importable without a built backend
    (e.g. under plain pytest on the pure logic).
    """
    from anki import concepts_pb2

    return [
        concepts_pb2.ConceptQuestionStat(
            concept_id=cid, attempts=v["attempts"], correct=v["correct"]
        )
        for cid, v in sorted(get_stats(col).items())
    ]
