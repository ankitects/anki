# coding: utf-8

import time
from tests.shared import assertException, getEmptyDeck
from anki.stdmodels import BasicModel
from anki.utils import stripHTML, intTime

def test_basics():
    d = getEmptyDeck()
    assert not d.sched.getCard()

def test_new():
    d = getEmptyDeck()
    assert d.sched.newCount == 0
    # add a fact
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addFact(f)
    d.reset()
    assert d.sched.newCount == 1
    # fetch it
    c = d.sched.getCard()
    assert c
    assert c.queue == 2
    assert c.type == 2
    # if we answer it, it should become a learn card
    t = intTime()
    d.sched.answerCard(c, 1)
    assert c.queue == 0
    assert c.type == 2
    assert c.due >= t
    # the default order should ensure siblings are not seen together, and
    # should show all cards
    m = d.currentModel()
    m.templates[1].active = True
    m.flush()
    f = d.newFact()
    f['Front'] = u"2"; f['Back'] = u"2"
    d.addFact(f)
    f = d.newFact()
    f['Front'] = u"3"; f['Back'] = u"3"
    d.addFact(f)
    d.reset()
    qs = ("2", "3", "2", "3")
    for n in range(4):
        c = d.sched.getCard()
        assert(stripHTML(c.q()) == qs[n])
        d.sched.answerCard(c, 2)

def test_learn():
    d = getEmptyDeck()
    # add a fact
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"two"
    f = d.addFact(f)
    # set as a learn card and rebuild queues
    d.db.execute("update cards set queue=0, type=2")
    d.reset()
    # sched.getCard should return it, since it's due in the past
    c = d.sched.getCard()
    assert c
    # it should have no cycles and a grade of 0
    assert c.grade == c.cycles == 0
    # fail it
    d.sched.answerCard(c, 1)
    # it should by due in 30 seconds
    assert round(c.due - time.time()) == 30
    # and have 1 cycle, but still a zero grade
    assert c.grade == 0
    assert c.cycles == 1
    # pass it once
    d.sched.answerCard(c, 2)
    # it should by due in 3 minutes
    assert round(c.due - time.time()) == 180
    # and it should be grade 1 now
    assert c.grade == 1
    assert c.cycles == 2
    # check log is accurate
    log = d.db.first("select * from revlog order by time desc")
    assert log[2] == 2
    assert log[3] == 2
    assert log[4] == 180
    assert log[5] == 30
    # pass again
    d.sched.answerCard(c, 2)
    # it should by due in 10 minutes
    assert round(c.due - time.time()) == 600
    # and it should be grade 1 now
    assert c.grade == 2
    assert c.cycles == 3
    # the next pass should graduate the card
    assert c.queue == 0
    assert c.type == 2
    d.sched.answerCard(c, 2)
    assert c.queue == 1
    assert c.type == 1
    print "test intervals, check early removal, etc"


