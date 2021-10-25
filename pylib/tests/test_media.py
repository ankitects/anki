# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# coding: utf-8

import os
import tempfile

from tests.shared import getEmptyCol, testDir


# copying files to media folder
def test_add():
    col = getEmptyCol()
    dir = tempfile.mkdtemp(prefix="anki")
    path = os.path.join(dir, "foo.jpg")
    with open(path, "w") as note:
        note.write("hello")
    # new file, should preserve name
    assert col.media.add_file(path) == "foo.jpg"
    # adding the same file again should not create a duplicate
    assert col.media.add_file(path) == "foo.jpg"
    # but if it has a different sha1, it should
    with open(path, "w") as note:
        note.write("world")
    assert (
        col.media.add_file(path) == "foo-7c211433f02071597741e6ff5a8ea34789abbf43.jpg"
    )


def test_strings():
    col = getEmptyCol()
    mf = col.media.files_in_str
    mid = col.models.current()["id"]
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
    sp = col.media.strip
    assert sp("aoeu") == "aoeu"
    assert sp("aoeu[sound:foo.mp3]aoeu") == "aoeuaoeu"
    assert sp("a<img src=yo>oeu") == "aoeu"
    es = col.media.escape_media_filenames
    assert es("aoeu") == "aoeu"
    assert es("<img src='http://foo.com'>") == "<img src='http://foo.com'>"
    assert es('<img src="foo bar.jpg">') == '<img src="foo%20bar.jpg">'


def test_deckIntegration():
    col = getEmptyCol()
    # create a media dir
    col.media.dir()
    # put a file into it
    file = str(os.path.join(testDir, "support", "fake.png"))
    col.media.add_file(file)
    # add a note which references it
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "<img src='fake.png'>"
    col.addNote(note)
    # and one which references a non-existent file
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "<img src='fake2.png'>"
    col.addNote(note)
    # and add another file which isn't used
    with open(os.path.join(col.media.dir(), "foo.jpg"), "w") as note:
        note.write("test")
    # check media
    ret = col.media.check()
    assert ret.missing == ["fake2.png"]
    assert ret.unused == ["foo.jpg"]
