# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import zipfile, os
import unicodedata
from anki.utils import tmpfile, json
from anki.importing.anki2 import Anki2Importer

class AnkiPackageImporter(Anki2Importer):

    def run(self):
        # extract the deck from the zip file
        self.zip = z = zipfile.ZipFile(self.file)
        col = z.read("collection.anki2")
        colpath = tmpfile(suffix=".anki2")
        open(colpath, "wb").write(col)
        self.file = colpath
        # we need the media dict in advance, and we'll need a map of fname ->
        # number to use during the import
        self.nameToNum = {}
        dir = self.col.media.dir()
        for k, v in json.loads(z.read("media")).items():
            path = os.path.abspath(os.path.join(dir, v))
            if os.path.commonprefix([path, dir]) != dir:
                raise Exception("Invalid file")

            self.nameToNum[v] = unicodedata.normalize("NFC", k)
        # run anki2 importer
        Anki2Importer.run(self)
        # import static media
        for file, c in self.nameToNum.items():
            if not file.startswith("_") and not file.startswith("latex-"):
                continue
            path = os.path.join(self.col.media.dir(), file)
            if not os.path.exists(path):
                open(path, "wb").write(z.read(c))

    def _srcMediaData(self, fname):
        if fname in self.nameToNum:
            return self.zip.read(self.nameToNum[fname])
        return None
