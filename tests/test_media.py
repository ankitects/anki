# coding: utf-8

import tempfile, os, time
from anki import Deck
from anki.utils import checksum
from shared import getEmptyDeck, testDir

# copying files to media folder
def test_add():
    d = getEmptyDeck()
    dir = tempfile.mkdtemp(prefix="anki")
    path = os.path.join(dir, "foo.jpg")
    open(path, "w").write("hello")
    # new file, should preserve name
    assert d.media.addFile(path) == "foo.jpg"
    # adding the same file again should not create a duplicate
    assert d.media.addFile(path) == "foo.jpg"
    # but if it has a different md5, it should
    open(path, "w").write("world")
    assert d.media.addFile(path) == "foo (1).jpg"

def test_strings():
    d = getEmptyDeck()
    mf = d.media.mediaFiles
    assert mf("aoeu") == []
    assert mf("aoeu<img src='foo.jpg'>ao") == ["foo.jpg"]
    assert mf("aoeu<img src=foo bar.jpg>ao") == ["foo bar.jpg"]
    assert mf("aoeu<img src=\"foo.jpg\">ao") == ["foo.jpg"]
    assert mf("aoeu<img src=\"foo.jpg\"><img class=yo src=fo>ao") == [
            "foo.jpg", "fo"]
    assert mf("aou[sound:foo.mp3]aou") == ["foo.mp3"]
    sp = d.media.strip
    assert sp("aoeu") == "aoeu"
    assert sp("aoeu[sound:foo.mp3]aoeu") == "aoeuaoeu"
    assert sp("a<img src=yo>oeu") == "aoeu"
    es = d.media.escapeImages
    assert es("aoeu") == "aoeu"
    assert es("<img src='http://foo.com'>") == "<img src='http://foo.com'>"
    assert es('<img src="foo bar.jpg">') == '<img src="foo%20bar.jpg">'

def test_deckIntegration():
    d = getEmptyDeck()
    # create a media dir
    d.media.dir(create=True)
    # put a file into it
    file = unicode(os.path.join(testDir, "support/fake.png"))
    d.media.addFile(file)
    # add a fact which references it
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"<img src='fake.png'>"
    d.addFact(f)
    # and one which references a non-existent file
    f = d.newFact()
    f['Front'] = u"one"; f['Back'] = u"<img src='fake2.png'>"
    d.addFact(f)
    # and add another file which isn't used
    open(os.path.join(d.media.dir(), "foo.jpg"), "wb").write("test")
    # check media
    ret = d.media.check()
    assert ret[0] == ["fake2.png"]
    assert ret[1] == ["foo.jpg"]
