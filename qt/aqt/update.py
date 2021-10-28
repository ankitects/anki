# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time
from typing import Any

import requests

import aqt
from anki.utils import plat_desc, version_with_build
from aqt.main import AnkiQt
from aqt.qt import *
from aqt.utils import openLink, showText, tr


class LatestVersionFinder(QThread):

    newVerAvail = pyqtSignal(str)
    newMsg = pyqtSignal(dict)
    clockIsOff = pyqtSignal(float)

    def __init__(self, main: AnkiQt) -> None:
        QThread.__init__(self)
        self.main = main
        self.config = main.pm.meta

    def _data(self) -> dict[str, Any]:
        return {
            "ver": version_with_build(),
            "os": plat_desc(),
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
    baseStr = tr.qt_misc_anki_updatedanki_has_been_released(val=ver)
    msg = QMessageBox(mw)
    msg.setStandardButtons(QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No)  # type: ignore
    msg.setIcon(QMessageBox.Icon.Information)
    msg.setText(baseStr + tr.qt_misc_would_you_like_to_download_it())
    button = QPushButton(tr.qt_misc_ignore_this_update())
    msg.addButton(button, QMessageBox.ButtonRole.RejectRole)
    msg.setDefaultButton(QMessageBox.StandardButton.Yes)
    ret = msg.exec()
    if msg.clickedButton() == button:
        # ignore this update
        mw.pm.meta["suppressUpdate"] = ver
    elif ret == QMessageBox.StandardButton.Yes:
        openLink(aqt.appWebsite)


def showMessages(mw: aqt.AnkiQt, data: dict) -> None:
    showText(data["msg"], parent=mw, type="html")
    mw.pm.meta["lastMsg"] = data["msgId"]
