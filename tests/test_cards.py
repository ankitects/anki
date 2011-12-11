# coding: utf-8

import time
from anki.db import DB
from anki.consts import *
from anki.utils import hexifyID
from tests.shared import getEmptyDeck

def test_previewCards():
    deck = getEmptyDeck()
    f = deck.newNote()
    f['Front'] = u'1'
    f['Back'] = u'2'
    # non-empty and active
    cards = deck.previewCards(f, 0)
    assert len(cards) == 1
    assert cards[0].ord == 0
    # all templates
    cards = deck.previewCards(f, 2)
    assert len(cards) == 1
    # add the note, and test existing preview
    deck.addNote(f)
    cards = deck.previewCards(f, 1)
    assert len(cards) == 1
    assert cards[0].ord == 0
    # make sure we haven't accidentally added cards to the db
    assert deck.cardCount() == 1

def test_delete():
    deck = getEmptyDeck()
    f = deck.newNote()
    f['Front'] = u'1'
    f['Back'] = u'2'
    deck.addNote(f)
    cid = f.cards()[0].id
    deck.reset()
    deck.sched.answerCard(deck.sched.getCard(), 2)
    assert deck.db.scalar("select count() from revlog") == 1
    deck.remCards([cid])
    assert deck.cardCount() == 0
    assert deck.noteCount() == 0
    assert deck.db.scalar("select count() from notes") == 0
    assert deck.db.scalar("select count() from cards") == 0
    assert deck.db.scalar("select count() from revlog") == 0
    assert deck.db.scalar("select count() from graves") == 2

def test_misc():
    d = getEmptyDeck()
    f = d.newNote()
    f['Front'] = u'1'
    f['Back'] = u'2'
    d.addNote(f)
    c = f.cards()[0]
    id = d.models.current()['id']
    assert c.template()['ord'] == 0

def test_genrem():
    d = getEmptyDeck()
    f = d.newNote()
    f['Front'] = u'1'
    f['Back'] = u''
    d.addNote(f)
    assert len(f.cards()) == 1
    m = d.models.current()
    mm = d.models
    # adding a new template should automatically create cards
    t = mm.newTemplate("rev")
    t['qfmt'] = '{{Front}}'
    t['afmt'] = ""
    mm.addTemplate(m, t)
    mm.save(m, templates=True)
    assert len(f.cards()) == 2
    # if the template is changed to remove cards, they'll be removed
    t['qfmt'] = "{{Back}}"
    mm.save(m, templates=True)
    assert len(f.cards()) == 1
    # if we add to the note, a card should be automatically generated
    f['Back'] = "1"
    f.flush()
    assert len(f.cards()) == 2
