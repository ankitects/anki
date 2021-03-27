# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import html
import re
import sys
import traceback
from typing import Optional, TextIO, cast

from markdown import markdown

from aqt import mw
from aqt.main import AnkiQt
from aqt.qt import *
from aqt.utils import showText, showWarning, supportText, tr

if not os.environ.get("DEBUG"):

    def excepthook(etype, val, tb) -> None:  # type: ignore
        sys.stderr.write(
            "Caught exception:\n%s\n"
            % ("".join(traceback.format_exception(etype, val, tb)))
        )

    sys.excepthook = excepthook


class ErrorHandler(QObject):
    "Catch stderr and write into buffer."
    ivl = 100

    errorTimer = pyqtSignal()

    def __init__(self, mw: AnkiQt) -> None:
        QObject.__init__(self, mw)
        self.mw = mw
        self.timer: Optional[QTimer] = None
        qconnect(self.errorTimer, self._setTimer)
        self.pool = ""
        self._oldstderr = sys.stderr
        sys.stderr = cast(TextIO, self)

    def unload(self) -> None:
        sys.stderr = self._oldstderr
        sys.excepthook = None

    def write(self, data: str) -> None:
        # dump to stdout
        sys.stdout.write(data)
        # save in buffer
        self.pool += data
        # and update timer
        self.setTimer()

    def setTimer(self) -> None:
        # we can't create a timer from a different thread, so we post a
        # message to the object on the main thread
        self.errorTimer.emit()  # type: ignore

    def _setTimer(self) -> None:
        if not self.timer:
            self.timer = QTimer(self.mw)
            qconnect(self.timer.timeout, self.onTimeout)
        self.timer.setInterval(self.ivl)
        self.timer.setSingleShot(True)
        self.timer.start()

    def tempFolderMsg(self) -> str:
        return tr.qt_misc_unable_to_access_anki_media_folder()

    def onTimeout(self) -> None:
        error = html.escape(self.pool)
        self.pool = ""
        self.mw.progress.clear()
        if "abortSchemaMod" in error:
            return
        if "DeprecationWarning" in error:
            return
        if "10013" in error:
            showWarning(tr.qt_misc_your_firewall_or_antivirus_program_is())
            return
        if "no default input" in error.lower():
            showWarning(tr.qt_misc_please_connect_a_microphone_and_ensure())
            return
        if "invalidTempFolder" in error:
            showWarning(self.tempFolderMsg())
            return
        if "Beautiful Soup is not an HTTP client" in error:
            return
        if "database or disk is full" in error or "Errno 28" in error:
            showWarning(tr.qt_misc_your_computers_storage_may_be_full())
            return
        if "disk I/O error" in error:
            showWarning(markdown(tr.errors_accessing_db()))
            return

        if self.mw.addonManager.dirty:
            txt = markdown(tr.errors_addons_active_popup())
            error = f"{supportText() + self._addonText(error)}\n{error}"
        else:
            txt = markdown(tr.errors_standard_popup())
            error = f"{supportText()}\n{error}"

        # show dialog
        txt = f"{txt}<div style='white-space: pre-wrap'>{error}</div>"
        showText(txt, type="html", copyBtn=True)

    def _addonText(self, error: str) -> str:
        matches = re.findall(r"addons21/(.*?)/", error)
        if not matches:
            return ""
        # reverse to list most likely suspect first, dict to deduplicate:
        addons = [
            mw.addonManager.addonName(i) for i in dict.fromkeys(reversed(matches))
        ]
        # highlight importance of first add-on:
        addons[0] = f"<b>{addons[0]}</b>"
        addons_str = ", ".join(addons)
        return f"{tr.addons_possibly_involved(addons=addons_str)}\n"
