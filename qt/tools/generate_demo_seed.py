# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""MCAT Speedrun fork: build the pre-seeded "In Progress" demo collection that
the desktop installer ships as its second user profile.

Imports the MileDown deck, enables FSRS, then stamps FSRS memory state
(stability/difficulty + a back-dated last review) plus a handful of revlog rows
onto roughly half the cards in each depth-2 topic. The stability/elapsed bands
vary per topic so the Topic Mastery dashboard shows a spread of per-topic memory
scores (weak -> strong) with the "Memory readiness" band populated out of the
box. The data is illustrative demo data, not a real study history.

Usage:
    PYTHONPATH=out/pylib ./out/pyenv/bin/python qt/tools/generate_demo_seed.py \
        [SOURCE.apkg] [OUT.anki2]

Defaults: SOURCE=MCAT_Milesdown.apkg (repo root),
          OUT=qt/installer/app/src/anki/seed/in_progress.anki2
The output is a derived artifact (gitignored, like the source deck); regenerate
it with this script before building the installer on a clean checkout.
"""

from __future__ import annotations

import os
import random
import shutil
import sys
import tempfile
import time
from collections import defaultdict

from anki.cards_pb2 import FsrsMemoryState
from anki.collection import (
    Collection,
    ImportAnkiPackageOptions,
    ImportAnkiPackageRequest,
)
from anki.decks import DeckId, UpdateDeckConfigs, UpdateDeckConfigsMode

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_SOURCE = os.path.join(REPO_ROOT, "MCAT_Milesdown.apkg")
DEFAULT_OUT = os.path.join(
    REPO_ROOT, "qt", "installer", "app", "src", "anki", "seed", "in_progress.anki2"
)

# Per-topic (stability_days_range, days_since_review_range). Ordered weak ->
# strong so consecutive topics render a visible spread of memory scores.
BANDS = [
    ((8, 25), (40, 110)),  # weak
    ((30, 70), (20, 55)),  # low-medium
    ((60, 120), (10, 35)),  # medium
    ((120, 220), (5, 25)),  # medium-strong
    ((200, 400), (1, 12)),  # strong
    ((25, 60), (25, 70)),  # low-medium
    ((90, 180), (8, 30)),  # medium-strong
]


def topic_of(note) -> str | None:
    """Depth-2 topic (e.g. MileDown::Behavioral) of a note, or None if untagged."""
    for tag in note.tags:
        if "::" in tag:
            return "::".join(tag.split("::")[:2])
    return None


def build_seed(source: str, out: str) -> None:
    """Build the seed in a temp dir, then copy out ONLY collection.anki2. The
    MileDown import writes ~2500 media files into the collection's media folder;
    we deliberately drop them (like the iOS deck) so the installer bundle stays
    small — the demo needs the card text + memory scores, not the images."""
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        work = os.path.join(tmp, "in_progress.anki2")
        _populate(source, work)
        if os.path.exists(out):
            os.remove(out)
        shutil.copyfile(work, out)  # .anki2 only; leave media behind
    _report(out)


def _populate(source: str, out: str) -> None:
    random.seed(42)  # reproducible seed data

    col = Collection(out)
    col.import_anki_package(
        ImportAnkiPackageRequest(
            package_path=source,
            options=ImportAnkiPackageOptions(with_scheduling=False, merge_notetypes=False),
        )
    )

    # Enable FSRS (the dashboard is FSRS-oriented).
    data = col.decks.get_deck_configs_for_update(DeckId(1))
    configs = [entry.config for entry in data.all_config]
    col.decks.update_deck_configs(
        UpdateDeckConfigs(
            target_deck_id=DeckId(1),
            configs=configs,
            removed_config_ids=[],
            mode=UpdateDeckConfigsMode.UPDATE_DECK_CONFIGS_MODE_NORMAL,
            card_state_customizer=data.card_state_customizer,
            limits=data.current_deck.limits,
            new_cards_ignore_review_limit=data.new_cards_ignore_review_limit,
            fsrs=True,
            apply_all_parent_limits=data.apply_all_parent_limits,
            fsrs_reschedule=False,
            fsrs_health_check=False,
        )
    )

    topic_cards: dict[str, list[int]] = defaultdict(list)
    for cid in col.find_cards(""):
        topic = topic_of(col.get_note(col.get_card(cid).nid))
        if topic:
            topic_cards[topic].append(cid)

    now = int(time.time())
    today = col.sched.today
    updated = []
    revlog = []
    rid = (now - 150 * 86400) * 1000  # unique, back-dated revlog ids (ms)

    for i, topic in enumerate(sorted(topic_cards)):
        (s_lo, s_hi), (d_lo, d_hi) = BANDS[i % len(BANDS)]
        cids = topic_cards[topic][:]
        random.shuffle(cids)
        count = max(10, len(cids) // 2)
        for cid in cids[:count]:
            card = col.get_card(cid)
            stability = random.uniform(s_lo, s_hi)
            difficulty = random.uniform(3, 8)
            days_ago = random.randint(d_lo, d_hi)
            # Leave card.decay unset so the engine's default FSRS decay applies.
            card.memory_state = FsrsMemoryState(stability=stability, difficulty=difficulty)
            card.type = 2  # review
            card.queue = 2
            card.ivl = max(1, int(stability))
            card.last_review_time = now - days_ago * 86400
            card.due = today + (int(stability) - days_ago)
            updated.append(card)
            for _ in range(random.randint(1, 3)):
                rid += 1000
                revlog.append(
                    (
                        rid,
                        cid,
                        random.choice([2, 3, 3, 4]),  # ease (>0 => counts as graded)
                        max(1, int(stability)),  # ivl
                        max(1, int(stability * 0.7)),  # lastIvl
                        random.randint(3000, 15000),  # time (ms)
                        1,  # type: review
                    )
                )

    col.update_cards(updated)
    col.db.executemany(
        "insert into revlog (id,cid,usn,ease,ivl,lastIvl,factor,time,type) "
        "values (?,?,-1,?,?,?,2000,?,?)",
        revlog,
    )
    col.close()  # commit to disk
    print(
        f"stamped memory state on {len(updated)} cards, "
        f"{len(revlog)} revlog rows across {len(topic_cards)} topics"
    )


def _report(out: str) -> None:
    """Print exactly what the dashboard will read (empty search = whole
    collection). Probes a temp copy so opening the collection doesn't create a
    stray media folder next to the shipped .anki2."""
    size_kb = os.path.getsize(out) // 1024
    with tempfile.TemporaryDirectory() as tmp:
        probe = os.path.join(tmp, "probe.anki2")
        shutil.copyfile(out, probe)
        col = Collection(probe)
        resp = col._backend.tag_mastery(group_depth=2, mastered_threshold=0.0, search="")
        print(f"seed: {size_kb} KB  ({out})")
        for group in sorted(resp.groups, key=lambda g: g.tag):
            print(
                f"  {group.tag:28s} cards={group.total_cards:4d} "
                f"scored={group.cards_with_state:4d} recall={group.average_recall * 100:5.1f}%"
            )
        print(
            f"readiness: enough_data={resp.enough_data} "
            f"graded_reviews={resp.total_graded_reviews} "
            f"topics_with_reviews={resp.topics_with_reviews} "
            f"overall_recall={resp.overall_mean_recall * 100:.1f}%"
        )
        col.close()


def main() -> None:
    source = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_SOURCE
    out = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_OUT
    if not os.path.exists(source):
        sys.exit(f"source deck not found: {source}")
    build_seed(source, out)


if __name__ == "__main__":
    main()
