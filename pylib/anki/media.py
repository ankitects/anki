# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
The media manager, which owns a collection's media folder and its `.db2`
media database.

Most of the work is done in the backend; this module is a thin layer on top of
it, plus the filename matching that older clients and add-ons still rely on.
"""

from __future__ import annotations

import os
import pprint
import re
import sys
import time
from collections.abc import Callable, Sequence

from anki import media_pb2
from anki._legacy import DeprecatedNamesMixin, deprecated_keywords
from anki.consts import *
from anki.latex import render_latex, render_latex_returning_errors
from anki.models import NotetypeId
from anki.sound import SoundOrVideoTag
from anki.template import av_tags_to_native
from anki.utils import int_time


def media_paths_from_col_path(col_path: str) -> tuple[str, str]:
    """Return the media folder and media database paths for a collection.

    A `collection.anki2` path is turned into a `collection.media` folder
    and a `collection.media.db2` database, matched case-insensitively on the
    `.anki2` suffix.
    """
    media_folder = re.sub(r"(?i)\.(anki2)$", ".media", col_path)
    media_db = f"{media_folder}.db2"
    return (media_folder, media_db)


CheckMediaResponse = media_pb2.CheckMediaResponse


class MediaManager(DeprecatedNamesMixin):
    """Provides access to a collection's media folder.

    The folder is created on demand, and is normally reached through
    `dir()` rather than constructed directly. An instance is available as
    `col.media`.

    Only a subset of the folder is managed here: the media database records
    which files are known and which still need syncing, and
    `check()` compares that against the notes that reference each file.
    """

    sound_regexps = [r"(?i)(\[sound:(?P<fname>[^]]+)\])"]
    html_media_regexps = [
        # src element quoted case
        r"(?i)(<(?:img|audio|source)\b[^>]* src=(?P<str>[\"'])(?P<fname>[^>]+?)(?P=str)[^>]*>)",
        # unquoted case
        r"(?i)(<(?:img|audio|source)\b[^>]* src=(?!['\"])(?P<fname>[^ >]+)[^>]*?>)",
        # src element quoted case
        r"(?i)(<object\b[^>]* data=(?P<str>[\"'])(?P<fname>[^>]+?)(?P=str)[^>]*>)",
        # unquoted case
        r"(?i)(<object\b[^>]* data=(?!['\"])(?P<fname>[^ >]+)[^>]*?>)",
    ]
    regexps = sound_regexps + html_media_regexps

    def __init__(self, col: anki.collection.Collection, server: bool) -> None:
        self.col = col.weakref()
        if server:
            return
        # media directory
        self._dir = media_paths_from_col_path(self.col.path)[0]
        if not os.path.exists(self._dir):
            os.makedirs(self._dir)

    def __repr__(self) -> str:
        dict_ = dict(self.__dict__)
        del dict_["col"]
        return f"{super().__repr__()} {pprint.pformat(dict_, width=300)}"

    def dir(self) -> str:
        """Return the path to the media folder.

        Not available if the collection was opened in server mode, as there is
        no local folder to work with.
        """
        return self._dir

    def force_resync(self) -> None:
        """Delete the media database, so the next check rescans the folder.

        This makes Anki re-checksum every file, so it is only needed when the
        database has become out of step with the folder's contents.
        """
        try:
            os.unlink(media_paths_from_col_path(self.col.path)[1])
        except FileNotFoundError:
            pass

    def empty_trash(self) -> None:
        """Permanently delete everything in the media trash folder."""
        self.col._backend.empty_trash()

    def restore_trash(self) -> None:
        """Move everything in the media trash folder back into the media folder.

        Files whose original name has since been taken are restored under a
        different name.
        """
        self.col._backend.restore_trash()

    def strip_av_tags(self, text: str) -> str:
        """Return `text` with its `[sound:...]` tags removed.

        A `[sound:...]` tag may point at an audio or a video file; both are
        removed, along with any TTS directives.
        """
        return self.col._backend.strip_av_tags(text)

    def _extract_filenames(self, text: str) -> list[str]:
        """This only exists to support a legacy function; do not use."""
        out = self.col._backend.extract_av_tags(text=text, question_side=True)
        return [
            x.filename
            for x in av_tags_to_native(out.av_tags)
            if isinstance(x, SoundOrVideoTag)
        ]

    # File manipulation
    ##########################################################################

    def add_file(self, path: str) -> str:
        """Add the file at `path` to the media folder, under its basename.

        Returns the possibly-renamed filename.
        """
        with open(path, "rb") as file:
            return self.write_data(os.path.basename(path), file.read())

    def write_data(self, desired_fname: str, data: bytes) -> str:
        """Add `data` to the media folder as `desired_fname`.

        If a different file already exists under that name, a hash is appended
        to it instead of overwriting. An identical file is left as it is. The
        file is recorded in the media database, and marked as needing to sync.

        Returns the possibly-renamed filename.
        """
        return self.col._backend.add_media_file(desired_name=desired_fname, data=data)

    def add_extension_based_on_mime(self, fname: str, content_type: str) -> str:
        """Append an extension based on `content_type` if `fname` has none.

        Only the common audio and image types are recognised; anything else is
        returned unchanged.
        """
        if not os.path.splitext(fname)[1]:
            # mimetypes is returning '.jpe' even after calling .init(), so we'll do
            # it manually instead
            type_map = {
                "audio/mpeg": ".mp3",
                "audio/ogg": ".oga",
                "audio/opus": ".opus",
                "audio/wav": ".wav",
                "audio/webm": ".weba",
                "audio/aac": ".aac",
                "image/jpeg": ".jpg",
                "image/png": ".png",
                "image/svg+xml": ".svg",
                "image/webp": ".webp",
                "image/avif": ".avif",
            }
            if content_type in type_map:
                fname += type_map[content_type]
        return fname

    def have(self, fname: str) -> bool:
        """Return whether `fname` exists in the media folder."""
        return os.path.exists(os.path.join(self.dir(), fname))

    def trash_files(self, fnames: list[str]) -> None:
        """Move the provided files to the media trash folder.

        They can be put back with `restore_trash()`, or removed for good
        with `empty_trash()`.
        """
        self.col._backend.trash_media_files(fnames)

    # String manipulation
    ##########################################################################

    @deprecated_keywords(includeRemote="include_remote")
    def files_in_str(
        self, mid: NotetypeId, string: str, include_remote: bool = False
    ) -> list[str]:
        """Return the media filenames referenced in `string`.

        LaTeX is rendered first using the note type `mid`, so that a
        `[latex]...[/latex]` block that produces an image is picked up too.
        Remote files are left out unless `include_remote` is set.
        """
        files = []
        model = self.col.models.get(mid)
        # handle latex
        string = render_latex(string, model, self.col)
        # extract filenames
        for reg in self.regexps:
            for match in re.finditer(reg, string):
                fname = match.group("fname")
                is_local = not re.match("(https?|ftp)://", fname.lower())
                if is_local or include_remote:
                    files.append(fname)
        return files

    def extract_static_media_files(self, mid: NotetypeId) -> Sequence[str]:
        """Return the media filenames the note type `mid` itself refers to.

        These come from its templates and its CSS, rather than from any note.
        Note that `check()` only looks at notes, so a file used solely by a
        note type is still reported as unused.
        """
        return self.col._backend.extract_static_media_files(mid)

    def transform_names(self, txt: str, func: Callable) -> str:
        """Return `txt` with `func` applied to each media filename it refers to."""
        for reg in self.regexps:
            txt = re.sub(reg, func, txt)
        return txt

    def strip(self, txt: str) -> str:
        """Return `txt` with its media references removed.

        Covers `[sound:...]` tags and the `src`/`data` attributes of
        `img`, `audio`, `source` and `object` tags, whether quoted or
        not.
        """
        for reg in self.regexps:
            txt = re.sub(reg, "", txt)
        return txt

    def escape_images(self, string: str, unescape: bool = False) -> str:
        """Deprecated alias of `escape_media_filenames()`, kept for add-ons."""
        return self.escape_media_filenames(string, unescape)

    def escape_media_filenames(self, string: str, unescape: bool = False) -> str:
        """Percent-encode media filenames in `string`, or decode them again.

        Set `unescape` to decode instead of encode. Only filenames inside
        `audio`, `image` and `object` tags are touched.
        """
        if unescape:
            return self.col._backend.decode_iri_paths(string)
        else:
            return self.col._backend.encode_iri_paths(string)

    # Checking media
    ##########################################################################

    def check(self) -> CheckMediaResponse:
        """Compare the media folder against the notes that reference it.

        The returned `CheckMediaResponse` carries:

        - `unused`: files not referenced by any note.
        - `missing`: referenced files absent from the folder.
        - `missing_media_notes`: ids of the notes with missing files.
        - `report`: a human-readable summary of the above.
        - `have_trash`: whether the trash folder holds anything.
        """
        output = self.col._backend.check_media()
        return output

    def render_all_latex(
        self, progress_cb: Callable[[int], bool] | None = None
    ) -> tuple[int, str] | None:
        """Render any LaTeX that is missing.

        If a progress callback is provided and it returns false, the operation
        will be aborted.

        If an error is encountered, returns (note_id, error_message)
        """
        last_progress = time.time()
        checked = 0
        for nid, mid, flds in self.col.db.execute(
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
                last_progress = int_time()
                if not progress_cb(checked):
                    return None

        return None

    # Legacy
    ##########################################################################

    _illegalCharReg = re.compile(r'[][><:"/?*^\\|\0\r\n]')

    def _legacy_strip_illegal(self, str: str) -> str:
        # currently used by ankiconnect
        return re.sub(self._illegalCharReg, "", str)

    def _legacy_has_illegal(self, string: str) -> bool:
        if re.search(self._illegalCharReg, string):
            return True
        try:
            string.encode(sys.getfilesystemencoding())
        except UnicodeEncodeError:
            return True
        return False

    def _legacy_find_changes(self) -> None:
        pass

    @deprecated_keywords(typeHint="type_hint")
    def _legacy_write_data(
        self, opath: str, data: bytes, type_hint: str | None = None
    ) -> str:
        fname = os.path.basename(opath)
        if type_hint:
            fname = self.add_extension_based_on_mime(fname, type_hint)
        return self.write_data(fname, data)


MediaManager.register_deprecated_attributes(
    stripIllegal=(MediaManager._legacy_strip_illegal, None),
    hasIllegal=(MediaManager._legacy_has_illegal, None),
    findChanges=(MediaManager._legacy_find_changes, None),
    writeData=(MediaManager._legacy_write_data, MediaManager.write_data),
)
