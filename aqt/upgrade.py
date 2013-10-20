# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, cPickle, ctypes, shutil
from aqt.qt import *
from anki.utils import isMac, isWin
from anki import Collection
from anki.importing import Anki1Importer
from aqt.utils import showWarning
import aqt

class Upgrader(object):

    def __init__(self, mw):
        self.mw = mw

    def maybeUpgrade(self):
        p = self._oldConfigPath()
        # does an old config file exist?
        if not p or not os.path.exists(p):
            return
        # load old settings and copy over
        try:
            self._loadConf(p)
        except:
            showWarning(_("""\
Anki wasn't able to load your old config file. Please use File>Import \
to import your decks from previous Anki versions."""))
            return
        if not self._copySettings():
            return
        # and show the wizard
        self._showWizard()

    # Settings
    ######################################################################

    def _oldConfigPath(self):
        if isWin:
            try:
                os.environ['HOME'] = os.environ['APPDATA']
            except:
                # system with %APPDATA% not defined
                return None
            p = "~/.anki/config.db"
        elif isMac:
            p = "~/Library/Application Support/Anki/config.db"
        else:
            p = "~/.anki/config.db"
        return os.path.expanduser(p)

    def _loadConf(self, path):
        self.conf = cPickle.load(open(path))

    def _copySettings(self):
        p = self.mw.pm.profile
        for k in (
            "recentColours", "stripHTML", "editFontFamily", "editFontSize",
            "editLineSize", "deleteMedia", "preserveKeyboard", "numBackups",
            "proxyHost", "proxyPass", "proxyPort", "proxyUser"):
            try:
                p[k] = self.conf[k]
            except:
                showWarning(_("""\
Anki 2.0 only supports automatic upgrading from Anki 1.2. To load old \
decks, please open them in Anki 1.2 to upgrade them, and then import them \
into Anki 2.0."""))
                return
        return True

    # Wizard
    ######################################################################

    def _showWizard(self):
        if not self.conf['recentDeckPaths']:
            # if there are no decks to upgrade, don't show wizard
            return
        class Wizard(QWizard):
            def reject(self):
                pass
        self.wizard = w = Wizard()
        w.addPage(self._welcomePage())
        w.addPage(self._decksPage())
        w.addPage(self._mediaPage())
        w.addPage(self._readyPage())
        w.addPage(self._upgradePage())
        w.addPage(self._finishedPage())
        w.setWindowTitle(_("Upgrade Wizard"))
        w.setWizardStyle(QWizard.ModernStyle)
        w.setOptions(QWizard.NoCancelButton)
        w.exec_()

    def _labelPage(self, title, txt):
        p = QWizardPage()
        p.setTitle(title)
        l = QLabel(txt)
        l.setTextFormat(Qt.RichText)
        l.setTextInteractionFlags(Qt.TextSelectableByMouse)
        l.setWordWrap(True)
        v = QVBoxLayout()
        v.addWidget(l)
        p.setLayout(v)
        return p

    def _welcomePage(self):
        return self._labelPage(_("Welcome"), _("""\
This wizard will guide you through the Anki 2.0 upgrade process.
For a smooth upgrade, please read the following pages carefully.
"""))

    def _decksPage(self):
        return self._labelPage(_("Your Decks"), _("""\
Anki 2 stores your decks in a new format. This wizard will automatically
convert your decks to that format. Your decks will be backed up before
the upgrade, so if you need to revert to the previous version of Anki, your
decks will still be usable."""))

    def _mediaPage(self):
        return self._labelPage(_("Sounds & Images"), _("""\
When your decks are upgraded, Anki will attempt to copy any sounds and images
from the old decks. If you were using a custom DropBox folder or custom media
folder, the upgrade process may not be able to locate your media. Later on, a
report of the upgrade will be presented to you. If you notice media was not
copied when it should have been, please see the upgrade guide for more
instructions.
<p>
AnkiWeb now supports media syncing directly. No special setup is required, and
media will be synchronized along with your cards when you sync to AnkiWeb."""))

    def _readyPage(self):
        class ReadyPage(QWizardPage):
            def initializePage(self):
                self.setTitle(_("Ready to Upgrade"))
                self.setCommitPage(True)
                l = QLabel(_("""\
When you're ready to upgrade, click the commit button to continue. The upgrade
guide will open in your browser while the upgrade proceeds. Please read it
carefully, as a lot has changed since the previous Anki version."""))
                l.setTextFormat(Qt.RichText)
                l.setTextInteractionFlags(Qt.TextSelectableByMouse)
                l.setWordWrap(True)
                v = QVBoxLayout()
                v.addWidget(l)
                self.setLayout(v)
        return ReadyPage()

    def _upgradePage(self):
        decks = self.conf['recentDeckPaths']
        colpath = self.mw.pm.collectionPath()
        upgrader = self
        class UpgradePage(QWizardPage):
            def isComplete(self):
                return False
            def initializePage(self):
                # can't use openLink; gui not ready for tooltips
                QDesktopServices.openUrl(QUrl(aqt.appChanges))
                self.setCommitPage(True)
                self.setTitle(_("Upgrading"))
                self.label = l = QLabel()
                l.setTextInteractionFlags(Qt.TextSelectableByMouse)
                l.setWordWrap(True)
                v = QVBoxLayout()
                v.addWidget(l)
                prog = QProgressBar()
                prog.setMaximum(0)
                v.addWidget(prog)
                l2 = QLabel(_("Please be patient; this can take a while."))
                l2.setTextInteractionFlags(Qt.TextSelectableByMouse)
                l2.setWordWrap(True)
                v.addWidget(l2)
                self.setLayout(v)
                # run the upgrade in a different thread
                self.thread = UpgradeThread(decks, colpath, upgrader.conf)
                self.thread.start()
                # and periodically update the GUI
                self.timer = QTimer(self)
                self.timer.connect(self.timer, SIGNAL("timeout()"), self.onTimer)
                self.timer.start(1000)
                self.onTimer()
            def onTimer(self):
                prog = self.thread.progress()
                if not prog:
                    self.timer.stop()
                    upgrader.log = self.thread.log
                    upgrader.wizard.next()
                self.label.setText(prog)
        return UpgradePage()

    def _finishedPage(self):
        upgrader = self
        class FinishedPage(QWizardPage):
            def initializePage(self):
                buf = ""
                for file in upgrader.log:
                    buf += "<b>%s</b>" % file[0]
                    buf += "<ul><li>" + "<li>".join(file[1]) + "</ul><p>"
                self.setTitle(_("Upgrade Complete"))
                l = QLabel(_("""\
The upgrade has finished, and you're ready to start using Anki 2.0.
<p>
Below is a log of the update:
<p>
%s<br><br>""") % buf)
                l.setTextFormat(Qt.RichText)
                l.setTextInteractionFlags(Qt.TextSelectableByMouse)
                l.setWordWrap(True)
                l.setMaximumWidth(400)
                a = QScrollArea()
                a.setWidget(l)
                v = QVBoxLayout()
                v.addWidget(a)
                self.setLayout(v)
        return FinishedPage()

class UpgradeThread(QThread):

    def __init__(self, paths, colpath, oldprefs):
        QThread.__init__(self)
        self.paths = paths
        self.max = len(paths)
        self.current = 1
        self.finished = False
        self.colpath = colpath
        self.oldprefs = oldprefs
        self.name = ""
        self.log = []

    def run(self):
        # open profile deck
        self.col = Collection(self.colpath)
        # loop through paths
        while True:
            path = self.paths.pop()
            self.name = os.path.basename(path)
            self.upgrade(path)
            # abort if finished
            if not self.paths:
                break
            self.current += 1
        self.col.close()
        self.finished = True

    def progress(self):
        if self.finished:
            return
        return _("Upgrading deck %(a)s of %(b)s...\n%(c)s") % \
            dict(a=self.current, b=self.max, c=self.name)

    def upgrade(self, path):
        log = self._upgrade(path)
        self.log.append((self.name, log))

    def _upgrade(self, path):
        if not os.path.exists(path):
            return [_("File was missing.")]
        imp = Anki1Importer(self.col, path)
        # try to copy over dropbox media first
        try:
            self.maybeCopyFromCustomFolder(path)
        except Exception, e:
            imp.log.append(repr(str(e)))
        # then run the import
        try:
            imp.run()
        except Exception, e:
            if repr(str(e)) == "invalidFile":
                # already logged
                pass
            else:
                imp.log.append(repr(str(e)))
        self.col.save()
        return imp.log

    def maybeCopyFromCustomFolder(self, path):
        folder = os.path.basename(path).replace(".anki", ".media")
        loc = self.oldprefs.get("mediaLocation")
        if not loc:
            # no prefix; user had media next to deck
            return
        elif loc == "dropbox":
            # dropbox no longer exports the folder location; try default
            if isWin:
                dll = ctypes.windll.shell32
                buf = ctypes.create_string_buffer(300)
                dll.SHGetSpecialFolderPathA(None, buf, 0x0005, False)
                loc = os.path.join(buf.value, 'Dropbox')
            else:
                loc = os.path.expanduser("~/Dropbox")
            loc = os.path.join(loc, "Public", "Anki")
        # no media folder in custom location?
        mfolder = os.path.join(loc, folder)
        if not os.path.exists(mfolder):
            return
        # folder exists; copy data next to the deck. leave a copy in the
        # custom location so users can revert easily.
        mdir = self.col.media.dir()
        for f in os.listdir(mfolder):
            src = os.path.join(mfolder, f)
            dst = os.path.join(mdir, f)
            if not os.path.exists(dst):
                shutil.copyfile(src, dst)
