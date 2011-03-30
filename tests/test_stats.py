# coding: utf-8

import time, copy, os
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

def test_graphs_empty():
    d = getEmptyDeck()
    assert d.stats().report()

def test_graphs():
    from anki import Deck
    d = Deck(os.path.expanduser("~/test.anki"))
    g = d.stats()
    rep = g.report()
    open(os.path.expanduser("~/test.html"), "w").write(rep)
    return
