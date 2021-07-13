# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import copy
import time

from anki.collection import Collection
from anki.consts import *
from anki.lang import without_unicode_isolation
from anki.utils import intTime
from tests.shared import getEmptyCol as getEmptyColOrig


def getEmptyCol() -> Collection:
    col = getEmptyColOrig()
    # only safe in test environment
    col.set_config("schedVer", 1)
    col._load_scheduler()
    return col


def test_clock():
    col = getEmptyCol()
    if (col.sched.dayCutoff - intTime()) < 10 * 60:
        raise Exception("Unit tests will fail around the day rollover.")


def checkRevIvl(col, c, targetIvl):
    min, max = col.sched._fuzzIvlRange(targetIvl)
    return min <= c.ivl <= max


def test_basics():
    col = getEmptyCol()
    col.reset()
    assert not col.sched.getCard()


def test_new():
    col = getEmptyCol()
    col.reset()
    assert col.sched.newCount == 0
    # add a note
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)
    col.reset()
    assert col.sched.newCount == 1
    # fetch it
    c = col.sched.getCard()
    assert c
    assert c.queue == QUEUE_TYPE_NEW
    assert c.type == CARD_TYPE_NEW
    # if we answer it, it should become a learn card
    t = intTime()
    col.sched.answerCard(c, 1)
    assert c.queue == QUEUE_TYPE_LRN
    assert c.type == CARD_TYPE_LRN
    assert c.due >= t

    # disabled for now, as the learn fudging makes this randomly fail
    # # the default order should ensure siblings are not seen together, and
    # # should show all cards
    # m = col.models.current(); mm = col.models
    # t = mm.new_template("Reverse")
    # t['qfmt'] = "{{Back}}"
    # t['afmt'] = "{{Front}}"
    # mm.add_template(m, t)
    # mm.save(m)
    # note = col.newNote()
    # note['Front'] = u"2"; note['Back'] = u"2"
    # col.addNote(note)
    # note = col.newNote()
    # note['Front'] = u"3"; note['Back'] = u"3"
    # col.addNote(note)
    # col.reset()
    # qs = ("2", "3", "2", "3")
    # for n in range(4):
    #     c = col.sched.getCard()
    #     assert qs[n] in c.question()
    #     col.sched.answerCard(c, 2)


def test_newLimits():
    col = getEmptyCol()
    # add some notes
    deck2 = col.decks.id("Default::foo")
    for i in range(30):
        note = col.newNote()
        note["Front"] = str(i)
        if i > 4:
            note.note_type()["did"] = deck2
        col.addNote(note)
    # give the child deck a different configuration
    c2 = col.decks.add_config_returning_id("new conf")
    col.decks.set_config_id_for_deck_dict(col.decks.get(deck2), c2)
    col.reset()
    # both confs have defaulted to a limit of 20
    assert col.sched.newCount == 20
    # first card we get comes from parent
    c = col.sched.getCard()
    assert c.did == 1
    # limit the parent to 10 cards, meaning we get 10 in total
    conf1 = col.decks.config_dict_for_deck_id(1)
    conf1["new"]["perDay"] = 10
    col.decks.save(conf1)
    col.reset()
    assert col.sched.newCount == 10
    # if we limit child to 4, we should get 9
    conf2 = col.decks.config_dict_for_deck_id(deck2)
    conf2["new"]["perDay"] = 4
    col.decks.save(conf2)
    col.reset()
    assert col.sched.newCount == 9


def test_newBoxes():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    col.reset()
    c = col.sched.getCard()
    conf = col.sched._cardConf(c)
    conf["new"]["delays"] = [1, 2, 3, 4, 5]
    col.decks.save(conf)
    col.sched.answerCard(c, 2)
    # should handle gracefully
    conf["new"]["delays"] = [1]
    col.decks.save(conf)
    col.sched.answerCard(c, 2)


def test_learn():
    col = getEmptyCol()
    # add a note
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)
    # set as a new card and rebuild queues
    col.db.execute(f"update cards set queue={QUEUE_TYPE_NEW}, type={CARD_TYPE_NEW}")
    col.reset()
    # sched.getCard should return it, since it's due in the past
    c = col.sched.getCard()
    assert c
    conf = col.sched._cardConf(c)
    conf["new"]["delays"] = [0.5, 3, 10]
    col.decks.save(conf)
    # fail it
    col.sched.answerCard(c, 1)
    # it should have three reps left to graduation
    assert c.left % 1000 == 3
    assert c.left // 1000 == 3
    # it should be due in 30 seconds
    t = round(c.due - time.time())
    assert t >= 25 and t <= 40
    # pass it once
    col.sched.answerCard(c, 2)
    # it should be due in 3 minutes
    assert round(c.due - time.time()) in (179, 180)
    assert c.left % 1000 == 2
    assert c.left // 1000 == 2
    # check log is accurate
    log = col.db.first("select * from revlog order by id desc")
    assert log[3] == 2
    assert log[4] == -180
    assert log[5] == -30
    # pass again
    col.sched.answerCard(c, 2)
    # it should be due in 10 minutes
    assert round(c.due - time.time()) in (599, 600)
    assert c.left % 1000 == 1
    assert c.left // 1000 == 1
    # the next pass should graduate the card
    assert c.queue == QUEUE_TYPE_LRN
    assert c.type == CARD_TYPE_LRN
    col.sched.answerCard(c, 2)
    assert c.queue == QUEUE_TYPE_REV
    assert c.type == CARD_TYPE_REV
    # should be due tomorrow, with an interval of 1
    assert c.due == col.sched.today + 1
    assert c.ivl == 1
    # or normal removal
    c.type = CARD_TYPE_NEW
    c.queue = QUEUE_TYPE_LRN
    col.sched.answerCard(c, 3)
    assert c.type == CARD_TYPE_REV
    assert c.queue == QUEUE_TYPE_REV
    assert checkRevIvl(col, c, 4)
    # revlog should have been updated each time
    assert col.db.scalar("select count() from revlog where type = 0") == 5
    # now failed card handling
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_LRN
    c.odue = 123
    col.sched.answerCard(c, 3)
    assert c.due == 123
    assert c.type == CARD_TYPE_REV
    assert c.queue == QUEUE_TYPE_REV
    # we should be able to remove manually, too
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_LRN
    c.odue = 321
    c.flush()
    col.sched.removeLrn()
    c.load()
    assert c.queue == QUEUE_TYPE_REV
    assert c.due == 321


def test_learn_collapsed():
    col = getEmptyCol()
    # add 2 notes
    note = col.newNote()
    note["Front"] = "1"
    col.addNote(note)
    note = col.newNote()
    note["Front"] = "2"
    col.addNote(note)
    # set as a new card and rebuild queues
    col.db.execute(f"update cards set queue={QUEUE_TYPE_NEW}, type={CARD_TYPE_NEW}")
    col.reset()
    # should get '1' first
    c = col.sched.getCard()
    assert c.question().endswith("1")
    # pass it so it's due in 10 minutes
    col.sched.answerCard(c, 2)
    # get the other card
    c = col.sched.getCard()
    assert c.question().endswith("2")
    # fail it so it's due in 1 minute
    col.sched.answerCard(c, 1)
    # we shouldn't get the same card again
    c = col.sched.getCard()
    assert not c.question().endswith("2")


def test_learn_day():
    col = getEmptyCol()
    # add a note
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    col.sched.reset()
    c = col.sched.getCard()
    conf = col.sched._cardConf(c)
    conf["new"]["delays"] = [1, 10, 1440, 2880]
    col.decks.save(conf)
    # pass it
    col.sched.answerCard(c, 2)
    # two reps to graduate, 1 more today
    assert c.left % 1000 == 3
    assert c.left // 1000 == 1
    assert col.sched.counts() == (0, 1, 0)
    c = col.sched.getCard()
    ni = col.sched.nextIvl
    assert ni(c, 2) == 86400
    # answering it will place it in queue 3
    col.sched.answerCard(c, 2)
    assert c.due == col.sched.today + 1
    assert c.queue == CARD_TYPE_RELEARNING
    assert not col.sched.getCard()
    # for testing, move it back a day
    c.due -= 1
    c.flush()
    col.reset()
    assert col.sched.counts() == (0, 1, 0)
    c = col.sched.getCard()
    # nextIvl should work
    assert ni(c, 2) == 86400 * 2
    # if we fail it, it should be back in the correct queue
    col.sched.answerCard(c, 1)
    assert c.queue == QUEUE_TYPE_LRN
    col.undo_legacy()
    col.reset()
    c = col.sched.getCard()
    col.sched.answerCard(c, 2)
    # simulate the passing of another two days
    c.due -= 2
    c.flush()
    col.reset()
    # the last pass should graduate it into a review card
    assert ni(c, 2) == 86400
    col.sched.answerCard(c, 2)
    assert c.queue == CARD_TYPE_REV and c.type == QUEUE_TYPE_REV
    # if the lapse step is tomorrow, failing it should handle the counts
    # correctly
    c.due = 0
    c.flush()
    col.reset()
    assert col.sched.counts() == (0, 0, 1)
    conf = col.sched._cardConf(c)
    conf["lapse"]["delays"] = [1440]
    col.decks.save(conf)
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    assert c.queue == CARD_TYPE_RELEARNING
    assert col.sched.counts() == (0, 0, 0)


def test_reviews():
    col = getEmptyCol()
    # add a note
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)
    # set the card up as a review card, due 8 days ago
    c = note.cards()[0]
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_REV
    c.due = col.sched.today - 8
    c.factor = STARTING_FACTOR
    c.reps = 3
    c.lapses = 1
    c.ivl = 100
    c.start_timer()
    c.flush()
    # save it for later use as well
    cardcopy = copy.copy(c)
    # failing it should put it in the learn queue with the default options
    ##################################################
    # different delay to new
    col.reset()
    conf = col.sched._cardConf(c)
    conf["lapse"]["delays"] = [2, 20]
    col.decks.save(conf)
    col.sched.answerCard(c, 1)
    assert c.queue == QUEUE_TYPE_LRN
    # it should be due tomorrow, with an interval of 1
    assert c.odue == col.sched.today + 1
    assert c.ivl == 1
    # but because it's in the learn queue, its current due time should be in
    # the future
    assert c.due >= time.time()
    assert (c.due - time.time()) > 118
    # factor should have been decremented
    assert c.factor == 2300
    # check counters
    assert c.lapses == 2
    assert c.reps == 4
    # check ests.
    ni = col.sched.nextIvl
    assert ni(c, 1) == 120
    assert ni(c, 2) == 20 * 60
    # try again with an ease of 2 instead
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    col.sched.answerCard(c, 2)
    assert c.queue == QUEUE_TYPE_REV
    # the new interval should be (100 + 8/4) * 1.2 = 122
    assert checkRevIvl(col, c, 122)
    assert c.due == col.sched.today + c.ivl
    # factor should have been decremented
    assert c.factor == 2350
    # check counters
    assert c.lapses == 1
    assert c.reps == 4
    # ease 3
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    col.sched.answerCard(c, 3)
    # the new interval should be (100 + 8/2) * 2.5 = 260
    assert checkRevIvl(col, c, 260)
    assert c.due == col.sched.today + c.ivl
    # factor should have been left alone
    assert c.factor == STARTING_FACTOR
    # ease 4
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    col.sched.answerCard(c, 4)
    # the new interval should be (100 + 8) * 2.5 * 1.3 = 351
    assert checkRevIvl(col, c, 351)
    assert c.due == col.sched.today + c.ivl
    # factor should have been increased
    assert c.factor == 2650


def test_button_spacing():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    # 1 day ivl review card due now
    c = note.cards()[0]
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_REV
    c.due = col.sched.today
    c.reps = 1
    c.ivl = 1
    c.start_timer()
    c.flush()
    col.reset()
    ni = col.sched.nextIvlStr
    wo = without_unicode_isolation
    assert wo(ni(c, 2)) == "2d"
    assert wo(ni(c, 3)) == "3d"
    assert wo(ni(c, 4)) == "4d"


def test_overdue_lapse():
    # disabled in commit 3069729776990980f34c25be66410e947e9d51a2
    return
    col = getEmptyCol()  # pylint: disable=unreachable
    # add a note
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    # simulate a review that was lapsed and is now due for its normal review
    c = note.cards()[0]
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_LRN
    c.due = -1
    c.odue = -1
    c.factor = STARTING_FACTOR
    c.left = 2002
    c.ivl = 0
    c.flush()
    # checkpoint
    col.save()
    col.sched.reset()
    assert col.sched.counts() == (0, 2, 0)
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    # it should be due tomorrow
    assert c.due == col.sched.today + 1
    # revert to before
    col.rollback()
    # with the default settings, the overdue card should be removed from the
    # learning queue
    col.sched.reset()
    assert col.sched.counts() == (0, 0, 1)


def test_nextIvl():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)
    col.reset()
    conf = col.decks.config_dict_for_deck_id(1)
    conf["new"]["delays"] = [0.5, 3, 10]
    conf["lapse"]["delays"] = [1, 5, 9]
    col.decks.save(conf)
    c = col.sched.getCard()
    # new cards
    ##################################################
    ni = col.sched.nextIvl
    assert ni(c, 1) == 30
    assert ni(c, 2) == 180
    assert ni(c, 3) == 4 * 86400
    col.sched.answerCard(c, 1)
    # cards in learning
    ##################################################
    assert ni(c, 1) == 30
    assert ni(c, 2) == 180
    assert ni(c, 3) == 4 * 86400
    col.sched.answerCard(c, 2)
    assert ni(c, 1) == 30
    assert ni(c, 2) == 600
    assert ni(c, 3) == 4 * 86400
    col.sched.answerCard(c, 2)
    # normal graduation is tomorrow
    assert ni(c, 2) == 1 * 86400
    assert ni(c, 3) == 4 * 86400
    # lapsed cards
    ##################################################
    c.type = CARD_TYPE_REV
    c.ivl = 100
    c.factor = STARTING_FACTOR
    assert ni(c, 1) == 60
    assert ni(c, 2) == 100 * 86400
    assert ni(c, 3) == 100 * 86400
    # review cards
    ##################################################
    c.queue = QUEUE_TYPE_REV
    c.ivl = 100
    c.factor = STARTING_FACTOR
    # failing it should put it at 60s
    assert ni(c, 1) == 60
    # or 1 day if relearn is false
    conf["lapse"]["delays"] = []
    col.decks.save(conf)
    assert ni(c, 1) == 1 * 86400
    # (* 100 1.2 86400)10368000.0
    assert ni(c, 2) == 10368000
    # (* 100 2.5 86400)21600000.0
    assert ni(c, 3) == 21600000
    # (* 100 2.5 1.3 86400)28080000.0
    assert ni(c, 4) == 28080000
    assert without_unicode_isolation(col.sched.nextIvlStr(c, 4)) == "10.8mo"


def test_misc():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    # burying
    col.sched.bury_notes([note.id])
    col.reset()
    assert not col.sched.getCard()
    col.sched.unbury_deck(deck_id=col.decks.get_current_id())
    col.reset()
    assert col.sched.getCard()


def test_suspend():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    # suspending
    col.reset()
    assert col.sched.getCard()
    col.sched.suspend_cards([c.id])
    col.reset()
    assert not col.sched.getCard()
    # unsuspending
    col.sched.unsuspend_cards([c.id])
    col.reset()
    assert col.sched.getCard()
    # should cope with rev cards being relearnt
    c.due = 0
    c.ivl = 100
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_REV
    c.flush()
    col.reset()
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    assert c.due >= time.time()
    assert c.queue == QUEUE_TYPE_LRN
    assert c.type == CARD_TYPE_REV
    col.sched.suspend_cards([c.id])
    col.sched.unsuspend_cards([c.id])
    c.load()
    assert c.queue == QUEUE_TYPE_REV
    assert c.type == CARD_TYPE_REV
    assert c.due == 1
    # should cope with cards in cram decks
    c.due = 1
    c.flush()
    did = col.decks.new_filtered("tmp")
    col.sched.rebuild_filtered_deck(did)
    c.load()
    assert c.due != 1
    assert c.did != 1
    col.sched.suspend_cards([c.id])
    c.load()
    assert c.due == 1
    assert c.did == 1


def test_cram():
    col = getEmptyCol()
    opt = col.models.by_name("Basic (and reversed card)")
    col.models.set_current(opt)
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    c.ivl = 100
    c.queue = CARD_TYPE_REV
    c.type = QUEUE_TYPE_REV
    # due in 25 days, so it's been waiting 75 days
    c.due = col.sched.today + 25
    c.mod = 1
    c.factor = STARTING_FACTOR
    c.start_timer()
    c.flush()
    col.reset()
    assert col.sched.counts() == (0, 0, 0)
    cardcopy = copy.copy(c)
    # create a dynamic deck and refresh it
    did = col.decks.new_filtered("Cram")
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    # should appear as new in the deck list
    assert sorted(col.sched.deck_due_tree().children)[0].new_count == 1
    # and should appear in the counts
    assert col.sched.counts() == (1, 0, 0)
    # grab it and check estimates
    c = col.sched.getCard()
    assert col.sched.answerButtons(c) == 2
    assert col.sched.nextIvl(c, 1) == 600
    assert col.sched.nextIvl(c, 2) == 138 * 60 * 60 * 24
    cram = col.decks.get(did)
    cram["delays"] = [1, 10]
    col.decks.save(cram)
    assert col.sched.answerButtons(c) == 3
    assert col.sched.nextIvl(c, 1) == 60
    assert col.sched.nextIvl(c, 2) == 600
    assert col.sched.nextIvl(c, 3) == 138 * 60 * 60 * 24
    col.sched.answerCard(c, 2)
    # elapsed time was 75 days
    # factor = 2.5+1.2/2 = 1.85
    # int(75*1.85) = 138
    assert c.ivl == 138
    assert c.odue == 138
    assert c.queue == QUEUE_TYPE_LRN
    # should be logged as a cram rep
    assert col.db.scalar("select type from revlog order by id desc limit 1") == 3
    # check ivls again
    assert col.sched.nextIvl(c, 1) == 60
    assert col.sched.nextIvl(c, 2) == 138 * 60 * 60 * 24
    assert col.sched.nextIvl(c, 3) == 138 * 60 * 60 * 24
    # when it graduates, due is updated
    c = col.sched.getCard()
    col.sched.answerCard(c, 2)
    assert c.ivl == 138
    assert c.due == 138
    assert c.queue == QUEUE_TYPE_REV
    # and it will have moved back to the previous deck
    assert c.did == 1
    # cram the deck again
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    c = col.sched.getCard()
    # check ivls again - passing should be idempotent
    assert col.sched.nextIvl(c, 1) == 60
    assert col.sched.nextIvl(c, 2) == 600
    assert col.sched.nextIvl(c, 3) == 138 * 60 * 60 * 24
    col.sched.answerCard(c, 2)
    assert c.ivl == 138
    assert c.odue == 138
    # fail
    col.sched.answerCard(c, 1)
    assert col.sched.nextIvl(c, 1) == 60
    assert col.sched.nextIvl(c, 2) == 600
    assert col.sched.nextIvl(c, 3) == 86400
    # delete the deck, returning the card mid-study
    col.decks.remove([col.decks.selected()])
    assert len(col.sched.deck_due_tree().children) == 1
    c.load()
    assert c.ivl == 1
    assert c.due == col.sched.today + 1
    # make it due
    col.reset()
    assert col.sched.counts() == (0, 0, 0)
    c.due = -5
    c.ivl = 100
    c.flush()
    col.reset()
    assert col.sched.counts() == (0, 0, 1)
    # cram again
    did = col.decks.new_filtered("Cram")
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    assert col.sched.counts() == (0, 0, 1)
    c.load()
    assert col.sched.answerButtons(c) == 4
    # add a sibling so we can test minSpace, etc
    note["Back"] = "foo"
    note.flush()
    c2 = note.cards()[1]
    c2.ord = 1
    c2.due = 325
    c2.flush()
    # should be able to answer it
    c = col.sched.getCard()
    col.sched.answerCard(c, 4)
    # it should have been moved back to the original deck
    assert c.did == 1


def test_cram_rem():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    oldDue = note.cards()[0].due
    did = col.decks.new_filtered("Cram")
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    c = col.sched.getCard()
    col.sched.answerCard(c, 2)
    # answering the card will put it in the learning queue
    assert c.type == CARD_TYPE_LRN and c.queue == QUEUE_TYPE_LRN
    assert c.due != oldDue
    # if we terminate cramming prematurely it should be set back to new
    col.sched.empty_filtered_deck(did)
    c.load()
    assert c.type == CARD_TYPE_NEW and c.queue == QUEUE_TYPE_NEW
    assert c.due == oldDue


def test_cram_resched():
    # add card
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    # cram deck
    did = col.decks.new_filtered("Cram")
    cram = col.decks.get(did)
    cram["resched"] = False
    col.decks.save(cram)
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    # graduate should return it to new
    c = col.sched.getCard()
    ni = col.sched.nextIvl
    assert ni(c, 1) == 60
    assert ni(c, 2) == 600
    assert ni(c, 3) == 0
    assert col.sched.nextIvlStr(c, 3) == "(end)"
    col.sched.answerCard(c, 3)
    assert c.type == CARD_TYPE_NEW and c.queue == QUEUE_TYPE_NEW
    # undue reviews should also be unaffected
    c.ivl = 100
    c.queue = CARD_TYPE_REV
    c.type = QUEUE_TYPE_REV
    c.due = col.sched.today + 25
    c.factor = STARTING_FACTOR
    c.flush()
    cardcopy = copy.copy(c)
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    c = col.sched.getCard()
    assert ni(c, 1) == 600
    assert ni(c, 2) == 0
    assert ni(c, 3) == 0
    col.sched.answerCard(c, 2)
    assert c.ivl == 100
    assert c.due == col.sched.today + 25
    # check failure too
    c = cardcopy
    c.flush()
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    col.sched.empty_filtered_deck(did)
    c.load()
    assert c.ivl == 100
    assert c.due == col.sched.today + 25
    # fail+grad early
    c = cardcopy
    c.flush()
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    col.sched.answerCard(c, 3)
    col.sched.empty_filtered_deck(did)
    c.load()
    assert c.ivl == 100
    assert c.due == col.sched.today + 25
    # due cards - pass
    c = cardcopy
    c.due = -25
    c.flush()
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    col.sched.empty_filtered_deck(did)
    c.load()
    assert c.ivl == 100
    assert c.due == -25
    # fail
    c = cardcopy
    c.due = -25
    c.flush()
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    col.sched.empty_filtered_deck(did)
    c.load()
    assert c.ivl == 100
    assert c.due == -25
    # fail with normal grad
    c = cardcopy
    c.due = -25
    c.flush()
    col.sched.rebuild_filtered_deck(did)
    col.reset()
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    col.sched.answerCard(c, 3)
    c.load()
    assert c.ivl == 100
    assert c.due == -25
    # lapsed card pulled into cram
    # col.sched._cardConf(c)['lapse']['mult']=0.5
    # col.sched.answerCard(c, 1)
    # col.sched.rebuild_filtered_deck(did)
    # col.reset()
    # c = col.sched.getCard()
    # col.sched.answerCard(c, 2)
    # print c.__dict__


def test_ordcycle():
    col = getEmptyCol()
    # add two more templates and set second active
    m = col.models.current()
    mm = col.models
    t = mm.new_template("Reverse")
    t["qfmt"] = "{{Back}}"
    t["afmt"] = "{{Front}}"
    mm.add_template(m, t)
    t = mm.new_template("f2")
    t["qfmt"] = "{{Front}}2"
    t["afmt"] = "{{Back}}"
    mm.add_template(m, t)
    mm.save(m)
    # create a new note; it should have 3 cards
    note = col.newNote()
    note["Front"] = "1"
    note["Back"] = "1"
    col.addNote(note)
    assert col.card_count() == 3
    col.reset()
    # ordinals should arrive in order
    assert col.sched.getCard().ord == 0
    assert col.sched.getCard().ord == 1
    assert col.sched.getCard().ord == 2


def test_counts_idx():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)
    col.reset()
    assert col.sched.counts() == (1, 0, 0)
    c = col.sched.getCard()
    # counter's been decremented but idx indicates 1
    assert col.sched.counts() == (0, 0, 0)
    assert col.sched.countIdx(c) == 0
    # answer to move to learn queue
    col.sched.answerCard(c, 1)
    assert col.sched.counts() == (0, 2, 0)
    # fetching again will decrement the count
    c = col.sched.getCard()
    assert col.sched.counts() == (0, 0, 0)
    assert col.sched.countIdx(c) == 1
    # answering should add it back again
    col.sched.answerCard(c, 1)
    assert col.sched.counts() == (0, 2, 0)


def test_repCounts():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    col.reset()
    # lrnReps should be accurate on pass/fail
    assert col.sched.counts() == (1, 0, 0)
    col.sched.answerCard(col.sched.getCard(), 1)
    assert col.sched.counts() == (0, 2, 0)
    col.sched.answerCard(col.sched.getCard(), 1)
    assert col.sched.counts() == (0, 2, 0)
    col.sched.answerCard(col.sched.getCard(), 2)
    assert col.sched.counts() == (0, 1, 0)
    col.sched.answerCard(col.sched.getCard(), 1)
    assert col.sched.counts() == (0, 2, 0)
    col.sched.answerCard(col.sched.getCard(), 2)
    assert col.sched.counts() == (0, 1, 0)
    col.sched.answerCard(col.sched.getCard(), 2)
    assert col.sched.counts() == (0, 0, 0)
    note = col.newNote()
    note["Front"] = "two"
    col.addNote(note)
    col.reset()
    # initial pass should be correct too
    col.sched.answerCard(col.sched.getCard(), 2)
    assert col.sched.counts() == (0, 1, 0)
    col.sched.answerCard(col.sched.getCard(), 1)
    assert col.sched.counts() == (0, 2, 0)
    col.sched.answerCard(col.sched.getCard(), 3)
    assert col.sched.counts() == (0, 0, 0)
    # immediate graduate should work
    note = col.newNote()
    note["Front"] = "three"
    col.addNote(note)
    col.reset()
    col.sched.answerCard(col.sched.getCard(), 3)
    assert col.sched.counts() == (0, 0, 0)
    # and failing a review should too
    note = col.newNote()
    note["Front"] = "three"
    col.addNote(note)
    c = note.cards()[0]
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_REV
    c.due = col.sched.today
    c.flush()
    col.reset()
    assert col.sched.counts() == (0, 0, 1)
    col.sched.answerCard(col.sched.getCard(), 1)
    assert col.sched.counts() == (0, 1, 0)


def test_timing():
    col = getEmptyCol()
    # add a few review cards, due today
    for i in range(5):
        note = col.newNote()
        note["Front"] = "num" + str(i)
        col.addNote(note)
        c = note.cards()[0]
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_REV
        c.due = 0
        c.flush()
    # fail the first one
    col.reset()
    c = col.sched.getCard()
    # set a a fail delay of 4 seconds
    conf = col.sched._cardConf(c)
    conf["lapse"]["delays"][0] = 1 / 15.0
    col.decks.save(conf)
    col.sched.answerCard(c, 1)
    # the next card should be another review
    c = col.sched.getCard()
    assert c.queue == QUEUE_TYPE_REV
    # but if we wait for a few seconds, the failed card should come back
    orig_time = time.time

    def adjusted_time():
        return orig_time() + 5

    time.time = adjusted_time
    c = col.sched.getCard()
    assert c.queue == QUEUE_TYPE_LRN
    time.time = orig_time


def test_collapse():
    col = getEmptyCol()
    # add a note
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    col.reset()
    # test collapsing
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    assert not col.sched.getCard()


def test_deckDue():
    col = getEmptyCol()
    # add a note with default deck
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    # and one that's a child
    note = col.newNote()
    note["Front"] = "two"
    default1 = note.note_type()["did"] = col.decks.id("Default::1")
    col.addNote(note)
    # make it a review card
    c = note.cards()[0]
    c.queue = QUEUE_TYPE_REV
    c.due = 0
    c.flush()
    # add one more with a new deck
    note = col.newNote()
    note["Front"] = "two"
    note.note_type()["did"] = col.decks.id("foo::bar")
    col.addNote(note)
    # and one that's a sibling
    note = col.newNote()
    note["Front"] = "three"
    note.note_type()["did"] = col.decks.id("foo::baz")
    col.addNote(note)
    col.reset()
    assert len(col.decks.all_names_and_ids()) == 5
    tree = col.sched.deck_due_tree().children
    assert tree[0].name == "Default"
    # sum of child and parent
    assert tree[0].deck_id == 1
    assert tree[0].review_count == 1
    assert tree[0].new_count == 1
    # child count is just review
    child = tree[0].children[0]
    assert child.name == "1"
    assert child.deck_id == default1
    assert child.review_count == 1
    assert child.new_count == 0
    # code should not fail if a card has an invalid deck
    c.did = 12345
    c.flush()
    col.sched.deck_due_tree()


def test_deckFlow():
    col = getEmptyCol()
    # add a note with default deck
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    # and one that's a child
    note = col.newNote()
    note["Front"] = "two"
    note.note_type()["did"] = col.decks.id("Default::2")
    col.addNote(note)
    # and another that's higher up
    note = col.newNote()
    note["Front"] = "three"
    default1 = note.note_type()["did"] = col.decks.id("Default::1")
    col.addNote(note)
    # should get top level one first, then ::1, then ::2
    col.reset()
    assert col.sched.counts() == (3, 0, 0)
    for i in "one", "three", "two":
        c = col.sched.getCard()
        assert c.note()["Front"] == i
        col.sched.answerCard(c, 2)


def test_norelearn():
    col = getEmptyCol()
    # add a note
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_REV
    c.due = 0
    c.factor = STARTING_FACTOR
    c.reps = 3
    c.lapses = 1
    c.ivl = 100
    c.start_timer()
    c.flush()
    col.reset()
    col.sched.answerCard(c, 1)
    col.sched._cardConf(c)["lapse"]["delays"] = []
    col.sched.answerCard(c, 1)


def test_failmult():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)
    c = note.cards()[0]
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_REV
    c.ivl = 100
    c.due = col.sched.today - c.ivl
    c.factor = STARTING_FACTOR
    c.reps = 3
    c.lapses = 1
    c.start_timer()
    c.flush()
    conf = col.sched._cardConf(c)
    conf["lapse"]["mult"] = 0.5
    col.decks.save(conf)
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    assert c.ivl == 50
    col.sched.answerCard(c, 1)
    assert c.ivl == 25
