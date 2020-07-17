# coding: utf-8

import time

from anki.consts import *
from tests.shared import getEmptyCol as getEmptyColOrig


def getEmptyCol():
    col = getEmptyColOrig()
    col.changeSchedulerVer(2)
    return col


def test_op():
    col = getEmptyCol()
    # should have no undo by default
    assert not col.undoName()
    # let's adjust a study option
    col.save("studyopts")
    col.conf["abc"] = 5
    # it should be listed as undoable
    assert col.undoName() == "studyopts"
    # with about 5 minutes until it's clobbered
    assert time.time() - col._lastSave < 1
    # undoing should restore the old value
    col.undo()
    assert not col.undoName()
    assert "abc" not in col.conf
    # an (auto)save will clear the undo
    col.save("foo")
    assert col.undoName() == "foo"
    col.save()
    assert not col.undoName()
    # and a review will, too
    col.save("add")
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    col.reset()
    assert col.undoName() == "add"
    c = col.sched.getCard()
    col.sched.answerCard(c, 2)
    assert col.undoName() == "Review"


def test_review():
    col = getEmptyCol()
    col.conf["counts"] = COUNT_REMAINING
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    col.reset()
    assert not col.undoName()
    # answer
    assert col.sched.counts() == (1, 0, 0)
    c = col.sched.getCard()
    assert c.queue == QUEUE_TYPE_NEW
    col.sched.answerCard(c, 3)
    assert c.left == 1001
    assert col.sched.counts() == (0, 1, 0)
    assert c.queue == QUEUE_TYPE_LRN
    # undo
    assert col.undoName()
    col.undo()
    col.reset()
    assert col.sched.counts() == (1, 0, 0)
    c.load()
    assert c.queue == QUEUE_TYPE_NEW
    assert c.left != 1001
    assert not col.undoName()
    # we should be able to undo multiple answers too
    note = col.newNote()
    note["Front"] = "two"
    col.addNote(note)
    col.reset()
    assert col.sched.counts() == (2, 0, 0)
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    assert col.sched.counts() == (0, 2, 0)
    col.undo()
    col.reset()
    assert col.sched.counts() == (1, 1, 0)
    col.undo()
    col.reset()
    assert col.sched.counts() == (2, 0, 0)
    # performing a normal op will clear the review queue
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    assert col.undoName() == "Review"
    col.save("foo")
    assert col.undoName() == "foo"
    col.undo()
    assert not col.undoName()
