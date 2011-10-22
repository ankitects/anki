# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson, copy
from anki.utils import intTime, ids2str
from anki.consts import *
from anki.lang import _

# fixmes:
# - make sure users can't set grad interval < 1
# - make sure lists like new[delays] are  not being shared by multiple groups
# - make sure all children have parents (create as necessary)
# - when renaming a group, top level properties should be added or removed as
#   appropriate

# notes:
# - it's difficult to enforce valid gids for models/facts/cards, as we
#   may update the gid locally only to have it overwritten by a more recent
#   change from somewhere else. to avoid this, we allow invalid gid
#   references, and treat any invalid gids as the default group.
# - deletions of group config force a full sync

# these are a cache of the current day's reviews. they may be wrong after a
# sync merge if someone reviewed from two locations
defaultGroup = {
    'newToday': [0, 0], # currentDay, count
    'revToday': [0, 0],
    'lrnToday': [0, 0],
    'timeToday': [0, 0], # time in ms
    'conf': 1,
}

# configuration only available to top level groups
defaultTopConf = {
    'revLim': 100,
    'newSpread': NEW_CARDS_DISTRIBUTE,
    'collapseTime': 1200,
    'repLim': 0,
    'timeLim': 600,
    'curModel': None,
}

# configuration available to all groups
defaultConf = {
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
    },
    'maxTaken': 60,
    'mod': 0,
    'usn': 0,
}

class GroupManager(object):

    # Registry save/load
    #############################################################

    def __init__(self, deck):
        self.deck = deck

    def load(self, groups, gconf):
        self.groups = simplejson.loads(groups)
        self.gconf = simplejson.loads(gconf)
        self.changed = False

    def save(self, g=None):
        "Can be called with either a group or a group configuration."
        if g:
            g['mod'] = intTime()
            g['usn'] = self.deck.usn()
        self.changed = True

    def flush(self):
        if self.changed:
            self.deck.db.execute("update deck set groups=?, gconf=?",
                                 simplejson.dumps(self.groups),
                                 simplejson.dumps(self.gconf))

    # Group save/load
    #############################################################

    def id(self, name, create=True):
        "Add a group with NAME. Reuse group if already exists. Return id as int."
        for id, g in self.groups.items():
            if g['name'].lower() == name.lower():
                return int(id)
        if not create:
            return None
        if "::" not in name:
            # if it's a top level group, it gets the top level config
            g = defaultTopConf.copy()
        else:
            # not top level; ensure all parents exist
            g = {}
            self._ensureParents(name)
        for (k,v) in defaultGroup.items():
            g[k] = v
        g['name'] = name
        while 1:
            id = intTime(1000)
            if str(id) not in self.groups:
                break
        g['id'] = id
        self.groups[str(id)] = g
        self.save(g)
        self.maybeAddToActive()
        return int(id)

    def rem(self, gid, cardsToo=False):
        "Remove the group. If cardsToo, delete any cards inside."
        assert gid != 1
        if not str(gid) in self.groups:
            return
        # delete children first
        for name, id in self.children(gid):
            self.rem(id, cardsToo)
        # delete cards too?
        if cardsToo:
            self.deck.remCards(self.cids(gid))
        # delete the group and add a grave
        del self.groups[str(gid)]
        self.deck._logRem([gid], REM_GROUP)
        self.save()

    def allNames(self):
        "An unsorted list of all group names."
        return [x['name'] for x in self.groups.values()]

    def all(self):
        "A list of all groups."
        return self.groups.values()

    def get(self, gid, default=True):
        id = str(gid)
        if id in self.groups:
            return self.groups[id]
        elif default:
            return self.groups['1']

    def update(self, g):
        "Add or update an existing group. Used for syncing and merging."
        self.groups[str(g['id'])] = g
        self.maybeAddToActive()
        # mark registry changed, but don't bump mod time
        self.save()

    def rename(self, g, newName):
        "Rename group prefix to NAME if not exists. Updates children."
        # make sure target node doesn't already exist
        if newName in self.allNames():
            raise Exception("Group exists")
        # rename children
        for grp in self.all():
            if grp['name'].startswith(g['name'] + "::"):
                grp['name'] = grp['name'].replace(g['name']+ "::",
                                                  newName + "::")
                self.save(grp)
        # then update provided group
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

    # Group configurations
    #############################################################

    def allConf(self):
        "A list of all group config."
        return self.gconf.values()

    def conf(self, gid):
        return self.gconf[str(self.groups[str(gid)]['conf'])]

    def updateConf(self, g):
        self.gconf[str(g['id'])] = g
        self.save()

    def confId(self, name):
        "Create a new configuration and return id."
        c = copy.deepcopy(defaultConf)
        while 1:
            id = intTime(1000)
            if str(id) not in self.gconf:
                break
        c['id'] = id
        self.gconf[str(id)] = c
        self.save(c)
        return id

    def remConf(self, id):
        "Remove a configuration and update all groups using it."
        self.deck.modSchema()
        del self.gconf[str(id)]
        for g in self.all():
            if g['conf'] == id:
                g['conf'] = 1
                self.save(g)

    def setConf(self, grp, id):
        grp['conf'] = id
        self.save(grp)

    # Group utils
    #############################################################

    def name(self, gid):
        return self.get(gid)['name']

    def setGroup(self, cids, gid):
        self.deck.db.execute(
            "update cards set gid=?,usn=?,mod=? where id in "+
            ids2str(cids), gid, self.deck.usn(), intTime())


    def maybeAddToActive(self):
        # since order is important, we can't just append to the end
        self.select(self.selected())

    def sendHome(self, cids):
        self.deck.db.execute("""
update cards set gid=(select gid from facts f where f.id=fid),
usn=?,mod=? where id in %s""" % ids2str(cids),
                             self.deck.usn(), intTime(), gid)

    def cids(self, gid):
        return self.deck.db.list("select id from cards where gid=?", gid)

    # Group selection
    #############################################################

    def top(self):
        "The current top level group as an object."
        g = self.get(self.deck.conf['topGroup'])
        return g

    def active(self):
        "The currrently active gids."
        return self.deck.conf['activeGroups']

    def selected(self):
        "The currently selected gid."
        return self.deck.conf['curGroup']

    def select(self, gid):
        "Select a new branch."
        # save the top level group
        name = self.groups[str(gid)]['name']
        self.deck.conf['topGroup'] = self._topFor(name)
        # current group
        self.deck.conf['curGroup'] = gid
        # and active groups (current + all children)
        actv = self.children(gid)
        actv.sort()
        self.deck.conf['activeGroups'] = [gid] + [a[1] for a in actv]

    def children(self, gid):
        "All children of gid, as (name, id)."
        name = self.get(gid)['name']
        actv = []
        for g in self.all():
            if g['name'].startswith(name + "::"):
                actv.append((g['name'], g['id']))
        return actv

    def parents(self, gid):
        "All parents of gid."
        path = self.get(gid)['name'].split("::")
        return [self.get(x) for x in path[:-1]]

    def _topFor(self, name):
        "The top level gid for NAME."
        path = name.split("::")
        return self.id(path[0])
