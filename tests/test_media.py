# coding: utf-8

import tempfile, os, time
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
    mf = d.media.filesInStr
    mid = d.models.models.keys()[0]
    assert mf(mid, "aoeu") == []
    assert mf(mid, "aoeu<img src='foo.jpg'>ao") == ["foo.jpg"]
    assert mf(mid, "aoeu<img src='foo.jpg' style='test'>ao") == ["foo.jpg"]
    assert mf(mid, "aoeu<img src='foo.jpg'><img src=\"bar.jpg\">ao") == [
            "foo.jpg", "bar.jpg"]
    assert mf(mid, "aoeu<img src=foo.jpg style=bar>ao") == ["foo.jpg"]
    assert mf(mid, "<img src=one><img src=two>") == ["one", "two"]
    assert mf(mid, "aoeu<img src=\"foo.jpg\">ao") == ["foo.jpg"]
    assert mf(mid, "aoeu<img src=\"foo.jpg\"><img class=yo src=fo>ao") == [
            "foo.jpg", "fo"]
    assert mf(mid, "aou[sound:foo.mp3]aou") == ["foo.mp3"]
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
    d.media.dir()
    # put a file into it
    file = unicode(os.path.join(testDir, "support/fake.png"))
    d.media.addFile(file)
    # add a note which references it
    f = d.newNote()
    f['Front'] = u"one"; f['Back'] = u"<img src='fake.png'>"
    d.addNote(f)
    # and one which references a non-existent file
    f = d.newNote()
    f['Front'] = u"one"; f['Back'] = u"<img src='fake2.png'>"
    d.addNote(f)
    # and add another file which isn't used
    open(os.path.join(d.media.dir(), "foo.jpg"), "wb").write("test")
    # check media
    ret = d.media.check()
    assert ret[0] == ["fake2.png"]
    assert ret[1] == ["foo.jpg"]

def test_changes():
    d = getEmptyDeck()
    assert d.media._changed()
    def added():
        return d.media.db.execute("select fname from log where type = 0")
    assert not list(added())
    assert not list(d.media.removed())
    # add a file
    dir = tempfile.mkdtemp(prefix="anki")
    path = os.path.join(dir, "foo.jpg")
    open(path, "w").write("hello")
    time.sleep(1)
    path = d.media.addFile(path)
    # should have been logged
    d.media.findChanges()
    assert list(added())
    assert not list(d.media.removed())
    # if we modify it, the cache won't notice
    time.sleep(1)
    open(path, "w").write("world")
    assert len(list(added())) == 1
    assert not list(d.media.removed())
    # but if we add another file, it will
    time.sleep(1)
    open(path+"2", "w").write("yo")
    d.media.findChanges()
    assert len(list(added())) == 2
    assert not list(d.media.removed())
    # deletions should get noticed too
    time.sleep(1)
    os.unlink(path+"2")
    d.media.findChanges()
    assert len(list(added())) == 1
    assert len(list(d.media.removed())) == 1
