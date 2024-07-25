# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: disable=invalid-name
from __future__ import annotations

import json
import os
import unicodedata
import zipfile
from typing import Any

from anki.importing.anki2 import Anki2Importer, MediaMapInvalid
from anki.utils import tmpfile


class AnkiPackageImporter(Anki2Importer):
    nameToNum: dict[str, str]
    zip: zipfile.ZipFile | None

    def run(self) -> None:  # type: ignore
        # extract the deck from the zip file
        self.zip = z = zipfile.ZipFile(self.file)
        # v2 scheduler?
        try:
            z.getinfo("collection.anki21")
            suffix = ".anki21"
        except KeyError:
            suffix = ".anki2"

        col = z.read(f"collection{suffix}")
        colpath = tmpfile(suffix=".anki2")
        with open(colpath, "wb") as f:
            f.write(col)
        self.file = colpath
        # we need the media dict in advance, and we'll need a map of fname ->
        # number to use during the import
        self.nameToNum = {}
        dir = self.col.media.dir()
        try:
            media_dict = json.loads(z.read("media").decode("utf8"))
        except Exception as exc:
            raise MediaMapInvalid() from exc
        for k, v in list(media_dict.items()):
            path = os.path.abspath(os.path.join(dir, v))
            if os.path.commonprefix([path, dir]) != dir:
                raise Exception("Invalid file")

            self.nameToNum[unicodedata.normalize("NFC", v)] = k
        # run anki2 importer
        Anki2Importer.run(self, importing_v2=suffix == ".anki21")
        # import static media
        for file, c in list(self.nameToNum.items()):
            if not file.startswith("_") and not file.startswith("latex-"):
                continue
            path = os.path.join(self.col.media.dir(), file)
            if not os.path.exists(path):
                with open(path, "wb") as f:
                    f.write(z.read(c))

    def _srcMediaData(self, fname: str) -> Any:
        if fname in self.nameToNum:
            return self.zip.read(
                self.nameToNum[fname]
            )  # pytype: disable=attribute-error
        return None
