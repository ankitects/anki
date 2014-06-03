# coding: utf-8

import time
from tests.shared import  getEmptyCol
from anki.consts import *

def test_op():
    d = getEmptyCol()
    # should have no undo by default
    assert not d.undoName()
    # let's adjust a study option
    d.save("studyopts")
    d.conf['abc'] = 5
    # it should be listed as undoable
    assert d.undoName() == "studyopts"
    # with about 5 minutes until it's clobbered
    assert time.time() - d._lastSave < 1
    # undoing should restore the old value
    d.undo()
    assert not d.undoName()
    assert 'abc' not in d.conf
    # an (auto)save will clear the undo
    d.save("foo")
    assert d.undoName() == "foo"
    d.save()
    assert not d.undoName()
    # and a review will, too
    d.save("add")
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    d.reset()
    assert d.undoName() == "add"
    c = d.sched.getCard()
    d.sched.answerCard(c, 2)
    assert d.undoName() == "Review"

def test_review():
    d = getEmptyCol()
    d.conf['counts'] = COUNT_REMAINING
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    d.reset()
    assert not d.undoName()
    # answer
    assert d.sched.counts() == (1, 0, 0)
    c = d.sched.getCard()
    assert c.queue == 0
    d.sched.answerCard(c, 2)
    assert c.left == 1001
    assert d.sched.counts() == (0, 1, 0)
    assert c.queue == 1
    # undo
    assert d.undoName()
    d.undo()
    d.reset()
    assert d.sched.counts() == (1, 0, 0)
    c.load()
    assert c.queue == 0
    assert c.left != 1001
    assert not d.undoName()
    # we should be able to undo multiple answers too
    f = d.newNote()
    f['Front'] = u"two"
    d.addNote(f)
    d.reset()
    assert d.sched.counts() == (2, 0, 0)
    c = d.sched.getCard()
    d.sched.answerCard(c, 2)
    c = d.sched.getCard()
    d.sched.answerCard(c, 2)
    assert d.sched.counts() == (0, 2, 0)
    d.undo()
    d.reset()
    assert d.sched.counts() == (1, 1, 0)
    d.undo()
    d.reset()
    assert d.sched.counts() == (2, 0, 0)
    # performing a normal op will clear the review queue
    c = d.sched.getCard()
    d.sched.answerCard(c, 2)
    assert d.undoName() == "Review"
    d.save("foo")
    assert d.undoName() == "foo"
    d.undo()
    assert not d.undoName()


