# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# make sure not to optimize imports on this file
# pylint: disable=unused-import

import os
import sys
import traceback
from typing import Callable, Union

try:
    from PyQt6 import sip
    from PyQt6.QtCore import *

    # conflicting Qt and qFuzzyCompare definitions require an ignore
    from PyQt6.QtGui import *  # type: ignore[misc]
    from PyQt6.QtNetwork import QLocalServer, QLocalSocket, QNetworkProxy
    from PyQt6.QtWebChannel import QWebChannel
    from PyQt6.QtWebEngineCore import *
    from PyQt6.QtWebEngineWidgets import *
    from PyQt6.QtWidgets import *
except:
    from PyQt5.QtCore import *  # type: ignore
    from PyQt5.QtGui import *  # type: ignore
    from PyQt5.QtNetwork import (  # type: ignore
        QLocalServer,
        QLocalSocket,
        QNetworkProxy,
    )
    from PyQt5.QtWebChannel import QWebChannel  # type: ignore
    from PyQt5.QtWebEngineCore import *  # type: ignore
    from PyQt5.QtWebEngineWidgets import *  # type: ignore
    from PyQt5.QtWidgets import *  # type: ignore

    try:
        from PyQt5 import sip  # type: ignore
    except ImportError:
        import sip  # type: ignore

from anki.utils import isMac, isWin

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
    """Helper to work around type checking not working with signal.connect(func).
    Not needed in PyQt6"""
    signal.connect(func)  # type: ignore
