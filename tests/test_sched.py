# coding: utf-8

import time
from tests.shared import assertException, getDeck
from anki.stdmodels import BasicModel
#from anki.db import *

def getEmptyDeck():
    d = getDeck()
    d.addModel(BasicModel())
    d.db.commit()
    return d

def test_basics():
    d = getEmptyDeck()
    assert not d.getCard()

def test_new():
    d = getEmptyDeck()
    assert d.sched.newCount == 0
    # add a fact
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"two"
    f = d.addFact(f)
    d.db.flush()
    d.reset()
    assert d.sched.newCount == 1
    # fetch it
    c = d.getCard()
    assert c
    assert c.queue == 2
    assert c.type == 2
    # if we answer it, it should become a learn card
    d.answerCard(c, 1)
    assert c.queue == 0
    assert c.type == 2

def test_learn():
    d = getEmptyDeck()
    # add a fact
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"two"
    f = d.addFact(f)
    d.db.flush()
    # set as a learn card and rebuild queues
    d.db.statement("update cards set queue=0, type=2")
    d.reset()
    # getCard should return it, since it's due in the past
    c = d.getCard()
    assert c
    # it should have no cycles and a grade of 0
    assert c.grade == c.cycles == 0
    # fail it
    d.answerCard(c, 1)
    # it should by due in 30 seconds
    assert round(c.due - time.time()) == 30
    # and have 1 cycle, but still a zero grade
    assert c.grade == 0
    assert c.cycles == 1
    # pass it once
    d.answerCard(c, 2)
    # it should by due in 3 minutes
    assert round(c.due - time.time()) == 180
    # and it should be grade 1 now
    assert c.grade == 1
    assert c.cycles == 2
    # pass again
    d.answerCard(c, 2)
    # it should by due in 10 minutes
    assert round(c.due - time.time()) == 600
    # and it should be grade 1 now
    assert c.grade == 2
    assert c.cycles == 3
    # the next pass should graduate the card
    assert c.queue == 0
    assert c.type == 2
    d.answerCard(c, 2)
    assert c.queue == 1
    assert c.type == 1
    print "test intervals, check early removal, etc"


