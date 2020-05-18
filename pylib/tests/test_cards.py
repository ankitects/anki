# coding: utf-8

from tests.shared import getEmptyCol


def test_delete():
    deck = getEmptyCol()
    f = deck.newNote()
    f["Front"] = "1"
    f["Back"] = "2"
    deck.addNote(f)
    cid = f.cards()[0].id
    deck.reset()
    deck.sched.answerCard(deck.sched.getCard(), 2)
    deck.remCards([cid])
    assert deck.cardCount() == 0
    assert deck.noteCount() == 0
    assert deck.db.scalar("select count() from notes") == 0
    assert deck.db.scalar("select count() from cards") == 0
    assert deck.db.scalar("select count() from graves") == 2


def test_misc():
    d = getEmptyCol()
    f = d.newNote()
    f["Front"] = "1"
    f["Back"] = "2"
    d.addNote(f)
    c = f.cards()[0]
    id = d.models.current()["id"]
    assert c.template()["ord"] == 0


def test_genrem():
    d = getEmptyCol()
    f = d.newNote()
    f["Front"] = "1"
    f["Back"] = ""
    d.addNote(f)
    assert len(f.cards()) == 1
    m = d.models.current()
    mm = d.models
    # adding a new template should automatically create cards
    t = mm.newTemplate("rev")
    t["qfmt"] = "{{Front}}"
    t["afmt"] = ""
    mm.addTemplate(m, t)
    mm.save(m, templates=True)
    assert len(f.cards()) == 2
    # if the template is changed to remove cards, they'll be removed
    t = m["tmpls"][1]
    t["qfmt"] = "{{Back}}"
    mm.save(m, templates=True)
    rep = d.backend.empty_cards_report()
    for note in rep.notes:
        d.remCards(note.card_ids)
    assert len(f.cards()) == 1
    # if we add to the note, a card should be automatically generated
    f.load()
    f["Back"] = "1"
    f.flush()
    assert len(f.cards()) == 2


def test_gendeck():
    d = getEmptyCol()
    cloze = d.models.byName("Cloze")
    d.models.setCurrent(cloze)
    f = d.newNote()
    f["Text"] = "{{c1::one}}"
    d.addNote(f)
    assert d.cardCount() == 1
    assert f.cards()[0].did == 1
    # set the model to a new default deck
    newId = d.decks.id("new")
    cloze["did"] = newId
    d.models.save(cloze, updateReqs=False)
    # a newly generated card should share the first card's deck
    f["Text"] += "{{c2::two}}"
    f.flush()
    assert f.cards()[1].did == 1
    # and same with multiple cards
    f["Text"] += "{{c3::three}}"
    f.flush()
    assert f.cards()[2].did == 1
    # if one of the cards is in a different deck, it should revert to the
    # model default
    c = f.cards()[1]
    c.did = newId
    c.flush()
    f["Text"] += "{{c4::four}}"
    f.flush()
    assert f.cards()[3].did == newId
