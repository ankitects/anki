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

try:
    import PyQt6
except Exception:
    from .qt5 import *  # type: ignore
else:
    if os.getenv("ENABLE_QT5_COMPAT"):
        print("Running with temporary Qt5 compatibility shims.")
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

_version = QLibraryInfo.version()
qtmajor = _version.majorVersion()
qtminor = _version.minorVersion()
qtpoint = _version.microVersion()
qtfullversion = _version.segments()

if qtmajor < 5 or (qtmajor == 5 and qtminor < 14):
    raise Exception("Anki does not support your Qt version.")


def qconnect(signal: Callable | pyqtSignal | pyqtBoundSignal, func: Callable) -> None:
    """Helper to work around type checking not working with signal.connect(func)."""
    signal.connect(func)  # type: ignore


_T = TypeVar("_T")


def without_qt5_compat_wrapper(cls: _T) -> _T:
    """Remove Qt5 compat wrapper from Qt class, if active.

    Only needed for a few Qt APIs that deal with QVariants."""
    if fn := getattr(cls, "_without_compat_wrapper", None):
        return fn()
    else:
        return cls
