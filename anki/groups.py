# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson
from anki.utils import intTime

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
}

defaultData = {
    'activeTags': None,
    'inactiveTags': None,
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
        g = dict(name=name, conf=1, mod=intTime())
        while 1:
            id = str(intTime(1000))
            if id in self.groups:
                continue
            self.groups[id] = g
            self.save(g)
            return int(id)

    def rem(self, gid):
        self.deck.modSchema()
        self.deck.db.execute("update cards set gid = 1 where gid = ?", gid)
        self.deck.db.execute("update facts set gid = 1 where gid = ?", gid)
        self.deck.db.execute("delete from groups where id = ?", gid)
        print "fixme: loop through models and update stale gid references"

    # Utils
    #############################################################

    def name(self, gid):
        return self.groups[str(gid)]['name']

    def conf(self, gid):
        return self.gconf[str(self.groups[str(gid)]['conf'])]

    def setGroup(self, cids, gid):
        self.db.execute(
            "update cards set gid = ? where id in "+ids2str(cids), gid)
