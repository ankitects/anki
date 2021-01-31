# coding: utf-8
import pytest

from anki.collection import BuiltinSortKind, ConfigBoolKey
from anki.consts import *
from tests.shared import getEmptyCol, isNearCutoff


class DummyCollection:
    def weakref(self):
        return None


def test_findCards():
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
    t = mm.newTemplate("Reverse")
    t["qfmt"] = "{{Back}}"
    t["afmt"] = "{{Front}}"
    mm.addTemplate(m, t)
    mm.save(m)
    note = col.newNote()
    note["Front"] = "test"
    note["Back"] = "foo bar"
    col.addNote(note)
    col.save()
    latestCardIds = [c.id for c in note.cards()]
    # tag searches
    assert len(col.findCards("tag:*")) == 5
    assert len(col.findCards("tag:\\*")) == 1
    assert len(col.findCards("tag:%")) == 1
    assert len(col.findCards("tag:sheep_goat")) == 0
    assert len(col.findCards('"tag:sheep goat"')) == 0
    assert len(col.findCards('"tag:* *"')) == 0
    assert len(col.findCards("tag:animal_1")) == 2
    assert len(col.findCards("tag:animal\\_1")) == 1
    assert not col.findCards("tag:donkey")
    assert len(col.findCards("tag:sheep")) == 1
    assert len(col.findCards("tag:sheep tag:goat")) == 1
    assert len(col.findCards("tag:sheep tag:monkey")) == 0
    assert len(col.findCards("tag:monkey")) == 1
    assert len(col.findCards("tag:sheep -tag:monkey")) == 1
    assert len(col.findCards("-tag:sheep")) == 4
    col.tags.bulkAdd(col.db.list("select id from notes"), "foo bar")
    assert len(col.findCards("tag:foo")) == len(col.findCards("tag:bar")) == 5
    col.tags.bulkRem(col.db.list("select id from notes"), "foo")
    assert len(col.findCards("tag:foo")) == 0
    assert len(col.findCards("tag:bar")) == 5
    # text searches
    assert len(col.findCards("cat")) == 2
    assert len(col.findCards("cat -dog")) == 1
    assert len(col.findCards("cat -dog")) == 1
    assert len(col.findCards("are goats")) == 1
    assert len(col.findCards('"are goats"')) == 0
    assert len(col.findCards('"goats are"')) == 1
    # card states
    c = note.cards()[0]
    c.queue = c.type = CARD_TYPE_REV
    assert col.findCards("is:review") == []
    c.flush()
    assert col.findCards("is:review") == [c.id]
    assert col.findCards("is:due") == []
    c.due = 0
    c.queue = QUEUE_TYPE_REV
    c.flush()
    assert col.findCards("is:due") == [c.id]
    assert len(col.findCards("-is:due")) == 4
    c.queue = QUEUE_TYPE_SUSPENDED
    # ensure this card gets a later mod time
    c.flush()
    col.db.execute("update cards set mod = mod + 1 where id = ?", c.id)
    assert col.findCards("is:suspended") == [c.id]
    # nids
    assert col.findCards("nid:54321") == []
    assert len(col.findCards(f"nid:{note.id}")) == 2
    assert len(col.findCards(f"nid:{n1id},{n2id}")) == 2
    # templates
    assert len(col.findCards("card:foo")) == 0
    assert len(col.findCards('"card:card 1"')) == 4
    assert len(col.findCards("card:reverse")) == 1
    assert len(col.findCards("card:1")) == 4
    assert len(col.findCards("card:2")) == 1
    # fields
    assert len(col.findCards("front:dog")) == 1
    assert len(col.findCards("-front:dog")) == 4
    assert len(col.findCards("front:sheep")) == 0
    assert len(col.findCards("back:sheep")) == 2
    assert len(col.findCards("-back:sheep")) == 3
    assert len(col.findCards("front:do")) == 0
    assert len(col.findCards("front:*")) == 5
    # ordering
    col.conf["sortType"] = "noteCrt"
    col.flush()
    assert col.findCards("front:*", order=True)[-1] in latestCardIds
    assert col.findCards("", order=True)[-1] in latestCardIds
    col.conf["sortType"] = "noteFld"
    col.flush()
    assert col.findCards("", order=True)[0] == catCard.id
    assert col.findCards("", order=True)[-1] in latestCardIds
    col.conf["sortType"] = "cardMod"
    col.flush()
    assert col.findCards("", order=True)[-1] in latestCardIds
    assert col.findCards("", order=True)[0] == firstCardId
    col.set_config_bool(ConfigBoolKey.BROWSER_SORT_BACKWARDS, True)
    col.flush()
    assert col.findCards("", order=True)[0] in latestCardIds
    assert (
        col.find_cards("", order=BuiltinSortKind.CARD_DUE, reverse=False)[0]
        == firstCardId
    )
    assert (
        col.find_cards("", order=BuiltinSortKind.CARD_DUE, reverse=True)[0]
        != firstCardId
    )
    # model
    assert len(col.findCards("note:basic")) == 3
    assert len(col.findCards("-note:basic")) == 2
    assert len(col.findCards("-note:foo")) == 5
    # col
    assert len(col.findCards("deck:default")) == 5
    assert len(col.findCards("-deck:default")) == 0
    assert len(col.findCards("-deck:foo")) == 5
    assert len(col.findCards("deck:def*")) == 5
    assert len(col.findCards("deck:*EFAULT")) == 5
    assert len(col.findCards("deck:*cefault")) == 0
    # full search
    note = col.newNote()
    note["Front"] = "hello<b>world</b>"
    note["Back"] = "abc"
    col.addNote(note)
    # as it's the sort field, it matches
    assert len(col.findCards("helloworld")) == 2
    # assert len(col.findCards("helloworld", full=True)) == 2
    # if we put it on the back, it won't
    (note["Front"], note["Back"]) = (note["Back"], note["Front"])
    note.flush()
    assert len(col.findCards("helloworld")) == 0
    # assert len(col.findCards("helloworld", full=True)) == 2
    # assert len(col.findCards("back:helloworld", full=True)) == 2
    # searching for an invalid special tag should not error
    with pytest.raises(Exception):
        len(col.findCards("is:invalid"))
    # should be able to limit to parent col, no children
    id = col.db.scalar("select id from cards limit 1")
    col.db.execute(
        "update cards set did = ? where id = ?", col.decks.id("Default::Child"), id
    )
    col.save()
    assert len(col.findCards("deck:default")) == 7
    assert len(col.findCards("deck:default::child")) == 1
    assert len(col.findCards("deck:default -deck:default::*")) == 6
    # properties
    id = col.db.scalar("select id from cards limit 1")
    col.db.execute(
        "update cards set queue=2, ivl=10, reps=20, due=30, factor=2200 "
        "where id = ?",
        id,
    )
    assert len(col.findCards("prop:ivl>5")) == 1
    assert len(col.findCards("prop:ivl<5")) > 1
    assert len(col.findCards("prop:ivl>=5")) == 1
    assert len(col.findCards("prop:ivl=9")) == 0
    assert len(col.findCards("prop:ivl=10")) == 1
    assert len(col.findCards("prop:ivl!=10")) > 1
    assert len(col.findCards("prop:due>0")) == 1
    # due dates should work
    assert len(col.findCards("prop:due=29")) == 0
    assert len(col.findCards("prop:due=30")) == 1
    # ease factors
    assert len(col.findCards("prop:ease=2.3")) == 0
    assert len(col.findCards("prop:ease=2.2")) == 1
    assert len(col.findCards("prop:ease>2")) == 1
    assert len(col.findCards("-prop:ease>2")) > 1
    # recently failed
    if not isNearCutoff():
        # rated
        assert len(col.findCards("rated:1:1")) == 0
        assert len(col.findCards("rated:1:2")) == 0
        c = col.sched.getCard()
        col.sched.answerCard(c, 2)
        assert len(col.findCards("rated:1:1")) == 0
        assert len(col.findCards("rated:1:2")) == 1
        c = col.sched.getCard()
        col.sched.answerCard(c, 1)
        assert len(col.findCards("rated:1:1")) == 1
        assert len(col.findCards("rated:1:2")) == 1
        assert len(col.findCards("rated:1")) == 2
        assert len(col.findCards("rated:2:2")) == 1
        assert len(col.findCards("rated:0")) == len(col.findCards("rated:1"))

        # added
        col.db.execute("update cards set id = id - 86400*1000 where id = ?", id)
        assert len(col.findCards("added:1")) == col.cardCount() - 1
        assert len(col.findCards("added:2")) == col.cardCount()
        assert len(col.findCards("added:0")) == len(col.findCards("added:1"))
    else:
        print("some find tests disabled near cutoff")
    # empty field
    assert len(col.findCards("front:")) == 0
    note = col.newNote()
    note["Front"] = ""
    note["Back"] = "abc2"
    assert col.addNote(note) == 1
    assert len(col.findCards("front:")) == 1
    # OR searches and nesting
    assert len(col.findCards("tag:monkey or tag:sheep")) == 2
    assert len(col.findCards("(tag:monkey OR tag:sheep)")) == 2
    assert len(col.findCards("-(tag:monkey OR tag:sheep)")) == 6
    assert len(col.findCards("tag:monkey or (tag:sheep sheep)")) == 2
    assert len(col.findCards("tag:monkey or (tag:sheep octopus)")) == 1
    # flag
    with pytest.raises(Exception):
        col.findCards("flag:12")


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
    assert col.findReplace(nids, "abc", "123") == 0
    # global replace
    assert col.findReplace(nids, "foo", "qux") == 2
    note.load()
    assert note["Front"] == "qux"
    note2.load()
    assert note2["Back"] == "qux"
    # single field replace
    assert col.findReplace(nids, "qux", "foo", field="Front") == 1
    note.load()
    assert note["Front"] == "foo"
    note2.load()
    assert note2["Back"] == "qux"
    # regex replace
    assert col.findReplace(nids, "B.r", "reg") == 0
    note.load()
    assert note["Back"] != "reg"
    assert col.findReplace(nids, "B.r", "reg", regex=True) == 1
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
    r = col.findDupes("Back")
    assert r[0][0] == "bar"
    assert len(r[0][1]) == 3
    # valid search
    r = col.findDupes("Back", "bar")
    assert r[0][0] == "bar"
    assert len(r[0][1]) == 3
    # excludes everything
    r = col.findDupes("Back", "invalid")
    assert not r
    # front isn't dupe
    assert col.findDupes("Front") == []
