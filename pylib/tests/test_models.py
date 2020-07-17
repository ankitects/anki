# coding: utf-8
import time

from anki.consts import MODEL_CLOZE
from anki.rsbackend import NotFoundError
from anki.utils import isWin, stripHTML
from tests.shared import getEmptyCol


def test_modelDelete():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "1"
    note["Back"] = "2"
    col.addNote(note)
    assert col.cardCount() == 1
    col.models.rem(col.models.current())
    assert col.cardCount() == 0


def test_modelCopy():
    col = getEmptyCol()
    m = col.models.current()
    m2 = col.models.copy(m)
    assert m2["name"] == "Basic copy"
    assert m2["id"] != m["id"]
    assert len(m2["flds"]) == 2
    assert len(m["flds"]) == 2
    assert len(m2["flds"]) == len(m["flds"])
    assert len(m["tmpls"]) == 1
    assert len(m2["tmpls"]) == 1
    assert col.models.scmhash(m) == col.models.scmhash(m2)


def test_fields():
    d = getEmptyCol()
    note = d.newNote()
    note["Front"] = "1"
    note["Back"] = "2"
    d.addNote(note)
    m = d.models.current()
    # make sure renaming a field updates the templates
    d.models.renameField(m, m["flds"][0], "NewFront")
    assert "{{NewFront}}" in m["tmpls"][0]["qfmt"]
    h = d.models.scmhash(m)
    # add a field
    note = d.models.newField("foo")
    d.models.addField(m, note)
    assert d.getNote(d.models.nids(m)[0]).fields == ["1", "2", ""]
    assert d.models.scmhash(m) != h
    # rename it
    note = m["flds"][2]
    d.models.renameField(m, note, "bar")
    assert d.getNote(d.models.nids(m)[0])["bar"] == ""
    # delete back
    d.models.remField(m, m["flds"][1])
    assert d.getNote(d.models.nids(m)[0]).fields == ["1", ""]
    # move 0 -> 1
    d.models.moveField(m, m["flds"][0], 1)
    assert d.getNote(d.models.nids(m)[0]).fields == ["", "1"]
    # move 1 -> 0
    d.models.moveField(m, m["flds"][1], 0)
    assert d.getNote(d.models.nids(m)[0]).fields == ["1", ""]
    # add another and put in middle
    note = d.models.newField("baz")
    d.models.addField(m, note)
    note = d.getNote(d.models.nids(m)[0])
    note["baz"] = "2"
    note.flush()
    assert d.getNote(d.models.nids(m)[0]).fields == ["1", "", "2"]
    # move 2 -> 1
    d.models.moveField(m, m["flds"][2], 1)
    assert d.getNote(d.models.nids(m)[0]).fields == ["1", "2", ""]
    # move 0 -> 2
    d.models.moveField(m, m["flds"][0], 2)
    assert d.getNote(d.models.nids(m)[0]).fields == ["2", "", "1"]
    # move 0 -> 1
    d.models.moveField(m, m["flds"][0], 1)
    assert d.getNote(d.models.nids(m)[0]).fields == ["", "2", "1"]


def test_templates():
    d = getEmptyCol()
    m = d.models.current()
    mm = d.models
    t = mm.newTemplate("Reverse")
    t["qfmt"] = "{{Back}}"
    t["afmt"] = "{{Front}}"
    mm.addTemplate(m, t)
    mm.save(m)
    note = d.newNote()
    note["Front"] = "1"
    note["Back"] = "2"
    d.addNote(note)
    assert d.cardCount() == 2
    (c, c2) = note.cards()
    # first card should have first ord
    assert c.ord == 0
    assert c2.ord == 1
    # switch templates
    d.models.moveTemplate(m, c.template(), 1)
    c.load()
    c2.load()
    assert c.ord == 1
    assert c2.ord == 0
    # removing a template should delete its cards
    d.models.remTemplate(m, m["tmpls"][0])
    assert d.cardCount() == 1
    # and should have updated the other cards' ordinals
    c = note.cards()[0]
    assert c.ord == 0
    assert stripHTML(c.q()) == "1"
    # it shouldn't be possible to orphan notes by removing templates
    t = mm.newTemplate("template name")
    mm.addTemplate(m, t)
    d.models.remTemplate(m, m["tmpls"][0])
    assert (
        d.db.scalar("select count() from cards where nid not in (select id from notes)")
        == 0
    )


def test_cloze_ordinals():
    d = getEmptyCol()
    d.models.setCurrent(d.models.byName("Cloze"))
    m = d.models.current()
    mm = d.models

    # We replace the default Cloze template
    t = mm.newTemplate("ChainedCloze")
    t["qfmt"] = "{{text:cloze:Text}}"
    t["afmt"] = "{{text:cloze:Text}}"
    mm.addTemplate(m, t)
    mm.save(m)
    d.models.remTemplate(m, m["tmpls"][0])

    note = d.newNote()
    note["Text"] = "{{c1::firstQ::firstA}}{{c2::secondQ::secondA}}"
    d.addNote(note)
    assert d.cardCount() == 2
    (c, c2) = note.cards()
    # first card should have first ord
    assert c.ord == 0
    assert c2.ord == 1


def test_text():
    d = getEmptyCol()
    m = d.models.current()
    m["tmpls"][0]["qfmt"] = "{{text:Front}}"
    d.models.save(m)
    note = d.newNote()
    note["Front"] = "hello<b>world"
    d.addNote(note)
    assert "helloworld" in note.cards()[0].q()


def test_cloze():
    d = getEmptyCol()
    d.models.setCurrent(d.models.byName("Cloze"))
    note = d.newNote()
    assert note.model()["name"] == "Cloze"
    # a cloze model with no clozes is not empty
    note["Text"] = "nothing"
    assert d.addNote(note)
    # try with one cloze
    note = d.newNote()
    note["Text"] = "hello {{c1::world}}"
    assert d.addNote(note) == 1
    assert "hello <span class=cloze>[...]</span>" in note.cards()[0].q()
    assert "hello <span class=cloze>world</span>" in note.cards()[0].a()
    # and with a comment
    note = d.newNote()
    note["Text"] = "hello {{c1::world::typical}}"
    assert d.addNote(note) == 1
    assert "<span class=cloze>[typical]</span>" in note.cards()[0].q()
    assert "<span class=cloze>world</span>" in note.cards()[0].a()
    # and with 2 clozes
    note = d.newNote()
    note["Text"] = "hello {{c1::world}} {{c2::bar}}"
    assert d.addNote(note) == 2
    (c1, c2) = note.cards()
    assert "<span class=cloze>[...]</span> bar" in c1.q()
    assert "<span class=cloze>world</span> bar" in c1.a()
    assert "world <span class=cloze>[...]</span>" in c2.q()
    assert "world <span class=cloze>bar</span>" in c2.a()
    # if there are multiple answers for a single cloze, they are given in a
    # list
    note = d.newNote()
    note["Text"] = "a {{c1::b}} {{c1::c}}"
    assert d.addNote(note) == 1
    assert "<span class=cloze>b</span> <span class=cloze>c</span>" in (
        note.cards()[0].a()
    )
    # if we add another cloze, a card should be generated
    cnt = d.cardCount()
    note["Text"] = "{{c2::hello}} {{c1::foo}}"
    note.flush()
    assert d.cardCount() == cnt + 1
    # 0 or negative indices are not supported
    note["Text"] += "{{c0::zero}} {{c-1:foo}}"
    note.flush()
    assert len(note.cards()) == 2


def test_cloze_mathjax():
    d = getEmptyCol()
    d.models.setCurrent(d.models.byName("Cloze"))
    note = d.newNote()
    note[
        "Text"
    ] = r"{{c1::ok}} \(2^2\) {{c2::not ok}} \(2^{{c3::2}}\) \(x^3\) {{c4::blah}} {{c5::text with \(x^2\) jax}}"
    assert d.addNote(note)
    assert len(note.cards()) == 5
    assert "class=cloze" in note.cards()[0].q()
    assert "class=cloze" in note.cards()[1].q()
    assert "class=cloze" not in note.cards()[2].q()
    assert "class=cloze" in note.cards()[3].q()
    assert "class=cloze" in note.cards()[4].q()

    note = d.newNote()
    note["Text"] = r"\(a\) {{c1::b}} \[ {{c1::c}} \]"
    assert d.addNote(note)
    assert len(note.cards()) == 1
    assert (
        note.cards()[0]
        .q()
        .endswith(r"\(a\) <span class=cloze>[...]</span> \[ [...] \]")
    )


def test_typecloze():
    d = getEmptyCol()
    m = d.models.byName("Cloze")
    d.models.setCurrent(m)
    m["tmpls"][0]["qfmt"] = "{{cloze:Text}}{{type:cloze:Text}}"
    d.models.save(m)
    note = d.newNote()
    note["Text"] = "hello {{c1::world}}"
    d.addNote(note)
    assert "[[type:cloze:Text]]" in note.cards()[0].q()


def test_chained_mods():
    d = getEmptyCol()
    d.models.setCurrent(d.models.byName("Cloze"))
    m = d.models.current()
    mm = d.models

    # We replace the default Cloze template
    t = mm.newTemplate("ChainedCloze")
    t["qfmt"] = "{{cloze:text:Text}}"
    t["afmt"] = "{{cloze:text:Text}}"
    mm.addTemplate(m, t)
    mm.save(m)
    d.models.remTemplate(m, m["tmpls"][0])

    note = d.newNote()
    q1 = '<span style="color:red">phrase</span>'
    a1 = "<b>sentence</b>"
    q2 = '<span style="color:red">en chaine</span>'
    a2 = "<i>chained</i>"
    note["Text"] = "This {{c1::%s::%s}} demonstrates {{c1::%s::%s}} clozes." % (
        q1,
        a1,
        q2,
        a2,
    )
    assert d.addNote(note) == 1
    assert (
        "This <span class=cloze>[sentence]</span> demonstrates <span class=cloze>[chained]</span> clozes."
        in note.cards()[0].q()
    )
    assert (
        "This <span class=cloze>phrase</span> demonstrates <span class=cloze>en chaine</span> clozes."
        in note.cards()[0].a()
    )


def test_modelChange():
    col = getEmptyCol()
    cloze = col.models.byName("Cloze")
    # enable second template and add a note
    m = col.models.current()
    mm = col.models
    t = mm.newTemplate("Reverse")
    t["qfmt"] = "{{Back}}"
    t["afmt"] = "{{Front}}"
    mm.addTemplate(m, t)
    mm.save(m)
    basic = m
    note = col.newNote()
    note["Front"] = "note"
    note["Back"] = "b123"
    col.addNote(note)
    # switch fields
    map = {0: 1, 1: 0}
    col.models.change(basic, [note.id], basic, map, None)
    note.load()
    assert note["Front"] == "b123"
    assert note["Back"] == "note"
    # switch cards
    c0 = note.cards()[0]
    c1 = note.cards()[1]
    assert "b123" in c0.q()
    assert "note" in c1.q()
    assert c0.ord == 0
    assert c1.ord == 1
    col.models.change(basic, [note.id], basic, None, map)
    note.load()
    c0.load()
    c1.load()
    assert "note" in c0.q()
    assert "b123" in c1.q()
    assert c0.ord == 1
    assert c1.ord == 0
    # .cards() returns cards in order
    assert note.cards()[0].id == c1.id
    # delete first card
    map = {0: None, 1: 1}
    if isWin:
        # The low precision timer on Windows reveals a race condition
        time.sleep(0.05)
    col.models.change(basic, [note.id], basic, None, map)
    note.load()
    c0.load()
    # the card was deleted
    try:
        c1.load()
        assert 0
    except NotFoundError:
        pass
    # but we have two cards, as a new one was generated
    assert len(note.cards()) == 2
    # an unmapped field becomes blank
    assert note["Front"] == "b123"
    assert note["Back"] == "note"
    col.models.change(basic, [note.id], basic, map, None)
    note.load()
    assert note["Front"] == ""
    assert note["Back"] == "note"
    # another note to try model conversion
    note = col.newNote()
    note["Front"] = "f2"
    note["Back"] = "b2"
    col.addNote(note)
    counts = col.models.all_use_counts()
    assert next(c.use_count for c in counts if c.name == "Basic") == 2
    assert next(c.use_count for c in counts if c.name == "Cloze") == 0
    map = {0: 0, 1: 1}
    col.models.change(basic, [note.id], cloze, map, map)
    note.load()
    assert note["Text"] == "f2"
    assert len(note.cards()) == 2
    # back the other way, with deletion of second ord
    col.models.remTemplate(basic, basic["tmpls"][1])
    assert col.db.scalar("select count() from cards where nid = ?", note.id) == 2
    map = {0: 0}
    col.models.change(cloze, [note.id], basic, map, map)
    assert col.db.scalar("select count() from cards where nid = ?", note.id) == 1


def test_req():
    def reqSize(model):
        if model["type"] == MODEL_CLOZE:
            return
        assert len(model["tmpls"]) == len(model["req"])

    d = getEmptyCol()
    mm = d.models
    basic = mm.byName("Basic")
    assert "req" in basic
    reqSize(basic)
    r = basic["req"][0]
    assert r[0] == 0
    assert r[1] in ("any", "all")
    assert r[2] == [0]
    opt = mm.byName("Basic (optional reversed card)")
    reqSize(opt)
    r = opt["req"][0]
    assert r[1] in ("any", "all")
    assert r[2] == [0]
    assert opt["req"][1] == [1, "all", [1, 2]]
    # testing any
    opt["tmpls"][1]["qfmt"] = "{{Back}}{{Add Reverse}}"
    mm.save(opt, templates=True)
    assert opt["req"][1] == [1, "any", [1, 2]]
    # testing None
    opt["tmpls"][1]["qfmt"] = "{{^Add Reverse}}{{/Add Reverse}}"
    mm.save(opt, templates=True)
    assert opt["req"][1] == [1, "none", []]

    opt = mm.byName("Basic (type in the answer)")
    reqSize(opt)
    r = opt["req"][0]
    assert r[1] in ("any", "all")
    assert r[2] == [0, 1]
