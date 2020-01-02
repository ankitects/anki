# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# Please leave the coding line in this file to prevent xgettext complaining.

import gettext
import re
import threading
from typing import Any

langs = sorted(
    [
        ("Afrikaans", "af_ZA"),
        ("Bahasa Melayu", "ms_MY"),
        ("Català", "ca_ES"),
        ("Dansk", "da_DK"),
        ("Deutsch", "de_DE"),
        ("Eesti", "et_EE"),
        ("English (United States)", "en_US"),
        ("English (United Kingdom)", "en_GB"),
        ("Español", "es_ES"),
        ("Esperanto", "eo_UY"),
        ("Euskara", "eu_ES"),
        ("Français", "fr_FR"),
        ("Galego", "gl_ES"),
        ("Hrvatski", "hr_HR"),
        ("Italiano", "it_IT"),
        ("lo jbobau", "jbo"),
        ("Lenga d'òc", "oc_FR"),
        ("Magyar", "hu_HU"),
        ("Nederlands", "nl_NL"),
        ("Norsk", "nb_NO"),
        ("Polski", "pl_PL"),
        ("Português Brasileiro", "pt_BR"),
        ("Português", "pt_PT"),
        ("Română", "ro_RO"),
        ("Slovenčina", "sk_SK"),
        ("Slovenščina", "sl_SI"),
        ("Suomi", "fi_FI"),
        ("Svenska", "sv_SE"),
        ("Tiếng Việt", "vi_VN"),
        ("Türkçe", "tr_TR"),
        ("简体中文", "zh_CN"),
        ("日本語", "ja_JP"),
        ("繁體中文", "zh_TW"),
        ("한국어", "ko_KR"),
        ("Čeština", "cs_CZ"),
        ("Ελληνικά", "el_GR"),
        ("Български", "bg_BG"),
        ("Монгол хэл", "mn_MN"),
        ("русский язык", "ru_RU"),
        ("Српски", "sr_SP"),
        ("українська мова", "uk_UA"),
        ("Հայերեն", "hy_AM"),
        ("עִבְרִית", "he_IL"),
        ("العربية", "ar_SA"),
        ("فارسی", "fa_IR"),
        ("ภาษาไทย", "th_TH"),
        ("Latin", "la_LA"),
    ]
)

# compatibility with old versions
compatMap = {
    "af": "af_ZA",
    "ar": "ar_SA",
    "bg": "bg_BG",
    "ca": "ca_ES",
    "cs": "cs_CZ",
    "da": "da_DK",
    "de": "de_DE",
    "el": "el_GR",
    "en": "en_US",
    "eo": "eo_UY",
    "es": "es_ES",
    "et": "et_EE",
    "eu": "eu_ES",
    "fa": "fa_IR",
    "fi": "fi_FI",
    "fr": "fr_FR",
    "gl": "gl_ES",
    "he": "he_IL",
    "hr": "hr_HR",
    "hu": "hu_HU",
    "hy": "hy_AM",
    "it": "it_IT",
    "ja": "ja_JP",
    "ko": "ko_KR",
    "mn": "mn_MN",
    "ms": "ms_MY",
    "nl": "nl_NL",
    "nb": "nb_NL",
    "no": "nb_NL",
    "oc": "oc_FR",
    "pl": "pl_PL",
    "pt": "pt_PT",
    "ro": "ro_RO",
    "ru": "ru_RU",
    "sk": "sk_SK",
    "sl": "sl_SI",
    "sr": "sr_SP",
    "sv": "sv_SE",
    "th": "th_TH",
    "tr": "tr_TR",
    "uk": "uk_UA",
    "vi": "vi_VN",
}

threadLocal = threading.local()

# global defaults
currentLang: Any = None
currentTranslation: Any = None


def localTranslation() -> Any:
    "Return the translation local to this thread, or the default."
    if getattr(threadLocal, "currentTranslation", None):
        return threadLocal.currentTranslation
    else:
        return currentTranslation


def _(str: str) -> str:
    return localTranslation().gettext(str)


def ngettext(single: str, plural: str, n: int) -> str:
    return localTranslation().ngettext(single, plural, n)


def setLang(lang: str, locale_dir: str, local: bool = True) -> None:
    lang = mungeCode(lang)
    trans = gettext.translation("anki", locale_dir, languages=[lang], fallback=True)
    if local:
        threadLocal.currentLang = lang
        threadLocal.currentTranslation = trans
    else:
        global currentLang, currentTranslation
        currentLang = lang
        currentTranslation = trans


def getLang() -> str:
    "Return the language local to this thread, or the default."
    if getattr(threadLocal, "currentLang", None):
        return threadLocal.currentLang
    else:
        return currentLang


def noHint(str) -> str:
    "Remove translation hint from end of string."
    return re.sub(r"(^.*?)( ?\(.+?\))?$", "\\1", str)


def mungeCode(code: str) -> Any:
    code = code.replace("-", "_")
    if code in compatMap:
        code = compatMap[code]

    return code


if not currentTranslation:
    setLang("en_US", locale_dir="", local=False)
