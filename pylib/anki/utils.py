# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# some add-ons expect json to be in the utils module
import json  # pylint: disable=unused-import
import locale
import math
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
from typing import Any, Iterable, Iterator, List, Optional, Tuple, Union

from anki.db import DB
from anki.lang import _, ngettext

_tmpdir: Optional[str]

# Time handling
##############################################################################


def intTime(scale: int = 1) -> int:
    "The time in integer seconds. Pass scale=1000 to get milliseconds."
    return int(time.time() * scale)


timeTable = {
    "years": lambda n: ngettext("%s year", "%s years", n),
    "months": lambda n: ngettext("%s month", "%s months", n),
    "days": lambda n: ngettext("%s day", "%s days", n),
    "hours": lambda n: ngettext("%s hour", "%s hours", n),
    "minutes": lambda n: ngettext("%s minute", "%s minutes", n),
    "seconds": lambda n: ngettext("%s second", "%s seconds", n),
}

inTimeTable = {
    "years": lambda n: ngettext("in %s year", "in %s years", n),
    "months": lambda n: ngettext("in %s month", "in %s months", n),
    "days": lambda n: ngettext("in %s day", "in %s days", n),
    "hours": lambda n: ngettext("in %s hour", "in %s hours", n),
    "minutes": lambda n: ngettext("in %s minute", "in %s minutes", n),
    "seconds": lambda n: ngettext("in %s second", "in %s seconds", n),
}


def shortTimeFmt(type: str) -> str:
    return {
        # T: year is an abbreviation for year. %s is a number of years
        "years": _("%sy"),
        # T: m is an abbreviation for month. %s is a number of months
        "months": _("%smo"),
        # T: d is an abbreviation for day. %s is a number of days
        "days": _("%sd"),
        # T: h is an abbreviation for hour. %s is a number of hours
        "hours": _("%sh"),
        # T: m is an abbreviation for minute. %s is a number of minutes
        "minutes": _("%sm"),
        # T: s is an abbreviation for second. %s is a number of seconds
        "seconds": _("%ss"),
    }[type]


def fmtTimeSpan(
    time: Union[int, float],
    pad: int = 0,
    point: int = 0,
    short: bool = False,
    inTime: bool = False,
    unit: int = 99,
) -> str:
    "Return a string representing a time span (eg '2 days')."
    (type, point) = optimalPeriod(time, point, unit)
    time = convertSecondsTo(time, type)
    if not point:
        time = int(round(time))
    if short:
        fmt = shortTimeFmt(type)
    else:
        if inTime:
            fmt = inTimeTable[type](_pluralCount(time, point))
        else:
            fmt = timeTable[type](_pluralCount(time, point))
    timestr = "%%%(a)d.%(b)df" % {"a": pad, "b": point}
    return locale.format_string(fmt % timestr, time)


def optimalPeriod(time: Union[int, float], point: int, unit: int) -> Tuple[str, int]:
    if abs(time) < 60 or unit < 1:
        type = "seconds"
        point -= 1
    elif abs(time) < 3600 or unit < 2:
        type = "minutes"
    elif abs(time) < 60 * 60 * 24 or unit < 3:
        type = "hours"
    elif abs(time) < 60 * 60 * 24 * 30 or unit < 4:
        type = "days"
    elif abs(time) < 60 * 60 * 24 * 365 or unit < 5:
        type = "months"
        point += 1
    else:
        type = "years"
        point += 1
    return (type, max(point, 0))


def convertSecondsTo(seconds: Union[int, float], type: str) -> Any:
    if type == "seconds":
        return seconds
    elif type == "minutes":
        return seconds / 60
    elif type == "hours":
        return seconds / 3600
    elif type == "days":
        return seconds / 86400
    elif type == "months":
        return seconds / 2592000
    elif type == "years":
        return seconds / 31536000
    assert False


def _pluralCount(time: Union[int, float], point: int) -> int:
    if point:
        return 2
    return math.floor(time)


# Locale
##############################################################################


def fmtPercentage(float_value, point=1) -> str:
    "Return float with percentage sign"
    fmt = "%" + "0.%(b)df" % {"b": point}
    return locale.format_string(fmt, float_value) + "%"


def fmtFloat(float_value, point=1) -> str:
    "Return a string with decimal separator according to current locale"
    fmt = "%" + "0.%(b)df" % {"b": point}
    return locale.format_string(fmt, float_value)


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

    def fixup(m):
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


# legacy function
def bodyClass(col, card) -> str:
    from aqt.theme import theme_manager

    print("bodyClass() deprecated")
    return theme_manager.body_classes_for_card_ord(card.ord)


# IDs
##############################################################################


def hexifyID(id) -> str:
    return "%x" % int(id)


def dehexifyID(id) -> int:
    return int(id, 16)


def ids2str(ids: Iterable[Union[int, str]]) -> str:
    """Given a list of integers, return a string '(int1,int2,...)'."""
    return "(%s)" % ",".join(str(i) for i in ids)


def timestampID(db: DB, table: str) -> int:
    "Return a non-conflicting timestamp for table."
    # be careful not to create multiple objects without flushing them, or they
    # may share an ID.
    t = intTime(1000)
    while db.scalar("select id from %s where id = ?" % table, t):
        t += 1
    return t


def maxID(db: DB) -> int:
    "Return the first safe ID to use."
    now = intTime(1000)
    for tbl in "cards", "notes":
        now = max(now, db.scalar("select max(id) from %s" % tbl) or 0)
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


# increment a guid by one, for note type conflicts
def incGuid(guid) -> str:
    return _incGuid(guid[::-1])[::-1]


def _incGuid(guid) -> str:
    s = string
    table = s.ascii_letters + s.digits + _base91_extra_chars
    idx = table.index(guid[0])
    if idx + 1 == len(table):
        # overflow
        guid = table[0] + _incGuid(guid[1:])
    else:
        guid = table[idx + 1] + guid[1:]
    return guid


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

        def cleanup():
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
        except (OSError, IOError):
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


def call(argv: List[str], wait: bool = True, **kwargs) -> int:
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


def invalidFilename(str, dirsep=True) -> Optional[str]:
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
                theos = "mac:%s" % (platform.mac_ver()[0])
            elif isWin:
                theos = "win:%s" % (platform.win32_ver()[0])
            elif system == "Linux":
                import distro  # pytype: disable=import-error # pylint: disable=import-error

                r = distro.linux_distribution(full_distribution_name=False)
                theos = "lin:%s:%s" % (r[0], r[1])
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

    def log(self, s) -> None:
        path, num, fn, y = traceback.extract_stack(limit=2)[0]
        sys.stderr.write(
            "%5dms: %s(): %s\n" % ((time.time() - self._last) * 1000, fn, s)
        )
        self._last = time.time()


# Version
##############################################################################


def versionWithBuild() -> str:
    from anki.buildinfo import version, buildhash

    return "%s (%s)" % (version, buildhash)


def pointVersion() -> int:
    from anki.buildinfo import version

    return int(version.split(".")[-1])
