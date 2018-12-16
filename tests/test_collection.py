# coding: utf-8

import os, tempfile
from tests.shared import assertException, getEmptyCol
from anki.stdmodels import addBasicModel, models

from anki import Collection as aopen

newPath = None
newMod = None

def test_create_open():
    global newPath, newMod
    (fd, path) = tempfile.mkstemp(suffix=".anki2", prefix="test_attachNew")
    try:
        os.close(fd)
        os.unlink(path)
    except OSError:
        pass
    deck = aopen(path)
    # for open()
    newPath = deck.path
    deck.close()
    newMod = deck.mod
    del deck

    # reopen
    deck = aopen(newPath)
    assert deck.mod == newMod
    deck.close()

    # non-writeable dir
    assertException(Exception,
                    lambda: aopen("/attachroot.anki2"))
    # reuse tmp file from before, test non-writeable file
    os.chmod(newPath, 0)
    assertException(Exception,
                    lambda: aopen(newPath))
    os.chmod(newPath, 0o666)
    os.unlink(newPath)

def test_noteAddDelete():
    deck = getEmptyCol()
    # add a note
    f = deck.newNote()
    f['Front'] = "one"; f['Back'] = "two"
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
    mm.save(m, templates=True)
    assert deck.cardCount() == 2
    # creating new notes should use both cards
    f = deck.newNote()
    f['Front'] = "three"; f['Back'] = "four"
    n = deck.addNote(f)
    assert n == 2
    assert deck.cardCount() == 4
    # check q/a generation
    c0 = f.cards()[0]
    assert "three" in c0.q()
    # it should not be a duplicate
    assert not f.dupeOrEmpty()
    # now let's make a duplicate
    f2 = deck.newNote()
    f2['Front'] = "one"; f2['Back'] = ""
    assert f2.dupeOrEmpty()
    # empty first field should not be permitted either
    f2['Front'] = " "
    assert f2.dupeOrEmpty()

def test_fieldChecksum():
    deck = getEmptyCol()
    f = deck.newNote()
    f['Front'] = "new"; f['Back'] = "new2"
    deck.addNote(f)
    assert deck.db.scalar(
        "select csum from notes") == int("c2a6b03f", 16)
    # changing the val should change the checksum
    f['Front'] = "newx"
    f.flush()
    assert deck.db.scalar(
        "select csum from notes") == int("302811ae", 16)

def test_addDelTags():
    deck = getEmptyCol()
    f = deck.newNote()
    f['Front'] = "1"
    deck.addNote(f)
    f2 = deck.newNote()
    f2['Front'] = "2"
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
    deck = getEmptyCol()
    assert len(deck.models.models) == len(models)
    for i in range(100):
        addBasicModel(deck)
    assert len(deck.models.models) == 100 + len(models)

def test_furigana():
    deck = getEmptyCol()
    mm = deck.models
    m = mm.current()
    # filter should work
    m['tmpls'][0]['qfmt'] = '{{kana:Front}}'
    mm.save(m)
    n = deck.newNote()
    n['Front'] = 'foo[abc]'
    deck.addNote(n)
    c = n.cards()[0]
    assert c.q().endswith("abc")
    # and should avoid sound
    n['Front'] = 'foo[sound:abc.mp3]'
    n.flush()
    assert "sound:" in c.q(reload=True)
    # it shouldn't throw an error while people are editing
    m['tmpls'][0]['qfmt'] = '{{kana:}}'
    mm.save(m)
    c.q(reload=True)
