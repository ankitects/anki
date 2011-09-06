# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson
from anki.utils import intTime
from anki.consts import *

# fixmes:
# - make sure lists like new[delays] are  not being shared by multiple groups
# - make sure all children have parents (create as necessary)
# - when renaming a group, top level properties should be added or removed as
#   appropriate

# configuration only available to top level groups
defaultTopConf = {
    'newPerDay': 20,
    'newToday': [0, 0], # currentDay, count
    'newTodayOrder': NEW_TODAY_ORD,
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
        'ints': [1, 7, 4],
        'initialFactor': 2500,
    },
    'lapse': {
        'delays': [1, 10],
        'mult': 0,
        'minInt': 1,
        'relearn': True,
        'leechFails': 16,
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

    def save(self, g):
        "Can be called with either a group or a group configuration."
        g['mod'] = intTime()
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
            # not top level. calling code should ensure parents already exist?
            g = {}
        g['name'] = name
        g['conf'] = 1
        while 1:
            id = intTime(1000)
            if str(id) in self.groups:
                continue
            g['id'] = id
            self.groups[str(id)] = g
            self.save(g)
            return int(id)

    def rem(self, gid):
        self.deck.modSchema()
        self.deck.db.execute("update cards set gid = 1 where gid = ?", gid)
        self.deck.db.execute("update facts set gid = 1 where gid = ?", gid)
        self.deck.db.execute("delete from groups where id = ?", gid)
        print "fixme: loop through models and update stale gid references"

    def allNames(self):
        "An unsorted list of all group names."
        return [x['name'] for x in self.groups.values()]

    def all(self):
        "A list of all groups."
        return self.groups.values()

    # Group utils
    #############################################################

    def name(self, gid):
        return self.groups[str(gid)]['name']

    def conf(self, gid):
        return self.gconf[str(self.groups[str(gid)]['conf'])]

    def get(self, gid):
        return self.groups[str(gid)]

    def setGroup(self, cids, gid):
        self.db.execute(
            "update cards set gid = ? where id in "+ids2str(cids), gid)

    # Group selection
    #############################################################

    def top(self):
        "The current top level group as an object, and marks as modified."
        g = self.get(self.deck.conf['topGroup'])
        self.save(g)
        return g

    def active(self):
        "The currrently active gids."
        return self.deck.conf['activeGroups']

    def selected(self):
        "The currently selected gid, or None if whole collection."
        return self.deck.conf['curGroup']

    def select(self, gid):
        "Select a new group. If gid is None, select whole collection."
        if not gid:
            self.deck.conf['topGroup'] = 1
            self.deck.conf['curGroup'] = None
            self.deck.conf['activeGroups'] = []
            return
        # save the top level group
        name = self.groups[str(gid)]['name']
        path = name.split("::")
        self.deck.conf['topGroup'] = self.id(path[0])
        # current group
        self.deck.conf['curGroup'] = gid
        # and active groups (current + all children)
        actv = [gid]
        for g in self.all():
            if g['name'].startswith(name + "::"):
                actv.append(g['id'])
        self.deck.conf['activeGroups'] = actv
