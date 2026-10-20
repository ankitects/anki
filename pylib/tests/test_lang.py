# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pytest

from anki import lang


def test_get_def_lang_selects_indonesian_system_locale(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(lang.locale, "getdefaultlocale", lambda: ("id_ID", "UTF-8"))

    index, selected = lang.get_def_lang()

    assert selected == "id_ID"
    assert lang.langs[index][0] == "Bahasa Indonesia"


@pytest.mark.parametrize("requested", ["id", "id_ID"])
def test_get_def_lang_selects_indonesian_preference_over_system_locale(
    monkeypatch: pytest.MonkeyPatch, requested: str
) -> None:
    monkeypatch.setattr(lang.locale, "getdefaultlocale", lambda: ("en_US", "UTF-8"))

    index, selected = lang.get_def_lang(requested)

    assert selected == "id_ID"
    assert lang.langs[index][0] == "Bahasa Indonesia"


@pytest.mark.parametrize("requested", ["id", "id_ID", "id-ID"])
def test_lang_to_disk_lang_uses_indonesian_translation_directory(
    requested: str,
) -> None:
    assert lang.lang_to_disk_lang(requested) == "id"


def test_get_def_lang_falls_back_to_english_for_unsupported_locales(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(lang.locale, "getdefaultlocale", lambda: ("xx_XX", "UTF-8"))

    index, selected = lang.get_def_lang("unknown")

    assert selected == "en_US"
    assert lang.langs[index][0] == "English (United States)"
