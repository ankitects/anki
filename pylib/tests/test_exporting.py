# coding: utf-8

import os
import tempfile

from anki import Collection as aopen
from anki.exporting import *
from anki.importing import Anki2Importer
from tests.shared import errorsAfterMidnight
from tests.shared import getEmptyCol as getEmptyColOrig


def getEmptyCol():
    col = getEmptyColOrig()
    col.changeSchedulerVer(2)
    return col


deck = None
ds = None
testDir = os.path.dirname(__file__)


def setup1():
    global deck
    deck = getEmptyCol()
    note = deck.newNote()
    note["Front"] = "foo"
    note["Back"] = "bar<br>"
    note.tags = ["tag", "tag2"]
    deck.addNote(note)
    # with a different deck
    note = deck.newNote()
    note["Front"] = "baz"
    note["Back"] = "qux"
    note.model()["did"] = deck.decks.id("new deck")
    deck.addNote(note)


##########################################################################


def test_export_anki():
    setup1()
    # create a new deck with its own conf to test conf copying
    did = deck.decks.id("test")
    dobj = deck.decks.get(did)
    confId = deck.decks.add_config_returning_id("newconf")
    conf = deck.decks.get_config(confId)
    conf["new"]["perDay"] = 5
    deck.decks.save(conf)
    deck.decks.setConf(dobj, confId)
    # export
    e = AnkiExporter(deck)
    fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2")
    newname = str(newname)
    os.close(fd)
    os.unlink(newname)
    e.exportInto(newname)
    # exporting should not have changed conf for original deck
    conf = deck.decks.confForDid(did)
    assert conf["id"] != 1
    # connect to new deck
    d2 = aopen(newname)
    assert d2.cardCount() == 2
    # as scheduling was reset, should also revert decks to default conf
    did = d2.decks.id("test", create=False)
    assert did
    conf2 = d2.decks.confForDid(did)
    assert conf2["new"]["perDay"] == 20
    dobj = d2.decks.get(did)
    # conf should be 1
    assert dobj["conf"] == 1
    # try again, limited to a deck
    fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2")
    newname = str(newname)
    os.close(fd)
    os.unlink(newname)
    e.did = 1
    e.exportInto(newname)
    d2 = aopen(newname)
    assert d2.cardCount() == 1


def test_export_ankipkg():
    setup1()
    # add a test file to the media folder
    with open(os.path.join(deck.media.dir(), "今日.mp3"), "w") as note:
        note.write("test")
    n = deck.newNote()
    n["Front"] = "[sound:今日.mp3]"
    deck.addNote(n)
    e = AnkiPackageExporter(deck)
    fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".apkg")
    newname = str(newname)
    os.close(fd)
    os.unlink(newname)
    e.exportInto(newname)


@errorsAfterMidnight
def test_export_anki_due():
    setup1()
    deck = getEmptyCol()
    note = deck.newNote()
    note["Front"] = "foo"
    deck.addNote(note)
    deck.crt -= 86400 * 10
    deck.flush()
    deck.sched.reset()
    c = deck.sched.getCard()
    deck.sched.answerCard(c, 3)
    deck.sched.answerCard(c, 3)
    # should have ivl of 1, due on day 11
    assert c.ivl == 1
    assert c.due == 11
    assert deck.sched.today == 10
    assert c.due - deck.sched.today == 1
    # export
    e = AnkiExporter(deck)
    e.includeSched = True
    fd, newname = tempfile.mkstemp(prefix="ankitest", suffix=".anki2")
    newname = str(newname)
    os.close(fd)
    os.unlink(newname)
    e.exportInto(newname)
    # importing into a new deck, the due date should be equivalent
    deck2 = getEmptyCol()
    imp = Anki2Importer(deck2, newname)
    imp.run()
    c = deck2.getCard(c.id)
    deck2.sched.reset()
    assert c.due - deck2.sched.today == 1


# def test_export_textcard():
#     setup1()
#     e = TextCardExporter(deck)
#     note = unicode(tempfile.mkstemp(prefix="ankitest")[1])
#     os.unlink(note)
#     e.exportInto(note)
#     e.includeTags = True
#     e.exportInto(note)


def test_export_textnote():
    setup1()
    e = TextNoteExporter(deck)
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
    assert "*.apkg" in str(exporters())
