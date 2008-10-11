# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import urllib, urllib2, os, sys, time, httplib
import anki, anki.utils, anki.lang, anki.stats
import ankiqt
import simplejson
import tempfile

#baseUrl = "http://localhost:5000/update/"
baseUrl = "http://anki.ichi2.net/update/"

# when requesting latest version number, gather their version, deck size and
# average retention ratio for future development
# FIXME: add ability to disable in prefs, warn about anonymous sent info
class LatestVersionFinder(QThread):

    def __init__(self, main):
        QThread.__init__(self)
        self.main = main
        self.config = main.config
        # calculate stats before we start a new thread
        if self.main.deck != None:
            deckSize = self.main.deck.cardCount()
            stats = anki.stats.globalStats(self.main.deck)
            deckRecall = "%0.2f" % (
                (stats.matureEase3 + stats.matureEase4) /
                float(stats.matureEase0 +
                      stats.matureEase1 +
                      stats.matureEase2 +
                      stats.matureEase3 +
                      stats.matureEase4 + 0.000001) * 100)
            pending = "(%d, %d)" % (self.main.deck.seenCardCount(),
                                    self.main.deck.newCardCount())
            ct = self.main.deck.created
            if ct:
                ol = anki.lang.getLang()
                anki.lang.setLang("en")
                age = anki.utils.fmtTimeSpan(abs(
                    time.time() - ct))
                anki.lang.setLang(ol)
            else:
                age = ""
            plat=sys.platform
            pver=sys.version
        else:
            deckSize = "nodeck"
            deckRecall = ""
            pending = ""
            age = ""
            plat=""
            pver=""
        d = {"ver": ankiqt.appVersion,
             "size": deckSize,
             "rec": deckRecall,
             "pend": pending,
             "age": age,
             "pver": pver,
             "plat": plat,}
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
        if abs(diff) > 300:
            self.emit(SIGNAL("clockIsOff"), diff)

class Updater(QThread):

    filename = "anki-update.exe"
    # FIXME: get when checking version number
    chunkSize = 428027
    percChange = 5

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
        while 1:
            self.setStatus(
                _("Downloading anki updater - %d%% complete.") % perc)
            resp = f.read(self.chunkSize)
            if not resp:
                break
            newfile.write(resp)
            perc += self.percChange
            if perc > 99:
                perc = 99
        newfile.close()
        self.setStatus(_("Updating.."))
        os.chdir(os.path.dirname(filename))
        os.system(os.path.basename(filename))
        self.setStatus(_("Update complete. Please restart Anki."))
        os.unlink(filename)

def askAndUpdate(parent, version=None):
    version = version['latestVersion']
    baseStr = (
        _("""<h1>Anki updated</h1>Anki %s has been released.<br>
The release notes are
<a href="http://ichi2.net/anki/download/index.html#changes">here</a>.
<br><br>""") %
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
            parent.autoUpdate.start()
        else:
            QDesktopServices.openUrl(QUrl(ankiqt.appWebsite))
