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


def test_topic_mastery():
    col = getEmptyCol()

    def add(front, tags):
        note = col.newNote()
        note["Front"] = front
        note.tags = tags
        col.addNote(note)

    add("1", ["cfa::topic::ethics"])
    add("2", ["cfa::topic::ethics"])
    add("3", ["cfa::topic::fixed_income"])
    add("4", ["misc"])
    add("5", [])

    res = col.topic_mastery(topic_prefix="cfa::topic::")
    assert res.considered == 5
    # "misc" and the untagged note don't match the topic prefix
    assert res.untagged == 2
    topics = {t.topic: t for t in res.topics}
    assert set(topics) == {"ethics", "fixed_income"}
    assert topics["ethics"].total == 2
    assert topics["fixed_income"].total == 1
    # fresh cards have no FSRS memory state yet
    assert topics["ethics"].with_memory_state == 0
    assert topics["ethics"].mastered == 0

    # empty prefix falls back to the default cfa::topic:: prefix
    assert len(col.topic_mastery().topics) == 2

    # honest "give-up" rule: too little graded data -> the dashboard abstains
    graded = sum(t.with_memory_state for t in res.topics)
    assert graded < 300


def test_get_dashboard():
    col = getEmptyCol()

    def add(front, tags):
        note = col.newNote()
        note["Front"] = front
        note.tags = tags
        col.addNote(note)

    add("1", ["cfa::topic::ethics"])
    add("2", ["cfa::topic::ethics"])
    add("3", ["cfa::topic::fixed_income"])

    res = col.get_dashboard()
    assert res.considered == 3
    # per-tag rows keyed by tag; fresh cards have no FSRS memory state yet
    tags = {t.tag: t for t in res.tags}
    assert "cfa::topic::ethics" in tags
    assert tags["cfa::topic::ethics"].total == 2
    assert tags["cfa::topic::ethics"].studied == 0
    # no cards have been graded yet -> honest give-up rule abstains
    assert res.graded_reviews == 0

    # answering a card produces a graded review, recorded per-tag for the
    # accuracy signal the dashboard uses to discount Memory
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)  # Good
    res = col.get_dashboard()
    assert res.graded_reviews == 1
    assert sum(t.graded_reviews for t in res.tags) == 1
    assert sum(t.again_reviews for t in res.tags) == 0
    assert sum(t.hard_reviews for t in res.tags) == 0

    # a failed answer is captured as an "again"; a "hard" answer as a weak recall
    col.sched.answerCard(col.sched.getCard(), 1)  # Again
    col.sched.answerCard(col.sched.getCard(), 2)  # Hard
    res = col.get_dashboard()
    assert res.graded_reviews == 3
    assert sum(t.again_reviews for t in res.tags) == 1
    assert sum(t.hard_reviews for t in res.tags) == 1


def test_get_concept_graph():
    col = getEmptyCol()

    def add(front, tags):
        note = col.newNote()
        note["Front"] = front
        note.tags = tags
        col.addNote(note)

    # two notes share the "shared" tag -> a co-occurrence edge between clusters
    add("1", ["reading::a", "shared"])
    add("2", ["reading::b", "shared"])

    res = col.get_concept_graph()
    assert res.considered == 2
    labels = {n.label for n in res.nodes}
    assert {"reading::a", "reading::b", "shared"} <= labels
    # clusters that co-occur on a note are linked by an edge
    assert len(res.edges) >= 1


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
