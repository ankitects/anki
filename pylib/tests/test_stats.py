# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import tempfile

from tests.shared import getEmptyCol


def test_batch_card_details_and_memory_metrics():
    col = getEmptyCol()
    try:
        note = col.new_note(col.models.current())
        note["Front"] = "<b>猫</b>"
        note["Back"] = "line one\nline two"
        col.add_note(note, 1)
        cid = note.cards()[0].id
        metrics = col.card_memory_metrics([cid, 1, cid], include_retrievability=False)
        assert len(metrics) == 2
        assert metrics[0] == metrics[1]
        assert not metrics[0].HasField("memory_state")
        assert not metrics[0].HasField("fsrs_retrievability")
        assert not col.card_details([cid])[0].HasField("note_fields")
        assert col.card_details([cid], note_fields=[])[0].HasField("note_fields")
        details = col.card_details(
            [cid, 1, cid],
            include_memory_state=True,
            note_fields=["Back", "Front", "Missing"],
        )
        assert len(details) == 2
        assert details[0] == details[1]
        assert [(f.name, f.value, f.order) for f in details[0].note_fields.fields] == [
            ("Front", "<b>猫</b>", 0),
            ("Back", "line one\nline two", 1),
        ]
        assert details[0].HasField("metrics")
        assert not details[0].metrics.HasField("fsrs_retrievability")
    finally:
        col.close()


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
