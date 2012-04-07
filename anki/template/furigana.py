# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# Based off Kieran Clancy's initial implementation.

import re
from anki.hooks import addHook

r = r' ?([^ ]+?)\[(.+?)\]'
ruby = r'<ruby><rb>\1</rb><rt>\2</rt></ruby>'

def kanji(txt, *args):
    return re.sub(r, r'\1', txt)

def kana(txt, *args):
    return re.sub(r, r'\2', txt)

def furigana(txt, *args):
    return re.sub(r, ruby, txt)

def install():
    addHook('fmod_kanji', kanji)
    addHook('fmod_kana', kana)
    addHook('fmod_furigana', furigana)
