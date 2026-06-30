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
from functools import lru_cache
from pathlib import Path

from anki import concepts_pb2
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


def fetch_mastery(col) -> tuple[list[ConceptMastery], int]:
    """Call the ConceptMastery RPC over the whole collection.

    Returns ``(rows, concepts_total)`` where ``concepts_total`` is the size of
    the taxonomy (denominator for topic coverage).
    """
    taxonomy, ids = load_taxonomy()
    entries = col._backend.concept_mastery(taxonomy=taxonomy, search="")
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
