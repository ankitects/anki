# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# make sure not to optimize imports on this file
# pylint: disable=unused-import

import os
import sys
import traceback
from typing import Callable, Union

from PyQt5.Qt import *  # type: ignore
from PyQt5.QtCore import *
from PyQt5.QtCore import pyqtRemoveInputHook  # pylint: disable=no-name-in-module
from PyQt5.QtGui import *  # type: ignore
from PyQt5.QtNetwork import QLocalServer, QLocalSocket, QNetworkProxy
from PyQt5.QtWebChannel import QWebChannel
from PyQt5.QtWebEngineWidgets import *
from PyQt5.QtWidgets import *

from anki.utils import isMac, isWin

# fix buggy ubuntu12.04 display of language selector
os.environ["LIBOVERLAY_SCROLLBAR"] = "0"


try:
    from PyQt5 import sip
except ImportError:
    import sip  # type: ignore


def debug() -> None:
    from pdb import set_trace

    pyqtRemoveInputHook()
    set_trace()


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

if qtmajor != 5 or qtminor < 9 or qtminor == 10:
    raise Exception("Anki does not support your Qt version.")


def qconnect(signal: Union[Callable, pyqtSignal], func: Callable) -> None:
    "Helper to work around type checking not working with signal.connect(func)."
    signal.connect(func)  # type: ignore
