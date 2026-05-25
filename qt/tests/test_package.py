# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys
from unittest.mock import MagicMock

import pytest


@pytest.mark.skipif(sys.platform != "win32", reason="windows taskbar test")
class TestFixWinTaskbarPinningIntegration:
    """Tests using real Win32 APIs. Only runs on Windows."""

    def test_skips_without_launcher(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Function does nothing on Windows when ANKI_LAUNCHER env var is not set."""
        from pywintypes import com_error
        from win32com.shell import shell

        monkeypatch.delenv("ANKI_LAUNCHER", raising=False)

        from aqt.package import _fix_win_taskbar_pinning

        _fix_win_taskbar_pinning()

        with pytest.raises(com_error):
            shell.GetCurrentProcessExplicitAppUserModelID()

    def test_sets_app_user_model_id(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Function sets AppUserModelID when on Windows with ANKI_LAUNCHER set."""
        from win32com.shell import shell

        monkeypatch.setenv("ANKI_LAUNCHER", "testpath")

        from aqt.package import _fix_win_taskbar_pinning

        _fix_win_taskbar_pinning()

        assert shell.GetCurrentProcessExplicitAppUserModelID() == "Ankitects.Anki"


@pytest.mark.skipif(
    sys.platform == "win32",
    reason="mocked platform tests, covered by TestFixWinTaskbarPinningIntegration on Windows",
)
class TestFixWinTaskbarPinning:
    """Tests using mocked Win32 APIs. Runs on macOS and Linux."""

    def test_skips_on_non_windows(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Function does nothing when not running on Windows."""
        monkeypatch.setattr(sys, "platform", "darwin")
        monkeypatch.delenv("ANKI_LAUNCHER", raising=False)

        mock_win32com_shell = MagicMock()
        monkeypatch.setitem(sys.modules, "win32com", MagicMock())
        monkeypatch.setitem(sys.modules, "win32com.shell", mock_win32com_shell)

        from aqt.package import _fix_win_taskbar_pinning

        _fix_win_taskbar_pinning()

        mock_win32com_shell.shell.SetCurrentProcessExplicitAppUserModelID.assert_not_called()

    def test_skips_without_launcher(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Function does nothing on Windows when ANKI_LAUNCHER env var is not set."""
        monkeypatch.setattr(sys, "platform", "win32")
        monkeypatch.delenv("ANKI_LAUNCHER", raising=False)

        mock_win32com_shell = MagicMock()
        monkeypatch.setitem(sys.modules, "win32com", MagicMock())
        monkeypatch.setitem(sys.modules, "win32com.shell", mock_win32com_shell)

        from aqt.package import _fix_win_taskbar_pinning

        _fix_win_taskbar_pinning()

        mock_win32com_shell.shell.SetCurrentProcessExplicitAppUserModelID.assert_not_called()

    def test_sets_app_user_model_id(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Function sets AppUserModelID when on Windows with ANKI_LAUNCHER set."""
        monkeypatch.setattr(sys, "platform", "win32")
        monkeypatch.setenv("ANKI_LAUNCHER", "testpath")

        mock_shell = MagicMock()
        mock_win32com_shell = MagicMock()
        mock_win32com_shell.shell = mock_shell
        monkeypatch.setitem(sys.modules, "win32com", MagicMock())
        monkeypatch.setitem(sys.modules, "win32com.shell", mock_win32com_shell)

        from aqt.package import _fix_win_taskbar_pinning

        _fix_win_taskbar_pinning()

        mock_shell.SetCurrentProcessExplicitAppUserModelID.assert_called_once_with(
            "Ankitects.Anki"
        )
