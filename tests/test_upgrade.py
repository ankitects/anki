# coding: utf-8

import datetime
from anki.consts import *
from shared import getUpgradeDeckPath, getEmptyDeck
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

def test_upgrade():
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
