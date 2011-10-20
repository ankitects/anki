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
#
# When importing facts

class Anki2Importer(Importer):

    needMapper = False
    groupPrefix = None

    def run(self):
        "Import."
        self.dst = self.deck
        self.src = Deck(self.file, queue=False)
        try:
            self._import()
        finally:
            self.src.close(save=False)

    def _import(self):
        self._groups = {}
        self._prepareTS()
        self._prepareModels()
        self._importFacts()
        self._importCards()

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
        self._srcModels = {}
        self._dstModels = {}
        self._dstHashes = {}
        for m in self.dst.models.all():
            h = self.dst.models.scmhash(m)
            mid = int(m['id'])
            self._dstHashes[h] = mid
            self._dstModels[mid] = h
        for m in self.src.models.all():
            mid = int(m['id'])
            self._srcModels[mid] = self.src.models.scmhash(m)

    def _mid(self, mid):
        "Return local id for remote MID."
        hash = self._srcModels[mid]
        dmid = self._dstHashes.get(hash)
        if dmid:
            # dst deck already has this model
            return dmid
        # need to add to local and update index
        m = self.dst.models._add(self.src.models.get(mid))
        h = self.dst.models.scmhash(m)
        mid = int(m['id'])
        self._dstModels[mid] = h
        self._dstHashes[h] = mid
        return mid

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
        # add prefix if necessary
        if self.groupPrefix:
            name = self.groupPrefix + "::" + name
        # create in local
        newid = self.dst.groups.id(name)
        # add to group map and return
        self._groups[gid] = newid
        return newid

    # Cards
    ######################################################################

    def _importCards(self):
        # build map of (guid, ord) -> cid
        self._cards = {}
        for guid, ord, cid in self.dst.db.execute(
            "select f.guid, c.ord, c.id from cards c, facts f "
            "where c.fid = f.id"):
            self._cards[(guid, ord)] = cid
        # loop through src
        cards = []
        revlog = []
        for card in self.src.db.execute(
            "select f.guid, f.mid, c.* from cards c, facts f "
            "where c.fid = f.id"):
            guid = card[0]
            shash = self._srcModels[card[1]]
            # does the card's fact exist in dst deck?
            if guid not in self._facts:
                continue
            dfid = self._facts[guid]
            # does the fact share the same schema?
            mid = self._facts[guid][2]
            if shash != self._dstModels[mid]:
                continue
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
