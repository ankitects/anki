# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# coding: utf-8
import pytest

from anki.browser import BrowserConfig
from anki.consts import *
from tests.shared import getEmptyCol, isNearCutoff


class DummyCollection:
    def weakref(self):
        return None


def test_find_cards():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "dog"
    note["Back"] = "cat"
    note.tags.append("monkey animal_1 * %")
    col.addNote(note)
    n1id = note.id
    firstCardId = note.cards()[0].id
    note = col.newNote()
    note["Front"] = "goats are fun"
    note["Back"] = "sheep"
    note.tags.append("sheep goat horse animal11")
    col.addNote(note)
    n2id = note.id
    note = col.newNote()
    note["Front"] = "cat"
    note["Back"] = "sheep"
    col.addNote(note)
    catCard = note.cards()[0]
    m = col.models.current()
    m = col.models.copy(m)
    mm = col.models
    t = mm.new_template("Reverse")
    t["qfmt"] = "{{Back}}"
    t["afmt"] = "{{Front}}"
    mm.add_template(m, t)
    mm.save(m)
    note = col.newNote()
    note["Front"] = "test"
    note["Back"] = "foo bar"
    col.addNote(note)
    latestCardIds = [c.id for c in note.cards()]
    # tag searches
    assert len(col.find_cards("tag:*")) == 5
    assert len(col.find_cards("tag:\\*")) == 1
    assert len(col.find_cards("tag:%")) == 1
    assert len(col.find_cards("tag:sheep_goat")) == 0
    assert len(col.find_cards('"tag:sheep goat"')) == 0
    assert len(col.find_cards('"tag:* *"')) == 0
    assert len(col.find_cards("tag:animal_1")) == 2
    assert len(col.find_cards("tag:animal\\_1")) == 1
    assert not col.find_cards("tag:donkey")
    assert len(col.find_cards("tag:sheep")) == 1
    assert len(col.find_cards("tag:sheep tag:goat")) == 1
    assert len(col.find_cards("tag:sheep tag:monkey")) == 0
    assert len(col.find_cards("tag:monkey")) == 1
    assert len(col.find_cards("tag:sheep -tag:monkey")) == 1
    assert len(col.find_cards("-tag:sheep")) == 4
    col.tags.bulk_add(col.db.list("select id from notes"), "foo bar")
    assert len(col.find_cards("tag:foo")) == len(col.find_cards("tag:bar")) == 5
    col.tags.bulk_remove(col.db.list("select id from notes"), "foo")
    assert len(col.find_cards("tag:foo")) == 0
    assert len(col.find_cards("tag:bar")) == 5
    # text searches
    assert len(col.find_cards("cat")) == 2
    assert len(col.find_cards("cat -dog")) == 1
    assert len(col.find_cards("cat -dog")) == 1
    assert len(col.find_cards("are goats")) == 1
    assert len(col.find_cards('"are goats"')) == 0
    assert len(col.find_cards('"goats are"')) == 1
    # card states
    c = note.cards()[0]
    c.queue = c.type = CARD_TYPE_REV
    assert col.find_cards("is:review") == []
    c.flush()
    assert col.find_cards("is:review") == [c.id]
    assert col.find_cards("is:due") == []
    c.due = 0
    c.queue = QUEUE_TYPE_REV
    c.flush()
    assert col.find_cards("is:due") == [c.id]
    assert len(col.find_cards("-is:due")) == 4
    c.queue = QUEUE_TYPE_SUSPENDED
    # ensure this card gets a later mod time
    c.flush()
    col.db.execute("update cards set mod = mod + 1 where id = ?", c.id)
    assert col.find_cards("is:suspended") == [c.id]
    # nids
    assert col.find_cards("nid:54321") == []
    assert len(col.find_cards(f"nid:{note.id}")) == 2
    assert len(col.find_cards(f"nid:{n1id},{n2id}")) == 2
    # templates
    assert len(col.find_cards("card:foo")) == 0
    assert len(col.find_cards('"card:card 1"')) == 4
    assert len(col.find_cards("card:reverse")) == 1
    assert len(col.find_cards("card:1")) == 4
    assert len(col.find_cards("card:2")) == 1
    # fields
    assert len(col.find_cards("front:dog")) == 1
    assert len(col.find_cards("-front:dog")) == 4
    assert len(col.find_cards("front:sheep")) == 0
    assert len(col.find_cards("back:sheep")) == 2
    assert len(col.find_cards("-back:sheep")) == 3
    assert len(col.find_cards("front:do")) == 0
    assert len(col.find_cards("front:*")) == 5
    # ordering
    col.conf["sortType"] = "noteCrt"
    assert col.find_cards("front:*", order=True)[-1] in latestCardIds
    assert col.find_cards("", order=True)[-1] in latestCardIds
    col.conf["sortType"] = "noteFld"
    assert col.find_cards("", order=True)[0] == catCard.id
    assert col.find_cards("", order=True)[-1] in latestCardIds
    col.conf["sortType"] = "cardMod"
    assert col.find_cards("", order=True)[-1] in latestCardIds
    assert col.find_cards("", order=True)[0] == firstCardId
    col.set_config(BrowserConfig.CARDS_SORT_BACKWARDS_KEY, True)
    assert col.find_cards("", order=True)[0] in latestCardIds
    assert (
        col.find_cards("", order=col.get_browser_column("cardDue"), reverse=False)[0]
        == firstCardId
    )
    assert (
        col.find_cards("", order=col.get_browser_column("cardDue"), reverse=True)[0]
        != firstCardId
    )
    # model
    assert len(col.find_cards("note:basic")) == 3
    assert len(col.find_cards("-note:basic")) == 2
    assert len(col.find_cards("-note:foo")) == 5
    # col
    assert len(col.find_cards("deck:default")) == 5
    assert len(col.find_cards("-deck:default")) == 0
    assert len(col.find_cards("-deck:foo")) == 5
    assert len(col.find_cards("deck:def*")) == 5
    assert len(col.find_cards("deck:*EFAULT")) == 5
    assert len(col.find_cards("deck:*cefault")) == 0
    # full search
    note = col.newNote()
    note["Front"] = "hello<b>world</b>"
    note["Back"] = "abc"
    col.addNote(note)
    # as it's the sort field, it matches
    assert len(col.find_cards("helloworld")) == 2
    # assert len(col.find_cards("helloworld", full=True)) == 2
    # if we put it on the back, it won't
    (note["Front"], note["Back"]) = (note["Back"], note["Front"])
    note.flush()
    assert len(col.find_cards("helloworld")) == 0
    # assert len(col.find_cards("helloworld", full=True)) == 2
    # assert len(col.find_cards("back:helloworld", full=True)) == 2
    # searching for an invalid special tag should not error
    with pytest.raises(Exception):
        len(col.find_cards("is:invalid"))
    # should be able to limit to parent col, no children
    id = col.db.scalar("select id from cards limit 1")
    col.db.execute(
        "update cards set did = ? where id = ?", col.decks.id("Default::Child"), id
    )
    assert len(col.find_cards("deck:default")) == 7
    assert len(col.find_cards("deck:default::child")) == 1
    assert len(col.find_cards("deck:default -deck:default::*")) == 6
    # properties
    id = col.db.scalar("select id from cards limit 1")
    col.db.execute(
        "update cards set queue=2, ivl=10, reps=20, due=30, factor=2200 "
        "where id = ?",
        id,
    )
    assert len(col.find_cards("prop:ivl>5")) == 1
    assert len(col.find_cards("prop:ivl<5")) > 1
    assert len(col.find_cards("prop:ivl>=5")) == 1
    assert len(col.find_cards("prop:ivl=9")) == 0
    assert len(col.find_cards("prop:ivl=10")) == 1
    assert len(col.find_cards("prop:ivl!=10")) > 1
    assert len(col.find_cards("prop:due>0")) == 1
    # due dates should work
    assert len(col.find_cards("prop:due=29")) == 0
    assert len(col.find_cards("prop:due=30")) == 1
    # ease factors
    assert len(col.find_cards("prop:ease=2.3")) == 0
    assert len(col.find_cards("prop:ease=2.2")) == 1
    assert len(col.find_cards("prop:ease>2")) == 1
    assert len(col.find_cards("-prop:ease>2")) > 1
    # recently failed
    if not isNearCutoff():
        # rated
        assert len(col.find_cards("rated:1:1")) == 0
        assert len(col.find_cards("rated:1:2")) == 0
        c = col.sched.getCard()
        col.sched.answerCard(c, 2)
        assert len(col.find_cards("rated:1:1")) == 0
        assert len(col.find_cards("rated:1:2")) == 1
        c = col.sched.getCard()
        col.sched.answerCard(c, 1)
        assert len(col.find_cards("rated:1:1")) == 1
        assert len(col.find_cards("rated:1:2")) == 1
        assert len(col.find_cards("rated:1")) == 2
        assert len(col.find_cards("rated:2:2")) == 1
        assert len(col.find_cards("rated:0")) == len(col.find_cards("rated:1"))

        # added
        col.db.execute("update cards set id = id - 86400*1000 where id = ?", id)
        assert len(col.find_cards("added:1")) == col.card_count() - 1
        assert len(col.find_cards("added:2")) == col.card_count()
        assert len(col.find_cards("added:0")) == len(col.find_cards("added:1"))
    else:
        print("some find tests disabled near cutoff")
    # empty field
    assert len(col.find_cards("front:")) == 0
    note = col.newNote()
    note["Front"] = ""
    note["Back"] = "abc2"
    assert col.addNote(note) == 1
    assert len(col.find_cards("front:")) == 1
    # OR searches and nesting
    assert len(col.find_cards("tag:monkey or tag:sheep")) == 2
    assert len(col.find_cards("(tag:monkey OR tag:sheep)")) == 2
    assert len(col.find_cards("-(tag:monkey OR tag:sheep)")) == 6
    assert len(col.find_cards("tag:monkey or (tag:sheep sheep)")) == 2
    assert len(col.find_cards("tag:monkey or (tag:sheep octopus)")) == 1
    # flag
    with pytest.raises(Exception):
        col.find_cards("flag:12")


def test_findReplace():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "foo"
    note["Back"] = "bar"
    col.addNote(note)
    note2 = col.newNote()
    note2["Front"] = "baz"
    note2["Back"] = "foo"
    col.addNote(note2)
    nids = [note.id, note2.id]
    # should do nothing
    assert (
        col.find_and_replace(note_ids=nids, search="abc", replacement="123").count == 0
    )
    # global replace
    assert (
        col.find_and_replace(note_ids=nids, search="foo", replacement="qux").count == 2
    )
    note.load()
    assert note["Front"] == "qux"
    note2.load()
    assert note2["Back"] == "qux"
    # single field replace
    assert (
        col.find_and_replace(
            note_ids=nids, search="qux", replacement="foo", field_name="Front"
        ).count
        == 1
    )
    note.load()
    assert note["Front"] == "foo"
    note2.load()
    assert note2["Back"] == "qux"
    # regex replace
    assert (
        col.find_and_replace(note_ids=nids, search="B.r", replacement="reg").count == 0
    )
    note.load()
    assert note["Back"] != "reg"
    assert (
        col.find_and_replace(
            note_ids=nids, search="B.r", replacement="reg", regex=True
        ).count
        == 1
    )
    note.load()
    assert note["Back"] == "reg"


def test_findDupes():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "foo"
    note["Back"] = "bar"
    col.addNote(note)
    note2 = col.newNote()
    note2["Front"] = "baz"
    note2["Back"] = "bar"
    col.addNote(note2)
    note3 = col.newNote()
    note3["Front"] = "quux"
    note3["Back"] = "bar"
    col.addNote(note3)
    note4 = col.newNote()
    note4["Front"] = "quuux"
    note4["Back"] = "nope"
    col.addNote(note4)
    r = col.find_dupes("Back")
    assert r[0][0] == "bar"
    assert len(r[0][1]) == 3
    # valid search
    r = col.find_dupes("Back", "bar")
    assert r[0][0] == "bar"
    assert len(r[0][1]) == 3
    # excludes everything
    r = col.find_dupes("Back", "invalid")
    assert not r
    # front isn't dupe
    assert col.find_dupes("Front") == []
