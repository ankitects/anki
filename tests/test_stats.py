# coding: utf-8

import time, copy
from tests.shared import assertException, getEmptyDeck
from anki.stdmodels import BasicModel
from anki.utils import stripHTML, intTime
from anki.hooks import addHook

def test_stats():
    d = getEmptyDeck()
    f = d.newFact()
    f['Front'] = "foo"
    d.addFact(f)
    c = f.cards()[0]
    # card stats
    assert d.cardStats(c)
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 3)
    d.sched.answerCard(c, 2)
    assert d.cardStats(c)
    # deck stats
    assert d.deckStats()
