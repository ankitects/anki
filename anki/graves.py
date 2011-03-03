# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

# FIXME:
# - check if we have to int(time)
# - port all the code referencing the old tables

import time
from anki.db import *
from anki.utils import intTime

FACT = 0
CARD = 1
MODEL = 2
MEDIA = 3
GROUP = 4
GROUPCONFIG = 5

gravestonesTable = Table(
    'gravestones', metadata,
    Column('delTime', Integer, nullable=False),
    Column('objectId', Integer, nullable=False),
    Column('type', Integer, nullable=False))

def registerOne(db, type, id):
    db.statement("insert into gravestones values (:t, :id, :ty)",
                 t=intTime(), id=id, ty=type)

def registerMany(db, type, ids):
    db.statements("insert into gravestones values (:t, :id, :ty)",
                 [{'t':intTime(), 'id':x, 'ty':type} for x in ids])

def forgetAll(db):
    db.statement("delete from gravestones")
