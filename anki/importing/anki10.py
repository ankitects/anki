# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Importing Anki 0.9+ decks
==========================
"""
__docformat__ = 'restructuredtext'

from anki import DeckStorage
from anki.importing import Importer
from anki.sync import SyncClient, SyncServer, copyLocalMedia
from anki.lang import _
from anki.utils import ids2str
from anki.deck import NEW_CARDS_RANDOM
import time

class Anki10Importer(Importer):

    needMapper = False

    def doImport(self):
        "Import."
        random = self.deck.newCardOrder == NEW_CARDS_RANDOM
        num = 4
        if random:
            num += 1
        self.deck.startProgress(num)
        self.deck.updateProgress(_("Importing..."))
        src = DeckStorage.Deck(self.file, backup=False)
        client = SyncClient(self.deck)
        server = SyncServer(src)
        client.setServer(server)
        # if there is a conflict, sync local -> src
        client.localTime = self.deck.modified
        client.remoteTime = 0
        src.s.execute("update facts set modified = 1")
        src.s.execute("update models set modified = 1")
        src.s.execute("update cards set modified = 1")
        src.s.execute("update media set created = 1")
        self.deck.s.flush()
        # set up a custom change list and sync
        lsum = client.summary(0)
        self._clearDeleted(lsum)
        rsum = server.summary(0)
        self._clearDeleted(rsum)
        payload = client.genPayload((lsum, rsum))
        # no need to add anything to src
        payload['added-models'] = []
        payload['added-cards'] = []
        payload['added-facts'] = {'facts': [], 'fields': []}
        assert payload['deleted-facts'] == []
        assert payload['deleted-cards'] == []
        assert payload['deleted-models'] == []
        self.deck.updateProgress()
        res = server.applyPayload(payload)
        self.deck.updateProgress()
        client.applyPayloadReply(res)
        copyLocalMedia(server.deck, client.deck)
        # add tags
        self.deck.updateProgress()
        fids = [f[0] for f in res['added-facts']['facts']]
        self.deck.addTags(fids, self.tagsToAdd)
        # mark import material as newly added
        self.deck.s.statement(
            "update cards set modified = :t where id in %s" %
            ids2str([x[0] for x in res['added-cards']]), t=time.time())
        self.deck.s.statement(
            "update facts set modified = :t where id in %s" %
            ids2str([x[0] for x in res['added-facts']['facts']]), t=time.time())
        self.deck.s.statement(
            "update models set modified = :t where id in %s" %
            ids2str([x['id'] for x in res['added-models']]), t=time.time())
        # update total and refresh
        self.total = len(res['added-facts']['facts'])
        src.s.rollback()
        src.engine.dispose()
        # randomize?
        if random:
            self.deck.updateProgress()
            self.deck.randomizeNewCards([x[0] for x in res['added-cards']])
        self.deck.flushMod()
        self.deck.finishProgress()

    def _clearDeleted(self, sum):
        sum['delcards'] = []
        sum['delfacts'] = []
        sum['delmodels'] = []
