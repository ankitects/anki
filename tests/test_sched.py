# coding: utf-8

import time, copy
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
    assert c.queue == 0
    assert c.type == 0
    # if we answer it, it should become a learn card
    t = intTime()
    d.sched.answerCard(c, 1)
    assert c.queue == 1
    assert c.type == 0
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
    d.db.execute("update cards set queue=0, type=0")
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
    assert c.queue == 1
    assert c.type == 0
    d.sched.answerCard(c, 2)
    assert c.queue == 2
    assert c.type == 2
    # should be due tomorrow, with an interval of 1
    assert c.due == d.sched.today+1
    assert c.ivl == 1
    # let's try early removal bonus
    c.type = 0
    c.queue = 1
    c.cycles = 0
    d.sched.answerCard(c, 3)
    assert c.type == 2
    assert c.ivl == 7
    # or normal removal
    c.type = 0
    c.queue = 1
    c.cycles = 1
    d.sched.answerCard(c, 3)
    assert c.type == 2
    assert c.queue == 2
    assert c.ivl == 4
    # revlog should have been updated each time
    d.db.scalar("select count() from revlog where type = 0") == 6
    # now failed card handling
    c.type = 2
    c.queue = 1
    c.edue = 123
    d.sched.answerCard(c, 3)
    assert c.due == 123
    assert c.type == 2
    assert c.queue == 2
    # we should be able to remove manually, too
    c.type = 2
    c.queue = 1
    c.edue = 321
    c.flush()
    d.sched.removeFailed()
    c.load()
    assert c.queue == 2
    assert c.due == 321

def test_reviews():
    d = getEmptyDeck()
    # add a fact
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addFact(f)
    # set the card up as a review card, due yesterday
    c = f.cards()[0]
    c.type = 2
    c.queue = 2
    c.due = d.sched.today - 8
    c.factor = 2500
    c.reps = 3
    c.streak = 2
    c.lapses = 1
    c.ivl = 100
    c.startTimer()
    c.flush()
    # save it for later use as well
    cardcopy = copy.copy(c)
    # failing it should put it in the learn queue with the default options
    ##################################################
    d.sched.answerCard(c, 1)
    assert c.queue == 1
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
    assert c.queue == 2
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

def test_finished():
    d = getEmptyDeck()
    # nothing due
    assert "No cards are due" in d.sched.finishedMsg()
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addFact(f)
    # have a new card
    assert "1 new" in d.sched.finishedMsg()
    # turn it into a review
    c = f.cards()[0]
    c.startTimer()
    d.sched.answerCard(c, 3)
    # nothing should be due tomorrow, as it's due in a week
    assert "No cards are due" in d.sched.finishedMsg()

def test_nextIvl():
    d = getEmptyDeck()
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addFact(f)
    c = f.cards()[0]
    # cards in learning
    ##################################################
    ni = d.sched.nextIvl
    assert ni(c, 1) == 30
    assert ni(c, 2) == 180
    # immediate removal is 7 days
    assert ni(c, 3) == 7*86400
    c.cycles = 1
    c.grade = 1
    assert ni(c, 1) == 30
    assert ni(c, 2) == 600
    # no first time bonus
    assert ni(c, 3) == 4*86400
    c.grade = 2
    # normal graduation is tomorrow
    assert ni(c, 2) == 1*86400
    assert ni(c, 3) == 4*86400
    # review cards
    ##################################################
    c.queue = 2
    c.ivl = 100
    c.factor = 2500
    # failing it puts it at tomorrow
    assert ni(c, 1) == 1*86400
    # (* 100 1.2 86400)10368000.0
    assert ni(c, 2) == 10368000
    # (* 100 2.5 86400)21600000.0
    assert ni(c, 3) == 21600000
    # (* 100 2.5 1.3 86400)28080000.0
    assert ni(c, 4) == 28080000
    assert d.sched.nextIvlStr(c, 4) == "10.8 months"

def test_misc():
    d = getEmptyDeck()
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addFact(f)
    c = f.cards()[0]
    # suspending
    d.reset()
    assert d.sched.getCard()
    d.sched.suspendCards([c.id])
    d.reset()
    assert not d.sched.getCard()
    # unsuspending
    d.sched.unsuspendCards([c.id])
    d.reset()
    assert d.sched.getCard()
    # burying
    d.sched.buryFact(c.fid)
    d.reset()
    assert not d.sched.getCard()
    d.sched.onClose()
    d.reset()
    assert d.sched.getCard()
    # counts
    assert d.sched.timeToday() == 0
    assert d.sched.repsToday() == 0
    c.timerStarted = time.time() - 10
    d.sched.answerCard(c, 2)
    assert d.sched.timeToday() > 0
    assert d.sched.repsToday() == 1

def test_cram():
    d = getEmptyDeck()
    f = d.newFact()
    f['Front'] = u"one"
    d.addFact(f)
    c = f.cards()[0]
    c.ivl = 100
    c.type = c.queue = 2
    # due in 25 days, so it's been waiting 75 days
    c.due = d.sched.today + 25
    c.mod = 1
    c.startTimer()
    c.flush()
    cardcopy = copy.copy(c)
    d.cramGroups([1])
    # first, test with initial intervals preserved
    conf = d.sched._lrnConf(c)
    conf['reset'] = False
    conf['resched'] = False
    assert d.sched.counts() == (1, 0, 0)
    c = d.sched.getCard()
    assert d.sched.counts() == (0, 0, 0)
    # check that estimates work
    assert d.sched.nextIvl(c, 1) == 30
    assert d.sched.nextIvl(c, 2) == 180
    assert d.sched.nextIvl(c, 3) == 86400*100
    # failing it should not reset ivl
    assert c.ivl == 100
    d.sched.answerCard(c, 1)
    assert c.ivl == 100
    # reset ivl for exit test, and pass card
    d.sched.answerCard(c, 2)
    delta = c.due - time.time()
    assert delta > 175 and delta <= 180
    # another two answers should reschedule it
    assert c.queue == 1
    d.sched.answerCard(c, 2)
    d.sched.answerCard(c, 2)
    assert c.queue == -3
    assert c.ivl == 100
    # and if the queue is reset, it shouldn't appear in the new queue again
    d.reset()
    assert d.sched.counts() == (0, 0, 0)
    # now try again with ivl rescheduling
    c = copy.copy(cardcopy)
    c.flush()
    d.cramGroups([1])
    conf = d.sched._lrnConf(c)
    conf['reset'] = False
    conf['resched'] = True
    # failures shouldn't matter
    d.sched.answerCard(c, 1)
    # graduating the card will keep the same interval, but shift the card
    # forward the number of days it had been waiting (75)
    assert d.sched.nextIvl(c, 3) == 75*86400
    d.sched.answerCard(c, 3)
    assert c.ivl == 100
    assert c.due == d.sched.today + 75
    # try with ivl reset
    c = copy.copy(cardcopy)
    c.flush()
    d.cramGroups([1])
    conf = d.sched._lrnConf(c)
    conf['reset'] = True
    conf['resched'] = True
    d.sched.answerCard(c, 1)
    assert d.sched.nextIvl(c, 3) == 1*86400
    d.sched.answerCard(c, 3)
    assert c.ivl == 1
    assert c.due == d.sched.today + 1

def test_cramLimits():
    d = getEmptyDeck()
    # create three cards, due tomorrow, the next, etc
    for i in range(3):
        f = d.newFact()
        f['Front'] = str(i)
        d.addFact(f)
        c = f.cards()[0]
        c.type = c.queue = 2
        c.due = d.sched.today + 1 + i
        c.flush()
    # the default cram should return all three
    d.cramGroups([1])
    assert d.sched.counts()[0] == 3
    # if we start from the day after tomorrow, it should be 2
    d.cramGroups([1], min=1)
    assert d.sched.counts()[0] == 2
    # or after 2 days
    d.cramGroups([1], min=2)
    assert d.sched.counts()[0] == 1
    # we may get nothing
    d.cramGroups([1], min=3)
    assert d.sched.counts()[0] == 0
    # tomorrow(0) + dayAfter(1) = 2
    d.cramGroups([1], max=1)
    assert d.sched.counts()[0] == 2
    # if max is tomorrow, we get only one
    d.cramGroups([1], max=0)
    assert d.sched.counts()[0] == 1
    # both should work
    d.cramGroups([1], min=0, max=0)
    assert d.sched.counts()[0] == 1
    d.cramGroups([1], min=1, max=1)
    assert d.sched.counts()[0] == 1
    d.cramGroups([1], min=0, max=1)
    assert d.sched.counts()[0] == 2
