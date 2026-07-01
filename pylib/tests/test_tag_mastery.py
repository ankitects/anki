# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from tests.shared import getEmptyCol


def _add(col, front, tags):
    note = col.newNote()
    note["Front"] = front
    note.tags = tags
    col.addNote(note)
    return note


def test_tag_mastery():
    col = getEmptyCol()
    _add(col, "a", ["bio::cell"])
    _add(col, "b", ["bio::cell", "chem::acid"])
    _add(col, "c", [])  # untagged

    # `TagMasteryRequest` is destructured into kwargs by codegen; the 8-field
    # response is returned whole (not collapsed to its `groups` field).
    resp = col._backend.tag_mastery(group_depth=1, mastered_threshold=0.0, search="")

    # Default threshold is echoed back.
    assert abs(resp.threshold_used - 0.9) < 1e-9

    groups = {g.tag: g for g in resp.groups}
    assert groups["bio"].total_cards == 2
    assert groups["chem"].total_cards == 1
    assert groups["(untagged)"].total_cards == 1

    # New cards carry no FSRS memory state -> honest abstention.
    assert groups["bio"].cards_with_state == 0
    assert resp.overall_n == 0
    assert resp.overall_mean_recall == 0.0

    # A real review shows up in the give-up counters.
    card = col.sched.getCard()
    col.sched.answerCard(card, 3)
    resp2 = col._backend.tag_mastery(group_depth=1, mastered_threshold=0.0, search="")
    assert resp2.total_graded_reviews >= 1
    assert resp2.topics_with_reviews >= 1
