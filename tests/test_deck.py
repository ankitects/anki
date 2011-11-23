# coding: utf-8

import os, re, datetime
from tests.shared import assertException, getEmptyDeck, testDir, \
    getUpgradeDeckPath
from anki.stdmodels import addBasicModel
from anki.consts import *

from anki import Deck

newPath = None
newMod = None

def test_create():
    global newPath, newMod
    path = "/tmp/test_attachNew.anki2"
    try:
        os.unlink(path)
    except OSError:
        pass
    deck = Deck(path)
    # for open()
    newPath = deck.path
    deck.close()
    newMod = deck.mod
    del deck

def test_open():
    deck = Deck(newPath)
    assert deck.mod == newMod
    deck.close()

def test_openReadOnly():
    # non-writeable dir
    assertException(Exception,
                    lambda: Deck("/attachroot.anki2"))
    # reuse tmp file from before, test non-writeable file
    os.chmod(newPath, 0)
    assertException(Exception,
                    lambda: Deck(newPath))
    os.chmod(newPath, 0666)
    os.unlink(newPath)

def test_noteAddDelete():
    deck = getEmptyDeck()
    # add a note
    f = deck.newNote()
    f['Front'] = u"one"; f['Back'] = u"two"
    n = deck.addNote(f)
    assert n == 1
    # test multiple cards - add another template
    m = deck.models.current(); mm = deck.models
    t = mm.newTemplate("Reverse")
    t['qfmt'] = "{{Back}}"
    t['afmt'] = "{{Front}}"
    mm.addTemplate(m, t)
    mm.save(m)
    # the default save doesn't generate cards
    assert deck.cardCount() == 1
    # but when templates are edited such as in the card layout screen, it
    # should generate cards on close
    mm.save(m, gencards=True)
    assert deck.cardCount() == 2
    # creating new notes should use both cards
    f = deck.newNote()
    f['Front'] = u"three"; f['Back'] = u"four"
    n = deck.addNote(f)
    assert n == 2
    assert deck.cardCount() == 4
    # check q/a generation
    c0 = f.cards()[0]
    assert re.sub("</?.+?>", "", c0.q()) == u"three"
    # it should not be a duplicate
    for p in f.problems():
        assert not p
    # now let's make a duplicate and test uniqueness
    f2 = deck.newNote()
    f2.model()['flds'][1]['req'] = True
    f2['Front'] = u"one"; f2['Back'] = u""
    p = f2.problems()
    assert p[0] == "unique"
    assert p[1] == "required"
    # try delete the first card
    cards = f.cards()
    id1 = cards[0].id; id2 = cards[1].id
    assert deck.cardCount() == 4
    assert deck.noteCount() == 2
    deck.remCards([id1])
    assert deck.cardCount() == 3
    assert deck.noteCount() == 2
    # and the second should clear the note
    deck.remCards([id2])
    assert deck.cardCount() == 2
    assert deck.noteCount() == 1

def test_fieldChecksum():
    deck = getEmptyDeck()
    f = deck.newNote()
    f['Front'] = u"new"; f['Back'] = u"new2"
    deck.addNote(f)
    assert deck.db.scalar(
        "select csum from nsums") == int("c2a6b03f", 16)
    # empty field should have no checksum
    f['Front'] = u""
    f.flush()
    assert deck.db.scalar(
        "select count() from nsums") == 0
    # changing the val should change the checksum
    f['Front'] = u"newx"
    f.flush()
    assert deck.db.scalar(
        "select csum from nsums") == int("302811ae", 16)
    # turning off unique and modifying the note should delete the sum
    m = f.model()
    m['flds'][0]['uniq'] = False
    deck.models.save(m)
    f.flush()
    assert deck.db.scalar(
        "select count() from nsums") == 0
    # and turning on both should ensure two checksums generated
    m['flds'][0]['uniq'] = True
    m['flds'][1]['uniq'] = True
    deck.models.save(m)
    f.flush()
    assert deck.db.scalar(
        "select count() from nsums") == 2

def test_selective():
    deck = getEmptyDeck()
    f = deck.newNote()
    f['Front'] = u"1"; f.tags = ["one", "three"]
    deck.addNote(f)
    f = deck.newNote()
    f['Front'] = u"2"; f.tags = ["two", "three", "four"]
    deck.addNote(f)
    f = deck.newNote()
    f['Front'] = u"3"; f.tags = ["one", "two", "three", "four"]
    deck.addNote(f)
    assert len(deck.tags.selTagNids(["one"], [])) == 2
    assert len(deck.tags.selTagNids(["three"], [])) == 3
    assert len(deck.tags.selTagNids([], ["three"])) == 0
    assert len(deck.tags.selTagNids(["one"], ["three"])) == 0
    assert len(deck.tags.selTagNids(["one"], ["two"])) == 1
    assert len(deck.tags.selTagNids(["two", "three"], [])) == 3
    assert len(deck.tags.selTagNids(["two", "three"], ["one"])) == 1
    assert len(deck.tags.selTagNids(["one", "three"], ["two", "four"])) == 1
    deck.tags.setGroupForTags(["three"], [], 3)
    assert deck.db.scalar("select count() from cards where gid = 3") == 3
    deck.tags.setGroupForTags(["one"], [], 2)
    assert deck.db.scalar("select count() from cards where gid = 2") == 2

def test_addDelTags():
    deck = getEmptyDeck()
    f = deck.newNote()
    f['Front'] = u"1"
    deck.addNote(f)
    f2 = deck.newNote()
    f2['Front'] = u"2"
    deck.addNote(f2)
    # adding for a given id
    deck.tags.bulkAdd([f.id], "foo")
    f.load(); f2.load()
    assert "foo" in f.tags
    assert "foo" not in f2.tags
    # should be canonified
    deck.tags.bulkAdd([f.id], "foo aaa")
    f.load()
    assert f.tags[0] == "aaa"
    assert len(f.tags) == 2

def test_timestamps():
    deck = getEmptyDeck()
    assert len(deck.models.models) == 2
    for i in range(100):
        addBasicModel(deck)
    assert len(deck.models.models) == 102

