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
    rep = col._backend.get_empty_cards()
    rep = col._backend.get_empty_cards()
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
