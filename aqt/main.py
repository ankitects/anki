# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, sys, re, stat, traceback, signal
import shutil, time, zipfile
from operator import itemgetter

from aqt.qt import *
QtConfig = pyqtconfig.Configuration()

from anki import Collection
from anki.sound import playFromText, clearAudioQueue, stripSounds
from anki.utils import stripHTML, checksum, isWin, isMac
from anki.hooks import runHook, addHook, removeHook
import anki.consts

import aqt, aqt.progress, aqt.webview, aqt.toolbar
from aqt.utils import saveGeom, restoreGeom, showInfo, showWarning, \
    saveState, restoreState, getOnlyText, askUser, GetTextDialog, \
    askUserDialog, applyStyles, getText, showText, showCritical, getFile

## fixme: open plugin folder broken on win32?

## models remembering the previous group

class AnkiQt(QMainWindow):
    def __init__(self, app, profileManager):
        QMainWindow.__init__(self)
        aqt.mw = self
        self.app = app
        self.pm = profileManager
        # use the global language for early init; once a profile is loaded we
        # can switch to a user's preferred language
        self.setupLang(force=self.pm.meta['defaultLang'])
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
            self.setupProfile()
        except:
            showInfo("Error during startup:\n%s" % traceback.format_exc())
            sys.exit(1)

    def setupUI(self):
        self.col = None
        self.state = None
        self.setupThreads()
        self.setupMainWindow()
        self.setupStyle()
        self.setupProxy()
        self.setupMenus()
        self.setupProgress()
        self.setupErrorHandler()
        self.setupSystemSpecific()
        self.setupSignals()
        self.setupAutoUpdate()
        self.setupUpgrade()
        self.setupCardStats()
        self.setupSchema()
        self.updateTitleBar()
        # screens
        self.setupDeckBrowser()
        self.setupOverview()
        self.setupReviewer()

    # Profiles
    ##########################################################################

    def setupProfile(self):
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
        d = self.profileDiag = QDialog()
        f = self.profileForm = aqt.forms.profiles.Ui_Dialog()
        f.setupUi(d)
        d.connect(f.login, SIGNAL("clicked()"), self.onOpenProfile)
        d.connect(f.quit, SIGNAL("clicked()"), lambda: sys.exit(0))
        d.connect(f.add, SIGNAL("clicked()"), self.onAddProfile)
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
        f.profiles.addItems(self.pm.profiles())
        f.profiles.setCurrentRow(0)

    def onProfileRowChange(self, n):
        if n < 0:
            # called on .clear()
            return
        name = self.pm.profiles()[n]
        f = self.profileForm
        passwd = False
        try:
            self.pm.load(name)
        except:
            passwd = True
        f.passEdit.setShown(passwd)
        f.passLabel.setShown(passwd)

    def openProfile(self):
        name = self.pm.profiles()[self.profileForm.profiles.currentRow()]
        passwd = self.profileForm.passEdit.text()
        try:
            self.pm.load(name, passwd)
        except:
            showWarning(_("Invalid password."))
            return
        return True

    def onOpenProfile(self):
        self.openProfile()
        self.profileDiag.close()
        self.loadProfile()
        return True

    def onAddProfile(self):
        name = getOnlyText("Name:")
        if name:
            if name in self.pm.profiles():
                return showWarning("Name exists.")
            if not re.match("^[A-Za-z0-9 ]+$", name):
                return showWarning(
                    "Only numbers, letters and spaces can be used.")
            self.pm.create(name)
            self.refreshProfilesList()

    def onRemProfile(self):
        profs = self.pm.profiles()
        if len(profs) < 2:
            return showWarning("There must be at least one profile.")
        # password correct?
        if not self.openProfile():
            return
        # sure?
        if not askUser("""\
All cards, notes, and media for this profile will be deleted. \
Are you sure?"""):
            return
        self.pm.remove(self.pm.name)
        self.refreshProfilesList()

    def loadProfile(self):
        self.setupLang()
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
        # maybe sync
        self.onSync()
        # then load collection and launch into the deck browser
        print "fixme: safeguard against multiple instances"
        self.col = Collection(self.pm.collectionPath())
        self.progress.setupDB(self.col.db)
        # skip the reset step; open overview directly
        self.moveToState("review")

    def unloadProfile(self):
        self.col = None

    # State machine
    ##########################################################################

    def moveToState(self, state, *args):
        print "-> move from", self.state, "to", state
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

    def _overviewState(self, oldState):
        self.overview.show()

    def _reviewState(self, oldState):
        self.reviewer.show()

    def _reviewCleanup(self, newState):
        print "rethink cleanup code?"
        if newState != "resetRequired":
            self.reviewer.cleanup()

    def _editCurrentState(self, oldState):
        pass

    def noteChanged(self, nid):
        "Called when a card or note is edited (but not deleted)."
        runHook("noteChanged", nid)

    # Resetting state
    ##########################################################################

    def reset(self, type="all", *args):
        "Called for non-trivial edits. Rebuilds queue and updates UI."
        if self.col:
            self.col.reset()
            runHook("reset")
            self.moveToState(self.state)

    def requireReset(self, modal=False):
        "Signal queue needs to be rebuilt when edits are finished or by user."
        self.autosave()
        self.resetModal = modal
        if self.state in ("overview", "review"):
            self.moveToState("resetRequired")
        elif self.state == "editCurrent":
            # reload current card
            pass

    def maybeReset(self):
        self.autosave()
        if self.state == "resetRequired":
            self.state = self.returnState
            self.reset()

    def _resetRequiredState(self, oldState):
        if oldState != "resetRequired":
            self.returnState = oldState
        if self.resetModal:
            # we don't have to change the webview, as we have a covering window
            return
        self.web.setKeyHandler(None)
        self.web.setLinkHandler(lambda url: self.maybeReset())
        i = _("Close the browser to resume.")
        b = self.button("refresh", _("Resume Now"))
        self.web.stdHtml("""
<center><div style="height: 100%%">
<div style="position:relative; vertical-align: middle;">
%s<br>
%s</div></div></center>
""" % (i, b), css=self.sharedCSS)

    # HTML helpers
    ##########################################################################

    sharedCSS = """
body {
background: #f3f3f3;
margin: 2em;
}
a:hover { background-color: #aaa; }
h1 { margin-bottom: 0.2em; }
hr { margin:5 0 5 0; border:0; height:1px; background-color:#ccc; }
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
        tweb.setFixedHeight(32)
        self.toolbar = aqt.toolbar.Toolbar(self, tweb)
        self.toolbar.draw()
        # main area
        self.web = aqt.webview.AnkiWebView()
        self.web.setObjectName("mainText")
        self.web.setFocusPolicy(Qt.WheelFocus)
        self.web.setMinimumWidth(400)
        # add in a layout
        self.mainLayout = QVBoxLayout()
        self.mainLayout.setContentsMargins(0,0,0,0)
        self.mainLayout.setSpacing(0)
        self.mainLayout.addWidget(tweb)
        self.mainLayout.addWidget(self.web)
        self.form.centralwidget.setLayout(self.mainLayout)

    def closeAllWindows(self):
        aqt.dialogs.closeAll()

    # Components
    ##########################################################################

    def setupSignals(self):
        signal.signal(signal.SIGINT, self.onSigInt)

    def onSigInt(self, signum, frame):
        self.close()

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

    # Upgrading from previous versions
    ##########################################################################

    def setupUpgrade(self):
        addHook("1.x upgrade", self.onUpgrade)

    def onUpgrade(self, db):
        self.upgrading = True
        self.progress.setupDB(db)
        self.progress.start(label=_("Upgrading. Please be patient..."))

    # Collection loading
    ##########################################################################

    def loadDeck(self, deckPath, showErrors=True):
        "Load a deck and update the user interface."
        self.upgrading = False
        try:
            self.col = Deck(deckPath, queue=False)
        except Exception, e:
            if not showErrors:
                return 0
            # FIXME: this needs updating
            if hasattr(e, 'data') and e.data.get('type') == 'inuse':
                showWarning(_("Deck is already open."))
            else:
                showCritical(_("""\
File is corrupt or not an Anki database. Click help for more info.\n
Debug info:\n%s""") % traceback.format_exc(), help="DeckErrors")
            self.moveToState("deckBrowser")
            return 0
        finally:
            # we may have a progress window open if we were upgrading
            self.progress.finish()
        self.pm.profile.addRecentDeck(self.col.path)
        self.setupMedia(self.col)
        if not self.upgrading:
            self.progress.setupDB(self.col.db)
        self.moveToState("deckLoading")
        return True

    # Closing
    ##########################################################################

    def onClose(self):
        "Called from a shortcut key. Close current active window."
        aw = self.app.activeWindow()
        if not aw or aw == self:
            self.close()
        else:
            aw.close()

    def close(self):
        "Close and backup collection."
        if not self.col:
            return
        runHook("deckClosing")
        #self.col.close()
        self.backup()
        self.closeAllWindows()
        self.col.close()
        self.col = None

    # Syncing
    ##########################################################################

    def backup(self):
        print "backup"

    # Syncing
    ##########################################################################

    def onSync(self):
        from aqt.sync import Syncer
        # close collection if loaded
        if self.col:
            self.col.close()
        # 
        Syncer()

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
        print "applystyles"
        #applyStyles(self)

    # App exit
    ##########################################################################

    def prepareForExit(self):
        "Save config and window geometry."
        runHook("quit")
        self.pm.profile['mainWindowGeom'] = self.saveGeometry()
        self.pm.profile['mainWindowState'] = self.saveState()
        # save config
        try:
            self.pm.save()
        except (IOError, OSError), e:
            showWarning(_("Anki was unable to save your "
                                   "configuration file:\n%s" % e))

    def closeEvent(self, event):
        "User hit the X button, etc."
        self.close()
        # if self.pm.profile['syncOnProgramOpen']:
        #     self.showBrowser = False
        #     self.syncDeck(interactive=False)
        self.prepareForExit()
        event.accept()
        self.app.quit()

    # Dockable widgets
    ##########################################################################

    def addDockable(self, title, w, target=None, startDocked=True):
        target = target or self
        dock = QDockWidget(title, target)
        dock.setObjectName(title)
        dock.setAllowedAreas(Qt.LeftDockWidgetArea | Qt.RightDockWidgetArea)
        dock.setFeatures(QDockWidget.AllDockWidgetFeatures)
        dock.setWidget(w)
        if target.width() < 600:
            target.resize(QSize(600, target.height()))
        if startDocked:
            target.addDockWidget(Qt.NoDockWidgetArea, dock)
        else:
            dock.setFloating(True)
            dock.show()
        return dock

    def remDockable(self, dock, target=None):
        target = target or self
        target.removeDockWidget(dock)

    # Marking, suspending and deleting
    ##########################################################################
    # These are only available while reviewing

    def updateMarkAction(self, ):
        self.form.actionMarkCard.blockSignals(True)
        self.form.actionMarkCard.setChecked(
            self.reviewer.card.note().hasTag("marked"))
        self.form.actionMarkCard.blockSignals(False)

    def onMark(self, toggled):
        f = self.reviewer.card.note()
        if f.hasTag("marked"):
            f.delTag("marked")
        else:
            f.addTag("marked")
        f.flush()

    def onSuspend(self):
        self.checkpoint(_("Suspend"))
        self.col.sched.suspendCards([self.reviewer.card.id])
        self.reviewer.nextCard()

    def onDelete(self):
        self.checkpoint(_("Delete"))
        self.col.remCards([self.reviewer.card.id])
        self.reviewer.nextCard()

    def onBuryNote(self):
        self.checkpoint(_("Bury"))
        self.col.sched.buryNote(self.reviewer.card.nid)
        self.reviewer.nextCard()

    # Undo & autosave
    ##########################################################################

    def onUndo(self):
        self.col.undo()
        self.reset()
        self.maybeEnableUndo()

    def maybeEnableUndo(self):
        if self.col and self.col.undoName():
            self.form.actionUndo.setText(_("Undo %s") %
                                            self.col.undoName())
            self.form.actionUndo.setEnabled(True)
            runHook("undoState", True)
        else:
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
        from aqt.editcurrent import EditCurrent
        EditCurrent(self)

    def setupCardStats(self):
        import aqt.stats
        self.cardStats = aqt.stats.CardStats(self)

    def onStudyOptions(self):
        import aqt.studyopts
        aqt.studyopts.StudyOptions(self)

    def onOverview(self):
        self.col.reset()
        self.moveToState("overview")

    def onGroups(self, parent=None):
        from aqt.groups import Groups
        g = Groups(self, parent)

    def onCardStats(self):
        self.cardStats.show()

    def onStats(self):
        aqt.stats.DeckStats(self)

    def onCardLayout(self):
        from aqt.clayout import CardLayout
        CardLayout(self, self.reviewer.card.note(), ord=self.reviewer.card.ord)

    def onDeckOpts(self):
        import aqt.deckopts
        aqt.deckopts.DeckOptions(self)

    def onModels(self):
        import aqt.models
        aqt.models.Models(self)

    def onPrefs(self):
        import aqt.preferences
        aqt.preferences.Preferences(self)

    def onAbout(self):
        import aqt.about
        aqt.about.show(self)

    def onDonate(self):
        QDesktopServices.openUrl(QUrl(aqt.appDonate))

    def onDocumentation(self):
        QDesktopServices.openUrl(QUrl(aqt.appHelpSite))

    # Importing & exporting
    ##########################################################################

    def onImport(self):
        return showInfo("not yet implemented")
        if self.col is None:
            self.onNew(prompt=_("""\
Importing copies cards to the current deck,
and since you have no deck open, we need to
create a new deck first.

Please choose a new deck name:"""))
        if not self.col:
            return
        if self.col.path:
            aqt.importing.ImportDialog(self)

    def onExport(self):
        return showInfo("not yet implemented")
        aqt.exporting.ExportDialog(self)

    # Language handling
    ##########################################################################

    def setupLang(self, force=None):
        "Set the user interface language for the current profile."
        import locale, gettext
        import anki.lang
        try:
            locale.setlocale(locale.LC_ALL, '')
        except:
            pass
        lang = force if force else self.pm.profile["lang"]
        languageDir=os.path.join(aqt.moduleDir, "locale")
        self.languageTrans = gettext.translation('aqt', languageDir,
                                            languages=[lang],
                                            fallback=True)
        self.installTranslation()
        if getattr(self, 'form', None):
            self.form.retranslateUi(self)
        anki.lang.setLang(lang, local=False)
        if lang in ("he","ar","fa"):
            self.app.setLayoutDirection(Qt.RightToLeft)
        else:
            self.app.setLayoutDirection(Qt.LeftToRight)

    def getTranslation(self, text):
        return self.languageTrans.ugettext(text)

    def getTranslation2(self, text1, text2, n):
        return self.languageTrans.ungettext(text1, text2, n)

    def installTranslation(self):
        import __builtin__
        __builtin__.__dict__['_'] = self.getTranslation
        __builtin__.__dict__['ngettext'] = self.getTranslation2

    # Menu, title bar & status
    ##########################################################################

    deckRelatedMenuItems = (
        "Add",
        "Browse",
        "Undo",
        "Export",
        "Stats",
        "Cstats",
        "StudyOptions",
        "Overview",
        "Groups",
        "Models",
        )

    def setupMenus(self):
        m = self.form
        s = SIGNAL("triggered()")
        #self.connect(m.actionDownloadSharedPlugin, s, self.onGetSharedPlugin)
        self.connect(m.actionExit, s, self, SLOT("close()"))
        self.connect(m.actionPreferences, s, self.onPrefs)
        self.connect(m.actionCstats, s, self.onCardStats)
        self.connect(m.actionAbout, s, self.onAbout)
        self.connect(m.actionUndo, s, self.onUndo)
        self.connect(m.actionFullDatabaseCheck, s, self.onCheckDB)
        self.connect(m.actionCheckMediaDatabase, s, self.onCheckMediaDB)
        self.connect(m.actionDocumentation, s, self.onDocumentation)
        self.connect(m.actionDonate, s, self.onDonate)

    def enableDeckMenuItems(self, enabled=True):
        "setEnabled deck-related items."
        for item in self.deckRelatedMenuItems:
            getattr(self.form, "action" + item).setEnabled(enabled)
        self.maybeEnableUndo()
        runHook("enableDeckMenuItems", enabled)

    def disableDeckMenuItems(self):
        "Disable deck-related items."
        self.enableDeckMenuItems(enabled=False)

    def updateTitleBar(self):
        self.setWindowTitle(aqt.appName)

    # Auto update
    ##########################################################################

    def setupAutoUpdate(self):
        import aqt.update
        self.autoUpdate = aqt.update.LatestVersionFinder(self)
        self.connect(self.autoUpdate, SIGNAL("newVerAvail"), self.newVerAvail)
        self.connect(self.autoUpdate, SIGNAL("newMsg"), self.newMsg)
        self.connect(self.autoUpdate, SIGNAL("clockIsOff"), self.clockIsOff)
        self.autoUpdate.start()

    def newVerAvail(self, data):
        if self.pm.profile['suppressUpdate'] < data['latestVersion']:
            aqt.update.askAndUpdate(self, data)

    def newMsg(self, data):
        aqt.update.showMessages(self, data)

    def clockIsOff(self, diff):
        if diff < 0:
            ret = _("late")
        else:
            ret = _("early")
        showWarning(
            _("The time or date on your computer is not correct.\n") +
            ngettext("It is %(sec)d second %(type)s.\n",
                "It is %(sec)d seconds %(type)s.\n", abs(diff))
                % {"sec": abs(diff), "type": ret} +
            _(" Please ensure it is set correctly and then restart Anki.")
         )

    # Sounds
    ##########################################################################

    def onRepeatAudio(self):
        self.reviewer.replayAudio()

    # Schema modifications
    ##########################################################################

    def setupSchema(self):
        addHook("modSchema", self.onSchemaMod)

    def onSchemaMod(self, arg):
        return askUser(_("""\
This operation can't be merged when syncing, so if you have made \
changes on other devices that haven't been synced to this device yet, \
they will be lost. Are you sure you want to continue?"""))

    # Advanced features
    ##########################################################################

    def onCheckDB(self):
        "True if no problems"
        if not askUser(_("""\
This operation will find and fix some common problems.<br><br>
On the next sync, all cards will be sent to the server. \
Any changes on the server since your last sync will be lost.<br><br>
<b>This operation is not undoable.</b> Proceed?""")):
            return
        self.progress.start(immediate=True)
        ret = self.col.fixIntegrity()
        self.progress.finish()
        showText(ret)
        self.reset()
        return ret

    def onCheckMediaDB(self):
        mb = QMessageBox(self)
        mb.setWindowTitle(_("Anki"))
        mb.setIcon(QMessageBox.Warning)
        mb.setText(_("""\
This operation finds media that is missing or unused.

If you choose Scan+Delete, any media in your media folder that is not \
used by cards will be deleted. Please note that media is only \
counted as used if it appears on the question or answer of a card. If \
media is in a field that is not on your cards, the media will \
be deleted, and there is no way to undo this. Please make a backup if in \
doubt."""))
        bScan = QPushButton(_("Scan"))
        mb.addButton(bScan, QMessageBox.RejectRole)
        bDelete = QPushButton(_("Scan+Delete"))
        mb.addButton(bDelete, QMessageBox.RejectRole)
        bCancel = QPushButton(_("Cancel"))
        mb.addButton(bCancel, QMessageBox.RejectRole)
        mb.exec_()
        if mb.clickedButton() == bScan:
            delete = False
        elif mb.clickedButton() == bDelete:
            delete = True
        else:
            return
        self.progress.start(immediate=True)
        (nohave, unused) = self.col.media.check(delete)
        self.progress.finish()
        # generate report
        report = ""
        if nohave:
            report += _(
                "Used on cards but missing from media folder:")
            report += "\n" + "\n".join(nohave)
        if unused:
            if report:
                report += "\n\n"
            if delete:
                report += _("Deleted unused:")
            else:
                report += _(
                    "In media folder but not used by any cards:")
            report += "\n" + "\n".join(unused)
        if not report:
            report = _("No unused or missing files found.")
        showText(report, parent=self, type="text")

    # System specific code
    ##########################################################################

    def setupSystemSpecific(self):
        addHook("macLoadEvent", self.onMacLoad)
        if isMac:
            qt_mac_set_menubar_icons(False)
            #self.setUnifiedTitleAndToolBarOnMac(self.pm.profile['showToolbar'])
            # mac users expect a minimize option
            self.minimizeShortcut = QShortcut("Ctrl+m", self)
            self.connect(self.minimizeShortcut, SIGNAL("activated()"),
                         self.onMacMinimize)
            self.hideAccelerators()
            self.hideStatusTips()
        elif isWin:
            # make sure ctypes is bundled
            from ctypes import windll, wintypes

    def hideAccelerators(self):
        for action in self.findChildren(QAction):
            txt = unicode(action.text())
            m = re.match("^(.+)\(&.+\)(.+)?", txt)
            if m:
                action.setText(m.group(1) + (m.group(2) or ""))

    def hideStatusTips(self):
        for action in self.findChildren(QAction):
            action.setStatusTip("")

    def onMacMinimize(self):
        self.setWindowState(self.windowState() | Qt.WindowMinimized)

    def onMacLoad(self, fname):
        self.loadDeck(fname)

    # Proxy support
    ##########################################################################

    def setupProxy(self):
        print "proxy"
        return
        import urllib2
        if self.pm.profile['proxyHost']:
            proxy = "http://"
            if self.pm.profile['proxyUser']:
                proxy += (self.pm.profile['proxyUser'] + ":" +
                          self.pm.profile['proxyPass'] + "@")
            proxy += (self.pm.profile['proxyHost'] + ":" +
                      str(self.pm.profile['proxyPort']))
            os.environ["http_proxy"] = proxy
            proxy_handler = urllib2.ProxyHandler()
            opener = urllib2.build_opener(proxy_handler)
            urllib2.install_opener(opener)
