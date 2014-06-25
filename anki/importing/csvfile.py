# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import codecs
import csv
import re

from anki.importing.noteimp import NoteImporter, ForeignNote
from anki.lang import _


class TextImporter(NoteImporter):

    needDelimiter = True
    patterns = ("\t", ";")

    def __init__(self, *args):
        NoteImporter.__init__(self, *args)
        self.lines = None
        self.fileobj = None
        self.delimiter = None
        self.tagsToAdd = []

    def foreignNotes(self):
        self.open()
        # process all lines
        log = []
        notes = []
        lineNum = 0
        ignored = 0
        if self.delimiter:
            reader = csv.reader(self.data, delimiter=self.delimiter, doublequote=True)
        else:
            reader = csv.reader(self.data, self.dialect, doublequote=True)
        try:
            for row in reader:
                row = [unicode(x, "utf-8") for x in row]
                if len(row) != self.numFields:
                    if row:
                        log.append(_(
                            "'%(row)s' had %(num1)d fields, "
                            "expected %(num2)d") % {
                            "row": u" ".join(row),
                            "num1": len(row),
                            "num2": self.numFields,
                            })
                        ignored += 1
                    continue
                note = self.noteFromFields(row)
                notes.append(note)
        except (csv.Error), e:
            log.append(_("Aborted: %s") % str(e))
        self.log = log
        self.ignored = ignored
        self.fileobj.close()
        return notes

    def open(self):
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
        if self.data.startswith(codecs.BOM_UTF8):
            self.data = self.data[len(codecs.BOM_UTF8):]
        def sub(s):
            return re.sub("^\#.*$", "__comment", s)
        self.data = [sub(x)+"\n" for x in self.data.split("\n") if sub(x) != "__comment"]
        if self.data:
            if self.data[0].startswith("tags:"):
                tags = unicode(self.data[0][5:], "utf8").strip()
                self.tagsToAdd = tags.split(" ")
                del self.data[0]
            self.updateDelimiter()
        if not self.dialect and not self.delimiter:
            raise Exception("unknownFormat")

    def updateDelimiter(self):
        def err():
            raise Exception("unknownFormat")
        self.dialect = None
        sniffer = csv.Sniffer()
        delims = [',', '\t', ';', ':']
        if not self.delimiter:
            try:
                self.dialect = sniffer.sniff("\n".join(self.data[:10]),
                                             delims)
            except:
                try:
                    self.dialect = sniffer.sniff(self.data[0], delims)
                except:
                    pass
        if self.dialect:
            try:
                reader = csv.reader(self.data, self.dialect, doublequote=True)
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
            reader = csv.reader(self.data, delimiter=self.delimiter, doublequote=True)
        try:
            while True:
                row = reader.next()
                if row:
                    self.numFields = len(row)
                    break
        except:
            err()
        self.initMapping()

    def fields(self):
        "Number of fields."
        self.open()
        return self.numFields

    def noteFromFields(self, fields):
        note = ForeignNote()
        note.fields.extend([x for x in fields])
        note.tags.extend(self.tagsToAdd)
        return note
