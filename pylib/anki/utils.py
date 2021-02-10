# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import json as _json
import os
import platform
import random
import re
import shutil
import string
import subprocess
import sys
import tempfile
import time
import traceback
from contextlib import contextmanager
from hashlib import sha1
from html.entities import name2codepoint
from typing import Any, Iterable, Iterator, List, Match, Optional, Union

from anki.dbproxy import DBProxy

_tmpdir: Optional[str]

try:
    # pylint: disable=c-extension-no-member
    import orjson

    to_json_bytes = orjson.dumps
    from_json_bytes = orjson.loads
except:
    print("orjson is missing; DB operations will be slower")
    to_json_bytes = lambda obj: _json.dumps(obj).encode("utf8")  # type: ignore
    from_json_bytes = _json.loads


def __getattr__(name: str) -> Any:
    if name == "json":
        traceback.print_stack(file=sys.stdout)
        print("add-on should import json directly, not from anki.utils")
        return _json
    raise AttributeError(f"module {__name__} has no attribute {name}")


# Time handling
##############################################################################


def intTime(scale: int = 1) -> int:
    "The time in integer seconds. Pass scale=1000 to get milliseconds."
    return int(time.time() * scale)


# HTML
##############################################################################
reComment = re.compile("(?s)<!--.*?-->")
reStyle = re.compile("(?si)<style.*?>.*?</style>")
reScript = re.compile("(?si)<script.*?>.*?</script>")
reTag = re.compile("(?s)<.*?>")
reEnts = re.compile(r"&#?\w+;")
reMedia = re.compile("(?i)<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>")


def stripHTML(s: str) -> str:
    s = reComment.sub("", s)
    s = reStyle.sub("", s)
    s = reScript.sub("", s)
    s = reTag.sub("", s)
    s = entsToTxt(s)
    return s


def stripHTMLMedia(s: str) -> str:
    "Strip HTML but keep media filenames"
    s = reMedia.sub(" \\1 ", s)
    return stripHTML(s)


def minimizeHTML(s: str) -> str:
    "Correct Qt's verbose bold/underline/etc."
    s = re.sub('<span style="font-weight:600;">(.*?)</span>', "<b>\\1</b>", s)
    s = re.sub('<span style="font-style:italic;">(.*?)</span>', "<i>\\1</i>", s)
    s = re.sub(
        '<span style="text-decoration: underline;">(.*?)</span>', "<u>\\1</u>", s
    )
    return s


def htmlToTextLine(s: str) -> str:
    s = s.replace("<br>", " ")
    s = s.replace("<br />", " ")
    s = s.replace("<div>", " ")
    s = s.replace("\n", " ")
    s = re.sub(r"\[sound:[^]]+\]", "", s)
    s = re.sub(r"\[\[type:[^]]+\]\]", "", s)
    s = stripHTMLMedia(s)
    s = s.strip()
    return s


def entsToTxt(html: str) -> str:
    # entitydefs defines nbsp as \xa0 instead of a standard space, so we
    # replace it first
    html = html.replace("&nbsp;", " ")

    def fixup(m: Match) -> str:
        text = m.group(0)
        if text[:2] == "&#":
            # character reference
            try:
                if text[:3] == "&#x":
                    return chr(int(text[3:-1], 16))
                else:
                    return chr(int(text[2:-1]))
            except ValueError:
                pass
        else:
            # named entity
            try:
                text = chr(name2codepoint[text[1:-1]])
            except KeyError:
                pass
        return text  # leave as is

    return reEnts.sub(fixup, html)


# IDs
##############################################################################


def ids2str(ids: Iterable[Union[int, str]]) -> str:
    """Given a list of integers, return a string '(int1,int2,...)'."""
    return "(%s)" % ",".join(str(i) for i in ids)


def timestampID(db: DBProxy, table: str) -> int:
    "Return a non-conflicting timestamp for table."
    # be careful not to create multiple objects without flushing them, or they
    # may share an ID.
    t = intTime(1000)
    while db.scalar(f"select id from {table} where id = ?", t):
        t += 1
    return t


def maxID(db: DBProxy) -> int:
    "Return the first safe ID to use."
    now = intTime(1000)
    for tbl in "cards", "notes":
        now = max(now, db.scalar(f"select max(id) from {tbl}") or 0)
    return now + 1


# used in ankiweb
def base62(num: int, extra: str = "") -> str:
    s = string
    table = s.ascii_letters + s.digits + extra
    buf = ""
    while num:
        num, i = divmod(num, len(table))
        buf = table[i] + buf
    return buf


_base91_extra_chars = "!#$%&()*+,-./:;<=>?@[]^_`{|}~"


def base91(num: int) -> str:
    # all printable characters minus quotes, backslash and separators
    return base62(num, _base91_extra_chars)


def guid64() -> str:
    "Return a base91-encoded 64bit random number."
    return base91(random.randint(0, 2 ** 64 - 1))


# Fields
##############################################################################


def joinFields(list: List[str]) -> str:
    return "\x1f".join(list)


def splitFields(string: str) -> List[str]:
    return string.split("\x1f")


# Checksums
##############################################################################


def checksum(data: Union[bytes, str]) -> str:
    if isinstance(data, str):
        data = data.encode("utf-8")
    return sha1(data).hexdigest()


def fieldChecksum(data: str) -> int:
    # 32 bit unsigned number from first 8 digits of sha1 hash
    return int(checksum(stripHTMLMedia(data).encode("utf-8"))[:8], 16)


# Temp files
##############################################################################

_tmpdir = None


def tmpdir() -> str:
    "A reusable temp folder which we clean out on each program invocation."
    global _tmpdir
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
    (fd, name) = tempfile.mkstemp(dir=tmpdir(), prefix=prefix, suffix=suffix)
    os.close(fd)
    return name


def namedtmp(name: str, rm: bool = True) -> str:
    "Return tmpdir+name. Deletes any existing file."
    path = os.path.join(tmpdir(), name)
    if rm:
        try:
            os.unlink(path)
        except OSError:
            pass
    return path


# Cmd invocation
##############################################################################


@contextmanager
def noBundledLibs() -> Iterator[None]:
    oldlpath = os.environ.pop("LD_LIBRARY_PATH", None)
    yield
    if oldlpath is not None:
        os.environ["LD_LIBRARY_PATH"] = oldlpath


def call(argv: List[str], wait: bool = True, **kwargs: Any) -> int:
    "Execute a command. If WAIT, return exit code."
    # ensure we don't open a separate window for forking process on windows
    if isWin:
        si = subprocess.STARTUPINFO()  # type: ignore
        try:
            si.dwFlags |= subprocess.STARTF_USESHOWWINDOW  # type: ignore
        except:
            # pylint: disable=no-member
            si.dwFlags |= subprocess._subprocess.STARTF_USESHOWWINDOW  # type: ignore
    else:
        si = None
    # run
    try:
        with noBundledLibs():
            o = subprocess.Popen(argv, startupinfo=si, **kwargs)
    except OSError:
        # command not found
        return -1
    # wait for command to finish
    if wait:
        while 1:
            try:
                ret = o.wait()
            except OSError:
                # interrupted system call
                continue
            break
    else:
        ret = 0
    return ret


# OS helpers
##############################################################################

isMac = sys.platform.startswith("darwin")
isWin = sys.platform.startswith("win32")
isLin = not isMac and not isWin
devMode = os.getenv("ANKIDEV", "")

invalidFilenameChars = ':*?"<>|'


def invalidFilename(str: str, dirsep: bool = True) -> Optional[str]:
    for c in invalidFilenameChars:
        if c in str:
            return c
    if (dirsep or isWin) and "/" in str:
        return "/"
    elif (dirsep or not isWin) and "\\" in str:
        return "\\"
    elif str.strip().startswith("."):
        return "."
    return None


def platDesc() -> str:
    # we may get an interrupted system call, so try this in a loop
    n = 0
    theos = "unknown"
    while n < 100:
        n += 1
        try:
            system = platform.system()
            if isMac:
                theos = f"mac:{platform.mac_ver()[0]}"
            elif isWin:
                theos = f"win:{platform.win32_ver()[0]}"
            elif system == "Linux":
                import distro  # pytype: disable=import-error # pylint: disable=import-error

                r = distro.linux_distribution(full_distribution_name=False)
                theos = f"lin:{r[0]}:{r[1]}"
            else:
                theos = system
            break
        except:
            continue
    return theos


# Debugging
##############################################################################


class TimedLog:
    def __init__(self) -> None:
        self._last = time.time()

    def log(self, s: str) -> None:
        path, num, fn, y = traceback.extract_stack(limit=2)[0]
        sys.stderr.write(
            "%5dms: %s(): %s\n" % ((time.time() - self._last) * 1000, fn, s)
        )
        self._last = time.time()


# Version
##############################################################################


def versionWithBuild() -> str:
    from anki.buildinfo import buildhash, version

    return f"{version} ({buildhash})"


def pointVersion() -> int:
    from anki.buildinfo import version

    return int(version.split(".")[-1])
