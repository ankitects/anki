# coding: utf-8

import nose, os
from tests.shared import assertException

from anki.errors import *
from anki import DeckStorage
from anki.db import *
from anki.stdmodels import *

def test_stdmodels():
    # test all but basicmodel
    deck = DeckStorage.Deck()
    deck.addModel(JapaneseModel())
    deck = DeckStorage.Deck()
    deck.addModel(CantoneseModel())
    deck = DeckStorage.Deck()
    deck.addModel(MandarinModel())
