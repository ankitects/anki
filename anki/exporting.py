# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import itertools, time, re, os, HTMLParser
from operator import itemgetter
from anki.cards import Card
from anki.lang import _
from anki.utils import stripHTML, ids2str, splitFields

# remove beautifulsoup dependency

class Exporter(object):
    def __init__(self, col, did=None):
        self.col = col
        self.did = did

    def exportInto(self, path):
        self._escapeCount = 0
        file = open(path, "wb")
        self.doExport(file)
        file.close()

    def escapeText(self, text):
        "Escape newlines and tabs, and strip Anki HTML."
        text = text.replace("\n", "<br>")
        text = text.replace("\t", " " * 8)
        return text

    def cardIds(self):
        if not self.did:
            cids = self.col.db.list("select id from cards")
        else:
            cids = self.col.decks.cids(self.did, children=True)
        self.count = len(cids)
        return cids

# Cards as TSV
######################################################################

class TextCardExporter(Exporter):

    key = _("Text files (*.txt)")
    ext = ".txt"

    # add option to strip html


    def __init__(self, col):
        Exporter.__init__(self, col)

    def doExport(self, file):
        ids = self.cardIds()
        strids = ids2str(ids)
        cards = self.col.db.all("""
select cards.question, cards.answer, cards.id from cards
where cards.id in %s
order by cards.created""" % strids)
        self.cardTags = dict(self.col.db.all("""
select cards.id, notes.tags from cards, notes
where cards.noteId = notes.id
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

    def tags(self, id):
        return "\t" + ", ".join(parseTags(self.cardTags[id]))

# Notes as TSV
######################################################################

class TextNoteExporter(Exporter):

    key = _("Text files (*.txt)")
    ext = ".txt"

    def __init__(self, col):
        Exporter.__init__(self, col)
        self.includeID = False
        self.includeTags = True

    def doExport(self, file):
        cardIds = self.cardIds()
        data = []
        for id, flds, tags in self.col.db.execute("""
select guid, flds, tags from notes
where id in
(select nid from cards
where cards.id in %s)""" % ids2str(cardIds)):
            row = []
            # note id
            if self.includeID:
                row.append(str(id))
            # fields
            row.extend([self.escapeText(f) for f in splitFields(flds)])
            # tags
            if self.includeTags:
                row.append(tags)
            data.append("\t".join(row))
        self.count = len(data)
        out = "\n".join(data)
        file.write(out.encode("utf-8"))

    def tags(self, id):
        if self.includeTags:
            return "\t" + self.noteTags[id]
        return ""

# Anki collection exporter
######################################################################

class AnkiExporter(Exporter):

    key = _("Anki Collection (*.anki)")
    ext = ".anki"

    def __init__(self, col):
        Exporter.__init__(self, col)
        self.includeSchedulingInfo = False
        self.includeMedia = True

    def exportInto(self, path):
        n = 3
        if not self.includeSchedulingInfo:
            n += 1
        try:
            os.unlink(path)
        except (IOError, OSError):
            pass
        self.newCol = DeckStorage.Deck(path)
        client = SyncClient(self.col)
        server = SyncServer(self.newDeck)
        client.setServer(server)
        client.localTime = self.col.modified
        client.remoteTime = 0
        self.col.db.flush()
        # set up a custom change list and sync
        lsum = self.localSummary()
        rsum = server.summary(0)
        payload = client.genPayload((lsum, rsum))
        res = server.applyPayload(payload)
        if not self.includeSchedulingInfo:
            self.newCol.resetCards()
        # media
        if self.includeMedia:
            server.col.mediaPrefix = ""
            copyLocalMedia(client.col, server.col)
        # need to save manually
        self.newCol.rebuildCounts()
        # FIXME
        #self.exportedCards = self.newCol.cardCount
        self.newCol.crt = 0
        self.newCol.db.commit()
        self.newCol.close()

    def localSummary(self):
        cardIds = self.cardIds()
        cStrIds = ids2str(cardIds)
        cards = self.col.db.all("""
select id, modified from cards
where id in %s""" % cStrIds)
        notes = self.col.db.all("""
select notes.id, notes.modified from cards, notes where
notes.id = cards.noteId and
cards.id in %s""" % cStrIds)
        models = self.col.db.all("""
select models.id, models.modified from models, notes where
notes.modelId = models.id and
notes.id in %s""" % ids2str([f[0] for f in notes]))
        media = self.col.db.all("""
select id, modified from media""")
        return {
            # cards
            "cards": cards,
            "delcards": [],
            # notes
            "notes": notes,
            "delnotes": [],
            # models
            "models": models,
            "delmodels": [],
            # media
            "media": media,
            "delmedia": [],
            }

# Export modules
##########################################################################

def exporters():
    return (
        (_("Anki Deck (*.anki)"), AnkiExporter),
        (_("Cards in tab-separated text file (*.txt)"), TextCardExporter),
        (_("Notes in tab-separated text file (*.txt)"), TextNoteExporter))
