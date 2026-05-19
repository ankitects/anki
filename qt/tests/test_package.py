# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys

import pytest


@pytest.mark.skipif(sys.platform != "win32", reason="windows taskbar test")
@pytest.mark.parametrize("is_launcher", [False, True])
def test_app_user_model_id_set(is_launcher: bool) -> None:
    from pywintypes import com_error
    from win32com.shell import shell

    if is_launcher:
        os.environ["ANKI_LAUNCHER"] = "testpath"

        from aqt.package import _fix_win_taskbar_pinning

        _fix_win_taskbar_pinning()

        assert shell.GetCurrentProcessExplicitAppUserModelID() == "Ankitects.Anki"
    else:
        with pytest.raises(com_error):
            shell.GetCurrentProcessExplicitAppUserModelID()
