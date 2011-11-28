# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson, copy
from anki.utils import intTime, ids2str
from anki.consts import *
from anki.lang import _

# fixmes:
# - make sure users can't set grad interval < 1
# - make sure lists like new[delays] are  not being shared by multiple decks
# - make sure all children have parents (create as necessary)
# - when renaming a deck, top level properties should be added or removed as
#   appropriate

# notes:
# - it's difficult to enforce valid dids for models/notes/cards, as we
#   may update the did locally only to have it overwritten by a more recent
#   change from somewhere else. to avoid this, we allow invalid did
#   references, and treat any invalid dids as the default deck.
# - deletions of deck config force a full sync

# these are a cache of the current day's reviews. they may be wrong after a
# sync merge if someone reviewed from two locations
defaultDeck = {
    'newToday': [0, 0], # currentDay, count
    'revToday': [0, 0],
    'lrnToday': [0, 0],
    'timeToday': [0, 0], # time in ms
    'conf': 1,
}

# configuration only available to top level decks
defaultTopConf = {
    'revLim': 100,
    'newSpread': NEW_CARDS_DISTRIBUTE,
    'collapseTime': 1200,
    'repLim': 0,
    'timeLim': 600,
    'curModel': None,
}

# configuration available to all decks
defaultConf = {
    'name': _("Default"),
    'new': {
        'delays': [1, 10],
        'ints': [1, 4],
        'initialFactor': 2500,
        'order': NEW_TODAY_ORD,
        'perDay': 20,
    },
    'lapse': {
        'delays': [1, 10],
        'mult': 0,
        'minInt': 1,
        'relearn': True,
        'leechFails': 8,
        # type 0=suspend, 1=tagonly
        'leechAction': 0,
    },
    'cram': {
        'delays': [1, 5, 10],
        'resched': True,
        'reset': True,
        'mult': 0,
        'minInt': 1,
    },
    'rev': {
        'ease4': 1.3,
        'fuzz': 0.05,
        'minSpace': 1,
        'fi': [0.1, 0.1],
    },
    'maxTaken': 60,
    'mod': 0,
    'usn': 0,
}

class DeckManager(object):

    # Registry save/load
    #############################################################

    def __init__(self, col):
        self.col = col

    def load(self, decks, dconf):
        self.decks = simplejson.loads(decks)
        self.dconf = simplejson.loads(dconf)
        self.changed = False

    def save(self, g=None):
        "Can be called with either a deck or a deck configuration."
        if g:
            g['mod'] = intTime()
            g['usn'] = self.col.usn()
        self.changed = True

    def flush(self):
        if self.changed:
            self.col.db.execute("update col set decks=?, dconf=?",
                                 simplejson.dumps(self.decks),
                                 simplejson.dumps(self.dconf))

    # Deck save/load
    #############################################################

    def id(self, name, create=True):
        "Add a deck with NAME. Reuse deck if already exists. Return id as int."
        for id, g in self.decks.items():
            if g['name'].lower() == name.lower():
                return int(id)
        if not create:
            return None
        if "::" not in name:
            # if it's a top level deck, it gets the top level config
            g = defaultTopConf.copy()
        else:
            # not top level; ensure all parents exist
            g = {}
            self._ensureParents(name)
        for (k,v) in defaultDeck.items():
            g[k] = v
        g['name'] = name
        while 1:
            id = intTime(1000)
            if str(id) not in self.decks:
                break
        g['id'] = id
        self.decks[str(id)] = g
        self.save(g)
        self.maybeAddToActive()
        return int(id)

    def rem(self, did, cardsToo=False):
        "Remove the deck. If cardsToo, delete any cards inside."
        assert did != 1
        if not str(did) in self.decks:
            return
        # delete children first
        for name, id in self.children(did):
            self.rem(id, cardsToo)
        # delete cards too?
        if cardsToo:
            self.col.remCards(self.cids(did))
        # delete the deck and add a grave
        del self.decks[str(did)]
        self.col._logRem([did], REM_DECK)
        # ensure we have an active deck
        if did in self.active():
            self.select(int(self.decks.keys()[0]))
        self.save()

    def allNames(self):
        "An unsorted list of all deck names."
        return [x['name'] for x in self.decks.values()]

    def all(self):
        "A list of all decks."
        return self.decks.values()

    def get(self, did, default=True):
        id = str(did)
        if id in self.decks:
            return self.decks[id]
        elif default:
            return self.decks['1']

    def update(self, g):
        "Add or update an existing deck. Used for syncing and merging."
        self.decks[str(g['id'])] = g
        self.maybeAddToActive()
        # mark registry changed, but don't bump mod time
        self.save()

    def rename(self, g, newName):
        "Rename deck prefix to NAME if not exists. Updates children."
        # make sure target node doesn't already exist
        if newName in self.allNames():
            raise Exception("Deck exists")
        # rename children
        for grp in self.all():
            if grp['name'].startswith(g['name'] + "::"):
                grp['name'] = grp['name'].replace(g['name']+ "::",
                                                  newName + "::")
                self.save(grp)
        # adjust top level conf
        if "::" in newName and "::" not in g['name']:
            for k in defaultTopConf.keys():
                del g[k]
        elif "::" not in newName and "::" in g['name']:
            for k,v in defaultTopConf.items():
                g[k] = v
        # adjust name and save
        g['name'] = newName
        self.save(g)
        # finally, ensure we have parents
        self._ensureParents(newName)

    def _ensureParents(self, name):
        path = name.split("::")
        s = ""
        for p in path[:-1]:
            if not s:
                s += p
            else:
                s += "::" + p
            self.id(s)

    # Deck configurations
    #############################################################

    def allConf(self):
        "A list of all deck config."
        return self.dconf.values()

    def conf(self, did):
        return self.dconf[str(self.decks[str(did)]['conf'])]

    def updateConf(self, g):
        self.dconf[str(g['id'])] = g
        self.save()

    def confId(self, name):
        "Create a new configuration and return id."
        c = copy.deepcopy(defaultConf)
        while 1:
            id = intTime(1000)
            if str(id) not in self.dconf:
                break
        c['id'] = id
        c['name'] = name
        self.dconf[str(id)] = c
        self.save(c)
        return id

    def remConf(self, id):
        "Remove a configuration and update all decks using it."
        assert int(id) != 1
        self.col.modSchema()
        del self.dconf[str(id)]
        for g in self.all():
            if str(g['conf']) == str(id):
                g['conf'] = 1
                self.save(g)

    def setConf(self, grp, id):
        grp['conf'] = id
        self.save(grp)

    # Deck utils
    #############################################################

    def name(self, did):
        return self.get(did)['name']

    def setDeck(self, cids, did):
        self.col.db.execute(
            "update cards set did=?,usn=?,mod=? where id in "+
            ids2str(cids), did, self.col.usn(), intTime())


    def maybeAddToActive(self):
        # since order is important, we can't just append to the end
        self.select(self.selected())

    def sendHome(self, cids):
        self.col.db.execute("""
update cards set did=(select did from notes f where f.id=nid),
usn=?,mod=? where id in %s""" % ids2str(cids),
                             self.col.usn(), intTime(), did)

    def cids(self, did):
        return self.col.db.list("select id from cards where did=?", did)

    # Deck selection
    #############################################################

    def top(self):
        "The current top level deck as an object."
        g = self.get(self.col.conf['topDeck'])
        return g

    def topIds(self):
        "All dids from top level."
        t = self.top()
        return [t['id']] + [a[1] for a in self.children(t['id'])]

    def active(self):
        "The currrently active dids."
        return self.col.conf['activeDecks']

    def selected(self):
        "The currently selected did."
        return self.col.conf['curDeck']

    def current(self):
        return self.get(self.selected())

    def select(self, did):
        "Select a new branch."
        # save the top level deck
        name = self.decks[str(did)]['name']
        self.col.conf['topDeck'] = self._topFor(name)
        # current deck
        self.col.conf['curDeck'] = did
        # and active decks (current + all children)
        actv = self.children(did)
        actv.sort()
        self.col.conf['activeDecks'] = [did] + [a[1] for a in actv]

    def children(self, did):
        "All children of did, as (name, id)."
        name = self.get(did)['name']
        actv = []
        for g in self.all():
            if g['name'].startswith(name + "::"):
                actv.append((g['name'], g['id']))
        return actv

    def parents(self, did):
        "All parents of did."
        path = self.get(did)['name'].split("::")
        return [self.get(x) for x in path[:-1]]

    def _topFor(self, name):
        "The top level did for NAME."
        path = name.split("::")
        return self.id(path[0])
