# coding: utf-8

import datetime, shutil
from anki import Collection
from anki.consts import *
from shared import getUpgradeDeckPath, getEmptyDeck, testDir
from anki.upgrade import Upgrader
from anki.importing import Anki2Importer
from anki.utils import ids2str, checksum

def test_check():
    dst = getUpgradeDeckPath()
    u = Upgrader()
    assert u.check(dst)
    # if it's corrupted, will fail
    open(dst, "w+").write("foo")
    assert not u.check(dst)

def test_upgrade1():
    dst = getUpgradeDeckPath()
    csum = checksum(open(dst).read())
    u = Upgrader()
    deck = u.upgrade(dst)
    # src file must not have changed
    assert csum == checksum(open(dst).read())
    # creation time should have been adjusted
    d = datetime.datetime.fromtimestamp(deck.crt)
    assert d.hour == 4 and d.minute == 0
    # 3 new, 2 failed, 1 due
    deck.reset()
    deck.conf['counts'] = COUNT_REMAINING
    assert deck.sched.counts() == (3,4,1)
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

def test_upgrade2():
    p = "/tmp/alpha-upgrade.anki2"
    if os.path.exists(p):
        os.unlink(p)
    shutil.copy2(os.path.join(testDir, "support/anki2-alpha.anki2"), p)
    col = Collection(p)
    assert col.db.scalar("select ver from col") == SCHEMA_VERSION
