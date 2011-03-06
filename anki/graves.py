# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

# FIXME:
# - check if we have to int(time)
# - port all the code referencing the old tables

import time
from anki.utils import intTime

FACT = 0
CARD = 1
MODEL = 2
MEDIA = 3
GROUP = 4
GROUPCONFIG = 5

def registerOne(db, type, id):
    db.execute("insert into graves values (:t, :id, :ty)",
               t=intTime(), id=id, ty=type)

def registerMany(db, type, ids):
    db.executemany("insert into graves values (:t, :id, :ty)",
                   [{'t':intTime(), 'id':x, 'ty':type} for x in ids])

def forgetAll(db):
    db.execute("delete from graves")
