# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import sys, os
from anki.utils import findTag, stripHTML
from anki.hooks import addHook
from anki.db import *

cantoneseTag = "Cantonese"
mandarinTag = "Mandarin"
srcField = "Expression"
dstField = "Reading"

class UnihanController(object):

    def __init__(self, target):
        base = os.path.dirname(os.path.abspath(__file__))
        if not os.path.exists(base):
            if sys.platform.startswith("darwin"):
                base = os.path.dirname(sys.argv[0])
            else:
                base = os.path.join(os.path.dirname(sys.argv[0]),
                                    "features")
        self.engine = create_engine("sqlite:///" + os.path.abspath(
            os.path.join(base, "unihan.db")),
                                    echo=False, strategy='threadlocal')
        self.session = sessionmaker(bind=self.engine,
                                    autoflush=False,
                                    transactional=True)
        self.type = target

    def reading(self, text):
        text = stripHTML(text)
        result = []
        s = SessionHelper(self.session())
        for c in text:
            n = ord(c)
            ret = s.scalar("select %s from unihan where id = :id"
                           % self.type, id=n)
            if ret:
                result.append(self.formatMatch(ret))
        return u" ".join(result)

    def formatMatch(self, match):
        m = match.split(" ")
        if len(m) == 1:
            return m[0]
        return "{%s}" % (",".join(m))

# Hooks
##########################################################################

class ChineseGenerator(object):

    def __init__(self):
        self.unihan = None

    def toReading(self, type, val):
        if not self.unihan:
            self.unihan = UnihanController(type)
        else:
            self.unihan.type = type
        return self.unihan.reading(val)

unihan = ChineseGenerator()

def onFocusLost(fact, field):
    if field.name != srcField:
        return
    if findTag(cantoneseTag, fact.model.tags):
        type = "cantonese"
    elif findTag(mandarinTag, fact.model.tags):
        type = "mandarin"
    else:
        return
    try:
        if fact[dstField]:
            return
    except:
        return
    fact[dstField] = unihan.toReading(type, field.value)

addHook('fact.focusLost', onFocusLost)
