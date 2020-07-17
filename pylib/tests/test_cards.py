# coding: utf-8

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
    d = getEmptyCol()
    note = d.newNote()
    note["Front"] = "1"
    note["Back"] = "2"
    d.addNote(note)
    c = note.cards()[0]
    id = d.models.current()["id"]
    assert c.template()["ord"] == 0


def test_genrem():
    d = getEmptyCol()
    note = d.newNote()
    note["Front"] = "1"
    note["Back"] = ""
    d.addNote(note)
    assert len(note.cards()) == 1
    m = d.models.current()
    mm = d.models
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
    rep = d.backend.get_empty_cards()
    for n in rep.notes:
        d.remove_cards_and_orphaned_notes(n.card_ids)
    assert len(note.cards()) == 1
    # if we add to the note, a card should be automatically generated
    note.load()
    note["Back"] = "1"
    note.flush()
    assert len(note.cards()) == 2


def test_gendeck():
    d = getEmptyCol()
    cloze = d.models.byName("Cloze")
    d.models.setCurrent(cloze)
    note = d.newNote()
    note["Text"] = "{{c1::one}}"
    d.addNote(note)
    assert d.cardCount() == 1
    assert note.cards()[0].did == 1
    # set the model to a new default col
    newId = d.decks.id("new")
    cloze["did"] = newId
    d.models.save(cloze, updateReqs=False)
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
