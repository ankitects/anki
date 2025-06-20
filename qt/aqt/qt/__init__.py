# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# make sure not to optimize imports on this file
# pylint: disable=unused-import
from __future__ import annotations

import os
import sys
import traceback
from collections.abc import Callable
from typing import TypeVar, Union

from anki._legacy import deprecated

# legacy code depends on these re-exports
from anki.utils import is_mac, is_win

from .qt6 import *


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

_version = QLibraryInfo.version()
qtmajor = _version.majorVersion()
qtminor = _version.minorVersion()
qtpoint = _version.microVersion()
qtfullversion = _version.segments()

if qtmajor == 6 and qtminor < 2:
    raise Exception("Anki does not support your Qt version.")


def qconnect(signal: Callable | pyqtSignal | pyqtBoundSignal, func: Callable) -> None:
    """Helper to work around type checking not working with signal.connect(func)."""
    signal.connect(func)  # type: ignore


_T = TypeVar("_T")


@deprecated(info="no longer required, and now a no-op")
def without_qt5_compat_wrapper(cls: _T) -> _T:
    return cls
