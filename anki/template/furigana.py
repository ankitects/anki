# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# Based off Kieran Clancy's initial implementation.

import re
from anki.hooks import addHook

# Match kanji followed by hiragana in brackets first
narrow = r'([\u4e00-\u9faf]*)\[(.*?)\]'
# Run original wider matching regex second
r = r' ?([^ >]+?)\[(.+?)\]'
ruby = r'<ruby><rb>\1</rb><rt>\2</rt></ruby>'

def noSound(repl):
    def func(match):
        if match.group(2).startswith("sound:"):
            # return without modification
            return match.group(0)
        else:
            # OK to use original match here as working on matching portion only
            return re.sub(r, repl, match.group(0))
    return func

def _munge(s):
    return s.replace("&nbsp;", " ")

def kanji(txt, *args):
    repl = noSound(r'\1')
    first = re.sub(narrow, repl, _munge(txt))
    return re.sub(r, repl, first)

def kana(txt, *args):
    repl = noSound(r'\2')
    first = re.sub(narrow, repl, _munge(txt))
    return re.sub(r, repl, first)

def furigana(txt, *args):
    repl = noSound(ruby)
    first = re.sub(narrow, repl, _munge(txt))
    return re.sub(r, repl, first)

def install():
    addHook('fmod_kanji', kanji)
    addHook('fmod_kana', kana)
    addHook('fmod_furigana', furigana)
