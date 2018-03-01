# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re, os, zipfile, shutil, unicodedata
from anki.lang import _
from anki.utils import  ids2str, splitFields, json
from anki.hooks import runHook
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
        "Escape newlines, tabs, CSS and quotechar."
        # fixme: we should probably quote fields with newlines
        # instead of converting them to spaces
        text = text.replace("\n", " ")
        text = text.replace("\t", " " * 8)
        text = re.sub("(?i)<style>.*?</style>", "", text)
        if "\"" in text:
            text = "\"" + text.replace("\"", "\"\"") + "\""
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

    key = _("Cards in Plain Text")
    ext = ".txt"
    hideTags = True

    def __init__(self, col):
        Exporter.__init__(self, col)

    def doExport(self, file):
        ids = sorted(self.cardIds())
        strids = ids2str(ids)
        def esc(s):
            # strip off the repeated question in answer if exists
            s = re.sub("(?si)^.*<hr id=answer>\n*", "", s)
            return self.escapeText(s)
        out = ""
        for cid in ids:
            c = self.col.getCard(cid)
            out += esc(c.q())
            out += "\t" + esc(c.a()) + "\n"
        file.write(out.encode("utf-8"))

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
        notedata = []
        for row in self.src.db.all(
            "select * from notes where id in "+strnids):
            # remove system tags if not exporting scheduling info
            if not self.includeSched:
                row = list(row)
                row[5] = self.removeSystemTags(row[5])
            notedata.append(row)
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
            self.dst.sched.resetCards(cids)
        # models - start with zero
        self.dst.models.models = {}
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
            if str(d['id']) == "1":
                continue
            if dids and d['id'] not in dids:
                continue
            if not d['dyn'] and d['conf'] != 1:
                if self.includeSched:
                    dconfs[d['conf']] = True
            if not self.includeSched:
                # scheduling not included, so reset deck settings to default
                d = dict(d)
                d['conf'] = 1
            self.dst.decks.update(d)
        # copy used deck confs
        for dc in self.src.decks.allConf():
            if dc['id'] in dconfs:
                self.dst.decks.updateConf(dc)
        # find used media
        media = {}
        self.mediaDir = self.src.media.dir()
        if self.includeMedia:
            for row in notedata:
                flds = row[6]
                mid = row[2]
                for file in self.src.media.filesInStr(mid, flds):
                    media[file] = True
            if self.mediaDir:
                for fname in os.listdir(self.mediaDir):
                    if fname.startswith("_"):
                        # Scan all models in mids for reference to fname
                        for m in self.src.models.all():
                            if int(m['id']) in mids:
                                if self._modelHasMedia(m, fname):
                                    media[fname] = True
                                    break
        self.mediaFiles = media.keys()
        self.dst.crt = self.src.crt
        # todo: tags?
        self.count = self.dst.cardCount()
        self.dst.setMod()
        self.postExport()
        self.dst.close()

    def postExport(self):
        # overwrite to apply customizations to the deck before it's closed,
        # such as update the deck description
        pass
    
    def removeSystemTags(self, tags):
        return self.src.tags.remFromStr("marked leech", tags)

    def _modelHasMedia(self, model, fname):
        # First check the styling
        if fname in model["css"]:
            return True
        # If no reference to fname then check the templates as well
        for t in model["tmpls"]:
            if fname in t["qfmt"] or fname in t["afmt"]:
                return True
        return False

# Packaged Anki decks
######################################################################

class AnkiPackageExporter(AnkiExporter):

    key = _("Anki Deck Package")
    ext = ".apkg"

    def __init__(self, col):
        AnkiExporter.__init__(self, col)

    def exportInto(self, path):
        # open a zip file
        z = zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, allowZip64=True)
        # if all decks and scheduling included, full export
        if self.includeSched and not self.did:
            media = self.exportVerbatim(z)
        else:
            # otherwise, filter
            media = self.exportFiltered(z, path)
        # media map
        z.writestr("media", json.dumps(media))
        z.close()

    def exportFiltered(self, z, path):
        # export into the anki2 file
        colfile = path.replace(".apkg", ".anki2")
        AnkiExporter.exportInto(self, colfile)
        z.write(colfile, "collection.anki2")
        # and media
        self.prepareMedia()
        media = {}
        for c, file in enumerate(self.mediaFiles):
            cStr = str(c)
            mpath = os.path.join(self.mediaDir, file)
            if os.path.exists(mpath):
                z.write(mpath, cStr, zipfile.ZIP_STORED)
                media[cStr] = unicodedata.normalize("NFC", file)
                runHook("exportedMediaFiles", c)
        # tidy up intermediate files
        os.unlink(colfile)
        p = path.replace(".apkg", ".media.db2")
        if os.path.exists(p):
            os.unlink(p)
        os.chdir(self.mediaDir)
        shutil.rmtree(path.replace(".apkg", ".media"))
        return media

    def exportVerbatim(self, z):
        # close our deck & write it into the zip file, and reopen
        self.count = self.col.cardCount()
        self.col.close()
        z.write(self.col.path, "collection.anki2")
        self.col.reopen()
        # copy all media
        if not self.includeMedia:
            return {}
        media = {}
        mdir = self.col.media.dir()
        for c, file in enumerate(os.listdir(mdir)):
            cStr = str(c)
            mpath = os.path.join(mdir, file)
            if os.path.exists(mpath):
                z.write(mpath, cStr, zipfile.ZIP_STORED)
                media[cStr] = file
                runHook("exportedMediaFiles", c)

        return media

    def prepareMedia(self):
        # chance to move each file in self.mediaFiles into place before media
        # is zipped up
        pass

# Export modules
##########################################################################

def exporters():
    def id(obj):
        return ("%s (*%s)" % (obj.key, obj.ext), obj)
    exps = [
        id(AnkiPackageExporter),
        id(TextNoteExporter),
        id(TextCardExporter),
    ]
    runHook("exportersList", exps)
    return exps
