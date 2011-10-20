# coding: utf-8

import datetime
from anki.consts import *
from shared import getUpgradeDeckPath
from anki.migration.checker import check
from anki.migration.upgrader import Upgrader

def test_checker():
    dst = getUpgradeDeckPath()
    assert check(dst)
    # if it's corrupted, will fail
    open(dst, "w+").write("foo")
    assert not check(dst)

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



