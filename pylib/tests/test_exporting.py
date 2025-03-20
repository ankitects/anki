# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import tempfile

from anki.collection import Collection as aopen
from anki.exporting import *
from anki.importing import Anki2Importer
from tests.shared import errorsAfterMidnight
from tests.shared import getEmptyCol as getEmptyColOrig


def getEmptyCol():
    col = getEmptyColOrig()
    col.upgrade_to_v2_scheduler()
    return col


col: Collection | None = None
testDir = os.path.dirname(__file__)


def setup1():
    global col
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "foo"
    note["Back"] = "bar<br>"
    note.tags = ["tag", "tag2"]
    col.addNote(note)
    # with a different col
    note = col.newNote()
    note["Front"] = "baz"
    note["Back"] = "qux"
    note_type = note.note_type()
    note_type["did"] = col.decks.id("new col")
    col.models.update_dict(note_type)
    col.addNote(note)


##########################################################################


def test_export_anki():
    setup1()
    # create a new col with its own conf to test conf copying
    did = col.decks.id("test")
    dobj = col.decks.get(did)
    confId = col.decks.add_config_returning_id("newconf")
    conf = col.decks.get_config(confId)
    conf["new"]["perDay"] = 5
    col.decks.save(conf)
    col.decks.set_config_id_for_deck_dict(dobj, confId)
    # export
    e = AnkiExporter(col)
    fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2")
    newname = str(newname)
    os.close(fd)
    os.unlink(newname)
    e.exportInto(newname)
    # exporting should not have changed conf for original deck
    conf = col.decks.config_dict_for_deck_id(did)
    assert conf["id"] != 1
    # connect to new deck
    col2 = aopen(newname)
    assert col2.card_count() == 2
    # as scheduling was reset, should also revert decks to default conf
    did = col2.decks.id("test", create=False)
    assert did
    conf2 = col2.decks.config_dict_for_deck_id(did)
    assert conf2["new"]["perDay"] == 20
    dobj = col2.decks.get(did)
    # conf should be 1
    assert dobj["conf"] == 1
    # try again, limited to a deck
    fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2")
    newname = str(newname)
    os.close(fd)
    os.unlink(newname)
    e.did = DeckId(1)
    e.exportInto(newname)
    col2 = aopen(newname)
    assert col2.card_count() == 1


def test_export_ankipkg():
    setup1()
    # add a test file to the media folder
    with open(os.path.join(col.media.dir(), "今日.mp3"), "w") as note:
        note.write("test")
    n = col.newNote()
    n["Front"] = "[sound:今日.mp3]"
    col.addNote(n)
    e = AnkiPackageExporter(col)
    fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".apkg")
    newname = str(newname)
    os.close(fd)
    os.unlink(newname)
    e.exportInto(newname)


@errorsAfterMidnight
def test_export_anki_due():
    setup1()
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "foo"
    col.addNote(note)
    col.crt -= 86400 * 10
    c = col.sched.getCard()
    col.sched.answerCard(c, 3)
    col.sched.answerCard(c, 3)
    # should have ivl of 1, due on day 11
    assert c.ivl == 1
    assert c.due == 11
    assert col.sched.today == 10
    assert c.due - col.sched.today == 1
    # export
    e = AnkiExporter(col)
    e.includeSched = True
    fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2")
    newname = str(newname)
    os.close(fd)
    os.unlink(newname)
    e.exportInto(newname)
    # importing into a new deck, the due date should be equivalent
    col2 = getEmptyCol()
    imp = Anki2Importer(col2, newname)
    imp.run()
    c = col2.getCard(c.id)
    assert c.due - col2.sched.today == 1


# def test_export_textcard():
#     setup1()
#     e = TextCardExporter(col)
#     note = unicode(tempfile.mkstemp(prefix="ankitest")[1])
#     os.unlink(note)
#     e.exportInto(note)
#     e.includeTags = True
#     e.exportInto(note)


def test_export_textnote():
    setup1()
    e = TextNoteExporter(col)
    fd, note = tempfile.mkstemp(prefix="ankitest")
    note = str(note)
    os.close(fd)
    os.unlink(note)
    e.exportInto(note)
    with open(note) as file:
        assert file.readline() == "foo\tbar<br>\ttag tag2\n"
    e.includeTags = False
    e.includeHTML = False
    e.exportInto(note)
    with open(note) as file:
        assert file.readline() == "foo\tbar\n"


def test_exporters():
    assert "*.apkg" in str(exporters(getEmptyCol()))
