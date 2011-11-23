# coding: utf-8

import nose, os, tempfile
import anki
from anki import Deck
from anki.exporting import *
from anki.stdmodels import *

deck = None
ds = None
testDir = os.path.dirname(__file__)

def setup1():
    global deck
    deck = Deck()
    deck.addModel(BasicModel())
    deck.currentModel.cardModels[1].active = True
    f = deck.newNote()
    f['Front'] = u"foo"; f['Back'] = u"bar"; f.tags = u"tag, tag2"
    deck.addNote(f)
    f = deck.newNote()
    f['Front'] = u"baz"; f['Back'] = u"qux"
    deck.addNote(f)

##########################################################################

@nose.with_setup(setup1)
def test_export_anki():
    oldTime = deck.modified
    e = AnkiExporter(deck)
    newname = unicode(tempfile.mkstemp(prefix="ankitest")[1])
    os.unlink(newname)
    e.exportInto(newname)
    assert deck.modified == oldTime
    # connect to new deck
    d2 = Deck(newname, backup=False)
    assert d2.cardCount() == 4
    # try again, limited to a tag
    newname = unicode(tempfile.mkstemp(prefix="ankitest")[1])
    os.unlink(newname)
    e.limitTags = ['tag']
    e.exportInto(newname)
    d2 = Deck(newname, backup=False)
    assert d2.cardCount() == 2

@nose.with_setup(setup1)
def test_export_textcard():
    e = TextCardExporter(deck)
    f = unicode(tempfile.mkstemp(prefix="ankitest")[1])
    os.unlink(f)
    e.exportInto(f)
    e.includeTags = True
    e.exportInto(f)

@nose.with_setup(setup1)
def test_export_textnote():
    e = TextNoteExporter(deck)
    f = unicode(tempfile.mkstemp(prefix="ankitest")[1])
    os.unlink(f)
    e.exportInto(f)
    e.includeTags = True
    e.exportInto(f)
