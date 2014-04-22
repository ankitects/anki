# coding: utf-8

import datetime, shutil, tempfile
from anki import Collection
from anki.consts import *
from shared import getUpgradeDeckPath, testDir
from anki.upgrade import Upgrader
from anki.utils import  checksum

def test_check():
    dst = getUpgradeDeckPath()
    u = Upgrader()
    assert u.check(dst) == "ok"
    # if it's corrupted, will fail
    open(dst, "w+").write("foo")
    assert u.check(dst) == "invalid"
    # the upgrade should be able to fix non-fatal errors -
    # test with a deck that has cards with missing notes
    dst = getUpgradeDeckPath("anki12-broken.anki")
    assert "with missing fact" in u.check(dst)

def test_upgrade1():
    dst = getUpgradeDeckPath()
    csum = checksum(open(dst).read())
    u = Upgrader()
    u.check(dst)
    deck = u.upgrade()
    # src file must not have changed
    assert csum == checksum(open(dst).read())
    # creation time should have been adjusted
    d = datetime.datetime.fromtimestamp(deck.crt)
    assert d.hour == 4 and d.minute == 0
    # 3 new, 2 failed, 1 due
    deck.reset()
    deck.conf['counts'] = COUNT_REMAINING
    assert deck.sched.counts() == (3,2,1)
    # modifying each note should not cause new cards to be generated
    assert deck.cardCount() == 6
    for nid in deck.db.list("select id from notes"):
        note = deck.getNote(nid)
        note.flush()
    assert deck.cardCount() == 6
    # now's a good time to test the integrity check too
    deck.fixIntegrity()
    # c = deck.sched.getCard()
    # print "--q", c.q()
    # print
    # print "--a", c.a()

def test_upgrade1_due():
    dst = getUpgradeDeckPath("anki12-due.anki")
    u = Upgrader()
    u.check(dst)
    deck = u.upgrade()
    assert not deck.db.scalar("select 1 from cards where due != 1")

def test_invalid_ords():
    dst = getUpgradeDeckPath("invalid-ords.anki")
    u = Upgrader()
    u.check(dst)
    deck = u.upgrade()
    assert deck.db.scalar("select count() from cards where ord = 0") == 1
    assert deck.db.scalar("select count() from cards where ord = 1") == 1

def test_upgrade2():
    fd, p = tempfile.mkstemp(suffix=".anki2", prefix="alpha-upgrade")
    if os.path.exists(p):
        os.close(fd)
        os.unlink(p)
    shutil.copy2(os.path.join(testDir, "support/anki2-alpha.anki2"), p)
    col = Collection(p)
    assert col.db.scalar("select ver from col") == SCHEMA_VERSION
