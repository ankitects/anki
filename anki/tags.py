# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson
from anki.utils import intTime, ids2str

"""
Anki maintains a cache of used tags so it can quickly present a list of tags
for autocomplete and in the browser. For efficiency, deletions are not
tracked, so unused tags can only be removed from the list with a DB check.

This module manages the tag cache and tags for facts.
"""

class TagManager(object):

    # Registry save/load
    #############################################################

    def __init__(self, deck):
        self.deck = deck

    def load(self, json):
        self.tags = simplejson.loads(json)
        self.changed = False

    def flush(self):
        if self.changed:
            self.deck.db.execute("update deck set tags=?",
                                 simplejson.dumps(self.tags))

    # Registering and fetching tags
    #############################################################

    def register(self, tags, usn=None):
        "Given a list of tags, add any missing ones to tag registry."
        # case is stored as received, so user can create different case
        # versions of the same tag if they ignore the qt autocomplete.
        for t in tags:
            if t not in self.tags:
                self.tags[t] = self.deck.usn() if usn is None else usn
                self.changed = True

    def all(self):
        return self.tags.keys()

    def registerFacts(self, fids=None):
        "Add any missing tags from facts to the tags list."
        # when called without an argument, the old list is cleared first.
        if fids:
            lim = " where id in " + ids2str(fids)
        else:
            lim = ""
            self.tags = {}
            self.changed = True
        self.register(set(self.split(
            " ".join(self.deck.db.list("select distinct tags from facts"+lim)))))

    def allItems(self):
        return self.tags.items()

    def save(self):
        self.changed = True

    # Bulk addition/removal from facts
    #############################################################

    def bulkAdd(self, ids, tags, add=True):
        "Add tags in bulk. TAGS is space-separated."
        newTags = self.split(tags)
        if not newTags:
            return
        # cache tag names
        self.register(newTags)
        # find facts missing the tags
        if add:
            l = "tags not "
            fn = self.addToStr
        else:
            l = "tags "
            fn = self.remFromStr
        lim = " or ".join(
            [l+"like :_%d" % c for c, t in enumerate(newTags)])
        res = self.deck.db.all(
            "select id, tags from facts where id in %s and %s" % (
                ids2str(ids), lim),
            **dict([("_%d" % x, '%% %s %%' % y)
                    for x, y in enumerate(newTags)]))
        # update tags
        fids = []
        def fix(row):
            fids.append(row[0])
            return {'id': row[0], 't': fn(tags, row[1]), 'n':intTime(),
                'u':self.deck.usn()}
        self.deck.db.executemany(
            "update facts set tags=:t,mod=:n,usn=:u where id = :id",
            [fix(row) for row in res])

    def bulkRem(self, ids, tags):
        self.bulkAdd(ids, tags, False)

    # String-based utilities
    ##########################################################################

    def split(self, tags):
        "Parse a string and return a list of tags."
        return [t for t in tags.split(" ") if t]

    def join(self, tags):
        "Join tags into a single string, with leading and trailing spaces."
        if not tags:
            return u""
        return u" %s " % u" ".join(tags)

    def addToStr(self, addtags, tags):
        "Add tags if they don't exist, and canonify."
        currentTags = self.split(tags)
        for tag in self.split(addtags):
            if not self.inList(tag, currentTags):
                currentTags.append(tag)
        return self.join(self.canonify(currentTags))

    def remFromStr(self, deltags, tags):
        "Delete tags if they don't exists."
        currentTags = self.split(tags)
        for tag in self.split(deltags):
            # find tags, ignoring case
            remove = []
            for tx in currentTags:
                if tag.lower() == tx.lower():
                    remove.append(tx)
            # remove them
            for r in remove:
                currentTags.remove(r)
        return self.join(currentTags)

    # List-based utilities
    ##########################################################################

    def canonify(self, tagList):
        "Strip duplicates and sort."
        return sorted(set(tagList))

    def inList(self, tag, tags):
        "True if TAG is in TAGS. Ignore case."
        return tag.lower() in [t.lower() for t in tags]

    # Tag-based selective study
    ##########################################################################

    def selTagFids(self, yes, no):
        l = []
        # find facts that match yes
        lim = ""
        args = []
        query = "select id from facts"
        if not yes and not no:
            pass
        else:
            if yes:
                lim += " or ".join(["tags like ?" for t in yes])
                args += ['%% %s %%' % t for t in yes]
            if no:
                lim2 = " and ".join(["tags not like ?" for t in no])
                if lim:
                    lim = "(%s) and %s" % (lim, lim2)
                else:
                    lim = lim2
                args += ['%% %s %%' % t for t in no]
            query += " where " + lim
        return self.deck.db.list(query, *args)

    def setGroupForTags(self, yes, no, gid):
        fids = self.selTagFids(yes, no)
        self.deck.db.execute(
            "update cards set gid=?,mod=?,usn=? where fid in "+ids2str(fids),
            gid, intTime(), self.deck.usn())
