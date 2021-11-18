# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Platform-specific functionality."""

from __future__ import annotations

import os
import sys
from ctypes import CDLL

import aqt.utils
from anki.utils import isMac


def set_macos_dark_mode(enabled: bool) -> bool:
    "True if setting successful."
    if not isMac:
        return False
    try:
        _ankihelper().set_darkmode_enabled(enabled)
        return True
    except Exception as e:
        # swallow exceptions, as library will fail on macOS 10.13
        print(e)
    return False


def get_macos_dark_mode() -> bool:
    if not isMac:
        return False
    try:
        return _ankihelper().system_is_dark()
    except Exception as e:
        # swallow exceptions, as library will fail on macOS 10.13
        print(e)
        return False


_ankihelper_dll: CDLL | None = None


def _ankihelper() -> CDLL:
    global _ankihelper_dll
    if _ankihelper_dll:
        return _ankihelper_dll
    if getattr(sys, "frozen", False):
        path = os.path.join(sys.prefix, "libankihelper.dylib")
    else:
        path = os.path.join(aqt.utils.aqt_data_folder(), "lib", "libankihelper.dylib")
    _ankihelper_dll = CDLL(path)
    return _ankihelper_dll
