# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, sys, re, stat, traceback, signal
import shutil, time, zipfile
from operator import itemgetter

from aqt.qt import *
QtConfig = pyqtconfig.Configuration()

from anki import Deck
from anki.sound import playFromText, clearAudioQueue, stripSounds
from anki.utils import addTags, parseTags, canonifyTags, stripHTML, \
    checksum, isWin, isMac
from anki.hooks import runHook, addHook, removeHook
import anki.consts

import aqt, aqt.progress, aqt.webview
from aqt.utils import saveGeom, restoreGeom, showInfo, showWarning, \
    saveState, restoreState, getOnlyText, askUser, GetTextDialog, \
    askUserDialog, applyStyles, getText, showText, showCritical, getFile

config = aqt.config

## fixme: open plugin folder broken on win32?

## models remembering the previous group

class AnkiQt(QMainWindow):
    def __init__(self, app, config, args, splash):
        QMainWindow.__init__(self)
        aqt.mw = self
        self.splash = splash
        self.app = app
        self.config = config
        try:
            # initialize everything
            self.setup()
            splash.update()
            # load plugins
            self.setupAddons()
            splash.update()
            # show main window
            splash.finish(self)
            self.show()
            # raise window for osx
            self.activateWindow()
            self.raise_()
            # sync on program open?
            # if self.config['syncOnProgramOpen']:
            #     if self.syncDeck(interactive=False):
            #         return

            # delay load so deck errors don't cause program to close
            self.progress.timer(10, lambda a=args: \
                                self.maybeLoadLastDeck(a),
                                False)
        except:
            showInfo("Error during startup:\n%s" % traceback.format_exc())
            sys.exit(1)

    def setup(self):
        self.deck = None
        self.state = None
        self.setupThreads()
        self.setupLang()
        self.setupMainWindow()
        self.setupStyle()
        self.setupProxy()
        self.setupMenus()
        self.setupToolbar()
        self.setupProgress()
        self.setupErrorHandler()
        self.setupSystemSpecific()
        self.setupSignals()
        self.setupVersion()
        self.setupAutoUpdate()
        self.setupUpgrade()
        self.setupCardStats()
        self.setupSchema()
        self.updateTitleBar()
        # screens
        self.setupDeckBrowser()
        self.setupOverview()
        self.setupReviewer()

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
        # shouldn't call this directly; call close
        self.disableDeckMenuItems()
        self.closeAllDeckWindows()
        self.deckBrowser.show()

    def _deckLoadingState(self, oldState):
        "Run once, when deck is loaded."
        self.enableDeckMenuItems()
        # ensure cwd is set if media dir exists
        self.deck.media.dir()
        runHook("deckLoading", self.deck)
        self.moveToState("overview")

    def _overviewState(self, oldState):
        self.overview.show()

    def _reviewState(self, oldState):
        self.reviewer.show()

    def _reviewCleanup(self, newState):
        if newState != "resetRequired":
            self.reviewer.cleanup()

    def _editCurrentState(self, oldState):
        pass

    def factChanged(self, fid):
        "Called when a card or fact is edited (but not deleted)."
        runHook("factChanged", fid)

    # Resetting state
    ##########################################################################

    def reset(self, type="all", *args):
        "Called for non-trivial edits. Rebuilds queue and updates UI."
        self.deck.reset()
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
background: -webkit-gradient(linear, left top, left bottom, from(#eee), to(#bbb));
/*background: #eee;*/
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
        self.web = aqt.webview.AnkiWebView(self.form.centralwidget)
        self.web.setObjectName("mainText")
        self.web.setFocusPolicy(Qt.WheelFocus)
        self.mainLayout = QVBoxLayout()
        self.mainLayout.addWidget(self.web)
        self.mainLayout.setContentsMargins(0,0,0,0)
        self.form.centralwidget.setLayout(self.mainLayout)
        addHook("undoEnd", self.maybeEnableUndo)
        if self.config['mainWindowState']:
            restoreGeom(self, "mainWindow")
            restoreState(self, "mainWindow")
        else:
            self.resize(500, 400)

    def closeAllDeckWindows(self):
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

    def setupVersion(self):
        # check if we've been updated
        if "version" not in self.config:
            # could be new user, or upgrade from older version
            # which didn't have version variable
            self.appUpdated = "first"
        elif self.config['version'] != aqt.appVersion:
            self.appUpdated = self.config['version']
        else:
            self.appUpdated = False
        if self.appUpdated:
            self.config['version'] = aqt.appVersion

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

    # Deck loading
    ##########################################################################

    def loadDeck(self, deckPath, showErrors=True):
        "Load a deck and update the user interface."
        self.upgrading = False
        try:
            self.deck = Deck(deckPath, queue=False)
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
        self.config.addRecentDeck(self.deck.path)
        self.setupMedia(self.deck)
        if not self.upgrading:
            self.progress.setupDB(self.deck.db)
        self.moveToState("deckLoading")
        return True

    def onOpen(self):
        self.raiseMain()
        filter = _("Deck files (*.anki)")
        if self.deck:
            dir = os.path.dirname(self.deck.path)
        else:
            dir = self.config['documentDir']
        def accept(file):
            ret = self.loadDeck(file)
            if not ret:
                showWarning(_("Unable to load file."))
                self.deck = None
        getFile(self, _("Open deck"), accept, filter, dir)

    def maybeLoadLastDeck(self, args):
        "Open the last deck if possible."
        # try a command line argument if available
        if args:
            f = unicode(args[0], sys.getfilesystemencoding())
            if os.path.exists(f):
                return self.loadDeck(f)
        # try recent deck paths
        for path in self.config.recentDecks():
            r = self.loadDeck(path, showErrors=False)
            if r:
                return r
        self.moveToState("deckBrowser")

    # Open recent
    ##########################################################################

    def onSwitchToDeck(self):
        diag = QDialog(self)
        diag.setWindowTitle(_("Open Recent Deck"))
        vbox = QVBoxLayout()
        combo = QComboBox()
        self.switchDecks = (
            [(os.path.basename(x).replace(".anki", ""), x)
             for x in self.config.recentDecks()
             if not self.deck or self.deck.path != x and
             os.path.exists(x)])
        self.switchDecks.sort()
        combo.addItems([x[0] for x in self.switchDecks])
        self.connect(combo, SIGNAL("activated(int)"),
                     self.onSwitchActivated)
        vbox.addWidget(combo)
        bbox = QDialogButtonBox(QDialogButtonBox.Cancel)
        self.connect(bbox, SIGNAL("rejected()"),
                     lambda: self.switchDeckDiag.close())
        vbox.addWidget(bbox)
        diag.setLayout(vbox)
        diag.show()
        self.app.processEvents()
        combo.setFocus()
        combo.showPopup()
        self.switchDeckDiag = diag
        diag.exec_()

    def onSwitchActivated(self, idx):
        self.switchDeckDiag.close()
        self.loadDeck(self.switchDecks[idx][1])

    # New deck
    ##########################################################################

    def onNew(self, path=None, prompt=None):
        self.raiseMain()
        self.close()
        register = not path
        bad = ":/\\"
        name = _("mydeck")
        if not path:
            if not prompt:
                prompt = _("Please give your deck a name:")
            while 1:
                name = getOnlyText(
                    prompt, default=name, title=_("New Deck"))
                if not name:
                    self.moveToState("deckBrowser")
                    return
                found = False
                for c in bad:
                    if c in name:
                        showInfo(
                            _("Sorry, '%s' can't be used in deck names.") % c)
                        found = True
                        break
                if found:
                    continue
                if not name.endswith(".anki"):
                    name += ".anki"
                break
            path = os.path.join(self.config['documentDir'], name)
            if os.path.exists(path):
                if askUser(_("That deck already exists. Overwrite?"),
                                    defaultno=True):
                    os.unlink(path)
                else:
                    self.moveToState("deckBrowser")
                    return
        self.loadDeck(path)
        if register:
            self.config.addRecentDeck(self.deck.path)

    # Closing
    ##########################################################################

    def onClose(self):
        "Called from a shortcut key. Close current active window."
        aw = self.app.activeWindow()
        if not aw or aw == self:
            self.close()
        else:
            aw.close()

    def close(self, showBrowser=True):
        "Close current deck."
        if not self.deck:
            return
        # if we were cramming, restore the standard scheduler
        if self.deck.stdSched():
            self.deck.reset()
        runHook("deckClosing")
        print "focusOut() should be handled with deckClosing now"
        self.closeAllDeckWindows()
        self.deck.close()
        self.deck = None
        if showBrowser:
            self.moveToState("deckBrowser")

    # Downloading
    ##########################################################################

    def onOpenOnline(self):
        return showInfo("not yet implemented")
        self.raiseMain()
        self.ensureSyncParams()
        self.close()
        # we need a disk-backed file for syncing
        path = namedtmp(u"untitled.anki")
        self.onNew(path=path)
        # ensure all changes come to us
        self.deck.modified = 0
        self.deck.db.commit()
        self.deck.syncName = u"something"
        self.deck.lastLoaded = self.deck.modified
        if self.config['syncUsername'] and self.config['syncPassword']:
            if self.syncDeck(onlyMerge=True, reload=2, interactive=False):
                return
        self.deck = None
        self.browserLastRefreshed = 0
        self.moveToState("initial")

    def onGetSharedDeck(self):
        return showInfo("not yet implemented")
        self.raiseMain()
        aqt.getshared.GetShared(self, 0)
        self.browserLastRefreshed = 0

    def onGetSharedPlugin(self):
        return showInfo("not yet implemented")
        self.raiseMain()
        aqt.getshared.GetShared(self, 1)

    # Syncing
    ##########################################################################

    def onSync(self):
        return showInfo("not yet implemented")

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

    # Renaming
    ##########################################################################

    def onRename(self):
        "Rename deck."
        dir = os.path.dirname(self.deck.path)
        path = QFileDialog.getSaveFileName(self, _("Rename Deck"),
                                           dir,
                                           _("Deck files (*.anki)"),
                                           None,
                                           QFileDialog.DontConfirmOverwrite)
        path = unicode(path)
        if not path:
            return
        if not path.lower().endswith(".anki"):
            path += ".anki"
        if os.path.abspath(path) == os.path.abspath(self.deck.path):
            return
        if os.path.exists(path):
            if not askUser(
                "Selected file exists. Overwrite it?"):
                return
        old = self.deck.path
        self.deck.rename(path)
        self.config.addRecentDeck(path)
        self.config.delRecentDeck(old)
        return path

    # App exit
    ##########################################################################

    def prepareForExit(self):
        "Save config and window geometry."
        runHook("quit")
        self.config['mainWindowGeom'] = self.saveGeometry()
        self.config['mainWindowState'] = self.saveState()
        # save config
        try:
            self.config.save()
        except (IOError, OSError), e:
            showWarning(_("Anki was unable to save your "
                                   "configuration file:\n%s" % e))

    def closeEvent(self, event):
        "User hit the X button, etc."
        print "fixme: exit from edit current, review, etc"
        if self.state == "editCurrentFact":
            event.ignore()
            return self.moveToState("saveEdit")
        self.close(showBrowser=False)
        # if self.config['syncOnProgramOpen']:
        #     self.showBrowser = False
        #     self.syncDeck(interactive=False)
        self.prepareForExit()
        event.accept()
        self.app.quit()

    # Toolbar
    ##########################################################################

    def setupToolbar(self):
        frm = self.form
        tb = frm.toolBar
        tb.addAction(frm.actionAddcards)
        tb.addAction(frm.actionEditCurrent)
        tb.addAction(frm.actionEditLayout)
        tb.addAction(frm.actionEditdeck)
        tb.addAction(frm.actionOverview)
        tb.addAction(frm.actionGroups)
        tb.addAction(frm.actionStats)
        tb.addAction(frm.actionMarkCard)
        tb.addAction(frm.actionRepeatAudio)
        tb.setIconSize(QSize(self.config['iconSize'],
                             self.config['iconSize']))
        toggle = tb.toggleViewAction()
        toggle.setText(_("Toggle Toolbar"))
        self.connect(toggle, SIGNAL("triggered()"),
                     self.onToolbarToggle)
        if not self.config['showToolbar']:
            tb.hide()

    def onToolbarToggle(self):
        tb = self.form.toolBar
        self.config['showToolbar'] = tb.isVisible()

    # Dockable widgets
    ##########################################################################

    def addDockable(self, title, w):
        dock = QDockWidget(title, self)
        dock.setObjectName(title)
        dock.setAllowedAreas(Qt.LeftDockWidgetArea | Qt.RightDockWidgetArea)
        dock.setFeatures(QDockWidget.DockWidgetClosable)
        dock.setWidget(w)
        self.addDockWidget(Qt.RightDockWidgetArea, dock)
        return dock

    def rmDockable(self, dock):
        self.removeDockWidget(dock)

    # Marking, suspending and deleting
    ##########################################################################
    # These are only available while reviewing

    def updateMarkAction(self, ):
        self.form.actionMarkCard.blockSignals(True)
        self.form.actionMarkCard.setChecked(
            self.reviewer.card.fact().hasTag("marked"))
        self.form.actionMarkCard.blockSignals(False)

    def onMark(self, toggled):
        f = self.reviewer.card.fact()
        if f.hasTag("marked"):
            f.delTag("marked")
        else:
            f.addTag("marked")
        f.flush()

    def onSuspend(self):
        self.checkpoint(_("Suspend"))
        self.deck.sched.suspendCards([self.reviewer.card.id])
        self.reviewer.nextCard()

    def onDelete(self):
        self.checkpoint(_("Delete"))
        self.deck.delCards([self.reviewer.card.id])
        self.reviewer.nextCard()

    def onBuryFact(self):
        self.checkpoint(_("Bury"))
        self.deck.sched.buryFact(self.reviewer.card.fid)
        self.reviewer.nextCard()

    # Undo & autosave
    ##########################################################################

    def onUndo(self):
        self.deck.undo()
        self.reset()
        self.maybeEnableUndo()

    def maybeEnableUndo(self):
        if self.deck and self.deck.undoName():
            self.form.actionUndo.setText(_("Undo %s") %
                                            self.deck.undoName())
            self.form.actionUndo.setEnabled(True)
            runHook("undoState", True)
        else:
            self.form.actionUndo.setEnabled(False)
            runHook("undoState", False)

    def checkpoint(self, name):
        self.deck.save(name)
        self.maybeEnableUndo()

    def autosave(self):
        self.deck.autosave()
        self.maybeEnableUndo()

    # Other menu operations
    ##########################################################################

    def onAddCard(self):
        aqt.dialogs.open("AddCards", self)

    def onEditDeck(self):
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
        CardLayout(self, self.reviewer.card.fact(), type=1,
                   ord=self.reviewer.card.ord)

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
        if self.deck is None:
            self.onNew(prompt=_("""\
Importing copies cards to the current deck,
and since you have no deck open, we need to
create a new deck first.

Please choose a new deck name:"""))
        if not self.deck:
            return
        if self.deck.path:
            aqt.importing.ImportDialog(self)

    def onExport(self):
        return showInfo("not yet implemented")
        aqt.exporting.ExportDialog(self)

    # Language handling
    ##########################################################################

    def setupLang(self):
        "Set the user interface language."
        import locale, gettext
        import anki.lang
        try:
            locale.setlocale(locale.LC_ALL, '')
        except:
            pass
        languageDir=os.path.join(aqt.modDir, "locale")
        self.languageTrans = gettext.translation('aqt', languageDir,
                                            languages=[self.config["interfaceLang"]],
                                            fallback=True)
        self.installTranslation()
        if getattr(self, 'form', None):
            self.form.retranslateUi(self)
        anki.lang.setLang(self.config["interfaceLang"], local=False)
        if self.config['interfaceLang'] in ("he","ar","fa"):
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
        "Rename",
        "Close",
        "Addcards",
        "Editdeck",
        "DeckProperties",
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
        self.connect(m.actionNew, s, self.onNew)
        self.connect(m.actionOpenOnline, s, self.onOpenOnline)
        self.connect(m.actionDownloadSharedDeck, s, self.onGetSharedDeck)
        self.connect(m.actionDownloadSharedPlugin, s, self.onGetSharedPlugin)
        self.connect(m.actionOpenRecent, s, self.onSwitchToDeck)
        self.connect(m.actionOpen, s, self.onOpen)
        self.connect(m.actionRename, s, self.onRename)
        self.connect(m.actionClose, s, self.onClose)
        self.connect(m.actionExit, s, self, SLOT("close()"))
        self.connect(m.actionSyncdeck, s, self.onSync)
        self.connect(m.actionDeckProperties, s, self.onDeckOpts)
        self.connect(m.actionModels, s, self.onModels)
        self.connect(m.actionAddcards, s, self.onAddCard)
        self.connect(m.actionEditdeck, s, self.onEditDeck)
        self.connect(m.actionEditCurrent, s, self.onEditCurrent)
        self.connect(m.actionPreferences, s, self.onPrefs)
        self.connect(m.actionStats, s, self.onStats)
        self.connect(m.actionCstats, s, self.onCardStats)
        self.connect(m.actionEditLayout, s, self.onCardLayout)
        self.connect(m.actionAbout, s, self.onAbout)
        self.connect(m.actionImport, s, self.onImport)
        self.connect(m.actionExport, s, self.onExport)
        self.connect(m.actionMarkCard, SIGNAL("toggled(bool)"), self.onMark)
        self.connect(m.actionSuspendCard, s, self.onSuspend)
        self.connect(m.actionDelete, s, self.onDelete)
        self.connect(m.actionRepeatAudio, s, self.onRepeatAudio)
        self.connect(m.actionUndo, s, self.onUndo)
        self.connect(m.actionFullDatabaseCheck, s, self.onCheckDB)
        self.connect(m.actionCheckMediaDatabase, s, self.onCheckMediaDB)
        self.connect(m.actionDownloadMissingMedia, s, self.onDownloadMissingMedia)
        self.connect(m.actionLocalizeMedia, s, self.onLocalizeMedia)
        self.connect(m.actionStudyOptions, s, self.onStudyOptions)
        self.connect(m.actionOverview, s, self.onOverview)
        self.connect(m.actionGroups, s, self.onGroups)
        self.connect(m.actionDocumentation, s, self.onDocumentation)
        self.connect(m.actionDonate, s, self.onDonate)
        self.connect(m.actionBuryFact, s, self.onBuryFact)

    def enableDeckMenuItems(self, enabled=True):
        "setEnabled deck-related items."
        for item in self.deckRelatedMenuItems:
            getattr(self.form, "action" + item).setEnabled(enabled)
        self.form.menuAdvanced.setEnabled(enabled)
        if not enabled:
            self.disableCardMenuItems()
        self.maybeEnableUndo()
        runHook("enableDeckMenuItems", enabled)

    def disableDeckMenuItems(self):
        "Disable deck-related items."
        self.enableDeckMenuItems(enabled=False)

    def updateTitleBar(self):
        self.setWindowTitle(aqt.appName)

    def disableCardMenuItems(self):
        self.maybeEnableUndo()
        self.form.actionEditCurrent.setEnabled(False)
        self.form.actionEditLayout.setEnabled(False)
	self.form.actionMarkCard.setEnabled(False)
	self.form.actionSuspendCard.setEnabled(False)
	self.form.actionDelete.setEnabled(False)
	self.form.actionBuryFact.setEnabled(False)
        self.form.actionRepeatAudio.setEnabled(False)
        runHook("disableCardMenuItems")

    def enableCardMenuItems(self):
        self.maybeEnableUndo()
	self.form.actionEditLayout.setEnabled(True)
	self.form.actionMarkCard.setEnabled(True)
	self.form.actionSuspendCard.setEnabled(True)
	self.form.actionDelete.setEnabled(True)
	self.form.actionBuryFact.setEnabled(True)
        self.form.actionEditCurrent.setEnabled(True)
        self.form.actionEditdeck.setEnabled(True)
        self.updateMarkAction()
        runHook("enableCardMenuItems")

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
        if self.config['suppressUpdate'] < data['latestVersion']:
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
This operation can't be merged when syncing, so the next \
sync will overwrite any remote changes. Continue?"""))

    # Media locations
    ##########################################################################

    def setupMedia(self, deck):
        print "setup media"
        return
        prefix = self.config['mediaLocation']
        prev = deck.getVar("mediaLocation") or ""
        # set the media prefix
        if not prefix:
            next = ""
        elif prefix == "dropbox":
            p = self.dropboxFolder()
            next = os.path.join(p, "Public", "Anki")
        else:
            next = prefix
        # check if the media has moved
        migrateFrom = None
        if prev != next:
            # check if they were using plugin
            if not prev:
                p = self.dropboxFolder()
                p = os.path.join(p, "Public")
                deck.mediaPrefix = p
                migrateFrom = deck.mediaDir()
            if not migrateFrom:
                # find the old location
                deck.mediaPrefix = prev
                dir = deck.mediaDir()
                if dir and os.listdir(dir):
                    # it contains files; we'll need to migrate
                    migrateFrom = dir
        # setup new folder
        deck.mediaPrefix = next
        if migrateFrom:
            # force creation of new folder
            dir = deck.mediaDir(create=True)
            # migrate old files
            self.migrateMedia(migrateFrom, dir)
        else:
            # chdir if dir exists
            dir = deck.mediaDir()
        # update location
        deck.setVar("mediaLocation", next, mod=False)
        if dir and prefix == "dropbox":
            self.setupDropbox(deck)

    def migrateMedia(self, from_, to):
        if from_ == to:
            return
        files = os.listdir(from_)
        skipped = False
        for f in files:
            src = os.path.join(from_, f)
            dst = os.path.join(to, f)
            if not os.path.isfile(src):
                skipped = True
                continue
            if not os.path.exists(dst):
                shutil.copy2(src, dst)
        if not skipped:
            # everything copied, we can remove old folder
            shutil.rmtree(from_, ignore_errors=True)

    def dropboxFolder(self):
        try:
            import aqt.dropbox as db
            p = db.getPath()
        except:
            if isWin:
                s = QSettings(QSettings.UserScope, "Microsoft", "Windows")
                s.beginGroup("CurrentVersion/Explorer/Shell Folders")
                p = os.path.join(unicode(s.value("Personal").toString()),
                                 "My Dropbox")
            else:
                p = os.path.expanduser("~/Dropbox")
        return p

    def setupDropbox(self, deck):
        if not self.config['dropboxPublicFolder']:
            # put a file in the folder
            open(os.path.join(
                deck.mediaPrefix, "right-click-me.txt"), "w").write("")
            # tell user what to do
            showInfo(_("""\
A file called right-click-me.txt has been placed in DropBox's public folder. \
After clicking OK, this folder will appear. Please right click on the file (\
command+click on a Mac), choose DropBox>Copy Public Link, and paste the \
link into Anki."""))
            # open folder and text prompt
            self.onOpenPluginFolder(deck.mediaPrefix)
            txt = getText(_("Paste path here:"), parent=self)
            if txt[0]:
                fail = False
                if not txt[0].lower().startswith("http"):
                    fail = True
                if not txt[0].lower().endswith("right-click-me.txt"):
                    fail = True
                if fail:
                    showInfo(_("""\
That doesn't appear to be a public link. You'll be asked again when the deck \
is next loaded."""))
                else:
                    self.config['dropboxPublicFolder'] = os.path.dirname(txt[0])
        if self.config['dropboxPublicFolder']:
            # update media url
            deck.setVar(
                "mediaURL", self.config['dropboxPublicFolder'] + "/" +
                os.path.basename(deck.mediaDir()) + "/")

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
        ret = self.deck.fixIntegrity()
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
        (nohave, unused) = self.deck.media.check(delete)
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

    def onDownloadMissingMedia(self):
        res = downloadMissing(self.deck)
        if res is None:
            showInfo(_("No media URL defined for this deck."),
                              help="MediaSupport")
            return
        if res[0] == True:
            # success
            (grabbed, missing) = res[1:]
            msg = _("%d successfully retrieved.") % grabbed
            if missing:
                msg += "\n" + ngettext("%d missing.", "%d missing.", missing) % missing
        else:
            msg = _("Unable to download %s\nDownload aborted.") % res[1]
        showInfo(msg)

    def onLocalizeMedia(self):
        if not askUser(_("""\
This will look for remote images and sounds on your cards, download them to \
your media folder, and convert the links to local ones. \
It can take a long time. Proceed?""")):
            return
        res = downloadRemote(self.deck)
        count = len(res[0])
        msg = ngettext("%d successfully downloaded.",
            "%d successfully downloaded.", count) % count
        if len(res[1]):
            msg += "\n\n" + _("Couldn't find:") + "\n" + "\n".join(res[1])
        aqt.utils.showText(msg, parent=self, type="text")

    # System specific code
    ##########################################################################

    def setupSystemSpecific(self):
        self.setupDocumentDir()
        addHook("macLoadEvent", self.onMacLoad)
        if isMac:
            qt_mac_set_menubar_icons(False)
            self.setUnifiedTitleAndToolBarOnMac(self.config['showToolbar'])
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

    def setupDocumentDir(self):
        if self.config['documentDir']:
            return
        if isWin:
            s = QSettings(QSettings.UserScope, "Microsoft", "Windows")
            s.beginGroup("CurrentVersion/Explorer/Shell Folders")
            d = unicode(s.value("Personal").toString())
            if os.path.exists(d):
                d = os.path.join(d, "Anki")
            else:
                d = os.path.expanduser("~/.anki/decks")
        elif isMac:
            d = os.path.expanduser("~/Documents/Anki")
        else:
            d = os.path.expanduser("~/.anki/decks")
        try:
            os.mkdir(d)
        except (OSError, IOError):
            # already exists
            pass
        self.config['documentDir'] = d

    # Proxy support
    ##########################################################################

    def setupProxy(self):
        import urllib2
        if self.config['proxyHost']:
            proxy = "http://"
            if self.config['proxyUser']:
                proxy += (self.config['proxyUser'] + ":" +
                          self.config['proxyPass'] + "@")
            proxy += (self.config['proxyHost'] + ":" +
                      str(self.config['proxyPort']))
            os.environ["http_proxy"] = proxy
            proxy_handler = urllib2.ProxyHandler()
            opener = urllib2.build_opener(proxy_handler)
            urllib2.install_opener(opener)
