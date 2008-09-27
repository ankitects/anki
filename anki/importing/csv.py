# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Importing CSV/TSV files
========================
"""
__docformat__ = 'restructuredtext'

import codecs
from anki.importing import Importer, ForeignCard
from anki.lang import _
from anki.errors import *

class TextImporter(Importer):

    patterns = ("\t", ";")

    def __init__(self, *args):
        Importer.__init__(self, *args)
        self.lines = None

    def foreignCards(self):
        self.parseTopLine()
        # process all lines
        log = []
        cards = []
        lineNum = 0
        ignored = 0
        for line in self.lines:
            lineNum += 1
            if not line.strip():
                # ignore blank lines
                continue
            try:
                fields = self.parseLine(line)
            except ValueError:
                log.append(_("Line %(line)d doesn't match pattern '%(pat)s'")
                           % {
                    'line': lineNum,
                    'pat': pattern,
                    })
                ignored += 1
                continue
            if len(fields) != self.numFields:
                log.append(_(
                    "Line %(line)d had %(num1)d fields,"
                    " expected %(num2)d") % {
                    "line": lineNum,
                    "num1": len(fields),
                    "num2": self.numFields,
                    })
                ignored += 1
                continue
            card = self.cardFromFields(fields)
            cards.append(card)
        self.log = log
        self.ignored = ignored
        return cards

    def parseTopLine(self):
        "Parse the top line and determine the pattern and number of fields."
        # load & look for the right pattern
        self.cacheFile()
        # look for the first non-blank line
        l = None
        for line in self.lines:
            ret = line.strip()
            if ret:
                l = line
                break
        if not l:
            raise ImportFormatError(type="emptyFile",
                                    info=_("The file had no non-empty lines."))
        found = False
        for p in self.patterns:
            if p in l:
                pattern = p
                fields = l.split(p)
                numFields = len(fields)
                found = True
                break
        if not found:
            fmtError = _(
                "Couldn't find pattern. The file should be a series "
                "of lines separated by tabs or semicolons.")
            raise ImportFormatError(type="invalidPattern",
                                    info=fmtError)
        self.pattern = pattern
        self.setNumFields(line)

    def cacheFile(self):
        "Read file into self.lines if not already there."
        if not self.lines:
            self.lines = self.readFile()

    def readFile(self):
        f = codecs.open(self.file, encoding="utf-8")
        try:
            data = f.readlines()
        except UnicodeDecodeError, e:
            raise ImportFormatError(type="encodingError",
                                    info=_("The file was not in UTF8 format."))
        if not data:
            return []
        if data[0].startswith(unicode(codecs.BOM_UTF8, "utf8")):
            data[0] = data[0][1:]
        # remove comment char
        lines = [l for l in data if not l.lstrip().startswith("#")]
        return lines

    def fields(self):
        "Number of fields."
        self.parseTopLine()
        return self.numFields

    def setNumFields(self, line):
        self.numFields = len(self.parseLine(line))

    def parseLine(self, line):
        fields = line.split(self.pattern)
        fields = [f.strip() for f in fields]
        return fields

    def cardFromFields(self, fields):
        card = ForeignCard()
        card.fields.extend(fields)
        return card
