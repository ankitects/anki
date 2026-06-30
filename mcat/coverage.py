#!/usr/bin/env python3
"""MCAT concept coverage map (FR-2).

Given a versioned concept taxonomy (mcat/taxonomy.json) and a set of
card->tags, compute how many cards map to each AAMC concept, and the
overall coverage % per concept and per MCAT section.

A concept is "covered" if >= 1 card maps to it. This coverage signal feeds
FR-5's give-up rule: the app abstains from a Memory score until
topic coverage >= 50% (and >= 200 graded reviews).

This module is importable (use the functions) AND runnable from the CLI:

    python mcat/coverage.py --cards mcat/fixtures/sample_cards.json \
        --taxonomy mcat/taxonomy.json --out mcat/fixtures/sample_coverage.json

Card sources accepted by load_cards():
  * JSON fixture: a list of {"cardId": <id>, "tags": ["...", ...]}.
  * Anki collection (.anki2) or package (.apkg): read read-only via stdlib
    sqlite3/zipfile, no Anki runtime required. Tags live on the `notes`
    table (space-separated `tags` column); we expand notes -> cards via the
    `cards` table. Full 35k-deck import is deferred (see README); this path
    exists so the same script works once the real deck is available.

No third-party dependencies; standard library only.
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import os
import sqlite3
import sys
import tempfile
import zipfile
from dataclasses import dataclass, field
from typing import Iterable


# --------------------------------------------------------------------------
# Data model
# --------------------------------------------------------------------------
@dataclass
class Concept:
    id: str
    name: str
    section: str
    foundational_concept: str
    topic_weight: float
    tag_patterns: list[str]


@dataclass
class Taxonomy:
    version: str
    concepts: list[Concept]

    @classmethod
    def from_dict(cls, data: dict) -> "Taxonomy":
        concepts = [
            Concept(
                id=c["id"],
                name=c["name"],
                section=c["section"],
                foundational_concept=c.get("foundational_concept", ""),
                topic_weight=float(c.get("topic_weight", 1.0)),
                tag_patterns=list(c.get("tag_patterns", [])),
            )
            for c in data["concepts"]
        ]
        return cls(version=data["version"], concepts=concepts)


@dataclass
class Card:
    card_id: object
    tags: list[str] = field(default_factory=list)


# --------------------------------------------------------------------------
# Loading
# --------------------------------------------------------------------------
def load_taxonomy(path: str) -> Taxonomy:
    with open(path, encoding="utf-8") as fh:
        return Taxonomy.from_dict(json.load(fh))


def load_cards(path: str) -> list[Card]:
    """Load cards from a JSON fixture, an .anki2 SQLite file, or an .apkg."""
    lower = path.lower()
    if lower.endswith(".json"):
        return _load_cards_json(path)
    if lower.endswith(".apkg"):
        return _load_cards_apkg(path)
    if lower.endswith(".anki2") or lower.endswith(".anki21"):
        return _load_cards_anki2(path)
    raise ValueError(f"Unsupported card source extension: {path!r}")


def _load_cards_json(path: str) -> list[Card]:
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    cards: list[Card] = []
    for row in data:
        cards.append(Card(card_id=row.get("cardId"), tags=list(row.get("tags", []))))
    return cards


def _load_cards_anki2(path: str) -> list[Card]:
    """Read cards + their note tags from an Anki SQLite collection, read-only.

    No Anki runtime needed. Anki stores tags as a space-separated string on
    the `notes.tags` column; each card references a note via `cards.nid`.
    """
    uri = f"file:{os.path.abspath(path)}?mode=ro"
    con = sqlite3.connect(uri, uri=True)
    try:
        rows = con.execute(
            "SELECT c.id, n.tags FROM cards c JOIN notes n ON c.nid = n.id"
        ).fetchall()
    finally:
        con.close()
    cards: list[Card] = []
    for card_id, tags in rows:
        cards.append(Card(card_id=card_id, tags=_split_anki_tags(tags)))
    return cards


def _load_cards_apkg(path: str) -> list[Card]:
    """Extract the embedded collection from an .apkg and read its cards."""
    with zipfile.ZipFile(path) as zf:
        # Newer packages use collection.anki21; older use collection.anki2.
        names = zf.namelist()
        inner = next(
            (n for n in ("collection.anki21", "collection.anki2") if n in names),
            None,
        )
        if inner is None:
            raise ValueError("No collection.anki2/.anki21 found inside .apkg")
        with tempfile.TemporaryDirectory() as tmp:
            extracted = zf.extract(inner, tmp)
            return _load_cards_anki2(extracted)


def _split_anki_tags(tags: str | None) -> list[str]:
    if not tags:
        return []
    return [t for t in tags.strip().split(" ") if t]


# --------------------------------------------------------------------------
# Matching
# --------------------------------------------------------------------------
def _normalize_pattern(pattern: str) -> str:
    """Treat patterns without any wildcard as a substring match."""
    if "*" in pattern or "?" in pattern or "[" in pattern:
        return pattern.lower()
    return f"*{pattern.lower()}*"


def tag_matches_concept(tag: str, concept: Concept) -> bool:
    """Case-insensitive glob/substring match of one tag against a concept."""
    tag_l = tag.lower()
    for pattern in concept.tag_patterns:
        if fnmatch.fnmatchcase(tag_l, _normalize_pattern(pattern)):
            return True
    return False


def card_concepts(card: Card, taxonomy: Taxonomy) -> list[str]:
    """Return the set of concept ids a card maps to (one card -> many)."""
    matched: list[str] = []
    for concept in taxonomy.concepts:
        if any(tag_matches_concept(tag, concept) for tag in card.tags):
            matched.append(concept.id)
    return matched


# --------------------------------------------------------------------------
# Coverage computation
# --------------------------------------------------------------------------
def compute_coverage(cards: Iterable[Card], taxonomy: Taxonomy) -> dict:
    """Compute the coverage report (per-concept, per-section, overall)."""
    cards = list(cards)
    concept_by_id = {c.id: c for c in taxonomy.concepts}
    counts: dict[str, int] = {c.id: 0 for c in taxonomy.concepts}

    mapped_cards = 0
    for card in cards:
        ids = card_concepts(card, taxonomy)
        if ids:
            mapped_cards += 1
        for cid in ids:
            counts[cid] += 1

    per_concept = []
    for c in taxonomy.concepts:
        per_concept.append(
            {
                "id": c.id,
                "name": c.name,
                "section": c.section,
                "foundational_concept": c.foundational_concept,
                "topic_weight": c.topic_weight,
                "card_count": counts[c.id],
                "covered": counts[c.id] >= 1,
            }
        )

    # Per-section rollup.
    sections: dict[str, dict] = {}
    for c in taxonomy.concepts:
        s = sections.setdefault(
            c.section, {"section": c.section, "total_concepts": 0, "covered_concepts": 0, "card_count": 0}
        )
        s["total_concepts"] += 1
        if counts[c.id] >= 1:
            s["covered_concepts"] += 1
        s["card_count"] += counts[c.id]
    per_section = []
    for s in sections.values():
        s["coverage_pct"] = _pct(s["covered_concepts"], s["total_concepts"])
        per_section.append(s)

    total = len(taxonomy.concepts)
    covered = sum(1 for c in taxonomy.concepts if counts[c.id] >= 1)

    # Weighted coverage: fraction of total topic_weight that is covered.
    weight_total = sum(c.topic_weight for c in taxonomy.concepts)
    weight_covered = sum(
        concept_by_id[cid].topic_weight for cid in counts if counts[cid] >= 1
    )

    return {
        "taxonomy_version": taxonomy.version,
        "total_cards": len(cards),
        "mapped_cards": mapped_cards,
        "unmapped_cards": len(cards) - mapped_cards,
        "overall": {
            "total_concepts": total,
            "covered_concepts": covered,
            "coverage_pct": _pct(covered, total),
            "weighted_coverage_pct": _pct(weight_covered, weight_total),
        },
        "per_section": per_section,
        "per_concept": per_concept,
        "uncovered_concepts": [c.id for c in taxonomy.concepts if counts[c.id] == 0],
    }


def _pct(num: float, den: float) -> float:
    if den == 0:
        return 0.0
    return round(100.0 * num / den, 2)


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------
def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Compute MCAT concept coverage map (FR-2).")
    parser.add_argument(
        "--cards",
        required=True,
        help="Path to card source: .json fixture, .anki2 collection, or .apkg.",
    )
    parser.add_argument(
        "--taxonomy",
        default=os.path.join(os.path.dirname(__file__), "taxonomy.json"),
        help="Path to taxonomy.json (default: alongside this script).",
    )
    parser.add_argument(
        "--out",
        default=None,
        help="Write the coverage report JSON here (default: stdout).",
    )
    args = parser.parse_args(argv)

    taxonomy = load_taxonomy(args.taxonomy)
    cards = load_cards(args.cards)
    report = compute_coverage(cards, taxonomy)

    text = json.dumps(report, indent=2)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(text + "\n")
        o = report["overall"]
        print(
            f"Wrote {args.out}: {o['covered_concepts']}/{o['total_concepts']} concepts "
            f"covered ({o['coverage_pct']}%), weighted {o['weighted_coverage_pct']}%, "
            f"from {report['total_cards']} cards."
        )
    else:
        print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
