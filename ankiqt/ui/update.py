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
# FIXME: add ability to disable in prefs, warn about anonymous sent info
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
        except (urllib2.URLError, httplib.BadStatusLine):
            return
        resp = f.read()
        if not resp:
            return
        resp = simplejson.loads(resp)
        if resp['latestVersion'] > ankiqt.appVersion:
            self.emit(SIGNAL("newVerAvail"), resp)
        diff = resp['currentTime'] - time.time()
        # a fairly liberal time check - sync is more strict
        if abs(diff) > 86400:
            self.emit(SIGNAL("clockIsOff"), diff)

class Updater(QThread):

    filename = "anki-update.exe"
    # FIXME: get when checking version number
    chunkSize = 131018
    percChange = 1

    def __init__(self):
        QThread.__init__(self)

    def setStatus(self, msg, timeout=0):
        self.emit(SIGNAL("statusChanged"), msg, timeout)

    def run(self):
        dir = tempfile.mkdtemp(prefix="anki-update")
        os.chdir(dir)
        filename = os.path.abspath(self.filename)
        try:
            f = urllib2.urlopen(baseUrl + "getQt")
        except urllib2.URLError:
            self.setStatus(_("Unable to reach server"))
            return
        try:
            newfile = open(filename, "wb")
        except:
            self.setStatus(_("Unable to open file"))
            return
        perc = 0
        self.emit(SIGNAL("updateStarted"), perc)
        while 1:
            self.emit(SIGNAL("updateDownloading"), perc)
            resp = f.read(self.chunkSize)
            if not resp:
                break
            newfile.write(resp)
            perc += self.percChange
            if perc > 99:
                perc = 99
        newfile.close()
        self.emit(SIGNAL("updateFinished"), perc)
        os.chdir(os.path.dirname(filename))
        os.system(os.path.basename(filename))
        self.setStatus(_("Update complete. Please restart Anki."))
        os.unlink(filename)

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
        if sys.platform == "win32":
            parent.autoUpdate = Updater()
            parent.connect(parent.autoUpdate,
                           SIGNAL("statusChanged"),
                           parent.setStatus)
            parent.connect(parent.autoUpdate,
                           SIGNAL("updateStarted"),
                           parent.updateStarted)
            parent.connect(parent.autoUpdate,
                           SIGNAL("updateDownloading"),
                           parent.updateDownloading)
            parent.connect(parent.autoUpdate,
                           SIGNAL("updateFinished"),
                           parent.updateFinished)
            parent.autoUpdate.start()
        else:
            QDesktopServices.openUrl(QUrl(ankiqt.appWebsite))
