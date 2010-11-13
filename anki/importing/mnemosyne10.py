# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Importing Mnemosyne 1.0 decks
==============================
"""
__docformat__ = 'restructuredtext'

import sys, pickle, time, re
from anki.importing import Importer, ForeignCard
from anki.errors import *

class Mnemosyne10Importer(Importer):

    multipleCardsAllowed = False

    def foreignCards(self):
        # empty objects so we can load the native mnemosyne file
        class MnemosyneModule(object):
            class StartTime:
                pass
            class Category:
                pass
            class Item:
                pass
        for module in ('mnemosyne',
                       'mnemosyne.core',
                       'mnemosyne.core.mnemosyne_core'):
            sys.modules[module] = MnemosyneModule()
        try:
            file = open(self.file, "rb")
        except (IOError, OSError), e:
            raise ImportFormatError(type="systemError",
                                    info=str(e))
        header = file.readline().strip()
        # read the structure in
        try:
            struct = pickle.load(file)
        except (EOFError, KeyError):
            raise ImportFormatError(type="invalidFile")
        startTime = struct[0].time
        daysPassed = (time.time() - startTime) / 86400.0
        # gather cards
        cards = []
        for item in struct[2]:
            card = ForeignCard()
            card.fields.append(self.fudgeText(item.q))
            card.fields.append(self.fudgeText(item.a))
            # scheduling data
            card.interval = item.next_rep - item.last_rep
            secDelta = (item.next_rep - daysPassed) * 86400.0
            card.due = card.nextTime = time.time() + secDelta
            card.factor = item.easiness
            # for some reason mnemosyne starts cards off on 1 instead of 0
            card.successive = max(
                (item.acq_reps_since_lapse + item.ret_reps_since_lapse -1), 0)
            card.yesCount = max((item.acq_reps + item.ret_reps) - 1, 0)
            card.noCount = item.lapses
            card.reps = card.yesCount + card.noCount
            if item.cat.name != u"<default>":
                card.tags = item.cat.name.replace(" ", "_")
            cards.append(card)
        return cards

    def fields(self):
        return 2

    def fudgeText(self, text):
        text = text.replace("\n", "<br>")
        text = re.sub('<sound src="(.*?)">', '[sound:\\1]', text)
        text = re.sub('<(/?latex)>', '[\\1]', text)
        text = re.sub('<(/?\$)>', '[\\1]', text)
        text = re.sub('<(/?\$\$)>', '[\\1]', text)
        return text
