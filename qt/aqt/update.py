# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time
from typing import Any, Dict

import requests

import aqt
from anki.utils import platDesc, versionWithBuild
from aqt.main import AnkiQt
from aqt.qt import *
from aqt.utils import TR, openLink, showText, tr


class LatestVersionFinder(QThread):

    newVerAvail = pyqtSignal(str)
    newMsg = pyqtSignal(dict)
    clockIsOff = pyqtSignal(float)

    def __init__(self, main: AnkiQt) -> None:
        QThread.__init__(self)
        self.main = main
        self.config = main.pm.meta

    def _data(self) -> Dict[str, Any]:
        return {
            "ver": versionWithBuild(),
            "os": platDesc(),
            "id": self.config["id"],
            "lm": self.config["lastMsg"],
            "crt": self.config["created"],
        }

    def run(self) -> None:
        if not self.config["updates"]:
            return
        d = self._data()
        d["proto"] = 1

        try:
            r = requests.post(aqt.appUpdate, data=d)
            r.raise_for_status()
            resp = r.json()
        except:
            # behind proxy, corrupt message, etc
            print("update check failed")
            return
        if resp["msg"]:
            self.newMsg.emit(resp)  # type: ignore
        if resp["ver"]:
            self.newVerAvail.emit(resp["ver"])  # type: ignore
        diff = resp["time"] - time.time()
        if abs(diff) > 300:
            self.clockIsOff.emit(diff)  # type: ignore


def askAndUpdate(mw: aqt.AnkiQt, ver: str) -> None:
    baseStr = tr(TR.QT_MISC_ANKI_UPDATEDANKI_HAS_BEEN_RELEASED, val=ver)
    msg = QMessageBox(mw)
    msg.setStandardButtons(QMessageBox.Yes | QMessageBox.No)  # type: ignore
    msg.setIcon(QMessageBox.Information)
    msg.setText(baseStr + tr(TR.QT_MISC_WOULD_YOU_LIKE_TO_DOWNLOAD_IT))
    button = QPushButton(tr(TR.QT_MISC_IGNORE_THIS_UPDATE))
    msg.addButton(button, QMessageBox.RejectRole)
    msg.setDefaultButton(QMessageBox.Yes)
    ret = msg.exec_()
    if msg.clickedButton() == button:
        # ignore this update
        mw.pm.meta["suppressUpdate"] = ver
    elif ret == QMessageBox.Yes:
        openLink(aqt.appWebsite)


def showMessages(mw: aqt.AnkiQt, data: Dict) -> None:
    showText(data["msg"], parent=mw, type="html")
    mw.pm.meta["lastMsg"] = data["msgId"]
