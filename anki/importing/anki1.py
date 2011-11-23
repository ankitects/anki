# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import traceback, os
from anki.lang import _
from anki.upgrade import Upgrader
from anki.importing.anki2 import Anki2Importer

class Anki1Importer(Anki2Importer):

    def run(self):
        u = Upgrader()
        # check
        if not u.check(self.file):
            self.log.append(_(
                "File is damaged; please run Tools>Advanced>Check DB "
                "in Anki 1.2 first."))
            raise Exception("invalidFile")
        # upgrade
        try:
            deck = u.upgrade(self.file)
        except:
            traceback.print_exc()
            self.log.append(traceback.format_exc())
            return
        # merge
        deck.close()
        mdir = self.file.replace(".anki", ".media")
        self.deckPrefix = os.path.basename(self.file).replace(".anki", "")
        self.file = deck.path
        Anki2Importer.run(self, mdir)

