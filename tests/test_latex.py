# coding: utf-8

import os
from tests.shared import  getEmptyCol
from anki.utils import stripHTML

def test_latex():
    d = getEmptyCol()
    # change latex cmd to simulate broken build
    import anki.latex
    anki.latex.latexCmds[0][0] = "nolatex"
    # add a note with latex
    f = d.newNote()
    f['Front'] = u"[latex]hello[/latex]"
    d.addNote(f)
    # but since latex couldn't run, there's nothing there
    assert len(os.listdir(d.media.dir())) == 0
    # check the error message
    msg = f.cards()[0].q()
    assert "executing nolatex" in msg
    assert "installed" in msg
    # check if we have latex installed, and abort test if we don't
    for cmd in ("latex", "dvipng"):
        if (not os.path.exists("/usr/bin/"+cmd) and
            not os.path.exists("/usr/texbin/"+cmd)):
            print "aborting test; %s is not installed" % cmd
            return
    # fix path
    anki.latex.latexCmds[0][0] = "latex"
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
    # turn it on again so other test don't suffer
    anki.latex.build = True

def test_bad_latex_command_write18():
    (result, msg) = _test_includes_bad_command("\\write18")
    assert result, msg

def test_bad_latex_command_readline():
    (result, msg) = _test_includes_bad_command("\\readline")
    assert result, msg

def test_bad_latex_command_input():
    (result, msg) = _test_includes_bad_command("\\input")
    assert result, msg

def test_bad_latex_command_include():
    (result, msg) = _test_includes_bad_command("\\include")
    assert result, msg

def test_bad_latex_command_catcode():
    (result, msg) = _test_includes_bad_command("\\catcode")
    assert result, msg

def test_bad_latex_command_openout():
    (result, msg) = _test_includes_bad_command("\\openout")
    assert result, msg

def test_bad_latex_command_write():
    (result, msg) = _test_includes_bad_command("\\write")
    assert result, msg

def test_bad_latex_command_loop():
    (result, msg) = _test_includes_bad_command("\\loop")
    assert result, msg

def test_bad_latex_command_def():
    (result, msg) = _test_includes_bad_command("\\def")
    assert result, msg

def test_bad_latex_command_shipout():
    (result, msg) = _test_includes_bad_command("\\shipout")
    assert result, msg

def test_good_latex_command_works():
    # inserting commands beginning with a bad name should not raise an error
    (result, msg) = _test_includes_bad_command("\\defeq")
    assert not result, msg
    # normal commands should not either
    (result, msg) = _test_includes_bad_command("\\emph")
    assert not result, msg

def _test_includes_bad_command(bad):
    d = getEmptyCol()
    f = d.newNote()
    f['Front'] = u'[latex]%s[/latex]' % bad;
    d.addNote(f)
    q = f.cards()[0].q()
    return ("'%s' is not allowed on cards" % bad in q, "Card content: %s" % q)