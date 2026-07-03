# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# coding: utf-8

import os
import shutil

from anki.config import Config
from anki.lang import without_unicode_isolation
from tests.shared import getEmptyCol


def test_latex():
    col = getEmptyCol()
    col.set_config_bool(Config.Bool.RENDER_LATEX, True)
    # change latex cmd to simulate broken build
    import anki.latex

    anki.latex.pngCommands[0][0] = "nolatex"
    # add a note with latex
    note = col.newNote()
    note["Front"] = "[latex]hello[/latex]"
    col.addNote(note)
    # but since latex couldn't run, there's nothing there
    assert len(os.listdir(col.media.dir())) == 0
    # check the error message
    msg = note.cards()[0].question()
    assert "executing nolatex" in without_unicode_isolation(msg)
    assert "installed" in msg
    # check if we have latex installed, and abort test if we don't
    if not shutil.which("latex") or not shutil.which("dvipng"):
        print("aborting test; latex or dvipng is not installed")
        return
    # fix path
    anki.latex.pngCommands[0][0] = "latex"
    # check media db should cause latex to be generated
    col.media.render_all_latex()
    assert len(os.listdir(col.media.dir())) == 1
    assert ".png" in note.cards()[0].question()
    # adding new notes should cause generation on question display
    note = col.newNote()
    note["Front"] = "[latex]world[/latex]"
    col.addNote(note)
    note.cards()[0].question()
    assert len(os.listdir(col.media.dir())) == 2
    # another note with the same media should reuse
    note = col.newNote()
    note["Front"] = " [latex]world[/latex]"
    col.addNote(note)
    assert len(os.listdir(col.media.dir())) == 2
    oldcard = note.cards()[0]
    assert ".png" in oldcard.question()
    # if we turn off building, then previous cards should work, but cards with
    # missing media will show a broken image
    col.set_config_bool(Config.Bool.RENDER_LATEX, False)
    note = col.newNote()
    note["Front"] = "[latex]foo[/latex]"
    col.addNote(note)
    assert len(os.listdir(col.media.dir())) == 2
    assert ".png" in oldcard.question()
