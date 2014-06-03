# coding: utf-8

import time
import copy

from tests.shared import  getEmptyCol
from anki.utils import  intTime
from anki.hooks import addHook


def test_clock():
    d = getEmptyCol()
    if (d.sched.dayCutoff - intTime()) < 10*60:
        raise Exception("Unit tests will fail around the day rollover.")

def checkRevIvl(d, c, targetIvl):
    min, max = d.sched._fuzzIvlRange(targetIvl)
    return min <= c.ivl <= max

def test_basics():
    d = getEmptyCol()
    d.reset()
    assert not d.sched.getCard()

def test_new():
    d = getEmptyCol()
    d.reset()
    assert d.sched.newCount == 0
    # add a note
    f = d.newNote()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addNote(f)
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
    assert c.type == 1
    assert c.due >= t

    # disabled for now, as the learn fudging makes this randomly fail
    # # the default order should ensure siblings are not seen together, and
    # # should show all cards
    # m = d.models.current(); mm = d.models
    # t = mm.newTemplate("Reverse")
    # t['qfmt'] = "{{Back}}"
    # t['afmt'] = "{{Front}}"
    # mm.addTemplate(m, t)
    # mm.save(m)
    # f = d.newNote()
    # f['Front'] = u"2"; f['Back'] = u"2"
    # d.addNote(f)
    # f = d.newNote()
    # f['Front'] = u"3"; f['Back'] = u"3"
    # d.addNote(f)
    # d.reset()
    # qs = ("2", "3", "2", "3")
    # for n in range(4):
    #     c = d.sched.getCard()
    #     assert qs[n] in c.q()
    #     d.sched.answerCard(c, 2)

def test_newLimits():
    d = getEmptyCol()
    # add some notes
    g2 = d.decks.id("Default::foo")
    for i in range(30):
        f = d.newNote()
        f['Front'] = str(i)
        if i > 4:
            f.model()['did'] = g2
        d.addNote(f)
    # give the child deck a different configuration
    c2 = d.decks.confId("new conf")
    d.decks.setConf(d.decks.get(g2), c2)
    d.reset()
    # both confs have defaulted to a limit of 20
    assert d.sched.newCount == 20
    # first card we get comes from parent
    c = d.sched.getCard()
    assert c.did == 1
    # limit the parent to 10 cards, meaning we get 10 in total
    conf1 = d.decks.confForDid(1)
    conf1['new']['perDay'] = 10
    d.reset()
    assert d.sched.newCount == 10
    # if we limit child to 4, we should get 9
    conf2 = d.decks.confForDid(g2)
    conf2['new']['perDay'] = 4
    d.reset()
    assert d.sched.newCount == 9

def test_newBoxes():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    d.reset()
    c = d.sched.getCard()
    d.sched._cardConf(c)['new']['delays'] = [1,2,3,4,5]
    d.sched.answerCard(c, 2)
    # should handle gracefully
    d.sched._cardConf(c)['new']['delays'] = [1]
    d.sched.answerCard(c, 2)

def test_learn():
    d = getEmptyCol()
    # add a note
    f = d.newNote()
    f['Front'] = u"one"; f['Back'] = u"two"
    f = d.addNote(f)
    # set as a learn card and rebuild queues
    d.db.execute("update cards set queue=0, type=0")
    d.reset()
    # sched.getCard should return it, since it's due in the past
    c = d.sched.getCard()
    assert c
    d.sched._cardConf(c)['new']['delays'] = [0.5, 3, 10]
    # fail it
    d.sched.answerCard(c, 1)
    # it should have three reps left to graduation
    assert c.left%1000 == 3
    assert c.left/1000 == 3
    # it should by due in 30 seconds
    t = round(c.due - time.time())
    assert t >= 25 and t <= 40
    # pass it once
    d.sched.answerCard(c, 2)
    # it should by due in 3 minutes
    assert round(c.due - time.time()) in (179, 180)
    assert c.left%1000 == 2
    assert c.left/1000 == 2
    # check log is accurate
    log = d.db.first("select * from revlog order by id desc")
    assert log[3] == 2
    assert log[4] == -180
    assert log[5] == -30
    # pass again
    d.sched.answerCard(c, 2)
    # it should by due in 10 minutes
    assert round(c.due - time.time()) in (599, 600)
    assert c.left%1000 == 1
    assert c.left/1000 == 1
    # the next pass should graduate the card
    assert c.queue == 1
    assert c.type == 1
    d.sched.answerCard(c, 2)
    assert c.queue == 2
    assert c.type == 2
    # should be due tomorrow, with an interval of 1
    assert c.due == d.sched.today+1
    assert c.ivl == 1
    # or normal removal
    c.type = 0
    c.queue = 1
    d.sched.answerCard(c, 3)
    assert c.type == 2
    assert c.queue == 2
    assert checkRevIvl(d, c, 4)
    # revlog should have been updated each time
    assert d.db.scalar("select count() from revlog where type = 0") == 5
    # now failed card handling
    c.type = 2
    c.queue = 1
    c.odue = 123
    d.sched.answerCard(c, 3)
    assert c.due == 123
    assert c.type == 2
    assert c.queue == 2
    # we should be able to remove manually, too
    c.type = 2
    c.queue = 1
    c.odue = 321
    c.flush()
    d.sched.removeLrn()
    c.load()
    assert c.queue == 2
    assert c.due == 321

def test_learn_collapsed():
    d = getEmptyCol()
    # add 2 notes
    f = d.newNote()
    f['Front'] = u"1"
    f = d.addNote(f)
    f = d.newNote()
    f['Front'] = u"2"
    f = d.addNote(f)
    # set as a learn card and rebuild queues
    d.db.execute("update cards set queue=0, type=0")
    d.reset()
    # should get '1' first
    c = d.sched.getCard()
    assert c.q().endswith("1")
    # pass it so it's due in 10 minutes
    d.sched.answerCard(c, 2)
    # get the other card
    c = d.sched.getCard()
    assert c.q().endswith("2")
    # fail it so it's due in 1 minute
    d.sched.answerCard(c, 1)
    # we shouldn't get the same card again
    c = d.sched.getCard()
    assert not c.q().endswith("2")

def test_learn_day():
    d = getEmptyCol()
    # add a note
    f = d.newNote()
    f['Front'] = u"one"
    f = d.addNote(f)
    d.sched.reset()
    c = d.sched.getCard()
    d.sched._cardConf(c)['new']['delays'] = [1, 10, 1440, 2880]
    # pass it
    d.sched.answerCard(c, 2)
    # two reps to graduate, 1 more today
    assert c.left%1000 == 3
    assert c.left/1000 == 1
    assert d.sched.counts() == (0, 1, 0)
    c = d.sched.getCard()
    ni = d.sched.nextIvl
    assert ni(c, 2) == 86400
    # answering it will place it in queue 3
    d.sched.answerCard(c, 2)
    assert c.due == d.sched.today+1
    assert c.queue == 3
    assert not d.sched.getCard()
    # for testing, move it back a day
    c.due -= 1
    c.flush()
    d.reset()
    assert d.sched.counts() == (0, 1, 0)
    c = d.sched.getCard()
    # nextIvl should work
    assert ni(c, 2) == 86400*2
    # if we fail it, it should be back in the correct queue
    d.sched.answerCard(c, 1)
    assert c.queue == 1
    d.undo()
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 2)
    # simulate the passing of another two days
    c.due -= 2
    c.flush()
    d.reset()
    # the last pass should graduate it into a review card
    assert ni(c, 2) == 86400
    d.sched.answerCard(c, 2)
    assert c.queue == c.type == 2
    # if the lapse step is tomorrow, failing it should handle the counts
    # correctly
    c.due = 0
    c.flush()
    d.reset()
    assert d.sched.counts() == (0, 0, 1)
    d.sched._cardConf(c)['lapse']['delays'] = [1440]
    c = d.sched.getCard()
    d.sched.answerCard(c, 1)
    assert c.queue == 3
    assert d.sched.counts() == (0, 0, 0)

def test_reviews():
    d = getEmptyCol()
    # add a note
    f = d.newNote()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addNote(f)
    # set the card up as a review card, due 8 days ago
    c = f.cards()[0]
    c.type = 2
    c.queue = 2
    c.due = d.sched.today - 8
    c.factor = 2500
    c.reps = 3
    c.lapses = 1
    c.ivl = 100
    c.startTimer()
    c.flush()
    # save it for later use as well
    cardcopy = copy.copy(c)
    # failing it should put it in the learn queue with the default options
    ##################################################
    # different delay to new
    d.reset()
    d.sched._cardConf(c)['lapse']['delays'] = [2, 20]
    d.sched.answerCard(c, 1)
    assert c.queue == 1
    # it should be due tomorrow, with an interval of 1
    assert c.odue == d.sched.today + 1
    assert c.ivl == 1
    # but because it's in the learn queue, its current due time should be in
    # the future
    assert c.due >= time.time()
    assert (c.due - time.time()) > 119
    # factor should have been decremented
    assert c.factor == 2300
    # check counters
    assert c.lapses == 2
    assert c.reps == 4
    # check ests.
    ni = d.sched.nextIvl
    assert ni(c, 1) == 120
    assert ni(c, 2) == 20*60
    # try again with an ease of 2 instead
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    d.sched.answerCard(c, 2)
    assert c.queue == 2
    # the new interval should be (100 + 8/4) * 1.2 = 122
    assert checkRevIvl(d, c, 122)
    assert c.due == d.sched.today + c.ivl
    # factor should have been decremented
    assert c.factor == 2350
    # check counters
    assert c.lapses == 1
    assert c.reps == 4
    # ease 3
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    d.sched.answerCard(c, 3)
    # the new interval should be (100 + 8/2) * 2.5 = 260
    assert checkRevIvl(d, c, 260)
    assert c.due == d.sched.today + c.ivl
    # factor should have been left alone
    assert c.factor == 2500
    # ease 4
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    d.sched.answerCard(c, 4)
    # the new interval should be (100 + 8) * 2.5 * 1.3 = 351
    assert checkRevIvl(d, c, 351)
    assert c.due == d.sched.today + c.ivl
    # factor should have been increased
    assert c.factor == 2650
    # leech handling
    ##################################################
    c = copy.copy(cardcopy)
    c.lapses = 7
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

def test_button_spacing():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    # 1 day ivl review card due now
    c = f.cards()[0]
    c.type = 2
    c.queue = 2
    c.due = d.sched.today
    c.reps = 1
    c.ivl = 1
    c.startTimer()
    c.flush()
    d.reset()
    ni = d.sched.nextIvlStr
    assert ni(c, 2) == "2 days"
    assert ni(c, 3) == "3 days"
    assert ni(c, 4) == "4 days"

def test_overdue_lapse():
    # disabled in commit 3069729776990980f34c25be66410e947e9d51a2
    return
    d = getEmptyCol()
    # add a note
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    # simulate a review that was lapsed and is now due for its normal review
    c = f.cards()[0]
    c.type = 2
    c.queue = 1
    c.due = -1
    c.odue = -1
    c.factor = 2500
    c.left = 2002
    c.ivl = 0
    c.flush()
    d.sched._clearOverdue = False
    # checkpoint
    d.save()
    d.sched.reset()
    assert d.sched.counts() == (0, 2, 0)
    c = d.sched.getCard()
    d.sched.answerCard(c, 3)
    # it should be due tomorrow
    assert c.due == d.sched.today + 1
    # revert to before
    d.rollback()
    d.sched._clearOverdue = True
    # with the default settings, the overdue card should be removed from the
    # learning queue
    d.sched.reset()
    assert d.sched.counts() == (0, 0, 1)

def test_finished():
    d = getEmptyCol()
    # nothing due
    assert "Congratulations" in d.sched.finishedMsg()
    assert "limit" not in d.sched.finishedMsg()
    f = d.newNote()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addNote(f)
    # have a new card
    assert "new cards available" in d.sched.finishedMsg()
    # turn it into a review
    d.reset()
    c = f.cards()[0]
    c.startTimer()
    d.sched.answerCard(c, 3)
    # nothing should be due tomorrow, as it's due in a week
    assert "Congratulations" in d.sched.finishedMsg()
    assert "limit" not in d.sched.finishedMsg()

def test_nextIvl():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addNote(f)
    d.reset()
    conf = d.decks.confForDid(1)
    conf['new']['delays'] = [0.5, 3, 10]
    conf['lapse']['delays'] = [1, 5, 9]
    c = d.sched.getCard()
    # new cards
    ##################################################
    ni = d.sched.nextIvl
    assert ni(c, 1) == 30
    assert ni(c, 2) == 180
    assert ni(c, 3) == 4*86400
    d.sched.answerCard(c, 1)
    # cards in learning
    ##################################################
    assert ni(c, 1) == 30
    assert ni(c, 2) == 180
    assert ni(c, 3) == 4*86400
    d.sched.answerCard(c, 2)
    assert ni(c, 1) == 30
    assert ni(c, 2) == 600
    assert ni(c, 3) == 4*86400
    d.sched.answerCard(c, 2)
    # normal graduation is tomorrow
    assert ni(c, 2) == 1*86400
    assert ni(c, 3) == 4*86400
    # lapsed cards
    ##################################################
    c.type = 2
    c.ivl = 100
    c.factor = 2500
    assert ni(c, 1) == 60
    assert ni(c, 2) == 100*86400
    assert ni(c, 3) == 100*86400
    # review cards
    ##################################################
    c.queue = 2
    c.ivl = 100
    c.factor = 2500
    # failing it should put it at 60s
    assert ni(c, 1) == 60
    # or 1 day if relearn is false
    d.sched._cardConf(c)['lapse']['delays']=[]
    assert ni(c, 1) == 1*86400
    # (* 100 1.2 86400)10368000.0
    assert ni(c, 2) == 10368000
    # (* 100 2.5 86400)21600000.0
    assert ni(c, 3) == 21600000
    # (* 100 2.5 1.3 86400)28080000.0
    assert ni(c, 4) == 28080000
    assert d.sched.nextIvlStr(c, 4) == "10.8 months"

def test_misc():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    c = f.cards()[0]
    # burying
    d.sched.buryNote(c.nid)
    d.reset()
    assert not d.sched.getCard()
    d.sched.unburyCards()
    d.reset()
    assert d.sched.getCard()

def test_suspend():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
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
    # should cope with rev cards being relearnt
    c.due = 0; c.ivl = 100; c.type = 2; c.queue = 2; c.flush()
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 1)
    assert c.due >= time.time()
    assert c.queue == 1
    assert c.type == 2
    d.sched.suspendCards([c.id])
    d.sched.unsuspendCards([c.id])
    c.load()
    assert c.queue == 2
    assert c.type == 2
    assert c.due == 1
    # should cope with cards in cram decks
    c.due = 1
    c.flush()
    cram = d.decks.newDyn("tmp")
    d.sched.rebuildDyn()
    c.load()
    assert c.due != 1
    assert c.did != 1
    d.sched.suspendCards([c.id])
    c.load()
    assert c.due == 1
    assert c.did == 1

def test_cram():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    c = f.cards()[0]
    c.ivl = 100
    c.type = c.queue = 2
    # due in 25 days, so it's been waiting 75 days
    c.due = d.sched.today + 25
    c.mod = 1
    c.factor = 2500
    c.startTimer()
    c.flush()
    d.reset()
    assert d.sched.counts() == (0,0,0)
    cardcopy = copy.copy(c)
    # create a dynamic deck and refresh it
    did = d.decks.newDyn("Cram")
    d.sched.rebuildDyn(did)
    d.reset()
    # should appear as new in the deck list
    assert sorted(d.sched.deckDueList())[0][4] == 1
    # and should appear in the counts
    assert d.sched.counts() == (1,0,0)
    # grab it and check estimates
    c = d.sched.getCard()
    assert d.sched.answerButtons(c) == 2
    assert d.sched.nextIvl(c, 1) == 600
    assert d.sched.nextIvl(c, 2) == 138*60*60*24
    cram = d.decks.get(did)
    cram['delays'] = [1, 10]
    assert d.sched.answerButtons(c) == 3
    assert d.sched.nextIvl(c, 1) == 60
    assert d.sched.nextIvl(c, 2) == 600
    assert d.sched.nextIvl(c, 3) == 138*60*60*24
    d.sched.answerCard(c, 2)
    # elapsed time was 75 days
    # factor = 2.5+1.2/2 = 1.85
    # int(75*1.85) = 138
    assert c.ivl == 138
    assert c.odue == 138
    assert c.queue == 1
    # should be logged as a cram rep
    assert d.db.scalar(
        "select type from revlog order by id desc limit 1") == 3
    # check ivls again
    assert d.sched.nextIvl(c, 1) == 60
    assert d.sched.nextIvl(c, 2) == 138*60*60*24
    assert d.sched.nextIvl(c, 3) == 138*60*60*24
    # when it graduates, due is updated
    c = d.sched.getCard()
    d.sched.answerCard(c, 2)
    assert c.ivl == 138
    assert c.due == 138
    assert c.queue == 2
    # and it will have moved back to the previous deck
    assert c.did == 1
    # cram the deck again
    d.sched.rebuildDyn(did)
    d.reset()
    c = d.sched.getCard()
    # check ivls again - passing should be idempotent
    assert d.sched.nextIvl(c, 1) == 60
    assert d.sched.nextIvl(c, 2) == 600
    assert d.sched.nextIvl(c, 3) == 138*60*60*24
    d.sched.answerCard(c, 2)
    assert c.ivl == 138
    assert c.odue == 138
    # fail
    d.sched.answerCard(c, 1)
    assert d.sched.nextIvl(c, 1) == 60
    assert d.sched.nextIvl(c, 2) == 600
    assert d.sched.nextIvl(c, 3) == 86400
    # delete the deck, returning the card mid-study
    d.decks.rem(d.decks.selected())
    assert len(d.sched.deckDueList()) == 1
    c.load()
    assert c.ivl == 1
    assert c.due == d.sched.today+1
    # make it due
    d.reset()
    assert d.sched.counts() == (0,0,0)
    c.due = -5
    c.ivl = 100
    c.flush()
    d.reset()
    assert d.sched.counts() == (0,0,1)
    # cram again
    did = d.decks.newDyn("Cram")
    d.sched.rebuildDyn(did)
    d.reset()
    assert d.sched.counts() == (0,0,1)
    c.load()
    assert d.sched.answerButtons(c) == 4
    # add a sibling so we can test minSpace, etc
    c2 = copy.deepcopy(c)
    c2.id = 123
    c2.ord = 1
    c2.due = 325
    c2.col = c.col
    c2.flush()
    # should be able to answer it
    c = d.sched.getCard()
    d.sched.answerCard(c, 4)
    # it should have been moved back to the original deck
    assert c.did == 1

def test_cram_rem():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    oldDue = f.cards()[0].due
    did = d.decks.newDyn("Cram")
    d.sched.rebuildDyn(did)
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 2)
    # answering the card will put it in the learning queue
    assert c.type == c.queue == 1
    assert c.due != oldDue
    # if we terminate cramming prematurely it should be set back to new
    d.sched.emptyDyn(did)
    c.load()
    assert c.type == c.queue == 0
    assert c.due == oldDue

def test_cram_resched():
    # add card
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    # cram deck
    did = d.decks.newDyn("Cram")
    cram = d.decks.get(did)
    cram['resched'] = False
    d.sched.rebuildDyn(did)
    d.reset()
    # graduate should return it to new
    c = d.sched.getCard()
    ni = d.sched.nextIvl
    assert ni(c, 1) == 60
    assert ni(c, 2) == 600
    assert ni(c, 3) == 0
    assert d.sched.nextIvlStr(c, 3) == "(end)"
    d.sched.answerCard(c, 3)
    assert c.queue == c.type == 0
    # undue reviews should also be unaffected
    c.ivl = 100
    c.type = c.queue = 2
    c.due = d.sched.today + 25
    c.factor = 2500
    c.flush()
    cardcopy = copy.copy(c)
    d.sched.rebuildDyn(did)
    d.reset()
    c = d.sched.getCard()
    assert ni(c, 1) == 600
    assert ni(c, 2) == 0
    assert ni(c, 3) == 0
    d.sched.answerCard(c, 2)
    assert c.ivl == 100
    assert c.due == d.sched.today + 25
    # check failure too
    c = cardcopy
    c.flush()
    d.sched.rebuildDyn(did)
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 1)
    d.sched.emptyDyn(did)
    c.load()
    assert c.ivl == 100
    assert c.due == d.sched.today + 25
    # fail+grad early
    c = cardcopy
    c.flush()
    d.sched.rebuildDyn(did)
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 1)
    d.sched.answerCard(c, 3)
    d.sched.emptyDyn(did)
    c.load()
    assert c.ivl == 100
    assert c.due == d.sched.today + 25
    # due cards - pass
    c = cardcopy
    c.due = -25
    c.flush()
    d.sched.rebuildDyn(did)
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 3)
    d.sched.emptyDyn(did)
    c.load()
    assert c.ivl == 100
    assert c.due == -25
    # fail
    c = cardcopy
    c.due = -25
    c.flush()
    d.sched.rebuildDyn(did)
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 1)
    d.sched.emptyDyn(did)
    c.load()
    assert c.ivl == 100
    assert c.due == -25
    # fail with normal grad
    c = cardcopy
    c.due = -25
    c.flush()
    d.sched.rebuildDyn(did)
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 1)
    d.sched.answerCard(c, 3)
    c.load()
    assert c.ivl == 100
    assert c.due == -25
    # lapsed card pulled into cram
    # d.sched._cardConf(c)['lapse']['mult']=0.5
    # d.sched.answerCard(c, 1)
    # d.sched.rebuildDyn(did)
    # d.reset()
    # c = d.sched.getCard()
    # d.sched.answerCard(c, 2)
    # print c.__dict__

def test_ordcycle():
    d = getEmptyCol()
    # add two more templates and set second active
    m = d.models.current(); mm = d.models
    t = mm.newTemplate("Reverse")
    t['qfmt'] = "{{Back}}"
    t['afmt'] = "{{Front}}"
    mm.addTemplate(m, t)
    t = mm.newTemplate("f2")
    t['qfmt'] = "{{Front}}"
    t['afmt'] = "{{Back}}"
    mm.addTemplate(m, t)
    mm.save(m)
    # create a new note; it should have 3 cards
    f = d.newNote()
    f['Front'] = "1"; f['Back'] = "1"
    d.addNote(f)
    assert d.cardCount() == 3
    d.reset()
    # ordinals should arrive in order
    assert d.sched.getCard().ord == 0
    assert d.sched.getCard().ord == 1
    assert d.sched.getCard().ord == 2

def test_counts_idx():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addNote(f)
    d.reset()
    assert d.sched.counts() == (1, 0, 0)
    c = d.sched.getCard()
    # counter's been decremented but idx indicates 1
    assert d.sched.counts() == (0, 0, 0)
    assert d.sched.countIdx(c) == 0
    # answer to move to learn queue
    d.sched.answerCard(c, 1)
    assert d.sched.counts() == (0, 2, 0)
    # fetching again will decrement the count
    c = d.sched.getCard()
    assert d.sched.counts() == (0, 0, 0)
    assert d.sched.countIdx(c) == 1
    # answering should add it back again
    d.sched.answerCard(c, 1)
    assert d.sched.counts() == (0, 2, 0)

def test_repCounts():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    d.reset()
    # lrnReps should be accurate on pass/fail
    assert d.sched.counts() == (1, 0, 0)
    d.sched.answerCard(d.sched.getCard(), 1)
    assert d.sched.counts() == (0, 2, 0)
    d.sched.answerCard(d.sched.getCard(), 1)
    assert d.sched.counts() == (0, 2, 0)
    d.sched.answerCard(d.sched.getCard(), 2)
    assert d.sched.counts() == (0, 1, 0)
    d.sched.answerCard(d.sched.getCard(), 1)
    assert d.sched.counts() == (0, 2, 0)
    d.sched.answerCard(d.sched.getCard(), 2)
    assert d.sched.counts() == (0, 1, 0)
    d.sched.answerCard(d.sched.getCard(), 2)
    assert d.sched.counts() == (0, 0, 0)
    f = d.newNote()
    f['Front'] = u"two"
    d.addNote(f)
    d.reset()
    # initial pass should be correct too
    d.sched.answerCard(d.sched.getCard(), 2)
    assert d.sched.counts() == (0, 1, 0)
    d.sched.answerCard(d.sched.getCard(), 1)
    assert d.sched.counts() == (0, 2, 0)
    d.sched.answerCard(d.sched.getCard(), 3)
    assert d.sched.counts() == (0, 0, 0)
    # immediate graduate should work
    f = d.newNote()
    f['Front'] = u"three"
    d.addNote(f)
    d.reset()
    d.sched.answerCard(d.sched.getCard(), 3)
    assert d.sched.counts() == (0, 0, 0)
    # and failing a review should too
    f = d.newNote()
    f['Front'] = u"three"
    d.addNote(f)
    c = f.cards()[0]
    c.type = 2
    c.queue = 2
    c.due = d.sched.today
    c.flush()
    d.reset()
    assert d.sched.counts() == (0, 0, 1)
    d.sched.answerCard(d.sched.getCard(), 1)
    assert d.sched.counts() == (0, 1, 0)

def test_timing():
    d = getEmptyCol()
    # add a few review cards, due today
    for i in range(5):
        f = d.newNote()
        f['Front'] = "num"+str(i)
        d.addNote(f)
        c = f.cards()[0]
        c.type = 2
        c.queue = 2
        c.due = 0
        c.flush()
    # fail the first one
    d.reset()
    c = d.sched.getCard()
    # set a a fail delay of 1 second so we don't have to wait
    d.sched._cardConf(c)['lapse']['delays'][0] = 1/60.0
    d.sched.answerCard(c, 1)
    # the next card should be another review
    c = d.sched.getCard()
    assert c.queue == 2
    # but if we wait for a second, the failed card should come back
    time.sleep(1)
    c = d.sched.getCard()
    assert c.queue == 1

def test_collapse():
    d = getEmptyCol()
    # add a note
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    d.reset()
    # test collapsing
    c = d.sched.getCard()
    d.sched.answerCard(c, 1)
    c = d.sched.getCard()
    d.sched.answerCard(c, 3)
    assert not d.sched.getCard()

def test_deckDue():
    d = getEmptyCol()
    # add a note with default deck
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    # and one that's a child
    f = d.newNote()
    f['Front'] = u"two"
    default1 = f.model()['did'] = d.decks.id("Default::1")
    d.addNote(f)
    # make it a review card
    c = f.cards()[0]
    c.queue = 2
    c.due = 0
    c.flush()
    # add one more with a new deck
    f = d.newNote()
    f['Front'] = u"two"
    foobar = f.model()['did'] = d.decks.id("foo::bar")
    d.addNote(f)
    # and one that's a sibling
    f = d.newNote()
    f['Front'] = u"three"
    foobaz = f.model()['did'] = d.decks.id("foo::baz")
    d.addNote(f)
    d.reset()
    assert len(d.decks.decks) == 5
    cnts = d.sched.deckDueList()
    assert cnts[0] == ["Default", 1, 0, 0, 1]
    assert cnts[1] == ["Default::1", default1, 1, 0, 0]
    assert cnts[2] == ["foo", d.decks.id("foo"), 0, 0, 0]
    assert cnts[3] == ["foo::bar", foobar, 0, 0, 1]
    assert cnts[4] == ["foo::baz", foobaz, 0, 0, 1]
    tree = d.sched.deckDueTree()
    assert tree[0][0] == "Default"
    # sum of child and parent
    assert tree[0][1] == 1
    assert tree[0][2] == 1
    assert tree[0][4] == 1
    # child count is just review
    assert tree[0][5][0][0] == "1"
    assert tree[0][5][0][1] == default1
    assert tree[0][5][0][2] == 1
    assert tree[0][5][0][4] == 0
    # code should not fail if a card has an invalid deck
    c.did = 12345; c.flush()
    d.sched.deckDueList()
    d.sched.deckDueTree()

def test_deckTree():
    d = getEmptyCol()
    d.decks.id("new::b::c")
    d.decks.id("new2")
    # new should not appear twice in tree
    names = [x[0] for x in d.sched.deckDueTree()]
    names.remove("new")
    assert "new" not in names

def test_deckFlow():
    d = getEmptyCol()
    # add a note with default deck
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    # and one that's a child
    f = d.newNote()
    f['Front'] = u"two"
    default1 = f.model()['did'] = d.decks.id("Default::2")
    d.addNote(f)
    # and another that's higher up
    f = d.newNote()
    f['Front'] = u"three"
    default1 = f.model()['did'] = d.decks.id("Default::1")
    d.addNote(f)
    # should get top level one first, then ::1, then ::2
    d.reset()
    assert d.sched.counts() == (3,0,0)
    for i in "one", "three", "two":
        c = d.sched.getCard()
        assert c.note()['Front'] == i
        d.sched.answerCard(c, 2)

def test_reorder():
    d = getEmptyCol()
    # add a note with default deck
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    f2 = d.newNote()
    f2['Front'] = u"two"
    d.addNote(f2)
    assert f2.cards()[0].due == 2
    found=False
    # 50/50 chance of being reordered
    for i in range(20):
        d.sched.randomizeCards(1)
        if f.cards()[0].due != f.id:
            found=True
            break
    assert found
    d.sched.orderCards(1)
    assert f.cards()[0].due == 1
    # shifting
    f3 = d.newNote()
    f3['Front'] = u"three"
    d.addNote(f3)
    f4 = d.newNote()
    f4['Front'] = u"four"
    d.addNote(f4)
    assert f.cards()[0].due == 1
    assert f2.cards()[0].due == 2
    assert f3.cards()[0].due == 3
    assert f4.cards()[0].due == 4
    d.sched.sortCards([
        f3.cards()[0].id, f4.cards()[0].id], start=1, shift=True)
    assert f.cards()[0].due == 3
    assert f2.cards()[0].due == 4
    assert f3.cards()[0].due == 1
    assert f4.cards()[0].due == 2

def test_forget():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    c = f.cards()[0]
    c.queue = 2; c.type = 2; c.ivl = 100; c.due = 0
    c.flush()
    d.reset()
    assert d.sched.counts() == (0, 0, 1)
    d.sched.forgetCards([c.id])
    d.reset()
    assert d.sched.counts() == (1, 0, 0)

def test_resched():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    c = f.cards()[0]
    d.sched.reschedCards([c.id], 0, 0)
    c.load()
    assert c.due == d.sched.today
    assert c.ivl == 1
    assert c.queue == c.type == 2
    d.sched.reschedCards([c.id], 1, 1)
    c.load()
    assert c.due == d.sched.today+1
    assert c.ivl == +1

def test_norelearn():
    d = getEmptyCol()
    # add a note
    f = d.newNote()
    f['Front'] = u"one"
    d.addNote(f)
    c = f.cards()[0]
    c.type = 2
    c.queue = 2
    c.due = 0
    c.factor = 2500
    c.reps = 3
    c.lapses = 1
    c.ivl = 100
    c.startTimer()
    c.flush()
    d.reset()
    d.sched.answerCard(c, 1)
    d.sched._cardConf(c)['lapse']['delays'] = []
    d.sched.answerCard(c, 1)

def test_failmult():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u"one"; f['Back'] = u"two"
    d.addNote(f)
    c = f.cards()[0]
    c.type = 2
    c.queue = 2
    c.ivl = 100
    c.due = d.sched.today - c.ivl
    c.factor = 2500
    c.reps = 3
    c.lapses = 1
    c.startTimer()
    c.flush()
    d.sched._cardConf(c)['lapse']['mult'] = 0.5
    c = d.sched.getCard()
    d.sched.answerCard(c, 1)
    assert c.ivl == 50
    d.sched.answerCard(c, 1)
    assert c.ivl == 25
