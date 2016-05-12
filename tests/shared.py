import tempfile, os, shutil
from anki import Collection as aopen

def assertException(exception, func):
    found = False
    try:
        func()
    except exception:
        found = True
    assert found


# Creating new decks is expensive. Just do it once, and then spin off
# copies from the master.
def getEmptyCol():
    if len(getEmptyCol.master) == 0:
        (fd, nam) = tempfile.mkstemp(suffix=".anki2")
        os.close(fd)
        os.unlink(nam)
        col = aopen(nam)
        col.db.close()
        getEmptyCol.master = nam
    (fd, nam) = tempfile.mkstemp(suffix=".anki2")
    shutil.copy(getEmptyCol.master, nam)
    return aopen(nam)

getEmptyCol.master = ""

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
