# coding: utf-8

import nose, os
from tests.shared import assertException

from anki.errors import *
from anki.facts import *
from anki import DeckStorage
from anki.utils import *


def test_tags():
    return
#     card = "one, two"
#     fact = "two,three, two"
#     cmodel = "four"

#     print (card+","+fact+","+cmodel)
#     print joinTags(parseTags(card+","+fact+","+cmodel))
#     print sorted(canonifyTags(card+","+fact+","+cmodel))
#     assert (sorted(canonifyTags(card+","+fact+","+cmodel)) ==
#             ['four', 'one', 'three', 'two'])
