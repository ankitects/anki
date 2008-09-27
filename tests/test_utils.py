# coding: utf-8

import nose, os
from tests.shared import assertException

from anki.errors import *
from anki.facts import *
from anki import DeckStorage
from anki.utils import *


def test_tags():
    card = "one, two"
    fact = "two,three, two"
    cmodel = "four"

    assert (sorted(parseTags(mergeTags(card, fact, cmodel))) ==
            ['four', 'one', 'three', 'two'])
