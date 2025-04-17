# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import copy
import os
import time
from collections.abc import Callable
from typing import Dict

import pytest

from anki import hooks
from anki.consts import *
from anki.lang import without_unicode_isolation
from anki.scheduler import UnburyDeck
from anki.utils import int_time
from tests.shared import getEmptyCol as getEmptyColOrig


def getEmptyCol():
    col = getEmptyColOrig()
    return col


def test_clock():
    col = getEmptyCol()
    if (col.sched.day_cutoff - int_time()) < 10 * 60:
        raise Exception("Unit tests will fail around the day rollover.")


def test_basics():
    col = getEmptyCol()
    assert not col.sched.getCard()


def test_new():
    col = getEmptyCol()
    assert col.sched.newCount == 0
    # add a note
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)
    assert col.sched.newCount == 1
    # fetch it
    c = col.sched.getCard()
    assert c
    assert c.queue == QUEUE_TYPE_NEW
    assert c.type == CARD_TYPE_NEW
    # if we answer it, it should become a learn card
    t = int_time()
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
            note_type = note.note_type()
            note_type["did"] = deck2
            col.models.update_dict(note_type)
        col.addNote(note)
    # give the child deck a different configuration
    c2 = col.decks.add_config_returning_id("new conf")
    col.decks.set_config_id_for_deck_dict(col.decks.get(deck2), c2)
    # both confs have defaulted to a limit of 20
    assert col.sched.newCount == 20
    # first card we get comes from parent
    c = col.sched.getCard()
    assert c.did == 1
    # limit the parent to 10 cards, meaning we get 10 in total
    conf1 = col.decks.config_dict_for_deck_id(1)
    conf1["new"]["perDay"] = 10
    col.decks.save(conf1)
    assert col.sched.newCount == 10
    # if we limit child to 4, we should get 9
    conf2 = col.decks.config_dict_for_deck_id(deck2)
    conf2["new"]["perDay"] = 4
    col.decks.save(conf2)
    assert col.sched.newCount == 9


def test_newBoxes():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
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
    # it should be due in 30 seconds
    t = round(c.due - time.time())
    assert t >= 25 and t <= 40
    # pass it once
    col.sched.answerCard(c, 3)
    # it should be due in 3 minutes
    dueIn = c.due - time.time()
    assert 178 <= dueIn <= 180 * 1.25
    assert c.left % 1000 == 2
    # check log is accurate
    log = col.db.first("select * from revlog order by id desc")
    assert log[3] == 3
    assert log[4] == -180
    assert log[5] == -30
    # pass again
    col.sched.answerCard(c, 3)
    # it should be due in 10 minutes
    dueIn = c.due - time.time()
    assert 598 <= dueIn <= 600 * 1.25
    assert c.left % 1000 == 1
    # the next pass should graduate the card
    assert c.queue == QUEUE_TYPE_LRN
    assert c.type == CARD_TYPE_LRN
    col.sched.answerCard(c, 3)
    assert c.queue == QUEUE_TYPE_REV
    assert c.type == CARD_TYPE_REV
    # should be due tomorrow, with an interval of 1
    assert c.due == col.sched.today + 1
    assert c.ivl == 1
    # or normal removal
    c.type = CARD_TYPE_NEW
    c.queue = QUEUE_TYPE_LRN
    c.flush()
    col.sched.answerCard(c, 4)
    assert c.type == CARD_TYPE_REV
    assert c.queue == QUEUE_TYPE_REV
    # revlog should have been updated each time
    assert col.db.scalar("select count() from revlog where type = 0") == 5


def test_relearn():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    c.ivl = 100
    c.due = col.sched.today
    c.queue = CARD_TYPE_REV
    c.type = QUEUE_TYPE_REV
    c.flush()

    # fail the card
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    assert c.queue == QUEUE_TYPE_LRN
    assert c.type == CARD_TYPE_RELEARNING
    assert c.ivl == 1

    # immediately graduate it
    col.sched.answerCard(c, 4)
    assert c.queue == CARD_TYPE_REV and c.type == QUEUE_TYPE_REV
    assert c.ivl == 2
    assert c.due == col.sched.today + c.ivl


def test_relearn_no_steps():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    c.ivl = 100
    c.due = col.sched.today
    c.queue = CARD_TYPE_REV
    c.type = QUEUE_TYPE_REV
    c.flush()

    conf = col.decks.config_dict_for_deck_id(1)
    conf["lapse"]["delays"] = []
    col.decks.save(conf)

    # fail the card
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    assert c.queue == CARD_TYPE_REV and c.type == QUEUE_TYPE_REV


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
    # should get '1' first
    c = col.sched.getCard()
    assert c.question().endswith("1")
    # pass it so it's due in 10 minutes
    col.sched.answerCard(c, 3)
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
    note = col.newNote()
    note["Front"] = "two"
    col.addNote(note)
    c = col.sched.getCard()
    conf = col.sched._cardConf(c)
    conf["new"]["delays"] = [1, 10, 1440, 2880]
    col.decks.save(conf)
    # pass it
    col.sched.answerCard(c, 3)
    # two reps to graduate, 1 more today
    assert c.left % 1000 == 3
    assert col.sched.counts() == (1, 1, 0)
    c.load()
    ni = col.sched.nextIvl
    assert ni(c, 3) == 86400
    # answer the other dummy card
    col.sched.answerCard(col.sched.getCard(), 4)
    # answering the first one will place it in queue 3
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    assert c.due == col.sched.today + 1
    assert c.queue == QUEUE_TYPE_DAY_LEARN_RELEARN
    assert not col.sched.getCard()
    # for testing, move it back a day
    c.due -= 1
    c.flush()
    assert col.sched.counts() == (0, 1, 0)
    c = col.sched.getCard()
    # nextIvl should work
    assert ni(c, 3) == 86400 * 2
    # if we fail it, it should be back in the correct queue
    col.sched.answerCard(c, 1)
    assert c.queue == QUEUE_TYPE_LRN
    col.undo()
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    # simulate the passing of another two days
    c.due -= 2
    c.flush()
    # the last pass should graduate it into a review card
    assert ni(c, 3) == 86400
    col.sched.answerCard(c, 3)
    assert c.queue == CARD_TYPE_REV and c.type == QUEUE_TYPE_REV
    # if the lapse step is tomorrow, failing it should handle the counts
    # correctly
    c.due = 0
    c.flush()
    assert col.sched.counts() == (0, 0, 1)
    conf = col.sched._cardConf(c)
    conf["lapse"]["delays"] = [1440]
    col.decks.save(conf)
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    assert c.queue == QUEUE_TYPE_DAY_LEARN_RELEARN
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
    # try with an ease of 2
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    col.sched.answerCard(c, 2)
    assert c.queue == QUEUE_TYPE_REV
    # the new interval should be (100) * 1.2 = 120
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
    assert c.due == col.sched.today + c.ivl
    # factor should have been left alone
    assert c.factor == STARTING_FACTOR
    # ease 4
    ##################################################
    c = copy.copy(cardcopy)
    c.flush()
    col.sched.answerCard(c, 4)
    # the new interval should be (100 + 8) * 2.5 * 1.3 = 351
    assert c.due == col.sched.today + c.ivl
    # factor should have been increased
    assert c.factor == 2650
    # leech handling
    ##################################################
    conf = col.decks.get_config(1)
    conf["lapse"]["leechAction"] = LEECH_SUSPEND
    col.decks.save(conf)
    c = copy.copy(cardcopy)
    c.lapses = 7
    c.flush()

    col.sched.answerCard(c, 1)
    assert c.queue == QUEUE_TYPE_SUSPENDED
    c.load()
    assert c.queue == QUEUE_TYPE_SUSPENDED
    assert "leech" in c.note().tags


def review_limits_setup() -> tuple[anki.collection.Collection, dict]:
    col = getEmptyCol()

    parent = col.decks.get(col.decks.id("parent"))
    child = col.decks.get(col.decks.id("parent::child"))

    pconf = col.decks.get_config(col.decks.add_config_returning_id("parentConf"))
    cconf = col.decks.get_config(col.decks.add_config_returning_id("childConf"))

    pconf["rev"]["perDay"] = 5
    col.decks.update_config(pconf)
    col.decks.set_config_id_for_deck_dict(parent, pconf["id"])
    cconf["rev"]["perDay"] = 10
    col.decks.update_config(cconf)
    col.decks.set_config_id_for_deck_dict(child, cconf["id"])

    m = col.models.current()
    m["did"] = child["id"]
    col.models.save(m, updateReqs=False)

    # add some cards
    for i in range(20):
        note = col.newNote()
        note["Front"] = "one"
        note["Back"] = "two"
        col.addNote(note)

        # make them reviews
        c = note.cards()[0]
        c.queue = CARD_TYPE_REV
        c.type = QUEUE_TYPE_REV
        c.due = 0
        c.flush()

    return col, child


def test_review_limits():
    col, child = review_limits_setup()

    tree = col.sched.deck_due_tree().children
    # (('parent', 1514457677462, 5, 0, 0, (('child', 1514457677463, 5, 0, 0, ()),)))
    assert tree[0].review_count == 5  # parent
    assert tree[0].children[0].review_count == 10  # child

    # .counts() should match
    col.decks.select(child["id"])
    col.sched.reset()
    assert col.sched.counts() == (0, 0, 10)

    # answering a card in the child should decrement parent count
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    assert col.sched.counts() == (0, 0, 9)

    tree = col.sched.deck_due_tree().children
    assert tree[0].review_count == 4  # parent
    assert tree[0].children[0].review_count == 9  # child


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
    ni = col.sched.nextIvlStr
    wo = without_unicode_isolation
    assert wo(ni(c, 2)) == "2d"
    assert wo(ni(c, 3)) == "3d"
    assert wo(ni(c, 4)) == "4d"

    # if hard factor is <= 1, then hard may not increase
    conf = col.decks.config_dict_for_deck_id(1)
    conf["rev"]["hardFactor"] = 1
    col.decks.save(conf)
    assert wo(ni(c, 2)) == "1d"


def test_nextIvl():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)
    conf = col.decks.config_dict_for_deck_id(1)
    conf["new"]["delays"] = [0.5, 3, 10]
    conf["lapse"]["delays"] = [1, 5, 9]
    col.decks.save(conf)
    c = col.sched.getCard()
    # new cards
    ##################################################
    ni = col.sched.nextIvl
    assert ni(c, 1) == 30
    assert ni(c, 2) == (30 + 180) // 2
    assert ni(c, 3) == 180
    assert ni(c, 4) == 4 * 86400
    col.sched.answerCard(c, 1)
    # cards in learning
    ##################################################
    assert ni(c, 1) == 30
    assert ni(c, 2) == (30 + 180) // 2
    assert ni(c, 3) == 180
    assert ni(c, 4) == 4 * 86400
    col.sched.answerCard(c, 3)
    assert ni(c, 1) == 30
    assert ni(c, 2) == 180
    assert ni(c, 3) == 600
    assert ni(c, 4) == 4 * 86400
    col.sched.answerCard(c, 3)
    # normal graduation is tomorrow
    assert ni(c, 3) == 1 * 86400
    assert ni(c, 4) == 4 * 86400
    # lapsed cards
    ##################################################
    c.type = CARD_TYPE_RELEARNING
    c.ivl = 100
    c.factor = STARTING_FACTOR
    c.flush()
    assert ni(c, 1) == 60
    assert ni(c, 3) == 100 * 86400
    assert ni(c, 4) == 101 * 86400
    # review cards
    ##################################################
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_REV
    c.ivl = 100
    c.factor = STARTING_FACTOR
    c.flush()
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
    assert without_unicode_isolation(col.sched.nextIvlStr(c, 4)) == "10.7mo"


def test_bury():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    note = col.newNote()
    note["Front"] = "two"
    col.addNote(note)
    c2 = note.cards()[0]
    # burying
    col.sched.bury_cards([c.id], manual=True)  # pylint: disable=unexpected-keyword-arg
    c.load()
    assert c.queue == QUEUE_TYPE_MANUALLY_BURIED
    col.sched.bury_cards(
        [c2.id], manual=False
    )  # pylint: disable=unexpected-keyword-arg
    c2.load()
    assert c2.queue == QUEUE_TYPE_SIBLING_BURIED

    assert not col.sched.getCard()

    col.sched.unbury_deck(deck_id=col.decks.get_current_id(), mode=UnburyDeck.USER_ONLY)
    c.load()
    assert c.queue == QUEUE_TYPE_NEW
    c2.load()
    assert c2.queue == QUEUE_TYPE_SIBLING_BURIED

    col.sched.unbury_deck(
        deck_id=col.decks.get_current_id(), mode=UnburyDeck.SCHED_ONLY
    )
    c2.load()
    assert c2.queue == QUEUE_TYPE_NEW

    col.sched.bury_cards([c.id, c2.id])
    col.sched.unbury_deck(deck_id=col.decks.get_current_id())

    assert col.sched.counts() == (2, 0, 0)


def test_suspend():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    # suspending
    assert col.sched.getCard()
    col.sched.suspend_cards([c.id])
    assert not col.sched.getCard()
    # unsuspending
    col.sched.unsuspend_cards([c.id])
    assert col.sched.getCard()
    # should cope with rev cards being relearnt
    c.due = 0
    c.ivl = 100
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_REV
    c.flush()
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    assert c.due >= time.time()
    due = c.due
    assert c.queue == QUEUE_TYPE_LRN
    assert c.type == CARD_TYPE_RELEARNING
    col.sched.suspend_cards([c.id])
    col.sched.unsuspend_cards([c.id])
    c.load()
    assert c.queue == QUEUE_TYPE_LRN
    assert c.type == CARD_TYPE_RELEARNING
    assert c.due == due
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
    assert c.due != 1
    assert c.did != 1
    assert c.odue == 1


def test_filt_reviewing_early_normal():
    col = getEmptyCol()
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
    assert col.sched.counts() == (0, 0, 0)
    # create a dynamic deck and refresh it
    did = col.decks.new_filtered("Cram")
    col.sched.rebuild_filtered_deck(did)
    # should appear as normal in the deck list
    assert sorted(col.sched.deck_due_tree().children)[0].review_count == 1
    # and should appear in the counts
    assert col.sched.counts() == (0, 0, 1)
    # grab it and check estimates
    c = col.sched.getCard()
    assert col.sched.answerButtons(c) == 4
    assert col.sched.nextIvl(c, 1) == 600
    assert col.sched.nextIvl(c, 2) == round(75 * 1.2) * 86400
    assert col.sched.nextIvl(c, 3) == round(75 * 2.5) * 86400
    assert col.sched.nextIvl(c, 4) == round(75 * 2.5 * 1.15) * 86400

    # answer 'good'
    col.sched.answerCard(c, 3)
    assert c.due == col.sched.today + c.ivl
    # should not be in learning
    assert c.queue == QUEUE_TYPE_REV
    # should be logged as a cram rep
    assert col.db.scalar("select type from revlog order by id desc limit 1") == 3

    # due in 75 days, so it's been waiting 25 days
    c.ivl = 100
    c.due = col.sched.today + 75
    c.flush()
    col.sched.rebuild_filtered_deck(did)
    c = col.sched.getCard()

    assert col.sched.nextIvl(c, 2) == 100 * 1.2 / 2 * 86400
    assert col.sched.nextIvl(c, 3) == 100 * 86400
    assert col.sched.nextIvl(c, 4) == round(100 * (1.3 - (1.3 - 1) / 2)) * 86400


def test_filt_keep_lrn_state():
    col = getEmptyCol()

    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)

    # fail the card outside filtered deck
    c = col.sched.getCard()
    conf = col.sched._cardConf(c)
    conf["new"]["delays"] = [1, 10, 61]
    col.decks.save(conf)

    col.sched.answerCard(c, 1)

    assert c.type == CARD_TYPE_LRN and c.queue == QUEUE_TYPE_LRN
    assert c.left % 1000 == 3

    col.sched.answerCard(c, 3)
    assert c.type == CARD_TYPE_LRN and c.queue == QUEUE_TYPE_LRN

    # create a dynamic deck and refresh it
    did = col.decks.new_filtered("Cram")
    col.sched.rebuild_filtered_deck(did)

    # card should still be in learning state
    c.load()
    assert c.type == CARD_TYPE_LRN and c.queue == QUEUE_TYPE_LRN
    assert c.left % 1000 == 2

    # should be able to advance learning steps
    col.sched.answerCard(c, 3)
    # should be due at least an hour in the future
    assert c.due - int_time() > 60 * 60

    # emptying the deck preserves learning state
    col.sched.empty_filtered_deck(did)
    c.load()
    assert c.type == CARD_TYPE_LRN and c.queue == QUEUE_TYPE_LRN
    assert c.left % 1000 == 1
    assert c.due - int_time() > 60 * 60


def test_preview():
    # add cards
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    note2 = col.newNote()
    note2["Front"] = "two"
    col.addNote(note2)
    # cram deck
    did = col.decks.new_filtered("Cram")
    cram = col.decks.get(did)
    cram["resched"] = False
    col.decks.save(cram)
    col.sched.rebuild_filtered_deck(did)
    # grab the first card
    c = col.sched.getCard()

    passing_grade = 4
    assert col.sched.answerButtons(c) == passing_grade
    assert col.sched.nextIvl(c, 1) == 60
    assert col.sched.nextIvl(c, passing_grade) == 0

    # failing it will push its due time back
    due = c.due
    col.sched.answerCard(c, 1)
    assert c.due != due

    # the other card should come next
    c2 = col.sched.getCard()
    assert c2.id != c.id

    # passing it will remove it
    col.sched.answerCard(c2, passing_grade)
    assert c2.queue == QUEUE_TYPE_NEW
    assert c2.reps == 0
    assert c2.type == CARD_TYPE_NEW

    # emptying the filtered deck should restore card
    col.sched.empty_filtered_deck(did)
    c.load()
    assert c.queue == QUEUE_TYPE_NEW
    assert c.reps == 0
    assert c.type == CARD_TYPE_NEW


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

    conf = col.decks.get_config(1)
    conf["new"]["bury"] = False
    col.decks.save(conf)

    # ordinals should arrive in order
    for i in range(3):
        c = col.sched.getCard()
        assert c.ord == i
        col.sched.answerCard(c, 4)


def test_counts_idx_new():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)
    note = col.newNote()
    note["Front"] = "two"
    note["Back"] = "two"
    col.addNote(note)
    assert col.sched.counts() == (2, 0, 0)
    c = col.sched.getCard()
    # getCard does not decrement counts
    assert col.sched.counts() == (2, 0, 0)
    assert col.sched.countIdx(c) == 0
    # answer to move to learn queue
    col.sched.answerCard(c, 1)
    assert col.sched.counts() == (1, 1, 0)
    assert col.sched.countIdx(c) == 1
    # fetching next will not decrement the count
    c = col.sched.getCard()
    assert col.sched.counts() == (1, 1, 0)
    assert col.sched.countIdx(c) == 0


def test_repCounts():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    note = col.newNote()
    note["Front"] = "two"
    col.addNote(note)
    # lrnReps should be accurate on pass/fail
    assert col.sched.counts() == (2, 0, 0)
    col.sched.answerCard(col.sched.getCard(), 1)
    assert col.sched.counts() == (1, 1, 0)
    col.sched.answerCard(col.sched.getCard(), 1)
    assert col.sched.counts() == (0, 2, 0)
    col.sched.answerCard(col.sched.getCard(), 3)
    assert col.sched.counts() == (0, 2, 0)
    col.sched.answerCard(col.sched.getCard(), 1)
    assert col.sched.counts() == (0, 2, 0)
    col.sched.answerCard(col.sched.getCard(), 3)
    assert col.sched.counts() == (0, 1, 0)
    col.sched.answerCard(col.sched.getCard(), 4)
    assert col.sched.counts() == (0, 0, 0)
    note = col.newNote()
    note["Front"] = "three"
    col.addNote(note)
    note = col.newNote()
    note["Front"] = "four"
    col.addNote(note)
    # initial pass and immediate graduate should be correct too
    assert col.sched.counts() == (2, 0, 0)
    col.sched.answerCard(col.sched.getCard(), 3)
    assert col.sched.counts() == (1, 1, 0)
    col.sched.answerCard(col.sched.getCard(), 4)
    assert col.sched.counts() == (0, 1, 0)
    col.sched.answerCard(col.sched.getCard(), 4)
    assert col.sched.counts() == (0, 0, 0)
    # and failing a review should too
    note = col.newNote()
    note["Front"] = "five"
    col.addNote(note)
    c = note.cards()[0]
    c.type = CARD_TYPE_REV
    c.queue = QUEUE_TYPE_REV
    c.due = col.sched.today
    c.flush()
    note = col.newNote()
    note["Front"] = "six"
    col.addNote(note)
    assert col.sched.counts() == (1, 0, 1)
    col.sched.answerCard(col.sched.getCard(), 1)
    assert col.sched.counts() == (1, 1, 0)


def test_timing():
    col = getEmptyCol()
    # add a few review cards, due today
    for i in range(5):
        note = col.newNote()
        note["Front"] = f"num{str(i)}"
        col.addNote(note)
        c = note.cards()[0]
        c.type = CARD_TYPE_REV
        c.queue = QUEUE_TYPE_REV
        c.due = 0
        c.flush()
    # fail the first one
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    # the next card should be another review
    c2 = col.sched.getCard()
    assert c2.queue == QUEUE_TYPE_REV
    # if the failed card becomes due, it should show first
    c.due = int_time() - 1
    c.flush()
    c = col.sched.getCard()
    assert c.queue == QUEUE_TYPE_LRN


def test_collapse():
    col = getEmptyCol()
    # add a note
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    # and another, so we don't get the same twice in a row
    note = col.newNote()
    note["Front"] = "two"
    col.addNote(note)
    # first note
    c = col.sched.getCard()
    col.sched.answerCard(c, 1)
    # second note
    c2 = col.sched.getCard()
    assert c2.nid != c.nid
    col.sched.answerCard(c2, 1)
    # first should become available again, despite it being due in the future
    c3 = col.sched.getCard()
    assert c3.due > int_time()
    col.sched.answerCard(c3, 4)
    # answer other
    c4 = col.sched.getCard()
    col.sched.answerCard(c4, 4)
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
    note_type = note.note_type()
    default1 = note_type["did"] = col.decks.id("Default::1")
    col.models.update_dict(note_type)
    col.addNote(note)
    # make it a review card
    c = note.cards()[0]
    c.queue = QUEUE_TYPE_REV
    c.due = 0
    c.flush()
    # add one more with a new deck
    note = col.newNote()
    note["Front"] = "two"
    note_type = note.note_type()
    note_type["did"] = col.decks.id("foo::bar")
    col.models.update_dict(note_type)
    col.addNote(note)
    # and one that's a sibling
    note = col.newNote()
    note["Front"] = "three"
    note_type = note.note_type()
    note_type["did"] = col.decks.id("foo::baz")
    col.models.update_dict(note_type)
    col.addNote(note)
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


def test_deckTree():
    col = getEmptyCol()
    col.decks.id("new::b::c")
    col.decks.id("new2")
    # new should not appear twice in tree
    names = [x.name for x in col.sched.deck_due_tree().children]
    names.remove("new")
    assert "new" not in names


def test_deckFlow():
    col = getEmptyCol()
    # add a note with default deck
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    # and one that's a child
    note = col.newNote()
    note["Front"] = "two"
    note_type = note.note_type()
    note_type["did"] = col.decks.id("Default::2")
    col.models.update_dict(note_type)
    col.addNote(note)
    # and another that's higher up
    note = col.newNote()
    note["Front"] = "three"
    note_type = note.note_type()
    default1 = note_type["did"] = col.decks.id("Default::1")
    col.models.update_dict(note_type)
    col.addNote(note)
    assert col.sched.counts() == (3, 0, 0)
    # should get top level one first, then ::1, then ::2
    for i in "one", "three", "two":
        c = col.sched.getCard()
        assert c.note()["Front"] == i
        col.sched.answerCard(c, 3)


def test_reorder():
    col = getEmptyCol()
    # add a note with default deck
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    note2 = col.newNote()
    note2["Front"] = "two"
    col.addNote(note2)
    assert note2.cards()[0].due == 2
    found = False
    # 50/50 chance of being reordered
    for i in range(20):
        col.sched.randomize_cards(1)
        if note.cards()[0].due != note.id:
            found = True
            break
    assert found
    col.sched.order_cards(1)
    assert note.cards()[0].due == 1
    # shifting
    note3 = col.newNote()
    note3["Front"] = "three"
    col.addNote(note3)
    note4 = col.newNote()
    note4["Front"] = "four"
    col.addNote(note4)
    assert note.cards()[0].due == 1
    assert note2.cards()[0].due == 2
    assert note3.cards()[0].due == 3
    assert note4.cards()[0].due == 4
    col.sched.reposition_new_cards(
        [note3.cards()[0].id, note4.cards()[0].id],
        starting_from=1,
        shift_existing=True,
        step_size=1,
        randomize=False,
    )
    assert note.cards()[0].due == 3
    assert note2.cards()[0].due == 4
    assert note3.cards()[0].due == 1
    assert note4.cards()[0].due == 2


def test_forget():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    c.queue = QUEUE_TYPE_REV
    c.type = CARD_TYPE_REV
    c.ivl = 100
    c.due = 0
    c.flush()
    assert col.sched.counts() == (0, 0, 1)
    col.sched.forgetCards([c.id])
    assert col.sched.counts() == (1, 0, 0)


def test_resched():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    col.addNote(note)
    c = note.cards()[0]
    col.sched.set_due_date([c.id], "0")
    c.load()
    assert c.due == col.sched.today
    assert c.ivl == 1
    assert c.queue == QUEUE_TYPE_REV and c.type == CARD_TYPE_REV
    # make it due tomorrow
    col.sched.set_due_date([c.id], "1")
    c.load()
    assert c.due == col.sched.today + 1
    assert c.ivl == 1


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


# cards with a due date earlier than the collection should retain
# their due date when removed
def test_negativeDueFilter():
    col = getEmptyCol()

    # card due prior to collection date
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)
    c = note.cards()[0]
    c.due = -5
    c.queue = QUEUE_TYPE_REV
    c.ivl = 5
    c.flush()

    # into and out of filtered deck
    did = col.decks.new_filtered("Cram")
    col.sched.rebuild_filtered_deck(did)
    col.sched.empty_filtered_deck(did)

    c.load()
    assert c.due == -5


# hard on the first step should be the average of again and good,
# and it should be logged properly
def test_initial_repeat():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    col.addNote(note)

    c = col.sched.getCard()
    col.sched.answerCard(c, 2)
    # should be due in ~ 5.5 mins
    expected = time.time() + 5.5 * 60
    assert expected - 10 < c.due < expected * 1.25

    ivl = col.db.scalar("select ivl from revlog")
    assert ivl == -5.5 * 60
