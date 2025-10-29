# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import shutil
import tempfile
import time

from anki.collection import Collection as aopen
from anki.config import Config

# Between 2-4AM, shift the time back so test assumptions hold.
lt = time.localtime()
if lt.tm_hour >= 2 and lt.tm_hour < 4:
    orig_time = time.time

    def adjusted_time():
        return orig_time() - 60 * 60 * 2

    time.time = adjusted_time
else:
    orig_time = None


def assertException(exception, func):
    found = False
    try:
        func()
    except exception:
        found = True
    assert found


# Creating new decks is expensive. Just do it once, and then spin off
# copies from the master.
_emptyCol: str | None = None


def getEmptyCol():
    global _emptyCol
    if not _emptyCol:
        (fd, path) = tempfile.mkstemp(suffix=".anki2")
        os.close(fd)
        os.unlink(path)
        col = aopen(path)
        col.close(downgrade=False)
        _emptyCol = path
    (fd, path) = tempfile.mkstemp(suffix=".anki2")
    shutil.copy(_emptyCol, path)
    col = aopen(path)
    # Disable FSRS for legacy scheduler tests
    col.set_config_bool(Config.Bool.FSRS, False, undoable=False)
    return col


# Fallback for when the DB needs options passed in.
def getEmptyDeckWith(**kwargs):
    (fd, nam) = tempfile.mkstemp(suffix=".anki2")
    os.close(fd)
    os.unlink(nam)
    return aopen(nam, **kwargs)


def getUpgradeDeckPath(name="anki12.anki"):
    src = os.path.join(testDir, "support", name)
    (fd, dst) = tempfile.mkstemp(suffix=".anki2")
    shutil.copy(src, dst)
    return dst


testDir = os.path.dirname(__file__)


def errorsAfterMidnight(func):
    def wrapper():
        lt = time.localtime()
        if lt.tm_hour < 4:
            print("test disabled around cutoff", func)
        else:
            func()

    return wrapper


def isNearCutoff():
    return orig_time is not None
