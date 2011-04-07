# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from anki import Deck
from anki.importing import Importer
from anki.sync import SyncClient, SyncServer, copyLocalMedia
from anki.lang import _
from anki.utils import ids2str
#from anki.deck import NEW_CARDS_RANDOM
import time

class Anki10Importer(Importer):

    needMapper = False

    def doImport(self):
        "Import."
        random = self.deck.newCardOrder == NEW_CARDS_RANDOM
        num = 4
        if random:
            num += 1
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
        self.deck.db.flush()
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
        res = server.applyPayload(payload)
        client.applyPayloadReply(res)
        copyLocalMedia(server.deck, client.deck)
        # add tags
        fids = [f[0] for f in res['added-facts']['facts']]
        self.deck.addTags(fids, self.tagsToAdd)
        # mark import material as newly added
        self.deck.db.execute(
            "update cards set modified = :t where id in %s" %
            ids2str([x[0] for x in res['added-cards']]), t=time.time())
        self.deck.db.execute(
            "update facts set modified = :t where id in %s" %
            ids2str([x[0] for x in res['added-facts']['facts']]), t=time.time())
        self.deck.db.execute(
            "update models set modified = :t where id in %s" %
            ids2str([x['id'] for x in res['added-models']]), t=time.time())
        # update total and refresh
        self.total = len(res['added-facts']['facts'])
        src.s.rollback()
        src.engine.dispose()
        # randomize?
        if random:
            self.deck.randomizeNewCards([x[0] for x in res['added-cards']])
        self.deck.flushMod()

    def _clearDeleted(self, sum):
        sum['delcards'] = []
        sum['delfacts'] = []
        sum['delmodels'] = []
