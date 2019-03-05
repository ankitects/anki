# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
import os
import random
import time
import math
from html.entities import name2codepoint
import subprocess
import tempfile
import shutil
import string
import sys
import locale
from hashlib import sha1
import platform
import traceback
from contextlib import contextmanager
from anki.lang import _, ngettext

# some add-ons expect json to be in the utils module
import json # pylint: disable=unused-import

# Time handling
##############################################################################

def intTime(scale=1):
    "The time in integer seconds. Pass scale=1000 to get milliseconds."
    return int(time.time()*scale)

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

def shortTimeFmt(type):
    return {
    "years": _("%sy"),
    "months": _("%smo"),
    "days": _("%sd"),
    "hours": _("%sh"),
    "minutes": _("%sm"),
    "seconds": _("%ss"),
    }[type]

def fmtTimeSpan(time, pad=0, point=0, short=False, inTime=False, unit=99):
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
    timestr = "%%%(a)d.%(b)df" % {'a': pad, 'b': point}
    return locale.format_string(fmt % timestr, time)

def optimalPeriod(time, point, unit):
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

def convertSecondsTo(seconds, type):
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

def _pluralCount(time, point):
    if point:
        return 2
    return math.floor(time)

# Locale
##############################################################################

def fmtPercentage(float_value, point=1):
    "Return float with percentage sign"
    fmt = '%' + "0.%(b)df" % {'b': point}
    return locale.format_string(fmt, float_value) + "%"

def fmtFloat(float_value, point=1):
    "Return a string with decimal separator according to current locale"
    fmt = '%' + "0.%(b)df" % {'b': point}
    return locale.format_string(fmt, float_value)

# HTML
##############################################################################
reComment = re.compile("(?s)<!--.*?-->")
reStyle = re.compile("(?si)<style.*?>.*?</style>")
reScript = re.compile("(?si)<script.*?>.*?</script>")
reTag = re.compile("(?s)<.*?>")
reEnts = re.compile(r"&#?\w+;")
reMedia = re.compile("(?i)<img[^>]+src=[\"']?([^\"'>]+)[\"']?[^>]*>")

def stripHTML(s):
    s = reComment.sub("", s)
    s = reStyle.sub("", s)
    s = reScript.sub("", s)
    s = reTag.sub("", s)
    s = entsToTxt(s)
    return s

def stripHTMLMedia(s):
    "Strip HTML but keep media filenames"
    s = reMedia.sub(" \\1 ", s)
    return stripHTML(s)

def minimizeHTML(s):
    "Correct Qt's verbose bold/underline/etc."
    s = re.sub('<span style="font-weight:600;">(.*?)</span>', '<b>\\1</b>',
               s)
    s = re.sub('<span style="font-style:italic;">(.*?)</span>', '<i>\\1</i>',
               s)
    s = re.sub('<span style="text-decoration: underline;">(.*?)</span>',
               '<u>\\1</u>', s)
    return s

def htmlToTextLine(s):
    s = s.replace("<br>", " ")
    s = s.replace("<br />", " ")
    s = s.replace("<div>", " ")
    s = s.replace("\n", " ")
    s = re.sub(r"\[sound:[^]]+\]", "", s)
    s = re.sub(r"\[\[type:[^]]+\]\]", "", s)
    s = stripHTMLMedia(s)
    s = s.strip()
    return s

def entsToTxt(html):
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
        return text # leave as is
    return reEnts.sub(fixup, html)

def bodyClass(col, card):
    bodyclass = "card card%d" % (card.ord+1)
    if col.conf.get("nightMode"):
        bodyclass += " nightMode"
    return bodyclass

# IDs
##############################################################################

def hexifyID(id):
    return "%x" % int(id)

def dehexifyID(id):
    return int(id, 16)

def ids2str(ids):
    """Given a list of integers, return a string '(int1,int2,...)'."""
    return "(%s)" % ",".join(str(i) for i in ids)

def timestampID(db, table):
    "Return a non-conflicting timestamp for table."
    # be careful not to create multiple objects without flushing them, or they
    # may share an ID.
    t = intTime(1000)
    while db.scalar("select id from %s where id = ?" % table, t):
        t += 1
    return t

def maxID(db):
    "Return the first safe ID to use."
    now = intTime(1000)
    for tbl in "cards", "notes":
        now = max(now, db.scalar("select max(id) from %s" % tbl) or 0)
    return now + 1

# used in ankiweb
def base62(num, extra=""):
    s = string; table = s.ascii_letters + s.digits + extra
    buf = ""
    while num:
        num, i = divmod(num, len(table))
        buf = table[i] + buf
    return buf

_base91_extra_chars = "!#$%&()*+,-./:;<=>?@[]^_`{|}~"
def base91(num):
    # all printable characters minus quotes, backslash and separators
    return base62(num, _base91_extra_chars)

def guid64():
    "Return a base91-encoded 64bit random number."
    return base91(random.randint(0, 2**64-1))

# increment a guid by one, for note type conflicts
def incGuid(guid):
    return _incGuid(guid[::-1])[::-1]

def _incGuid(guid):
    s = string; table = s.ascii_letters + s.digits + _base91_extra_chars
    idx = table.index(guid[0])
    if idx + 1 == len(table):
        # overflow
        guid = table[0] + _incGuid(guid[1:])
    else:
        guid = table[idx+1] + guid[1:]
    return guid

# Fields
##############################################################################

def joinFields(list):
    return "\x1f".join(list)

def splitFields(string):
    return string.split("\x1f")

# Checksums
##############################################################################

def checksum(data):
    if isinstance(data, str):
        data = data.encode("utf-8")
    return sha1(data).hexdigest()

def fieldChecksum(data):
    # 32 bit unsigned number from first 8 digits of sha1 hash
    return int(checksum(stripHTMLMedia(data).encode("utf-8"))[:8], 16)

# Temp files
##############################################################################

_tmpdir = None

def tmpdir():
    "A reusable temp folder which we clean out on each program invocation."
    global _tmpdir
    if not _tmpdir:
        def cleanup():
            shutil.rmtree(_tmpdir)
        import atexit
        atexit.register(cleanup)
        _tmpdir = os.path.join(tempfile.gettempdir(), "anki_temp")
    if not os.path.exists(_tmpdir):
        os.mkdir(_tmpdir)
    return _tmpdir

def tmpfile(prefix="", suffix=""):
    (fd, name) = tempfile.mkstemp(dir=tmpdir(), prefix=prefix, suffix=suffix)
    os.close(fd)
    return name

def namedtmp(name, rm=True):
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
def noBundledLibs():
    oldlpath = os.environ.pop("LD_LIBRARY_PATH", None)
    yield
    if oldlpath is not None:
        os.environ["LD_LIBRARY_PATH"] = oldlpath

def call(argv, wait=True, **kwargs):
    "Execute a command. If WAIT, return exit code."
    # ensure we don't open a separate window for forking process on windows
    if isWin:
        si = subprocess.STARTUPINFO()
        try:
            si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        except:
            # pylint: disable=no-member
            si.dwFlags |= subprocess._subprocess.STARTF_USESHOWWINDOW
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

invalidFilenameChars = ":*?\"<>|"

def invalidFilename(str, dirsep=True):
    for c in invalidFilenameChars:
        if c in str:
            return c
    if (dirsep or isWin) and "/" in str:
        return "/"
    elif (dirsep or not isWin) and "\\" in str:
        return "\\"
    elif str.strip().startswith("."):
        return "."

def platDesc():
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
                import distro
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
    def __init__(self):
        self._last = time.time()
    def log(self, s):
        path, num, fn, y = traceback.extract_stack(limit=2)[0]
        sys.stderr.write("%5dms: %s(): %s\n" % ((time.time() - self._last)*1000, fn, s))
        self._last = time.time()

# Version
##############################################################################

def versionWithBuild():
    from anki import version
    try:
        from anki.buildhash import build
    except:
        build = "dev"
    return "%s (%s)" % (version, build)
