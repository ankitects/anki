# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import pprint
import re
import sys
import time
from typing import Any, Callable, List, Optional, Tuple

from anki import media_pb2
from anki.consts import *
from anki.latex import render_latex, render_latex_returning_errors
from anki.models import NotetypeId
from anki.sound import SoundOrVideoTag
from anki.template import av_tags_to_native
from anki.utils import intTime


def media_paths_from_col_path(col_path: str) -> Tuple[str, str]:
    media_folder = re.sub(r"(?i)\.(anki2)$", ".media", col_path)
    media_db = f"{media_folder}.db2"
    return (media_folder, media_db)


CheckMediaResponse = media_pb2.CheckMediaResponse


# fixme: look into whether we can drop chdir() below
# - need to check aa89d06304fecd3597da4565330a3e55bdbb91fe
# - and audio handling code


class MediaManager:

    sound_regexps = [r"(?i)(\[sound:(?P<fname>[^]]+)\])"]
    html_media_regexps = [
        # src element quoted case
        r"(?i)(<[img|audio][^>]* src=(?P<str>[\"'])(?P<fname>[^>]+?)(?P=str)[^>]*>)",
        # unquoted case
        r"(?i)(<[img|audio][^>]* src=(?!['\"])(?P<fname>[^ >]+)[^>]*?>)",
        # src element quoted case
        r"(?i)(<object[^>]* data=(?P<str>[\"'])(?P<fname>[^>]+?)(?P=str)[^>]*>)",
        # unquoted case
        r"(?i)(<object[^>]* data=(?!['\"])(?P<fname>[^ >]+)[^>]*?>)",
    ]
    regexps = sound_regexps + html_media_regexps

    def __init__(self, col: anki.collection.Collection, server: bool) -> None:
        self.col = col.weakref()
        self._dir: Optional[str] = None
        if server:
            return
        # media directory
        self._dir = media_paths_from_col_path(self.col.path)[0]
        if not os.path.exists(self._dir):
            os.makedirs(self._dir)
        try:
            self._oldcwd = os.getcwd()
        except OSError:
            # cwd doesn't exist
            self._oldcwd = None
        try:
            os.chdir(self._dir)
        except OSError as exc:
            raise Exception("invalidTempFolder") from exc

    def __repr__(self) -> str:
        d = dict(self.__dict__)
        del d["col"]
        return f"{super().__repr__()} {pprint.pformat(d, width=300)}"

    def connect(self) -> None:
        if self.col.server:
            return
        os.chdir(self._dir)

    def close(self) -> None:
        if self.col.server:
            return
        # change cwd back to old location
        if self._oldcwd:
            try:
                os.chdir(self._oldcwd)
            except:
                # may have been deleted
                pass

    def dir(self) -> Optional[str]:
        return self._dir

    def force_resync(self) -> None:
        try:
            os.unlink(media_paths_from_col_path(self.col.path)[1])
        except FileNotFoundError:
            pass

    def empty_trash(self) -> None:
        self.col._backend.empty_trash()

    def restore_trash(self) -> None:
        self.col._backend.restore_trash()

    def strip_av_tags(self, text: str) -> str:
        return self.col._backend.strip_av_tags(text)

    def _extract_filenames(self, text: str) -> List[str]:
        "This only exists do support a legacy function; do not use."
        out = self.col._backend.extract_av_tags(text=text, question_side=True)
        return [
            x.filename
            for x in av_tags_to_native(out.av_tags)
            if isinstance(x, SoundOrVideoTag)
        ]

    # File manipulation
    ##########################################################################

    def add_file(self, path: str) -> str:
        """Add basename of path to the media folder, renaming if not unique.

        Returns possibly-renamed filename."""
        with open(path, "rb") as f:
            return self.write_data(os.path.basename(path), f.read())

    def write_data(self, desired_fname: str, data: bytes) -> str:
        """Write the file to the media folder, renaming if not unique.

        Returns possibly-renamed filename."""
        return self.col._backend.add_media_file(desired_name=desired_fname, data=data)

    def add_extension_based_on_mime(self, fname: str, content_type: str) -> str:
        "If jpg or png mime, add .png/.jpg if missing extension."
        if not os.path.splitext(fname)[1]:
            # mimetypes is returning '.jpe' even after calling .init(), so we'll do
            # it manually instead
            type_map = {
                "image/jpeg": ".jpg",
                "image/png": ".png",
                "image/svg+xml": ".svg",
            }
            if content_type in type_map:
                fname += type_map[content_type]
        return fname

    def have(self, fname: str) -> bool:
        return os.path.exists(os.path.join(self.dir(), fname))

    def trash_files(self, fnames: List[str]) -> None:
        "Move provided files to the trash."
        self.col._backend.trash_media_files(fnames)

    # String manipulation
    ##########################################################################

    def filesInStr(
        self, mid: NotetypeId, string: str, includeRemote: bool = False
    ) -> List[str]:
        l = []
        model = self.col.models.get(mid)
        # handle latex
        string = render_latex(string, model, self.col)
        # extract filenames
        for reg in self.regexps:
            for match in re.finditer(reg, string):
                fname = match.group("fname")
                isLocal = not re.match("(https?|ftp)://", fname.lower())
                if isLocal or includeRemote:
                    l.append(fname)
        return l

    def transformNames(self, txt: str, func: Callable) -> Any:
        for reg in self.regexps:
            txt = re.sub(reg, func, txt)
        return txt

    def strip(self, txt: str) -> str:
        "Return text with sound and image tags removed."
        for reg in self.regexps:
            txt = re.sub(reg, "", txt)
        return txt

    def escapeImages(self, string: str, unescape: bool = False) -> str:
        "escape_media_filenames alias for compatibility with add-ons."
        return self.escape_media_filenames(string, unescape)

    def escape_media_filenames(self, string: str, unescape: bool = False) -> str:
        "Apply or remove percent encoding to filenames in html tags (audio, image, object)."
        if unescape:
            return self.col._backend.decode_iri_paths(string)
        else:
            return self.col._backend.encode_iri_paths(string)

    # Checking media
    ##########################################################################

    def check(self) -> CheckMediaResponse:
        output = self.col._backend.check_media()
        # files may have been renamed on disk, so an undo at this point could
        # break file references
        self.col.save()
        return output

    def render_all_latex(
        self, progress_cb: Optional[Callable[[int], bool]] = None
    ) -> Optional[Tuple[int, str]]:
        """Render any LaTeX that is missing.

        If a progress callback is provided and it returns false, the operation
        will be aborted.

        If an error is encountered, returns (note_id, error_message)
        """
        last_progress = time.time()
        checked = 0
        for (nid, mid, flds) in self.col.db.execute(
            "select id, mid, flds from notes where flds like '%[%'"
        ):

            model = self.col.models.get(mid)
            _html, errors = render_latex_returning_errors(
                flds, model, self.col, expand_clozes=True
            )
            if errors:
                return (nid, "\n".join(errors))

            checked += 1
            elap = time.time() - last_progress
            if elap >= 0.3 and progress_cb is not None:
                last_progress = intTime()
                if not progress_cb(checked):
                    return None

        return None

    # Legacy
    ##########################################################################

    _illegalCharReg = re.compile(r'[][><:"/?*^\\|\0\r\n]')

    def stripIllegal(self, str: str) -> str:
        # currently used by ankiconnect
        print("stripIllegal() will go away")
        return re.sub(self._illegalCharReg, "", str)

    def hasIllegal(self, s: str) -> bool:
        print("hasIllegal() will go away")
        if re.search(self._illegalCharReg, s):
            return True
        try:
            s.encode(sys.getfilesystemencoding())
        except UnicodeEncodeError:
            return True
        return False

    def findChanges(self) -> None:
        pass

    addFile = add_file

    def writeData(self, opath: str, data: bytes, typeHint: Optional[str] = None) -> str:
        fname = os.path.basename(opath)
        if typeHint:
            fname = self.add_extension_based_on_mime(fname, typeHint)
        return self.write_data(fname, data)
