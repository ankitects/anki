# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import QApplication, QMenu
from aqt.utils import qtMenuShortcutWorkaround


def test_qt_menu_shortcut_workaround_applies_to_submenus() -> None:
    _app = QApplication.instance() or QApplication([])

    menu = QMenu()
    top_action = menu.addAction("Top")
    submenu = menu.addMenu("Submenu")
    assert submenu is not None
    nested_action = submenu.addAction("Nested")

    assert top_action is not None
    assert nested_action is not None

    top_action.setShortcutVisibleInContextMenu(False)
    nested_action.setShortcutVisibleInContextMenu(False)

    qtMenuShortcutWorkaround(menu)

    assert top_action.isShortcutVisibleInContextMenu()
    assert nested_action.isShortcutVisibleInContextMenu()
