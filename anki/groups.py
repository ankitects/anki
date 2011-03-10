# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import simplejson, time
from anki.utils import intTime

# maybe define a random cutoff at say +/-30% which controls exit interval
# variation - 30% of 1 day is 0.7 or 1.3 so always 1 day; 30% of 4 days is
# 2.8-5.2, so any time from 3-5 days is acceptable

defaultConf = {
    'new': {
        'delays': [0.5, 3, 10],
        'ints': [1, 7, 4],
        'initialFactor': 2.5,
    },
    'lapse': {
        'delays': [0.5, 3, 10],
        'mult': 0
    },
    'suspendLeeches': True,
    'leechFails': 16,
}

class GroupConfig(object):
    def __init__(self, name):
        self.name = name
        self.id = None
        self.config = defaultConf

    def load(self):
        self.config = simplejson.loads(self._config)
        return self

    def save(self):
        self._config = simplejson.dumps(self.config)
        self.modified = intTime()
