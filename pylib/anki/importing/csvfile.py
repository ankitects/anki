# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: disable=invalid-name

from __future__ import annotations

import csv
import re
from typing import Any, Optional, TextIO

from anki.collection import Collection
from anki.importing.noteimp import ForeignNote, NoteImporter


class TextImporter(NoteImporter):
    needDelimiter = True
    patterns = "\t|,;:"

    def __init__(self, col: Collection, file: str) -> None:
        NoteImporter.__init__(self, col, file)
        self.lines = None
        self.fileobj: Optional[TextIO] = None
        self.delimiter: Optional[str] = None
        self.tagsToAdd: list[str] = []
        self.numFields = 0
        self.dialect: Optional[Any]
        self.data: Optional[str | list[str]]

    def foreignNotes(self) -> list[ForeignNote]:
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
                if len(row) != self.numFields:
                    if row:
                        log.append(
                            self.col.tr.importing_rows_had_num1d_fields_expected_num2d(
                                row=" ".join(row),
                                found=len(row),
                                expected=self.numFields,
                            )
                        )
                        ignored += 1
                    continue
                note = self.noteFromFields(row)
                notes.append(note)
        except csv.Error as e:
            log.append(self.col.tr.importing_aborted(val=str(e)))
        self.log = log
        self.ignored = ignored
        self.close()
        return notes

    def open(self) -> None:
        "Parse the top line and determine the pattern and number of fields."
        # load & look for the right pattern
        self.cacheFile()

    def cacheFile(self) -> None:
        "Read file into self.lines if not already there."
        if not self.fileobj:
            self.openFile()

    def openFile(self) -> None:
        self.dialect = None
        self.fileobj = open(self.file, encoding="utf-8-sig")
        self.data = self.fileobj.read()

        def sub(s):
            return re.sub(r"^\#.*$", "__comment", s)

        self.data = [
            f"{sub(x)}\n" for x in self.data.split("\n") if sub(x) != "__comment"
        ]
        if self.data:
            if self.data[0].startswith("tags:"):
                tags = str(self.data[0][5:]).strip()
                self.tagsToAdd = tags.split(" ")
                del self.data[0]
            self.updateDelimiter()
        if not self.dialect and not self.delimiter:
            raise Exception("unknownFormat")

    def updateDelimiter(self) -> None:
        def err():
            raise Exception("unknownFormat")

        self.dialect = None
        sniffer = csv.Sniffer()
        if not self.delimiter:
            try:
                self.dialect = sniffer.sniff("\n".join(self.data[:10]), self.patterns)
            except:
                try:
                    self.dialect = sniffer.sniff(self.data[0], self.patterns)
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
                row = next(reader)
                if row:
                    self.numFields = len(row)
                    break
        except:
            err()
        self.initMapping()

    def fields(self) -> int:
        "Number of fields."
        self.open()
        return self.numFields

    def close(self):
        if self.fileobj:
            self.fileobj.close()
            self.fileobj = None

    def __del__(self):
        self.close()
        zuper = super()
        if hasattr(zuper, "__del__"):
            # pylint: disable=no-member
            zuper.__del__(self)  # type: ignore

    def noteFromFields(self, fields: list[str]) -> ForeignNote:
        note = ForeignNote()
        note.fields.extend([x for x in fields])
        note.tags.extend(self.tagsToAdd)
        return note
