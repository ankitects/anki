# coding: utf-8

import os
import tempfile

from .shared import getEmptyCol, testDir


# copying files to media folder
def test_add():
    d = getEmptyCol()
    dir = tempfile.mkdtemp(prefix="anki")
    path = os.path.join(dir, "foo.jpg")
    with open(path, "w") as f:
        f.write("hello")
    # new file, should preserve name
    assert d.media.addFile(path) == "foo.jpg"
    # adding the same file again should not create a duplicate
    assert d.media.addFile(path) == "foo.jpg"
    # but if it has a different sha1, it should
    with open(path, "w") as f:
        f.write("world")
    assert d.media.addFile(path) == "foo-7c211433f02071597741e6ff5a8ea34789abbf43.jpg"


def test_strings():
    d = getEmptyCol()
    mf = d.media.filesInStr
    mid = d.models.current()["id"]
    assert mf(mid, "aoeu") == []
    assert mf(mid, "aoeu<img src='foo.jpg'>ao") == ["foo.jpg"]
    assert mf(mid, "aoeu<img src='foo.jpg' style='test'>ao") == ["foo.jpg"]
    assert mf(mid, "aoeu<img src='foo.jpg'><img src=\"bar.jpg\">ao") == [
        "foo.jpg",
        "bar.jpg",
    ]
    assert mf(mid, "aoeu<img src=foo.jpg style=bar>ao") == ["foo.jpg"]
    assert mf(mid, "<img src=one><img src=two>") == ["one", "two"]
    assert mf(mid, 'aoeu<img src="foo.jpg">ao') == ["foo.jpg"]
    assert mf(mid, 'aoeu<img src="foo.jpg"><img class=yo src=fo>ao') == [
        "foo.jpg",
        "fo",
    ]
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
    d = getEmptyCol()
    # create a media dir
    d.media.dir()
    # put a file into it
    file = str(os.path.join(testDir, "support/fake.png"))
    d.media.addFile(file)
    # add a note which references it
    f = d.newNote()
    f["Front"] = "one"
    f["Back"] = "<img src='fake.png'>"
    d.addNote(f)
    # and one which references a non-existent file
    f = d.newNote()
    f["Front"] = "one"
    f["Back"] = "<img src='fake2.png'>"
    d.addNote(f)
    # and add another file which isn't used
    with open(os.path.join(d.media.dir(), "foo.jpg"), "w") as f:
        f.write("test")
    # check media
    ret = d.media.check()
    assert ret.missing == ["fake2.png"]
    assert ret.unused == ["foo.jpg"]
