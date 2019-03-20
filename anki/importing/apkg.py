# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import zipfile, os
import unicodedata
import json
from anki.utils import tmpfile
from anki.importing.anki2 import Anki2Importer

class AnkiPackageImporter(Anki2Importer):

    def run(self):
        # extract the deck from the zip file
        self.zip = z = zipfile.ZipFile(self.file)
        # v2 scheduler?
        try:
            z.getinfo("collection.anki21")
            suffix = ".anki21"
        except KeyError:
            suffix = ".anki2"

        col = z.read("collection"+suffix)
        colpath = tmpfile(suffix=suffix)
        with open(colpath, "wb") as f:
            f.write(col)
        self.file = colpath
        # we need the media dict in advance, and we'll need a map of fname ->
        # number to use during the import
        self.nameToNum = {}
        dir = self.col.media.dir()
        for k, v in list(json.loads(z.read("media").decode("utf8")).items()):
            path = os.path.abspath(os.path.join(dir, v))
            if os.path.commonprefix([path, dir]) != dir:
                raise Exception("Invalid file")

            self.nameToNum[unicodedata.normalize("NFC",v)] = k
        # run anki2 importer
        Anki2Importer.run(self)
        # import static media
        for file, c in list(self.nameToNum.items()):
            if not file.startswith("_") and not file.startswith("latex-"):
                continue
            path = os.path.join(self.col.media.dir(), file)
            if not os.path.exists(path):
                with open(path, "wb") as f:
                    f.write(z.read(c))

    def _srcMediaData(self, fname):
        if fname in self.nameToNum:
            return self.zip.read(self.nameToNum[fname])
        return None
