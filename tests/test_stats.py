# coding: utf-8

import  os
from tests.shared import  getEmptyCol

def test_stats():
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = "foo"
    d.addNote(f)
    c = f.cards()[0]
    # card stats
    assert d.cardStats(c)
    d.reset()
    c = d.sched.getCard()
    d.sched.answerCard(c, 3)
    d.sched.answerCard(c, 2)
    assert d.cardStats(c)

def test_graphs_empty():
    d = getEmptyCol()
    assert d.stats().report()

def test_graphs():
    from anki import Collection as aopen
    d = aopen(os.path.expanduser("~/test.anki2"))
    g = d.stats()
    rep = g.report()
    with open(os.path.expanduser("~/test.html"), "w") as f:
        f.write(rep)
    return
