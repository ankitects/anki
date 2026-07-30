# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import tempfile

from anki.collection import CardStats
from tests.shared import getEmptyCol


def test_stats():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "foo"
    col.addNote(note)
    c = note.cards()[0]
    # card stats
    card_stats = col.card_stats_data(c.id)
    assert card_stats.note_id == note.id
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    col.sched.answerCard(c, 2)
    card_stats = col.card_stats_data(c.id)
    assert len(card_stats.revlog) == 2


def test_graphs_empty():
    col = getEmptyCol()
    assert col.stats().report()


def test_graphs():
    dir = tempfile.gettempdir()
    col = getEmptyCol()
    g = col.stats()
    rep = g.report()
    with open(os.path.join(dir, "test.html"), "w", encoding="UTF-8") as note:
        note.write(rep)
    return


def test_topic_mastery():
    """The Rust TopicMastery query, exercised from Python.

    Covers the two behaviours the dashboard depends on: cards aggregate into
    every tag they carry, and the engine withholds a recall estimate until it
    has enough graded reviews to support one.
    """
    col = getEmptyCol()

    bio_and_chem = col.newNote()
    bio_and_chem["Front"] = "shared"
    bio_and_chem.tags = ["MCAT::Bio", "MCAT::Chem"]
    col.addNote(bio_and_chem)

    untagged = col.newNote()
    untagged["Front"] = "untagged"
    col.addNote(untagged)

    resp = col._backend.topic_mastery(
        search="", topic_prefix="MCAT::", min_reviews_for_estimate=10
    )

    assert resp.total_card_count == 2
    assert resp.untagged_card_count == 1
    assert [t.name for t in resp.topics] == ["MCAT::Bio", "MCAT::Chem"]
    assert all(t.card_count == 1 for t in resp.topics)
    # No reviews yet, so no topic may report a recall estimate.
    assert all(not t.HasField("average_recall") for t in resp.topics)

    # Answer the card once; the estimate stays withheld below the threshold,
    # and appears once the threshold is lowered to match reality.
    card = col.sched.getCard()
    col.sched.answerCard(card, 3)

    still_withheld = col._backend.topic_mastery(
        search="", topic_prefix="MCAT::", min_reviews_for_estimate=10
    )
    assert still_withheld.topics[0].review_count == 1
    assert not still_withheld.topics[0].HasField("average_recall")

    reported = col._backend.topic_mastery(
        search="", topic_prefix="MCAT::", min_reviews_for_estimate=1
    )
    assert reported.topics[0].average_recall == 1.0
