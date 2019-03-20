# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import copy, operator
import unicodedata
import json

from anki.utils import intTime, ids2str
from anki.hooks import runHook
from anki.consts import *
from anki.lang import _
from anki.errors import DeckRenameError

# fixmes:
# - make sure users can't set grad interval < 1

defaultDeck = {
    'newToday': [0, 0], # currentDay, count
    'revToday': [0, 0],
    'lrnToday': [0, 0],
    'timeToday': [0, 0], # time in ms
    'conf': 1,
    'usn': 0,
    'desc': "",
    'dyn': 0,  # anki uses int/bool interchangably here
    'collapsed': False,
    # added in beta11
    'extendNew': 10,
    'extendRev': 50,
}

defaultDynamicDeck = {
    'newToday': [0, 0],
    'revToday': [0, 0],
    'lrnToday': [0, 0],
    'timeToday': [0, 0],
    'collapsed': False,
    'dyn': 1,
    'desc': "",
    'usn': 0,
    'delays': None,
    'separate': True,
     # list of (search, limit, order); we only use first two elements for now
    'terms': [["", 100, 0]],
    'resched': True,
    'return': True, # currently unused

    # v2 scheduler
    "previewDelay": 10,
}

defaultConf = {
    'name': _("Default"),
    'new': {
        'delays': [1, 10],
        'ints': [1, 4, 7], # 7 is not currently used
        'initialFactor': STARTING_FACTOR,
        'separate': True,
        'order': NEW_CARDS_DUE,
        'perDay': 20,
        # may not be set on old decks
        'bury': False,
    },
    'lapse': {
        'delays': [10],
        'mult': 0,
        'minInt': 1,
        'leechFails': 8,
        # type 0=suspend, 1=tagonly
        'leechAction': 0,
    },
    'rev': {
        'perDay': 200,
        'ease4': 1.3,
        'fuzz': 0.05,
        'minSpace': 1, # not currently used
        'ivlFct': 1,
        'maxIvl': 36500,
        # may not be set on old decks
        'bury': False,
        'hardFactor': 1.2,
    },
    'maxTaken': 60,
    'timer': 0,
    'autoplay': True,
    'replayq': True,
    'mod': 0,
    'usn': 0,
}

class DeckManager:

    # Registry save/load
    #############################################################

    def __init__(self, col):
        self.col = col

    def load(self, decks, dconf):
        self.decks = json.loads(decks)
        self.dconf = json.loads(dconf)
        # set limits to within bounds
        found = False
        for c in list(self.dconf.values()):
            for t in ('rev', 'new'):
                pd = 'perDay'
                if c[t][pd] > 999999:
                    c[t][pd] = 999999
                    self.save(c)
                    found = True
        if not found:
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
                                 json.dumps(self.decks),
                                 json.dumps(self.dconf))
            self.changed = False

    # Deck save/load
    #############################################################

    def id(self, name, create=True, type=None):
        "Add a deck with NAME. Reuse deck if already exists. Return id as int."
        if type is None:
            type = defaultDeck
        name = name.replace('"', '')
        name = unicodedata.normalize("NFC", name)
        for id, g in list(self.decks.items()):
            if unicodedata.normalize("NFC", g['name'].lower()) == name.lower():
                return int(id)
        if not create:
            return None
        g = copy.deepcopy(type)
        if "::" in name:
            # not top level; ensure all parents exist
            name = self._ensureParents(name)
        g['name'] = name
        while 1:
            id = intTime(1000)
            if str(id) not in self.decks:
                break
        g['id'] = id
        self.decks[str(id)] = g
        self.save(g)
        self.maybeAddToActive()
        runHook("newDeck")
        return int(id)

    def rem(self, did, cardsToo=False, childrenToo=True):
        "Remove the deck. If cardsToo, delete any cards inside."
        if str(did) == '1':
            # we won't allow the default deck to be deleted, but if it's a
            # child of an existing deck then it needs to be renamed
            deck = self.get(did)
            if '::' in deck['name']:
                base = deck['name'].split("::")[-1]
                suffix = ""
                while True:
                    # find an unused name
                    name = base + suffix
                    if not self.byName(name):
                        deck['name'] = name
                        self.save(deck)
                        break
                    suffix += "1"
            return
        # log the removal regardless of whether we have the deck or not
        self.col._logRem([did], REM_DECK)
        # do nothing else if doesn't exist
        if not str(did) in self.decks:
            return
        deck = self.get(did)
        if deck['dyn']:
            # deleting a cramming deck returns cards to their previous deck
            # rather than deleting the cards
            self.col.sched.emptyDyn(did)
            if childrenToo:
                for name, id in self.children(did):
                    self.rem(id, cardsToo)
        else:
            # delete children first
            if childrenToo:
                # we don't want to delete children when syncing
                for name, id in self.children(did):
                    self.rem(id, cardsToo)
            # delete cards too?
            if cardsToo:
                # don't use cids(), as we want cards in cram decks too
                cids = self.col.db.list(
                    "select id from cards where did=? or odid=?", did, did)
                self.col.remCards(cids)
        # delete the deck and add a grave
        del self.decks[str(did)]
        # ensure we have an active deck
        if did in self.active():
            self.select(int(list(self.decks.keys())[0]))
        self.save()

    def allNames(self, dyn=True):
        "An unsorted list of all deck names."
        if dyn:
            return [x['name'] for x in list(self.decks.values())]
        else:
            return [x['name'] for x in list(self.decks.values()) if not x['dyn']]

    def all(self):
        "A list of all decks."
        return list(self.decks.values())

    def allIds(self):
        return list(self.decks.keys())

    def collapse(self, did):
        deck = self.get(did)
        deck['collapsed'] = not deck['collapsed']
        self.save(deck)

    def collapseBrowser(self, did):
        deck = self.get(did)
        collapsed = deck.get('browserCollapsed', False)
        deck['browserCollapsed'] = not collapsed
        self.save(deck)

    def count(self):
        return len(self.decks)

    def get(self, did, default=True):
        id = str(did)
        if id in self.decks:
            return self.decks[id]
        elif default:
            return self.decks['1']

    def byName(self, name):
        "Get deck with NAME."
        for m in list(self.decks.values()):
            if m['name'] == name:
                return m

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
            raise DeckRenameError(_("That deck already exists."))
        # make sure we're not nesting under a filtered deck
        for p in self.parentsByName(newName):
            if p['dyn']:
                raise DeckRenameError(_("A filtered deck cannot have subdecks."))
        # ensure we have parents
        newName = self._ensureParents(newName)
        # rename children
        for grp in self.all():
            if grp['name'].startswith(g['name'] + "::"):
                grp['name'] = grp['name'].replace(g['name']+ "::",
                                                  newName + "::", 1)
                self.save(grp)
        # adjust name
        g['name'] = newName
        # ensure we have parents again, as we may have renamed parent->child
        newName = self._ensureParents(newName)
        self.save(g)
        # renaming may have altered active did order
        self.maybeAddToActive()

    def renameForDragAndDrop(self, draggedDeckDid, ontoDeckDid):
        draggedDeck = self.get(draggedDeckDid)
        draggedDeckName = draggedDeck['name']
        ontoDeckName = self.get(ontoDeckDid)['name']

        if ontoDeckDid is None or ontoDeckDid == '':
            if len(self._path(draggedDeckName)) > 1:
                self.rename(draggedDeck, self._basename(draggedDeckName))
        elif self._canDragAndDrop(draggedDeckName, ontoDeckName):
            draggedDeck = self.get(draggedDeckDid)
            draggedDeckName = draggedDeck['name']
            ontoDeckName = self.get(ontoDeckDid)['name']
            assert ontoDeckName.strip()
            self.rename(draggedDeck, ontoDeckName + "::" + self._basename(draggedDeckName))

    def _canDragAndDrop(self, draggedDeckName, ontoDeckName):
        if draggedDeckName == ontoDeckName \
            or self._isParent(ontoDeckName, draggedDeckName) \
            or self._isAncestor(draggedDeckName, ontoDeckName):
            return False
        else:
            return True

    def _isParent(self, parentDeckName, childDeckName):
        return self._path(childDeckName) == self._path(parentDeckName) + [ self._basename(childDeckName) ]

    def _isAncestor(self, ancestorDeckName, descendantDeckName):
        ancestorPath = self._path(ancestorDeckName)
        return ancestorPath == self._path(descendantDeckName)[0:len(ancestorPath)]

    def _path(self, name):
        return name.split("::")
    def _basename(self, name):
        return self._path(name)[-1]

    def _ensureParents(self, name):
        "Ensure parents exist, and return name with case matching parents."
        s = ""
        path = self._path(name)
        if len(path) < 2:
            return name
        for p in path[:-1]:
            if not s:
                s += p
            else:
                s += "::" + p
            # fetch or create
            did = self.id(s)
            # get original case
            s = self.name(did)
        name = s + "::" + path[-1]
        return name

    # Deck configurations
    #############################################################

    def allConf(self):
        "A list of all deck config."
        return list(self.dconf.values())

    def confForDid(self, did):
        deck = self.get(did, default=False)
        assert deck
        if 'conf' in deck:
            conf = self.getConf(deck['conf'])
            conf['dyn'] = False
            return conf
        # dynamic decks have embedded conf
        return deck

    def getConf(self, confId):
        return self.dconf[str(confId)]

    def updateConf(self, g):
        self.dconf[str(g['id'])] = g
        self.save()

    def confId(self, name, cloneFrom=None):
        "Create a new configuration and return id."
        if cloneFrom is None:
            cloneFrom = defaultConf
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
        self.col.modSchema(check=True)
        del self.dconf[str(id)]
        for g in self.all():
            # ignore cram decks
            if 'conf' not in g:
                continue
            if str(g['conf']) == str(id):
                g['conf'] = 1
                self.save(g)

    def setConf(self, grp, id):
        grp['conf'] = id
        self.save(grp)

    def didsForConf(self, conf):
        dids = []
        for deck in list(self.decks.values()):
            if 'conf' in deck and deck['conf'] == conf['id']:
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

    def name(self, did, default=False):
        deck = self.get(did, default=default)
        if deck:
            return deck['name']
        return _("[no deck]")

    def nameOrNone(self, did):
        deck = self.get(did, default=False)
        if deck:
            return deck['name']
        return None

    def setDeck(self, cids, did):
        self.col.db.execute(
            "update cards set did=?,usn=?,mod=? where id in "+
            ids2str(cids), did, self.col.usn(), intTime())

    def maybeAddToActive(self):
        # reselect current deck, or default if current has disappeared
        c = self.current()
        self.select(c['id'])

    def cids(self, did, children=False):
        if not children:
            return self.col.db.list("select id from cards where did=?", did)
        dids = [did]
        for name, id in self.children(did):
            dids.append(id)
        return self.col.db.list("select id from cards where did in "+
                                ids2str(dids))

    def _recoverOrphans(self):
        dids = list(self.decks.keys())
        mod = self.col.db.mod
        self.col.db.execute("update cards set did = 1 where did not in "+
                            ids2str(dids))
        self.col.db.mod = mod

    def _checkDeckTree(self):
        decks = self.col.decks.all()
        decks.sort(key=operator.itemgetter('name'))
        names = set()

        for deck in decks:
            # two decks with the same name?
            if deck['name'] in names:
                print("fix duplicate deck name", deck['name'].encode("utf8"))
                deck['name'] += "%d" % intTime(1000)
                self.save(deck)

            # ensure no sections are blank
            if not all(deck['name'].split("::")):
                print("fix deck with missing sections", deck['name'].encode("utf8"))
                deck['name'] = "recovered%d" % intTime(1000)
                self.save(deck)

            # immediate parent must exist
            if "::" in deck['name']:
                immediateParent = "::".join(deck['name'].split("::")[:-1])
                if immediateParent not in names:
                    print("fix deck with missing parent", deck['name'].encode("utf8"))
                    self._ensureParents(deck['name'])
                    names.add(immediateParent)

            names.add(deck['name'])

    def checkIntegrity(self):
        self._recoverOrphans()
        self._checkDeckTree()

    # Deck selection
    #############################################################

    def active(self):
        "The currrently active dids. Make sure to copy before modifying."
        return self.col.conf['activeDecks']

    def selected(self):
        "The currently selected did."
        return self.col.conf['curDeck']

    def current(self):
        return self.get(self.selected())

    def select(self, did):
        "Select a new branch."
        # make sure arg is an int
        did = int(did)
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

    def childDids(self, did, childMap):
        def gather(node, arr):
            for did, child in node.items():
                arr.append(did)
                gather(child, arr)

        arr = []
        gather(childMap[did], arr)
        return arr

    def childMap(self):
        nameMap = self.nameMap()
        childMap = {}

        # go through all decks, sorted by name
        for deck in sorted(self.all(), key=operator.itemgetter("name")):
            node = {}
            childMap[deck['id']] = node

            # add note to immediate parent
            parts = deck['name'].split("::")
            if len(parts) > 1:
                immediateParent = "::".join(parts[:-1])
                pid = nameMap[immediateParent]['id']
                childMap[pid][deck['id']] = node

        return childMap

    def parents(self, did, nameMap=None):
        "All parents of did."
        # get parent and grandparent names
        parents = []
        for part in self.get(did)['name'].split("::")[:-1]:
            if not parents:
                parents.append(part)
            else:
                parents.append(parents[-1] + "::" + part)
        # convert to objects
        for c, p in enumerate(parents):
            if nameMap:
                deck = nameMap[p]
            else:
                deck = self.get(self.id(p))
            parents[c] = deck
        return parents

    def parentsByName(self, name):
        "All existing parents of name"
        if "::" not in name:
            return []
        names = name.split("::")[:-1]
        head = []
        parents = []

        while names:
            head.append(names.pop(0))
            deck = self.byName("::".join(head))
            if deck:
                parents.append(deck)

        return parents

    def nameMap(self):
        return dict((d['name'], d) for d in self.decks.values())

    # Sync handling
    ##########################################################################

    def beforeUpload(self):
        for d in self.all():
            d['usn'] = 0
        for c in self.allConf():
            c['usn'] = 0
        self.save()

    # Dynamic decks
    ##########################################################################

    def newDyn(self, name):
        "Return a new dynamic deck and set it as the current deck."
        did = self.id(name, type=defaultDynamicDeck)
        self.select(did)
        return did

    def isDyn(self, did):
        return self.get(did)['dyn']
