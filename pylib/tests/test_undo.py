# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time

from anki.consts import *
from tests.shared import getEmptyCol as getEmptyColOrig


def getEmptyCol():
    col = getEmptyColOrig()
    col.upgrade_to_v2_scheduler()
    return col


def test_op():
    col = getEmptyCol()
    # should have no undo by default
    assert not col.undo_status().undo
    # let's adjust a study option
    col.save("studyopts")
    col.conf["abc"] = 5
    # it should be listed as undoable
    assert col.undo_status().undo == "studyopts"
    # with about 5 minutes until it's clobbered
    assert time.time() - col._last_checkpoint_at < 1
    # undoing should restore the old value
    col.undo_legacy()
    assert not col.undo_status().undo
    assert "abc" not in col.conf
    # an (auto)save will clear the undo
    col.save("foo")
    assert col.undo_status().undo == "foo"
    col.save()
    assert not col.undo_status().undo
    # and a review will, too
    col.save("add")
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    col.reset()
    assert "add" in col.undo_status().undo.lower()
    c = col.sched.getCard()
    col.sched.answerCard(c, 2)
    assert col.undo_status().undo == "Review"


def test_review():
    col = getEmptyCol()
    col.conf["counts"] = COUNT_REMAINING
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    note = col.newNote()
    note["Front"] = "two"
    col.addNote(note)
    col.reset()
    # answer
    assert col.sched.counts() == (2, 0, 0)
    c = col.sched.getCard()
    assert c.queue == QUEUE_TYPE_NEW
    col.sched.answerCard(c, 3)
    assert c.left % 1000 == 1
    assert col.sched.counts() == (1, 1, 0)
    assert c.queue == QUEUE_TYPE_LRN
    # undo
    assert col.undo_status().undo
    col.undo_legacy()
    col.reset()
    assert col.sched.counts() == (2, 0, 0)
    c.load()
    assert c.queue == QUEUE_TYPE_NEW
    assert c.left % 1000 != 1
    assert not col.undo_status().undo
    # we should be able to undo multiple answers too
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    assert col.sched.counts() == (0, 2, 0)
    col.undo_legacy()
    col.reset()
    assert col.sched.counts() == (1, 1, 0)
    col.undo_legacy()
    col.reset()
    assert col.sched.counts() == (2, 0, 0)
    # performing a normal op will clear the review queue
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    assert col.undo_status().undo == "Review"
    col.save("foo")
    assert col.undo_status().undo == "foo"
    col.undo_legacy()
    assert not col.undo_status().undo
