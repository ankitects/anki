# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# coding: utf-8

import os
import tempfile
from typing import Any

from anki.collection import Collection as aopen
from anki.dbproxy import emulate_named_args
from anki.lang import TR, without_unicode_isolation
from anki.stdmodels import _legacy_add_basic_model, get_stock_notetypes
from anki.utils import is_win
from tests.shared import assertException, getEmptyCol


def test_create_open():
    (fd, path) = tempfile.mkstemp(suffix=".anki2", prefix="test_attachNew")
    try:
        os.close(fd)
        os.unlink(path)
    except OSError:
        pass
    col = aopen(path)
    # for open()
    newPath = col.path
    newMod = col.mod
    col.close()
    del col

    # reopen
    col = aopen(newPath)
    assert col.mod == newMod
    col.close()

    # non-writeable dir
    if is_win:
        dir = "c:\root.anki2"
    else:
        dir = "/attachroot.anki2"
    assertException(Exception, lambda: aopen(dir))
    # reuse tmp file from before, test non-writeable file
    os.chmod(newPath, 0)
    assertException(Exception, lambda: aopen(newPath))
    os.chmod(newPath, 0o666)
    os.unlink(newPath)


def test_noteAddDelete():
    col = getEmptyCol()
    # add a note
    note = col.newNote()
    note["Front"] = "one"
    note["Back"] = "two"
    n = col.addNote(note)
    assert n == 1
    # test multiple cards - add another template
    m = col.models.current()
    mm = col.models
    t = mm.new_template("Reverse")
    t["qfmt"] = "{{Back}}"
    t["afmt"] = "{{Front}}"
    mm.add_template(m, t)
    mm.save(m)
    assert col.card_count() == 2
    # creating new notes should use both cards
    note = col.newNote()
    note["Front"] = "three"
    note["Back"] = "four"
    n = col.addNote(note)
    assert n == 2
    assert col.card_count() == 4
    # check q/a generation
    c0 = note.cards()[0]
    assert "three" in c0.question()
    # it should not be a duplicate
    assert not note.fields_check()
    # now let's make a duplicate
    note2 = col.newNote()
    note2["Front"] = "one"
    note2["Back"] = ""
    assert note2.fields_check()
    # empty first field should not be permitted either
    note2["Front"] = " "
    assert note2.fields_check()


def test_fieldChecksum():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "new"
    note["Back"] = "new2"
    col.addNote(note)
    assert col.db.scalar("select csum from notes") == int("c2a6b03f", 16)
    # changing the val should change the checksum
    note["Front"] = "newx"
    note.flush()
    assert col.db.scalar("select csum from notes") == int("302811ae", 16)


def test_addDelTags():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "1"
    col.addNote(note)
    note2 = col.newNote()
    note2["Front"] = "2"
    col.addNote(note2)
    # adding for a given id
    col.tags.bulk_add([note.id], "foo")
    note.load()
    note2.load()
    assert "foo" in note.tags
    assert "foo" not in note2.tags
    # should be canonified
    col.tags.bulk_add([note.id], "foo aaa")
    note.load()
    assert note.tags[0] == "aaa"
    assert len(note.tags) == 2


def test_timestamps():
    col = getEmptyCol()
    assert len(col.models.all_names_and_ids()) == len(get_stock_notetypes(col))
    for i in range(100):
        _legacy_add_basic_model(col)
    assert len(col.models.all_names_and_ids()) == 100 + len(get_stock_notetypes(col))


def test_furigana():
    col = getEmptyCol()
    mm = col.models
    m = mm.current()
    # filter should work
    m["tmpls"][0]["qfmt"] = "{{kana:Front}}"
    mm.save(m)
    n = col.newNote()
    n["Front"] = "foo[abc]"
    col.addNote(n)
    c = n.cards()[0]
    assert c.question().endswith("abc")
    # and should avoid sound
    n["Front"] = "foo[sound:abc.mp3]"
    n.flush()
    assert "anki:play" in c.question(reload=True)
    # it shouldn't throw an error while people are editing
    m["tmpls"][0]["qfmt"] = "{{kana:}}"
    mm.save(m)
    c.question(reload=True)


def test_translate():
    col = getEmptyCol()
    no_uni = without_unicode_isolation

    assert (
        col.tr.card_template_rendering_front_side_problem()
        == "Front template has a problem:"
    )
    assert no_uni(col.tr.statistics_reviews(reviews=1)) == "1 review"
    assert no_uni(col.tr.statistics_reviews(reviews=2)) == "2 reviews"


def test_db_named_args(capsys):
    sql = "select a, 2+:test5 from b where arg =:foo and x = :test5"
    args: tuple = tuple()
    kwargs = dict(test5=5, foo="blah")

    s, a = emulate_named_args(sql, args, kwargs)
    assert s == "select a, 2+?1 from b where arg =?2 and x = ?1"
    assert a == [5, "blah"]

    # swallow the warning
    _ = capsys.readouterr()
