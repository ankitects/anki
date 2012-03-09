# coding: utf-8

import nose, os, tempfile
import anki
from anki import Collection as aopen
from anki.exporting import *
from anki.stdmodels import *
from shared import getEmptyDeck

deck = None
ds = None
testDir = os.path.dirname(__file__)

def setup1():
    global deck
    deck = getEmptyDeck()
    f = deck.newNote()
    f['Front'] = u"foo"; f['Back'] = u"bar"; f.tags = ["tag", "tag2"]
    deck.addNote(f)
    # with a different deck
    f = deck.newNote()
    f['Front'] = u"baz"; f['Back'] = u"qux"
    f.model()['did'] = deck.decks.id("new deck")
    deck.addNote(f)

##########################################################################

@nose.with_setup(setup1)
def test_export_anki():
    e = AnkiExporter(deck)
    newname = unicode(tempfile.mkstemp(prefix="ankitest", suffix=".anki2")[1])
    os.unlink(newname)
    e.exportInto(newname)
    # connect to new deck
    d2 = aopen(newname)
    assert d2.cardCount() == 2
    # try again, limited to a deck
    newname = unicode(tempfile.mkstemp(prefix="ankitest", suffix=".anki2")[1])
    os.unlink(newname)
    e.did = 1
    e.exportInto(newname)
    d2 = aopen(newname)
    assert d2.cardCount() == 1

@nose.with_setup(setup1)
def test_export_ankipkg():
    # add a test file to the media folder
    open(os.path.join(deck.media.dir(), u"今日.mp3"), "w").write("test")
    n = deck.newNote()
    n['Front'] = u'[sound:今日.mp3]'
    deck.addNote(n)
    e = AnkiPackageExporter(deck)
    newname = unicode(tempfile.mkstemp(prefix="ankitest", suffix=".apkg")[1])
    os.unlink(newname)
    e.exportInto(newname)

# @nose.with_setup(setup1)
# def test_export_textcard():
#     e = TextCardExporter(deck)
#     f = unicode(tempfile.mkstemp(prefix="ankitest")[1])
#     os.unlink(f)
#     e.exportInto(f)
#     e.includeTags = True
#     e.exportInto(f)

@nose.with_setup(setup1)
def test_export_textnote():
    e = TextNoteExporter(deck)
    f = unicode(tempfile.mkstemp(prefix="ankitest")[1])
    os.unlink(f)
    e.exportInto(f)
    e.includeTags = True
    e.exportInto(f)

def test_exporters():
    assert "*.apkg" in str(exporters())
