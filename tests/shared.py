import tempfile, os
from anki import Deck

def assertException(exception, func):
    found = False
    try:
        func()
    except exception:
        found = True
    assert found

def getEmptyDeck(**kwargs):
    (fd, nam) = tempfile.mkstemp(suffix=".anki")
    os.unlink(nam)
    return Deck(nam, **kwargs)

testDir = os.path.dirname(__file__)
