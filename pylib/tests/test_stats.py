# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import tempfile

from anki import stats_pb2
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


def test_brainlift_score_snapshot_bridge():
    col = getEmptyCol()
    memory_note = col.newNote()
    memory_note["Front"] = "memory"
    memory_note.tags = ["mcat::biology", "mcat::chemistry"]
    col.addNote(memory_note)
    performance_note = col.newNote()
    performance_note["Front"] = "performance"
    performance_note.tags = [
        "mcat::biology",
        "mcat::chemistry",
        "brainlift::evidence::performance::0",
    ]
    col.addNote(performance_note)

    rows = []
    for index in range(10):
        rows.append(
            (
                1_700_000_000_000 + index,
                memory_note.cards()[0].id,
                3 if index < 8 else 1,
                1,
            )
        )
        rows.append(
            (
                1_700_000_100_000 + index,
                performance_note.cards()[0].id,
                3 if index < 9 else 1,
                1,
            )
        )
    col.db.executemany(
        """
        insert into revlog (id, cid, usn, ease, ivl, lastIvl, factor, time, type)
        values (?, ?, 0, ?, 1, 1, 2500, 1000, ?)
        """,
        rows,
    )

    snapshot = col.brainlift_score_snapshot(
        [("Biology", "mcat::biology"), ("Chemistry", "mcat::chemistry")]
    )

    assert (
        snapshot.memory.availability
        == stats_pb2.BrainliftEvidenceScore.Availability.AVAILABLE
    )
    assert snapshot.memory.rated_reviews == 10
    assert (
        snapshot.performance.availability
        == stats_pb2.BrainliftEvidenceScore.Availability.AVAILABLE
    )
    assert snapshot.performance.rated_reviews == 10
    assert (
        snapshot.readiness.availability
        == stats_pb2.BrainliftEvidenceScore.Availability.ABSTAINED
    )
    assert snapshot.readiness.coverage == 1.0
    assert snapshot.readiness.estimate == 0.0
    assert not snapshot.readiness.HasField("range")
    assert (
        snapshot.readiness.confidence
        == stats_pb2.BrainliftEvidenceScore.Confidence.NONE
    )
    assert snapshot.readiness.reasons == ["readiness_score_mapping_not_validated"]
    assert not snapshot.readiness_formula
    assert snapshot.thresholds.memory_min_reviews == 10


def test_graphs():
    dir = tempfile.gettempdir()
    col = getEmptyCol()
    g = col.stats()
    rep = g.report()
    with open(os.path.join(dir, "test.html"), "w", encoding="UTF-8") as note:
        note.write(rep)
