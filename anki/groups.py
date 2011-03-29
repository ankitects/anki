# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import simplejson
from anki.utils import intTime

defaultConf = {
    'new': {
        'delays': [0.5, 3, 10],
        'ints': [1, 7, 4],
        'initialFactor': 2.5,
    },
    'lapse': {
        'delays': [0.5, 3, 10],
        'mult': 0,
        'minInt': 1,
        'relearn': True,
        'leechFails': 16,
        # [type, data], where type 0=suspend, 1=tagonly
        'leechAction': [0],
    },
    'cram': {
        'delays': [0.5, 3, 10],
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
