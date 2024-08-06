# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import locale
import re
import warnings
import weakref
from typing import TYPE_CHECKING, Any

import anki
import anki._backend
import anki.i18n_pb2 as _pb
from anki._legacy import DeprecatedNamesMixinForModule

# public exports
TR = anki._fluent.LegacyTranslationEnum
FormatTimeSpan = _pb.FormatTimespanRequest


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
        ("Pусский язык", "ru_RU"),
        ("Српски", "sr_SP"),
        ("Українська мова", "uk_UA"),
        ("Հայերեն", "hy_AM"),
        ("עִבְרִית", "he_IL"),
        ("العربية", "ar_SA"),
        ("فارسی", "fa_IR"),
        ("ภาษาไทย", "th_TH"),
        ("Latin", "la_LA"),
        ("Gaeilge", "ga_IE"),
        ("Беларуская мова", "be_BY"),
        ("ଓଡ଼ିଆ", "or_OR"),
        ("Filipino", "tl"),
        ("ئۇيغۇر", "ug"),
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
    match = re.match("(.*)_", lang)
    if match:
        return match.group(1)
    else:
        return lang


# the currently set interface language
current_lang = "en"  # pylint: disable=invalid-name

# the current Fluent translation instance. Code in pylib/ should
# not reference this, and should use col.tr instead. The global
# instance exists for legacy reasons, and as a convenience for the
# Qt code.
current_i18n: anki._backend.RustBackend | None = None  # pylint: disable=invalid-name
tr_legacyglobal = anki._backend.Translations(None)


def _(str: str) -> str:
    print(f"gettext _() is deprecated: {str}")
    return str


def ngettext(single: str, plural: str, num: int) -> str:
    print(f"ngettext() is deprecated: {plural}")
    return plural


def set_lang(lang: str) -> None:
    global current_lang, current_i18n  # pylint: disable=invalid-name
    current_lang = lang
    current_i18n = anki._backend.RustBackend(langs=[lang])
    tr_legacyglobal.backend = weakref.ref(current_i18n)


def get_def_lang(user_lang: str | None = None) -> tuple[int, str]:
    """Return user_lang converted to name used on disk and its index, defaulting to system language
    or English if not available."""

    def get_index_of_language(wanted_locale: str) -> int | None:
        for i, (_, locale_) in enumerate(langs):
            if locale_ == wanted_locale:
                return i
        return None

    try:
        # getdefaultlocale() is deprecated since Python 3.11, but we need to keep using it as getlocale() behaves differently: https://bugs.python.org/issue38805
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", DeprecationWarning)
            (sys_lang, enc) = locale.getdefaultlocale()
    except AttributeError:
        # this will return a different format on Windows (e.g. Italian_Italy), resulting in us falling back to en_US
        # further below
        (sys_lang, enc) = locale.getlocale()
    except Exception:
        # fails on osx
        sys_lang = "en_US"
    if user_lang in compatMap:
        user_lang = compatMap[user_lang]

    idx = None
    lang = None
    for preferred_lang in (user_lang, sys_lang):
        idx = get_index_of_language(preferred_lang)
        is_language_supported = idx is not None
        if is_language_supported:
            assert preferred_lang is not None
            lang = preferred_lang
            break
    # if the specified language and the system language aren't available, revert to english
    is_preferred_language_supported = idx is not None
    if not is_preferred_language_supported:
        lang = "en_US"
        idx = get_index_of_language(lang)
        is_english_supported = idx is not None
        if not is_english_supported:
            raise AssertionError("English is supposed to be a supported language.")
    assert idx is not None and lang is not None
    return (idx, lang)


def is_rtl(lang: str) -> bool:
    return lang in ("he", "ar", "fa", "ug")


# strip off unicode isolation markers from a translated string
# for testing purposes
def without_unicode_isolation(string: str) -> str:
    return string.replace("\u2068", "").replace("\u2069", "")


def with_collapsed_whitespace(string: str) -> str:
    return re.sub(r"\s+", " ", string)


_deprecated_names = DeprecatedNamesMixinForModule(globals())


if not TYPE_CHECKING:

    def __getattr__(name: str) -> Any:
        return _deprecated_names.__getattr__(name)
