# coding: utf-8

import nose, os
from tests.shared import assertException

from anki.errors import *
from anki import DeckStorage
from anki.db import *
from anki.stdmodels import JapaneseModel, MandarinModel, CantoneseModel

def test_japanese():
    deck = DeckStorage.Deck()
    deck.addModel(JapaneseModel())
    f = deck.newFact()
    f['Expression'] = u'了解'
    f.onKeyPress(f.fields[0], f.fields[0].value)
    assert f['Reading'] == u'りょうかい'

def test_chinese():
    deck = DeckStorage.Deck()
    deck.addModel(MandarinModel())
    f = deck.newFact()
    f['Expression'] = u'食べる'
    f.onKeyPress(f.fields[0], f.fields[0].value)
    assert f['Reading'] == u"{shí,sì,yì}"
    deck = DeckStorage.Deck()
    deck.addModel(CantoneseModel())
    f = deck.newFact()
    f['Expression'] = u'食べる'
    f.onKeyPress(f.fields[0], f.fields[0].value)
    assert f['Reading'] == u"{ji6,sik6,zi6}"
