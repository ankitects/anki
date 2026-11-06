# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from types import MethodType, SimpleNamespace

import pytest
from PyQt6.QtTest import QTest

import anki.lang
import aqt.forms
from aqt.main import AnkiQt
from aqt.preferences import Preferences
from aqt.profiles import ProfileManager
from aqt.qt import (
    QAction,
    QApplication,
    QDialog,
    QKeySequence,
    QMainWindow,
    QMessageBox,
    QShortcut,
    Qt,
    QTimer,
    QToolButton,
)
from aqt.shortcuts import (
    SHORTCUT_DEFINITIONS,
    SHORTCUTS_BY_ID,
    get_shortcuts,
    key_variants,
)


@pytest.fixture(autouse=True)
def english() -> None:
    # labels are asserted in English, and other tests may change the language
    anki.lang.set_lang("en")


def make_profile_manager() -> ProfileManager:
    manager = ProfileManager.__new__(ProfileManager)
    manager.meta = {}
    return manager


def test_shortcut_definitions_have_unique_ids() -> None:
    ids = [definition.id for definition in SHORTCUT_DEFINITIONS]
    assert len(ids) == len(set(ids))


def test_custom_shortcut_overrides_default_and_can_be_reset() -> None:
    manager = make_profile_manager()
    definition = SHORTCUTS_BY_ID["global.add"]

    assert manager.get_shortcut(definition.id, definition.default_key) == "a"

    manager.set_shortcut(definition.id, "Ctrl+K")
    assert manager.get_shortcut(definition.id, definition.default_key) == "Ctrl+K"

    manager.reset_shortcuts()
    assert manager.get_shortcut(definition.id, definition.default_key) == "a"


def test_answer_key_overrides_preserve_existing_profile_storage() -> None:
    manager = make_profile_manager()

    manager.set_shortcut("review.answer.again", "f")

    assert manager.get_answer_key(1) == "f"
    assert manager.get_shortcut("review.answer.again", "1") == "f"
    assert "shortcuts" not in manager.meta


def test_screen_shortcut_overrides_conflicting_global_shortcut(qapp) -> None:
    window = QMainWindow()
    window.state = "review"
    window._normalize_shortcuts = MethodType(AnkiQt._normalize_shortcuts, window)
    window.applyShortcuts = MethodType(AnkiQt.applyShortcuts, window)
    window.globalShortcuts = [QShortcut(QKeySequence("a"), window)]
    window.stateShortcuts = []
    menu_action = QAction(window)
    window.menuShortcuts = [(menu_action, [QKeySequence("a"), QKeySequence("b")])]

    AnkiQt.setStateShortcuts(window, [("a", lambda: None)])

    assert not window.globalShortcuts[0].isEnabled()
    assert menu_action.shortcuts() == [QKeySequence("b")]
    assert len(window.stateShortcuts) == 1

    AnkiQt.clearStateShortcuts(window)

    assert window.globalShortcuts[0].isEnabled()
    assert menu_action.shortcuts() == [QKeySequence("a"), QKeySequence("b")]
    assert window.stateShortcuts == []


def test_empty_custom_shortcut_disables_action(qapp) -> None:
    window = QMainWindow()
    window._normalize_shortcuts = MethodType(AnkiQt._normalize_shortcuts, window)

    shortcuts = AnkiQt.applyShortcuts(window, [("", lambda: None)])

    assert shortcuts == []


def test_keybindings_editor_searches_and_saves_bindings(qapp) -> None:
    dialog = Preferences.__new__(Preferences)
    QDialog.__init__(dialog)
    dialog.form = aqt.forms.preferences.Ui_Preferences()
    dialog.form.setupUi(dialog)
    manager = make_profile_manager()
    dialog.mw = SimpleNamespace(pm=manager)

    dialog.setup_configurable_shortcuts()

    assert dialog.form.tabWidget.tabText(3) == "Keybindings"
    assert dialog.shortcut_tree.topLevelItem(0).text(0) == "Answer keys"
    dialog.shortcut_search.setText("Sync")
    assert dialog.shortcut_tree.topLevelItem(0).isHidden()

    dialog.shortcut_search.setText("Add")
    general = dialog.shortcut_tree.topLevelItem(3)
    assert not general.isHidden()
    assert (
        sum(not general.child(i).isHidden() for i in range(general.childCount())) == 1
    )

    dialog.shortcut_search.clear()
    dialog.shortcut_key_filter.setKeySequence(QKeySequence("A"))
    dialog._filter_shortcuts()
    assert not general.isHidden()
    assert (
        sum(not general.child(i).isHidden() for i in range(general.childCount())) == 1
    )
    assert not general.child(3).isHidden()
    dialog.shortcut_key_filter.clear()

    editor = dialog.shortcut_editors["global.add"]
    editor.setKeySequence(QKeySequence("Ctrl+K"))
    dialog._shortcut_edited("global.add")
    assert manager.get_shortcut("global.add", "a") == "Ctrl+K"

    dialog._reset_shortcuts()
    assert editor.keySequence().toString() == "A"


def test_keybinding_conflicts_can_be_rejected_or_swapped(qapp) -> None:
    dialog = Preferences.__new__(Preferences)
    QDialog.__init__(dialog)
    dialog.form = aqt.forms.preferences.Ui_Preferences()
    dialog.form.setupUi(dialog)
    manager = make_profile_manager()
    dialog.mw = SimpleNamespace(pm=manager)
    dialog.setup_configurable_shortcuts()

    manager.set_shortcut("global.sync", "Ctrl+K")
    dialog.shortcut_editors["global.sync"].setKeySequence(QKeySequence("Ctrl+K"))
    editor = dialog.shortcut_editors["global.add"]
    editor.setKeySequence(QKeySequence("Ctrl+K"))

    def escape_popup() -> None:
        popup = QApplication.activeModalWidget()
        assert isinstance(popup, QMessageBox)
        QTest.keyClick(popup, Qt.Key.Key_Escape)

    QTimer.singleShot(0, escape_popup)
    dialog._shortcut_edited("global.add")
    assert manager.get_shortcut("global.add", "a") == "a"
    assert manager.get_shortcut("global.sync", "y") == "Ctrl+K"
    assert editor.keySequence().toString() == "A"

    def swap_in_popup() -> None:
        popup = QApplication.activeModalWidget()
        assert isinstance(popup, QMessageBox)
        for button in popup.buttons():
            if popup.buttonRole(button) == QMessageBox.ButtonRole.AcceptRole:
                button.click()
                return
        raise AssertionError("Swap button not found")

    editor.setKeySequence(QKeySequence("Ctrl+K"))
    QTimer.singleShot(0, swap_in_popup)
    dialog._shortcut_edited("global.add")
    assert manager.get_shortcut("global.add", "a") == "Ctrl+K"
    assert manager.get_shortcut("global.sync", "y") == "a"
    assert dialog.shortcut_editors["global.sync"].keySequence().toString() == "A"


def test_shortcut_edit_enter_confirms_and_escape_cancels(qapp) -> None:
    dialog = Preferences.__new__(Preferences)
    QDialog.__init__(dialog)
    dialog.form = aqt.forms.preferences.Ui_Preferences()
    dialog.form.setupUi(dialog)
    manager = make_profile_manager()
    dialog.mw = SimpleNamespace(pm=manager)
    dialog.setup_configurable_shortcuts()
    dialog.form.tabWidget.setCurrentIndex(3)
    dialog.show()
    editor = dialog.shortcut_editors["global.add"]

    editor.setFocus()
    qapp.processEvents()
    assert (
        dialog.shortcut_edit_hint.text() == "Press Enter to confirm, or Esc to cancel."
    )
    QTest.keyClick(editor, Qt.Key.Key_K, Qt.KeyboardModifier.ControlModifier)
    QTest.keyClick(editor, Qt.Key.Key_Return)
    qapp.processEvents()
    assert manager.get_shortcut("global.add", "a") == "Ctrl+K"

    editor.setFocus()
    QTest.keyClick(editor, Qt.Key.Key_O, Qt.KeyboardModifier.ControlModifier)
    QTest.keyClick(editor, Qt.Key.Key_Escape)
    qapp.processEvents()
    assert editor.keySequence().toString() == "Ctrl+K"
    assert manager.get_shortcut("global.add", "a") == "Ctrl+K"
    assert dialog.shortcut_edit_hint.text() == ""

    editor.setFocus()
    qapp.processEvents()
    QTest.keyClick(editor, Qt.Key.Key_O, Qt.KeyboardModifier.ControlModifier)
    confirm_button = editor.findChild(QToolButton)
    assert confirm_button is not None
    assert not confirm_button.icon().isNull()
    QTest.mouseClick(confirm_button, Qt.MouseButton.LeftButton)
    qapp.processEvents()
    assert manager.get_shortcut("global.add", "a") == "Ctrl+O"


def test_shortcut_edit_escape_cancels_after_recording_timeout(qapp) -> None:
    dialog = Preferences.__new__(Preferences)
    QDialog.__init__(dialog)
    dialog.form = aqt.forms.preferences.Ui_Preferences()
    dialog.form.setupUi(dialog)
    manager = make_profile_manager()
    dialog.mw = SimpleNamespace(pm=manager)
    dialog.setup_configurable_shortcuts()
    dialog.form.tabWidget.setCurrentIndex(3)
    dialog.show()
    editor = dialog.shortcut_editors["global.add"]

    editor.setFocus()
    qapp.processEvents()
    QTest.keyClick(editor, Qt.Key.Key_O, Qt.KeyboardModifier.ControlModifier)
    # QKeySequenceEdit emits editingFinished about a second after the last key
    editor.editingFinished.emit()
    QTest.keyClick(editor, Qt.Key.Key_Escape)
    qapp.processEvents()
    assert editor.keySequence().toString() == "A"
    assert manager.get_shortcut("global.add", "a") == "a"


def test_answer_key_conflicts_with_global_shortcut(qapp) -> None:
    dialog = Preferences.__new__(Preferences)
    QDialog.__init__(dialog)
    dialog.form = aqt.forms.preferences.Ui_Preferences()
    dialog.form.setupUi(dialog)
    manager = make_profile_manager()
    dialog.mw = SimpleNamespace(pm=manager)
    dialog.setup_configurable_shortcuts()

    dialog.shortcut_editors["review.answer.good"].setKeySequence(QKeySequence("D"))
    dialog._update_shortcut_conflicts()

    assert "Good" in dialog.shortcut_conflict_label.text()
    assert "Decks" in dialog.shortcut_conflict_label.text()


def test_shortcut_labels_are_translated() -> None:
    for definition in SHORTCUT_DEFINITIONS:
        assert definition.label
        assert "&" not in definition.label
        assert definition.category


def test_menu_shortcuts_are_listed() -> None:
    from aqt.shortcuts import MENU_ACTIONS

    assert SHORTCUTS_BY_ID["global.create_filtered"].default_key == "F"
    assert set(MENU_ACTIONS) <= set(SHORTCUTS_BY_ID)


def test_alternate_keys_are_configurable() -> None:
    manager = make_profile_manager()

    assert get_shortcuts(manager, "global.stats") == ["t", "Shift+T"]
    assert get_shortcuts(manager, "review.show_answer") == ["Space", "Return"]
    assert get_shortcuts(manager, "review.replay_audio") == ["r", "F5"]
    assert get_shortcuts(manager, "global.add") == ["a"]

    manager.set_shortcut("global.stats.alternate", "")
    manager.set_shortcut("global.add.alternate", "Ctrl+K")
    manager.set_shortcut("review.answer.good.alternate", "j")

    assert get_shortcuts(manager, "global.stats") == ["t"]
    assert get_shortcuts(manager, "global.add") == ["a", "Ctrl+K"]
    assert get_shortcuts(manager, "review.answer.good") == ["3", "j"]


def test_key_variants_cover_keypad_and_korean_layout() -> None:
    assert key_variants("Return") == ["Return", "Enter"]
    assert key_variants("e") == ["e", "ㄷ"]
    assert key_variants("Ctrl+Alt+N") == ["Ctrl+Alt+N", "Ctrl+Alt+ㅜ"]
    assert key_variants("Shift+V") == ["Shift+V", "Shift+ㅍ"]
    assert key_variants("Space") == ["Space"]
    assert key_variants("Ctrl++") == ["Ctrl++"]
    assert key_variants("3") == ["3"]


def test_alternate_keybinding_can_be_edited(qapp) -> None:
    dialog = Preferences.__new__(Preferences)
    QDialog.__init__(dialog)
    dialog.form = aqt.forms.preferences.Ui_Preferences()
    dialog.form.setupUi(dialog)
    manager = make_profile_manager()
    dialog.mw = SimpleNamespace(pm=manager)
    dialog.setup_configurable_shortcuts()

    editor = dialog.shortcut_editors["global.stats.alternate"]
    assert editor.keySequence().toString() == "Shift+T"
    editor.setKeySequence(QKeySequence("Ctrl+K"))
    dialog._shortcut_edited("global.stats.alternate")
    assert get_shortcuts(manager, "global.stats") == ["t", "Ctrl+K"]

    dialog.shortcut_key_filter.setKeySequence(QKeySequence("Ctrl+K"))
    dialog._filter_shortcuts()
    general = dialog.shortcut_tree.topLevelItem(3)
    visible = [
        general.child(i).text(0)
        for i in range(general.childCount())
        if not general.child(i).isHidden()
    ]
    assert visible == ["Stats"]

    dialog.shortcut_editors["global.add.alternate"].setKeySequence(
        QKeySequence("Ctrl+K")
    )
    dialog._update_shortcut_conflicts()
    assert "Stats" in dialog.shortcut_conflict_label.text()
    assert "Add" in dialog.shortcut_conflict_label.text()
