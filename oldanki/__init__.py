# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <oldanki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Anki (libanki)
====================

Open a deck:

    deck = oldanki.DeckStorage.Deck(path)

Get a card:

    card = deck.getCard()
    if not card:
        # deck is finished

Show the card:

    print card.question, card.answer

Answer the card:

    deck.answerCard(card, ease)

Edit the card:

    fields = card.fact.model.fieldModels
    for field in fields:
        card.fact[field.name] = "newvalue"
    card.fact.setModified(textChanged=True, deck=deck)
    deck.setModified()

Get all cards via ORM (slow):

    from oldanki.cards import Card
    cards = deck.s.query(Card).all()

Get all q/a/ids via SQL (fast):

    cards = deck.s.all("select id, question, answer from cards")

Save & close:

    deck.save()
    deck.close()
"""
__docformat__ = 'restructuredtext'

version = "1.2.11"

from oldanki.deck import DeckStorage
