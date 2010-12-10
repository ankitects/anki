# coding: utf-8

import tempfile, os, time
import anki.media as m
from anki import DeckStorage
from anki.stdmodels import BasicModel
from anki.utils import checksum

# uniqueness check
def test_unique():
    dir = tempfile.mkdtemp(prefix="anki")
    # new file
    n = "foo.jpg"
    new = os.path.basename(m.uniquePath(dir, n))
    assert new == n
    # duplicate file
    open(os.path.join(dir, n), "w").write("hello")
    n = "foo.jpg"
    new = os.path.basename(m.uniquePath(dir, n))
    assert new == "foo (1).jpg"
    # another duplicate
    open(os.path.join(dir, "foo (1).jpg"), "w").write("hello")
    n = "foo.jpg"
    new = os.path.basename(m.uniquePath(dir, n))
    assert new == "foo (2).jpg"

# copying files to media folder
def test_copy():
    deck = DeckStorage.Deck()
    dir = tempfile.mkdtemp(prefix="anki")
    path = os.path.join(dir, "foo.jpg")
    open(path, "w").write("hello")
    # new file
    assert m.copyToMedia(deck, path) == "foo.jpg"
    # dupe md5
    deck.s.statement("""
insert into media values (null, 'foo.jpg', 0, 0, :sum, '')""",
                     sum=checksum("hello"))
    path = os.path.join(dir, "bar.jpg")
    open(path, "w").write("hello")
    assert m.copyToMedia(deck, path) == "foo.jpg"

# media db
def test_db():
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    dir = tempfile.mkdtemp(prefix="anki")
    path = os.path.join(dir, "foo.jpg")
    open(path, "w").write("hello")
    # add a new fact that references it twice
    f = deck.newFact()
    f['Front'] = u"<img src='foo.jpg'>"
    f['Back'] = u"back [sound:foo.jpg]"
    deck.addFact(f)
    # 1 entry in the media db, with two references, and missing file
    assert deck.s.scalar("select count() from media") == 1
    assert deck.s.scalar("select size from media") == 2
    assert deck.s.scalar("select not originalPath from media")
    # copy to media folder & check db
    path = m.copyToMedia(deck, path)
    m.rebuildMediaDir(deck)
    # md5 should be set now
    assert deck.s.scalar("select count() from media") == 1
    assert deck.s.scalar("select size from media") == 2
    assert deck.s.scalar("select originalPath from media")
    # edit the fact to remove a reference
    f['Back'] = u""
    f.setModified(True, deck)
    deck.s.flush()
    assert deck.s.scalar("select count() from media") == 1
    assert deck.s.scalar("select size from media") == 1
    # remove the front reference too
    f['Front'] = u""
    f.setModified(True, deck)
    assert deck.s.scalar("select size from media") == 0
    # add the reference back
    f['Front'] = u"<img src='foo.jpg'>"
    f.setModified(True, deck)
    assert deck.s.scalar("select size from media") == 1
    # detect file modifications
    oldsum = deck.s.scalar("select originalPath from media")
    open(path, "w").write("world")
    m.rebuildMediaDir(deck)
    newsum = deck.s.scalar("select originalPath from media")
    assert newsum and newsum != oldsum
    # delete underlying file and check db
    os.unlink(path)
    m.rebuildMediaDir(deck)
    # md5 should be gone again
    assert deck.s.scalar("select count() from media") == 1
    assert deck.s.scalar("select not originalPath from media")
    # media db should pick up media defined via templates & bulk update
    f['Back'] = u"bar.jpg"
    f.setModified(True, deck)
    deck.s.flush()
    # modify template & regenerate
    assert deck.s.scalar("select count() from media") == 1
    assert deck.s.scalar("select sum(size) from media") == 1
    deck.currentModel.cardModels[0].aformat=u'<img src="{{{Back}}}">'
    deck.updateCardsFromModel(deck.currentModel)
    assert deck.s.scalar("select sum(size) from media") == 2
    assert deck.s.scalar("select count() from media") == 2
    deck.currentModel.cardModels[0].aformat=u'{{{Back}}}'
    deck.updateCardsFromModel(deck.currentModel)
    assert deck.s.scalar("select count() from media") == 2
    assert deck.s.scalar("select sum(size) from media") == 1
