# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import zipfile, os
from anki.utils import tmpfile, json
from anki.importing.anki2 import Anki2Importer

class AnkiPackageImporter(Anki2Importer):

    def run(self):
        # extract the deck from the zip file
        z = zipfile.ZipFile(self.file)
        col = z.read("collection.anki2")
        colpath = tmpfile(suffix=".anki2")
        open(colpath, "wb").write(col)
        # pass it to the anki2 importer
        self.file = colpath
        Anki2Importer.run(self)
        # import media
        media = json.loads(z.read("media"))
        for c, file in media.items():
            path = os.path.join(self.col.media.dir(), file)
            if not os.path.exists(path):
                open(path, "wb").write(z.read(c))
