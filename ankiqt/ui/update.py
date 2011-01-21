# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import urllib, urllib2, os, sys, time, httplib
import anki, anki.utils, anki.lang, anki.stats
import ankiqt
import simplejson, platform

baseUrl = "http://anki.ichi2.net/update/"
#baseUrl = "http://localhost:8001/update/"

class LatestVersionFinder(QThread):

    def __init__(self, main):
        QThread.__init__(self)
        self.main = main
        self.config = main.config
        plat=sys.platform
        pver=platform.platform()
        d = {"ver": ankiqt.appVersion,
             "pver": pver,
             "plat": plat,
             "id": self.config['id'],
             "lm": self.config['lastMsg'],
             "conf": self.config['created']}
        self.stats = d

    def run(self):
        if not self.config['checkForUpdates']:
            return
        d = self.stats
        d['proto'] = 2
        d = urllib.urlencode(d)
        try:
            f = urllib2.urlopen(baseUrl + "getQtVersion", d)
            resp = f.read()
            if not resp:
                return
            resp = simplejson.loads(resp)
        except:
            # behind proxy, corrupt message, etc
            return
        if resp['msg']:
            self.emit(SIGNAL("newMsg"), resp)
        if resp['latestVersion'] > ankiqt.appVersion:
            self.emit(SIGNAL("newVerAvail"), resp)
        diff = resp['currentTime'] - time.time()
        # a fairly liberal time check - sync is more strict
        if abs(diff) > 86400:
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
        QDesktopServices.openUrl(QUrl(ankiqt.appWebsite))

def showMessages(main, data):
    ankiqt.ui.utils.showText(data['msg'], main, type="html")
    main.config['lastMsg'] = data['msgId']
