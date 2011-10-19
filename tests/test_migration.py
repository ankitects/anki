# coding: utf-8

from shared import getUpgradeDeckPath
from anki.migration.checker import check

def test_checker():
    dst = getUpgradeDeckPath()
    assert check(dst)
    # if it's corrupted, will fail
    open(dst, "w+").write("foo")
    assert not check(dst)

