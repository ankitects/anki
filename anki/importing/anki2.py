# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import unicodedata
from anki import Collection
from anki.utils import intTime, splitFields, joinFields
from anki.importing.base import Importer
from anki.lang import _

GUID = 1
MID = 2
MOD = 3

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
        importingV2 = self.file.endswith(".anki21")
        if importingV2 and self.col.schedVer() == 1:
            raise Exception("V2 scheduler must be enabled to import this file.")

        self.dst = self.col
        self.src = Collection(self.file)

        if not importingV2 and self.col.schedVer() != 1:
            # if v2 scheduler enabled, can't import v1 decks that include scheduling
            if self.src.db.scalar("select 1 from cards where queue != 0 limit 1"):
                self.src.close(save=False)
                raise Exception("V2 scheduler can not import V1 decks with scheduling included.")

    def _import(self):
        self._decks = {}
        if self.deckPrefix:
            id = self.dst.decks.id(self.deckPrefix)
            self.dst.decks.select(id)
        self._prepareTS()
        self._prepareModels()
        self._importNotes()
        self._importCards()
        self._importStaticMedia()
        self._postImport()
        self.dst.db.setAutocommit(True)
        self.dst.db.execute("vacuum")
        self.dst.db.execute("analyze")
        self.dst.db.setAutocommit(False)

    # Notes
    ######################################################################

    def _logNoteRow(self, action, noteRow):
        self.log.append("[%s] %s" % (
            action,
            noteRow[6].replace("\x1f", ", ")
        ))

    def _importNotes(self):
        # build guid -> (id,mod,mid) hash & map of existing note ids
        self._notes = {}
        existing = {}
        for id, guid, mod, mid in self.dst.db.execute(
            "select id, guid, mod, mid from notes"):
            self._notes[guid] = (id, mod, mid)
            existing[id] = True
        # we may need to rewrite the guid if the model schemas don't match,
        # so we need to keep track of the changes for the card import stage
        self._changedGuids = {}
        # we ignore updates to changed schemas. we need to note the ignored
        # guids, so we avoid importing invalid cards
        self._ignoredGuids = {}
        # iterate over source collection
        add = []
        update = []
        dirty = []
        usn = self.dst.usn()
        dupesIdentical = []
        dupesIgnored = []
        total = 0
        for note in self.src.db.execute(
            "select * from notes"):
            total += 1
            # turn the db result into a mutable list
            note = list(note)
            shouldAdd = self._uniquifyNote(note)
            if shouldAdd:
                # ensure id is unique
                while note[0] in existing:
                    note[0] += 999
                existing[note[0]] = True
                # bump usn
                note[4] = usn
                # update media references in case of dupes
                note[6] = self._mungeMedia(note[MID], note[6])
                add.append(note)
                dirty.append(note[0])
                # note we have the added the guid
                self._notes[note[GUID]] = (note[0], note[3], note[MID])
            else:
                # a duplicate or changed schema - safe to update?
                if self.allowUpdate:
                    oldNid, oldMod, oldMid = self._notes[note[GUID]]
                    # will update if incoming note more recent
                    if oldMod < note[MOD]:
                        # safe if note types identical
                        if oldMid == note[MID]:
                            # incoming note should use existing id
                            note[0] = oldNid
                            note[4] = usn
                            note[6] = self._mungeMedia(note[MID], note[6])
                            update.append(note)
                            dirty.append(note[0])
                        else:
                            dupesIgnored.append(note)
                            self._ignoredGuids[note[GUID]] = True
                    else:
                        dupesIdentical.append(note)

        self.log.append(_("Notes found in file: %d") % total)

        if dupesIgnored:
            self.log.append(
                _("Notes that could not be imported as note type has changed: %d") %
                len(dupesIgnored))
        if update:
            self.log.append(
                _("Notes updated, as file had newer version: %d") %
                len(update))
        if add:
            self.log.append(
                _("Notes added from file: %d") %
                len(add))
        if dupesIdentical:
            self.log.append(
                _("Notes skipped, as they're already in your collection: %d") %
                len(dupesIdentical))

        self.log.append("")

        if dupesIgnored:
            for row in dupesIgnored:
                self._logNoteRow(_("Skipped"), row)
        if update:
            for row in update:
                self._logNoteRow(_("Updated"), row)
        if add:
            for row in add:
                self._logNoteRow(_("Added"), row)
        if dupesIdentical:
            for row in dupesIdentical:
                self._logNoteRow(_("Identical"), row)

        # export info for calling code
        self.dupes = len(dupesIdentical)
        self.added = len(add)
        self.updated = len(update)
        # add to col
        self.dst.db.executemany(
            "insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)",
            add)
        self.dst.db.executemany(
            "insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)",
            update)
        self.dst.updateFieldCache(dirty)
        self.dst.tags.registerNotes(dirty)

    # determine if note is a duplicate, and adjust mid and/or guid as required
    # returns true if note should be added
    def _uniquifyNote(self, note):
        origGuid = note[GUID]
        srcMid = note[MID]
        dstMid = self._mid(srcMid)
        # duplicate schemas?
        if srcMid == dstMid:
            return origGuid not in self._notes
        # differing schemas and note doesn't exist?
        note[MID] = dstMid
        if origGuid not in self._notes:
            return True
        # schema changed; don't import
        self._ignoredGuids[origGuid] = True
        return False

    # Models
    ######################################################################
    # Models in the two decks may share an ID but not a schema, so we need to
    # compare the field & template signature rather than just rely on ID. If
    # the schemas don't match, we increment the mid and try again, creating a
    # new model if necessary.

    def _prepareModels(self):
        "Prepare index of schema hashes."
        self._modelMap = {}

    def _mid(self, srcMid):
        "Return local id for remote MID."
        # already processed this mid?
        if srcMid in self._modelMap:
            return self._modelMap[srcMid]
        mid = srcMid
        srcModel = self.src.models.get(srcMid)
        srcScm = self.src.models.scmhash(srcModel)
        while True:
            # missing from target col?
            if not self.dst.models.have(mid):
                # copy it over
                model = srcModel.copy()
                model['id'] = mid
                model['usn'] = self.col.usn()
                self.dst.models.update(model)
                break
            # there's an existing model; do the schemas match?
            dstModel = self.dst.models.get(mid)
            dstScm = self.dst.models.scmhash(dstModel)
            if srcScm == dstScm:
                # copy styling changes over if newer
                if srcModel['mod'] > dstModel['mod']:
                    model = srcModel.copy()
                    model['id'] = mid
                    model['usn'] = self.col.usn()
                    self.dst.models.update(model)
                break
            # as they don't match, try next id
            mid += 1
        # save map and return new mid
        self._modelMap[srcMid] = mid
        return mid

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
        # manually create any parents so we can pull in descriptions
        head = ""
        for parent in name.split("::")[:-1]:
            if head:
                head += "::"
            head += parent
            idInSrc = self.src.decks.id(head)
            self._did(idInSrc)
        # if target is a filtered deck, we'll need a new deck name
        deck = self.dst.decks.byName(name)
        if deck and deck['dyn']:
            name = "%s %d" % (name, intTime())
        # create in local
        newid = self.dst.decks.id(name)
        # pull conf over
        if 'conf' in g and g['conf'] != 1:
            conf = self.src.decks.getConf(g['conf'])
            self.dst.decks.save(conf)
            self.dst.decks.updateConf(conf)
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
            if guid in self._changedGuids:
                guid = self._changedGuids[guid]
            if guid in self._ignoredGuids:
                continue
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
            if card[7] in (2, 3) or card[6] == 2:
                card[8] -= aheadBy
            # odue needs updating too
            if card[14]:
                card[14] -= aheadBy
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

    # Media
    ######################################################################

    # note: this func only applies to imports of .anki2. for .apkg files, the
    # apkg importer does the copying
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
            with open(path, "rb") as f:
                return f.read()
        except (IOError, OSError):
            return

    def _srcMediaData(self, fname):
        "Data for FNAME in src collection."
        return self._mediaData(fname, self.src.media.dir())

    def _dstMediaData(self, fname):
        "Data for FNAME in dst collection."
        return self._mediaData(fname, self.dst.media.dir())

    def _writeDstMedia(self, fname, data):
        path = os.path.join(self.dst.media.dir(),
                            unicodedata.normalize("NFC", fname))
        try:
            with open(path, "wb") as f:
                f.write(data)
        except (OSError, IOError):
            # the user likely used subdirectories
            pass

    def _mungeMedia(self, mid, fields):
        fields = splitFields(fields)
        def repl(match):
            fname = match.group("fname")
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

    def _postImport(self):
        for did in list(self._decks.values()):
            self.col.sched.maybeRandomizeDeck(did)
        # make sure new position is correct
        self.dst.conf['nextPos'] = self.dst.db.scalar(
            "select max(due)+1 from cards where type = 0") or 0
        self.dst.save()
