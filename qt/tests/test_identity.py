# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import anki.lang
import aqt
from aqt.about import brainlift_build_line
from aqt.identity import app_name, brainlift_commit
from aqt.qt import QCoreApplication

anki.lang.set_lang("en")


def test_identity_defaults_to_anki(monkeypatch) -> None:
    monkeypatch.delenv("ANKI_BRAINLIFT_COMMIT", raising=False)

    assert app_name() == "Anki"
    assert brainlift_commit() is None
    assert brainlift_build_line() == ""


def test_identity_accepts_a_full_brainlift_commit(monkeypatch) -> None:
    commit = "0123456789abcdef0123456789abcdef01234567"
    monkeypatch.setenv("ANKI_BRAINLIFT_COMMIT", commit)

    assert app_name() == "Anki Brainlift"
    assert brainlift_commit() == commit
    assert commit in brainlift_build_line()


def test_identity_ignores_a_malformed_brainlift_commit(monkeypatch) -> None:
    monkeypatch.setenv("ANKI_BRAINLIFT_COMMIT", "0123456")

    assert app_name() == "Anki"
    assert brainlift_commit() is None
    assert brainlift_build_line() == ""


def test_installer_smoke_runs_startup_identity_wiring(tmp_path, monkeypatch) -> None:
    commit = "0123456789abcdef0123456789abcdef01234567"
    monkeypatch.setenv("ANKI_BRAINLIFT_COMMIT", commit)
    monkeypatch.setenv("BRAINLIFT_INSTALLER_SMOKE_ONLY", "1")

    assert aqt._run(["Anki Brainlift", "--base", str(tmp_path)], exec=False) is None
    assert QCoreApplication.applicationName() == "Anki Brainlift"
