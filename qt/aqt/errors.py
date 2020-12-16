# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import html
import re
import sys
import traceback

from markdown import markdown

from aqt import mw
from aqt.qt import *
from aqt.utils import TR, showText, showWarning, supportText, tr

if not os.environ.get("DEBUG"):

    def excepthook(etype, val, tb):
        sys.stderr.write(
            "Caught exception:\n%s\n"
            % ("".join(traceback.format_exception(etype, val, tb)))
        )

    sys.excepthook = excepthook


class ErrorHandler(QObject):
    "Catch stderr and write into buffer."
    ivl = 100

    errorTimer = pyqtSignal()

    def __init__(self, mw):
        QObject.__init__(self, mw)
        self.mw = mw
        self.timer = None
        qconnect(self.errorTimer, self._setTimer)
        self.pool = ""
        self._oldstderr = sys.stderr
        sys.stderr = self

    def unload(self):
        sys.stderr = self._oldstderr
        sys.excepthook = None

    def write(self, data):
        # dump to stdout
        sys.stdout.write(data)
        # save in buffer
        self.pool += data
        # and update timer
        self.setTimer()

    def setTimer(self):
        # we can't create a timer from a different thread, so we post a
        # message to the object on the main thread
        self.errorTimer.emit()  # type: ignore

    def _setTimer(self):
        if not self.timer:
            self.timer = QTimer(self.mw)
            qconnect(self.timer.timeout, self.onTimeout)
        self.timer.setInterval(self.ivl)
        self.timer.setSingleShot(True)
        self.timer.start()

    def tempFolderMsg(self):
        return tr(TR.QT_MISC_UNABLE_TO_ACCESS_ANKI_MEDIA_FOLDER)

    def onTimeout(self):
        error = html.escape(self.pool)
        self.pool = ""
        self.mw.progress.clear()
        if "abortSchemaMod" in error:
            return
        if "DeprecationWarning" in error:
            return
        if "10013" in error:
            return showWarning(tr(TR.QT_MISC_YOUR_FIREWALL_OR_ANTIVIRUS_PROGRAM_IS))
        if "no default input" in error.lower():
            return showWarning(tr(TR.QT_MISC_PLEASE_CONNECT_A_MICROPHONE_AND_ENSURE))
        if "invalidTempFolder" in error:
            return showWarning(self.tempFolderMsg())
        if "Beautiful Soup is not an HTTP client" in error:
            return
        if "database or disk is full" in error or "Errno 28" in error:
            return showWarning(tr(TR.QT_MISC_YOUR_COMPUTERS_STORAGE_MAY_BE_FULL))
        if "disk I/O error" in error:
            showWarning(markdown(tr(TR.ERRORS_ACCESSING_DB)))
            return

        if self.mw.addonManager.dirty:
            txt = markdown(tr(TR.ERRORS_ADDONS_ACTIVE_POPUP))
            error = supportText() + self._addonText(error) + "\n" + error
        else:
            txt = markdown(tr(TR.ERRORS_STANDARD_POPUP))
            error = supportText() + "\n" + error

        # show dialog
        txt = txt + "<div style='white-space: pre-wrap'>" + error + "</div>"
        showText(txt, type="html", copyBtn=True)

    def _addonText(self, error):
        matches = re.findall(r"addons21/(.*?)/", error)
        if not matches:
            return ""
        # reverse to list most likely suspect first, dict to deduplicate:
        addons = [
            mw.addonManager.addonName(i) for i in dict.fromkeys(reversed(matches))
        ]
        # highlight importance of first add-on:
        addons[0] = "<b>{}</b>".format(addons[0])
        addons_str = ", ".join(addons)
        return tr(TR.ADDONS_POSSIBLY_INVOLVED, addons=addons_str) + "\n"
