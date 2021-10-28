# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Platform-specific functionality."""

import os
import sys
from ctypes import CDLL

import aqt.utils
from anki.utils import isMac


def set_dark_mode(enabled: bool) -> bool:
    "True if setting successful."
    if not isMac:
        return False
    try:
        _set_dark_mode(enabled)
        return True
    except Exception as e:
        # swallow exceptions, as library will fail on macOS 10.13
        print(e)
    return False


def _set_dark_mode(enabled: bool) -> None:
    if getattr(sys, "frozen", False):
        path = os.path.join(sys.prefix, "libankihelper.dylib")
    else:
        path = os.path.join(aqt.utils.aqt_data_folder(), "lib", "libankihelper.dylib")
    CDLL(path).set_darkmode_enabled(enabled)
