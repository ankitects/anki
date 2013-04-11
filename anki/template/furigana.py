# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# Based off Kieran Clancy's initial implementation.

import re
from anki.hooks import addHook

r = r' ?([^ >]+?)\[(.+?)\]'
ruby = r'<ruby><rb>\1</rb><rt>\2</rt></ruby>'

def noSound(repl):
    def func(match):
        if match.group(2).startswith("sound:"):
            # return without modification
            return match.group(0)
        else:
            return re.sub(r, repl, match.group(0))
    return func

def _munge(s):
    return s.replace("&nbsp;", " ")

def kanji(txt, *args):
    return re.sub(r, noSound(r'\1'), _munge(txt))

def kana(txt, *args):
    return re.sub(r, noSound(r'\2'), _munge(txt))

def furigana(txt, *args):
    return re.sub(r, noSound(ruby), _munge(txt))

def install():
    addHook('fmod_kanji', kanji)
    addHook('fmod_kana', kana)
    addHook('fmod_furigana', furigana)
