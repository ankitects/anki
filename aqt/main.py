# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
import signal
import zipfile
import gc
import time
import faulthandler
import platform
from threading import Thread

from send2trash import send2trash
from aqt.qt import *
from anki import Collection
from anki.utils import  isWin, isMac, intTime, splitFields, ids2str, \
        devMode
from anki.hooks import runHook, addHook, runFilter
import aqt
import aqt.progress
import aqt.webview
import aqt.toolbar
import aqt.stats
import aqt.mediasrv
import anki.sound
import anki.mpv
from aqt.utils import saveGeom, restoreGeom, showInfo, showWarning, \
    restoreState, getOnlyText, askUser, showText, tooltip, \
    openHelp, openLink, checkInvalidFilename, getFile
from aqt.qt import sip
from anki.lang import _, ngettext

class AnkiQt(QMainWindow):
    def __init__(self, app, profileManager, opts, args):
        QMainWindow.__init__(self)
        self.state = "startup"
        self.opts = opts
        aqt.mw = self
        self.app = app
        self.pm = profileManager
        # running 2.0 for the first time?
        if self.pm.meta['firstRun']:
            # load the new deck user profile
            self.pm.load(self.pm.profiles()[0])
            self.pm.meta['firstRun'] = False
            self.pm.save()
        # init rest of app
        self.safeMode = self.app.queryKeyboardModifiers() & Qt.ShiftModifier
        try:
            self.setupUI()
            self.setupAddons()
        except:
            showInfo(_("Error during startup:\n%s") % traceback.format_exc())
            sys.exit(1)
        # must call this after ui set up
        if self.safeMode:
            tooltip(_("Shift key was held down. Skipping automatic "
                    "syncing and add-on loading."))
        # were we given a file to import?
        if args and args[0]:
            self.onAppMsg(args[0])
        # Load profile in a timer so we can let the window finish init and not
        # close on profile load error.
        self.progress.timer(10, self.setupProfile, False, requiresCollection=False)

    def setupUI(self):
        self.col = None
        self.setupCrashLog()
        self.disableGC()
        self.setupAppMsg()
        self.setupKeys()
        self.setupThreads()
        self.setupMediaServer()
        self.setupSound()
        self.setupSpellCheck()
        self.setupMainWindow()
        self.setupSystemSpecific()
        self.setupStyle()
        self.setupMenus()
        self.setupProgress()
        self.setupErrorHandler()
        self.setupSignals()
        self.setupAutoUpdate()
        self.setupHooks()
        self.setupRefreshTimer()
        self.updateTitleBar()
        # screens
        self.setupDeckBrowser()
        self.setupOverview()
        self.setupReviewer()

    # Profiles
    ##########################################################################

    class ProfileManager(QMainWindow):
        onClose = pyqtSignal()
        closeFires = True

        def closeEvent(self, evt):
            if self.closeFires:
                self.onClose.emit()
            evt.accept()

        def closeWithoutQuitting(self):
            self.closeFires = False
            self.close()
            self.closeFires = True

    def setupProfile(self):
        self.pendingImport = None
        self.restoringBackup = False
        # profile not provided on command line?
        if not self.pm.name:
            # if there's a single profile, load it automatically
            profs = self.pm.profiles()
            if len(profs) == 1:
                self.pm.load(profs[0])
        if not self.pm.name:
            self.showProfileManager()
        else:
            self.loadProfile()

    def showProfileManager(self):
        self.pm.profile = None
        self.state = "profileManager"
        d = self.profileDiag = self.ProfileManager()
        f = self.profileForm = aqt.forms.profiles.Ui_MainWindow()
        f.setupUi(d)
        f.login.clicked.connect(self.onOpenProfile)
        f.profiles.itemDoubleClicked.connect(self.onOpenProfile)
        f.openBackup.clicked.connect(self.onOpenBackup)
        f.quit.clicked.connect(d.close)
        d.onClose.connect(self.cleanupAndExit)
        f.add.clicked.connect(self.onAddProfile)
        f.rename.clicked.connect(self.onRenameProfile)
        f.delete_2.clicked.connect(self.onRemProfile)
        f.profiles.currentRowChanged.connect(self.onProfileRowChange)
        f.statusbar.setVisible(False)
        # enter key opens profile
        QShortcut(QKeySequence("Return"), d, activated=self.onOpenProfile)
        self.refreshProfilesList()
        # raise first, for osx testing
        d.show()
        d.activateWindow()
        d.raise_()

    def refreshProfilesList(self):
        f = self.profileForm
        f.profiles.clear()
        profs = self.pm.profiles()
        f.profiles.addItems(profs)
        try:
            idx = profs.index(self.pm.name)
        except:
            idx = 0
        f.profiles.setCurrentRow(idx)

    def onProfileRowChange(self, n):
        if n < 0:
            # called on .clear()
            return
        name = self.pm.profiles()[n]
        f = self.profileForm
        self.pm.load(name)

    def openProfile(self):
        name = self.pm.profiles()[self.profileForm.profiles.currentRow()]
        return self.pm.load(name)

    def onOpenProfile(self):
        self.loadProfile(self.profileDiag.closeWithoutQuitting)

    def profileNameOk(self, str):
        return not checkInvalidFilename(str)

    def onAddProfile(self):
        name = getOnlyText(_("Name:"))
        if name:
            name = name.strip()
            if name in self.pm.profiles():
                return showWarning(_("Name exists."))
            if not self.profileNameOk(name):
                return
            self.pm.create(name)
            self.pm.name = name
            self.refreshProfilesList()

    def onRenameProfile(self):
        name = getOnlyText(_("New name:"), default=self.pm.name)
        if not name:
            return
        if name == self.pm.name:
            return
        if name in self.pm.profiles():
            return showWarning(_("Name exists."))
        if not self.profileNameOk(name):
            return
        self.pm.rename(name)
        self.refreshProfilesList()

    def onRemProfile(self):
        profs = self.pm.profiles()
        if len(profs) < 2:
            return showWarning(_("There must be at least one profile."))
        # sure?
        if not askUser(_("""\
All cards, notes, and media for this profile will be deleted. \
Are you sure?"""), msgfunc=QMessageBox.warning, defaultno=True):
            return
        self.pm.remove(self.pm.name)
        self.refreshProfilesList()

    def onOpenBackup(self):
        if not askUser(_("""\
Replace your collection with an earlier backup?"""),
                       msgfunc=QMessageBox.warning,
                       defaultno=True):
            return
        def doOpen(path):
            self._openBackup(path)
        getFile(self.profileDiag, _("Revert to backup"),
                cb=doOpen, filter="*.colpkg", dir=self.pm.backupFolder())

    def _openBackup(self, path):
        try:
            # move the existing collection to the trash, as it may not open
            self.pm.trashCollection()
        except:
            showWarning(_("Unable to move existing file to trash - please try restarting your computer."))
            return

        self.pendingImport = path
        self.restoringBackup = True

        showInfo(_("""\
Automatic syncing and backups have been disabled while restoring. To enable them again, \
close the profile or restart Anki."""))

        self.onOpenProfile()

    def loadProfile(self, onsuccess=None):
        self.maybeAutoSync()

        if not self.loadCollection():
            return

        # show main window
        if self.pm.profile['mainWindowState']:
            restoreGeom(self, "mainWindow")
            restoreState(self, "mainWindow")
        # titlebar
        self.setWindowTitle(self.pm.name + " - Anki")
        # show and raise window for osx
        self.show()
        self.activateWindow()
        self.raise_()

        # import pending?
        if self.pendingImport:
            self.handleImport(self.pendingImport)
            self.pendingImport = None
        runHook("profileLoaded")
        if onsuccess:
            onsuccess()

    def unloadProfile(self, onsuccess):
        def callback():
            self._unloadProfile()
            onsuccess()

        runHook("unloadProfile")
        self.unloadCollection(callback)

    def _unloadProfile(self):
        self.pm.profile['mainWindowGeom'] = self.saveGeometry()
        self.pm.profile['mainWindowState'] = self.saveState()
        self.pm.save()
        self.hide()

        self.restoringBackup = False

        # at this point there should be no windows left
        self._checkForUnclosedWidgets()

        self.maybeAutoSync()

    def _checkForUnclosedWidgets(self):
        for w in self.app.topLevelWidgets():
            if w.isVisible():
                # windows with this property are safe to close immediately
                if getattr(w, "silentlyClose", None):
                    w.close()
                else:
                    showWarning("Window should have been closed: {}".format(w))

    def unloadProfileAndExit(self):
        self.unloadProfile(self.cleanupAndExit)

    def unloadProfileAndShowProfileManager(self):
        self.unloadProfile(self.showProfileManager)

    def cleanupAndExit(self):
        self.errorHandler.unload()
        self.mediaServer.shutdown()
        anki.sound.cleanupMPV()
        self.app.exit(0)

    # Sound/video
    ##########################################################################

    def setupSound(self):
        if isWin:
            return
        try:
            anki.sound.setupMPV()
        except FileNotFoundError:
            print("mpv not found, reverting to mplayer")
        except anki.mpv.MPVProcessError:
            print("mpv too old, reverting to mplayer")

    # Collection load/unload
    ##########################################################################

    def loadCollection(self):
        try:
            return self._loadCollection()
        except Exception as e:
            showWarning(_("""\
Anki was unable to open your collection file. If problems persist after \
restarting your computer, please use the Open Backup button in the profile \
manager.

Debug info:
""")+traceback.format_exc())
            # clean up open collection if possible
            if self.col:
                try:
                    self.col.close(save=False)
                except:
                    pass
                self.col = None

            # return to profile manager
            self.showProfileManager()
            return False

    def _loadCollection(self):
        cpath = self.pm.collectionPath()

        self.col = Collection(cpath, log=True)

        self.setEnabled(True)
        self.progress.setupDB(self.col.db)
        self.maybeEnableUndo()
        self.moveToState("deckBrowser")
        return True

    def unloadCollection(self, onsuccess):
        def callback():
            self.setEnabled(False)
            self._unloadCollection()
            onsuccess()

        self.closeAllWindows(callback)

    def _unloadCollection(self):
        if not self.col:
            return
        if self.restoringBackup:
            label = _("Closing...")
        else:
            label = _("Backing Up...")
        self.progress.start(label=label, immediate=True)
        corrupt = False
        try:
            self.maybeOptimize()
            if not devMode:
                corrupt = self.col.db.scalar("pragma integrity_check") != "ok"
        except:
            corrupt = True
        try:
            self.col.close()
        except:
            corrupt = True
        finally:
            self.col = None
        if corrupt:
            showWarning(_("Your collection file appears to be corrupt. \
This can happen when the file is copied or moved while Anki is open, or \
when the collection is stored on a network or cloud drive. If problems \
persist after restarting your computer, please open an automatic backup \
from the profile screen."))
        if not corrupt and not self.restoringBackup:
            self.backup()

        self.progress.finish()

    # Backup and auto-optimize
    ##########################################################################

    class BackupThread(Thread):
        def __init__(self, path, data):
            Thread.__init__(self)
            self.path = path
            self.data = data
            # create the file in calling thread to ensure the same
            # file is not created twice
            open(self.path, "wb").close()

        def run(self):
            z = zipfile.ZipFile(self.path, "w", zipfile.ZIP_DEFLATED)
            z.writestr("collection.anki2", self.data)
            z.writestr("media", "{}")
            z.close()

    def backup(self):
        nbacks = self.pm.profile['numBackups']
        if not nbacks or devMode:
            return
        dir = self.pm.backupFolder()
        path = self.pm.collectionPath()

        # do backup
        fname = time.strftime("backup-%Y-%m-%d-%H.%M.%S.colpkg", time.localtime(time.time()))
        newpath = os.path.join(dir, fname)
        with open(path, "rb") as f:
            data = f.read()
        b = self.BackupThread(newpath, data)
        b.start()

        # find existing backups
        backups = []
        for file in os.listdir(dir):
            # only look for new-style format
            m = re.match(r"backup-\d{4}-\d{2}-.+.colpkg", file)
            if not m:
                continue
            backups.append(file)
        backups.sort()

        # remove old ones
        while len(backups) > nbacks:
            fname = backups.pop(0)
            path = os.path.join(dir, fname)
            os.unlink(path)

    def maybeOptimize(self):
        # have two weeks passed?
        if (intTime() - self.pm.profile['lastOptimize']) < 86400*14:
            return
        self.progress.start(label=_("Optimizing..."), immediate=True)
        self.col.optimize()
        self.pm.profile['lastOptimize'] = intTime()
        self.pm.save()
        self.progress.finish()

    # State machine
    ##########################################################################

    def moveToState(self, state, *args):
        #print("-> move from", self.state, "to", state)
        oldState = self.state or "dummy"
        cleanup = getattr(self, "_"+oldState+"Cleanup", None)
        if cleanup:
            # pylint: disable=not-callable
            cleanup(state)
        self.clearStateShortcuts()
        self.state = state
        runHook('beforeStateChange', state, oldState, *args)
        getattr(self, "_"+state+"State")(oldState, *args)
        if state != "resetRequired":
            self.bottomWeb.show()
        runHook('afterStateChange', state, oldState, *args)

    def _deckBrowserState(self, oldState):
        self.deckBrowser.show()

    def _colLoadingState(self, oldState):
        "Run once, when col is loaded."
        self.enableColMenuItems()
        # ensure cwd is set if media dir exists
        self.col.media.dir()
        runHook("colLoading", self.col)
        self.moveToState("overview")

    def _selectedDeck(self):
        did = self.col.decks.selected()
        if not self.col.decks.nameOrNone(did):
            showInfo(_("Please select a deck."))
            return
        return self.col.decks.get(did)

    def _overviewState(self, oldState):
        if not self._selectedDeck():
            return self.moveToState("deckBrowser")
        self.col.reset()
        self.overview.show()

    def _reviewState(self, oldState):
        self.reviewer.show()

    def _reviewCleanup(self, newState):
        if newState != "resetRequired" and newState != "review":
            self.reviewer.cleanup()

    def noteChanged(self, nid):
        "Called when a card or note is edited (but not deleted)."
        runHook("noteChanged", nid)

    # Resetting state
    ##########################################################################

    def reset(self, guiOnly=False):
        "Called for non-trivial edits. Rebuilds queue and updates UI."
        if self.col:
            if not guiOnly:
                self.col.reset()
            runHook("reset")
            self.maybeEnableUndo()
            self.moveToState(self.state)

    def requireReset(self, modal=False):
        "Signal queue needs to be rebuilt when edits are finished or by user."
        self.autosave()
        self.resetModal = modal
        if self.interactiveState():
            self.moveToState("resetRequired")

    def interactiveState(self):
        "True if not in profile manager, syncing, etc."
        return self.state in ("overview", "review", "deckBrowser")

    def maybeReset(self):
        self.autosave()
        if self.state == "resetRequired":
            self.state = self.returnState
            self.reset()

    def delayedMaybeReset(self):
        # if we redraw the page in a button click event it will often crash on
        # windows
        self.progress.timer(100, self.maybeReset, False)

    def _resetRequiredState(self, oldState):
        if oldState != "resetRequired":
            self.returnState = oldState
        if self.resetModal:
            # we don't have to change the webview, as we have a covering window
            return
        self.web.resetHandlers()
        self.web.onBridgeCmd = lambda url: self.delayedMaybeReset()
        i = _("Waiting for editing to finish.")
        b = self.button("refresh", _("Resume Now"), id="resume")
        self.web.stdHtml("""
<center><div style="height: 100%%">
<div style="position:relative; vertical-align: middle;">
%s<br><br>
%s</div></div></center>
<script>$('#resume').focus()</script>
""" % (i, b))
        self.bottomWeb.hide()
        self.web.setFocus()

    # HTML helpers
    ##########################################################################

    def button(self, link, name, key=None, class_="", id="", extra=""):
        class_ = "but "+ class_
        if key:
            key = _("Shortcut key: %s") % key
        else:
            key = ""
        return '''
<button id="%s" class="%s" onclick="pycmd('%s');return false;"
title="%s" %s>%s</button>''' % (
            id, class_, link, key, extra, name)

    # Main window setup
    ##########################################################################

    def setupMainWindow(self):
        # main window
        self.form = aqt.forms.main.Ui_MainWindow()
        self.form.setupUi(self)
        # toolbar
        tweb = self.toolbarWeb = aqt.webview.AnkiWebView()
        tweb.title = "top toolbar"
        tweb.setFocusPolicy(Qt.WheelFocus)
        self.toolbar = aqt.toolbar.Toolbar(self, tweb)
        self.toolbar.draw()
        # main area
        self.web = aqt.webview.AnkiWebView()
        self.web.title = "main webview"
        self.web.setFocusPolicy(Qt.WheelFocus)
        self.web.setMinimumWidth(400)
        # bottom area
        sweb = self.bottomWeb = aqt.webview.AnkiWebView()
        sweb.title = "bottom toolbar"
        sweb.setFocusPolicy(Qt.WheelFocus)
        # add in a layout
        self.mainLayout = QVBoxLayout()
        self.mainLayout.setContentsMargins(0,0,0,0)
        self.mainLayout.setSpacing(0)
        self.mainLayout.addWidget(tweb)
        self.mainLayout.addWidget(self.web)
        self.mainLayout.addWidget(sweb)
        self.form.centralwidget.setLayout(self.mainLayout)

    def closeAllWindows(self, onsuccess):
        aqt.dialogs.closeAll(onsuccess)

    # Components
    ##########################################################################

    def setupSignals(self):
        signal.signal(signal.SIGINT, self.onSigInt)

    def onSigInt(self, signum, frame):
        # interrupt any current transaction and schedule a rollback & quit
        if self.col:
            self.col.db.interrupt()
        def quit():
            self.col.db.rollback()
            self.close()
        self.progress.timer(100, quit, False)

    def setupProgress(self):
        self.progress = aqt.progress.ProgressManager(self)

    def setupErrorHandler(self):
        import aqt.errors
        self.errorHandler = aqt.errors.ErrorHandler(self)

    def setupAddons(self):
        import aqt.addons
        self.addonManager = aqt.addons.AddonManager(self)
        if not self.safeMode:
            self.addonManager.loadAddons()

    def setupSpellCheck(self):
        os.environ["QTWEBENGINE_DICTIONARIES_PATH"] = (
            os.path.join(self.pm.base, "dictionaries"))

    def setupThreads(self):
        self._mainThread = QThread.currentThread()

    def inMainThread(self):
        return self._mainThread == QThread.currentThread()

    def setupDeckBrowser(self):
        from aqt.deckbrowser import DeckBrowser
        self.deckBrowser = DeckBrowser(self)

    def setupOverview(self):
        from aqt.overview import Overview
        self.overview = Overview(self)

    def setupReviewer(self):
        from aqt.reviewer import Reviewer
        self.reviewer = Reviewer(self)

    # Syncing
    ##########################################################################

    # expects a current profile and a loaded collection; reloads
    # collection after sync completes
    def onSync(self):
        self.unloadCollection(self._onSync)

    def _onSync(self):
        self._sync()
        if not self.loadCollection():
            return

    # expects a current profile, but no collection loaded
    def maybeAutoSync(self):
        if (not self.pm.profile['syncKey']
            or not self.pm.profile['autoSync']
            or self.safeMode
            or self.restoringBackup):
            return

        # ok to sync
        self._sync()

    def _sync(self):
        from aqt.sync import SyncManager
        self.state = "sync"
        self.syncer = SyncManager(self, self.pm)
        self.syncer.sync()

    # Tools
    ##########################################################################

    def raiseMain(self):
        if not self.app.activeWindow():
            # make sure window is shown
            self.setWindowState(self.windowState() & ~Qt.WindowMinimized)
        return True

    def setupStyle(self):
        buf = ""

        if isWin and platform.release() == '10':
            # add missing bottom border to menubar
            buf += """
QMenuBar {
  border-bottom: 1px solid #aaa;
  background: white;
}        
"""
            # qt bug? setting the above changes the browser sidebar
            # to white as well, so set it back
            buf += """
QTreeWidget {
  background: #eee;
}            
            """

        # allow addons to modify the styling
        buf = runFilter("setupStyle", buf)

        # allow users to extend styling
        p = os.path.join(aqt.mw.pm.base, "style.css")
        if os.path.exists(p):
            buf += open(p).read()

        self.app.setStyleSheet(buf)

    # Key handling
    ##########################################################################

    def setupKeys(self):
        globalShortcuts = [
            ("Ctrl+:", self.onDebug),
            ("d", lambda: self.moveToState("deckBrowser")),
            ("s", self.onStudyKey),
            ("a", self.onAddCard),
            ("b", self.onBrowse),
            ("t", self.onStats),
            ("y", self.onSync)
        ]
        self.applyShortcuts(globalShortcuts)

        self.stateShortcuts = []

    def applyShortcuts(self, shortcuts):
        qshortcuts = []
        for key, fn in shortcuts:
            scut = QShortcut(QKeySequence(key), self, activated=fn)
            scut.setAutoRepeat(False)
            qshortcuts.append(scut)
        return qshortcuts

    def setStateShortcuts(self, shortcuts):
        runHook(self.state+"StateShortcuts", shortcuts)
        self.stateShortcuts = self.applyShortcuts(shortcuts)

    def clearStateShortcuts(self):
        for qs in self.stateShortcuts:
            sip.delete(qs)
        self.stateShortcuts = []

    def onStudyKey(self):
        if self.state == "overview":
            self.col.startTimebox()
            self.moveToState("review")
        else:
            self.moveToState("overview")

    # App exit
    ##########################################################################

    def closeEvent(self, event):
        if self.state == "profileManager":
            # if profile manager active, this event may fire via OS X menu bar's
            # quit option
            self.profileDiag.close()
            event.accept()
        else:
            # ignore the event for now, as we need time to clean up
            event.ignore()
            self.unloadProfileAndExit()

    # Undo & autosave
    ##########################################################################

    def onUndo(self):
        n = self.col.undoName()
        if not n:
            return
        cid = self.col.undo()
        if cid and self.state == "review":
            card = self.col.getCard(cid)
            self.reviewer.cardQueue.append(card)
            self.reviewer.nextCard()
            self.maybeEnableUndo()
            return

        tooltip(_("Reverted to state prior to '%s'.") % n.lower())
        self.reset()
        self.maybeEnableUndo()

    def maybeEnableUndo(self):
        if self.col and self.col.undoName():
            self.form.actionUndo.setText(_("Undo %s") %
                                            self.col.undoName())
            self.form.actionUndo.setEnabled(True)
            runHook("undoState", True)
        else:
            self.form.actionUndo.setText(_("Undo"))
            self.form.actionUndo.setEnabled(False)
            runHook("undoState", False)

    def checkpoint(self, name):
        self.col.save(name)
        self.maybeEnableUndo()

    def autosave(self):
        saved = self.col.autosave()
        self.maybeEnableUndo()
        if saved:
            self.doGC()

    # Other menu operations
    ##########################################################################

    def onAddCard(self):
        aqt.dialogs.open("AddCards", self)

    def onBrowse(self):
        aqt.dialogs.open("Browser", self)

    def onEditCurrent(self):
        aqt.dialogs.open("EditCurrent", self)

    def onDeckConf(self, deck=None):
        if not deck:
            deck = self.col.decks.current()
        if deck['dyn']:
            import aqt.dyndeckconf
            aqt.dyndeckconf.DeckConf(self, deck=deck)
        else:
            import aqt.deckconf
            aqt.deckconf.DeckConf(self, deck)

    def onOverview(self):
        self.col.reset()
        self.moveToState("overview")

    def onStats(self):
        deck = self._selectedDeck()
        if not deck:
            return
        aqt.dialogs.open("DeckStats", self)

    def onPrefs(self):
        aqt.dialogs.open("Preferences", self)

    def onNoteTypes(self):
        import aqt.models
        aqt.models.Models(self, self, fromMain=True)

    def onAbout(self):
        aqt.dialogs.open("About", self)

    def onDonate(self):
        openLink(aqt.appDonate)

    def onDocumentation(self):
        openHelp("")

    # Importing & exporting
    ##########################################################################

    def handleImport(self, path):
        import aqt.importing
        if not os.path.exists(path):
            return showInfo(_("Please use File>Import to import this file."))

        aqt.importing.importFile(self, path)

    def onImport(self):
        import aqt.importing
        aqt.importing.onImport(self)

    def onExport(self, did=None):
        import aqt.exporting
        aqt.exporting.ExportDialog(self, did=did)

    # Cramming
    ##########################################################################

    def onCram(self, search=""):
        import aqt.dyndeckconf
        n = 1
        deck = self.col.decks.current()
        if not search:
            if not deck['dyn']:
                search = 'deck:"%s" ' % deck['name']
        decks = self.col.decks.allNames()
        while _("Filtered Deck %d") % n in decks:
            n += 1
        name = _("Filtered Deck %d") % n
        did = self.col.decks.newDyn(name)
        diag = aqt.dyndeckconf.DeckConf(self, first=True, search=search)
        if not diag.ok:
            # user cancelled first config
            self.col.decks.rem(did)
            self.col.decks.select(deck['id'])

    # Menu, title bar & status
    ##########################################################################

    def setupMenus(self):
        m = self.form
        m.actionSwitchProfile.triggered.connect(
            self.unloadProfileAndShowProfileManager)
        m.actionImport.triggered.connect(self.onImport)
        m.actionExport.triggered.connect(self.onExport)
        m.actionExit.triggered.connect(self.close)
        m.actionPreferences.triggered.connect(self.onPrefs)
        m.actionAbout.triggered.connect(self.onAbout)
        m.actionUndo.triggered.connect(self.onUndo)
        if qtminor < 11:
            m.actionUndo.setShortcut(QKeySequence(_("Ctrl+Alt+Z")))
        m.actionFullDatabaseCheck.triggered.connect(self.onCheckDB)
        m.actionCheckMediaDatabase.triggered.connect(self.onCheckMediaDB)
        m.actionDocumentation.triggered.connect(self.onDocumentation)
        m.actionDonate.triggered.connect(self.onDonate)
        m.actionStudyDeck.triggered.connect(self.onStudyDeck)
        m.actionCreateFiltered.triggered.connect(self.onCram)
        m.actionEmptyCards.triggered.connect(self.onEmptyCards)
        m.actionNoteTypes.triggered.connect(self.onNoteTypes)

    def updateTitleBar(self):
        self.setWindowTitle("Anki")

    # Auto update
    ##########################################################################

    def setupAutoUpdate(self):
        import aqt.update
        self.autoUpdate = aqt.update.LatestVersionFinder(self)
        self.autoUpdate.newVerAvail.connect(self.newVerAvail)
        self.autoUpdate.newMsg.connect(self.newMsg)
        self.autoUpdate.clockIsOff.connect(self.clockIsOff)
        self.autoUpdate.start()

    def newVerAvail(self, ver):
        if self.pm.meta.get('suppressUpdate', None) != ver:
            aqt.update.askAndUpdate(self, ver)

    def newMsg(self, data):
        aqt.update.showMessages(self, data)

    def clockIsOff(self, diff):
        diffText = ngettext("%s second", "%s seconds", diff) % diff
        warn = _("""\
In order to ensure your collection works correctly when moved between \
devices, Anki requires your computer's internal clock to be set correctly. \
The internal clock can be wrong even if your system is showing the correct \
local time.

Please go to the time settings on your computer and check the following:

- AM/PM
- Clock drift
- Day, month and year
- Timezone
- Daylight savings

Difference to correct time: %s.""") % diffText
        showWarning(warn)
        self.app.closeAllWindows()

    # Count refreshing
    ##########################################################################

    def setupRefreshTimer(self):
        # every 10 minutes
        self.progress.timer(10*60*1000, self.onRefreshTimer, True)

    def onRefreshTimer(self):
        if self.state == "deckBrowser":
            self.deckBrowser.refresh()
        elif self.state == "overview":
            self.overview.refresh()

    # Permanent libanki hooks
    ##########################################################################

    def setupHooks(self):
        addHook("modSchema", self.onSchemaMod)
        addHook("remNotes", self.onRemNotes)
        addHook("odueInvalid", self.onOdueInvalid)

        addHook("mpvWillPlay", self.onMpvWillPlay)
        addHook("mpvIdleHook", self.onMpvIdle)
        self._activeWindowOnPlay = None

    def onOdueInvalid(self):
        showWarning(_("""\
Invalid property found on card. Please use Tools>Check Database, \
and if the problem comes up again, please ask on the support site."""))

    def _isVideo(self, file):
        head, ext = os.path.splitext(file.lower())
        return ext in (".mp4", ".mov", ".mpg", ".mpeg", ".mkv", ".avi")

    def onMpvWillPlay(self, file):
        if not self._isVideo(file):
            return

        self._activeWindowOnPlay = self.app.activeWindow() or self._activeWindowOnPlay

    def onMpvIdle(self):
        w = self._activeWindowOnPlay
        if not self.app.activeWindow() and w and not sip.isdeleted(w) and w.isVisible():
            w.activateWindow()
            w.raise_()
        self._activeWindowOnPlay = None

    # Log note deletion
    ##########################################################################

    def onRemNotes(self, col, nids):
        path = os.path.join(self.pm.profileFolder(), "deleted.txt")
        existed = os.path.exists(path)
        with open(path, "ab") as f:
            if not existed:
                f.write(b"nid\tmid\tfields\n")
            for id, mid, flds in col.db.execute(
                    "select id, mid, flds from notes where id in %s" %
                ids2str(nids)):
                fields = splitFields(flds)
                f.write(("\t".join([str(id), str(mid)] + fields)).encode("utf8"))
                f.write(b"\n")

    # Schema modifications
    ##########################################################################

    def onSchemaMod(self, arg):
        return askUser(_("""\
The requested change will require a full upload of the database when \
you next synchronize your collection. If you have reviews or other changes \
waiting on another device that haven't been synchronized here yet, they \
will be lost. Continue?"""))

    # Advanced features
    ##########################################################################

    def onCheckDB(self):
        "True if no problems"
        self.progress.start(immediate=True)
        ret, ok = self.col.fixIntegrity()
        self.progress.finish()
        if not ok:
            showText(ret)
        else:
            tooltip(ret)

        # if an error has directed the user to check the database,
        # silently clean up any broken reset hooks which distract from
        # the underlying issue
        while True:
            try:
                self.reset()
                break
            except Exception as e:
                print("swallowed exception in reset hook:", e)
                continue
        return ret

    def onCheckMediaDB(self):
        self.progress.start(immediate=True)
        (nohave, unused, warnings) = self.col.media.check()
        self.progress.finish()
        # generate report
        report = ""
        if warnings:
            report += "\n".join(warnings) + "\n"
        if unused:
            if report:
                report += "\n\n\n"
            report += _(
                "In media folder but not used by any cards:")
            report += "\n" + "\n".join(unused)
        if nohave:
            if report:
                report += "\n\n\n"
            report += _(
                "Used on cards but missing from media folder:")
            report += "\n" + "\n".join(nohave)
        if not report:
            tooltip(_("No unused or missing files found."))
            return
        # show report and offer to delete
        diag = QDialog(self)
        diag.setWindowTitle("Anki")
        layout = QVBoxLayout(diag)
        diag.setLayout(layout)
        text = QTextEdit()
        text.setReadOnly(True)
        text.setPlainText(report)
        layout.addWidget(text)
        box = QDialogButtonBox(QDialogButtonBox.Close)
        layout.addWidget(box)
        if unused:
            b = QPushButton(_("Delete Unused Files"))
            b.setAutoDefault(False)
            box.addButton(b, QDialogButtonBox.ActionRole)
            b.clicked.connect(
                lambda c, u=unused, d=diag: self.deleteUnused(u, d))

        box.rejected.connect(diag.reject)
        diag.setMinimumHeight(400)
        diag.setMinimumWidth(500)
        restoreGeom(diag, "checkmediadb")
        diag.exec_()
        saveGeom(diag, "checkmediadb")

    def deleteUnused(self, unused, diag):
        if not askUser(
            _("Delete unused media?")):
            return
        mdir = self.col.media.dir()
        for f in unused:
            path = os.path.join(mdir, f)
            if os.path.exists(path):
                send2trash(path)
        tooltip(_("Deleted."))
        diag.close()

    def onStudyDeck(self):
        from aqt.studydeck import StudyDeck
        ret = StudyDeck(
            self, dyn=True, current=self.col.decks.current()['name'])
        if ret.name:
            self.col.decks.select(self.col.decks.id(ret.name))
            self.moveToState("overview")

    def onEmptyCards(self):
        self.progress.start(immediate=True)
        cids = self.col.emptyCids()
        if not cids:
            self.progress.finish()
            tooltip(_("No empty cards."))
            return
        report = self.col.emptyCardReport(cids)
        self.progress.finish()
        part1 = ngettext("%d card", "%d cards", len(cids)) % len(cids)
        part1 = _("%s to delete:") % part1
        diag, box = showText(part1 + "\n\n" + report, run=False,
                geomKey="emptyCards")
        box.addButton(_("Delete Cards"), QDialogButtonBox.AcceptRole)
        box.button(QDialogButtonBox.Close).setDefault(True)
        def onDelete():
            saveGeom(diag, "emptyCards")
            QDialog.accept(diag)
            self.checkpoint(_("Delete Empty"))
            self.col.remCards(cids)
            tooltip(ngettext("%d card deleted.", "%d cards deleted.", len(cids)) % len(cids))
            self.reset()
        box.accepted.connect(onDelete)
        diag.show()

    # Debugging
    ######################################################################

    def onDebug(self):
        d = self.debugDiag = QDialog()
        d.silentlyClose = True
        frm = aqt.forms.debug.Ui_Dialog()
        frm.setupUi(d)
        font = QFontDatabase.systemFont(QFontDatabase.FixedFont)
        font.setPointSize(frm.text.font().pointSize() + 1)
        frm.text.setFont(font)
        frm.log.setFont(font)
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+return"), d)
        s.activated.connect(lambda: self.onDebugRet(frm))
        s = self.debugDiagShort = QShortcut(
            QKeySequence("ctrl+shift+return"), d)
        s.activated.connect(lambda: self.onDebugPrint(frm))
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+l"), d)
        s.activated.connect(frm.log.clear)
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+shift+l"), d)
        s.activated.connect(frm.text.clear)
        d.show()

    def _captureOutput(self, on):
        mw = self
        class Stream:
            def write(self, data):
                mw._output += data
        if on:
            self._output = ""
            self._oldStderr = sys.stderr
            self._oldStdout = sys.stdout
            s = Stream()
            sys.stderr = s
            sys.stdout = s
        else:
            sys.stderr = self._oldStderr
            sys.stdout = self._oldStdout

    def _debugCard(self):
        return self.reviewer.card.__dict__

    def _debugBrowserCard(self):
        return aqt.dialogs._dialogs['Browser'][1].card.__dict__

    def onDebugPrint(self, frm):
        cursor = frm.text.textCursor()
        position = cursor.position()
        cursor.select(QTextCursor.LineUnderCursor)
        line = cursor.selectedText()
        pfx, sfx = "pp(", ")"
        if not line.startswith(pfx):
            line = "{}{}{}".format(pfx, line, sfx)
            cursor.insertText(line)
            cursor.setPosition(position + len(pfx))
            frm.text.setTextCursor(cursor)
        self.onDebugRet(frm)

    def onDebugRet(self, frm):
        import pprint, traceback
        text = frm.text.toPlainText()
        card = self._debugCard
        bcard = self._debugBrowserCard
        mw = self
        pp = pprint.pprint
        self._captureOutput(True)
        try:
            # pylint: disable=exec-used
            exec(text)
        except:
            self._output += traceback.format_exc()
        self._captureOutput(False)
        buf = ""
        for c, line in enumerate(text.strip().split("\n")):
            if c == 0:
                buf += ">>> %s\n" % line
            else:
                buf += "... %s\n" % line
        try:
            frm.log.appendPlainText(buf + (self._output or "<no output>"))
        except UnicodeDecodeError:
            frm.log.appendPlainText(_("<non-unicode text>"))
        frm.log.ensureCursorVisible()

    # System specific code
    ##########################################################################

    def setupSystemSpecific(self):
        self.hideMenuAccels = False
        if isMac:
            # mac users expect a minimize option
            self.minimizeShortcut = QShortcut("Ctrl+M", self)
            self.minimizeShortcut.activated.connect(self.onMacMinimize)
            self.hideMenuAccels = True
            self.maybeHideAccelerators()
            self.hideStatusTips()
        elif isWin:
            # make sure ctypes is bundled
            from ctypes import windll, wintypes
            _dummy = windll
            _dummy = wintypes

    def maybeHideAccelerators(self, tgt=None):
        if not self.hideMenuAccels:
            return
        tgt = tgt or self
        for action in tgt.findChildren(QAction):
            txt = str(action.text())
            m = re.match(r"^(.+)\(&.+\)(.+)?", txt)
            if m:
                action.setText(m.group(1) + (m.group(2) or ""))

    def hideStatusTips(self):
        for action in self.findChildren(QAction):
            action.setStatusTip("")

    def onMacMinimize(self):
        self.setWindowState(self.windowState() | Qt.WindowMinimized)

    # Single instance support
    ##########################################################################

    def setupAppMsg(self):
        self.app.appMsg.connect(self.onAppMsg)

    def onAppMsg(self, buf):
        if self.state == "startup":
            # try again in a second
            return self.progress.timer(1000, lambda: self.onAppMsg(buf), False)
        elif self.state == "profileManager":
            # can't raise window while in profile manager
            if buf == "raise":
                return
            self.pendingImport = buf
            return tooltip(_("Deck will be imported when a profile is opened."))
        if not self.interactiveState() or self.progress.busy():
            # we can't raise the main window while in profile dialog, syncing, etc
            if buf != "raise":
                showInfo(_("""\
Please ensure a profile is open and Anki is not busy, then try again."""),
                     parent=None)
            return
        # raise window
        if isWin:
            # on windows we can raise the window by minimizing and restoring
            self.showMinimized()
            self.setWindowState(Qt.WindowActive)
            self.showNormal()
        else:
            # on osx we can raise the window. on unity the icon in the tray will just flash.
            self.activateWindow()
            self.raise_()
        if buf == "raise":
            return
        # import
        self.handleImport(buf)

    # GC
    ##########################################################################
    # ensure gc runs in main thread

    def setupDialogGC(self, obj):
        obj.finished.connect(lambda: self.gcWindow(obj))

    def gcWindow(self, obj):
        obj.deleteLater()
        self.progress.timer(1000, self.doGC, False, requiresCollection=False)

    def disableGC(self):
        gc.collect()
        gc.disable()

    def doGC(self):
        assert not self.progress.inDB
        gc.collect()

    # Crash log
    ##########################################################################

    def setupCrashLog(self):
        p = os.path.join(self.pm.base, "crash.log")
        self._crashLog = open(p, "ab", 0)
        faulthandler.enable(self._crashLog)

    # Media server
    ##########################################################################

    def setupMediaServer(self):
        self.mediaServer = aqt.mediasrv.MediaServer(self)
        self.mediaServer.start()

    def baseHTML(self):
        return '<base href="%s">' % self.serverURL()

    def serverURL(self):
        return "http://127.0.0.1:%d/" % self.mediaServer.getPort()
