# coding: utf-8

import os
from tests.shared import assertException, getEmptyDeck
from anki.utils import stripHTML, intTime
from anki.hooks import addHook

def test_latex():
    d = getEmptyDeck()
    # change latex cmd to simulate broken build
    import anki.latex
    anki.latex.latexCmd[0] = "nolatex"
    # add a fact with latex
    f = d.newFact()
    f['Front'] = u"[latex]hello[/latex]"
    d.addFact(f)
    # but since latex couldn't run, it will only have the media.db
    assert len(os.listdir(d.media.dir())) == 1
    # check the error message
    msg = f.cards()[0].q()
    assert "executing latex" in msg
    assert "installed" in msg
    # check if we have latex installed, and abort test if we don't
    if not os.path.exists("/usr/bin/latex"):
        print "aborting test; latex is not installed"
        return
    # fix path
    anki.latex.latexCmd[0] = "latex"
    # check media db should cause latex to be generated
    d.media.check()
    assert len(os.listdir(d.media.dir())) == 1
    assert ".png" in f.cards()[0].q()
    # adding new facts should cause immediate generation
    f = d.newFact()
    f['Front'] = u"[latex]world[/latex]"
    d.addFact(f)
    assert len(os.listdir(d.media.dir())) == 2
    # another fact with the same media should reuse
    f = d.newFact()
    f['Front'] = u" [latex]world[/latex]"
    d.addFact(f)
    assert len(os.listdir(d.media.dir())) == 2
    oldcard = f.cards()[0]
    assert ".png" in oldcard.q()
    # if we turn off building, then previous cards should work, but cards with
    # missing media will show the latex
    anki.latex.build = False
    f = d.newFact()
    f['Front'] = u"[latex]foo[/latex]"
    d.addFact(f)
    assert len(os.listdir(d.media.dir())) == 2
    assert stripHTML(f.cards()[0].q()) == "[latex]foo[/latex]"
    assert ".png" in oldcard.q()
