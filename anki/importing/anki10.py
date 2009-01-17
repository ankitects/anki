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
from anki.sync import SyncClient, SyncServer, BulkMediaSyncer
from anki.lang import _

class Anki10Importer(Importer):

    needMapper = False

    def doImport(self):
        "Import."
        self.deck.startProgress(4)
        self.deck.updateProgress(_("Importing..."))
        src = DeckStorage.Deck(self.file)
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
        if client.mediaSyncPending:
            bulkClient = BulkMediaSyncer(client.deck)
            bulkServer = BulkMediaSyncer(server.deck)
            bulkClient.server = bulkServer
            bulkClient.sync()
        # add tags
        self.deck.updateProgress()
        fids = [f[0] for f in res['added-facts']['facts']]
        self.deck.addTags(fids, self.tagsToAdd)
        self.total = len(res['added-facts']['facts'])
        src.s.rollback()
        self.deck.flushMod()
        self.deck.finishProgress()

    def _clearDeleted(self, sum):
        sum['delcards'] = []
        sum['delfacts'] = []
        sum['delmodels'] = []
