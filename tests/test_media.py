# coding: utf-8

import tempfile, os, time
from anki import Deck
from anki.utils import checksum
from shared import getEmptyDeck, testDir

# uniqueness check
def test_unique():
    d = getEmptyDeck()
    dir = tempfile.mkdtemp(prefix="anki")
    # new file
    n = "foo.jpg"
    new = os.path.basename(d.media.uniquePath(dir, n))
    assert new == n
    # duplicate file
    open(os.path.join(dir, n), "w").write("hello")
    n = "foo.jpg"
    new = os.path.basename(d.media.uniquePath(dir, n))
    assert new == "foo (1).jpg"
    # another duplicate
    open(os.path.join(dir, "foo (1).jpg"), "w").write("hello")
    n = "foo.jpg"
    new = os.path.basename(d.media.uniquePath(dir, n))
    assert new == "foo (2).jpg"

# copying files to media folder
def test_copy():
    d = getEmptyDeck()
    dir = tempfile.mkdtemp(prefix="anki")
    path = os.path.join(dir, "foo.jpg")
    open(path, "w").write("hello")
    # new file
    assert d.media.addFile(path) == "foo.jpg"
    # dupe md5
    path = os.path.join(dir, "bar.jpg")
    open(path, "w").write("hello")
    assert d.media.addFile(path) == "foo.jpg"

# media db
def test_db():
    deck = getEmptyDeck()
    dir = tempfile.mkdtemp(prefix="anki")
    path = os.path.join(dir, "foo.jpg")
    open(path, "w").write("hello")
    # add a new fact that references it twice
    f = deck.newFact()
    f['Front'] = u"<img src='foo.jpg'>"
    f['Back'] = u"back [sound:foo.jpg]"
    deck.addFact(f)
    # 1 entry in the media db, and no checksum
    assert deck.db.scalar("select count() from media") == 1
    assert not deck.db.scalar("select group_concat(csum, '') from media")
    # copy to media folder
    path = deck.media.addFile(path)
    # md5 should be set now
    assert deck.db.scalar("select count() from media") == 1
    assert deck.db.scalar("select group_concat(csum, '') from media")
    # detect file modifications
    oldsum = deck.db.scalar("select csum from media")
    open(path, "w").write("world")
    deck.media.rebuildMediaDir()
    newsum = deck.db.scalar("select csum from media")
    assert newsum and newsum != oldsum
    # delete underlying file and check db
    os.unlink(path)
    deck.media.rebuildMediaDir()
    # md5 should be gone again
    assert deck.db.scalar("select count() from media") == 1
    assert deck.db.scalar("select not csum from media")
    # media db should pick up media defined via templates & bulk update
    f['Back'] = u"bar.jpg"
    f.flush()
    # modify template & regenerate
    assert deck.db.scalar("select count() from media") == 1
    m = deck.currentModel()
    m.templates[0].afmt=u'<img src="{{{Back}}}">'
    m.flush()
    m.updateCache()
    assert deck.db.scalar("select count() from media") == 2

def test_deckIntegration():
    deck = getEmptyDeck()
    # create a media dir
    deck.media.mediaDir(create=True)
    # put a file into it
    file = unicode(os.path.join(testDir, "deck/fake.png"))
    deck.media.addFile(file)
    print "todo: check media copied on rename"
