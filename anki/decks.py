# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson, copy
from anki.utils import intTime, ids2str
from anki.consts import *
from anki.lang import _

# fixmes:
# - make sure users can't set grad interval < 1

defaultDeck = {
    'newToday': [0, 0], # currentDay, count
    'revToday': [0, 0],
    'lrnToday': [0, 0],
    'timeToday': [0, 0], # time in ms
    'conf': 1,
    'usn': 0,
}

defaultConf = {
    'name': _("Default"),
    'new': {
        'delays': [1, 10],
        'ints': [1, 4, 7], # 7 is not currently used
        'initialFactor': 2500,
        'separate': True,
        'order': NEW_CARDS_DUE,
        'perDay': 20,
    },
    'lapse': {
        'delays': [1, 10],
        'mult': 0,
        'minInt': 1,
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
        'perDay': 100,
        'ease4': 1.3,
        'fuzz': 0.05,
        'minSpace': 1,
        'fi': [10, 10],
        'order': REV_CARDS_RANDOM,
    },
    'maxTaken': 60,
    'timer': 0,
    'autoplay': True,
    'desc': "",
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
            self.changed = False

    # Deck save/load
    #############################################################

    def id(self, name, create=True):
        "Add a deck with NAME. Reuse deck if already exists. Return id as int."
        name = name.replace("'", "").replace('"', '')
        for id, g in self.decks.items():
            if g['name'].lower() == name.lower():
                return int(id)
        if not create:
            return None
        g = copy.deepcopy(defaultDeck)
        if "::" in name:
            # not top level; ensure all parents exist
            self._ensureParents(name)
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

    def count(self):
        return len(self.decks)

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

    def confForDid(self, did):
        return self.getConf(self.get(did)['conf'])

    def getConf(self, confId):
        return self.dconf[str(confId)]

    def updateConf(self, g):
        self.dconf[str(g['id'])] = g
        self.save()

    def confId(self, name, cloneFrom=defaultConf):
        "Create a new configuration and return id."
        c = copy.deepcopy(cloneFrom)
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

    def didsForConf(self, conf):
        dids = []
        for deck in self.decks.values():
            if deck['conf'] == conf['id']:
                dids.append(deck['id'])
        return dids

    def restoreToDefault(self, conf):
        oldOrder = conf['new']['order']
        new = copy.deepcopy(defaultConf)
        new['id'] = conf['id']
        new['name'] = conf['name']
        self.dconf[str(conf['id'])] = new
        self.save(new)
        # if it was previously randomized, resort
        if not oldOrder:
            self.col.sched.resortConf(new)

    # Deck utils
    #############################################################

    def name(self, did):
        return self.get(did)['name']

    def setDeck(self, cids, did):
        self.col.db.execute(
            "update cards set did=?,usn=?,mod=? where id in "+
            ids2str(cids), did, self.col.usn(), intTime())

    def maybeAddToActive(self):
        # reselect current deck, or default if current has disappeared
        c = self.current()
        self.select(c['id'])

    def sendHome(self, cids):
        self.col.db.execute("""
update cards set did=(select did from notes f where f.id=nid),
usn=?,mod=? where id in %s""" % ids2str(cids),
                             self.col.usn(), intTime(), did)

    def cids(self, did):
        return self.col.db.list("select id from cards where did=?", did)

    # Deck selection
    #############################################################

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
        name = self.decks[str(did)]['name']
        # current deck
        self.col.conf['curDeck'] = did
        # and active decks (current + all children)
        actv = self.children(did)
        actv.sort()
        self.col.conf['activeDecks'] = [did] + [a[1] for a in actv]
        self.changed = True

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

    # Sync handling
    ##########################################################################

    def beforeUpload(self):
        for d in self.all():
            d['usn'] = 0
        for c in self.allConf():
            c['usn'] = 0
        self.save()
