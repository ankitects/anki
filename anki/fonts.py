# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Fonts - mapping to/from platform-specific fonts
==============================================================
"""

import sys

# set this to 'all', to get all fonts in a list
policy="platform"

mapping = [
    [u"Mincho", u"MS Mincho", "win32"],
    [u"Mincho", u"ＭＳ 明朝", "win32"],
    [u"Mincho", u"ヒラギノ明朝 Pro W3", "mac"],
    [u"Mincho", u"Kochi Mincho", "linux"],
    [u"Mincho", u"東風明朝", "linux"],
    ]

def platform():
    if sys.platform == "win32":
        return "win32"
    elif sys.platform.startswith("darwin"):
        return "mac"
    else:
        return "linux"

def toCanonicalFont(family):
    "Turn a platform-specific family into a canonical one."
    for (s, p, type) in mapping:
        if family == p:
            return s
    return family

def toPlatformFont(family):
    "Turn a canonical font into a platform-specific one."
    if policy == "all":
        return allFonts(family)
    ltype = platform()
    for (s, p, type) in mapping:
        if family == s and type == ltype:
            return p
    return family

def substitutions():
    "Return a tuple mapping canonical fonts to platform ones."
    type = platform()
    return [(s, p) for (s, p, t) in mapping if t == type]

def allFonts(family):
    ret = ", ".join([p for (s, p, t) in mapping if s == family])
    return ret or family
