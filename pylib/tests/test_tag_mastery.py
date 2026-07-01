# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time

from anki import stats_pb2
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


def test_tag_mastery_new_fields_on_fresh_collection():
    """AC5: on a fresh (no-review) collection, the 5 new honest-score fields
    are present, structurally correct, and honest about "no data yet"."""
    col = getEmptyCol()
    _add(col, "a", ["bio::cell"])
    _add(col, "b", ["bio::cell", "chem::acid"])
    _add(col, "c", [])  # untagged

    resp = col._backend.tag_mastery(group_depth=1, mastered_threshold=0.0, search="")

    # topics_total/topics_covered: 3 groups (bio, chem, (untagged)); none
    # covered since no card has FSRS memory state yet.
    assert resp.topics_total == 3
    assert resp.topics_covered == 0
    assert resp.topics_covered <= resp.topics_total

    # No graded reviews yet -> honest "no reviews yet" sentinel (0), never a
    # garbage/epoch value.
    assert resp.last_updated_secs == 0

    # No scored cards (overall_n == 0) -> INSUFFICIENT.
    assert resp.how_sure == stats_pb2.TagMasteryResponse.HowSure.INSUFFICIENT
    assert resp.how_sure in (0, 1, 2, 3)

    # next_topic is always a str; with nothing covered, falls back to the
    # largest untested group. "bio" has 2 cards vs. 1 each for the others.
    assert isinstance(resp.next_topic, str)
    assert resp.next_topic == "bio"


def test_tag_mastery_new_fields_after_real_review():
    """AC5: after a real review via the scheduler, last_updated_secs becomes
    a plausible "now-ish" timestamp, and the new fields stay structurally
    sound (covered <= total, valid enum, next_topic still a str)."""
    col = getEmptyCol()
    _add(col, "a", ["bio::cell"])
    _add(col, "b", ["chem::acid"])

    before = int(time.time())
    card = col.sched.getCard()
    col.sched.answerCard(card, 3)
    after = int(time.time())

    resp = col._backend.tag_mastery(group_depth=1, mastered_threshold=0.0, search="")

    assert resp.topics_covered <= resp.topics_total
    assert resp.topics_total == 2

    # A graded review just happened -> last_updated_secs must be > 0 and
    # within a wide but sane window around "now" (not epoch/garbage).
    assert resp.last_updated_secs > 0
    assert before - 5 <= resp.last_updated_secs <= after + 5

    # Enum still round-trips as a valid plain int even without checking
    # against the generated wrapper.
    assert resp.how_sure in (0, 1, 2, 3)
    assert isinstance(resp.next_topic, str)

    # Sanity: nothing about calling tag_mastery (a read-only RPC) corrupted
    # the collection. Kept to a single call to stay fast.
    (_, ok) = col.fix_integrity()
    assert ok is True
