# Copyright: Anki Software, LLC and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import QMenu
from aqt.utils import qtMenuShortcutWorkaround


def test_qt_menu_shortcut_workaround_applies_to_submenus(qapp) -> None:
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


from anki.utils import is_lin
from aqt.utils import _available_linux_file_manager, show_in_folder


def test_show_in_folder_passes_path_safely(mocker) -> None:
    mock_popen = mocker.patch("subprocess.Popen")
    proc = mock_popen.return_value
    proc.__enter__.return_value = proc
    proc.communicate.return_value = (None, None)
    proc.poll.return_value = 0
    mocker.patch("aqt.mw")

    path = "foo.txt"
    show_in_folder(path)

    if is_lin and not _available_linux_file_manager():
        mock_popen.assert_not_called()
    else:
        # Path is only passed as the last argument
        args = mock_popen.call_args.args[0]
        kwargs = mock_popen.call_args.kwargs
        assert args[-1] == path
        for arg in args[:-1]:
            assert path not in arg
        assert not kwargs.get("shell", False)
