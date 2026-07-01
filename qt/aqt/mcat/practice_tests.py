# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Practice-test ingestion store -- the kernel's input signal.

This fork is a **kernel** for MCAT predictions and recommendations, not an
all-in-one study app. It does not ship its own question bank or quiz. Instead
the student **ingests their own past practice tests** (UWorld, AAMC, full
lengths, ...): for each question they annotate *which MCAT concept it tested*
and *whether they got it right or wrong*. Those per-concept tallies feed the
Rust engine's per-concept **Need-to-Review (NTR)** signal, which re-prioritises
Anki review toward the concepts the student is actually weak on -- exactly the
"do UWorld first, then add weak points to Anki" workflow the Brainlift cites,
made automatic.

What this touches, and what it deliberately does not:

* It **feeds NTR** (review prioritisation, the Rust ``ConceptMastery`` /
  ``ConceptAwareQueue`` RPCs). Missing a concept's questions raises that
  concept's NTR; getting them right lowers it -- the evidence-weighted blend
  the engine already implements.
* It deliberately does **not** feed the displayed **Memory** score, which stays
  a pure function of FSRS card recall. Keeping ingested-question performance out
  of Memory preserves the spec's no-score-blending rule.

Everything here is deterministic -- **no AI**. Ingested attempts are aggregated
per concept and persisted in the collection config, so they survive restarts and
can be handed to the Rust RPC each time the recommendations panel or the
concept-aware queue is computed.

This module has **no hard Qt dependency** so it can be unit-tested with plain
pytest. The Qt ingestion surface lives in :mod:`aqt.mcat.ingest`.
"""

from __future__ import annotations

import csv
import io
import json
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

# Collection-config key under which per-concept attempt tallies are stored. The
# value is ``{concept_id: {"attempts": int, "correct": int}}``.
CONFIG_KEY = "mcatPracticeTestStats"

# Strings accepted (case-insensitively) as "got it right" when parsing a CSV.
_TRUE_TOKENS = {"1", "true", "t", "yes", "y", "right", "correct", "c"}
_FALSE_TOKENS = {"0", "false", "f", "no", "n", "wrong", "incorrect", "x"}


@dataclass(frozen=True)
class ConceptChoice:
    """A concept the student can tag an ingested question against."""

    concept_id: str
    name: str
    section: str

    def label(self) -> str:
        return f"{self.concept_id} - {self.name}"


@dataclass(frozen=True)
class IngestedAttempt:
    """One annotated practice-test question, before it is aggregated.

    ``label`` is optional free text (e.g. a question number) kept only for the
    student's reference while annotating; only ``concept_id`` and ``correct``
    affect NTR.
    """

    concept_id: str
    correct: bool
    label: str = ""


@dataclass(frozen=True)
class ConceptAttemptStat:
    """Aggregated attempts/correct for one concept (mirrors the proto row)."""

    concept_id: str
    attempts: int
    correct: int

    @property
    def accuracy(self) -> float:
        return self.correct / self.attempts if self.attempts else 0.0


def _taxonomy_path() -> Path:
    """Locate ``mcat/taxonomy.json`` (honours ``MCAT_TAXONOMY_PATH``)."""
    override = os.environ.get("MCAT_TAXONOMY_PATH")
    if override:
        return Path(override)
    return Path(__file__).resolve().parents[3] / "mcat" / "taxonomy.json"


@lru_cache(maxsize=1)
def load_concept_choices() -> tuple[ConceptChoice, ...]:
    """Return the taxonomy concepts the student can tag a question against.

    Reads ``mcat/taxonomy.json`` directly (no protobuf/backend dependency) so
    the ingestion dropdown works in the app and the parser stays unit-testable.
    Raises ``FileNotFoundError`` if the taxonomy is missing so callers can fall
    back rather than silently offering an empty concept list.
    """
    data = json.loads(_taxonomy_path().read_text(encoding="utf-8"))
    return tuple(
        ConceptChoice(
            concept_id=c["id"],
            name=c.get("name", c["id"]),
            section=c.get("section", ""),
        )
        for c in data.get("concepts", [])
    )


def _coerce_correct(raw: str) -> bool | None:
    """Interpret a CSV correctness cell. Returns ``None`` if unrecognised."""
    token = raw.strip().lower()
    if token in _TRUE_TOKENS:
        return True
    if token in _FALSE_TOKENS:
        return False
    return None


def parse_practice_test_csv(text: str) -> tuple[list[IngestedAttempt], list[str]]:
    """Parse an uploaded practice-test CSV into annotated attempts.

    The kernel does not read PDFs (that would need AI, which is out of scope):
    the student exports or transcribes a past test into a simple spreadsheet and
    uploads it here. Recognised columns (case-insensitive, in any order):

    * ``concept`` / ``concept_id`` -- the MCAT concept id the question tested
      (must match a taxonomy id, e.g. ``1A``); **required**.
    * ``correct`` / ``result`` / ``right`` -- whether it was answered correctly
      (``1/0``, ``true/false``, ``right/wrong``, ``correct/incorrect``, ...);
      **required**.
    * ``question`` / ``label`` / ``id`` -- optional free-text reference.

    Returns ``(attempts, warnings)``. Rows that are missing a concept or have an
    unrecognised correctness value are skipped and reported in ``warnings`` --
    the parse never raises on bad data so one bad row can't lose the whole file.
    Concept ids are validated against the taxonomy when it is available.
    """
    attempts: list[IngestedAttempt] = []
    warnings: list[str] = []

    reader = csv.DictReader(io.StringIO(text))
    if reader.fieldnames is None:
        return attempts, ["The file is empty or has no header row."]

    # Map lower-cased header -> actual header so lookups are case-insensitive.
    headers = {(h or "").strip().lower(): h for h in reader.fieldnames}

    def pick(*names: str) -> str | None:
        for n in names:
            if n in headers:
                return headers[n]
        return None

    concept_col = pick("concept_id", "concept", "topic")
    correct_col = pick("correct", "result", "right", "score")
    label_col = pick("question", "label", "id", "q")

    if concept_col is None or correct_col is None:
        return attempts, [
            "CSV needs a concept column (concept / concept_id) and a correctness "
            "column (correct / result / right)."
        ]

    try:
        valid_ids = {c.concept_id for c in load_concept_choices()}
    except FileNotFoundError:
        valid_ids = set()

    for i, row in enumerate(reader, start=2):  # row 1 is the header
        cid = (row.get(concept_col) or "").strip()
        if not cid:
            warnings.append(f"Row {i}: no concept id -- skipped.")
            continue
        if valid_ids and cid not in valid_ids:
            warnings.append(f"Row {i}: unknown concept '{cid}' -- skipped.")
            continue
        correct = _coerce_correct(row.get(correct_col) or "")
        if correct is None:
            warnings.append(
                f"Row {i}: unrecognised result '{row.get(correct_col)}' -- skipped."
            )
            continue
        label = (row.get(label_col) or "").strip() if label_col else ""
        attempts.append(IngestedAttempt(concept_id=cid, correct=correct, label=label))

    if not attempts and not warnings:
        warnings.append("No data rows found.")
    return attempts, warnings


# --------------------------------------------------------------------------
# Attempt storage. Kept tiny and side-effect-isolated: the only thing that
# touches the collection is get/set of one JSON config blob.
# --------------------------------------------------------------------------


def _normalize(raw: object) -> dict[str, dict[str, int]]:
    """Coerce a stored config blob into a clean ``{cid: {attempts, correct}}``.

    Tolerates missing/garbage shapes (returns what it can), so a hand-edited or
    older config can never crash ingestion or the recommendations panel.
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
    return record_batch(col, [(concept_id, correct)])


def record_batch(col, attempts) -> dict[str, dict[str, int]]:
    """Persist many annotated attempts at once (one config write).

    ``attempts`` is an iterable of ``(concept_id, correct)`` pairs (or
    :class:`IngestedAttempt`). Used by the ingestion dialog after the student
    annotates a whole practice test.
    """
    stats = get_stats(col)
    for item in attempts:
        if isinstance(item, IngestedAttempt):
            concept_id, correct = item.concept_id, item.correct
        else:
            concept_id, correct = item
        entry = stats.setdefault(str(concept_id), {"attempts": 0, "correct": 0})
        entry["attempts"] += 1
        if correct:
            entry["correct"] += 1
    col.set_config(CONFIG_KEY, stats)
    return stats


def reset_stats(col) -> None:
    """Clear all stored ingested attempts (used by the ingestion reset action)."""
    col.set_config(CONFIG_KEY, {})


def concept_attempt_stats(col) -> list[ConceptAttemptStat]:
    """Return per-concept aggregates as dataclasses (for display/inspection)."""
    return [
        ConceptAttemptStat(cid, v["attempts"], v["correct"])
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
