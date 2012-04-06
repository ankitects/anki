import tempfile, os, shutil
from anki import Collection as aopen

def assertException(exception, func):
    found = False
    try:
        func()
    except exception:
        found = True
    assert found

def getEmptyDeck(**kwargs):
    (fd, nam) = tempfile.mkstemp(suffix=".anki2")
    os.unlink(nam)
    return aopen(nam, **kwargs)

def getUpgradeDeckPath(name="anki12.anki"):
    src = os.path.join(testDir, "support", name)
    (fd, dst) = tempfile.mkstemp(suffix=".anki2")
    shutil.copy(src, dst)
    return dst

testDir = os.path.dirname(__file__)
