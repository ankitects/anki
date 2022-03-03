# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import sys
from ctypes import CDLL, CFUNCTYPE, c_char_p
from typing import Callable

import aqt
import aqt.utils


class _MacOSHelper:
    def __init__(self) -> None:
        if getattr(sys, "frozen", False):
            path = os.path.join(sys.prefix, "libankihelper.dylib")
        else:
            path = os.path.join(
                aqt.utils.aqt_data_folder(), "lib", "libankihelper.dylib"
            )

        self._dll = CDLL(path)

    def system_is_dark(self) -> bool:
        return self._dll.system_is_dark()

    def set_darkmode_enabled(self, enabled: bool) -> bool:
        return self._dll.set_darkmode_enabled(enabled)

    def start_wav_record(self, path: str, on_error: Callable[[str], None]) -> None:
        global _on_audio_error
        _on_audio_error = on_error
        self._dll.start_wav_record(path.encode("utf8"), _audio_error_callback)

    def end_wav_record(self) -> None:
        "On completion, file should be saved if no error has arrived."
        self._dll.end_wav_record()


# this must not be overwritten or deallocated
@CFUNCTYPE(None, c_char_p)  # type: ignore
def _audio_error_callback(msg: str) -> None:
    if handler := _on_audio_error:
        handler(msg)


_on_audio_error: Callable[[str], None] | None = None

macos_helper: _MacOSHelper | None = None
if sys.platform == "darwin":
    try:
        macos_helper = _MacOSHelper()
    except Exception as e:
        print("macos_helper:", e)
