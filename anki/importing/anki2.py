# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki import Deck
from anki.utils import intTime
from anki.importing.base import Importer

#
# Import a .anki2 file into the current deck. Used for migration from 1.x,
# shared decks, and import from a packaged deck.
#
# We can't rely on internal ids, so we:
# - compare facts by guid
# - compare models by schema signature
# - compare cards by fact guid + ordinal
# - compare groups by name
#

class Anki2Importer(Importer):

    needMapper = False
    groupPrefix = None
    needCards = True
    flagModels = False

    def run(self, media=None):
        self._prepareDecks()
        if media is not None:
            # Anki1 importer has provided us with a custom media folder
            self.src.media._dir = media
        try:
            self._import()
        finally:
            self.src.close(save=False)

    def _prepareDecks(self):
        self.dst = self.deck
        self.src = Deck(self.file, queue=False)

    def _import(self):
        self._groups = {}
        if self.groupPrefix:
            id = self.dst.groups.id(self.groupPrefix)
            self.dst.groups.select(id)
        self._prepareTS()
        self._prepareModels()
        self._importFacts()
        self._importCards()
        self._importMedia()
        self._postImport()
        self.dst.db.execute("vacuum")
        self.dst.db.execute("analyze")

    # Facts
    ######################################################################
    # - should note new for wizard

    def _importFacts(self):
        # build guid -> (id,mod,mid) hash
        self._facts = {}
        for id, guid, mod, mid in self.dst.db.execute(
            "select id, guid, mod, mid from facts"):
            self._facts[guid] = (id, mod, mid)
        # iterate over source deck
        add = []
        dirty = []
        for fact in self.src.db.execute(
            "select * from facts"):
            # turn the db result into a mutable list
            fact = list(fact)
            guid, mid = fact[1:3]
            # missing from local deck?
            if guid not in self._facts:
                # get corresponding local model
                lmid = self._mid(mid)
                # rewrite internal ids, models, etc
                fact[0] = self.ts()
                fact[2] = lmid
                fact[3] = self._gid(fact[3])
                fact[4] = intTime()
                fact[5] = -1 # usn
                add.append(fact)
                dirty.append(fact[0])
                # note we have the added fact
                self._facts[guid] = (fact[0], fact[4], fact[2])
            else:
                continue #raise Exception("merging facts nyi")
        # add to deck
        self.dst.db.executemany(
            "insert or replace into facts values (?,?,?,?,?,?,?,?,?,?,?)",
            add)
        self.dst.updateFieldCache(dirty)
        self.dst.tags.registerFacts(dirty)

    # Models
    ######################################################################

    def _prepareModels(self):
        "Prepare index of schema hashes."
        self._modelMap = {}

    def _mid(self, mid):
        "Return local id for remote MID."
        # already processed this mid?
        if mid in self._modelMap:
            return self._modelMap[mid]
        src = self.src.models.get(mid).copy()
        if self.flagModels:
            src['needWizard'] = True
        # if it doesn't exist, we'll copy it over, preserving id
        if not self.dst.models.have(mid):
            self.dst.models.update(src)
            # make sure to bump usn
            self.dst.models.save(src)
            self._modelMap[mid] = mid
            return mid
        # if it does exist, do the schema match?
        dst = self.dst.models.get(mid)
        dhash = self.src.models.scmhash(dst)
        if self.src.models.scmhash(src) == dhash:
            # reuse without modification
            self._modelMap[mid] = mid
            return mid
        # try any alternative versions
        vers = src.get("vers")
        for v in vers:
            m = self.src.models.get(v)
            if self.src.models.scmhash(m) == dhash:
                # valid alternate found; use that
                self._modelMap[mid] = m['id']
                return m['id']
        # need to add a new alternate version, with new id
        self.dst.models._add(src)
        if vers:
            dst['vers'].append(src['id'])
        else:
            dst['vers'] = [src['id']]

    # Groups
    ######################################################################

    def _gid(self, gid):
        "Given gid in src deck, return local id."
        # already converted?
        if gid in self._groups:
            return self._groups[gid]
        # get the name in src
        g = self.src.groups.get(gid)
        name = g['name']
        # if there's a prefix, replace the top level group
        if self.groupPrefix:
            tmpname = "::".join(name.split("::")[1:])
            name = self.groupPrefix
            if tmpname:
                name += "::" + name
        # create in local
        newid = self.dst.groups.id(name)
        # add to group map and return
        self._groups[gid] = newid
        return newid

    # Cards
    ######################################################################

    def _importCards(self):
        if not self.needCards:
            return
        # build map of (guid, ord) -> cid
        self._cards = {}
        for guid, ord, cid in self.dst.db.execute(
            "select f.guid, c.ord, c.id from cards c, facts f "
            "where c.fid = f.id"):
            self._cards[(guid, ord)] = cid
        # loop through src
        cards = []
        revlog = []
        print "fixme: need to check schema issues in card import"
        for card in self.src.db.execute(
            "select f.guid, f.mid, c.* from cards c, facts f "
            "where c.fid = f.id"):
            guid = card[0]
            # does the card's fact exist in dst deck?
            if guid not in self._facts:
                continue
            dfid = self._facts[guid]
            # does the fact share the same schema?
            # shash = self._srcModels[card[1]]
            # mid = self._facts[guid][2]
            # if shash != self._dstModels[mid]:
            #     continue
            # does the card already exist in the dst deck?
            ord = card[5]
            if (guid, ord) in self._cards:
                # fixme: in future, could update if newer mod time
                continue
            # doesn't exist. strip off fact info, and save src id for later
            card = list(card[2:])
            scid = card[0]
            # update cid, fid, etc
            card[0] = self.ts()
            card[1] = self._facts[guid][0]
            card[2] = self._gid(card[2])
            card[4] = intTime()
            cards.append(card)
            # we need to import revlog, rewriting card ids
            for rev in self.src.db.execute(
                "select * from revlog where cid = ?", scid):
                rev = list(rev)
                rev[1] = card[0]
                revlog.append(rev)
        # apply
        self.dst.db.executemany("""
insert into cards values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""", cards)
        self.dst.db.executemany("""
insert into revlog values (?,?,?,?,?,?,?,?,?)""", revlog)

    # Media
    ######################################################################

    def _importMedia(self):
        self.src.media.copyTo(self.dst.media.dir())

    # Post-import cleanup
    ######################################################################
    # fixme: we could be handling new card order more elegantly on import

    def _postImport(self):
        if self.needCards:
            # make sure new position is correct
            self.dst.conf['nextPos'] = self.dst.db.scalar(
                "select max(due)+1 from cards where type = 0")
        self.dst.save()
