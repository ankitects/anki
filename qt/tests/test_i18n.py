# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import builtins
from unittest.mock import MagicMock

import pytest

import anki.lang
import aqt
from aqt.profiles import ProfileManager
from aqt.qt import QApplication, QLocale, Qt


def test_no_collection_i18n():
    anki.lang.set_lang("zz")
    tr = anki.lang.tr_legacyglobal
    no_uni = anki.lang.without_unicode_isolation
    assert no_uni(tr.statistics_reviews(reviews=2)) == "2 reviews"

    anki.lang.set_lang("ja")
    assert no_uni(tr.statistics_reviews(reviews=2)) == "2枚"


def test_legacy_enum():
    anki.lang.set_lang("ja")
    TR = anki.lang.TR
    tr = anki.lang.tr_legacyglobal
    no_uni = anki.lang.without_unicode_isolation

    assert no_uni(tr(TR.STATISTICS_REVIEWS, reviews=2)) == "2枚"


def test_all_langs_resolve_to_a_locale():
    for _name, code in anki.lang.langs:
        qt_lang = anki.lang.lang_to_disk_lang(code)
        assert QLocale(qt_lang).language() != QLocale.Language.C, qt_lang


@pytest.mark.parametrize("first_time", [False, True])
@pytest.mark.parametrize(
    "saved,force,system,first_expected,later_expected",
    [
        (None, None, "de_DE", "de", "de"),
        (None, None, "ar_SA", "ar", "ar"),
        (None, None, "pt_BR", "pt-BR", "pt-BR"),
        (None, None, "zz_ZZ", "en", "en"),
        (None, None, None, "en", "en"),
        ("", None, "de_DE", "de", "de"),
        (None, "ja", "de_DE", "ja", "ja"),
        ("ja_JP", None, "de_DE", "ja", "ja"),
        ("he_IL", "ja", "de_DE", "he", "ja"),
        (None, "pt-BR", "de_DE", "de", "pt-BR"),
    ],
)
def test_startup_language(
    monkeypatch: pytest.MonkeyPatch,
    first_time: bool,
    saved: str | None,
    force: str | None,
    system: str | None,
    first_expected: str,
    later_expected: str,
) -> None:
    expected = first_expected if first_time else later_expected

    # Keep startup's process-wide state isolated from other tests.
    monkeypatch.setattr(anki.lang, "current_lang", anki.lang.current_lang)
    monkeypatch.setattr(anki.lang, "current_i18n", anki.lang.current_i18n)
    monkeypatch.setattr(
        anki.lang.tr_legacyglobal, "backend", anki.lang.tr_legacyglobal.backend
    )
    monkeypatch.setattr(aqt, "_qtrans", None)
    monkeypatch.setattr(builtins, "_", None, raising=False)
    monkeypatch.setattr(builtins, "ngettext", None, raising=False)
    monkeypatch.setattr(aqt.locale, "setlocale", MagicMock())
    monkeypatch.setattr(anki.lang.locale, "getdefaultlocale", lambda: (system, None))
    translator = MagicMock()
    monkeypatch.setattr(aqt, "QTranslator", lambda: translator)

    pm = MagicMock(spec=ProfileManager)
    pm.meta = {"defaultLang": saved}
    app = MagicMock(spec=QApplication)
    previous_locale = QLocale()
    try:
        backend = aqt.setupLangAndBackend(pm, app, force, first_time)

        assert backend is anki.lang.current_i18n
        assert anki.lang.current_lang == expected
        assert QLocale().name() == QLocale(expected).name()
        direction = (
            Qt.LayoutDirection.RightToLeft
            if expected in ("ar", "he")
            else Qt.LayoutDirection.LeftToRight
        )
        app.setLayoutDirection.assert_called_once_with(direction)
        assert (
            translator.load.call_args.args[0] == f"qtbase_{expected.replace('-', '_')}"
        )
        app.installTranslator.assert_called_once_with(translator)
        assert pm.meta == {"defaultLang": saved}
        pm.setLang.assert_not_called()
    finally:
        QLocale.setDefault(previous_locale)
