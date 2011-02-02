# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Exporting support
==============================
"""
__docformat__ = 'restructuredtext'

import itertools, time, re, os, HTMLParser
from operator import itemgetter
from anki import DeckStorage
from anki.cards import Card
from anki.sync import SyncClient, SyncServer, copyLocalMedia
from anki.lang import _
from anki.utils import findTag, parseTags, stripHTML, ids2str
from anki.tags import tagIds
from anki.db import *

class Exporter(object):
    def __init__(self, deck):
        self.deck = deck
        self.limitTags = []
        self.limitCardIds = []

    def exportInto(self, path):
        self._escapeCount = 0
        file = open(path, "wb")
        self.doExport(file)
        file.close()

    def escapeText(self, text, removeFields=False):
        "Escape newlines and tabs, and strip Anki HTML."
        from BeautifulSoup import BeautifulSoup as BS
        text = text.replace("\n", "<br>")
        text = text.replace("\t", " " * 8)
        if removeFields:
            # beautifulsoup is slow
            self._escapeCount += 1
            if self._escapeCount % 100 == 0:
                self.deck.updateProgress()
            try:
                s = BS(text)
                all = s('span', {'class': re.compile("fm.*")})
                for e in all:
                    e.replaceWith("".join([unicode(x) for x in e.contents]))
                text = unicode(s)
            except HTMLParser.HTMLParseError:
                pass
        return text

    def cardIds(self):
        "Return all cards, limited by tags or provided ids."
        if self.limitCardIds:
            return self.limitCardIds
        if not self.limitTags:
            cards = self.deck.s.column0("select id from cards")
        else:
            d = tagIds(self.deck.s, self.limitTags, create=False)
            cards = self.deck.s.column0(
                "select cardId from cardTags where tagid in %s" %
                ids2str(d.values()))
        self.count = len(cards)
        return cards

class AnkiExporter(Exporter):

    key = _("Anki Deck (*.anki)")
    ext = ".anki"

    def __init__(self, deck):
        Exporter.__init__(self, deck)
        self.includeSchedulingInfo = False
        self.includeMedia = True

    def exportInto(self, path):
        n = 3
        if not self.includeSchedulingInfo:
            n += 1
        self.deck.startProgress(n)
        self.deck.updateProgress(_("Exporting..."))
        try:
            os.unlink(path)
        except (IOError, OSError):
            pass
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
        self.deck.updateProgress()
        payload = client.genPayload((lsum, rsum))
        self.deck.updateProgress()
        res = server.applyPayload(payload)
        if not self.includeSchedulingInfo:
            self.deck.updateProgress()
            self.newDeck.s.statement("""
delete from reviewHistory""")
            self.newDeck.s.statement("""
update cards set
interval = 0,
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
type = 2,
relativeDelay = 2,
combinedDue = created,
modified = :now
""", now=time.time())
            self.newDeck.s.statement("""
delete from stats""")
        # media
        if self.includeMedia:
            server.deck.mediaPrefix = ""
            copyLocalMedia(client.deck, server.deck)
        # need to save manually
        self.newDeck.rebuildCounts()
        self.newDeck.updateAllPriorities()
        self.exportedCards = self.newDeck.cardCount
        self.newDeck.utcOffset = -1
        self.newDeck.s.commit()
        self.newDeck.close()
        self.deck.finishProgress()

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
        ids = self.cardIds()
        strids = ids2str(ids)
        self.deck.startProgress((len(ids) + 1) / 50)
        self.deck.updateProgress(_("Exporting..."))
        cards = self.deck.s.all("""
select cards.question, cards.answer, cards.id from cards
where cards.id in %s
order by cards.created""" % strids)
        self.deck.updateProgress()
        if self.includeTags:
            self.cardTags = dict(self.deck.s.all("""
select cards.id, facts.tags from cards, facts
where cards.factId = facts.id
and cards.id in %s
order by cards.created""" % strids))
        out = u"\n".join(["%s\t%s%s" % (
            self.escapeText(c[0], removeFields=True),
            self.escapeText(c[1], removeFields=True),
            self.tags(c[2]))
                          for c in cards])
        if out:
            out += "\n"
        file.write(out.encode("utf-8"))
        self.deck.finishProgress()

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
        self.deck.startProgress()
        self.deck.updateProgress(_("Exporting..."))
        facts = self.deck.s.all("""
select factId, value, facts.created from facts, fields
where
facts.id in
(select distinct factId from cards
where cards.id in %s)
and facts.id = fields.factId
order by factId, ordinal""" % ids2str(cardIds))
        txt = ""
        self.deck.updateProgress()
        if self.includeTags:
            self.factTags = dict(self.deck.s.all(
                "select id, tags from facts where id in %s" %
                ids2str([fact[0] for fact in facts])))
        groups = itertools.groupby(facts, itemgetter(0))
        groups = [[x for x in y[1]] for y in groups]
        groups = [(group[0][2],
                   "\t".join([self.escapeText(x[1]) for x in group]) +
                   self.tags(group[0][0]))
                  for group in groups]
        self.deck.updateProgress()
        groups.sort(key=itemgetter(0))
        out = [ret[1] for ret in groups]
        self.count = len(out)
        out = "\n".join(out)
        file.write(out.encode("utf-8"))
        self.deck.finishProgress()

    def tags(self, id):
        if self.includeTags:
            return "\t" + self.factTags[id]
        return ""

# Export modules
##########################################################################

def exporters():
    return (
        (_("Anki Deck (*.anki)"), AnkiExporter),
        (_("Cards in tab-separated text file (*.txt)"), TextCardExporter),
        (_("Facts in tab-separated text file (*.txt)"), TextFactExporter))
