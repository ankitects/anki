# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time

import requests

import aqt
from anki.lang import _
from anki.utils import platDesc, versionWithBuild
from aqt.qt import *
from aqt.utils import openLink, showText


class LatestVersionFinder(QThread):

    newVerAvail = pyqtSignal(str)
    newMsg = pyqtSignal(dict)
    clockIsOff = pyqtSignal(float)

    def __init__(self, main):
        QThread.__init__(self)
        self.main = main
        self.config = main.pm.meta

    def _data(self):
        d = {
            "ver": versionWithBuild(),
            "os": platDesc(),
            "id": self.config["id"],
            "lm": self.config["lastMsg"],
            "crt": self.config["created"],
        }
        return d

    def run(self):
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
            self.newMsg.emit(resp)
        if resp["ver"]:
            self.newVerAvail.emit(resp["ver"])
        diff = resp["time"] - time.time()
        if abs(diff) > 300:
            self.clockIsOff.emit(diff)


def askAndUpdate(mw, ver):
    baseStr = _("""<h1>Anki Updated</h1>Anki %s has been released.<br><br>""") % ver
    msg = QMessageBox(mw)
    msg.setStandardButtons(QMessageBox.Yes | QMessageBox.No)
    msg.setIcon(QMessageBox.Information)
    msg.setText(baseStr + _("Would you like to download it now?"))
    button = QPushButton(_("Ignore this update"))
    msg.addButton(button, QMessageBox.RejectRole)
    msg.setDefaultButton(QMessageBox.Yes)
    ret = msg.exec_()
    if msg.clickedButton() == button:
        # ignore this update
        mw.pm.meta["suppressUpdate"] = ver
    elif ret == QMessageBox.Yes:
        openLink(aqt.appWebsite)


def showMessages(mw, data):
    showText(data["msg"], parent=mw, type="html")
    mw.pm.meta["lastMsg"] = data["msgId"]
