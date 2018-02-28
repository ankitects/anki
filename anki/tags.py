# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.utils import intTime, ids2str, json
from anki.hooks import runHook
import re

"""
Anki maintains a cache of used tags so it can quickly present a list of tags
for autocomplete and in the browser. For efficiency, deletions are not
tracked, so unused tags can only be removed from the list with a DB check.

This module manages the tag cache and tags for notes.
"""

class TagManager(object):

    # Registry save/load
    #############################################################

    def __init__(self, col):
        self.col = col

    def load(self, json_):
        self.tags = json.loads(json_)
        self.changed = False

    def flush(self):
        if self.changed:
            self.col.db.execute("update col set tags=?",
                                 json.dumps(self.tags))
            self.changed = False

    # Registering and fetching tags
    #############################################################

    def register(self, tags, usn=None):
        "Given a list of tags, add any missing ones to tag registry."
        found = False
        for t in tags:
            if t not in self.tags:
                found = True
                self.tags[t] = self.col.usn() if usn is None else usn
                self.changed = True
        if found:
            runHook("newTag")

    def all(self):
        return self.tags.keys()

    def registerNotes(self, nids=None):
        "Add any missing tags from notes to the tags list."
        # when called without an argument, the old list is cleared first.
        if nids:
            lim = " where id in " + ids2str(nids)
        else:
            lim = ""
            self.tags = {}
            self.changed = True
        self.register(set(self.split(
            " ".join(self.col.db.list("select distinct tags from notes"+lim)))))

    def allItems(self):
        return self.tags.items()

    def save(self):
        self.changed = True

    def byDeck(self, did, children=False):
        basequery = "select n.tags from cards c, notes n WHERE c.nid = n.id"
        if not children:
            query = basequery + " AND c.did=?"
            res = self.col.db.list(query, did)
            return list(set(self.split(" ".join(res))))
        dids = [did]
        for name, id in self.col.decks.children(did):
            dids.append(id)
        query = basequery + " AND c.did IN " + ids2str(dids)
        res = self.col.db.list(query)
        return list(set(self.split(" ".join(res))))

    # Bulk addition/removal from notes
    #############################################################

    def bulkAdd(self, ids, tags, add=True):
        "Add tags in bulk. TAGS is space-separated."
        newTags = self.split(tags)
        if not newTags:
            return
        # cache tag names
        self.register(newTags)
        # find notes missing the tags
        if add:
            l = "tags not "
            fn = self.addToStr
        else:
            l = "tags "
            fn = self.remFromStr
        lim = " or ".join(
            [l+"like :_%d" % c for c, t in enumerate(newTags)])
        res = self.col.db.all(
            "select id, tags from notes where id in %s and (%s)" % (
                ids2str(ids), lim),
            **dict([("_%d" % x, '%% %s %%' % y)
                    for x, y in enumerate(newTags)]))
        # update tags
        nids = []
        def fix(row):
            nids.append(row[0])
            return {'id': row[0], 't': fn(tags, row[1]), 'n':intTime(),
                'u':self.col.usn()}
        self.col.db.executemany(
            "update notes set tags=:t,mod=:n,usn=:u where id = :id",
            [fix(row) for row in res])

    def bulkRem(self, ids, tags):
        self.bulkAdd(ids, tags, False)

    # String-based utilities
    ##########################################################################

    def split(self, tags):
        "Parse a string and return a list of tags."
        return [t for t in tags.replace(u'\u3000', ' ').split(" ") if t]

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
        "Strip duplicates, adjust case to match existing tags, and sort."
        strippedTags = []
        for t in tagList:
            s = re.sub("[\"']", "", t)
            for existingTag in self.tags:
                if s.lower() == existingTag.lower():
                    s = existingTag
            strippedTags.append(s)
        return sorted(set(strippedTags))

    def inList(self, tag, tags):
        "True if TAG is in TAGS. Ignore case."
        return tag.lower() in [t.lower() for t in tags]

    # Sync handling
    ##########################################################################

    def beforeUpload(self):
        for k in self.tags.keys():
            self.tags[k] = 0
        self.save()
