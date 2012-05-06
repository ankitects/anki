# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import itertools, time, re, os, HTMLParser, zipfile
from operator import itemgetter
from anki.cards import Card
from anki.lang import _
from anki.utils import stripHTML, ids2str, splitFields, json
from anki import Collection

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
    pass

#     key = _("Text files (*.txt)")
#     ext = ".txt"

#     def __init__(self, col):
#         Exporter.__init__(self, col)

#     def doExport(self, file):
#         ids = self.cardIds()
#         strids = ids2str(ids)
#         cards = self.col.db.all("""
# select cards.question, cards.answer, cards.id from cards
# where cards.id in %s
# order by cards.created""" % strids)
#         self.cardTags = dict(self.col.db.all("""
# select cards.id, notes.tags from cards, notes
# where cards.noteId = notes.id
# and cards.id in %s
# order by cards.created""" % strids))
#         out = u"\n".join(["%s\t%s%s" % (
#             self.escapeText(c[0], removeFields=True),
#             self.escapeText(c[1], removeFields=True),
#             self.tags(c[2]))
#                           for c in cards])
#         if out:
#             out += "\n"
#         file.write(out.encode("utf-8"))

#     def tags(self, id):
#         return "\t" + ", ".join(parseTags(self.cardTags[id]))

# Notes as TSV
######################################################################

class TextNoteExporter(Exporter):

    key = _("Notes in Plain Text")
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
                row.append(tags.strip())
            data.append("\t".join(row))
        self.count = len(data)
        out = "\n".join(data)
        file.write(out.encode("utf-8"))

# Anki decks
######################################################################
# media files are stored in self.mediaFiles, but not exported.

class AnkiExporter(Exporter):

    key = _("Anki 2.0 Deck")
    ext = ".anki2"

    def __init__(self, col):
        Exporter.__init__(self, col)
        self.includeSched = False
        self.includeMedia = True

    def exportInto(self, path):
        # create a new collection at the target
        try:
            os.unlink(path)
        except (IOError, OSError):
            pass
        self.dst = Collection(path)
        self.src = self.col
        # find cards
        if not self.did:
            cids = self.src.db.list("select id from cards")
        else:
            cids = self.src.decks.cids(self.did, children=True)
        # copy cards, noting used nids
        nids = {}
        data = []
        for row in self.src.db.execute(
            "select * from cards where id in "+ids2str(cids)):
            nids[row[1]] = True
            data.append(row)
        self.dst.db.executemany(
            "insert into cards values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            data)
        # notes
        strnids = ids2str(nids.keys())
        notedata = self.src.db.all("select * from notes where id in "+
                               strnids)
        self.dst.db.executemany(
            "insert into notes values (?,?,?,?,?,?,?,?,?,?,?)",
            notedata)
        # models used by the notes
        mids = self.dst.db.list("select distinct mid from notes where id in "+
                                strnids)
        # card history and revlog
        if self.includeSched:
            data = self.src.db.all(
                "select * from revlog where cid in "+ids2str(cids))
            self.dst.db.executemany(
                "insert into revlog values (?,?,?,?,?,?,?,?,?)",
                data)
        else:
            # need to reset card state
            self.dst.sched.forgetCards(cids)
        # models
        for m in self.src.models.all():
            if int(m['id']) in mids:
                self.dst.models.update(m)
        # decks
        if not self.did:
            dids = []
        else:
            dids = [self.did] + [
                x[1] for x in self.src.decks.children(self.did)]
        dconfs = {}
        for d in self.src.decks.all():
            if d['id'] == 1:
                continue
            if dids and d['id'] not in dids:
                continue
            if not d['dyn'] and d['conf'] != 1:
                dconfs[d['conf']] = True
            self.dst.decks.update(d)
        # copy used deck confs
        for dc in self.src.decks.allConf():
            if dc['id'] in dconfs:
                self.dst.decks.updateConf(dc)
        # find used media
        media = {}
        if self.includeMedia:
            for row in notedata:
                flds = row[6]
                mid = row[2]
                for file in self.src.media.filesInStr(mid, flds):
                    media[file] = True
        self.mediaFiles = media.keys()
        self.dst.crt = self.src.crt
        # todo: tags?
        self.count = self.dst.cardCount()
        self.dst.setMod()
        self.dst.close()

# Packaged Anki decks
######################################################################

class AnkiPackageExporter(AnkiExporter):

    key = _("Packaged Anki Deck")
    ext = ".apkg"

    def __init__(self, col):
        AnkiExporter.__init__(self, col)

    def exportInto(self, path):
        # export into the anki2 file
        colfile = path.replace(".apkg", ".anki2")
        AnkiExporter.exportInto(self, colfile)
        # zip the deck up
        z = zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED)
        z.write(colfile, "collection.anki2")
        # and media
        media = {}
        for c, file in enumerate(self.mediaFiles):
            c = str(c)
            if os.path.exists(file):
                z.write(file, c)
                media[c] = file
        # media map
        z.writestr("media", json.dumps(media))
        z.close()
        # tidy up intermediate files
        os.unlink(colfile)
        os.unlink(path.replace(".apkg", ".media.db"))
        os.rmdir(path.replace(".apkg", ".media"))

# Export modules
##########################################################################

def exporters():
    def id(obj):
        return ("%s (*%s)" % (obj.key, obj.ext), obj)
    return (
        id(TextNoteExporter),
        id(AnkiPackageExporter),
    )
