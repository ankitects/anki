# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import urllib, urllib2, os, sys, time, httplib
import anki, anki.utils, anki.lang, anki.stats
import aqt
import platform
from aqt.utils import openLink
from anki.utils import json

baseUrl = "http://ankiweb.net/update/"
#baseUrl = "http://localhost:8001/update/"

class LatestVersionFinder(QThread):

    def __init__(self, main):
        QThread.__init__(self)
        self.main = main
        self.config = main.pm.meta
        plat=sys.platform
        while 1:
            try:
                pver=platform.platform()
                break
            except IOError:
                # interrupted system call
                continue
        d = {"ver": aqt.appVersion,
             "pver": pver,
             "plat": plat,
             "id": self.config['id'],
             "lm": self.config['lastMsg'],
             "conf": self.config['created']}
        self.stats = d

    def run(self):
        if not self.config['updates']:
            return
        d = self.stats
        d['proto'] = 2
        d = urllib.urlencode(d)
        try:
            f = urllib2.urlopen(baseUrl + "getQtVersion", d)
            resp = f.read()
            if not resp:
                return
            resp = json.loads(resp)
        except:
            # behind proxy, corrupt message, etc
            return
        if resp['msg']:
            self.emit(SIGNAL("newMsg"), resp)
        if resp['latestVersion'] > aqt.appVersion:
            self.emit(SIGNAL("newVerAvail"), resp)
        diff = resp['currentTime'] - time.time()
        if abs(diff) > 300:
            self.emit(SIGNAL("clockIsOff"), diff)

def askAndUpdate(parent, version=None):
    version = version['latestVersion']
    baseStr = (
        _('''<h1>Anki Updated</h1>Anki %s has been released.<br><br>''') %
        version)
    msg = QMessageBox(parent)
    msg.setStandardButtons(QMessageBox.Yes | QMessageBox.No)
    msg.setIcon(QMessageBox.Information)
    msg.setText(baseStr + _("Would you like to download it now?"))
    button = QPushButton(_("Ignore this update"))
    msg.addButton(button, QMessageBox.RejectRole)
    ret = msg.exec_()
    if msg.clickedButton() == button:
        # ignore this update
        parent.config['suppressUpdate'] = version
    elif ret == QMessageBox.Yes:
        openLink(aqt.appWebsite)

def showMessages(main, data):
    aqt.ui.utils.showText(data['msg'], main, type="html")
    main.config['lastMsg'] = data['msgId']
