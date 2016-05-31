# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import urllib.request, urllib.parse, urllib.error
import urllib.request, urllib.error, urllib.parse
import time

from aqt.qt import *
import aqt
from aqt.utils import openLink
from anki.utils import json, platDesc
from aqt.utils import showText


class LatestVersionFinder(QThread):

    newVerAvail = pyqtSignal(str)
    newMsg = pyqtSignal(dict)
    clockIsOff = pyqtSignal(float)

    def __init__(self, main):
        QThread.__init__(self)
        self.main = main
        self.config = main.pm.meta

    def _data(self):
        d = {"ver": aqt.appVersion,
             "os": platDesc(),
             "id": self.config['id'],
             "lm": self.config['lastMsg'],
             "crt": self.config['created']}
        return d

    def run(self):
        if not self.config['updates']:
            return
        d = self._data()
        d['proto'] = 1
        d = urllib.parse.urlencode(d).encode("utf8")
        try:
            f = urllib.request.urlopen(aqt.appUpdate, d)
            resp = f.read()
            if not resp:
                print("update check load failed")
                return
            resp = json.loads(resp.decode("utf8"))
        except:
            # behind proxy, corrupt message, etc
            print("update check failed")
            return
        if resp['msg']:
            self.newMsg.emit(resp)
        if resp['ver']:
            self.newVerAvail.emit(resp['ver'])
        diff = resp['time'] - time.time()
        if abs(diff) > 300:
            self.clockIsOff.emit(diff)

def askAndUpdate(mw, ver):
    baseStr = (
        _('''<h1>Anki Updated</h1>Anki %s has been released.<br><br>''') %
        ver)
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
        mw.pm.meta['suppressUpdate'] = ver
    elif ret == QMessageBox.Yes:
        openLink(aqt.appWebsite)

def showMessages(mw, data):
    showText(data['msg'], parent=mw, type="html")
    mw.pm.meta['lastMsg'] = data['msgId']
