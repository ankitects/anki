# coding: utf-8

import os
from tests.shared import  getEmptyDeck
from anki.utils import stripHTML

def test_latex():
    d = getEmptyDeck()
    # change latex cmd to simulate broken build
    import anki.latex
    anki.latex.latexCmd[0] = "nolatex"
    # add a note with latex
    f = d.newNote()
    f['Front'] = u"[latex]hello[/latex]"
    d.addNote(f)
    # but since latex couldn't run, there's nothing there
    assert len(os.listdir(d.media.dir())) == 0
    # check the error message
    msg = f.cards()[0].q()
    assert "executing latex" in msg
    assert "installed" in msg
    # check if we have latex installed, and abort test if we don't
    for cmd in ("latex", "dvipng"):
        if (not os.path.exists("/usr/bin/"+cmd) and
            not os.path.exists("/usr/texbin/"+cmd)):
            print "aborting test; %s is not installed" % cmd
            return
    # fix path
    anki.latex.latexCmd[0] = "latex"
    # check media db should cause latex to be generated
    d.media.check()
    assert len(os.listdir(d.media.dir())) == 1
    assert ".png" in f.cards()[0].q()
    # adding new notes should cause generation on question display
    f = d.newNote()
    f['Front'] = u"[latex]world[/latex]"
    d.addNote(f)
    f.cards()[0].q()
    assert len(os.listdir(d.media.dir())) == 2
    # another note with the same media should reuse
    f = d.newNote()
    f['Front'] = u" [latex]world[/latex]"
    d.addNote(f)
    assert len(os.listdir(d.media.dir())) == 2
    oldcard = f.cards()[0]
    assert ".png" in oldcard.q()
    # if we turn off building, then previous cards should work, but cards with
    # missing media will show the latex
    anki.latex.build = False
    f = d.newNote()
    f['Front'] = u"[latex]foo[/latex]"
    d.addNote(f)
    assert len(os.listdir(d.media.dir())) == 2
    assert stripHTML(f.cards()[0].q()) == "[latex]foo[/latex]"
    assert ".png" in oldcard.q()
