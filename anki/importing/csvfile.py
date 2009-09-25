# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Importing CSV/TSV files
========================
"""
__docformat__ = 'restructuredtext'

import codecs, csv, re
from anki.importing import Importer, ForeignCard
from anki.lang import _
from anki.errors import *
from anki.utils import tidyHTML

class TextImporter(Importer):

    needDelimiter = True
    patterns = ("\t", ";")

    def __init__(self, *args):
        Importer.__init__(self, *args)
        self.lines = None
        self.fileobj = None
        self.delimiter = None

    def foreignCards(self):
        self.sniff()
        # process all lines
        log = []
        cards = []
        lineNum = 0
        ignored = 0
        if self.delimiter:
            reader = csv.reader(self.data, delimiter=self.delimiter)
        else:
            reader = csv.reader(self.data, self.dialect)
        for row in reader:
            try:
                row = [unicode(x, "utf-8") for x in row]
            except UnicodeDecodeError, e:
                raise ImportFormatError(
                    type="encodingError",
                    info=_("The file was not in UTF8 format."))
            if len(row) != self.numFields:
                log.append(_(
                    "'%(row)s' had %(num1)d fields, "
                    "expected %(num2)d") % {
                    "row": u" ".join(row),
                    "num1": len(row),
                    "num2": self.numFields,
                    })
                ignored += 1
                continue
            card = self.cardFromFields(row)
            cards.append(card)
        self.log = log
        self.ignored = ignored
        return cards

    def sniff(self):
        "Parse the top line and determine the pattern and number of fields."
        # load & look for the right pattern
        self.cacheFile()

    def cacheFile(self):
        "Read file into self.lines if not already there."
        if not self.fileobj:
            self.openFile()

    def openFile(self):
        self.dialect = None
        self.fileobj = open(self.file, "rbU")
        self.data = self.fileobj.read()
        self.data = re.sub("^ *#.*", "", self.data)
        self.data = [x for x in self.data.split("\n") if x]
        if self.data:
            self.updateDelimiter()
        if not self.dialect and not self.delimiter:
            raise ImportFormatError(
                type="encodingError",
                info=_("Couldn't determine format of file."))

    def updateDelimiter(self):
        def err():
            raise ImportFormatError(
                type="encodingError",
                info=_("File is not encoded in UTF-8."))
        self.dialect = None
        if not self.delimiter:
            try:
                self.dialect = csv.Sniffer().sniff("\n".join(self.data[:10]))
            except:
                try:
                    self.dialect = csv.Sniffer().sniff(self.data[0])
                except:
                    pass
        if self.dialect:
            try:
                reader = csv.reader(self.data, self.dialect)
            except:
                err()
        else:
            if not self.delimiter:
                if "\t" in self.data[0]:
                    self.delimiter = "\t"
                elif ";" in self.data[0]:
                    self.delimiter = ";"
                elif "," in self.data[0]:
                    self.delimiter = ","
                else:
                    self.delimiter = " "
            reader = csv.reader(self.data, delimiter=self.delimiter)
        try:
            self.numFields = len(reader.next())
        except:
            err()

    def fields(self):
        "Number of fields."
        self.sniff()
        return self.numFields

    def cardFromFields(self, fields):
        card = ForeignCard()
        card.fields.extend(fields)
        return card
