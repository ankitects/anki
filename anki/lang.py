# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, sys, re
import gettext
import threading

langs = [
    ("Afrikaans", "af"),
    ("Bahasa Melayu", "ms"),
    ("Dansk", "da"),
    ("Deutsch", "de"),
    ("Eesti", "et"),
    ("English", "en"),
    ("Español", "es"),
    ("Esperanto", "eo"),
    ("Euskara", "eu"),
    ("Français", "fr"),
    ("Galego", "gl"),
    ("Hrvatski", "hr"),
    ("Interlingua", "ia"),
    ("Italiano", "it"),
    ("Lenga d'òc", "oc"),
    ("Magyar", "hu"),
    ("Nederlands","nl"),
    ("Norsk","nb"),
    ("Occitan","oc"),
    ("Plattdüütsch", "nds"),
    ("Polski", "pl"),
    ("Português Brasileiro", "pt_BR"),
    ("Português", "pt"),
    ("Româneşte", "ro"),
    ("Slovenčina", "sk"),
    ("Slovenščina", "sl"),
    ("Suomi", "fi"),
    ("Svenska", "sv"),
    ("Tiếng Việt", "vi"),
    ("Türkçe", "tr"),
    ("Čeština", "cs"),
    ("Ελληνικά", "el"),
    ("Ελληνικά", "el"),
    ("босански", "bs"),
    ("Български", "bg"),
    ("Монгол хэл","mn"),
    ("русский язык", "ru"),
    ("Српски", "sr"),
    ("українська мова", "uk"),
    ("עִבְרִית", "he"),
    ("العربية", "ar"),
    ("فارسی", "fa"),
    ("ภาษาไทย", "th"),
    ("日本語", "ja"),
    ("简体中文", "zh_CN"),
    ("繁體中文", "zh_TW"),
    ("한국어", "ko"),
]

threadLocal = threading.local()

# global defaults
currentLang = None
currentTranslation = None

def localTranslation():
    "Return the translation local to this thread, or the default."
    if getattr(threadLocal, 'currentTranslation', None):
        return threadLocal.currentTranslation
    else:
        return currentTranslation

def _(str):
    return localTranslation().gettext(str)

def ngettext(single, plural, n):
    return localTranslation().ngettext(single, plural, n)

def langDir():
    dir = os.path.join(os.path.dirname(
        os.path.abspath(__file__)), "locale")
    if not os.path.isdir(dir):
        dir = os.path.join(os.path.dirname(sys.argv[0]), "locale")
    if not os.path.isdir(dir):
        dir = "/usr/share/anki/locale"
    return dir

def setLang(lang, local=True):
    trans = gettext.translation(
        'anki', langDir(), languages=[lang], fallback=True)
    if local:
        threadLocal.currentLang = lang
        threadLocal.currentTranslation = trans
    else:
        global currentLang, currentTranslation
        currentLang = lang
        currentTranslation = trans

def getLang():
    "Return the language local to this thread, or the default."
    if getattr(threadLocal, 'currentLang', None):
        return threadLocal.currentLang
    else:
        return currentLang

def noHint(str):
    "Remove translation hint from end of string."
    return re.sub("(^.*?)( ?\(.+?\))?$", "\\1", str)

if not currentTranslation:
    setLang("en_US", local=False)
