# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import zipfile
from anki.utils import tmpfile
from anki.importing.anki2 import Anki2Importer

class AnkiPackageImporter(Anki2Importer):

    def run(self):
        # extract the deck from the zip file
        z = zipfile.ZipFile(self.file)
        f = z.open("collection.anki2")
        colpath = tmpfile(suffix=".anki2")
        open(colpath, "w").write(f.read())
        # pass it to the anki2 importer
        self.file = colpath
        Anki2Importer.run(self)
