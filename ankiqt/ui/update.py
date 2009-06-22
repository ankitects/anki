# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import urllib, urllib2, os, sys, time, httplib
import anki, anki.utils, anki.lang, anki.stats
import ankiqt
import simplejson
import tempfile

baseUrl = "http://anki.ichi2.net/update/"
#baseUrl = "http://localhost:8001/update/"

# when requesting latest version number, gather their version, deck size and
# average retention ratio for future development
class LatestVersionFinder(QThread):

    def __init__(self, main):
        QThread.__init__(self)
        self.main = main
        self.config = main.config
        # calculate stats before we start a new thread
        plat=sys.platform
        pver=sys.version.replace("\n", "--")
        if self.main.deck != None:
            deckSize = self.main.deck.cardCount
            stats = self.main.deck.getStats()
            deckRecall = "%0.2f" % stats['gMatureYes%']
            age = self.main.deck.created
        else:
            deckSize = "noDeck"
            deckRecall = ""
            age = ""
        d = {"ver": ankiqt.appVersion,
             "size": deckSize,
             "ret": deckRecall,
             "age": age,
             "pver": pver,
             "plat": plat,
             "id": self.config['id'],
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
        if resp['latestVersion'] > ankiqt.appVersion:
            self.emit(SIGNAL("newVerAvail"), resp)
        diff = resp['currentTime'] - time.time()
        # a fairly liberal time check - sync is more strict
        if abs(diff) > 86400:
            self.emit(SIGNAL("clockIsOff"), diff)

def askAndUpdate(parent, version=None):
    version = version['latestVersion']
    baseStr = (
        _('''<h1>Anki updated</h1>Anki %s has been released.<br>
The release notes are
<a href="http://ichi2.net/anki/download/index.html#changes">here</a>.
<br><br>''') %
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
