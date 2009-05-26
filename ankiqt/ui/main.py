# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from PyQt4.QtWebKit import QWebPage

import os, sys, re, types, gettext, stat, traceback, inspect
import shutil, time, glob, tempfile, datetime, zipfile, locale

from PyQt4.QtCore import *
from PyQt4.QtGui import *
from anki import DeckStorage
from anki.errors import *
from anki.sound import hasSound, playFromText, clearAudioQueue
from anki.utils import addTags, deleteTags, parseTags, canonifyTags
from anki.media import rebuildMediaDir
from anki.db import OperationalError, SessionHelper
from anki.stdmodels import BasicModel
from anki.hooks import runHook, addHook, removeHook, _hooks, wrap
from anki.deck import newCardOrderLabels, newCardSchedulingLabels
from anki.deck import revCardOrderLabels, failedCardOptionLabels
import anki.latex
import anki.lang
import anki.deck
import ankiqt
ui = ankiqt.ui
config = ankiqt.config

class AnkiQt(QMainWindow):
    def __init__(self, app, config, args):
        QMainWindow.__init__(self)
        self.errorOccurred = False
        self.inDbHandler = False
        self.reviewingStarted = False
        if sys.platform.startswith("darwin"):
            qt_mac_set_menubar_icons(False)
        ankiqt.mw = self
        self.app = app
        self.config = config
        self.deck = None
        self.state = "initial"
        self.hideWelcome = False
        self.views = []
        self.setLang()
        self.setupFonts()
        self.setupBackupDir()
        self.setupMainWindow()
        self.setupSystemHacks()
        self.setupSound()
        self.setupTray()
        self.connectMenuActions()
        ui.splash.update()
        self.setupViews()
        self.setupEditor()
        self.setupStudyScreen()
        self.setupButtons()
        self.setupAnchors()
        self.setupToolbar()
        self.setupProgressInfo()
        self.setupBackups()
        if self.config['mainWindowState']:
            self.restoreGeometry(self.config['mainWindowGeom'])
            self.restoreState(self.config['mainWindowState'])
        if sys.platform.startswith("darwin"):
            self.setUnifiedTitleAndToolBarOnMac(True)
            pass
        # load deck
        ui.splash.update()
        if not self.maybeLoadLastDeck(args):
            self.setEnabled(True)
            self.moveToState("auto")
        # check for updates
        ui.splash.update()
        self.setupErrorHandler()
        self.setupMisc()
        self.loadPlugins()
        self.setupAutoUpdate()
        self.rebuildPluginsMenu()
        # run after-init hook
        try:
            runHook('init')
        except:
            ui.utils.showWarning(_("Broken plugin:\n\n%s") %
                                 traceback.format_exc())
        ui.splash.update()
        ui.splash.finish(self)
        self.show()
        if (self.deck and self.config['syncOnLoad'] and
            self.deck.syncName):
            self.syncDeck(interactive=False)

    def setupMainWindow(self):
        # main window
        self.mainWin = ankiqt.forms.main.Ui_MainWindow()
        self.mainWin.setupUi(self)
        self.mainWin.mainText = ui.view.AnkiWebView(self.mainWin.mainTextFrame)
        self.mainWin.mainText.setObjectName("mainText")
        self.mainWin.mainText.setFocusPolicy(Qt.ClickFocus)
        self.mainWin.mainStack.addWidget(self.mainWin.mainText)
        self.help = ui.help.HelpArea(self.mainWin.helpFrame, self.config, self)
        self.connect(self.mainWin.mainText.pageAction(QWebPage.Reload),
                     SIGNAL("activated()"),
                     lambda: self.moveToState("auto"))
        # congrats
        self.connect(self.mainWin.learnMoreButton,
                     SIGNAL("clicked()"),
                     self.onLearnMore)
        self.connect(self.mainWin.reviewEarlyButton,
                     SIGNAL("clicked()"),
                     self.onReviewEarly)

    def setupViews(self):
        self.bodyView = ui.view.View(self, self.mainWin.mainText,
                                     self.mainWin.mainTextFrame)
        self.addView(self.bodyView)
        self.statusView = ui.status.StatusView(self)
        self.addView(self.statusView)

    def setupTray(self):
	self.trayIcon = ui.tray.AnkiTrayIcon(self)

    def setupErrorHandler(self):
        class ErrorPipe(object):
            def __init__(self, parent):
                self.parent = parent
                self.timer = None
                self.pool = ""
                self.poolUpdated = 0

            def write(self, data):
                print data.encode("utf-8"),
                self.pool += data
                self.poolUpdated = time.time()

            def haveError(self):
                if self.pool:
                    if (time.time() - self.poolUpdated) > 1:
                        return True

            def getError(self):
                p = self.pool
                self.pool = ""
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
                ui.utils.showInfo(
                    _("Couldn't play sound. Please install mplayer."))
                return
            stdText = _("""\
An error occurred. Please:<p>
<ol>
<li><b>Restart Anki</b>.
<li><b>Tools > Advanced > Check DB</b>.
</ol>
If it does not fix the problem, please copy the following<br>
into a bug report:<br>
""")
            pluginText = _("""\
An error occurred in a plugin. Please contact the plugin author.<br>
Please do not file a bug report with Anki.<br>""")
            if "plugin" in error:
                txt = pluginText
            else:
                txt = stdText
            self.errorOccurred = True
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

    def closeAllDeckWindows(self):
        ui.dialogs.closeAll()
        self.help.hide()

    # State machine
    ##########################################################################

    def addView(self, view):
        self.views.append(view)

    def updateViews(self, status):
        if self.deck is None and status != "noDeck":
            raise "updateViews() called with no deck. status=%s" % status
        for view in self.views:
            view.setState(status)

    def pauseViews(self):
        if getattr(self, 'viewsBackup', None):
            return
        self.viewsBackup = self.views
        self.views = []

    def restoreViews(self):
        self.views = self.viewsBackup
        self.viewsBackup = None

    def reset(self, count=True, priorities=False):
        if self.deck:
            self.deck.refresh()
            if priorities:
                self.deck.updateAllPriorities()
            if count:
                self.deck.rebuildCounts()
            self.deck.rebuildQueue()
            runHook("guiReset")
            self.moveToState("initial")

    def moveToState(self, state):
        t = time.time()
        if state == "initial":
            # reset current card and load again
            self.currentCard = None
            self.lastCard = None
            self.editor.deck = self.deck
            if self.deck:
                self.enableDeckMenuItems()
                self.updateRecentFilesMenu()
                self.updateViews(state)
                if self.state == "studyScreen":
                    return self.moveToState("studyScreen")
                else:
                    return self.moveToState("getQuestion")
            else:
                return self.moveToState("noDeck")
        elif state == "auto":
            self.currentCard = None
            if self.deck:
                if self.state == "studyScreen":
                    return self.moveToState("studyScreen")
                else:
                    return self.moveToState("getQuestion")
            else:
                return self.moveToState("noDeck")
        # save the new & last state
        self.lastState = getattr(self, "state", None)
        self.state = state
        self.updateTitleBar()
        if 'state' != 'noDeck' and state != 'editCurrentFact':
            self.switchToReviewScreen()
        if state == "noDeck":
            self.deck = None
            self.switchToWelcomeScreen()
            self.help.hide()
            self.currentCard = None
            self.lastCard = None
            self.disableDeckMenuItems()
            self.updateRecentFilesMenu()
            # hide all deck-associated dialogs
            self.closeAllDeckWindows()
        elif state == "getQuestion":
            if self.deck.isEmpty():
                return self.moveToState("deckEmpty")
            else:
                if not self.deck.reviewEarly:
                    if (self.config['showStudyScreen'] and
                        not self.deck.sessionStartTime):
                        return self.moveToState("studyScreen")
                    if self.deck.sessionLimitReached():
                        return self.moveToState("studyScreen")
                if not self.currentCard:
                    self.currentCard = self.deck.getCard()
                if self.currentCard:
                    if self.lastCard:
                        if self.lastCard.id == self.currentCard.id:
                            if self.currentCard.combinedDue > time.time():
                                # if the same card is being shown and it's not
                                # due yet, give up
                                return self.moveToState("deckFinished")
                    self.enableCardMenuItems()
                    return self.moveToState("showQuestion")
                else:
                    return self.moveToState("deckFinished")
        elif state == "deckEmpty":
            self.switchToWelcomeScreen()
            self.disableCardMenuItems()
        elif state == "deckFinished":
            self.deck.s.flush()
            self.hideButtons()
            self.disableCardMenuItems()
            self.switchToCongratsScreen()
            self.mainWin.learnMoreButton.setEnabled(
                not not self.deck.newCount)
            self.startRefreshTimer()
            self.bodyView.setState(state)
            # make sure the buttons aren't focused
            self.mainWin.congratsLabel.setFocus()
        elif state == "showQuestion":
            self.reviewingStarted = True
            if self.deck.mediaDir():
                os.chdir(self.deck.mediaDir())
            self.showAnswerButton()
            self.updateMarkAction()
            runHook('showQuestion')
        elif state == "showAnswer":
            self.showEaseButtons()
            self.enableCardMenuItems()
        elif state == "editCurrentFact":
            if self.lastState == "editCurrentFact":
                return self.moveToState("saveEdit")
            self.mainWin.actionRepeatAudio.setEnabled(False)
            self.deck.s.flush()
            self.showEditor()
        elif state == "saveEdit":
            self.editor.saveFieldsNow()
            self.mainWin.buttonStack.show()
            self.deck.refresh()
            return self.moveToState("auto")
        elif state == "studyScreen":
            self.currentCard = None
            self.deck.resetAfterReviewEarly()
            self.disableCardMenuItems()
            self.showStudyScreen()
        self.updateViews(state)

    def keyPressEvent(self, evt):
        "Show answer on RET or register answer."
        if evt.key() in (Qt.Key_Up,Qt.Key_Down,Qt.Key_Left,Qt.Key_Right,
                         Qt.Key_PageUp,Qt.Key_PageDown):
            mf = self.bodyView.body.page().currentFrame()
            if evt.key() == Qt.Key_Up:
                mf.evaluateJavaScript("window.scrollBy(0,-20)")
            elif evt.key() == Qt.Key_Down:
                mf.evaluateJavaScript("window.scrollBy(0,20)")
            elif evt.key() == Qt.Key_Left:
                mf.evaluateJavaScript("window.scrollBy(-20,0)")
            elif evt.key() == Qt.Key_Right:
                mf.evaluateJavaScript("window.scrollBy(20,0)")
            elif evt.key() == Qt.Key_PageUp:
                mf.evaluateJavaScript("window.scrollBy(0,-%d)" %
                                      int(0.9*self.bodyView.body.size().
                                          height()))
            elif evt.key() == Qt.Key_PageDown:
                mf.evaluateJavaScript("window.scrollBy(0,%d)" %
                                      int(0.9*self.bodyView.body.size().
                                          height()))
            evt.accept()
            return
        if self.state == "showQuestion":
            if evt.key() in (Qt.Key_Enter,
                             Qt.Key_Return):
                evt.accept()
                return self.mainWin.showAnswerButton.click()
            elif evt.key() == Qt.Key_Space and not (
                self.currentCard.cardModel.typeAnswer):
                evt.accept()
                return self.mainWin.showAnswerButton.click()
        elif self.state == "showAnswer":
            if evt.key() == Qt.Key_Space:
                key = str(self.defaultEaseButton())
            else:
                key = unicode(evt.text())
            if key and key >= "1" and key <= "4":
                # user entered a quality setting
                num=int(key)
                evt.accept()
                return getattr(self.mainWin, "easeButton%d" %
                               num).animateClick()
        elif self.state == "studyScreen":
            if evt.key() in (Qt.Key_Enter,
                             Qt.Key_Return):
                evt.accept()
                return self.onStartReview()
        elif self.state == "editCurrentFact":
            if evt.key() == Qt.Key_Escape:
                evt.accept()
                return self.moveToState("saveEdit")
        evt.ignore()

    def cardAnswered(self, quality):
        "Reschedule current card and move back to getQuestion state."
        if self.state != "showAnswer":
            return
        # force refresh of card then remove from session as we update in pure sql
        self.deck.s.refresh(self.currentCard)
        self.deck.s.refresh(self.currentCard.fact)
        self.deck.s.refresh(self.currentCard.cardModel)
        self.deck.s.expunge(self.currentCard)
        # answer
        self.deck.answerCard(self.currentCard, quality)
        self.lastScheduledTime = anki.utils.fmtTimeSpan(
            self.currentCard.due - time.time())
        self.lastQuality = quality
        self.lastCard = self.currentCard
        self.currentCard = None
        if self.config['saveAfterAnswer']:
            num = self.config['saveAfterAnswerNum']
            stats = self.deck.getStats()
            if stats['gTotal'] % num == 0:
                self.save()
        # stop anything playing
        clearAudioQueue()
        self.moveToState("getQuestion")

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
                new = self.deck.newCardTable()
                rev = self.deck.revCardTable()
                sys.stderr.write("""\
earliest time returned %f

please report this error, but it's not serious.
closing and opening your deck should fix it.

counts are %d %d %d
according to the db %d %d %d
failed:
%s
rev:
%s
new:
%s""" % (delay,
         self.deck.failedSoonCount,
         self.deck.revCount,
         self.deck.newCountToday,
         self.deck.s.scalar("select count(*) from failedCards"),
         self.deck.s.scalar("select count(*) from %s" % rev),
         self.deck.s.scalar("select count(*) from %s" % new),
         self.deck.s.all("select * from failedCards limit 2"),
         self.deck.s.all("select * from %s limit 2" % rev),
         self.deck.s.all("select * from %s limit 2" % new)))
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

    def switchToWelcomeScreen(self):
        self.mainWin.mainStack.setCurrentIndex(1)
        self.hideButtons()

    def switchToEditScreen(self):
        self.mainWin.mainStack.setCurrentIndex(2)

    def switchToStudyScreen(self):
        self.mainWin.mainStack.setCurrentIndex(3)

    def switchToCongratsScreen(self):
        self.mainWin.mainStack.setCurrentIndex(4)

    def switchToReviewScreen(self):
        self.mainWin.mainStack.setCurrentIndex(5)

    # Buttons
    ##########################################################################

    def setupButtons(self):
        # ask
        self.connect(self.mainWin.showAnswerButton, SIGNAL("clicked()"),
                     lambda: self.moveToState("showAnswer"))
        if sys.platform.startswith("win32"):
            if self.config['alternativeTheme']:
                self.mainWin.showAnswerButton.setFixedWidth(370)
            else:
                self.mainWin.showAnswerButton.setFixedWidth(358)
        else:
            self.mainWin.showAnswerButton.setFixedWidth(351)
        self.mainWin.showAnswerButton.setFixedHeight(41)
        # answer
        for i in range(1, 5):
            b = getattr(self.mainWin, "easeButton%d" % i)
            b.setFixedWidth(85)
            self.connect(b, SIGNAL("clicked()"),
                lambda i=i: self.cardAnswered(i))
        # type answer
        outer = QHBoxLayout()
        self.typeAnswerSpacer1 = QSpacerItem(5, 5)
        outer.addSpacerItem(self.typeAnswerSpacer1)
        class QLineEditNoUndo(QLineEdit):
            def __init__(self, parent):
                self.parent = parent
                QLineEdit.__init__(self, parent)
            def keyPressEvent(self, evt):
                if evt.matches(QKeySequence.Undo):
                    evt.accept()
                    if self.parent.mainWin.actionUndo.isEnabled():
                        self.parent.onUndo()
                else:
                    return QLineEdit.keyPressEvent(self, evt)
        self.typeAnswerField = QLineEditNoUndo(self)
        self.typeAnswerField.setObjectName("typeAnswerField")
        self.typeAnswerField.setFixedWidth(351)
        f = QFont()
        if sys.platform.startswith("darwin"):
            self.typeAnswerField.setFixedHeight(40)
        f.setPixelSize(self.config['typeAnswerFontSize'])
        self.typeAnswerField.setFont(f)
        outer.addWidget(self.typeAnswerField)
        self.typeAnswerSpacer2 = QSpacerItem(5, 5)
        outer.addSpacerItem(self.typeAnswerSpacer2)
        self.mainWin.typeAnswerPage.setLayout(outer)

    def hideButtons(self):
        self.mainWin.buttonStack.hide()

    def showAnswerButton(self):
        if self.currentCard.cardModel.typeAnswer:
            self.mainWin.buttonStack.setCurrentIndex(2)
            self.typeAnswerField.setFocus()
            if not unicode(self.typeAnswerField.text()):
                self.typeAnswerField.setText(_(
                    "Type in the answer and hit enter"))
                self.typeAnswerField.selectAll()
            else:
                self.typeAnswerField.setText("")
        else:
            self.mainWin.buttonStack.setCurrentIndex(0)
            self.mainWin.showAnswerButton.setFocus()
        self.mainWin.buttonStack.show()

    def showEaseButtons(self):
        self.updateEaseButtons()
        self.mainWin.buttonStack.setCurrentIndex(1)
        self.mainWin.buttonStack.show()
        if self.defaultEaseButton() == 2:
            self.mainWin.easeButton2.setFocus()
        else:
            self.mainWin.easeButton3.setFocus()

    def defaultEaseButton(self):
        if self.currentCard.reps and not self.currentCard.successive:
            return 2
        else:
            return 3

    def updateEaseButtons(self):
        nextInts = {}
        for i in range(1, 5):
            l = getattr(self.mainWin, "easeLabel%d" % i)
            if self.config['suppressEstimates']:
                l.setText("")
            elif i == 1:
                l.setText(_("Soon"))
            else:
                l.setText("<b>" + self.deck.nextIntervalStr(
                    self.currentCard, i) + "</b>")

    # Deck loading & saving: backend
    ##########################################################################

    def setupBackupDir(self):
        anki.deck.backupDir = os.path.join(
            self.config.configPath, "backups")

    def loadDeck(self, deckPath, sync=True, interactive=True, uprecent=True,
                 media=None):
        "Load a deck and update the user interface. Maybe sync."
        self.reviewingStarted = False
        # return True on success
        try:
            self.pauseViews()
            if not self.saveAndClose(hideWelcome=True):
                return 0
        finally:
            self.restoreViews()
        if not os.path.exists(deckPath):
            self.moveToState("noDeck")
            return
        try:
            self.deck = DeckStorage.Deck(deckPath)
        except Exception, e:
            if hasattr(e, 'data') and e.data['type'] == 'inuse':
                if interactive:
                    ui.utils.showInfo(_("Deck is already open."))
            else:
                fmt = traceback.format_exc().split("\n")
                fmt1 = "\n".join(fmt[0:3])
                fmt2 = "\n".join(fmt[-3:])
                ui.utils.showInfo(_("""\
Unable to load deck.

Possible reasons:
 - file is not an Anki deck
 - deck is read only
 - directory is read only
 - deck was created with Anki < 0.9

To upgrade an old deck, download Anki 0.9.8.7."""))
                traceback.print_exc()
            self.moveToState("noDeck")
            return
        if media is not None:
            self.deck.forceMediaDir = media
        if uprecent:
            self.updateRecentFiles(self.deck.path)
        if (sync and self.config['syncOnLoad']
            and self.deck.syncName):
            if self.syncDeck(interactive=False):
                return True
        try:
            self.deck.initUndo()
            self.moveToState("initial")
        except:
            traceback.print_exc()
            if ui.utils.askUser(_(
                "An error occurred while trying to build the queue.\n"
                "Would you like to try check the deck for errors?\n"
                "This may take some time.")):
                self.onCheckDB()
                # try again
                try:
                    self.reset()
                except:
                    ui.utils.showWarning(
                        _("Unable to recover. Deck load failed."))
                    self.deck = None
            else:
                self.deck = None
                return 0
            self.moveToState("noDeck")
        return True

    def maybeLoadLastDeck(self, args):
        "Open the last deck if possible."
        # try a command line argument if available
        if args:
            f = unicode(args[0], sys.getfilesystemencoding())
            return self.loadDeck(f, sync=False)
        # try recent deck paths
        for path in self.config['recentDeckPaths']:
            r = self.loadDeck(path, interactive=False, sync=False)
            if r:
                return r
        self.onNew(initial=True)

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
        del self.config['recentDeckPaths'][8:]
        self.config.save()
        self.updateRecentFilesMenu()

    def updateRecentFilesMenu(self):
        self.config['recentDeckPaths'] = [
            p for p in self.config['recentDeckPaths']
            if os.path.exists(p)]
        if not self.config['recentDeckPaths']:
            self.mainWin.menuOpenRecent.setEnabled(False)
            return
        self.mainWin.menuOpenRecent.setEnabled(True)
        self.mainWin.menuOpenRecent.clear()
        n = 1
        for file in self.config['recentDeckPaths']:
            a = QAction(self)
            if sys.platform.startswith("darwin"):
                a.setShortcut(_("Ctrl+Alt+%d" % n))
            else:
                a.setShortcut(_("Alt+%d" % n))
            a.setText(os.path.basename(file))
            a.setStatusTip(os.path.abspath(file))
            self.connect(a, SIGNAL("triggered()"),
                         lambda n=n: self.loadRecent(n-1))
            self.mainWin.menuOpenRecent.addAction(a)
            n += 1

    def loadRecent(self, n):
        self.loadDeck(self.config['recentDeckPaths'][n])

    # New files, loading & saving
    ##########################################################################

    def onClose(self):
        if self.inMainWindow():
            isCram = self.isCramming()
            self.saveAndClose(hideWelcome=isCram)
            if isCram:
                self.loadRecent(0)
        else:
            self.app.activeWindow().close()

    def saveAndClose(self, hideWelcome=False):
        "(Auto)save and close. Prompt if necessary. True if okay to proceed."
        self.hideWelcome = hideWelcome
        self.closeAllDeckWindows()
        if self.deck is not None:
            if self.deck.reviewEarly:
                self.deck.resetAfterReviewEarly()
            if self.deck.modifiedSinceSave():
                if (self.deck.path is None or
                    (not self.config['saveOnClose'] and
                     not self.config['syncOnClose'])):
                    # backed in memory or autosave/sync off, must confirm
                    while 1:
                        res = ui.unsaved.ask(self)
                        if res == ui.unsaved.save:
                            if self.save(required=True):
                                break
                        elif res == ui.unsaved.cancel:
                            return False
                        else:
                            break
            # auto sync (saving automatically)
            if self.config['syncOnClose'] and self.deck.syncName:
                # force save, the user may not have set passwd/etc
                self.deck.save()
                if self.syncDeck(False, reload=False):
                    while self.deckPath:
                        self.app.processEvents()
                        time.sleep(0.1)
                    return True
            # auto save
            if self.config['saveOnClose'] or self.config['syncOnClose']:
                self.save()
            # close
            self.deck.rollback()
            self.deck.close()
            self.deck = None
        if not hideWelcome:
            self.moveToState("noDeck")
        return True

    def inMainWindow(self):
        return self.app.activeWindow() == self

    def onNew(self, initial=False, path=None):
        if not self.inMainWindow() and not path: return
        if not self.saveAndClose(hideWelcome=True): return
        if initial:
            path = os.path.join(self.documentDir, "mydeck.anki")
            if os.path.exists(path):
                # load mydeck instead
                return self.loadDeck(path)
        self.deck = DeckStorage.Deck(path)
        self.deck.initUndo()
        self.deck.addModel(BasicModel())
        self.deck.save()
        self.moveToState("initial")

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
        if not self.inMainWindow(): return
        self.ensureSyncParams()
        if not self.saveAndClose(hideWelcome=True): return
        # we need a disk-backed file for syncing
        dir = unicode(tempfile.mkdtemp(prefix="anki"), sys.getfilesystemencoding())
        path = os.path.join(dir, u"untitled.anki")
        self.onNew(path=path)
        # ensure all changes come to us
        self.deck.modified = 0
        self.deck.s.commit()
        self.deck.syncName = u"something"
        self.deck.lastLoaded = self.deck.modified
        if self.config['syncUsername'] and self.config['syncPassword']:
            if self.syncDeck(onlyMerge=True, reload=2, interactive=False):
                return
        self.deck = None
        self.moveToState("initial")

    def onGetSharedDeck(self):
        if not self.inMainWindow(): return
        if not self.saveAndClose(hideWelcome=True): return
        s = ui.getshared.GetShared(self, 0)
        if not s.ok:
            self.deck = None
            self.moveToState("initial")

    def onGetSharedPlugin(self):
        if not self.inMainWindow(): return
        ui.getshared.GetShared(self, 1)

    def onOpen(self):
        if not self.inMainWindow(): return
        key = _("Deck files (*.anki)")
        defaultDir = self.getDefaultDir()
        file = QFileDialog.getOpenFileName(self, _("Open deck"),
                                           defaultDir, key)
        file = unicode(file)
        if not file:
            return False
        ret = self.loadDeck(file, interactive=True)
        if not ret:
            if ret is None:
                ui.utils.showWarning(_("Unable to load file."))
            self.deck = None
            return False
        else:
            self.updateRecentFiles(file)
            return True

    def showToolTip(self, msg):
        t = QTimer(self)
        t.setSingleShot(True)
        t.start(200)
        self.connect(t, SIGNAL("timeout()"),
                     lambda msg=msg: self._showToolTip(msg))

    def _showToolTip(self, msg):
        QToolTip.showText(
            self.mainWin.statusbar.mapToGlobal(QPoint(0, -40)),
            """\
<table cellpadding=10>
<tr>
<td><img src=":/icons/help-hint.png"></td>
<td>%s</td>
</tr>
</table>""" % msg)

    def save(self, required=False):
        if not self.deck.path:
            if required:
                # backed in memory, make sure it's saved
                return self.onSaveAs()
            else:
                self.showToolTip(_("""\
<h1>Unsaved Deck</h1>
Careful. You're editing an unsaved Deck.<br>
Choose File -> Save to start autosaving<br>
your deck."""))
            return
        if not self.deck.modifiedSinceSave():
            return True
        self.deck.save()
        self.updateTitleBar()
        return True

    def onSave(self):
        self.save(required=True)

    def onSaveAs(self):
        "Prompt for a file name, then save."
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
            if not ui.utils.askUser(
                "This file exists. Are you sure you want to overwrite it?"):
                return
        self.deck = self.deck.saveAs(file)
        self.deck.initUndo()
        self.updateTitleBar()
        self.updateRecentFiles(self.deck.path)
        self.moveToState("initial")
        return file

    # Opening and closing the app
    ##########################################################################

    def prepareForExit(self):
        "Save config and window geometry."
        runHook("quit")
        self.help.hide()
        self.config['mainWindowGeom'] = self.saveGeometry()
        self.config['mainWindowState'] = self.saveState()
        # save config
        try:
            self.config.save()
        except (IOError, OSError), e:
            ui.utils.showWarning(_("Anki was unable to save your "
                                   "configuration file:\n%s" % e))

    def closeEvent(self, event):
        "User hit the X button, etc."
        if self.state == "editCurrentFact":
            event.ignore()
            return self.moveToState("saveEdit")
        if not self.saveAndClose(hideWelcome=True):
            event.ignore()
        else:
            self.prepareForExit()
            event.accept()
            self.app.quit()

    # Anchor clicks
    ##########################################################################

    def onWelcomeAnchor(self, str):
        if str == "new":
            self.onNew()
        elif str == "open":
            self.onOpen()
        elif str == "sample":
            self.onGetSharedDeck()
        elif str == "openrem":
            self.onOpenOnline()
        if str == "addfacts":
            if not self.deck:
                self.onNew()
            if self.deck:
                self.onAddCard()

    def setupAnchors(self):
        # welcome
        self.anchorPrefixes = {
            'welcome': self.onWelcomeAnchor,
            }
        self.connect(self.mainWin.welcomeText,
                     SIGNAL("anchorClicked(QUrl)"),
                     self.anchorClicked)
        # main
        self.mainWin.mainText.page().setLinkDelegationPolicy(
            QWebPage.DelegateExternalLinks)
        self.connect(self.mainWin.mainText,
                     SIGNAL("linkClicked(QUrl)"),
                     self.linkClicked)

    def anchorClicked(self, url):
        # prevent the link being handled
        self.mainWin.welcomeText.setSource(QUrl(""))
        addr = unicode(url.toString())
        fields = addr.split(":")
        if len(fields) > 1 and fields[0] in self.anchorPrefixes:
            self.anchorPrefixes[fields[0]](*fields[1:])
        else:
            # open in browser
            QDesktopServices.openUrl(QUrl(url))

    def linkClicked(self, url):
        QDesktopServices.openUrl(QUrl(url))

    # Edit current fact
    ##########################################################################

    def setupEditor(self):
        self.editor = ui.facteditor.FactEditor(
            self, self.mainWin.fieldsArea, self.deck)
        self.editor.onFactValid = self.onFactValid
        self.editor.onFactInvalid = self.onFactInvalid
        # editor
        self.connect(self.mainWin.saveEditorButton, SIGNAL("clicked()"),
                     lambda: self.moveToState("saveEdit"))


    def showEditor(self):
        self.mainWin.buttonStack.hide()
        self.switchToEditScreen()
        self.editor.setFact(self.currentCard.fact)

    def onFactValid(self, fact):
        self.mainWin.saveEditorButton.setEnabled(True)

    def onFactInvalid(self, fact):
        self.mainWin.saveEditorButton.setEnabled(False)

    # Study screen
    ##########################################################################

    def setupStudyScreen(self):
        self.mainWin.newCardOrder.insertItems(
            0, QStringList(newCardOrderLabels().values()))
        self.mainWin.newCardScheduling.insertItems(
            0, QStringList(newCardSchedulingLabels().values()))
        self.mainWin.revCardOrder.insertItems(
            0, QStringList(revCardOrderLabels().values()))
        self.connect(self.mainWin.optionsHelpButton,
                     SIGNAL("clicked()"),
                     lambda: QDesktopServices.openUrl(QUrl(
            ankiqt.appWiki + "StudyOptions")))
        self.mainWin.optionsBox.setShown(False)
        self.connect(self.mainWin.minuteLimit,
                     SIGNAL("textChanged(QString)"), self.onMinuteLimitChanged)
        self.connect(self.mainWin.newPerDay,
                     SIGNAL("textChanged(QString)"), self.onNewLimitChanged)

    def onMinuteLimitChanged(self, qstr):
        try:
            val = float(self.mainWin.minuteLimit.text()) * 60
            if self.deck.sessionTimeLimit == val:
                return
            self.deck.sessionTimeLimit = val
        except ValueError:
            pass
        self.deck.flushMod()
        self.updateStudyStats()

    def onNewLimitChanged(self, qstr):
        try:
            val = int(self.mainWin.newPerDay.text())
            if self.deck.newCardsPerDay == val:
                return
            self.deck.newCardsPerDay = val
        except ValueError:
            pass
        self.deck.checkDue()
        self.deck.flushMod()
        self.statusView.redraw()
        self.updateStudyStats()

    def updateStudyStats(self):
        wasReached = self.deck.sessionLimitReached()
        sessionColour = '<font color=#0000ff>%s</font>'
        cardColour = '<font color=#0000ff>%s</font>'
        if not wasReached:
            top = _("<h1>Study Options</h1>")
        else:
            top = _("<h1>Well done!</h1>")
        # top label
        h = {}
        s = self.deck.getStats()
        h['ret'] = cardColour % (s['rev']+s['failed'])
        h['new'] = cardColour % s['new']
        h['newof'] = str(self.deck.newCountAll())
        dtoday = s['dTotal']
        yesterday = self.deck._dailyStats.day - datetime.timedelta(1)
        res = self.deck.s.first("""
select reps, reviewTime from stats where type = 1 and
day = :d""", d=yesterday)
        if res:
            (dyest, tyest) = res
        else:
            dyest = 0; tyest = 0
        h['repsToday'] = sessionColour % dtoday
        h['repsTodayChg'] = str(dyest)
        limit = self.deck.sessionTimeLimit
        start = self.deck.sessionStartTime or time.time() - limit
        start2 = self.deck.lastSessionStart or start - limit
        last10 = self.deck.s.scalar(
            "select count(*) from reviewHistory where time >= :t",
            t=start)
        last20 = self.deck.s.scalar(
            "select count(*) from reviewHistory where "
            "time >= :t and time < :t2",
            t=start2, t2=start)
        h['repsInSes'] = sessionColour % last10
        h['repsInSesChg'] = str(last20)
        ttoday = s['dReviewTime']
        h['timeToday'] = sessionColour % (
            anki.utils.fmtTimeSpan(ttoday, short=True, point=1))
        h['timeTodayChg'] = str(anki.utils.fmtTimeSpan(
            tyest, short=True, point=1))
        h['cs_header'] = _("Cards/session:")
        h['cd_header'] = _("Cards/day:")
        h['td_header'] = _("Time/day:")
        h['rd_header'] = _("Reviews due:")
        h['ntod_header'] = _("New today:")
        h['ntot_header'] = _("New total:")
        stats1 = ("""\
<table>
<tr><td width=80>%(cs_header)s</td><td width=50><b>%(repsInSesChg)s</b></td>
<td><b>%(repsInSes)s</b></td></tr>
 <tr><td>%(cd_header)s</td><td><b>%(repsTodayChg)s</b></td>
<td><b>%(repsToday)s</b></td></tr>
<tr><td>%(td_header)s</td><td><b>%(timeTodayChg)s</b></td>
<td><b>%(timeToday)s</b></td></tr>
</table>""") % h

        stats2 = ("""\
<table>
<tr><td width=100>%(rd_header)s</td><td align=right><b>%(ret)s</b></td></tr>
<tr><td>%(ntod_header)s</td><td align=right><b>%(new)s</b></td></tr>
<tr><td>%(ntot_header)s</td><td align=right>%(newof)s</td></tr>
</table>""") % h
        if (not dyest and not dtoday) or not self.config['showStudyStats']:
            stats1 = ""
        else:
            stats1 = (
                "<td>%s</td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td>" % stats1)
        self.mainWin.optionsLabel.setText(top + """\
<p><table><tr>
%s
<td>%s</td></tr></table>""" % (stats1, stats2))
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

        self.mainWin.optionsLabel.setToolTip(statToolTip)

    def showStudyScreen(self):
        # forget last card
        self.lastCard = None
        self.mainWin.optionsButton.setChecked(self.config['showStudyOptions'])
        self.mainWin.optionsBox.setShown(self.config['showStudyOptions'])
        self.switchToStudyScreen()
        self.updateStudyStats()
        # start reviewing button
        self.mainWin.buttonStack.hide()
        if self.reviewingStarted:
            self.mainWin.startReviewingButton.setText(_("Continue &Reviewing"))
        else:
            self.mainWin.startReviewingButton.setText(_("Start &Reviewing"))
        self.mainWin.startReviewingButton.setFocus()
        self.connect(self.mainWin.startReviewingButton,
                     SIGNAL("clicked()"),
                     self.onStartReview)
        self.setupStudyOptions()
        self.mainWin.studyOptionsFrame.show()

    def setupStudyOptions(self):
        self.mainWin.newPerDay.setText(str(self.deck.newCardsPerDay))
        lim = self.deck.sessionTimeLimit/60
        if int(lim) == lim:
            lim = int(lim)
        self.mainWin.minuteLimit.setText(str(lim))
        self.mainWin.questionLimit.setText(str(self.deck.sessionRepLimit))
        self.mainWin.newCardOrder.setCurrentIndex(self.deck.newCardOrder)
        self.mainWin.newCardScheduling.setCurrentIndex(self.deck.newCardSpacing)
        self.mainWin.revCardOrder.setCurrentIndex(self.deck.revCardOrder)
        self.mainWin.failedCardsOption.clear()
        if self.deck.getFailedCardPolicy() == 5:
            labels = failedCardOptionLabels().values()
        else:
            labels = failedCardOptionLabels().values()[0:-1]
        self.mainWin.failedCardsOption.insertItems(0, labels)
        self.mainWin.failedCardsOption.setCurrentIndex(self.deck.getFailedCardPolicy())

    def onStartReview(self):
        def uf(obj, field, value):
            if getattr(obj, field) != value:
                setattr(obj, field, value)
                self.deck.flushMod()
        self.mainWin.studyOptionsFrame.hide()
        # make sure the size is updated before button stack shown
        self.app.processEvents()
        self.config['showStudyOptions'] = self.mainWin.optionsButton.isChecked()
        try:
            uf(self.deck, 'newCardsPerDay', int(self.mainWin.newPerDay.text()))
            uf(self.deck, 'sessionTimeLimit', min(float(
                self.mainWin.minuteLimit.text()), 3600) * 60)
            uf(self.deck, 'sessionRepLimit',
               int(self.mainWin.questionLimit.text()))
        except (ValueError, OverflowError):
            pass
        ncOrd = self.mainWin.newCardOrder.currentIndex()
        if self.deck.newCardOrder != ncOrd:
            if self.deck.newCardOrder == 0 and ncOrd != 0:
                # random to non-random
                self.deck.startProgress()
                self.deck.updateProgress(_("Ordering..."))
                self.deck.orderNewCards()
                self.deck.finishProgress()
            elif self.deck.newCardOrder != 0 and ncOrd == 0:
                # non-random to random
                self.deck.startProgress()
                self.deck.updateProgress(_("Randomizing..."))
                self.deck.randomizeNewCards()
                self.deck.finishProgress()
        uf(self.deck, 'newCardOrder', ncOrd)
        uf(self.deck, 'newCardSpacing',
           self.mainWin.newCardScheduling.currentIndex())
        uf(self.deck, 'revCardOrder',
           self.mainWin.revCardOrder.currentIndex())
        if (self.deck.getFailedCardPolicy() !=
            self.mainWin.failedCardsOption.currentIndex()):
            self.deck.setFailedCardPolicy(
                self.mainWin.failedCardsOption.currentIndex())
            self.deck.flushMod()
        self.deck.rebuildQueue()
        self.deck.startSession()
        self.moveToState("getQuestion")

    def onStudyOptions(self):
        if self.state == "studyScreen":
            pass
        else:
            self.moveToState("studyScreen")

    # Toolbar
    ##########################################################################

    def setupToolbar(self):
        mw = self.mainWin
        if self.config['simpleToolbar']:
            self.removeToolBar(mw.toolBar)
            mw.toolBar.hide()
            mw.toolBar = QToolBar(self)
            mw.toolBar.setObjectName("toolBar")
            mw.toolBar.addAction(mw.actionAddcards)
            mw.toolBar.addAction(mw.actionEditCurrent)
            mw.toolBar.addAction(mw.actionEditdeck)
            mw.toolBar.addAction(mw.actionStudyOptions)
            mw.toolBar.addAction(mw.actionGraphs)
            mw.toolBar.addAction(mw.actionMarkCard)
            mw.toolBar.addAction(mw.actionRepeatAudio)
            self.addToolBar(Qt.TopToolBarArea, mw.toolBar)
        mw.toolBar.setIconSize(QSize(self.config['iconSize'],
                                     self.config['iconSize']))
        toggle = mw.toolBar.toggleViewAction()
        toggle.setText(_("Toggle Toolbar"))
        self.connect(toggle, SIGNAL("triggered()"),
                     self.onToolbarToggle)
        if not self.config['showToolbar']:
            mw.toolBar.hide()

    def onToolbarToggle(self):
        tb = self.mainWin.toolBar
        self.config['showToolbar'] = tb.isVisible()

    # Tools - looking up words in the dictionary
    ##########################################################################

    def initLookup(self):
        if not getattr(self, "lookup", None):
            self.lookup = ui.lookup.Lookup(self)

    def onLookupExpression(self):
        self.initLookup()
        try:
            self.lookup.alc(self.currentCard.fact['Expression'])
        except KeyError:
            ui.utils.showInfo(_("No expression in current card."))

    def onLookupMeaning(self):
        self.initLookup()
        try:
            self.lookup.alc(self.currentCard.fact['Meaning'])
        except KeyError:
            ui.utils.showInfo(_("No meaning in current card."))

    def onLookupEdictSelection(self):
        self.initLookup()
        self.lookup.selection(self.lookup.edict)

    def onLookupEdictKanjiSelection(self):
        self.initLookup()
        self.lookup.selection(self.lookup.edictKanji)

    def onLookupAlcSelection(self):
        self.initLookup()
        self.lookup.selection(self.lookup.alc)

    # Tools - statistics
    ##########################################################################

    def onKanjiStats(self):
        rep = anki.stats.KanjiStats(self.deck).report()
        rep += _("<a href=py:miss>Missing</a><br>")
        rep += _("<a href=py:seen>Seen</a><br>")
        rep += _("<a href=py:non>Non-jouyou</a><br>")
        self.help.showText(rep, py={
            "miss": self.onMissingStats,
            "seen": self.onSeenKanjiStats,
            "non": self.onNonJouyouKanjiStats,
            })

    def onMissingStats(self):
        ks = anki.stats.KanjiStats(self.deck)
        ks.genKanjiSets()
        self.help.showText(ks.missingReport())

    def onSeenKanjiStats(self):
        ks = anki.stats.KanjiStats(self.deck)
        ks.genKanjiSets()
        self.help.showText(ks.seenReport())

    def onNonJouyouKanjiStats(self):
        ks = anki.stats.KanjiStats(self.deck)
        ks.genKanjiSets()
        self.help.showText(ks.nonJouyouReport())

    def onDeckStats(self):
        txt = anki.stats.DeckStats(self.deck).report()
        self.help.showText(txt)

    def onCardStats(self):
        addHook("showQuestion", self.onCardStats)
        txt = ""
        if self.currentCard:
            txt += _("<h1>Current card</h1>")
            txt += anki.stats.CardStats(self.deck, self.currentCard).report()
        if self.lastCard and self.lastCard != self.currentCard:
            txt += _("<h1>Last card</h1>")
            txt += anki.stats.CardStats(self.deck, self.lastCard).report()
        if not txt:
            txt = _("No current card or last card.")
        self.help.showText(txt, py={'hide': self.removeCardStatsHook})

    def removeCardStatsHook(self):
        "Remove the update hook if the help menu was changed."
        removeHook("showQuestion", self.onCardStats)

    def onShowGraph(self):
        self.setStatus(_("Loading graphs (may take time)..."))
        self.app.processEvents()
        import anki.graphs
        if anki.graphs.graphsAvailable():
            try:
                ui.dialogs.get("Graphs", self, self.deck)
            except (ImportError, ValueError):
                traceback.print_exc()
                if sys.platform.startswith("win32"):
                    ui.utils.showInfo(
                        _("To display graphs, Anki needs a .dll file which\n"
                          "you don't have. Please install:\n") +
                        "http://www.dll-files.com/dllindex/dll-files.shtml?msvcp71")
                else:
                    ui.utils.showInfo(_(
                        "Your version of Matplotlib is broken.\n"
                        "Please see http://ichi2.net/anki/wiki/MatplotlibBroken"))
        else:
            ui.utils.showInfo(_("Please install python-matplotlib to access graphs."))

    # Marking, suspending and undoing
    ##########################################################################

    def onMark(self, toggled):
        if self.currentCard.hasTag("Marked"):
            self.currentCard.fact.tags = canonifyTags(deleteTags(
                "Marked", self.currentCard.fact.tags))
        else:
            self.currentCard.fact.tags = canonifyTags(addTags(
                "Marked", self.currentCard.fact.tags))
        self.currentCard.fact.setModified(textChanged=True)
        self.deck.updateFactTags([self.currentCard.fact.id])
        self.deck.setModified()

    def onSuspend(self):
        undo = _("Suspend")
        self.deck.setUndoStart(undo)
        self.currentCard.fact.tags = canonifyTags(
            addTags("Suspended", self.currentCard.fact.tags))
        self.currentCard.fact.setModified(textChanged=True)
        self.deck.updateFactTags([self.currentCard.fact.id])
        for card in self.currentCard.fact.cards:
            self.deck.updatePriority(card)
        self.deck.setModified()
        self.lastScheduledTime = None
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
        for card in self.currentCard.fact.cards:
            card.priority = -2
            card.isDue = 0
        self.deck.flushMod()
        self.reset()
        self.deck.setUndoEnd(undo)

    def onUndo(self):
        self.deck.undo()
        self.reset(count=False)

    def onRedo(self):
        self.deck.redo()
        self.reset(count=False)

    # Other menu operations
    ##########################################################################

    def onAddCard(self):
        if self.isCramming():
            ui.utils.showInfo(_("""\
You are currently cramming. Please close this deck first."""))
            return
        ui.dialogs.get("AddCards", self)

    def onEditDeck(self):
        ui.dialogs.get("CardList", self)
        if self.isCramming():
            self.showToolTip(_("""\
<h1>Cramming</h1>
You are currently cramming. Any edits you make to this deck
will be lost when you close the deck."""))

    def onEditCurrent(self):
        self.moveToState("editCurrentFact")

    def onDeckProperties(self):
        self.deckProperties = ui.deckproperties.DeckProperties(self, self.deck)

    def onDisplayProperties(self):
        ui.dialogs.get("DisplayProperties", self)

    def onPrefs(self):
        ui.preferences.Preferences(self, self.config)

    def onReportBug(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appIssueTracker))

    def onForum(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appForum))

    def onReleaseNotes(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appReleaseNotes))

    def onAbout(self):
        ui.about.show(self)

    def onDonate(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appDonate))

    def onActiveTags(self):
        ui.activetags.show(self)

    # Importing & exporting
    ##########################################################################

    def onImport(self):
        if self.isCramming():
            ui.utils.showInfo(_("""\
You are currently cramming. Please close this deck first."""))
            return
        if self.deck is None:
            self.onNew()
        ui.importing.ImportDialog(self)

    def onExport(self):
        ui.exporting.ExportDialog(self)

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

    def isCramming(self):
        return self.deck is not None and self.deck.name() == "cram"

    def onCram(self, cardIds=[]):
        if self.isCramming():
            ui.utils.showInfo(
                _("Already cramming. Please close this deck first."))
            return
        if not self.save(required=True):
            return
        if not cardIds:
            (s, ret) = ui.utils.getTag(self, self.deck, _("Tags to cram:"),
                                       help="CramMode", tags="all")
            if not ret:
                return
            s = unicode(s)
            # open tmp deck
            (e, path) = self._copyToTmpDeck(tags=s)
        else:
            (e, path) = self._copyToTmpDeck(ids=cardIds)
        if not e.exportedCards:
            ui.utils.showInfo(_("No cards matched the provided tags."))
            return
        if self.config['randomizeOnCram']:
            n = 3
        else:
            n = 2
        p = ui.utils.ProgressWin(self, n, 0, _("Cram"))
        p.update(_("Loading deck..."))
        oldMedia = self.deck.mediaDir()
        self.deck.close()
        self.deck = None
        self.loadDeck(path, media=oldMedia)
        self.config['recentDeckPaths'].pop(0)
        self.deck.newCardsPerDay = 99999
        self.deck.delay0 = 300
        self.deck.delay1 = 600
        self.deck.hardIntervalMin = 0.01388
        self.deck.hardIntervalMax = 0.02083
        self.deck.midIntervalMin = 0.0416
        self.deck.midIntervalMax = 0.0486
        self.deck.easyIntervalMin = 0.2083
        self.deck.easyIntervalMax = 0.25
        self.deck.newCardOrder = 0
        self.deck.syncName = None
        p.update()
        self.deck.updateDynamicIndices()
        if self.config['randomizeOnCram']:
            p.update(_("Randomizing..."))
            self.deck.randomizeNewCards()
        self.reset()
        p.finish()

    def onShare(self, tags):
        pwd = os.getcwd()
        # open tmp deck
        (e, path) = self._copyToTmpDeck(name="shared.anki", tags=tags)
        if not e.exportedCards:
            ui.utils.showInfo(_("No cards matched the provided tags."))
            return
        self.deck.startProgress()
        self.deck.updateProgress()
        d = DeckStorage.Deck(path)
        # reset scheduling to defaults
        d.newCardsPerDay = 20
        d.delay0 = 600
        d.delay1 = 600
        d.delay2 = 0
        d.hardIntervalMin = 0.333
        d.hardIntervalMax = 0.5
        d.midIntervalMin = 3.0
        d.midIntervalMax = 5.0
        d.easyIntervalMin = 7.0
        d.easyIntervalMax = 9.0
        d.syncName = None
        d.suspended = u"Suspended"
        self.deck.updateProgress()
        d.updateAllPriorities()
        d.utcOffset = -1
        d.flushMod()
        d.save()
        self.deck.updateProgress()
        # remove indices
        indices = d.s.column0(
            "select name from sqlite_master where type = 'index' "
            "and sql != ''")
        for i in indices:
            d.s.statement("drop index %s" % i)
        # and q/a cache
        d.s.statement("update cards set question = '', answer = ''")
        self.deck.updateProgress()
        d.s.statement("vacuum")
        self.deck.updateProgress()
        nfacts = d.factCount
        mdir = d.mediaDir()
        d.close()
        dir = os.path.dirname(path)
        zippath = os.path.join(dir, "shared-%d.zip" % time.time())
        # zip it up
        zip = zipfile.ZipFile(zippath, "w", zipfile.ZIP_DEFLATED)
        zip.writestr("facts", str(nfacts))
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
        zip.write(readmep, "README.txt")
        zip.write(path, "shared.anki")
        if mdir:
            for f in os.listdir(mdir):
                zip.write(os.path.join(mdir, f),
                          str(os.path.join("shared.media/", f)))
            os.chdir(pwd)
            shutil.rmtree(mdir)
        os.chdir(pwd)
        self.deck.updateProgress()
        zip.close()
        os.unlink(path)
        self.deck.finishProgress()
        self.onOpenPluginFolder(dir)

    # Reviewing and learning ahead
    ##########################################################################

    def onLearnMore(self):
        self.deck.newEarly = True
        self.reset()
        self.showToolTip(_("""\
<h1>Learning More</h1>Click the clock in the toolbar to finish."""))

    def onReviewEarly(self):
        self.deck.reviewEarly = True
        self.reset()
        self.showToolTip(_("""\
<h1>Reviewing Early</h1>Click the clock in the toolbar to finish."""))

    # Language handling
    ##########################################################################

    def setLang(self):
        "Set the user interface language."
        locale.setlocale(locale.LC_ALL, '')
        languageDir=os.path.join(ankiqt.modDir, "locale")
        self.languageTrans = gettext.translation('ankiqt', languageDir,
                                            languages=[self.config["interfaceLang"]],
                                            fallback=True)
        self.installTranslation()
        if getattr(self, 'mainWin', None):
            self.mainWin.retranslateUi(self)
        anki.lang.setLang(self.config["interfaceLang"], local=False)
        self.updateTitleBar()

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

    def syncDeck(self, interactive=True, create=False, onlyMerge=False,
                 reload=True, checkSources=True):
        "Synchronise a deck with the server."
        if not self.inMainWindow() and interactive: return
        # vet input
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
                self.onDeckProperties()
                self.deckProperties.dialog.qtabwidget.setCurrentIndex(1)
            return
        if self.deck is None and self.deckPath is None:
            # qt on linux incorrectly accepts shortcuts for disabled actions
            return
        # hide all deck-associated dialogs
        self.closeAllDeckWindows()
        if self.deck:
            # save first, so we can rollback on failure
            self.deck.save()
            # store data we need before closing the deck
            self.deckPath = self.deck.path
            self.syncName = self.deck.syncName or self.deck.name()
            self.lastSync = self.deck.lastSync
            if checkSources:
                self.sourcesToCheck = self.deck.s.column0(
                    "select id from sources where syncPeriod != -1 "
                    "and syncPeriod = 0 or :t - lastSync > syncPeriod",
                    t=time.time())
            else:
                self.sourcesToCheck = []
            self.deck.close()
            self.deck = None
            self.loadAfterSync = reload
        # bug triggered by preferences dialog - underlying c++ widgets are not
        # garbage collected until the middle of the child thread
        import gc; gc.collect()
        self.mainWin.welcomeText.setText(u"")
        self.syncThread = ui.sync.Sync(self, u, p, interactive, create,
                                       onlyMerge, self.sourcesToCheck)
        self.connect(self.syncThread, SIGNAL("setStatus"), self.setSyncStatus)
        self.connect(self.syncThread, SIGNAL("showWarning"), self.showSyncWarning)
        self.connect(self.syncThread, SIGNAL("noSyncResponse"), self.noSyncResponse)
        self.connect(self.syncThread, SIGNAL("moveToState"), self.moveToState)
        self.connect(self.syncThread, SIGNAL("noMatchingDeck"), self.selectSyncDeck)
        self.connect(self.syncThread, SIGNAL("syncClockOff"), self.syncClockOff)
        self.connect(self.syncThread, SIGNAL("cleanNewDeck"), self.cleanNewDeck)
        self.connect(self.syncThread, SIGNAL("syncFinished"), self.syncFinished)
        self.connect(self.syncThread, SIGNAL("openSyncProgress"), self.openSyncProgress)
        self.connect(self.syncThread, SIGNAL("closeSyncProgress"), self.closeSyncProgress)
        self.connect(self.syncThread, SIGNAL("updateSyncProgress"), self.updateSyncProgress)
        self.connect(self.syncThread, SIGNAL("bulkSyncFailed"), self.bulkSyncFailed)
        self.syncThread.start()
        self.switchToWelcomeScreen()
        self.setEnabled(False)
        while not self.syncThread.isFinished():
            self.app.processEvents()
            self.syncThread.wait(100)
        self.setEnabled(True)
        return self.syncThread.ok

    def syncFinished(self):
        "Reopen after sync finished."
        self.mainWin.buttonStack.show()
        if self.loadAfterSync:
            uprecent = self.loadAfterSync != 2
            self.loadDeck(self.deckPath, sync=False, uprecent=uprecent)
            self.deck.syncName = self.syncName
            self.deck.s.flush()
            self.deck.s.commit()
            if self.loadAfterSync == 2:
                # ugly hack for open online: mark temp deck as in-memory
                self.deck.tmpMediaDir = re.sub(
                    "(?i)\.(anki)$", ".media", self.deck.path)
                self.deck.path = None
                self.deck.flushMod()
                if not self.onSaveAs():
                    self.deck = None
                    self.moveToState("noDeck")
        elif not self.hideWelcome:
            self.moveToState("noDeck")
        self.deckPath = None

    def selectSyncDeck(self, decks, create=True):
        name = ui.sync.DeckChooser(self, decks, create).getName()
        self.syncName = name
        if name:
            # name chosen
            onlyMerge = self.loadAfterSync == 2
            self.syncDeck(create=True, interactive=False, onlyMerge=onlyMerge)
        else:
            if not create:
                self.cleanNewDeck()
            else:
                self.syncFinished()

    def cleanNewDeck(self):
        "Unload a new deck if an initial sync failed."
        self.deck = None
        self.moveToState("initial")

    def setSyncStatus(self, text, *args):
        self.mainWin.welcomeText.append("<font size=+2>" + text + "</font>")

    def syncClockOff(self, diff):
        ui.utils.showWarning(
            _("Your computer clock is not set to the correct time.\n") +
            ngettext("It is off by %d second.\n\n",
                "It is off by %d seconds.\n\n", diff) % diff +
            _("Since this can cause many problems with syncing,\n"
              "syncing is disabled until you fix the problem.")
            )
        self.syncFinished()

    def showSyncWarning(self, text):
        ui.utils.showWarning(text, self)
        self.setStatus("")

    def noSyncResponse(self):
        msg = _("""\
<h1>Sync Failed</h1>
Couldn't contact Anki Online. Please check your internet connection.""")
        if self.config['syncInMsgBox']:
            ui.utils.showWarning(msg)
        else:
            self.showToolTip(msg)

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
        ui.utils.showWarning(_(
            "Failed to upload media. Please run 'check media db'."), self)

    # Menu, title bar & status
    ##########################################################################

    deckRelatedMenuItems = (
        "Save",
        "SaveAs",
        "Close",
        "Addcards",
        "Editdeck",
        "Syncdeck",
        "DisplayProperties",
        "DeckProperties",
        "Undo",
        "Redo",
        "Export",
        "Graphs",
        "Dstats",
        "Kstats",
        "Cstats",
        "ActiveTags",
        "StudyOptions",
        )

    deckRelatedMenus = (
        "Tools",
        )

    def connectMenuActions(self):
        m = self.mainWin
        s = SIGNAL("triggered()")
        self.connect(m.actionNew, s, self.onNew)
        self.connect(m.actionOpenOnline, s, self.onOpenOnline)
        self.connect(m.actionDownloadSharedDeck, s, self.onGetSharedDeck)
        self.connect(m.actionDownloadSharedPlugin, s, self.onGetSharedPlugin)
        self.connect(m.actionOpen, s, self.onOpen)
        self.connect(m.actionSave, s, self.onSave)
        self.connect(m.actionSaveAs, s, self.onSaveAs)
        self.connect(m.actionShare, s, self.onShare)
        self.connect(m.actionClose, s, self.onClose)
        self.connect(m.actionExit, s, self, SLOT("close()"))
        self.connect(m.actionSyncdeck, s, self.syncDeck)
        self.connect(m.actionDeckProperties, s, self.onDeckProperties)
        self.connect(m.actionDisplayProperties, s,self.onDisplayProperties)
        self.connect(m.actionAddcards, s, self.onAddCard)
        self.connect(m.actionEditdeck, s, self.onEditDeck)
        self.connect(m.actionEditCurrent, s, self.onEditCurrent)
        self.connect(m.actionPreferences, s, self.onPrefs)
        self.connect(m.actionLookup_es, s, self.onLookupEdictSelection)
        self.connect(m.actionLookup_esk, s, self.onLookupEdictKanjiSelection)
        self.connect(m.actionLookup_expr, s, self.onLookupExpression)
        self.connect(m.actionLookup_mean, s, self.onLookupMeaning)
        self.connect(m.actionLookup_as, s, self.onLookupAlcSelection)
        self.connect(m.actionDstats, s, self.onDeckStats)
        self.connect(m.actionKstats, s, self.onKanjiStats)
        self.connect(m.actionCstats, s, self.onCardStats)
        self.connect(m.actionGraphs, s, self.onShowGraph)
        self.connect(m.actionAbout, s, self.onAbout)
        self.connect(m.actionReportbug, s, self.onReportBug)
        self.connect(m.actionForum, s, self.onForum)
        self.connect(m.actionStarthere, s, self.onStartHere)
        self.connect(m.actionImport, s, self.onImport)
        self.connect(m.actionExport, s, self.onExport)
        self.connect(m.actionMarkCard, SIGNAL("toggled(bool)"), self.onMark)
        self.connect(m.actionSuspendCard, s, self.onSuspend)
        self.connect(m.actionDelete, s, self.onDelete)
        self.connect(m.actionRepeatAudio, s, self.onRepeatAudio)
        self.connect(m.actionUndo, s, self.onUndo)
        self.connect(m.actionRedo, s, self.onRedo)
        self.connect(m.actionCheckDatabaseIntegrity, s, self.onQuickCheckDB)
        self.connect(m.actionFullDatabaseCheck, s, self.onCheckDB)
        self.connect(m.actionOptimizeDatabase, s, self.onOptimizeDB)
        self.connect(m.actionCheckMediaDatabase, s, self.onCheckMediaDB)
        self.connect(m.actionCram, s, self.onCram)
        self.connect(m.actionGetPlugins, s, self.onGetPlugins)
        self.connect(m.actionOpenPluginFolder, s, self.onOpenPluginFolder)
        self.connect(m.actionEnableAllPlugins, s, self.onEnableAllPlugins)
        self.connect(m.actionDisableAllPlugins, s, self.onDisableAllPlugins)
        self.connect(m.actionActiveTags, s, self.onActiveTags)
        self.connect(m.actionReleaseNotes, s, self.onReleaseNotes)
        self.connect(m.actionCacheLatex, s, self.onCacheLatex)
        self.connect(m.actionUncacheLatex, s, self.onUncacheLatex)
        self.connect(m.actionStudyOptions, s, self.onStudyOptions)
        self.connect(m.actionDonate, s, self.onDonate)
        self.connect(m.actionRecordNoiseProfile, s, self.onRecordNoiseProfile)
        self.connect(m.actionBuryFact, s, self.onBuryFact)

    def enableDeckMenuItems(self, enabled=True):
        "setEnabled deck-related items."
        for item in self.deckRelatedMenus:
            getattr(self.mainWin, "menu" + item).setEnabled(enabled)
        for item in self.deckRelatedMenuItems:
            getattr(self.mainWin, "action" + item).setEnabled(enabled)
        if not enabled:
            self.disableCardMenuItems()

    def disableDeckMenuItems(self):
        "Disable deck-related items."
        self.enableDeckMenuItems(enabled=False)

    def updateTitleBar(self):
        "Display the current deck and card count in the titlebar."
        title=ankiqt.appName
        if self.deck != None:
            deckpath = self.deck.name()
            if self.deck.modifiedSinceSave():
                deckpath += "*"
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
        self.setWindowTitle(title)

    def setStatus(self, text, timeout=3000):
        self.mainWin.statusbar.showMessage(text, timeout)

    def onStartHere(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appHelpSite))

    def updateMarkAction(self):
        self.mainWin.actionMarkCard.blockSignals(True)
        if self.currentCard.hasTag("Marked"):
            self.mainWin.actionMarkCard.setChecked(True)
        else:
            self.mainWin.actionMarkCard.setChecked(False)
        self.mainWin.actionMarkCard.blockSignals(False)

    def disableCardMenuItems(self):
        self.maybeEnableUndo()
        self.maybeShowLookup(False)
        self.maybeShowKanjiStats()
        self.mainWin.actionEditCurrent.setEnabled(False)
	self.mainWin.actionMarkCard.setEnabled(False)
	self.mainWin.actionSuspendCard.setEnabled(False)
	self.mainWin.actionDelete.setEnabled(False)
	self.mainWin.actionBuryFact.setEnabled(False)
        self.mainWin.actionRepeatAudio.setEnabled(False)

    def enableCardMenuItems(self):
        self.maybeEnableUndo()
        self.maybeShowLookup(True)
        self.maybeShowKanjiStats()
        snd = (hasSound(self.currentCard.question) or
               (hasSound(self.currentCard.answer) and
                self.state != "getQuestion"))
        self.mainWin.actionRepeatAudio.setEnabled(snd)
	self.mainWin.actionMarkCard.setEnabled(True)
	self.mainWin.actionSuspendCard.setEnabled(True)
	self.mainWin.actionDelete.setEnabled(True)
	self.mainWin.actionBuryFact.setEnabled(True)
        enableEdits = (not self.config['preventEditUntilAnswer'] or
                       self.state != "getQuestion")
        self.mainWin.actionEditCurrent.setEnabled(enableEdits)
        self.mainWin.actionEditdeck.setEnabled(enableEdits)

    def maybeShowKanjiStats(self):
        if not self.deck:
            have = False
        else:
            if getattr(self.deck, "haveJapanese", None) is None:
                self.deck.haveJapanese = False
                if self.deck:
                    for m in self.deck.models:
                        if "Japanese" in m.tags:
                            self.deck.haveJapanese = True
                            break
            have = self.deck.haveJapanese
        self.mainWin.actionKstats.setVisible(have)

    def maybeShowLookup(self, enable):
        if (self.currentCard and
            "Japanese" in self.currentCard.fact.model.tags):
            self.mainWin.menu_Lookup.menuAction().setVisible(True)
        else:
            self.mainWin.menu_Lookup.menuAction().setVisible(False)
            enable = False
        self.mainWin.menu_Lookup.setEnabled(enable)
        self.mainWin.actionLookup_es.setEnabled(enable)
        self.mainWin.actionLookup_esk.setEnabled(enable)
        self.mainWin.actionLookup_expr.setEnabled(enable)
        self.mainWin.actionLookup_mean.setEnabled(enable)
        self.mainWin.actionLookup_as.setEnabled(enable)

    def maybeEnableUndo(self):
        if self.deck and self.deck.undoAvailable():
            self.mainWin.actionUndo.setText(_("Undo %s") %
                                            self.deck.undoName())
            self.mainWin.actionUndo.setEnabled(True)
        else:
            self.mainWin.actionUndo.setEnabled(False)
        if self.deck and self.deck.redoAvailable():
            self.mainWin.actionRedo.setText(_("Redo %s") %
                                            self.deck.redoName())
            self.mainWin.actionRedo.setEnabled(True)
        else:
            self.mainWin.actionRedo.setEnabled(False)

    # Auto update
    ##########################################################################

    def setupAutoUpdate(self):
        self.autoUpdate = ui.update.LatestVersionFinder(self)
        self.connect(self.autoUpdate, SIGNAL("newVerAvail"), self.newVerAvail)
        self.connect(self.autoUpdate, SIGNAL("clockIsOff"), self.clockIsOff)
        self.autoUpdate.start()

    def newVerAvail(self, version):
        if self.config['suppressUpdate'] < version['latestVersion']:
            ui.update.askAndUpdate(self, version)

    def clockIsOff(self, diff):
        if diff < 0:
            ret = _("late")
        else:
            ret = _("early")
        ui.utils.showWarning(
            _("Your computer clock is not set to the correct time.\n") +
            ngettext("It is %(sec)d second %(type)s.\n",
                "It is %(sec)d seconds %(type)s.\n", abs(diff))
                % {"sec": abs(diff), "type": ret} +
            _(" Please ensure it is set correctly and then restart Anki.")
         )

    def updateStarted(self):
        self.updateProgressDialog = QProgressDialog(_(
            "Updating Anki...\n - you can keep studying"
            "\n - please don't close this"), "", 0, 0, self)
        self.updateProgressDialog.setMinimum(0)
        self.updateProgressDialog.setMaximum(100)
        self.updateProgressDialog.setCancelButton(None)
        self.updateProgressDialog.setMinimumDuration(0)

    def updateDownloading(self, perc):
        self.updateProgressDialog.setValue(perc)

    def updateFinished(self):
        self.updateProgressDialog.cancel()

    # Plugins
    ##########################################################################

    def pluginsFolder(self):
        dir = self.config.configPath
        return os.path.join(dir, "plugins")

    def loadPlugins(self):
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

    def rebuildPluginsMenu(self):
        if getattr(self, "pluginActions", None) is None:
            self.pluginActions = []
        for action in self.pluginActions:
            self.mainWin.menuStartup.removeAction(action)
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
            self.mainWin.menuStartup.addAction(a)
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
            # reuse our process handling code from latex
            anki.latex.call(["explorer", path.encode(
                sys.getfilesystemencoding())],
                            wait=False)
        else:
            QDesktopServices.openUrl(QUrl("file://" + path))

    def onGetPlugins(self):
        QDesktopServices.openUrl(QUrl("http://ichi2.net/anki/wiki/Plugins"))

    def enablePlugin(self, p):
        pd = self.pluginsFolder()
        os.rename(os.path.join(pd, p),
                  os.path.join(pd, p.replace(".off", "")))

    def disablePlugin(self, p):
        pd = self.pluginsFolder()
        os.rename(os.path.join(pd, p),
                  os.path.join(pd, p.replace(".py", ".py.off")))

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
        src = os.path.basename(inspect.getfile(inspect.currentframe(1)))
        self.registeredPlugins[src] = {'name': name,
                                       'id': updateId}

    def checkForUpdatedPlugins(self):
        pass

    # Font localisation
    ##########################################################################

    def setupFonts(self):
        for (s, p) in anki.fonts.substitutions():
            QFont.insertSubstitution(s, p)

    # Sounds
    ##########################################################################

    def setupSound(self):
        anki.sound.noiseProfile = os.path.join(
            self.config.configPath, "noise.profile").\
            encode(sys.getfilesystemencoding())
        anki.sound.checkForNoiseProfile()
        if sys.platform.startswith("darwin"):
            self.mainWin.actionRecordNoiseProfile.setEnabled(False)

    def onRepeatAudio(self):
        playFromText(self.currentCard.question)
        if self.state != "showQuestion":
            playFromText(self.currentCard.answer)

    def onRecordNoiseProfile(self):
        from ui.sound import recordNoiseProfile
        recordNoiseProfile(self)

    # Progress info
    ##########################################################################

    def setupProgressInfo(self):
        addHook("startProgress", self.startProgress)
        addHook("updateProgress", self.updateProgress)
        addHook("finishProgress", self.finishProgress)
        addHook("dbProgress", self.onDbProgress)
        addHook("dbFinished", self.onDbFinished)
        self.progressParent = None
        self.progressWins = []
        self.busyCursor = False
        self.updatingBusy = False
        self.mainThread = QThread.currentThread()
        self.oldSessionHelperGetter = SessionHelper.__getattr__
        SessionHelper.__getattr__ = wrap(SessionHelper.__getattr__,
                                         self.checkProgressHandler,
                                         pos="before")

    def checkProgressHandler(self, ses, k):
        "Catch attempts to access the DB from a progress handler."
        if self.inDbHandler:
            raise Exception("Accessed DB while in progress handler")

    def setProgressParent(self, parent):
        self.progressParent = parent

    def startProgress(self, max=0, min=0, title=None):
        if self.mainThread != QThread.currentThread():
            return
        self.setBusy()
        parent = self.progressParent or self.app.activeWindow() or self
        if self.progressWins:
            parent = self.progressWins[-1].diag
        p = ui.utils.ProgressWin(parent, max, min, title)
        self.progressWins.append(p)

    def updateProgress(self, label=None, value=None):
        if self.mainThread != QThread.currentThread():
            return
        if self.progressWins:
            self.progressWins[-1].update(label, value)
        self.app.processEvents()

    def finishProgress(self):
        if self.mainThread != QThread.currentThread():
            return
        if self.progressWins:
            p = self.progressWins.pop()
            p.finish()
        if not self.progressWins:
            self.unsetBusy()

    def onDbProgress(self):
        if self.mainThread != QThread.currentThread():
            return
        self.setBusy()
        self.inDbHandler = True
        self.app.processEvents()
        self.inDbHandler = False

    def onDbFinished(self):
        if self.mainThread != QThread.currentThread():
            return
        if not self.progressWins:
            self.unsetBusy()

    def setBusy(self):
        if not self.busyCursor and not self.updatingBusy:
            self.busyCursor = True
            self.app.setOverrideCursor(QCursor(Qt.WaitCursor))
            self.updatingBusy = True
            self.setEnabled(False)
            self.updatingBusy = False

    def unsetBusy(self):
        if self.busyCursor and not self.updatingBusy:
            self.app.restoreOverrideCursor()
            self.busyCursor = None
            self.updatingBusy = True
            self.setEnabled(True)
            self.updatingBusy = False

    # Advanced features
    ##########################################################################

    def onQuickCheckDB(self):
        self.onCheckDB(full=False)

    def onCheckDB(self, full=True):
        "True if no problems"
        if self.errorOccurred:
            ui.utils.showWarning(_(
                "Please restart Anki before checking the DB."))
            return
        if full and not ui.utils.askUser(_("""\
This operation will find and fix some common problems.<br>
<br>
On the next sync, all cards will be sent to the server.<br>
Any changes on the server since your last sync will be lost.<br>
<br>
<b>This operation is not undoable.</b><br>
Proceed?""")):
            return
        ret = self.deck.fixIntegrity(quick=not full)
        if ret == "ok":
            ret = True
        else:
            ret = _("Problems found:\n%s") % ret
            diag = QDialog(self)
            diag.setWindowTitle("Anki")
            layout = QVBoxLayout(diag)
            diag.setLayout(layout)
            text = QTextEdit()
            text.setReadOnly(True)
            text.setPlainText(ret)
            layout.addWidget(text)
            box = QDialogButtonBox(QDialogButtonBox.Close)
            layout.addWidget(box)
            self.connect(box, SIGNAL("rejected()"), diag, SLOT("reject()"))
            diag.exec_()
            ret = False
        self.reset()
        return ret

    def onOptimizeDB(self):
        size = self.deck.optimize()
        ui.utils.showInfo(_("Database optimized.\nShrunk by %dKB") % (size/1024.0))

    def onCheckMediaDB(self):
        if self.isCramming():
            ui.utils.showInfo(_("""\
You are currently cramming. Please close this deck first."""))
            return
        mb = QMessageBox(self)
        mb.setWindowTitle(_("Anki"))
        mb.setIcon(QMessageBox.Warning)
        mb.setText(_("""\
This operation:<br>
 - deletes files not referenced by cards<br>
 - either tags cards, or deletes references to missing files<br>
 - renames files to a string of numbers and letters<br>
 - updates checksums for files which have been changed<br>
<br>
<b>This operation is not undoable.</b><br>
Consider backing up your media directory first."""))
        bTag = QPushButton(_("Tag Cards"))
        mb.addButton(bTag, QMessageBox.RejectRole)
        bDelete = QPushButton(_("Delete Refs"))
        mb.addButton(bDelete, QMessageBox.RejectRole)
        bCancel = QPushButton(_("Cancel"))
        mb.addButton(bCancel, QMessageBox.RejectRole)
        mb.exec_()
        if mb.clickedButton() == bTag:
            (missing, unused) = rebuildMediaDir(self.deck, False)
        elif mb.clickedButton() == bDelete:
            (missing, unused) = rebuildMediaDir(self.deck, True)
        else:
            return
        ui.utils.showInfo(
                ngettext("%d missing reference.", "%d missing references.",
                    missing) % missing + "\n" +
                ngettext("%d unused file removed.", "%d unused files removed.",
                    unused) % unused)

    def addHook(self, *args):
        addHook(*args)

    def onCacheLatex(self):
        anki.latex.cacheAllLatexImages(self.deck)

    def onUncacheLatex(self):
        anki.latex.deleteAllLatexImages(self.deck)

    # System specific misc
    ##########################################################################

    def setupSystemHacks(self):
        self.setupDocumentDir()
        self.changeLayoutSpacing()
        addHook("macLoadEvent", self.onMacLoad)

    def onMacLoad(self, fname):
        self.loadDeck(fname)

    def setupDocumentDir(self):
        if sys.platform.startswith("win32"):
            s = QSettings(QSettings.UserScope, "Microsoft", "Windows")
            s.beginGroup("CurrentVersion/Explorer/Shell Folders")
            self.documentDir = unicode(s.value("Personal").toString())
        elif sys.platform.startswith("darwin"):
            self.documentDir = os.path.expanduser("~/Documents")
        else:
            self.documentDir = os.path.expanduser("~/.anki")

    def changeLayoutSpacing(self):
        if sys.platform.startswith("darwin"):
            #self.mainWin.studyOptionsFrame.setFixedWidth(400)
            self.mainWin.studyOptionsReviewBar.setContentsMargins(0, 20, 0, 0)
            self.mainWin.optionsBox.layout().setSpacing(10)
            self.mainWin.optionsBox.layout().setContentsMargins(4, 10, 4, 4)

    # Misc
    ##########################################################################

    def setupMisc(self):
        # if they've just upgraded, set created time based on deck age
        if time.time() - self.config['created'] < 60 and self.deck:
            self.config['created'] = self.deck.created

    def setupBackups(self):
        # set backups
        anki.deck.numBackups = self.config['numBackups']
