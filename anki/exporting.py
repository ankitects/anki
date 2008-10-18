# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Exporting support
==============================
"""
__docformat__ = 'restructuredtext'

import itertools, time
from operator import itemgetter
from anki import DeckStorage
from anki.cards import Card
from anki.sync import SyncClient, SyncServer, BulkMediaSyncer
from anki.lang import _
from anki.utils import findTag, parseTags, stripHTML, ids2str
from anki.db import *

class Exporter(object):
    def __init__(self, deck):
        self.deck = deck
        self.limitTags = []

    def exportInto(self, path):
        file = open(path, "wb")
        self.doExport(file)
        file.close()

    def escapeText(self, text):
        "Escape newlines and tabs."
        text = text.replace("\n", "<br>")
        text = text.replace("\t", " " * 8)
        return text

    def cardIds(self):
        "Return all cards, limited by tags."
        tlist = self.deck.tagsList()
        cards = [id for (id, tags, pri) in tlist if self.hasTags(tags)]
        self.count = len(cards)
        return cards

    def hasTags(self, tags):
        tags = parseTags(tags)
        if not self.limitTags:
            return True
        for tag in self.limitTags:
            if findTag(tag, tags):
                return True
        return False

class AnkiExporter(Exporter):

    key = _("Anki decks (*.anki)")
    ext = ".anki"

    def __init__(self, deck):
        Exporter.__init__(self, deck)
        self.includeSchedulingInfo = False

    def exportInto(self, path):
        self.newDeck = DeckStorage.Deck(path)
        client = SyncClient(self.deck)
        server = SyncServer(self.newDeck)
        client.setServer(server)
        client.localTime = self.deck.modified
        client.remoteTime = 0
        self.deck.s.flush()
        # set up a custom change list and sync
        lsum = self.localSummary()
        rsum = server.summary(0)
        payload = client.genPayload((lsum, rsum))
        res = server.applyPayload(payload)
        client.applyPayloadReply(res)
        if not self.includeSchedulingInfo:
            self.newDeck.s.statement("""
update cards set
interval = 0.001,
lastInterval = 0,
due = created,
lastDue = 0,
factor = 2.5,
firstAnswered = 0,
reps = 0,
successive = 0,
averageTime = 0,
reviewTime = 0,
youngEase0 = 0,
youngEase1 = 0,
youngEase2 = 0,
youngEase3 = 0,
youngEase4 = 0,
matureEase0 = 0,
matureEase1 = 0,
matureEase2 = 0,
matureEase3 = 0,
matureEase4 = 0,
yesCount = 0,
noCount = 0,
spaceUntil = 0,
isDue = 1,
relativeDelay = 0,
type = 2,
combinedDue = created,
modified = :now
""", now=time.time())
        # media
        if client.mediaSyncPending:
            bulkClient = BulkMediaSyncer(client.deck)
            bulkServer = BulkMediaSyncer(server.deck)
            bulkClient.server = bulkServer
            bulkClient.sync()
        # need to save manually
        self.newDeck.s.commit()
        self.newDeck.close()

    def localSummary(self):
        cardIds = self.cardIds()
        cStrIds = ids2str(cardIds)
        cards = self.deck.s.all("""
select id, modified from cards
where id in %s""" % cStrIds)
        facts = self.deck.s.all("""
select facts.id, facts.modified from cards, facts where
facts.id = cards.factId and
cards.id in %s""" % cStrIds)
        models = self.deck.s.all("""
select models.id, models.modified from models, facts where
facts.modelId = models.id and
facts.id in %s""" % ids2str([f[0] for f in facts]))
        media = self.deck.s.all("""
select id, created from media""")
        return {
            # cards
            "cards": cards,
            "delcards": [],
            # facts
            "facts": facts,
            "delfacts": [],
            # models
            "models": models,
            "delmodels": [],
            # media
            "media": media,
            "delmedia": [],
            }

class TextCardExporter(Exporter):

    key = _("Text files (*.txt)")
    ext = ".txt"

    def __init__(self, deck):
        Exporter.__init__(self, deck)
        self.includeTags = False

    def doExport(self, file):
        strids = ids2str(self.cardIds())
        cards = self.deck.s.all("""
select cards.question, cards.answer, cards.id from cards
where cards.id in %s""" % strids)
        if self.includeTags:
            self.cardTags = dict(self.deck.s.all("""
select cards.id, cards.tags || "," || facts.tags from cards, facts
where cards.factId = facts.id
and cards.id in %s""" % strids))
        out = u"\n".join(["%s\t%s%s" % (self.escapeText(c[0]),
                                        self.escapeText(c[1]),
                                        self.tags(c[2]))
                          for c in cards])
        if out:
            out += "\n"
        file.write(out.encode("utf-8"))

    def tags(self, id):
        if self.includeTags:
            return "\t" + ", ".join(parseTags(self.cardTags[id]))
        return ""

class TextFactExporter(Exporter):

    key = _("Text files (*.txt)")
    ext = ".txt"

    def __init__(self, deck):
        Exporter.__init__(self, deck)
        self.includeTags = False

    def doExport(self, file):
        cardIds = self.cardIds()
        facts = self.deck.s.all("""
select factId, value from fields
where
factId in
(select distinct facts.id from facts, cards
where facts.id = cards.factId
and cards.id in %s)
order by factId, ordinal""" % ids2str(cardIds))
        txt = ""
        if self.includeTags:
            self.factTags = dict(self.deck.s.all(
                "select id, tags from facts where id in %s" %
                ids2str([fact[0] for fact in facts])))
        out = ["\t".join([self.escapeText(x[1]) for x in ret[1]]) +
               self.tags(ret[0])
               for ret in (itertools.groupby(facts, itemgetter(0)))]
        self.count = len(out)
        out = "\n".join(out)
        file.write(out.encode("utf-8"))

    def tags(self, id):
        if self.includeTags:
            return "\t" + self.factTags[id]
        return ""

# Export modules
##########################################################################

def exporters():
    return (
        (_("Anki deck (*.anki)"), AnkiExporter),
        (_("Cards in tab-separated text file (*.txt)"), TextCardExporter),
        (_("Facts in tab-separated text file (*.txt)"), TextFactExporter))
