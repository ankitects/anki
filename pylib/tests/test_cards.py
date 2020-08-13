# coding: utf-8

from copy import deepcopy

from tests.shared import getEmptyCol


def test_delete():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "1"
    note["Back"] = "2"
    col.addNote(note)
    cid = note.cards()[0].id
    col.reset()
    col.sched.answerCard(col.sched.getCard(), 2)
    col.remove_cards_and_orphaned_notes([cid])
    assert col.cardCount() == 0
    assert col.noteCount() == 0
    assert col.db.scalar("select count() from notes") == 0
    assert col.db.scalar("select count() from cards") == 0
    assert col.db.scalar("select count() from graves") == 2


def test_misc():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "1"
    note["Back"] = "2"
    col.addNote(note)
    c = note.cards()[0]
    id = col.models.current()["id"]
    assert c.template()["ord"] == 0


def test_genrem():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "1"
    note["Back"] = ""
    col.addNote(note)
    assert len(note.cards()) == 1
    m = col.models.current()
    mm = col.models
    # adding a new template should automatically create cards
    t = mm.newTemplate("rev")
    t["qfmt"] = "{{Front}}"
    t["afmt"] = ""
    mm.addTemplate(m, t)
    mm.save(m, templates=True)
    assert len(note.cards()) == 2
    # if the template is changed to remove cards, they'll be removed
    t = m["tmpls"][1]
    t["qfmt"] = "{{Back}}"
    mm.save(m, templates=True)
    rep = col.backend.get_empty_cards()
    rep = col.backend.get_empty_cards()
    for n in rep.notes:
        col.remove_cards_and_orphaned_notes(n.card_ids)
    assert len(note.cards()) == 1
    # if we add to the note, a card should be automatically generated
    note.load()
    note["Back"] = "1"
    note.flush()
    assert len(note.cards()) == 2


def test_gendeck():
    col = getEmptyCol()
    cloze = col.models.byName("Cloze")
    col.models.setCurrent(cloze)
    note = col.newNote()
    note["Text"] = "{{c1::one}}"
    col.addNote(note)
    assert col.cardCount() == 1
    assert note.cards()[0].did == 1
    # set the model to a new default col
    newId = col.decks.id("new")
    cloze["did"] = newId
    col.models.save(cloze, updateReqs=False)
    # a newly generated card should share the first card's col
    note["Text"] += "{{c2::two}}"
    note.flush()
    assert note.cards()[1].did == 1
    # and same with multiple cards
    note["Text"] += "{{c3::three}}"
    note.flush()
    assert note.cards()[2].did == 1
    # if one of the cards is in a different col, it should revert to the
    # model default
    c = note.cards()[1]
    c.did = newId
    c.flush()
    note["Text"] += "{{c4::four}}"
    note.flush()
    assert note.cards()[3].did == newId


def assert_ord(note, ords):
    assert {card.ord for card in note.cards()} == ords


def test_gen_or():
    col = getEmptyCol()
    models = col.models
    model = models.byName("Basic")
    flds = model["flds"]
    models.rename_field(model, flds[0], "A")
    models.rename_field(model, flds[1], "B")
    fld2 = deepcopy(flds[0])
    fld2["name"] = "C"
    fld2["ord"] = None
    models.addField(model, fld2)

    tmpls = model["tmpls"]
    tmpls[0]["qfmt"] = """{{A}}{{B}}{{C}}"""
    # ensure first card is always generated,
    # because at last one card is generated
    tmpl = models.new_template("AND_OR")
    tmpl[
        "qfmt"
    ] = """
    {{A}}
{{#B}}
    {{#C}}
        {{B}}
    {{/C}}
{{/B}}"""
    models.add_template(model, tmpl)

    models.save(model)
    models.setCurrent(model)

    note = col.newNote()
    note["A"] = "foo"
    col.addNote(note)
    assert_ord(note, {0, 1})

    note = col.newNote()
    note["B"] = note["C"] = "foo"
    col.addNote(note)
    assert_ord(note, {0, 1})

    note = col.newNote()
    note["B"] = "foo"
    col.addNote(note)
    assert_ord(note, {0})

    note = col.newNote()
    note["C"] = "foo"
    col.addNote(note)
    assert_ord(note, {0})

    note = col.newNote()
    note["A"] = note["B"] = note["C"] = "foo"
    col.addNote(note)
    assert_ord(note, {0, 1})

    note = col.newNote()
    col.addNote(note)
    assert_ord(note, {0})  # First card generated if no other cards


def test_gen_not():
    col = getEmptyCol()
    models = col.models
    model = models.byName("Basic")
    flds = model["flds"]
    models.rename_field(model, flds[0], "First")
    models.rename_field(model, flds[1], "Front")
    fld2 = deepcopy(flds[0])
    fld2["name"] = "AddIfEmpty"
    fld2["ord"] = None
    models.addField(model, fld2)

    tmpls = model["tmpls"]
    # ensure first card is always generated,
    # because at last one card is generated
    tmpls[0]["qfmt"] = "{{AddIfEmpty}}{{Front}}{{First}}"
    tmpl = models.new_template("NOT")
    tmpl[
        "qfmt"
    ] = """
{{^AddIfEmpty}}
    {{Front}}
{{/AddIfEmpty}}
"""

    models.add_template(model, tmpl)

    models.save(model)
    models.setCurrent(model)

    note = col.newNote()
    note["First"] = "foo"
    note["AddIfEmpty"] = note["Front"] = "foo"
    col.addNote(note)
    assert_ord(note, {0})

    note = col.newNote()
    note["First"] = "foo"
    note["AddIfEmpty"] = "foo"
    col.addNote(note)
    assert_ord(note, {0})

    note = col.newNote()
    note["First"] = "foo"  # ensure first note generated
    col.addNote(note)
    assert_ord(note, {0})

    note = col.newNote()
    note["First"] = "foo"
    note["Front"] = "foo"
    col.addNote(note)
    assert_ord(note, {0, 1})
