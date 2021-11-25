# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# make sure not to optimize imports on this file
# pylint: disable=unused-import

import os
import sys
import traceback
from typing import Callable, Union

try:
    import PyQt6
except:
    from .qt5 import *  # type: ignore
else:
    if not os.getenv("DISABLE_QT5_COMPAT"):
        print("Running with temporary Qt5 compatibility shims.")
        print("Run with DISABLE_QT5_COMPAT=1 to confirm compatibility with Qt6.")
        from . import qt5_compat  # needs to be imported first
    from .qt6 import *

from anki.utils import is_mac, is_win

# fix buggy ubuntu12.04 display of language selector
os.environ["LIBOVERLAY_SCROLLBAR"] = "0"


def debug() -> None:
    from pdb import set_trace

    pyqtRemoveInputHook()
    set_trace()  # pylint: disable=forgotten-debug-statement


if os.environ.get("DEBUG"):

    def info(type, value, tb) -> None:  # type: ignore
        for line in traceback.format_exception(type, value, tb):
            sys.stdout.write(line)
        pyqtRemoveInputHook()
        from pdb import pm

        pm()

    sys.excepthook = info

qtmajor = (QT_VERSION & 0xFF0000) >> 16
qtminor = (QT_VERSION & 0x00FF00) >> 8
qtpoint = QT_VERSION & 0xFF

if qtmajor < 5 or (qtmajor == 5 and qtminor < 14):
    raise Exception("Anki does not support your Qt version.")


def qconnect(
    signal: Union[Callable, pyqtSignal, pyqtBoundSignal], func: Callable
) -> None:
    """Helper to work around type checking not working with signal.connect(func)."""
    signal.connect(func)  # type: ignore
