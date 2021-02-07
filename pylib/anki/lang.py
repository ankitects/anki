# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import locale
import re
from typing import Any, Optional, Tuple

import anki
import anki._backend.backend_pb2 as _pb
import anki._backend.fluent_pb2 as _fluent_pb

# public exports
TR = _fluent_pb.FluentString
FormatTimeSpan = _pb.FormatTimespanIn

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
        ("lo jbobau", "jbo_EN"),
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
        ("Gaeilge", "ga_IE"),
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


def lang_to_disk_lang(lang: str) -> str:
    """Normalize lang, then convert it to name used on disk."""
    # convert it into our canonical representation first
    lang = lang.replace("-", "_")
    if lang in compatMap:
        lang = compatMap[lang]

    # these language/region combinations are fully qualified, but with a hyphen
    if lang in (
        "en_GB",
        "ga_IE",
        "hy_AM",
        "nb_NO",
        "nn_NO",
        "pt_BR",
        "pt_PT",
        "sv_SE",
        "zh_CN",
        "zh_TW",
    ):
        return lang.replace("_", "-")
    # other languages have the region portion stripped
    m = re.match("(.*)_", lang)
    if m:
        return m.group(1)
    else:
        return lang


# the currently set interface language
currentLang = "en"

# the current Fluent translation instance
current_i18n: Optional[anki._backend.RustBackend] = None

# path to locale folder
locale_folder = ""


def _(str: str) -> str:
    print(f"gettext _() is deprecated: {str}")
    return str


def ngettext(single: str, plural: str, n: int) -> str:
    print(f"ngettext() is deprecated: {plural}")
    return plural


def tr_legacyglobal(*args: Any, **kwargs: Any) -> str:
    "Should use col.tr() instead."
    if current_i18n:
        return current_i18n.translate(*args, **kwargs)
    else:
        return "tr_legacyglobal() called without active backend"


def set_lang(lang: str, locale_dir: str) -> None:
    global currentLang, current_i18n, locale_folder
    currentLang = lang
    current_i18n = anki._backend.RustBackend(ftl_folder=locale_folder, langs=[lang])
    locale_folder = locale_dir


def get_def_lang(lang: Optional[str] = None) -> Tuple[int, str]:
    """Return lang converted to name used on disk and its index, defaulting to system language
    or English if not available."""
    try:
        (sys_lang, enc) = locale.getdefaultlocale()
    except:
        # fails on osx
        sys_lang = "en_US"
    user_lang = lang
    if user_lang in compatMap:
        user_lang = compatMap[user_lang]
    idx = None
    lang = None
    en = None
    for l in (user_lang, sys_lang):
        for c, (name, code) in enumerate(langs):
            if code == "en_US":
                en = c
            if code == l:
                idx = c
                lang = l
        if idx is not None:
            break
    # if the specified language and the system language aren't available, revert to english
    if idx is None:
        idx = en
        lang = "en_US"
    return (idx, lang)


def is_rtl(lang: str) -> bool:
    return lang in ("he", "ar", "fa")


# strip off unicode isolation markers from a translated string
# for testing purposes
def without_unicode_isolation(s: str) -> str:
    return s.replace("\u2068", "").replace("\u2069", "")
