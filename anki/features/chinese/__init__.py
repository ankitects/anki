# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import sys, os, pickle
from anki.features import Feature
from anki.utils import findTag, parseTags, stripHTML
from anki.db import *

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

class ChineseGenerator(Feature):

    def __init__(self):
        self.expressionField = "Expression"
        self.readingField = "Reading"

    def lazyInit(self):
        pass

    def onKeyPress(self, fact, field, value):
        if findTag("Reading source", parseTags(field.fieldModel.features)):
            dst = None
            for field in fact.fields:
                if findTag("Reading destination",
                           parseTags(field.fieldModel.features)):
                    dst = field
                    break
            if not dst:
                return
            self.lazyInit()
            reading = self.unihan.reading(value)
            fact[dst.name] = reading

class CantoneseGenerator(ChineseGenerator):

    def __init__(self):
        ChineseGenerator.__init__(self)
        self.tags = ["Cantonese"]
        self.name = "Reading generation for Cantonese"

    def lazyInit(self):
        if 'unihan' not in self.__dict__:
            self.unihan = UnihanController("cantonese")

class MandarinGenerator(ChineseGenerator):

    def __init__(self):
        ChineseGenerator.__init__(self)
        self.tags = ["Mandarin"]
        self.name = "Reading generation for Mandarin"

    def lazyInit(self):
        if 'unihan' not in self.__dict__:
            self.unihan = UnihanController("mandarin")
