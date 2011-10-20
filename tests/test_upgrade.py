# coding: utf-8

import datetime
from anki.consts import *
from shared import getUpgradeDeckPath, getEmptyDeck
from anki.upgrade import Upgrader
from anki.importing import Anki2Importer
from anki.utils import ids2str

def test_check():
    dst = getUpgradeDeckPath()
    u = Upgrader()
    assert u.check(dst)
    # if it's corrupted, will fail
    open(dst, "w+").write("foo")
    assert not u.check(dst)

def test_upgrade():
    dst = getUpgradeDeckPath()
    u = Upgrader()
    print "upgrade to", dst
    deck = u.upgrade(dst)
    # creation time should have been adjusted
    d = datetime.datetime.fromtimestamp(deck.crt)
    assert d.hour == 4 and d.minute == 0
    # 3 new, 2 failed, 1 due
    deck.reset()
    deck.conf['counts'] = COUNT_REMAINING
    assert deck.sched.cardCounts() == (3,2,1)
    # now's a good time to test the integrity check too
    deck.fixIntegrity()

def test_import():
    # get the deck to import
    tmp = getUpgradeDeckPath()
    u = Upgrader()
    src = u.upgrade(tmp)
    srcpath = src.path
    srcFacts = src.factCount()
    srcCards = src.cardCount()
    srcRev = src.db.scalar("select count() from revlog")
    # add a media file for testing
    open(os.path.join(src.media.dir(), "foo.jpg"), "w").write("foo")
    src.close()
    # create a new empty deck
    dst = getEmptyDeck()
    # import src into dst
    imp = Anki2Importer(dst, srcpath)
    imp.run()
    def check():
        assert dst.factCount() == srcFacts
        assert dst.cardCount() == srcCards
        assert srcRev == dst.db.scalar("select count() from revlog")
        mids = [int(x) for x in dst.models.models.keys()]
        assert not dst.db.scalar(
            "select count() from facts where mid not in "+ids2str(mids))
        assert not dst.db.scalar(
            "select count() from cards where fid not in (select id from facts)")
        assert not dst.db.scalar(
            "select count() from revlog where cid not in (select id from cards)")
    check()
    # importing should be idempotent
    imp.run()
    check()
    print dst.path
