#!/usr/bin/env python3
# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Seed a ready-to-open demo collection for exercising NTR + the Memory score.

Unlike ``make_synthetic_deck.py`` (which builds a big, random performance
fixture), this builds a *small, controlled* collection whose numbers are
designed to make the dashboard interesting and legible:

* Cards are spread across a curated set of taxonomy concepts, tagged so the
  engine's ``tag_patterns`` actually match them.
* Each concept is assigned a recall *band* (strong / medium / weak / very weak)
  by choosing FSRS stability + last-review age, so per-concept NTR spans a wide,
  clearly-ordered range.
* Some strong-on-cards concepts are given *weak* ingested practice-test stats
  (and some weak-on-cards concepts *strong* stats), so you can watch ingested
  questions push NTR up or down independently of card recall -- the blend, made
  visible.
* Real ``revlog`` rows are written so ``graded_reviews`` clears FR-5's give-up
  threshold and the Memory score shows a number + range instead of abstaining.

Everything is deterministic (fixed seed); NO AI.

The output is a self-contained Anki *base folder* with a single "User 1"
profile, so it never touches your real collection. Launch the app against it:

    python mcat/seed_demo.py                     # writes mcat/fixtures/demo_base
    just run -- -b <printed absolute base path>  # opens straight into the demo

Then open Tools -> "MCAT: Prediction & Review Plan" to see the projected score,
Memory score, and per-concept NTR recommendations.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time

# The pure Memory-score logic lives beside the Qt panel; import it by path so
# this script needs only pylib (no Qt / no full aqt import).
_QT_MCAT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "qt", "aqt", "mcat"
)
sys.path.insert(0, _QT_MCAT)

_HERE = os.path.dirname(os.path.abspath(__file__))
_TAXONOMY = os.path.join(_HERE, "taxonomy.json")

# Recall bands: (label, stability_days, elapsed = k * stability). Higher k means
# more forgetting since the last review, hence lower retrievability -> higher
# NTR. Values chosen to spread retrievability across ~0.3..0.98.
BANDS = {
    "strong": (60.0, 0.1),
    "medium": (30.0, 4.0),
    "weak": (30.0, 15.0),
    "very weak": (20.0, 40.0),
}
BAND_ORDER = ["strong", "medium", "weak", "very weak"]

CARDS_PER_CONCEPT = 25
REVIEWED_FRACTION = 0.85  # fraction of a concept's cards that have memory state
REVLOG_ROWS_PER_CARD = 3  # graded reviews written per reviewed card


def _load_concepts() -> list[dict]:
    data = json.loads(open(_TAXONOMY, encoding="utf-8").read())
    return data.get("concepts", [])


def _match_tag(concept_id: str, pattern: str) -> str:
    """Build a single (space-free) tag guaranteed to match ``pattern`` under the
    engine's ``tag_matches_pattern`` rules.

    The engine treats a pattern *ending* in ``*`` as a literal prefix (it strips
    only the final ``*``; any leading/internal ``*`` stay literal), matching a
    tag that equals the prefix or starts with ``prefix::``. A pattern with an
    internal-but-not-trailing ``*`` uses ordered-fragment ``contains``; a pattern
    with no ``*`` matches exactly or by hierarchical prefix. We construct a tag
    for whichever branch ``pattern`` hits, so it matches deterministically.
    """
    if pattern.endswith("*"):
        prefix = pattern[:-1]
        while prefix.endswith("::"):
            prefix = prefix[:-2]
        if not prefix:  # degenerate "match everything" pattern
            return f"#AK_MCAT::demo::{concept_id}"
        return f"{prefix}::demo::{concept_id}"
    if "*" in pattern:
        # Ordered fragments must appear in sequence; concatenating them does that.
        return f"{pattern.replace('*', '')}::demo::{concept_id}"
    return pattern


def _curate(concepts: list[dict]) -> list[dict]:
    """Pick a subset spanning all sections, leaving some concepts uncovered.

    Returns concept dicts annotated with ``band`` and ``questions`` (attempts,
    correct) so the demo shows a clear NTR spread and the card/question blend.
    """
    selected: list[dict] = []
    for idx, c in enumerate(concepts):
        # Skip every 3rd concept so coverage stays realistic (< 100%) while
        # still clearing FR-5's >= 50% rule.
        if idx % 3 == 2:
            continue
        band = BAND_ORDER[len(selected) % len(BAND_ORDER)]

        # Contrast the two signals: give strong-card concepts weak questions and
        # weak-card concepts strong questions, so questions visibly move NTR the
        # opposite way from card recall. Leave the middle bands question-free.
        questions: tuple[int, int] | None = None
        if band == "strong":
            questions = (10, 2)  # 20% -> pushes NTR UP despite strong cards
        elif band == "weak":
            questions = (10, 9)  # 90% -> pulls NTR DOWN despite weak cards

        entry = dict(c)
        entry["band"] = band
        entry["questions"] = questions
        selected.append(entry)
    return selected


def generate(base: str, seed: int = 20260630) -> dict:
    import random

    from anki.cards_pb2 import FsrsMemoryState
    from anki.collection import Collection

    base = os.path.abspath(base)
    profile_dir = os.path.join(base, "User 1")
    os.makedirs(profile_dir, exist_ok=True)
    col_path = os.path.join(profile_dir, "collection.anki2")
    if os.path.exists(col_path):
        os.remove(col_path)
    # Remove any stale profile metadata so the app initialises the base fresh
    # and adopts the seeded "User 1" collection rather than a half-written prefs.
    stale = os.path.join(base, "prefs21.db")
    if os.path.exists(stale):
        os.remove(stale)

    rng = random.Random(seed)
    concepts = _curate(_load_concepts())

    col = Collection(col_path)
    basic = col.models.by_name("Basic")
    deck_id = col.decks.id("MCAT::Demo")
    now = int(time.time())

    reviewed_card_ids: list[int] = []
    per_concept: list[dict] = []

    for c in concepts:
        cid = c["id"]
        tag = _match_tag(cid, c["tag_patterns"][0])
        stability, k = BANDS[c["band"]]
        days_ago = int(stability * k)

        made = 0
        reviewed = 0
        for i in range(CARDS_PER_CONCEPT):
            note = col.new_note(basic)
            note["Front"] = f"[{cid}] demo card {i}: {c['name'][:60]}?"
            note["Back"] = f"Deterministic answer for {cid} card {i}."
            note.tags = [tag]
            col.add_note(note, deck_id)
            made += 1

            if rng.random() < REVIEWED_FRACTION:
                (card_id,) = col.card_ids_of_note(note.id)
                card = col.get_card(card_id)
                card.memory_state = FsrsMemoryState(
                    stability=stability, difficulty=round(rng.uniform(3.0, 8.0), 2)
                )
                card.last_review_time = now - days_ago * 86400
                card.ivl = max(1, int(stability))
                card.reps = REVLOG_ROWS_PER_CARD
                card.due = rng.randint(1, 365)
                col.update_card(card, skip_undo_entry=True)
                reviewed_card_ids.append(card_id)
                reviewed += 1

        per_concept.append(
            {"id": cid, "band": c["band"], "cards": made, "reviewed": reviewed}
        )

    # Write real revlog rows so graded_reviews clears the give-up threshold.
    graded = _write_revlog(col, reviewed_card_ids, now, rng)

    # Seed per-concept ingested practice-test stats (feeds NTR, not the Memory
    # score) -- as if the student had ingested a few practice tests.
    stats = {
        c["id"]: {"attempts": c["questions"][0], "correct": c["questions"][1]}
        for c in concepts
        if c["questions"] is not None
    }
    col.set_config("mcatPracticeTestStats", stats)

    report = _report(col, now)
    col.close()

    return {
        "base": base,
        "collection": col_path,
        "concepts_seeded": len(concepts),
        "cards": sum(p["cards"] for p in per_concept),
        "reviewed_cards": len(reviewed_card_ids),
        "graded_reviews": graded,
        "question_stats_concepts": len(stats),
        **report,
    }


def _write_revlog(col, card_ids: list[int], now: int, rng) -> int:
    """Insert ``REVLOG_ROWS_PER_CARD`` graded review rows per reviewed card.

    Direct inserts (not undoable) are fine for a throwaway demo. ``id`` is a
    unique millisecond timestamp; ``ease > 0`` is what the Memory score counts.
    """
    rows = []
    base_ms = now * 1000
    counter = 0
    for cid in card_ids:
        for _ in range(REVLOG_ROWS_PER_CARD):
            rid = base_ms + counter  # unique, monotonically increasing
            counter += 1
            ease = rng.randint(1, 4)
            ivl = rng.randint(1, 200)
            rows.append((rid, cid, 0, ease, ivl, ivl, 2500, 8000, 1))
    col.db.executemany(
        "insert into revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type)"
        " values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        rows,
    )
    return int(col.db.scalar("select count() from revlog where ease > 0"))


def _report(col, now: int) -> dict:
    """Run the real ConceptMastery RPC + Memory score so the printed numbers
    match exactly what the app will show."""
    import memory_score  # type: ignore[import-not-found]  # imported by path
    import readiness  # type: ignore[import-not-found]  # imported by path

    from anki import concepts_pb2

    concepts = _load_concepts()
    taxonomy = concepts_pb2.ConceptTaxonomy(
        rules=[
            concepts_pb2.ConceptRule(
                id=c["id"],
                topic_weight=float(c.get("topic_weight", 1.0)),
                tag_patterns=list(c.get("tag_patterns", [])),
            )
            for c in concepts
        ]
    )
    stats_cfg = col.get_config("mcatPracticeTestStats", default={})
    question_stats = [
        concepts_pb2.ConceptQuestionStat(
            concept_id=cid, attempts=v["attempts"], correct=v["correct"]
        )
        for cid, v in stats_cfg.items()
    ]

    entries = col._backend.concept_mastery(
        taxonomy=taxonomy, search="", question_stats=question_stats
    )
    covered = [e for e in entries if e.cards_total > 0]
    ntr_rows = sorted(
        (
            (e.concept_id, e.ntr, e.avg_recall, e.questions_total, e.question_accuracy)
            for e in covered
        ),
        key=lambda r: r[1],
        reverse=True,
    )

    mastery = [
        memory_score.ConceptMastery(
            concept=e.concept_id,
            cards_total=e.cards_total,
            cards_mastered=e.cards_mastered,
            avg_recall=e.avg_recall,
        )
        for e in entries
    ]
    graded = int(col.db.scalar("select count() from revlog where ease > 0"))
    coverage = len(covered) / len(concepts) * 100.0 if concepts else 0.0
    score = memory_score.compute_memory_score(
        mastery=mastery,
        graded_reviews=graded,
        topic_coverage_pct=coverage,
        concepts_total=len(concepts),
    )

    q_attempts = sum(e.questions_total for e in entries)
    q_correct = sum(e.questions_correct for e in entries)
    q_concepts = sum(1 for e in entries if e.questions_total > 0)
    best = (ntr_rows[0][0], ntr_rows[0][0]) if ntr_rows else None
    ready = readiness.compute_readiness(
        question_attempts=q_attempts,
        question_correct=q_correct,
        concepts_with_questions=q_concepts,
        topic_coverage_pct=coverage,
        concepts_total=len(concepts),
        best_next=best,
    )

    return {
        "coverage_pct": round(coverage, 1),
        "readiness_headline": ready.headline(),
        "readiness_confidence": ready.confidence,
        "memory_headline": score.headline(),
        "memory_how_sure": score.how_sure,
        "top_ntr": ntr_rows[:8],
        "low_ntr": ntr_rows[-5:],
    }


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Seed a demo collection for NTR + Memory.")
    ap.add_argument(
        "--base",
        default=os.path.join(_HERE, "fixtures", "demo_base"),
        help="Anki base folder to create (default mcat/fixtures/demo_base).",
    )
    ap.add_argument("--seed", type=int, default=20260630, help="RNG seed.")
    args = ap.parse_args(argv)

    stats = generate(args.base, args.seed)

    print("Demo collection seeded:\n")
    print(f"  base folder      : {stats['base']}")
    print(f"  collection       : {stats['collection']}")
    print(f"  concepts seeded  : {stats['concepts_seeded']}")
    print(f"  cards            : {stats['cards']}")
    print(f"  reviewed cards   : {stats['reviewed_cards']}")
    print(f"  graded reviews   : {stats['graded_reviews']}")
    print(f"  concepts w/ Qs   : {stats['question_stats_concepts']}")
    print(f"  topic coverage   : {stats['coverage_pct']}%")
    print(
        f"\n  Readiness        : {stats['readiness_headline']}  (confidence: {stats['readiness_confidence']})"
    )
    print(
        f"  Memory score     : {stats['memory_headline']}  (how sure: {stats['memory_how_sure']})"
    )

    print("\n  Highest NTR (reviewed soonest):")
    for cid, ntr, r, qn, qacc in stats["top_ntr"]:
        q = f"Q {qacc * 100:.0f}% x{qn}" if qn else "no Qs"
        print(f"    {cid:<4} NTR {ntr:5.2f}   recall {r * 100:4.0f}%   {q}")
    print("  Lowest NTR:")
    for cid, ntr, r, qn, qacc in stats["low_ntr"]:
        q = f"Q {qacc * 100:.0f}% x{qn}" if qn else "no Qs"
        print(f"    {cid:<4} NTR {ntr:5.2f}   recall {r * 100:4.0f}%   {q}")

    print("\nLaunch the app against this demo (opens a separate 'User 1' profile,")
    print("your real collection is untouched):\n")
    print("    just mcat-run-demo")
    print("\n  or, equivalently, set the base folder yourself:")
    print(f"    $env:ANKI_BASE = '{stats['base']}'; just run   # PowerShell")
    print(f"    ANKI_BASE='{stats['base']}' just run          # bash")
    print("\nThen open Tools -> 'MCAT: Prediction & Review Plan'.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
