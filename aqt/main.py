# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, sys, re, stat, traceback, signal
import shutil, time, tempfile, zipfile
from operator import itemgetter

from PyQt4.QtCore import *
from PyQt4.QtGui import *
from PyQt4 import pyqtconfig
QtConfig = pyqtconfig.Configuration()

from anki import Deck
from anki.sound import hasSound, playFromText, clearAudioQueue, stripSounds
from anki.utils import addTags, parseTags, canonifyTags, stripHTML, checksum
from anki.hooks import runHook, addHook, removeHook
import anki.consts

import aqt, aqt.facteditor, aqt.progress, aqt.webview, aqt.stats
from aqt.utils import saveGeom, restoreGeom, showInfo, showWarning, \
    saveState, restoreState, getOnlyText, askUser, GetTextDialog, \
    askUserDialog, applyStyles, getText, showText, showCritical

config = aqt.config

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
            self.loadPlugins()
            splash.update()
            # show main window
            splash.finish(self)
            self.show()
            # raise window for osx
            self.activateWindow()
            self.raise_()
            # sync on program open?
            if self.config['syncOnProgramOpen']:
                if self.syncDeck(interactive=False):
                    return
            # load a deck?
            if (args or self.config['loadLastDeck'] or
                len(self.config['recentDeckPaths']) == 1):
                self.maybeLoadLastDeck(args)
            else:
                self.moveToState("deckBrowser")
        except:
            showInfo("Error during startup:\n%s" % traceback.format_exc())
            sys.exit(1)

    def setup(self):
        self.deck = None
        self.state = None
        self.setupLang()
        self.setupMainWindow()
        self.setupStyle()
        self.setupProxy()
        self.setupSound()
        self.setupTray()
        self.setupMenus()
        self.setupToolbar()
        self.setupProgress()
        self.setupErrorHandler()
        self.setupSystemSpecific()
        self.setupSignals()
        self.setupVersion()
        self.setupMisc()
        self.setupAutoUpdate()
        self.setupCardStats()
        # screens
        self.setupDeckBrowser()
        self.setupOverview()
        self.setupReviewer()
        self.setupEditor()
        self.setupStudyScreen()

    # State machine
    ##########################################################################

    def moveToState(self, state, *args):
        print "-> move from", self.state, "to", state
        self.state = state
        getattr(self, "_"+state+"State")(self.state, *args)

    def _deckBrowserState(self, oldState):
        # shouldn't call this directly; call close
        self.disableDeckMenuItems()
        self.closeAllDeckWindows()
        self.deckBrowser.show()
        self.updateTitleBar()

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

    def _editCurrentFactState(self, oldState):
        if self.lastState == "editCurrentFact":
            return self.moveToState("saveEdit")
        self.form.actionRepeatAudio.setEnabled(False)
        self.deck.db.flush()
        self.showEditor()

    def _saveEditState(self, oldState):
        self.form.actionRepeatAudio.setEnabled(True)
        self.editor.saveFieldsNow()
        self.form.buttonStack.show()
        return self.reset()

    def _studyScreenState(self, oldState):
        self.currentCard = None
        # if self.deck.finishScheduler:
        #     self.deck.finishScheduler()
        self.disableCardMenuItems()
        self.showStudyScreen()

    def reset(self):
        self.deck.reset()
        runHook("reset")

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

    # Signal handling
    ##########################################################################

    def setupSignals(self):
        signal.signal(signal.SIGINT, self.onSigInt)

    def onSigInt(self, signum, frame):
        self.close()

    # Progress handling
    ##########################################################################

    def setupProgress(self):
        self.progress = aqt.progress.ProgressManager(self)

    # Error handling
    ##########################################################################

    def setupErrorHandler(self):
        class ErrorPipe(object):
            def __init__(self, parent):
                self.parent = parent
                self.timer = None
                self.pool = ""
                self.poolUpdated = 0

            def write(self, data):
                try:
                    print data.encode("utf-8"),
                except:
                    print data
                self.pool += data
                self.poolUpdated = time.time()

            def haveError(self):
                if self.pool:
                    if (time.time() - self.poolUpdated) > 1:
                        return True

            def getError(self):
                p = self.pool
                self.pool = ""
                try:
                    return unicode(p, 'utf8', 'replace')
                except TypeError:
                    # already unicode
                    return p

        self.errorPipe = ErrorPipe(self)
        sys.stderr = self.errorPipe
        self.errorTimer = QTimer(self)
        self.errorTimer.start(1000)
        self.connect(self.errorTimer,
                            SIGNAL("timeout()"),
                            self.onErrorTimer)

    def onErrorTimer(self):
        if self.errorPipe.haveError():
            error = self.errorPipe.getError()
            if "font_manager.py" in error:
                # hack for matplotlib errors on osx
                return
            if "Audio player not found" in error:
                showInfo(_("Couldn't play sound. Please install mplayer."))
                return
            if "ERR-0100" in error:
                showInfo(error)
                return
            if "ERR-0101" in error:
                showInfo(error)
                return
            stdText = _("""\

An error occurred. It may have been caused by a harmless bug, <br>
or your deck may have a problem.
<p>To confirm it's not a problem with your deck, please <b>restart<br>
Anki</b> and run <b>Tools > Advanced > Check Database</b>.

<p>If that doesn't fix the problem, please copy the following<br>
into a bug report:<br>
""")
            pluginText = _("""\
An error occurred in a plugin. Please contact the plugin author.<br>
Please do not file a bug report with Anki.<br>""")
            if "plugin" in error:
                txt = pluginText
            else:
                txt = stdText
            # show dialog
            diag = QDialog(self.app.activeWindow())
            diag.setWindowTitle("Anki")
            layout = QVBoxLayout(diag)
            diag.setLayout(layout)
            text = QTextEdit()
            text.setReadOnly(True)
            text.setHtml(txt + "<div style='white-space: pre-wrap'>" + error + "</div>")
            layout.addWidget(text)
            box = QDialogButtonBox(QDialogButtonBox.Close)
            layout.addWidget(box)
            self.connect(box, SIGNAL("rejected()"), diag, SLOT("reject()"))
            diag.setMinimumHeight(400)
            diag.setMinimumWidth(500)
            diag.exec_()
            self.progress.clear()

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
        #self.help = aqt.help.HelpArea(self.form.helpFrame, self.config, self)
        #self.connect(self.web.pageAction(QWebPage.Reload),
        #             SIGNAL("triggered()"),
        #             self.onReload)
        # congrats
        # self.connect(self.mainWin.learnMoreButton,
        #              SIGNAL("clicked()"),
        #              self.onLearnMore)
        # self.connect(self.mainWin.reviewEarlyButton,
        #              SIGNAL("clicked()"),
        #              self.onReviewEarly)
        # self.connect(self.mainWin.finishButton,
        #              SIGNAL("clicked()"),
        #              self.onClose)
        # notices
        #self.form.noticeFrame.setShown(False)
        # self.connect(self.form.noticeButton, SIGNAL("clicked()"),
        #              lambda: self.form.noticeFrame.setShown(False))
        # if sys.platform.startswith("win32"):
        #     self.form.noticeButton.setFixedWidth(24)
        # elif sys.platform.startswith("darwin"):
        #     self.form.noticeButton.setFixedWidth(20)
        #     self.form.noticeButton.setFixedHeight(20)
        addHook("cardAnswered", self.onCardAnsweredHook)
        addHook("undoEnd", self.maybeEnableUndo)
        addHook("notify", self.onNotify)
        if self.config['mainWindowState']:
            restoreGeom(self, "mainWindow", 21)
            restoreState(self, "mainWindow")
        else:
            self.resize(500, 500)

    def closeAllDeckWindows(self):
        print "closealldeckwindows()"
        #aqt.dialogs.closeAll()

    # to port
        # elif self.state == "studyScreen":
        #     if evt.key() in (Qt.Key_Enter,
        #                      Qt.Key_Return):
        #         evt.accept()
        #         return self.onStartReview()
        # elif self.state == "editCurrentFact":
        #     if evt.key() == Qt.Key_Escape:
        #         evt.accept()
        #         return self.moveToState("saveEdit")
        # evt.ignore()

    def onCardAnsweredHook(self, cardId, isLeech):
        if not isLeech:
            self.setNotice()
            return
        txt = (_("""\
<b>%s</b>... is a <a href="http://ichi2.net/anki/wiki/Leeches">leech</a>.""")
               % stripHTML(stripSounds(self.currentCard.question)).\
               replace("\n", " ")[0:30])
        if isLeech and self.deck.db.scalar(
            "select 1 from cards where id = :id and type < 0", id=cardId):
            txt += _(" It has been suspended.")
        self.setNotice(txt)

    def startRefreshTimer(self):
        "Update the screen once a minute until next card is displayed."
        if getattr(self, 'refreshTimer', None):
            return
        self.refreshTimer = QTimer(self)
        self.refreshTimer.start(60000)
        self.connect(self.refreshTimer, SIGNAL("timeout()"), self.refreshStatus)
        # start another time to refresh exactly after we've finished
        next = self.deck.earliestTime()
        if next:
            delay = next - time.time()
            if delay > 86400:
                return
            if delay < 0:
                c = self.deck.getCard()
                if c:
                    return self.moveToState("auto")
                sys.stderr.write("""\
earliest time returned %f

please report this error, but it's not serious.
closing and opening your deck should fix it.

counts are %d %d %d
""" % (delay,
         self.deck.failedSoonCount,
         self.deck.revCount,
         self.deck.newCount))
                return
            t = QTimer(self)
            t.setSingleShot(True)
            self.connect(t, SIGNAL("timeout()"), self.refreshStatus)
            t.start((delay+1)*1000)

    def refreshStatus(self):
        "If triggered when the deck is finished, reset state."
        if self.inDbHandler:
            return
        if self.state == "deckFinished":
            # don't try refresh if the deck is closed during a sync
            if self.deck:
                self.moveToState("getQuestion")
        if self.state != "deckFinished":
            if self.refreshTimer:
                self.refreshTimer.stop()
                self.refreshTimer = None

    # Main stack
    ##########################################################################

    # def switchToEditScreen(self):
    #     self.form.mainStack.setCurrentIndex(0)

    # def switchToMainScreen(self):
    #     self.form.mainStack.setCurrentIndex(1)



    # misc
    ##########################################################################

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

    def onReload(self):
        self.moveToState("auto")

    def onNotify(self, msg):
        if self.mainThread != QThread.currentThread():
            # decks may be opened in a sync thread
            sys.stderr.write(msg + "\n")
        else:
            showInfo(msg)

    def setNotice(self, str=""):
        if str:
            self.form.noticeLabel.setText(str)
            self.form.noticeFrame.setShown(True)
        else:
            self.form.noticeFrame.setShown(False)

    def setupTray(self):
        import aqt.tray
	self.trayIcon = aqt.tray.AnkiTrayIcon(self)

    # Deck loading & saving: backend
    ##########################################################################

    def loadDeck(self, deckPath, showErrors=True):
        "Load a deck and update the user interface. Maybe sync."
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
        self.config.addRecentDeck(self.deck.path)
        self.setupMedia(self.deck)
        self.deck.initUndo()
        self.progress.setupDB()
        self.moveToState("deckLoading")
        return True

    def maybeLoadLastDeck(self, args):
        "Open the last deck if possible."
        # try a command line argument if available
        if args:
            f = unicode(args[0], sys.getfilesystemencoding())
            return self.loadDeck(f)
        # try recent deck paths
        for path in self.config['recentDeckPaths']:
            r = self.loadDeck(path, showErrors=False)
            if r:
                return r

    def getDefaultDir(self, save=False):
        "Try and get default dir from most recently opened file."
        defaultDir = ""
        if self.config['recentDeckPaths']:
            latest = self.config['recentDeckPaths'][0]
            defaultDir = os.path.dirname(latest)
        else:
            defaultDir = unicode(os.path.expanduser("~/"),
                                 sys.getfilesystemencoding())
        return defaultDir

    def updateRecentFiles(self, path):
        "Add the current deck to the list of recent files."
        path = os.path.normpath(path)
        if path in self.config['recentDeckPaths']:
            self.config['recentDeckPaths'].remove(path)
        self.config['recentDeckPaths'].insert(0, path)
        self.config.save()

    def onSwitchToDeck(self):
        diag = QDialog(self)
        diag.setWindowTitle(_("Open Recent Deck"))
        vbox = QVBoxLayout()
        combo = QComboBox()
        self.switchDecks = (
            [(os.path.basename(x).replace(".anki", ""), x)
             for x in self.config['recentDeckPaths']
             if not self.deck or self.mw.path != x and
             os.path.exists(x)])
        self.switchDecks.sort()
        combo.addItems(QStringList([x[0] for x in self.switchDecks]))
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

    # New files, loading & saving
    ##########################################################################

    def onClose(self):
        aw = self.app.activeWindow()
        if not aw or aw == self:
            self.close()
        else:
            aw.close()

    def close(self, showBrowser=True):
        "(Auto)save and close. Prompt if necessary. True if okay to proceed."
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

    def raiseMain(self):
        if not self.app.activeWindow():
            # make sure window is shown
            self.setWindowState(self.windowState() & ~Qt.WindowMinimized)
        return True

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
            path = os.path.join(self.documentDir, name)
            if os.path.exists(path):
                if askUser(_("That deck already exists. Overwrite?"),
                                    defaultno=True):
                    os.unlink(path)
                else:
                    self.moveToState("deckBrowser")
                    return
        self.loadDeck(path)
        if register:
            self.updateRecentFiles(self.deck.path)

    def ensureSyncParams(self):
        if not self.config['syncUsername'] or not self.config['syncPassword']:
            d = QDialog(self)
            vbox = QVBoxLayout()
            l = QLabel(_(
                '<h1>Online Account</h1>'
                'To use your free <a href="http://anki.ichi2.net/">online account</a>,<br>'
                "please enter your details below.<br><br>"
                "You can change your details later with<br>"
                "Settings->Preferences->Sync<br>"))
            l.setOpenExternalLinks(True)
            vbox.addWidget(l)
            g = QGridLayout()
            l1 = QLabel(_("Username:"))
            g.addWidget(l1, 0, 0)
            user = QLineEdit()
            g.addWidget(user, 0, 1)
            l2 = QLabel(_("Password:"))
            g.addWidget(l2, 1, 0)
            passwd = QLineEdit()
            passwd.setEchoMode(QLineEdit.Password)
            g.addWidget(passwd, 1, 1)
            vbox.addLayout(g)
            bb = QDialogButtonBox(QDialogButtonBox.Ok|QDialogButtonBox.Cancel)
            self.connect(bb, SIGNAL("accepted()"), d.accept)
            self.connect(bb, SIGNAL("rejected()"), d.reject)
            vbox.addWidget(bb)
            d.setLayout(vbox)
            d.exec_()
            self.config['syncUsername'] = unicode(user.text())
            self.config['syncPassword'] = unicode(passwd.text())

    def onOpenOnline(self):
        self.raiseMain()
        self.ensureSyncParams()
        self.close()
        # we need a disk-backed file for syncing
        dir = unicode(tempfile.mkdtemp(prefix="anki"), sys.getfilesystemencoding())
        path = os.path.join(dir, u"untitled.anki")
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
        self.raiseMain()
        aqt.getshared.GetShared(self, 0)
        self.browserLastRefreshed = 0

    def onGetSharedPlugin(self):
        self.raiseMain()
        aqt.getshared.GetShared(self, 1)

    def onOpen(self):
        self.raiseMain()
        key = _("Deck files (*.anki)")
        defaultDir = self.getDefaultDir()
        file = QFileDialog.getOpenFileName(self, _("Open deck"),
                                           defaultDir, key)
        file = unicode(file)
        if not file:
            return False
        ret = self.loadDeck(file)
        if not ret:
            if ret is None:
                showWarning(_("Unable to load file."))
            self.deck = None
            return False
        else:
            self.updateRecentFiles(file)
            self.browserLastRefreshed = 0
            return True

    def showToolTip(self, msg):
        class CustomLabel(QLabel):
            def mousePressEvent(self, evt):
                evt.accept()
                self.hide()
        old = getattr(self, 'toolTipFrame', None)
        if old:
            old.deleteLater()
        old = getattr(self, 'toolTipTimer', None)
        if old:
            old.stop()
            old.deleteLater()
        self.toolTipLabel = CustomLabel("""\
<table cellpadding=10>
<tr>
<td><img src=":/icons/help-hint.png"></td>
<td>%s</td>
</tr>
</table>""" % msg)
        self.toolTipLabel.setFrameStyle(QFrame.Panel)
        self.toolTipLabel.setLineWidth(2)
        self.toolTipLabel.setWindowFlags(Qt.ToolTip)
        p = QPalette()
        p.setColor(QPalette.Window, QColor("#feffc4"))
        self.toolTipLabel.setPalette(p)
        aw = (self.app.instance().activeWindow() or
              self)
        self.toolTipLabel.move(
            aw.mapToGlobal(QPoint(0, -100 + aw.height())))
        self.toolTipLabel.show()
        self.toolTipTimer = QTimer(self)
        self.toolTipTimer.setSingleShot(True)
        self.toolTipTimer.start(5000)
        self.connect(self.toolTipTimer, SIGNAL("timeout()"),
                     self.closeToolTip)

    def closeToolTip(self):
        label = getattr(self, 'toolTipLabel', None)
        if label:
            label.deleteLater()
            self.toolTipLabel = None
        timer = getattr(self, 'toolTipTimer', None)
        if timer:
            timer.stop()
            timer.deleteLater()
            self.toolTipTimer = None

    def save(self, required=False):
        if not self.deck.modifiedSinceSave() and self.deck.path:
            return True
        if not self.deck.path:
            if required:
                # backed in memory, make sure it's saved
                return self.onSaveAs()
            else:
                self.showToolTip(_("""\
<h1>Unsaved Deck</h1>
Careful. You're editing an unsaved deck.<br>
Choose File -> Save to start autosaving<br>
your deck."""))
            return
        self.deck.save()
        self.updateTitleBar()
        return True

    def onRename(self):
        "Prompt for a file name, then save."
        return
        title = _("Save Deck As")
        if self.deck.path:
            dir = os.path.dirname(self.deck.path)
        else:
            dir = self.documentDir
        file = QFileDialog.getSaveFileName(self, title,
                                           dir,
                                           _("Deck files (*.anki)"),
                                           None,
                                           QFileDialog.DontConfirmOverwrite)
        file = unicode(file)
        if not file:
            return
        if not file.lower().endswith(".anki"):
            file += ".anki"
        if self.deck.path:
            if os.path.abspath(file) == os.path.abspath(self.deck.path):
                return self.onSave()
        if os.path.exists(file):
            # check for existence after extension
            if not askUser(
                "This file exists. Are you sure you want to overwrite it?"):
                return
        self.closeAllDeckWindows()
        self.deck = self.deck.saveAs(file)
        self.deck.initUndo()
        self.updateTitleBar()
        self.updateRecentFiles(self.deck.path)
        self.browserLastRefreshed = 0
        self.moveToState("initial")
        return file

    # Components
    ##########################################################################

    def setupDeckBrowser(self):
        from aqt.deckbrowser import DeckBrowser
        self.deckBrowser = DeckBrowser(self)

    def setupOverview(self):
        from aqt.overview import Overview
        self.overview = Overview(self)

    def setupReviewer(self):
        from aqt.reviewer import Reviewer
        self.reviewer = Reviewer(self)

    # Opening and closing the app
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
        if self.state == "editCurrentFact":
            event.ignore()
            return self.moveToState("saveEdit")
        self.close(showBrowser=False)
        if self.config['syncOnProgramOpen']:
            self.showBrowser = False
            self.syncDeck(interactive=False)
        self.prepareForExit()
        event.accept()
        self.app.quit()

    # Edit current fact
    ##########################################################################

    def setupEditor(self):
        print "setupeditor"
        return
        self.editor = aqt.facteditor.FactEditor(
            self, self.form.fieldsArea, self.deck)
        self.editor.clayout.setShortcut("")
        self.editor.onFactValid = self.onFactValid
        self.editor.onFactInvalid = self.onFactInvalid
        self.editor.resetOnEdit = False
        # editor
        self.connect(self.form.saveEditorButton, SIGNAL("clicked()"),
                     lambda: self.moveToState("saveEdit"))


    def showEditor(self):
        self.form.buttonStack.hide()
        self.switchToEditScreen()
        self.editor.setFact(self.currentCard.fact)
        self.editor.card = self.currentCard

    def onFactValid(self, fact):
        self.form.saveEditorButton.setEnabled(True)

    def onFactInvalid(self, fact):
        self.form.saveEditorButton.setEnabled(False)

    # Study screen
    ##########################################################################

    def setupStudyScreen(self):
        return
        self.form.buttonStack.hide()
        self.form.newCardOrder.insertItems(
            0, QStringList(anki.consts.newCardOrderLabels().values()))
        self.form.newCardScheduling.insertItems(
            0, QStringList(anki.consts.newCardSchedulingLabels().values()))
        self.form.revCardOrder.insertItems(
            0, QStringList(anki.consts.revCardOrderLabels().values()))
        self.connect(self.form.optionsHelpButton,
                     SIGNAL("clicked()"),
                     lambda: QDesktopServices.openUrl(QUrl(
            aqt.appWiki + "StudyOptions")))
        self.connect(self.form.minuteLimit,
                     SIGNAL("textChanged(QString)"), self.onMinuteLimitChanged)
        self.connect(self.form.questionLimit,
                     SIGNAL("textChanged(QString)"), self.onQuestionLimitChanged)
        self.connect(self.form.newPerDay,
                     SIGNAL("textChanged(QString)"), self.onNewLimitChanged)
        self.connect(self.form.startReviewingButton,
                     SIGNAL("clicked()"),
                     self.onStartReview)
        self.connect(self.form.newCardOrder,
                     SIGNAL("activated(int)"), self.onNewCardOrderChanged)
        self.connect(self.form.failedCardMax,
                     SIGNAL("editingFinished()"),
                     self.onFailedMaxChanged)
        self.connect(self.form.newCategories,
                     SIGNAL("clicked()"), self.onNewCategoriesClicked)
        self.connect(self.form.revCategories,
                     SIGNAL("clicked()"), self.onRevCategoriesClicked)
        self.form.tabWidget.setCurrentIndex(self.config['studyOptionsTab'])

    def onNewCategoriesClicked(self):
        aqt.activetags.show(self, "new")

    def onRevCategoriesClicked(self):
        aqt.activetags.show(self, "rev")

    def onFailedMaxChanged(self):
        try:
            v = int(self.form.failedCardMax.text())
            if v == 1 or v < 0:
                v = 2
            self.deck.failedCardMax = v
        except ValueError:
            pass
        self.form.failedCardMax.setText(str(self.deck.failedCardMax))
        self.deck.flushMod()

    def onMinuteLimitChanged(self, qstr):
        try:
            val = float(self.form.minuteLimit.text()) * 60
            if self.deck.sessionTimeLimit == val:
                return
            self.deck.sessionTimeLimit = val
        except ValueError:
            pass
        self.deck.flushMod()
        self.updateStudyStats()

    def onQuestionLimitChanged(self, qstr):
        try:
            val = int(self.form.questionLimit.text())
            if self.deck.sessionRepLimit == val:
                return
            self.deck.sessionRepLimit = val
        except ValueError:
            pass
        self.deck.flushMod()
        self.updateStudyStats()

    def onNewLimitChanged(self, qstr):
        try:
            val = int(self.form.newPerDay.text())
            if self.deck.newCardsPerDay == val:
                return
            self.deck.newCardsPerDay = val
        except ValueError:
            pass
        self.deck.flushMod()
        self.deck.reset()
        self.statusView.redraw()
        self.updateStudyStats()

    def onNewCardOrderChanged(self, ncOrd):
        def uf(obj, field, value):
            if getattr(obj, field) != value:
                setattr(obj, field, value)
                self.deck.flushMod()
        if ncOrd != 0:
            if self.deck.newCardOrder == 0:
                # need to put back in order
                self.mw.startProgress()
                self.mw.updateProgress(_("Ordering..."))
                self.deck.orderNewCards()
                self.deck.finishProgress()
            uf(self.deck, 'newCardOrder', ncOrd)
        elif ncOrd == 0:
            # (re-)randomize
            self.deck.startProgress()
            self.deck.updateProgress(_("Randomizing..."))
            self.deck.randomizeNewCards()
            self.deck.finishProgress()
            uf(self.deck, 'newCardOrder', ncOrd)

    def updateActives(self):
        labels = [
            _("Show All Due Cards"),
            _("Show Chosen Categories")
            ]
        if self.deck.getVar("newActive") or self.deck.getVar("newInactive"):
            new = labels[1]
        else:
            new = labels[0]
        self.form.newCategoryLabel.setText(new)
        if self.deck.getVar("revActive") or self.deck.getVar("revInactive"):
            rev = labels[1]
        else:
            rev = labels[0]
        self.form.revCategoryLabel.setText(rev)

    def updateStudyStats(self):
        self.form.buttonStack.hide()
        self.deck.reset()
        self.updateActives()
        wasReached = self.deck.timeboxReached()
        sessionColour = '<font color=#0000ff>%s</font>'
        cardColour = '<font color=#0000ff>%s</font>'
        # top label
        h = {}
        h['ret'] = cardColour % (self.deck.revCount+self.deck.failedSoonCount)
        h['new'] = cardColour % self.deck.newCount
        h['newof'] = str(self.deck.newCountAll())
        # counts & time for today
        todayStart = self.deck.failedCutoff - 86400
        sql = "select count(), sum(userTime) from revlog"
        (reps, time_) = self.deck.db.first(
            sql + " where time > :start", start=todayStart)
        h['timeToday'] = sessionColour % (
            anki.utils.fmtTimeSpan(time_ or 0, short=True, point=1))
        h['repsToday'] = sessionColour % reps
        # and yesterday
        yestStart = todayStart - 86400
        (reps, time_) = self.deck.db.first(
            sql + " where time > :start and time <= :end",
            start=yestStart, end=todayStart)
        h['timeTodayChg'] = str(
            anki.utils.fmtTimeSpan(time_ or 0, short=True, point=1))
        h['repsTodayChg'] = str(reps)
        # session counts
        limit = self.deck.sessionTimeLimit
        start = self.deck.sessionStartTime or time.time() - limit
        start2 = self.deck.lastSessionStart or start - limit
        last10 = self.deck.db.scalar(
            "select count(*) from revlog where time >= :t",
            t=start)
        last20 = self.deck.db.scalar(
            "select count(*) from revlog where "
            "time >= :t and time < :t2",
            t=start2, t2=start)
        h['repsInSes'] = sessionColour % last10
        h['repsInSesChg'] = str(last20)
        h['cs_header'] = "<b>" + _("Cards/session:") + "</b>"
        h['cd_header'] = "<b>" + _("Cards/day:") + "</b>"
        h['td_header'] = "<b>" + _("Time/day:") + "</b>"
        h['rd_header'] = "<b>" + _("Reviews due:") + "</b>"
        h['ntod_header'] = "<b>" + _("New today:") + "</b>"
        h['ntot_header'] = "<b>" + _("New total:") + "</b>"
        stats1 = ("""\
<table>
<tr><td width=150>%(cs_header)s</td><td width=50><b>%(repsInSesChg)s</b></td>
<td><b>%(repsInSes)s</b></td></tr></table>
<hr>
<table>
<tr><td width=150>
%(cd_header)s</td><td width=50><b>%(repsTodayChg)s</b></td>
<td><b>%(repsToday)s</b></td></tr>
<tr><td>%(td_header)s</td><td><b>%(timeTodayChg)s</b></td>
<td><b>%(timeToday)s</b></td></tr>
</table>""") % h

        stats2 = ("""\
<table>
<tr><td width=180>%(rd_header)s</td><td align=right><b>%(ret)s</b></td></tr>
<tr><td>%(ntod_header)s</td><td align=right><b>%(new)s</b></td></tr>
<tr><td>%(ntot_header)s</td><td align=right>%(newof)s</td></tr>
</table>""") % h
        self.form.optionsLabel.setText("""\
<p><table><tr>
%s
</tr><tr>
<td><hr>%s<hr></td></tr></table>""" % (stats1, stats2))
        h['tt_header'] = _("Session Statistics")
        h['cs_tip'] = _("The number of cards you studied in the current \
session (blue) and previous session (black)")
        h['cd_tip'] = _("The number of cards you studied today (blue) and \
yesterday (black)")
        h['td_tip'] = _("The number of minutes you studied today (blue) and \
yesterday (black)")
        h['rd_tip'] = _("The number of cards that are waiting to be reviewed \
today")
        h['ntod_tip'] = _("The number of new cards that are waiting to be \
learnt today")
        h['ntot_tip'] = _("The total number of new cards in the deck")
        statToolTip = ("""<h1>%(tt_header)s</h1>
<dl><dt><b>%(cs_header)s</b></dt><dd>%(cs_tip)s</dd></dl>
<dl><dt><b>%(cd_header)s</b></dt><dd>%(cd_tip)s</dd></dl>
<dl><dt><b>%(td_header)s</b></dt><dd>%(td_tip)s</dd></dl>
<dl><dt><b>%(rd_header)s</b></dt><dd>%(rd_tip)s</dd></dl>
<dl><dt><b>%(ntod_header)s</b></dt><dd>%(ntod_tip)s</dd></dl>
<dl><dt><b>%(ntot_header)s</b></dt><dd>%(ntot_tip)s<</dd></dl>""") % h

        self.form.optionsLabel.setToolTip(statToolTip)

    def showStudyScreen(self):
        # forget last card
        self.lastCard = None
        self.switchToStudyScreen()
        self.updateStudyStats()
        self.form.startReviewingButton.setFocus()
        self.setupStudyOptions()
        self.form.studyOptionsFrame.setMaximumWidth(500)
        self.form.studyOptionsFrame.show()

    def setupStudyOptions(self):
        self.form.newPerDay.setText(str(self.deck.newCardsPerDay))
        lim = self.deck.sessionTimeLimit/60
        if int(lim) == lim:
            lim = int(lim)
        self.form.minuteLimit.setText(str(lim))
        self.form.questionLimit.setText(str(self.deck.sessionRepLimit))
        self.form.newCardOrder.setCurrentIndex(self.deck.newCardOrder)
        self.form.newCardScheduling.setCurrentIndex(self.deck.newCardSpacing)
        self.form.revCardOrder.setCurrentIndex(self.deck.revCardOrder)
        self.form.failedCardsOption.clear()
        if self.deck.getFailedCardPolicy() == 5:
            labels = failedCardOptionLabels().values()
        else:
            labels = failedCardOptionLabels().values()[0:-1]
        self.form.failedCardsOption.insertItems(0, labels)
        self.form.failedCardsOption.setCurrentIndex(self.deck.getFailedCardPolicy())
        self.form.failedCardMax.setText(unicode(self.deck.failedCardMax))

    def onStartReview(self):
        def uf(obj, field, value):
            if getattr(obj, field) != value:
                setattr(obj, field, value)
                self.deck.flushMod()
        self.form.studyOptionsFrame.hide()
        # make sure the size is updated before button stack shown
        self.app.processEvents()
        uf(self.deck, 'newCardSpacing',
           self.form.newCardScheduling.currentIndex())
        uf(self.deck, 'revCardOrder',
           self.form.revCardOrder.currentIndex())
        pol = self.deck.getFailedCardPolicy()
        if (pol != 5 and pol !=
            self.form.failedCardsOption.currentIndex()):
            self.deck.setFailedCardPolicy(
                self.form.failedCardsOption.currentIndex())
            self.deck.flushMod()
        self.deck.reset()
        if not self.deck.finishScheduler:
            self.deck.startTimebox()
        self.config['studyOptionsTab'] = self.form.tabWidget.currentIndex()
        self.moveToState("getQuestion")

    def onStudyOptions(self):
        if self.state == "studyScreen":
            pass
        else:
            self.moveToState("studyScreen")

    # Toolbar
    ##########################################################################

    def setupToolbar(self):
        frm = self.form
        tb = frm.toolBar
        tb.addAction(frm.actionAddcards)
        tb.addAction(frm.actionEditCurrent)
        tb.addAction(frm.actionEditLayout)
        tb.addAction(frm.actionEditdeck)
        tb.addAction(frm.actionStudyOptions)
        tb.addAction(frm.actionGraphs)
        tb.addAction(frm.actionMarkCard)
        tb.addAction(frm.actionRepeatAudio)
        tb.addAction(frm.actionClose)
        tb.setIconSize(QSize(self.config['iconSize'],
                             self.config['iconSize']))
        toggle = tb.toggleViewAction()
        toggle.setText(_("Toggle Toolbar"))
        self.connect(toggle, SIGNAL("triggered()"),
                     self.onToolbarToggle)

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

    # Card & deck stats
    ##########################################################################

    def setupCardStats(self):
        self.cardStats = aqt.stats.CardStats(self)

    def onCardStats(self):
        self.cardStats.show()

    def onDeckStats(self):
        aqt.stats.deckStats(self)

    # Graphs
    ##########################################################################

    def onShowGraph(self):
        self.setStatus(_("Loading graphs (may take time)..."))
        self.app.processEvents()
        import anki.graphs
        if anki.graphs.graphsAvailable():
            try:
                aqt.dialogs.open("Graphs", self, self.deck)
            except (ImportError, ValueError):
                traceback.print_exc()
                if sys.platform.startswith("win32"):
                    showInfo(
                        _("To display graphs, Anki needs a .dll file which\n"
                          "you don't have. Please install:\n") +
                        "http://www.dll-files.com/dllindex/dll-files.shtml?msvcp71")
                else:
                    showInfo(_(
                        "Your version of Matplotlib is broken.\n"
                        "Please see http://ichi2.net/anki/wiki/MatplotlibBroken"))
        else:
            showInfo(_("Please install python-matplotlib to access graphs."))

    # Marking, suspending and undoing
    ##########################################################################

    def onMark(self, toggled):
        if self.deck.cardHasTag(self.currentCard, "Marked"):
            self.currentCard.fact.tags = canonifyTags(deleteTags(
                "Marked", self.currentCard.fact.tags))
        else:
            self.currentCard.fact.tags = canonifyTags(addTags(
                "Marked", self.currentCard.fact.tags))
        self.currentCard.fact.setModified(textChanged=True, deck=self.deck)
        self.deck.updateFactTags([self.currentCard.fact.id])
        self.deck.setModified()

    def onSuspend(self):
        undo = _("Suspend")
        self.deck.setUndoStart(undo)
        self.deck.suspendCards([self.currentCard.id])
        self.reset()
        self.deck.setUndoEnd(undo)

    def onDelete(self):
        undo = _("Delete")
        if self.state == "editCurrent":
            self.moveToState("saveEdit")
        self.deck.setUndoStart(undo)
        self.deck.deleteCard(self.currentCard.id)
        self.reset()
        self.deck.setUndoEnd(undo)
        runHook("currentCardDeleted")

    def onBuryFact(self):
        undo = _("Bury")
        self.deck.setUndoStart(undo)
        self.deck.buryFact(self.currentCard.fact)
        self.reset()
        self.deck.setUndoEnd(undo)

    def onUndo(self):
        name = self.deck.undoName()
        self.deck.undo()
        self.reset()
        if name == "Answer Card":
            self.setStatus(_("Card placed back in queue."))

    def onRedo(self):
        self.deck.redo()
        self.reset()

    # Other menu operations
    ##########################################################################

    def onAddCard(self):
        aqt.dialogs.open("AddCards", self)

    def onEditDeck(self):
        aqt.dialogs.open("CardList", self)

    def onEditCurrent(self):
        self.moveToState("editCurrentFact")

    def onCardLayout(self):
        aqt.clayout.CardLayout(self, 0, self.currentCard.fact,
                              card=self.currentCard)

    def onDeckProperties(self):
        self.deckProperties = aqt.deckproperties.DeckProperties(self, self.deck)

    def onPrefs(self):
        aqt.preferences.Preferences(self, self.config)

    def onAbout(self):
        aqt.about.show(self)

    def onDonate(self):
        QDesktopServices.openUrl(QUrl(aqt.appDonate))

    # Importing & exporting
    ##########################################################################

    def onImport(self):
        if self.deck is None:
            self.onNew(prompt=_("""\
Importing copies cards to the current deck,
so we need to create a new deck first.

Please give your deck a name:"""))
        if not self.deck:
            return
        if self.deck.path:
            aqt.importing.ImportDialog(self)

    def onExport(self):
        aqt.exporting.ExportDialog(self)

    # Cramming & Sharing
    ##########################################################################

    def _copyToTmpDeck(self, name="cram.anki", tags="", ids=[]):
        ndir = tempfile.mkdtemp(prefix="anki")
        path = os.path.join(ndir, name)
        from anki.exporting import AnkiExporter
        e = AnkiExporter(self.deck)
        e.includeMedia = False
        if tags:
            e.limitTags = parseTags(tags)
        if ids:
            e.limitCardIds = ids
        path = unicode(path, sys.getfilesystemencoding())
        e.exportInto(path)
        return (e, path)

    def onCram(self, cardIds=[]):
        te = aqt.tagedit.TagEdit(self)
        te.setDeck(self.deck, "all")
        diag = GetTextDialog(
            self, _("Tags to cram:"), help="CramMode", edit=te)
        l = diag.layout()
        g = QGroupBox(_("Review Mode"))
        l.insertWidget(2, g)
        box = QVBoxLayout()
        g.setLayout(box)
        keep = QRadioButton(_("Show oldest modified first"))
        box.addWidget(keep)
        keep.setChecked(True)
        diag.setTabOrder(diag.l, keep)
        order = QRadioButton(_("Show in order added"))
        box.addWidget(order)
        random = QRadioButton(_("Show in random order"))
        box.addWidget(random)
        # hide tag list if we have ids
        if cardIds:
            diag.l.hide()
            diag.qlabel.hide()
        if diag.exec_():
            if keep.isChecked():
                order = "type, modified"
            elif order.isChecked():
                order = "created"
            else:
                order = "random()"
            if cardIds:
                active = cardIds
            else:
                active = unicode(diag.l.text())
            self.deck.setupCramScheduler(active, order)
            if self.state == "studyScreen":
                self.onStartReview()
            else:
                self.deck.reset()
                self.deck.getCard() # so scheduler will reset if empty
                self.moveToState("initial")
            if not self.deck.finishScheduler:
                showInfo(_("No cards matched the provided tags."))

    def onShare(self, tags):
        pwd = os.getcwd()
        # open tmp deck
        (e, path) = self._copyToTmpDeck(name="shared.anki", tags=tags)
        if not e.exportedCards:
            showInfo(_("No cards matched the provided tags."))
            return
        self.deck.startProgress()
        self.deck.updateProgress()
        d = DeckStorage.Deck(path, backup=False)
        # reset scheduling to defaults
        d.newCardsPerDay = 20
        d.delay0 = 600
        d.delay1 = 0
        d.delay2 = 0
        d.hardIntervalMin = 1.0
        d.hardIntervalMax = 1.1
        d.midIntervalMin = 3.0
        d.midIntervalMax = 5.0
        d.easyIntervalMin = 7.0
        d.easyIntervalMax = 9.0
        d.syncName = None
        d.setVar("newActive", u"")
        d.setVar("newInactive", u"")
        d.setVar("revActive", u"")
        d.setVar("revInactive", u"")
        self.deck.updateProgress()
        # unsuspend cards
        d.unsuspendCards(d.s.column0("select id from cards where type < 0"))
        self.deck.updateProgress()
        d.utcOffset = -2
        d.flushMod()
        d.save()
        self.deck.updateProgress()
        # media
        d.s.statement("update deckVars set value = '' where key = 'mediaURL'")
        self.deck.updateProgress()
        d.s.statement("vacuum")
        self.deck.updateProgress()
        nfacts = d.factCount
        mdir = self.deck.mediaDir()
        d.close()
        dir = os.path.dirname(path)
        zippath = os.path.join(dir, "shared-%d.zip" % time.time())
        # zip it up
        zip = zipfile.ZipFile(zippath, "w", zipfile.ZIP_DEFLATED)
        zip.writestr("facts", str(nfacts))
        zip.writestr("version", str(2))
        readmep = os.path.join(dir, "README.html")
        readme = open(readmep, "w")
        readme.write('''\
<html><body>
This is an exported packaged deck created by Anki.<p>

To share this deck with other people, upload it to
<a href="http://anki.ichi2.net/file/upload">
http://anki.ichi2.net/file/upload</a>, or email
it to your friends.
</body></html>''')
        readme.close()
        zip.write(readmep, "README.html")
        zip.write(path, "shared.anki")
        if mdir:
            for f in os.listdir(mdir):
                zip.write(os.path.join(mdir, f),
                          os.path.join("shared.media/", f))
            os.chdir(pwd)
        os.chdir(pwd)
        self.deck.updateProgress()
        zip.close()
        os.unlink(path)
        self.deck.finishProgress()
        self.onOpenPluginFolder(dir)

    # Reviewing and learning ahead
    ##########################################################################

    def onLearnMore(self):
        self.deck.setupLearnMoreScheduler()
        self.reset()
        self.showToolTip(_("""\
<h1>Learning More</h1>Click the stopwatch at the top to finish."""))

    def onReviewEarly(self):
        self.deck.setupReviewEarlyScheduler()
        self.reset()
        self.showToolTip(_("""\
<h1>Reviewing Early</h1>Click the stopwatch at the top to finish."""))

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

    # Syncing
    ##########################################################################

    def syncDeck(self, interactive=True, onlyMerge=False, reload=True):
        "Synchronise a deck with the server."
        self.raiseMain()
        #self.setNotice()
        # vet input
        if interactive:
            self.ensureSyncParams()
        u=self.config['syncUsername']
        p=self.config['syncPassword']
        if not u or not p:
            return
        if self.deck:
            if not self.deck.path:
                if not self.save(required=True):
                    return
        if self.deck and not self.deck.syncName:
            if interactive:
                if (not self.config['mediaLocation']
                    and self.deck.db.scalar("select 1 from media limit 1")):
                    showInfo(_("""\
Syncing sounds and images requires a free file synchronization service like \
DropBox. Click help to learn more, and OK to continue syncing."""),
                                      help="SyncingMedia")
                # enable syncing
                self.deck.enableSyncing()
            else:
                return
        if self.deck is None and getattr(self, 'deckPath', None) is None:
            # sync all decks
            self.loadAfterSync = -1
            self.syncName = None
            self.syncDecks = self.decksToSync()
            if not self.syncDecks:
                if interactive:
                    showInfo(_("""\
Please open a deck and run File>Sync. After you do this once, the deck \
will sync automatically from then on."""))
                return
        else:
            # sync one deck
            # hide all deck-associated dialogs
            self.closeAllDeckWindows()
            if self.deck:
                # save first, so we can rollback on failure
                self.deck.save()
                # store data we need before closing the deck
                self.deckPath = self.deck.path
                self.syncName = self.deck.name()
                self.lastSync = self.deck.lastSync
                self.deck.close()
                self.deck = None
                self.loadAfterSync = reload
        # bug triggered by preferences dialog - underlying c++ widgets are not
        # garbage collected until the middle of the child thread
        self.state = "nostate"
        import gc; gc.collect()
        self.form.welcomeText.setText(u"")
        self.syncThread = aqt.sync.Sync(self, u, p, interactive, onlyMerge)
        self.connect(self.syncThread, SIGNAL("setStatus"), self.setSyncStatus)
        self.connect(self.syncThread, SIGNAL("showWarning"), self.showSyncWarning)
        self.connect(self.syncThread, SIGNAL("moveToState"), self.moveToState)
        self.connect(self.syncThread, SIGNAL("noMatchingDeck"), self.selectSyncDeck)
        self.connect(self.syncThread, SIGNAL("syncClockOff"), self.syncClockOff)
        self.connect(self.syncThread, SIGNAL("cleanNewDeck"), self.cleanNewDeck)
        self.connect(self.syncThread, SIGNAL("syncFinished"), self.onSyncFinished)
        self.connect(self.syncThread, SIGNAL("openSyncProgress"), self.openSyncProgress)
        self.connect(self.syncThread, SIGNAL("closeSyncProgress"), self.closeSyncProgress)
        self.connect(self.syncThread, SIGNAL("updateSyncProgress"), self.updateSyncProgress)
        self.connect(self.syncThread, SIGNAL("bulkSyncFailed"), self.bulkSyncFailed)
        self.connect(self.syncThread, SIGNAL("fullSyncStarted"), self.fullSyncStarted)
        self.connect(self.syncThread, SIGNAL("fullSyncFinished"), self.fullSyncFinished)
        self.connect(self.syncThread, SIGNAL("fullSyncProgress"), self.fullSyncProgress)
        self.connect(self.syncThread, SIGNAL("badUserPass"), self.badUserPass)
        self.connect(self.syncThread, SIGNAL("syncConflicts"), self.onConflict)
        self.connect(self.syncThread, SIGNAL("syncClobber"), self.onClobber)
        self.syncThread.start()
        self.switchToWelcomeScreen()
        self.setEnabled(False)
        self.syncFinished = False
        while not self.syncFinished:
            self.app.processEvents()
            self.syncThread.wait(100)
        self.setEnabled(True)
        return True

    def decksToSync(self):
        ok = []
        for d in self.config['recentDeckPaths']:
            if os.path.exists(d):
                ok.append(d)
        return ok

    def onConflict(self, deckName):
        diag = askUserDialog(_("""\
<b>%s</b> has been changed on both
the local and remote side. What do
you want to do?""" % deckName),
                          [_("Keep Local"),
                           _("Keep Remote"),
                           _("Cancel")])
        diag.setDefault(2)
        ret = diag.run()
        if ret == _("Keep Local"):
            self.syncThread.conflictResolution = "keepLocal"
        elif ret == _("Keep Remote"):
            self.syncThread.conflictResolution = "keepRemote"
        else:
            self.syncThread.conflictResolution = "cancel"

    def onClobber(self, deckName):
        diag = askUserDialog(_("""\
You are about to upload <b>%s</b>
to AnkiOnline. This will overwrite
the online copy of this deck.
Are you sure?""" % deckName),
                          [_("Upload"),
                           _("Cancel")])
        diag.setDefault(1)
        ret = diag.run()
        if ret == _("Upload"):
            self.syncThread.clobberChoice = "overwrite"
        else:
            self.syncThread.clobberChoice = "cancel"

    def onSyncFinished(self):
        "Reopen after sync finished."
        self.form.buttonStack.show()
        try:
            try:
                if not self.showBrowser:
                    # no deck load & no deck browser, as we're about to quit or do
                    # something manually
                    pass
                else:
                    if self.loadAfterSync == -1:
                        # after sync all, so refresh browser list
                        self.browserLastRefreshed = 0
                        self.moveToState("deckBrowser")
                    elif self.loadAfterSync and self.deckPath:
                        if self.loadAfterSync == 2:
                            name = re.sub("[<>]", "", self.syncName)
                            p = os.path.join(self.documentDir, name + ".anki")
                            shutil.copy2(self.deckPath, p)
                            self.deckPath = p
                            # since we've moved the deck, we have to set sync path
                            # ourselves
                            c = sqlite.connect(p)
                            v = c.execute(
                                "select version from decks").fetchone()[0]
                            if v >= 52:
                                # deck has bene upgraded already, so we can
                                # use a checksum
                                name = checksum(p.encode("utf-8"))
                            else:
                                # FIXME: compat code because deck hasn't been
                                # upgraded yet. can be deleted in the future.
                                # strip off .anki part
                                name = os.path.splitext(
                                    os.path.basename(p))[0]
                            c.execute("update decks set syncName = ?", (name,))
                            c.commit()
                            c.close()
                        self.loadDeck(self.deckPath)
                    else:
                        self.moveToState("deckBrowser")
            except:
                self.moveToState("deckBrowser")
                raise
        finally:
            self.deckPath = None
            self.syncFinished = True

    def selectSyncDeck(self, decks):
        name = aqt.sync.DeckChooser(self, decks).getName()
        self.syncName = name
        if name:
            # name chosen
            p = os.path.join(self.documentDir, name + ".anki")
            if os.path.exists(p):
                d = askUserDialog(_("""\
This deck already exists on your computer. Overwrite the local copy?"""),
                                         ["Overwrite", "Cancel"])
                d.setDefault(1)
                if d.run() == "Overwrite":
                    self.syncDeck(interactive=False, onlyMerge=True)
                else:
                    self.syncFinished = True
                    self.cleanNewDeck()
            else:
                self.syncDeck(interactive=False, onlyMerge=True)
            return
        self.syncFinished = True
        self.cleanNewDeck()

    def cleanNewDeck(self):
        "Unload a new deck if an initial sync failed."
        self.deck = None
        self.deckPath = None
        self.moveToState("deckBrowser")
        self.syncFinished = True

    def setSyncStatus(self, text, *args):
        self.form.welcomeText.append("<font size=+2>" + text + "</font>")

    def syncClockOff(self, diff):
        showWarning(
            _("The time or date on your computer is not correct.\n") +
            ngettext("It is off by %d second.\n\n",
                "It is off by %d seconds.\n\n", diff) % diff +
            _("Since this can cause many problems with syncing,\n"
              "syncing is disabled until you fix the problem.")
            )
        self.onSyncFinished()

    def showSyncWarning(self, text):
        showWarning(text, self)
        self.setStatus("")

    def badUserPass(self):
        aqt.preferences.Preferences(self, self.config).dialog.tabWidget.\
                                         setCurrentIndex(1)

    def openSyncProgress(self):
        self.syncProgressDialog = QProgressDialog(_("Syncing Media..."),
                                                  "", 0, 0, self)
        self.syncProgressDialog.setWindowTitle(_("Syncing Media..."))
        self.syncProgressDialog.setCancelButton(None)
        self.syncProgressDialog.setAutoClose(False)
        self.syncProgressDialog.setAutoReset(False)

    def closeSyncProgress(self):
        self.syncProgressDialog.cancel()

    def updateSyncProgress(self, args):
        (type, x, y, fname) = args
        self.syncProgressDialog.setMaximum(y)
        self.syncProgressDialog.setValue(x)
        self.syncProgressDialog.setMinimumDuration(0)
        if type == "up":
            self.syncProgressDialog.setLabelText("Uploading %s..." % fname)
        else:
            self.syncProgressDialog.setLabelText("Downloading %s..." % fname)

    def bulkSyncFailed(self):
        showWarning(_(
            "Failed to upload media. Please run 'check media db'."), self)

    def fullSyncStarted(self, max):
        self.startProgress(max=max)

    def fullSyncFinished(self):
        self.finishProgress()
        # need to deactivate interface again
        self.setEnabled(False)

    def fullSyncProgress(self, type, val):
        if type == "fromLocal":
            s = _("Uploaded %dKB to server...")
            self.updateProgress(label=s % (val / 1024), value=val)
        else:
            s = _("Downloaded %dKB from server...")
            self.updateProgress(label=s % (val / 1024))

    # Menu, title bar & status
    ##########################################################################

    deckRelatedMenuItems = (
        "Rename",
        "Close",
        "Addcards",
        "Editdeck",
        "DeckProperties",
        "Undo",
        "Redo",
        "Export",
        "Graphs",
        "Dstats",
        "Cstats",
        "Cram",
        "StudyOptions",
        )

    deckRelatedMenus = (
        "Edit",
        "Tools",
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
        self.connect(m.actionSyncdeck, s, self.syncDeck)
        self.connect(m.actionDeckProperties, s, self.onDeckProperties)
        self.connect(m.actionAddcards, s, self.onAddCard)
        self.connect(m.actionEditdeck, s, self.onEditDeck)
        self.connect(m.actionEditCurrent, s, self.onEditCurrent)
        self.connect(m.actionPreferences, s, self.onPrefs)
        self.connect(m.actionDstats, s, self.onDeckStats)
        self.connect(m.actionCstats, s, self.onCardStats)
        self.connect(m.actionGraphs, s, self.onShowGraph)
        self.connect(m.actionEditLayout, s, self.onCardLayout)
        self.connect(m.actionAbout, s, self.onAbout)
        self.connect(m.actionStarthere, s, self.onStartHere)
        self.connect(m.actionImport, s, self.onImport)
        self.connect(m.actionExport, s, self.onExport)
        self.connect(m.actionMarkCard, SIGNAL("toggled(bool)"), self.onMark)
        self.connect(m.actionSuspendCard, s, self.onSuspend)
        self.connect(m.actionDelete, s, self.onDelete)
        self.connect(m.actionRepeatAudio, s, self.onRepeatAudio)
        self.connect(m.actionUndo, s, self.onUndo)
        self.connect(m.actionRedo, s, self.onRedo)
        self.connect(m.actionFullDatabaseCheck, s, self.onCheckDB)
        self.connect(m.actionCheckMediaDatabase, s, self.onCheckMediaDB)
        self.connect(m.actionDownloadMissingMedia, s, self.onDownloadMissingMedia)
        self.connect(m.actionLocalizeMedia, s, self.onLocalizeMedia)
        self.connect(m.actionCram, s, self.onCram)
        self.connect(m.actionOpenPluginFolder, s, self.onOpenPluginFolder)
        self.connect(m.actionEnableAllPlugins, s, self.onEnableAllPlugins)
        self.connect(m.actionDisableAllPlugins, s, self.onDisableAllPlugins)
        self.connect(m.actionStudyOptions, s, self.onStudyOptions)
        self.connect(m.actionDonate, s, self.onDonate)
        self.connect(m.actionRecordNoiseProfile, s, self.onRecordNoiseProfile)
        self.connect(m.actionBuryFact, s, self.onBuryFact)

    def enableDeckMenuItems(self, enabled=True):
        "setEnabled deck-related items."
        for item in self.deckRelatedMenus:
            getattr(self.form, "menu" + item).setEnabled(enabled)
        for item in self.deckRelatedMenuItems:
            getattr(self.form, "action" + item).setEnabled(enabled)
        if not enabled:
            self.disableCardMenuItems()
        runHook("enableDeckMenuItems", enabled)

    def disableDeckMenuItems(self):
        "Disable deck-related items."
        self.enableDeckMenuItems(enabled=False)

    def updateTitleBar(self):
        "Display the current deck and card count in the titlebar."
        title=aqt.appName
        if self.deck:
            deckpath = self.deck.name()
            if not self.config['showProgress']:
                title = deckpath + " - " + title
            else:
                title = _("%(path)s (%(due)d of %(cards)d due)"
                          " - %(title)s") % {
                    "path": deckpath,
                    "title": title,
                    "cards": self.deck.cardCount,
                    "due": self.deck.failedSoonCount + self.deck.revCount
                    }
        else:
            title += " - " + _("Decks")
        self.setWindowTitle(title)

    def setStatus(self, text, timeout=3000):
        self.form.statusbar.showMessage(text, timeout)

    def onStartHere(self):
        QDesktopServices.openUrl(QUrl(aqt.appHelpSite))

    def updateMarkAction(self):
        self.form.actionMarkCard.blockSignals(True)
        if self.deck.cardHasTag(self.currentCard, "Marked"):
            self.form.actionMarkCard.setChecked(True)
        else:
            self.form.actionMarkCard.setChecked(False)
        self.form.actionMarkCard.blockSignals(False)

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
        runHook("enableCardMenuItems")

    def maybeEnableUndo(self):
        if self.deck and self.deck.undoAvailable():
            self.form.actionUndo.setText(_("Undo %s") %
                                            self.deck.undoName())
            self.form.actionUndo.setEnabled(True)
        else:
            self.form.actionUndo.setEnabled(False)
        if self.deck and self.deck.redoAvailable():
            self.form.actionRedo.setText(_("Redo %s") %
                                            self.deck.redoName())
            self.form.actionRedo.setEnabled(True)
        else:
            self.form.actionRedo.setEnabled(False)

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

    # Plugins
    ##########################################################################

    def pluginsFolder(self):
        dir = self.config.confDir
        if sys.platform.startswith("win32"):
            dir = dir.encode(sys.getfilesystemencoding())
        return os.path.join(dir, "plugins")

    def loadPlugins(self):
        if sys.platform.startswith("win32"):
            self.clearPluginCache()
        self.disableObsoletePlugins()
        plugdir = self.pluginsFolder()
        sys.path.insert(0, plugdir)
        plugins = self.enabledPlugins()
        plugins.sort()
        self.registeredPlugins = {}
        for plugin in plugins:
            try:
                nopy = plugin.replace(".py", "")
                __import__(nopy)
            except:
                print "Error in %s" % plugin
                traceback.print_exc()
        self.checkForUpdatedPlugins()
        self.disableCardMenuItems()
        self.rebuildPluginsMenu()
        # run the obsolete init hook
        try:
            runHook('init')
        except:
            showWarning(
                _("Broken plugin:\n\n%s") %
                unicode(traceback.format_exc(), "utf-8", "replace"))

    def clearPluginCache(self):
        "Clear .pyc files which may cause crashes if Python version updated."
        dir = self.pluginsFolder()
        for curdir, dirs, files in os.walk(dir):
            for f in files:
                if not f.endswith(".pyc"):
                    continue
                os.unlink(os.path.join(curdir, f))

    def disableObsoletePlugins(self):
        dir = self.pluginsFolder()
        native = _(
            "The %s plugin has been disabled, as Anki supports "+
            "this natively now.")
        plugins = [
            ("Custom Media Directory.py",
             (native % "custom media folder") + _(""" \
Please visit Settings>Preferences.""")),
            ("Regenerate Reading Field.py", _("""\
The regenerate reading field plugin has been disabled, as the Japanese \
support plugin supports this now. Please download the latest version.""")),
            ("Sync LaTeX with iPhone client.py",
             native % "sync LaTeX"),
            ("Incremental Reading.py",
             _("""The incremental reading plugin has been disabled because \
it needs updates.""")),
            ("Learn Mode.py", _("""\
The learn mode plugin has been disabled because it needs to be rewritten \
to work with this version of Anki."""))
            ]
        for p in plugins:
            path = os.path.join(dir, p[0])
            if os.path.exists(path):
                new = path.replace(".py", ".disabled")
                if os.path.exists(new):
                    os.unlink(new)
                os.rename(path, new)
                showInfo(p[1])

    def rebuildPluginsMenu(self):
        if getattr(self, "pluginActions", None) is None:
            self.pluginActions = []
        for action in self.pluginActions:
            self.form.menuStartup.removeAction(action)
        all = self.allPlugins()
        all.sort()
        for fname in all:
            enabled = fname.endswith(".py")
            p = re.sub("\.py(\.off)?", "", fname)
            if p+".py" in self.registeredPlugins:
                p = self.registeredPlugins[p+".py"]['name']
            a = QAction(p, self)
            a.setCheckable(True)
            a.setChecked(enabled)
            self.connect(a, SIGNAL("triggered()"),
                         lambda fname=fname: self.togglePlugin(fname))
            self.form.menuStartup.addAction(a)
            self.pluginActions.append(a)

    def enabledPlugins(self):
        return [p for p in os.listdir(self.pluginsFolder())
                if p.endswith(".py")]

    def disabledPlugins(self):
        return [p for p in os.listdir(self.pluginsFolder())
                if p.endswith(".py.off")]

    def allPlugins(self):
        return [p for p in os.listdir(self.pluginsFolder())
                if p.endswith(".py.off") or p.endswith(".py")]

    def onOpenPluginFolder(self, path=None):
        if path is None:
            path = self.pluginsFolder()
        if sys.platform == "win32":
            if isinstance(path, unicode):
                path = path.encode(sys.getfilesystemencoding())
            anki.utils.call(["explorer", path], wait=False)
        else:
            QDesktopServices.openUrl(QUrl("file://" + path))

    def onGetPlugins(self):
        QDesktopServices.openUrl(QUrl("http://ichi2.net/anki/wiki/Plugins"))

    def enablePlugin(self, p):
        pd = self.pluginsFolder()
        old = os.path.join(pd, p)
        new = os.path.join(pd, p.replace(".off", ""))
        try:
            os.unlink(new)
        except:
            pass
        os.rename(old, new)

    def disablePlugin(self, p):
        pd = self.pluginsFolder()
        old = os.path.join(pd, p)
        new = os.path.join(pd, p.replace(".py", ".py.off"))
        try:
            os.unlink(new)
        except:
            pass
        os.rename(old, new)

    def onEnableAllPlugins(self):
        for p in self.disabledPlugins():
            self.enablePlugin(p)
        self.rebuildPluginsMenu()

    def onDisableAllPlugins(self):
        for p in self.enabledPlugins():
            self.disablePlugin(p)
        self.rebuildPluginsMenu()

    def togglePlugin(self, plugin):
        if plugin.endswith(".py"):
            self.disablePlugin(plugin)
        else:
            self.enablePlugin(plugin)
        self.rebuildPluginsMenu()

    def registerPlugin(self, name, updateId):
        return
        # src = os.path.basename(inspect.getfile(inspect.currentframe(1)))
        # self.registeredPlugins[src] = {'name': name,
        #                                'id': updateId}

    def checkForUpdatedPlugins(self):
        pass

    # Custom styles
    ##########################################################################

    def setupStyle(self):
        applyStyles(self)

    # Sounds
    ##########################################################################

    def setupSound(self):
        anki.sound.noiseProfile = os.path.join(
            self.config.confDir, "noise.profile").\
            encode(sys.getfilesystemencoding())
        anki.sound.checkForNoiseProfile()
        if sys.platform.startswith("darwin"):
            self.form.actionRecordNoiseProfile.setEnabled(False)

    def onRepeatAudio(self):
        clearAudioQueue()
        if (not self.currentCard.cardModel.questionInAnswer
            or self.state == "showQuestion") and \
            self.config['repeatQuestionAudio']:
            playFromText(self.currentCard.question)
        if self.state != "showQuestion":
            playFromText(self.currentCard.answer)

    def onRecordNoiseProfile(self):
        from aqt.sound import recordNoiseProfile
        recordNoiseProfile(self)

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
            if sys.platform.startswith("win32"):
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
        ret = self.deck.fixIntegrity()
        showText(ret)
        self.reset()
        return ret

    def onCheckMediaDB(self):
        mb = QMessageBox(self)
        mb.setWindowTitle(_("Anki"))
        mb.setIcon(QMessageBox.Warning)
        mb.setText(_("""\
This operation looks through the content of your cards for media, and \
registers it so that it can be used with the online and mobile clients.

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
        (nohave, unused) = rebuildMediaDir(self.deck, delete=delete)
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

    def addHook(self, *args):
        addHook(*args)

    # System specific code
    ##########################################################################

    def setupSystemSpecific(self):
        self.setupDocumentDir()
        addHook("macLoadEvent", self.onMacLoad)
        if sys.platform.startswith("darwin"):
            qt_mac_set_menubar_icons(False)
            self.setUnifiedTitleAndToolBarOnMac(True)
            self.form.actionMarkCard.setShortcut(_("Alt+m"))
            # mac users expect a minimize option
            self.minimizeShortcut = QShortcut("Ctrl+m", self)
            self.connect(self.minimizeShortcut, SIGNAL("activated()"),
                         self.onMacMinimize)
            self.hideAccelerators()
            self.hideStatusTips()
        elif sys.platform.startswith("win32"):
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
            self.documentDir = self.config['documentDir']
        elif sys.platform.startswith("win32"):
            s = QSettings(QSettings.UserScope, "Microsoft", "Windows")
            s.beginGroup("CurrentVersion/Explorer/Shell Folders")
            self.documentDir = unicode(s.value("Personal").toString())
            if os.path.exists(self.documentDir):
                self.documentDir = os.path.join(self.documentDir, "Anki")
            else:
                self.documentDir = os.path.expanduser("~/.anki/decks")
        elif sys.platform.startswith("darwin"):
            self.documentDir = os.path.expanduser("~/Documents/Anki")
        else:
            self.documentDir = os.path.expanduser("~/.anki/decks")
        try:
            os.mkdir(self.documentDir)
        except (OSError, IOError):
            pass

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

    # Misc
    ##########################################################################

    def setupMisc(self):
        # if they've just upgraded, set created time based on deck age
        if time.time() - self.config['created'] < 60 and self.deck:
            self.config['created'] = self.deck.created
        # tweaks for small screens
        if self.config['optimizeSmall']:
            p = self.form.deckBrowserOuterFrame.sizePolicy()
            p.setHorizontalStretch(1)
            self.form.deckBrowserOuterFrame.setSizePolicy(p)
            self.form.decksLabel.hide()
            self.form.decksLine.hide()
            self.form.studyOptsLabel.hide()
