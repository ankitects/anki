# coding: utf-8

import time
from tests.shared import assertException, getEmptyDeck
from anki.stdmodels import BasicModel
from anki.utils import stripHTML, intTime
from anki.hooks import addHook

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
    m.templates[1]['actv'] = True
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
    # should be due tomorrow, with an interval of 1
    assert c.due == d.sched.today+1
    assert c.ivl == 1
    # let's try early removal bonus
    c.type = 2
    c.queue = 0
    c.cycles = 0
    d.sched.answerCard(c, 3)
    assert c.type == 1
    assert c.ivl == 7
    # or normal removal
    c.type = 2
    c.queue = 0
    c.cycles = 1
    d.sched.answerCard(c, 3)
    assert c.type == 1
    assert c.ivl == 4
    # revlog should have been updated each time
    d.db.scalar("select count() from revlog where type = 0") == 6
    # now failed card handling
    c.type = 1
    c.queue = 0
    c.edue = 123
    d.sched.answerCard(c, 3)
    assert c.due == 123
    assert c.type == 1
    assert c.queue == 1
    # we should be able to remove manually, too
    c.type = 1
    c.queue = 0
    c.edue = 321
    c.flush()
    d.sched.removeFailed()
    c.load()
    assert c.queue == 1
    assert c.due == 321

def test_reviews():
    d = getEmptyDeck()
    # add a fact
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addFact(f)
    # set the card up as a review card, due yesterday
    c = f.cards()[0]
    c.type = 1
    c.queue = 1
    c.due = d.sched.today - 8
    c.factor = 2500
    c.reps = 3
    c.streak = 2
    c.lapses = 1
    c.ivl = 100
    c.startTimer()
    c.flush()
    # save it for later use as well
    import copy
    cardcopy = copy.copy(c)
    # failing it should put it in the learn queue with the default options
    ##################################################
    d.sched.answerCard(c, 1)
    assert c.queue == 0
    # it should be due tomorrow, with an interval of 1
    assert c.due == d.sched.today + 1
    assert c.ivl == 1
    # factor should have been decremented
    assert c.factor == 2300
    # check counters
    assert c.streak == 0
    assert c.lapses == 2
    assert c.reps == 4
    # try again with an ease of 2 instead
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    d.sched.answerCard(c, 2)
    # the new interval should be (100 + 8/4) * 1.2 = 122
    assert c.ivl == 122
    assert c.due == d.sched.today + 122
    # factor should have been decremented
    assert c.factor == 2350
    # check counters
    assert c.streak == 3
    assert c.lapses == 1
    assert c.reps == 4
    # ease 3
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    d.sched.answerCard(c, 3)
    # the new interval should be (100 + 8/2) * 2.5 = 260
    assert c.ivl == 260
    assert c.due == d.sched.today + 260
    # factor should have been left alone
    assert c.factor == 2500
    # ease 4
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    d.sched.answerCard(c, 4)
    # the new interval should be (100 + 8) * 2.5 * 1.3 = 351
    assert c.ivl == 351
    assert c.due == d.sched.today + 351
    # factor should have been increased
    assert c.factor == 2650
    # leech handling
    ##################################################
    c = copy.copy(cardcopy)
    c.lapses = 15
    c.flush()
    # steup hook
    hooked = []
    def onLeech(card):
        hooked.append(1)
    addHook("leech", onLeech)
    d.sched.answerCard(c, 1)
    assert hooked
    assert c.queue == -1
    c.load()
    assert c.queue == -1
