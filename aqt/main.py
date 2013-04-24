# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, sys, re, traceback, signal
import  zipfile

from aqt.qt import *
QtConfig = pyqtconfig.Configuration()

from anki import Collection
from anki.utils import  isWin, isMac, intTime
from anki.hooks import runHook, addHook

import aqt, aqt.progress, aqt.webview, aqt.toolbar, aqt.stats
from aqt.utils import  restoreGeom, showInfo, showWarning,\
    restoreState, getOnlyText, askUser, applyStyles, showText, tooltip, \
    openHelp, openLink, checkInvalidFilename

class AnkiQt(QMainWindow):
    def __init__(self, app, profileManager, args):
        QMainWindow.__init__(self)
        self.state = "startup"
        aqt.mw = self
        self.app = app
        if isWin:
            self._xpstyle = QStyleFactory.create("WindowsXP")
            self.app.setStyle(self._xpstyle)
        self.pm = profileManager
        # running 2.0 for the first time?
        if self.pm.meta['firstRun']:
            # load the new deck user profile
            self.pm.load(self.pm.profiles()[0])
            # upgrade if necessary
            from aqt.upgrade import Upgrader
            u = Upgrader(self)
            u.maybeUpgrade()
            self.pm.meta['firstRun'] = False
            self.pm.save()
        # init rest of app
        try:
            self.setupUI()
            self.setupAddons()
        except:
            showInfo(_("Error during startup:\n%s") % traceback.format_exc())
            sys.exit(1)
        # were we given a file to import?
        if args and args[0]:
            self.onAppMsg(unicode(args[0], "utf8", "ignore"))
        # Load profile in a timer so we can let the window finish init and not
        # close on profile load error.
        self.progress.timer(10, self.setupProfile, False)

    def setupUI(self):
        self.col = None
        self.hideSchemaMsg = False
        self.setupAppMsg()
        self.setupKeys()
        self.setupThreads()
        self.setupFonts()
        self.setupMainWindow()
        self.setupSystemSpecific()
        self.setupStyle()
        self.setupMenus()
        self.setupProgress()
        self.setupErrorHandler()
        self.setupSignals()
        self.setupAutoUpdate()
        self.setupSchema()
        self.setupRefreshTimer()
        self.updateTitleBar()
        # screens
        self.setupDeckBrowser()
        self.setupOverview()
        self.setupReviewer()

    # Profiles
    ##########################################################################

    def setupProfile(self):
        self.pendingImport = None
        # profile not provided on command line?
        if not self.pm.name:
            # if there's a single profile, load it automatically
            profs = self.pm.profiles()
            if len(profs) == 1:
                try:
                    self.pm.load(profs[0])
                except:
                    # password protected
                    pass
        if not self.pm.name:
            self.showProfileManager()
        else:
            self.loadProfile()

    def showProfileManager(self):
        self.state = "profileManager"
        d = self.profileDiag = QDialog()
        f = self.profileForm = aqt.forms.profiles.Ui_Dialog()
        f.setupUi(d)
        d.connect(f.login, SIGNAL("clicked()"), self.onOpenProfile)
        d.connect(f.profiles, SIGNAL("itemDoubleClicked(QListWidgetItem*)"),
                  self.onOpenProfile)
        d.connect(f.quit, SIGNAL("clicked()"), lambda: sys.exit(0))
        d.connect(f.add, SIGNAL("clicked()"), self.onAddProfile)
        d.connect(f.rename, SIGNAL("clicked()"), self.onRenameProfile)
        d.connect(f.delete_2, SIGNAL("clicked()"), self.onRemProfile)
        d.connect(d, SIGNAL("rejected()"), lambda: d.close())
        d.connect(f.profiles, SIGNAL("currentRowChanged(int)"),
                  self.onProfileRowChange)
        self.refreshProfilesList()
        # raise first, for osx testing
        d.show()
        d.activateWindow()
        d.raise_()
        d.exec_()

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
        passwd = not self.pm.load(name)
        f.passEdit.setVisible(passwd)
        f.passLabel.setVisible(passwd)

    def openProfile(self):
        name = self.pm.profiles()[self.profileForm.profiles.currentRow()]
        passwd = self.profileForm.passEdit.text()
        return self.pm.load(name, passwd)

    def onOpenProfile(self):
        if not self.openProfile():
            showWarning(_("Invalid password."))
            return
        self.profileDiag.close()
        self.loadProfile()
        return True

    def profileNameOk(self, str):
        from anki.utils import invalidFilename, invalidFilenameChars
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
        if not self.openProfile():
            return showWarning(_("Invalid password."))
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
        # password correct?
        if not self.openProfile():
            return
        # sure?
        if not askUser(_("""\
All cards, notes, and media for this profile will be deleted. \
Are you sure?""")):
            return
        self.pm.remove(self.pm.name)
        self.refreshProfilesList()

    def loadProfile(self):
        # show main window
        if self.pm.profile['mainWindowState']:
            restoreGeom(self, "mainWindow")
            restoreState(self, "mainWindow")
        else:
            self.resize(500, 400)
        # toolbar needs to be retranslated
        self.toolbar.draw()
        # show and raise window for osx
        self.show()
        self.activateWindow()
        self.raise_()
        # maybe sync (will load DB)
        self.onSync(auto=True)
        # import pending?
        if self.pendingImport:
            if self.pm.profile['key']:
                showInfo(_("""\
To import into a password protected profile, please open the profile before attempting to import."""))
            else:
                import aqt.importing
                aqt.importing.importFile(self, self.pendingImport)
            self.pendingImport = None
        runHook("profileLoaded")

    def unloadProfile(self, browser=True):
        if not self.pm.profile:
            # already unloaded
            return
        runHook("unloadProfile")
        if not self.unloadCollection():
            return
        self.state = "profileManager"
        self.onSync(auto=True, reload=False)
        self.pm.profile['mainWindowGeom'] = self.saveGeometry()
        self.pm.profile['mainWindowState'] = self.saveState()
        self.pm.save()
        self.pm.profile = None
        self.hide()
        if browser:
            self.showProfileManager()

    # Collection load/unload
    ##########################################################################

    def loadCollection(self):
        self.hideSchemaMsg = True
        try:
            self.col = Collection(self.pm.collectionPath())
        except:
            # move back to profile manager
            showWarning("""\
Your collection is corrupt. Please see the manual for \
how to restore from a backup.""")
            return self.unloadProfile()
        self.hideSchemaMsg = False
        self.progress.setupDB(self.col.db)
        self.maybeEnableUndo()
        self.moveToState("deckBrowser")

    def unloadCollection(self):
        """
        Unload the collection.

        This unloads a collection if ther is one and returns True if
        there is no collection after the call. (Because the unload
        worked or because there was no collection to start with.)
        """
        if self.col:
            if not self.closeAllCollectionWindows():
                return
            self.maybeOptimize()
            self.col.close()
            self.col = None
            self.progress.start(immediate=True)
            self.backup()
            self.progress.finish()
        return True


    # Backup and auto-optimize
    ##########################################################################

    def backup(self):
        nbacks = self.pm.profile['numBackups']
        if not nbacks:
            return
        dir = self.pm.backupFolder()
        path = self.pm.collectionPath()
        # find existing backups
        backups = []
        for file in os.listdir(dir):
            m = re.search("backup-(\d+).apkg", file)
            if not m:
                # unknown file
                continue
            backups.append((int(m.group(1)), file))
        backups.sort()
        # get next num
        if not backups:
            n = 1
        else:
            n = backups[-1][0] + 1
        # do backup
        newpath = os.path.join(dir, "backup-%d.apkg" % n)
        z = zipfile.ZipFile(newpath, "w", zipfile.ZIP_DEFLATED)
        z.write(path, "collection.anki2")
        z.writestr("media", "{}")
        z.close()
        # remove if over
        if len(backups) + 1 > nbacks:
            delete = len(backups) + 1 - nbacks
            delete = backups[:delete]
            for file in delete:
                os.unlink(os.path.join(dir, file[1]))

    def maybeOptimize(self):
        # has two weeks passed?
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
        #print "-> move from", self.state, "to", state
        oldState = self.state or "dummy"
        cleanup = getattr(self, "_"+oldState+"Cleanup", None)
        if cleanup:
            cleanup(state)
        self.state = state
        getattr(self, "_"+state+"State")(oldState, *args)

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
        self.web.setLinkHandler(lambda url: self.delayedMaybeReset())
        i = _("Waiting for editing to finish.")
        b = self.button("refresh", _("Resume Now"), id="resume")
        self.web.stdHtml("""
<center><div style="height: 100%%">
<div style="position:relative; vertical-align: middle;">
%s<br>
%s</div></div></center>
""" % (i, b), css=self.sharedCSS)
        self.bottomWeb.hide()
        self.web.setFocus()
        self.web.eval("$('#resume').focus()")

    # HTML helpers
    ##########################################################################

    sharedCSS = """
body {
background: #f3f3f3;
margin: 2em;
}
h1 { margin-bottom: 0.2em; }
"""

    def button(self, link, name, key=None, class_="", id=""):
        class_ = "but "+ class_
        if key:
            key = _("Shortcut key: %s") % key
        else:
            key = ""
        return '''
<button id="%s" class="%s" onclick="py.link('%s');return false;"
title="%s">%s</button>''' % (
            id, class_, link, key, name)

    # Main window setup
    ##########################################################################

    def setupMainWindow(self):
        # main window
        self.form = aqt.forms.main.Ui_MainWindow()
        self.form.setupUi(self)
        # toolbar
        tweb = aqt.webview.AnkiWebView()
        tweb.setObjectName("toolbarWeb")
        tweb.setFocusPolicy(Qt.WheelFocus)
        tweb.setFixedHeight(32+self.fontHeightDelta)
        self.toolbar = aqt.toolbar.Toolbar(self, tweb)
        self.toolbar.draw()
        # main area
        self.web = aqt.webview.AnkiWebView()
        self.web.setObjectName("mainText")
        self.web.setFocusPolicy(Qt.WheelFocus)
        self.web.setMinimumWidth(400)
        # bottom area
        sweb = self.bottomWeb = aqt.webview.AnkiWebView()
        #sweb.hide()
        sweb.setFixedHeight(100)
        sweb.setObjectName("bottomWeb")
        sweb.setFocusPolicy(Qt.WheelFocus)
        # add in a layout
        self.mainLayout = QVBoxLayout()
        self.mainLayout.setContentsMargins(0,0,0,0)
        self.mainLayout.setSpacing(0)
        self.mainLayout.addWidget(tweb)
        self.mainLayout.addWidget(self.web)
        self.mainLayout.addWidget(sweb)
        self.form.centralwidget.setLayout(self.mainLayout)

    def closeAllCollectionWindows(self):
        return aqt.dialogs.closeAll()

    # Components
    ##########################################################################

    def setupSignals(self):
        signal.signal(signal.SIGINT, self.onSigInt)

    def onSigInt(self, signum, frame):
        # interrupt any current transaction and schedule a rollback & quit
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

    def onSync(self, auto=False, reload=True):
        if not auto or (self.pm.profile['syncKey'] and
                        self.pm.profile['autoSync']):
            from aqt.sync import SyncManager
            if not self.unloadCollection():
                return
            # set a sync state so the refresh timer doesn't fire while deck
            # unloaded
            self.state = "sync"
            self.syncer = SyncManager(self, self.pm)
            self.syncer.sync()
        if reload:
            if not self.col:
                self.loadCollection()

    def onFullSync(self):
        if not askUser(_("""\
If you proceed, you will need to choose between a full download or full \
upload, overwriting any changes either here or on AnkiWeb. Proceed?""")):
            return
        self.hideSchemaMsg = True
        self.col.modSchema()
        self.col.setMod()
        self.hideSchemaMsg = False
        self.onSync()

    # Tools
    ##########################################################################

    def raiseMain(self):
        if not self.app.activeWindow():
            # make sure window is shown
            self.setWindowState(self.windowState() & ~Qt.WindowMinimized)
        return True

    def setStatus(self, text, timeout=3000):
        self.form.statusbar.showMessage(text, timeout)

    def setupStyle(self):
        applyStyles(self)

    # Key handling
    ##########################################################################

    def setupKeys(self):
        self.keyHandler = None
        # debug shortcut
        self.debugShortcut = QShortcut(QKeySequence("Ctrl+:"), self)
        self.connect(
            self.debugShortcut, SIGNAL("activated()"), self.onDebug)

    def keyPressEvent(self, evt):
        # do we have a delegate?
        if self.keyHandler:
            # did it eat the key?
            if self.keyHandler(evt):
                return
        # run standard handler
        QMainWindow.keyPressEvent(self, evt)
        # check global keys
        key = unicode(evt.text())
        if key == "d":
            self.moveToState("deckBrowser")
        elif key == "s":
            if self.state == "overview":
                self.col.startTimebox()
                self.moveToState("review")
            else:
                self.moveToState("overview")
        elif key == "a":
            self.onAddCard()
        elif key == "b":
            self.onBrowse()
        elif key == "S":
            self.onStats()
        elif key == "y":
            self.onSync()

    # App exit
    ##########################################################################

    def closeEvent(self, event):
        "User hit the X button, etc."
        event.accept()
        self.onClose(force=True)

    def onClose(self, force=False):
        "Called from a shortcut key. Close current active window."
        aw = self.app.activeWindow()
        if not aw or aw == self or force:
            self.unloadProfile(browser=False)
            self.app.closeAllWindows()
        else:
            aw.close()

    # Undo & autosave
    ##########################################################################

    def onUndo(self):
        n = self.col.undoName()
        cid = self.col.undo()
        if cid and self.state == "review":
            card = self.col.getCard(cid)
            self.reviewer.cardQueue.append(card)
        else:
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
        self.col.autosave()
        self.maybeEnableUndo()

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
        if deck['dyn']:
            showWarning(_("""\
As cards are removed from a filtered deck as they are answered, viewing the \
statistics of a filtered deck will only show you reviews for cards with \
multiple steps. To get an accurate report, please empty the filtered deck \
and check the statistics for a home deck instead."""))
            return
        aqt.stats.DeckStats(self)

    def onPrefs(self):
        import aqt.preferences
        aqt.preferences.Preferences(self)

    def onNoteTypes(self):
        import aqt.models
        aqt.models.Models(self, self, fromMain=True)

    def onAbout(self):
        import aqt.about
        aqt.about.show(self)

    def onDonate(self):
        openLink(aqt.appDonate)

    def onDocumentation(self):
        openHelp("")

    # Importing & exporting
    ##########################################################################

    def onImport(self):
        import aqt.importing
        aqt.importing.onImport(self)

    def onExport(self):
        import aqt.exporting
        aqt.exporting.ExportDialog(self)

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
        else:
            self.moveToState("overview")

    # Menu, title bar & status
    ##########################################################################

    def setupMenus(self):
        m = self.form
        s = SIGNAL("triggered()")
        #self.connect(m.actionDownloadSharedPlugin, s, self.onGetSharedPlugin)
        self.connect(m.actionSwitchProfile, s, self.unloadProfile)
        self.connect(m.actionImport, s, self.onImport)
        self.connect(m.actionExport, s, self.onExport)
        self.connect(m.actionExit, s, self, SLOT("close()"))
        self.connect(m.actionPreferences, s, self.onPrefs)
        self.connect(m.actionAbout, s, self.onAbout)
        self.connect(m.actionUndo, s, self.onUndo)
        self.connect(m.actionFullDatabaseCheck, s, self.onCheckDB)
        self.connect(m.actionCheckMediaDatabase, s, self.onCheckMediaDB)
        self.connect(m.actionDocumentation, s, self.onDocumentation)
        self.connect(m.actionDonate, s, self.onDonate)
        self.connect(m.actionFullSync, s, self.onFullSync)
        self.connect(m.actionStudyDeck, s, self.onStudyDeck)
        self.connect(m.actionCreateFiltered, s, self.onCram)
        self.connect(m.actionEmptyCards, s, self.onEmptyCards)
        self.connect(m.actionNoteTypes, s, self.onNoteTypes)

    def updateTitleBar(self):
        self.setWindowTitle("Anki")

    # Auto update
    ##########################################################################

    def setupAutoUpdate(self):
        import aqt.update
        self.autoUpdate = aqt.update.LatestVersionFinder(self)
        self.connect(self.autoUpdate, SIGNAL("newVerAvail"), self.newVerAvail)
        self.connect(self.autoUpdate, SIGNAL("newMsg"), self.newMsg)
        self.connect(self.autoUpdate, SIGNAL("clockIsOff"), self.clockIsOff)
        self.autoUpdate.start()

    def newVerAvail(self, ver):
        if self.pm.meta.get('suppressUpdate', None) != ver:
            aqt.update.askAndUpdate(self, ver)

    def newMsg(self, data):
        aqt.update.showMessages(self, data)

    def clockIsOff(self):
        showWarning("""\
In order to ensure your collection works correctly when moved between \
devices, Anki requires the system clock to be set correctly. Your system \
clock appears to be wrong by more than 5 minutes.

This can be because the \
clock is slow or fast, because the date is set incorrectly, or because \
the timezone or daylight savings information is incorrect. Please correct \
the problem and restart Anki.""")
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

    # Schema modifications
    ##########################################################################

    def setupSchema(self):
        addHook("modSchema", self.onSchemaMod)

    def onSchemaMod(self, arg):
        # if triggered in sync, make sure we don't use the gui
        if not self.inMainThread():
            return True
        # if from the full sync menu, ignore
        if self.hideSchemaMsg:
            return True
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
        self.reset()
        return ret

    def onCheckMediaDB(self):
        self.progress.start(immediate=True)
        (nohave, unused) = self.col.media.check()
        self.progress.finish()
        # generate report
        report = ""
        if unused:
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
            report = _("No unused or missing files found.")
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
        b = QPushButton(_("Delete Unused"))
        b.setAutoDefault(False)
        box.addButton(b, QDialogButtonBox.ActionRole)
        b.connect(
            b, SIGNAL("clicked()"), lambda u=unused, d=diag: self.deleteUnused(u, d))
        diag.connect(box, SIGNAL("rejected()"), diag, SLOT("reject()"))
        diag.setMinimumHeight(400)
        diag.setMinimumWidth(500)
        diag.exec_()

    def deleteUnused(self, unused, diag):
        if not askUser(
            _("Delete unused media? This operation can not be undone.")):
            return
        mdir = self.col.media.dir()
        for f in unused:
            path = os.path.join(mdir, f)
            os.unlink(path)
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
        diag, box = showText(part1 + "\n\n" + report, run=False)
        box.addButton(_("Delete Cards"), QDialogButtonBox.AcceptRole)
        box.button(QDialogButtonBox.Close).setDefault(True)
        def onDelete():
            QDialog.accept(diag)
            self.checkpoint(_("Delete Empty"))
            self.col.remCards(cids)
            tooltip(ngettext("%d card deleted.", "%d cards deleted.", len(cids)) % len(cids))
            self.reset()
        diag.connect(box, SIGNAL("accepted()"), onDelete)
        diag.show()

    # Debugging
    ######################################################################

    def onDebug(self):
        d = self.debugDiag = QDialog()
        frm = aqt.forms.debug.Ui_Dialog()
        frm.setupUi(d)
        s = self.debugDiagShort = QShortcut(QKeySequence("ctrl+return"), d)
        self.connect(s, SIGNAL("activated()"),
                     lambda: self.onDebugRet(frm))
        s = self.debugDiagShort = QShortcut(
            QKeySequence("ctrl+shift+return"), d)
        self.connect(s, SIGNAL("activated()"),
                     lambda: self.onDebugPrint(frm))
        d.show()

    def _captureOutput(self, on):
        mw = self
        class Stream(object):
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
        frm.text.setPlainText("pp(%s)" % frm.text.toPlainText())
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
            exec text
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

    def setupFonts(self):
        f = QFontInfo(self.font())
        ws = QWebSettings.globalSettings()
        self.fontHeight = f.pixelSize()
        self.fontFamily = f.family()
        self.fontHeightDelta = max(0, self.fontHeight - 13)
        ws.setFontFamily(QWebSettings.StandardFont, self.fontFamily)
        ws.setFontSize(QWebSettings.DefaultFontSize, self.fontHeight)

    def setupSystemSpecific(self):
        self.hideMenuAccels = False
        if isMac:
            # mac users expect a minimize option
            self.minimizeShortcut = QShortcut("Ctrl+M", self)
            self.connect(self.minimizeShortcut, SIGNAL("activated()"),
                         self.onMacMinimize)
            self.hideMenuAccels = True
            self.maybeHideAccelerators()
            self.hideStatusTips()
        elif isWin:
            # make sure ctypes is bundled
            from ctypes import windll, wintypes

    def maybeHideAccelerators(self, tgt=None):
        if not self.hideMenuAccels:
            return
        tgt = tgt or self
        for action in tgt.findChildren(QAction):
            txt = unicode(action.text())
            m = re.match("^(.+)\(&.+\)(.+)?", txt)
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
        self.connect(self.app, SIGNAL("appMsg"), self.onAppMsg)

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
        if not isinstance(buf, unicode):
            buf = unicode(buf, "utf8", "ignore")
        if not os.path.exists(buf):
            return showInfo(_("Please use File>Import to import this file."))
        import aqt.importing
        aqt.importing.importFile(self, buf)
