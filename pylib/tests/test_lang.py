# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
from collections.abc import Callable

import pytest

from anki import lang

LANG_CODES = [code for _, code in lang.langs]
UNSUPPORTED_LOCALE = "xx_XX"
CORE_FTL_DIR = os.path.join(
    os.path.dirname(__file__), "..", "..", "ftl", "core-repo", "core"
)

SetSystemLocale = Callable[[str], None]


@pytest.fixture
def set_system_locale(monkeypatch: pytest.MonkeyPatch) -> SetSystemLocale:
    def set_locale(code: str) -> None:
        monkeypatch.setattr(lang.locale, "getdefaultlocale", lambda: (code, "UTF-8"))

    return set_locale


@pytest.mark.parametrize("code", LANG_CODES)
def test_get_def_lang_selects_supported_system_locale(
    set_system_locale: SetSystemLocale, code: str
) -> None:
    set_system_locale(code)

    index, selected = lang.get_def_lang()

    assert selected == code
    assert lang.langs[index][1] == code


@pytest.mark.parametrize("code", LANG_CODES)
def test_get_def_lang_selects_preference_over_system_locale(
    set_system_locale: SetSystemLocale, code: str
) -> None:
    set_system_locale(UNSUPPORTED_LOCALE)

    index, selected = lang.get_def_lang(code)

    assert selected == code
    assert lang.langs[index][1] == code


@pytest.mark.parametrize(("short", "full"), lang.compatMap.items())
def test_get_def_lang_maps_short_codes_to_supported_languages(
    set_system_locale: SetSystemLocale, short: str, full: str
) -> None:
    set_system_locale(UNSUPPORTED_LOCALE)

    _, selected = lang.get_def_lang(short)

    assert selected == full


@pytest.mark.parametrize(("short", "full"), lang.compatMap.items())
def test_lang_to_disk_lang_matches_for_short_and_full_codes(
    short: str, full: str
) -> None:
    assert lang.lang_to_disk_lang(short) == lang.lang_to_disk_lang(full)


@pytest.mark.parametrize("code", LANG_CODES)
def test_lang_to_disk_lang_accepts_hyphenated_codes(code: str) -> None:
    assert lang.lang_to_disk_lang(code.replace("_", "-")) == lang.lang_to_disk_lang(
        code
    )


@pytest.mark.parametrize("code", [c for c in LANG_CODES if c != "en_US"])
def test_lang_to_disk_lang_matches_a_translation_folder(code: str) -> None:
    disk_lang = lang.lang_to_disk_lang(code)

    assert os.path.isdir(os.path.join(CORE_FTL_DIR, disk_lang))


def test_get_def_lang_falls_back_to_english_for_unsupported_locales(
    set_system_locale: SetSystemLocale,
) -> None:
    set_system_locale(UNSUPPORTED_LOCALE)

    index, selected = lang.get_def_lang("unknown")

    assert selected == "en_US"
    assert lang.langs[index][1] == "en_US"
