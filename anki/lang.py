# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, sys
import gettext
import threading

langs = [
    (u"Bahasa Melayu", "ms"),
    (u"Dansk", "da"),
    (u"Deutsch", "de"),
    (u"Eesti", "et"),
    (u"English", "en"),
    (u"Español", "es"),
    (u"Esperanto", "eo"),
    (u"Français", "fr"),
    (u"Italiano", "it"),
    (u"Magyar", "hu"),
    (u"Nederlands","nl"),
    (u"Norsk","nb"),
    (u"Occitan","oc"),
    (u"Polski", "pl"),
    (u"Português Brasileiro", "pt_BR"),
    (u"Português", "pt"),
    (u"Româneşte", "ro"),
    (u"Slovenščina", "sl"),
    (u"Suomi", "fi"),
    (u"Svenska", "sv"),
    (u"Tiếng Việt", "vi"),
    (u"Türkçe", "tr"),
    (u"Čeština", "cs"),
    (u"Ελληνικά", "el"),
    (u"Български", "bg"),
    (u"Монгол хэл","mn"),
    (u"русский язык", "ru"),
    (u"українська мова", "uk"),
    (u"עִבְרִית", "he"),
    (u"العربية", "ar"),
    (u"فارسی", "fa"),
    (u"日本語", "ja"),
    (u"简体中文", "zh_CN"),
    (u"繁體中文", "zh_TW"),
    (u"한국어", "ko"),
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
    return localTranslation().ugettext(str)

def ngettext(single, plural, n):
    return localTranslation().ungettext(single, plural, n)

def langDir():
    dir = os.path.join(os.path.dirname(
        os.path.abspath(__file__)), "locale")
    if not os.path.exists(dir):
        dir = os.path.join(os.path.dirname(sys.argv[0]), "locale")
    return dir

def setLang(lang, local=True):
    trans = gettext.translation(
        'libanki', langDir(), languages=[lang], fallback=True)
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

if not currentTranslation:
    setLang("en_US", local=False)
