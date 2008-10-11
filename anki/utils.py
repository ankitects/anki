# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Miscellaneous utilities
==============================
"""
__docformat__ = 'restructuredtext'

import re, os, random, time

from anki.db import *
from anki.lang import _, ngettext

timeTable = {
    "years": lambda n: ngettext("%s year", "%s years", n),
    "months": lambda n: ngettext("%s month", "%s months", n),
    "days": lambda n: ngettext("%s day", "%s days", n),
    "hours": lambda n: ngettext("%s hour", "%s hours", n),
    "minutes": lambda n: ngettext("%s minute", "%s minutes", n),
    "seconds": lambda n: ngettext("%s second", "%s seconds", n),
    }

def fmtTimeSpan(time, pad=0, point=0):
    "Return a string representing a time span (eg '2 days')."
    (type, point) = optimalPeriod(time, point)
    time = convertSecondsTo(time, type)
    fmt = timeTable[type](_pluralCount(round(time, point)))
    timestr = "%(a)d.%(b)df" % {'a': pad, 'b': point}
    return ("%" + (fmt % timestr)) % time

def fmtTimeSpanPair(time1, time2):
    (type, point) = optimalPeriod(time1, 0)
    time1 = convertSecondsTo(time1, type)
    time2 = convertSecondsTo(time2, type)
    # a pair is always  should always be read as plural
    fmt = timeTable[type](2)
    timestr = "%(a)d.%(b)df" % {'a': 0, 'b': point}
    finalstr = "%s-%s" % (
        ('%' + timestr) % time1,
        ('%' + timestr) % time2)
    return fmt % finalstr

def optimalPeriod(time, point):
    if abs(time) < 60:
        type = "seconds"
        point -= 1
    elif abs(time) < 3599:
        type = "minutes"
    elif abs(time) < 60 * 60 * 24:
        type = "hours"
    elif abs(time) < 60 * 60 * 24 * 30:
        type = "days"
    elif abs(time) < 60 * 60 * 24 * 365:
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
        return seconds / 60.0
    elif type == "hours":
        return seconds / 3600.0
    elif type == "days":
        return seconds / 86400.0
    elif type == "months":
        return seconds / 2592000.0
    elif type == "years":
        return seconds / 31536000.0
    assert False

def _pluralCount(time):
    if round(time, 1) == 1:
        return 1
    return 2

def mergeTags(*args):
    "Merge tag lists into a single string."
    return ", ".join(set(parseTags(",".join(args))))

def parseTags(tags):
    "Parse a string and return a list of tags."
    tags = tags.split(",")
    tags = [tag.strip() for tag in tags if tag.strip()]
    return tags

def joinTags(tags):
    return ", ".join(tags)

def findTag(tag, tags):
    "True if TAG is in TAGS. Ignore case."
    return tag.lower() in [t.lower() for t in tags]

def addTags(tagstr, tags):
    "Add tag if doesn't exist."
    currentTags = parseTags(tags)
    for tag in parseTags(tagstr):
        if not findTag(tag, currentTags):
            currentTags.append(tag)
    return u", ".join(currentTags)

def deleteTags(tagstr, tags):
    "Delete tag if exists."
    currentTags = parseTags(tags)
    for tag in parseTags(tagstr):
        try:
            currentTags.remove(tag)
        except ValueError:
            pass
    return u", ".join(currentTags)

def stripHTML(s):
    s = re.sub("<.*?>", "", s)
    s = s.replace("&lt;", "<")
    s = s.replace("&gt;", ">")
    return s

def tidyHTML(html):
    "Remove cruft like body tags and return just the important part."
    # contents of body - no head or html tags
    html = re.sub(".*<body.*?>(.*)</body></html>",
                  "\\1", html.replace("\n", u""))
    # strip superfluous Qt formatting
    html = re.sub("margin-top:\d+px; margin-bottom:\d+px; margin-left:\d+px; "
                  "margin-right:\d+px; -qt-block-indent:0; "
                  "text-indent:0px;", "", html)
    html = re.sub("-qt-paragraph-type:empty;", "", html)
    # collapse multiple spaces into one
    html = re.sub("  +", " ", html)
    # strip leading space in style statements, and remove if no contents
    html = re.sub('style=" ', 'style="', html)
    html = re.sub(' style=""', "", html)
    # convert P tags into SPAN and/or BR
    html = re.sub('<p( style=.+?)>(.*?)</p>', u'<span\\1>\\2</span><br>', html)
    html = re.sub('<p>(.*?)</p>', u'\\1<br>', html)
    html = re.sub('<br>$', u'', html)
    # remove leading or trailing whitespace
    html = re.sub('^ +', u'', html)
    html = re.sub(' +$', u'', html)
    return html

def genID(static=[]):
    "Generate a random, unique 64bit ID."
    # 23 bits of randomness, 41 bits of current time
    # random rather than a counter to ensure efficient btree
    t = long(time.time()*1000)
    if not static:
        static.extend([t, {}])
    else:
        if static[0] != t:
            static[0] = t
            static[1] = {}
    while 1:
        rand = random.getrandbits(23)
        if rand not in static[1]:
            static[1][rand] = True
            break
    x = rand << 41 | t
    # turn into a signed long
    if x >= 9223372036854775808L:
        x -= 18446744073709551616L
    return x

def hexifyID(id):
    if id < 0:
        id += 18446744073709551616L
    return "%x" % id

def dehexifyID(id):
    id = int(id, 16)
    if id >= 9223372036854775808L:
        id -= 18446744073709551616L
    return id

def ids2str(ids):
    """Given a list of integers, return a string '(int1,int2,.)'

The caller is responsible for ensuring only integers are provided.
This is safe if you use sqlite primary key columns, which are guaranteed
to be integers."""
    return "(%s)" % ",".join([str(i) for i in ids])
