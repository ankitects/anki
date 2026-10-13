# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.fields import FieldDialog


def test_unique_name_allows_case_only_rename(monkeypatch):
    dialog = FieldDialog.__new__(FieldDialog)
    dialog.model = {"flds": [{"name": "A"}, {"name": "b"}]}

    monkeypatch.setattr("aqt.fields.getOnlyText", lambda prompt, default="": "a")

    assert dialog._uniqueName("prompt", "A") == "a"


def test_unique_name_rejects_duplicate_case_insensitive_names(monkeypatch):
    dialog = FieldDialog.__new__(FieldDialog)
    dialog.model = {"flds": [{"name": "A"}, {"name": "b"}]}

    monkeypatch.setattr("aqt.fields.getOnlyText", lambda prompt, default="": "a")
    monkeypatch.setattr(
        "aqt.fields.tr.fields_that_field_name_is_already_used",
        lambda: "field name already used",
    )
    warned = {"called": False}

    def fake_warning(message):
        warned["called"] = True

    monkeypatch.setattr("aqt.fields.show_warning", fake_warning)

    assert dialog._uniqueName("prompt", "") is None
    assert warned["called"] is True
