# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
from anki import Collection
from anki.utils import intTime, splitFields, joinFields, checksum
from anki.importing.base import Importer
from anki.lang import _
from anki.lang import ngettext
from anki.hooks import runFilter

#
# Import a .anki2 file into the current collection. Used for migration from
# 1.x, shared decks, and import from a packaged deck.
#
# We can't rely on internal ids, so we:
# - compare notes by guid
# - compare models by schema signature
# - compare cards by note guid + ordinal
# - compare decks by name
#

class Anki2Importer(Importer):

    needMapper = False
    deckPrefix = None
    allowUpdate = True

    def run(self, media=None):
        self._prepareFiles()
        if media is not None:
            # Anki1 importer has provided us with a custom media folder
            self.src.media._dir = media
        try:
            self._import()
        finally:
            self.src.close(save=False)

    def _prepareFiles(self):
        self.dst = self.col
        self.src = Collection(self.file)

    def _import(self):
        self._decks = {}
        self._prepareDeckPrefix()
        if self.deckPrefix:
            id = self.dst.decks.id(self.deckPrefix)
            self.dst.decks.select(id)
        self._prepareTS()
        self._prepareModels()
        self._importNotes()
        self._importCards()
        self._importStaticMedia()
        self._postImport()
        self.dst.db.execute("vacuum")
        self.dst.db.execute("analyze")

    def _prepareDeckPrefix(self):
        prefix = None
        for deck in self.src.decks.all():
            if str(deck['id']) == "1":
                # we can ignore the default deck if it's empty
                if not self.src.db.scalar(
                    "select 1 from cards where did = ? limit 1", deck['id']):
                    continue
            head = deck['name'].split("::")[0]
            if not prefix:
                prefix = head
            else:
                if prefix != head:
                    return
        self.deckPrefix = runFilter("prepareImportPrefix", prefix)

    # Notes
    ######################################################################
    # - should note new for wizard

    def _importNotes(self):
        # build guid -> (id,mod,mid) hash
        self._notes = {}
        existing = {}
        for id, guid, mod, mid in self.dst.db.execute(
            "select id, guid, mod, mid from notes"):
            self._notes[guid] = (id, mod, mid)
            existing[id] = True
        # iterate over source collection
        add = []
        dirty = []
        usn = self.dst.usn()
        dupes = 0
        for note in self.src.db.execute(
            "select * from notes"):
            # turn the db result into a mutable list
            note = list(note)
            guid, mid = note[1:3]
            # missing from local col?
            if guid not in self._notes:
                # get corresponding local model
                lmid = self._mid(mid)
                # ensure id is unique
                while note[0] in existing:
                    note[0] += 999
                existing[note[0]] = True
                # rewrite internal ids, models, etc
                note[2] = lmid
                note[4] = usn
                # update media references in case of dupes
                note[6] = self._mungeMedia(mid, note[6])
                add.append(note)
                dirty.append(note[0])
                # note we have the added note
                self._notes[guid] = (note[0], note[3], note[2])
            else:
                dupes += 1
                pass
                ## update existing note - not yet tested; for post 2.0
                # newer = note[3] > mod
                # if self.allowUpdate and self._mid(mid) == mid and newer:
                #     localNid = self._notes[guid][0]
                #     note[0] = localNid
                #     note[4] = usn
                #     add.append(note)
                #     dirty.append(note[0])
        if dupes:
            self.log.append(_("Already in collection: %s.") % (ngettext(
                "%d note", "%d notes", dupes) % dupes))
        # add to col
        self.dst.db.executemany(
            "insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)",
            add)
        self.dst.updateFieldCache(dirty)
        self.dst.tags.registerNotes(dirty)

    # Models
    ######################################################################
    # Models in the two decks may share an ID but not a schema, so we need to
    # compare the field & template signature rather than just rely on ID. If
    # we created a new model on a conflict then multiple imports would end up
    # with lots of models however, so we store a list of "alternate versions"
    # of a model in the model, so that importing a model is idempotent.

    def _prepareModels(self):
        "Prepare index of schema hashes."
        self._modelMap = {}

    def _mid(self, mid):
        "Return local id for remote MID."
        # already processed this mid?
        if mid in self._modelMap:
            return self._modelMap[mid]
        src = self.src.models.get(mid).copy()
        # if it doesn't exist, we'll copy it over, preserving id
        if not self.dst.models.have(mid):
            self.dst.models.update(src)
            # if we're importing with a prefix, make the model default to it
            if self.deckPrefix:
                src['did'] = self.dst.decks.current()['id']
                # and give it a unique name if it's not a shared deck
                if self.deckPrefix != "shared":
                    src['name'] += " (%s)" % self.deckPrefix
            # make sure to bump usn
            self.dst.models.save(src)
            self._modelMap[mid] = mid
            return mid
        # if it does exist, do the schema match?
        dst = self.dst.models.get(mid)
        shash = self.src.models.scmhash(src)
        dhash = self.src.models.scmhash(dst)
        if shash == dhash:
            # reuse without modification
            self._modelMap[mid] = mid
            return mid
        # try any alternative versions
        vers = dst.get("vers")
        for v in vers:
            m = self.dst.models.get(v)
            if self.dst.models.scmhash(m) == shash:
                # valid alternate found; use that
                self._modelMap[mid] = m['id']
                return m['id']
        # need to add a new alternate version, with new id
        self.dst.models.add(src)
        if vers:
            dst['vers'].append(src['id'])
        else:
            dst['vers'] = [src['id']]
        self.dst.models.save(dst)
        return src['id']

    # Decks
    ######################################################################

    def _did(self, did):
        "Given did in src col, return local id."
        # already converted?
        if did in self._decks:
            return self._decks[did]
        # get the name in src
        g = self.src.decks.get(did)
        name = g['name']
        # if there's a prefix, replace the top level deck
        if self.deckPrefix:
            tmpname = "::".join(name.split("::")[1:])
            name = self.deckPrefix
            if tmpname:
                name += "::" + tmpname
        # create in local
        newid = self.dst.decks.id(name)
        # pull conf over
        if 'conf' in g and g['conf'] != 1:
            self.dst.decks.updateConf(self.src.decks.getConf(g['conf']))
            g2 = self.dst.decks.get(newid)
            g2['conf'] = g['conf']
            self.dst.decks.save(g2)
        # save desc
        deck = self.dst.decks.get(newid)
        deck['desc'] = g['desc']
        self.dst.decks.save(deck)
        # add to deck map and return
        self._decks[did] = newid
        return newid

    # Cards
    ######################################################################

    def _importCards(self):
        # build map of (guid, ord) -> cid and used id cache
        self._cards = {}
        existing = {}
        for guid, ord, cid in self.dst.db.execute(
            "select f.guid, c.ord, c.id from cards c, notes f "
            "where c.nid = f.id"):
            existing[cid] = True
            self._cards[(guid, ord)] = cid
        # loop through src
        cards = []
        revlog = []
        cnt = 0
        usn = self.dst.usn()
        aheadBy = self.src.sched.today - self.dst.sched.today
        for card in self.src.db.execute(
            "select f.guid, f.mid, c.* from cards c, notes f "
            "where c.nid = f.id"):
            guid = card[0]
            # does the card's note exist in dst col?
            if guid not in self._notes:
                continue
            dnid = self._notes[guid]
            # does the card already exist in the dst col?
            ord = card[5]
            if (guid, ord) in self._cards:
                # fixme: in future, could update if newer mod time
                continue
            # doesn't exist. strip off note info, and save src id for later
            card = list(card[2:])
            scid = card[0]
            # ensure the card id is unique
            while card[0] in existing:
                card[0] += 999
            existing[card[0]] = True
            # update cid, nid, etc
            card[1] = self._notes[guid][0]
            card[2] = self._did(card[2])
            card[4] = intTime()
            card[5] = usn
            # review cards have a due date relative to collection
            if card[7] in (2, 3):
                card[8] -= aheadBy
            # if odid true, convert card from filtered to normal
            if card[15]:
                # odid
                card[15] = 0
                # odue
                card[8] = card[14]
                card[14] = 0
                # queue
                if card[6] == 1: # type
                    card[7] = 0
                else:
                    card[7] = card[6]
                # type
                if card[6] == 1:
                    card[6] = 0
            cards.append(card)
            # we need to import revlog, rewriting card ids and bumping usn
            for rev in self.src.db.execute(
                "select * from revlog where cid = ?", scid):
                rev = list(rev)
                rev[1] = card[0]
                rev[2] = self.dst.usn()
                revlog.append(rev)
            cnt += 1
        # apply
        self.dst.db.executemany("""
insert or ignore into cards values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", cards)
        self.dst.db.executemany("""
insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)""", revlog)
        self.log.append(ngettext("%d card imported.", "%d cards imported.", cnt) % cnt)

    # Media
    ######################################################################

    def _importStaticMedia(self):
        # Import any '_foo' prefixed media files regardless of whether
        # they're used on notes or not
        dir = self.src.media.dir()
        if not os.path.exists(dir):
            return
        for fname in os.listdir(dir):
            if fname.startswith("_") and not self.dst.media.have(fname):
                self._writeDstMedia(fname, self._srcMediaData(fname))

    def _mediaData(self, fname, dir=None):
        if not dir:
            dir = self.src.media.dir()
        path = os.path.join(dir, fname)
        try:
            return open(path, "rb").read()
        except IOError, OSError:
            return

    def _srcMediaData(self, fname):
        "Data for FNAME in src collection."
        return self._mediaData(fname, self.src.media.dir())

    def _dstMediaData(self, fname):
        "Data for FNAME in dst collection."
        return self._mediaData(fname, self.dst.media.dir())

    def _writeDstMedia(self, fname, data):
        path = os.path.join(self.dst.media.dir(), fname)
        open(path, "wb").write(data)

    def _mungeMedia(self, mid, fields):
        fields = splitFields(fields)
        def repl(match):
            fname = match.group(2)
            srcData = self._srcMediaData(fname)
            dstData = self._dstMediaData(fname)
            if not srcData:
                # file was not in source, ignore
                return match.group(0)
            # if model-local file exists from a previous import, use that
            name, ext = os.path.splitext(fname)
            lname = "%s_%s%s" % (name, mid, ext)
            if self.dst.media.have(lname):
                return match.group(0).replace(fname, lname)
            # if missing or the same, pass unmodified
            elif not dstData or srcData == dstData:
                # need to copy?
                if not dstData:
                    self._writeDstMedia(fname, srcData)
                return match.group(0)
            # exists but does not match, so we need to dedupe
            self._writeDstMedia(lname, srcData)
            return match.group(0).replace(fname, lname)
        for i in range(len(fields)):
            fields[i] = self.dst.media.transformNames(fields[i], repl)
        return joinFields(fields)

    # Post-import cleanup
    ######################################################################
    # fixme: we could be handling new card order more elegantly on import

    def _postImport(self):
        # make sure new position is correct
        self.dst.conf['nextPos'] = self.dst.db.scalar(
            "select max(due)+1 from cards where type = 0") or 0
        self.dst.save()
