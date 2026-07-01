# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Integration bridge between the MCAT pieces (FR-2 taxonomy + FR-3 engine RPC).

This is the single place that knows how to:
  * load the FR-2 concept taxonomy (``mcat/taxonomy.json``) and turn it into the
    protobuf ``ConceptTaxonomy`` the Rust engine expects, and
  * call the FR-3 ``ConceptMastery`` RPC and map its rows into the pure
    :class:`~aqt.mcat.memory_score.ConceptMastery` dataclass the score uses.

Keeping it isolated means the score logic (``memory_score.py``) and the Qt
surface (``panel.py``) stay free of backend/taxonomy details and remain testable
with plain fixtures. Everything is deterministic -- no AI.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from anki import concepts_pb2
from aqt.mcat import questions
from aqt.mcat.memory_score import ConceptMastery


def _taxonomy_path() -> Path:
    """Locate ``mcat/taxonomy.json``.

    Honours the ``MCAT_TAXONOMY_PATH`` env var (used by a packaged build to
    point at a bundled copy); otherwise resolves it relative to the repo root
    (this file is ``<root>/qt/aqt/mcat/integration.py``).
    """
    override = os.environ.get("MCAT_TAXONOMY_PATH")
    if override:
        return Path(override)
    return Path(__file__).resolve().parents[3] / "mcat" / "taxonomy.json"


@lru_cache(maxsize=1)
def load_taxonomy() -> tuple[concepts_pb2.ConceptTaxonomy, tuple[str, ...]]:
    """Return ``(ConceptTaxonomy proto, tuple of concept ids)``.

    Raises ``FileNotFoundError`` if the taxonomy is missing so callers can fall
    back to a fixture rather than silently scoring against an empty taxonomy.
    """
    data = json.loads(_taxonomy_path().read_text(encoding="utf-8"))
    concepts = data.get("concepts", [])
    rules = [
        concepts_pb2.ConceptRule(
            id=c["id"],
            topic_weight=float(c.get("topic_weight", 1.0)),
            tag_patterns=list(c.get("tag_patterns", [])),
        )
        for c in concepts
    ]
    ids = tuple(c["id"] for c in concepts)
    return concepts_pb2.ConceptTaxonomy(rules=rules), ids


@lru_cache(maxsize=1)
def _taxonomy_meta() -> dict[str, tuple[str, str]]:
    """Map concept id -> (human name, section), for labelling the NTR diagram."""
    data = json.loads(_taxonomy_path().read_text(encoding="utf-8"))
    return {
        c["id"]: (c.get("name", c["id"]), c.get("section", ""))
        for c in data.get("concepts", [])
    }


@dataclass(frozen=True)
class NtrRow:
    """One concept's NTR breakdown -- the numbers the dashboard diagram renders.

    ``avg_recall`` and ``question_accuracy`` are the two inputs; ``ntr`` is the
    engine's blended output (``topic_weight * weakness``). Carrying all of them
    lets the panel *show its work* rather than just a bar.
    """

    concept_id: str
    name: str
    section: str
    topic_weight: float
    avg_recall: float
    cards_total: int
    questions_total: int
    questions_correct: int
    question_accuracy: float
    ntr: float


def _fetch_entries(col):
    """Single ConceptMastery RPC, with the student's question stats blended in.

    Returns ``(entries, concept_ids)``. Centralised so mastery and the NTR
    breakdown come from one scan and can never disagree.
    """
    taxonomy, ids = load_taxonomy()
    stats = questions.question_stat_protos(col)
    entries = col._backend.concept_mastery(
        taxonomy=taxonomy, search="", question_stats=stats
    )
    return entries, ids


def fetch_mastery(col) -> tuple[list[ConceptMastery], int]:
    """Call the ConceptMastery RPC over the whole collection.

    Returns ``(rows, concepts_total)`` where ``concepts_total`` is the size of
    the taxonomy (denominator for topic coverage). Question performance does not
    change these card-recall rows; it only moves NTR (see :func:`fetch_ntr`).
    """
    entries, ids = _fetch_entries(col)
    rows = [
        ConceptMastery(
            concept=e.concept_id,
            cards_total=e.cards_total,
            cards_mastered=e.cards_mastered,
            avg_recall=e.avg_recall,
        )
        for e in entries
    ]
    return rows, len(ids)


def fetch_ntr(col) -> list[NtrRow]:
    """Per-concept NTR breakdown, highest NTR first (the dashboard diagram).

    Reflects both card recall and practice-question performance, exactly as the
    Rust engine blends them. Concepts with no evidence at all are dropped so the
    diagram shows actionable rows, not a wall of max-NTR placeholders.
    """
    entries, _ids = _fetch_entries(col)
    meta = _taxonomy_meta()
    rows = [
        NtrRow(
            concept_id=e.concept_id,
            name=meta.get(e.concept_id, (e.concept_id, ""))[0],
            section=meta.get(e.concept_id, (e.concept_id, ""))[1],
            topic_weight=e.topic_weight,
            avg_recall=e.avg_recall,
            cards_total=e.cards_total,
            questions_total=e.questions_total,
            questions_correct=e.questions_correct,
            question_accuracy=e.question_accuracy,
            ntr=e.ntr,
        )
        for e in entries
        if e.cards_total > 0 or e.questions_total > 0
    ]
    rows.sort(key=lambda r: r.ntr, reverse=True)
    return rows


def coverage_pct_from_mastery(rows: list[ConceptMastery], concepts_total: int) -> float:
    """Topic coverage %: taxonomy concepts with >=1 mapped card / total concepts.

    Derived from the same RPC result as the score, so coverage and mastery can
    never disagree. Feeds FR-5's give-up rule.
    """
    if concepts_total <= 0:
        return 0.0
    covered = sum(1 for r in rows if r.cards_total > 0)
    return covered / concepts_total * 100.0


def fetch_graded_reviews(col) -> int:
    """Total count of graded reviews (revlog rows with a real grade)."""
    return int(col.db.scalar("select count() from revlog where ease > 0"))
