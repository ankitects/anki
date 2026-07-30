# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.identity import app_name, brainlift_commit


def test_identity_defaults_to_anki(monkeypatch) -> None:
    monkeypatch.delenv("ANKI_BRAINLIFT_COMMIT", raising=False)

    assert app_name() == "Anki"
    assert brainlift_commit() is None


def test_identity_accepts_a_full_brainlift_commit(monkeypatch) -> None:
    commit = "0123456789abcdef0123456789abcdef01234567"
    monkeypatch.setenv("ANKI_BRAINLIFT_COMMIT", commit)

    assert app_name() == "Anki Brainlift"
    assert brainlift_commit() == commit


def test_identity_ignores_a_malformed_brainlift_commit(monkeypatch) -> None:
    monkeypatch.setenv("ANKI_BRAINLIFT_COMMIT", "0123456")

    assert app_name() == "Anki"
    assert brainlift_commit() is None
