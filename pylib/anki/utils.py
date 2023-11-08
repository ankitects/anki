# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import json as _json
import os
import platform
import random
import shutil
import string
import subprocess
import sys
import tempfile
import time
from contextlib import contextmanager
from hashlib import sha1
from typing import TYPE_CHECKING, Any, Callable, Iterable, Iterator

from anki._legacy import DeprecatedNamesMixinForModule
from anki.dbproxy import DBProxy

_tmpdir: str | None

try:
    # pylint: disable=c-extension-no-member
    import orjson

    to_json_bytes: Callable[[Any], bytes] = orjson.dumps
    from_json_bytes = orjson.loads
except:
    print("orjson is missing; DB operations will be slower")

    def to_json_bytes(obj: Any) -> bytes:
        return _json.dumps(obj).encode("utf8")

    from_json_bytes = _json.loads


# Time handling
##############################################################################


def int_time(scale: int = 1) -> int:
    "The time in integer seconds. Pass scale=1000 to get milliseconds."
    return int(time.time() * scale)


# HTML
##############################################################################


def strip_html(txt: str) -> str:
    import anki.lang
    from anki.collection import StripHtmlMode

    return anki.lang.current_i18n.strip_html(text=txt, mode=StripHtmlMode.NORMAL)


def strip_html_media(txt: str) -> str:
    "Strip HTML but keep media filenames"
    import anki.lang
    from anki.collection import StripHtmlMode

    return anki.lang.current_i18n.strip_html(
        text=txt, mode=StripHtmlMode.PRESERVE_MEDIA_FILENAMES
    )


def html_to_text_line(txt: str) -> str:
    import anki.lang

    return anki.lang.current_i18n.html_to_text_line(
        text=txt, preserve_media_filenames=True
    )


# IDs
##############################################################################


def ids2str(ids: Iterable[int | str]) -> str:
    """Given a list of integers, return a string '(int1,int2,...)'."""
    return f"({','.join(str(i) for i in ids)})"


def timestamp_id(db: DBProxy, table: str) -> int:
    "Return a non-conflicting timestamp for table."
    # be careful not to create multiple objects without flushing them, or they
    # may share an ID.
    timestamp = int_time(1000)
    while db.scalar(f"select id from {table} where id = ?", timestamp):
        timestamp += 1
    return timestamp


def max_id(db: DBProxy) -> int:
    "Return the first safe ID to use."
    now = int_time(1000)
    for tbl in "cards", "notes":
        now = max(now, db.scalar(f"select max(id) from {tbl}") or 0)
    return now + 1


# used in ankiweb
def base62(num: int, extra: str = "") -> str:
    table = string.ascii_letters + string.digits + extra
    buf = ""
    while num:
        num, mod = divmod(num, len(table))
        buf = table[mod] + buf
    return buf


_BASE91_EXTRA_CHARS = "!#$%&()*+,-./:;<=>?@[]^_`{|}~"


def base91(num: int) -> str:
    # all printable characters minus quotes, backslash and separators
    return base62(num, _BASE91_EXTRA_CHARS)


def guid64() -> str:
    "Return a base91-encoded 64bit random number."
    return base91(random.randint(0, 2**64 - 1))


# Fields
##############################################################################


def join_fields(list: list[str]) -> str:
    return "\x1f".join(list)


def split_fields(string: str) -> list[str]:
    return string.split("\x1f")


# Checksums
##############################################################################


def checksum(data: bytes | str) -> str:
    if isinstance(data, str):
        data = data.encode("utf-8")
    return sha1(data).hexdigest()


def field_checksum(data: str) -> int:
    # 32 bit unsigned number from first 8 digits of sha1 hash
    return int(checksum(strip_html_media(data).encode("utf-8"))[:8], 16)


# Temp files
##############################################################################

_tmpdir = None  # pylint: disable=invalid-name


def tmpdir() -> str:
    "A reusable temp folder which we clean out on each program invocation."
    global _tmpdir  # pylint: disable=invalid-name
    if not _tmpdir:

        def cleanup() -> None:
            if os.path.exists(_tmpdir):
                shutil.rmtree(_tmpdir)

        import atexit

        atexit.register(cleanup)
        _tmpdir = os.path.join(tempfile.gettempdir(), "anki_temp")
    try:
        os.mkdir(_tmpdir)
    except FileExistsError:
        pass
    return _tmpdir


def tmpfile(prefix: str = "", suffix: str = "") -> str:
    (descriptor, name) = tempfile.mkstemp(dir=tmpdir(), prefix=prefix, suffix=suffix)
    os.close(descriptor)
    return name


def namedtmp(name: str, remove: bool = True) -> str:
    "Return tmpdir+name. Deletes any existing file."
    path = os.path.join(tmpdir(), name)
    if remove:
        try:
            os.unlink(path)
        except OSError:
            pass
    return path


# Cmd invocation
##############################################################################


@contextmanager
def no_bundled_libs() -> Iterator[None]:
    oldlpath = os.environ.pop("LD_LIBRARY_PATH", None)
    yield
    if oldlpath is not None:
        os.environ["LD_LIBRARY_PATH"] = oldlpath


def call(argv: list[str], wait: bool = True, **kwargs: Any) -> int:
    "Execute a command. If WAIT, return exit code."
    # ensure we don't open a separate window for forking process on windows
    if is_win:
        info = subprocess.STARTUPINFO()  # type: ignore
        try:
            info.dwFlags |= subprocess.STARTF_USESHOWWINDOW  # type: ignore
        except:
            # pylint: disable=no-member
            info.dwFlags |= subprocess._subprocess.STARTF_USESHOWWINDOW  # type: ignore
    else:
        info = None
    # run
    try:
        with no_bundled_libs():
            process = subprocess.Popen(argv, startupinfo=info, **kwargs)
    except OSError:
        # command not found
        return -1
    # wait for command to finish
    if wait:
        while 1:
            try:
                ret = process.wait()
            except OSError:
                # interrupted system call
                continue
            break
    else:
        ret = 0
    return ret


# OS helpers
##############################################################################

is_mac = sys.platform.startswith("darwin")
is_win = sys.platform.startswith("win32")
# also covers *BSD
is_lin = not is_mac and not is_win
dev_mode = os.getenv("ANKIDEV", "")

INVALID_FILENAME_CHARS = ':*?"<>|'


def invalid_filename(str: str, dirsep: bool = True) -> str | None:
    for char in INVALID_FILENAME_CHARS:
        if char in str:
            return char
    if (dirsep or is_win) and "/" in str:
        return "/"
    elif (dirsep or not is_win) and "\\" in str:
        return "\\"
    elif str.strip().startswith("."):
        return "."
    return None


def plat_desc() -> str:
    # we may get an interrupted system call, so try this in a loop
    theos = "unknown"
    for _ in range(100):
        try:
            system = platform.system()
            if is_mac:
                theos = f"mac:{platform.mac_ver()[0]}"
            elif is_win:
                theos = f"win:{platform.win32_ver()[0]}"
            elif system == "Linux":
                import distro  # pytype: disable=import-error # pylint: disable=import-error

                dist_id = distro.id()
                dist_version = distro.version()
                theos = f"lin:{dist_id}:{dist_version}"
            else:
                theos = system
            break
        except:
            continue
    return theos


# Version
##############################################################################


def version_with_build() -> str:
    from anki.buildinfo import buildhash, version

    return f"{version} ({buildhash})"


def int_version() -> int:
    """Anki's version as an integer in the form YYMMPP, e.g. 230900.
    (year, month, patch).
    In 2.1.x releases, this was just the last number."""
    from anki.buildinfo import version

    try:
        [year, month, patch] = version.split(".")
    except ValueError:
        [year, month] = version.split(".")
        patch = "0"

    year_num = int(year)
    month_num = int(month)
    patch_num = int(patch)

    return year_num * 10_000 + month_num * 100 + patch_num


def int_version_to_str(ver: int) -> str:
    if ver <= 99:
        return f"2.1.{ver}"
    else:
        year = ver // 10_000
        month = (ver // 100) % 100
        patch = ver % 100
        out = f"{year:02}.{month:02}"
        if patch:
            out += f".{patch}"
        return out


# these two legacy aliases are provided without deprecation warnings, as add-ons that want to support
# old versions could not use the new name without catching cases where it doesn't exist
point_version = int_version
pointVersion = int_version

_deprecated_names = DeprecatedNamesMixinForModule(globals())
_deprecated_names.register_deprecated_aliases(
    stripHTML=strip_html,
    stripHTMLMedia=strip_html_media,
    timestampID=timestamp_id,
    maxID=max_id,
    invalidFilenameChars=(INVALID_FILENAME_CHARS, "INVALID_FILENAME_CHARS"),
)
_deprecated_names.register_deprecated_attributes(json=((_json, "_json"), None))


if not TYPE_CHECKING:

    def __getattr__(name: str) -> Any:
        return _deprecated_names.__getattr__(name)
