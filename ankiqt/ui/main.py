# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, sys, re, types, gettext, stat, traceback, inspect, signal
import shutil, time, glob, tempfile, datetime, zipfile, locale
from operator import itemgetter

from PyQt4.QtCore import *
from PyQt4.QtGui import *
from PyQt4.QtWebKit import QWebPage
from PyQt4 import pyqtconfig
QtConfig = pyqtconfig.Configuration()
from anki import DeckStorage
from anki.errors import *
from anki.sound import hasSound, playFromText, clearAudioQueue, stripSounds
from anki.utils import addTags, deleteTags, parseTags, canonifyTags, \
     stripHTML, checksum
from anki.media import rebuildMediaDir, downloadMissing, downloadRemote
from anki.db import OperationalError, SessionHelper, sqlite
from anki.stdmodels import BasicModel
from anki.hooks import runHook, addHook, removeHook, _hooks, wrap
from anki.deck import newCardOrderLabels, newCardSchedulingLabels
from anki.deck import revCardOrderLabels, failedCardOptionLabels
from ankiqt.ui.utils import saveGeom, restoreGeom, saveState, restoreState
import anki.lang
import anki.deck
import ankiqt
ui = ankiqt.ui
config = ankiqt.config

class AnkiQt(QMainWindow):
    def __init__(self, app, config, args):
        QMainWindow.__init__(self)
        try:
            self.errorOccurred = False
            self.inDbHandler = False
            self.reviewingStarted = False
            if sys.platform.startswith("darwin"):
                qt_mac_set_menubar_icons(False)
            elif sys.platform.startswith("win32"):
                # make sure they're picked up on bundle
                from ctypes import windll, wintypes
            ankiqt.mw = self
            self.app = app
            self.config = config
            self.deck = None
            self.state = "initial"
            self.hideWelcome = False
            self.views = []
            signal.signal(signal.SIGINT, self.onSigInt)
            self.setLang()
            self.setupStyle()
            self.setupFonts()
            self.setupBackupDir()
            self.setupProxy()
            self.setupMainWindow()
            self.setupDeckBrowser()
            self.setupSystemHacks()
            self.setupSound()
            self.setupTray()
            self.setupSync()
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
                restoreGeom(self, "mainWindow", 21)
                restoreState(self, "mainWindow")
            else:
                self.resize(500, 500)
            # load deck
            ui.splash.update()
            self.setupErrorHandler()
            self.setupMisc()
            # check if we've been updated
            if "version" not in self.config:
                # could be new user, or upgrade from older version
                # which didn't have version variable
                self.appUpdated = "first"
            elif self.config['version'] != ankiqt.appVersion:
                self.appUpdated = self.config['version']
            else:
                self.appUpdated = False
            if self.appUpdated:
                self.config['version'] = ankiqt.appVersion
            # plugins might be looking at this
            self.state = "noDeck"
            self.loadPlugins()
            self.setupAutoUpdate()
            self.rebuildPluginsMenu()
            # plugins loaded, now show interface
            ui.splash.finish(self)
            self.show()
            # program open sync
            if self.config['syncOnProgramOpen']:
                self.syncDeck(interactive=False)
            if (args or self.config['loadLastDeck'] or
                len(self.config['recentDeckPaths']) == 1):
                # open the last deck
                self.maybeLoadLastDeck(args)
            if self.deck:
                # deck open sync?
                if self.config['syncOnLoad'] and self.deck.syncName:
                    self.syncDeck(interactive=False)
            elif not self.config['syncOnProgramOpen'] or not self.browserDecks:
                # sync disabled or no user/pass, so draw deck browser manually
                self.moveToState("noDeck")
            # all setup is done, run after-init hook
            try:
                runHook('init')
            except:
                ui.utils.showWarning(
                    _("Broken plugin:\n\n%s") %
                    unicode(traceback.format_exc(), "utf-8", "replace"))
            # activate & raise is useful when run from the command line on osx
            self.activateWindow()
            self.raise_()
        except:
            ui.utils.showInfo("Error during startup:\n%s" %
                              traceback.format_exc())
            sys.exit(1)

    def onSigInt(self, signum, frame):
        self.close()

    def onReload(self):
        self.moveToState("auto")

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
                     SIGNAL("triggered()"),
                     self.onReload)
        # congrats
        self.connect(self.mainWin.learnMoreButton,
                     SIGNAL("clicked()"),
                     self.onLearnMore)
        self.connect(self.mainWin.reviewEarlyButton,
                     SIGNAL("clicked()"),
                     self.onReviewEarly)
        self.connect(self.mainWin.finishButton,
                     SIGNAL("clicked()"),
                     self.onClose)
        # notices
        self.mainWin.noticeFrame.setShown(False)
        self.connect(self.mainWin.noticeButton, SIGNAL("clicked()"),
                     lambda: self.mainWin.noticeFrame.setShown(False))
        if sys.platform.startswith("win32"):
            self.mainWin.noticeButton.setFixedWidth(24)
        elif sys.platform.startswith("darwin"):
            self.mainWin.noticeButton.setFixedWidth(20)
            self.mainWin.noticeButton.setFixedHeight(20)
        addHook("cardAnswered", self.onCardAnsweredHook)
        addHook("undoEnd", self.maybeEnableUndo)
        addHook("notify", self.onNotify)

    def onNotify(self, msg):
        if self.mainThread != QThread.currentThread():
            # decks may be opened in a sync thread
            sys.stderr.write(msg + "\n")
        else:
            ui.utils.showInfo(msg)

    def setNotice(self, str=""):
        if str:
            self.mainWin.noticeLabel.setText(str)
            self.mainWin.noticeFrame.setShown(True)
        else:
            self.mainWin.noticeFrame.setShown(False)

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
                ui.utils.showInfo(
                    _("Couldn't play sound. Please install mplayer."))
                return
            if "ERR-0100" in error:
                ui.utils.showInfo(error)
                return
            if "ERR-0101" in error:
                ui.utils.showInfo(error)
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
            self.clearProgress()

    def closeAllDeckWindows(self):
        ui.dialogs.closeAll()
        self.help.hide()

    # State machine
    ##########################################################################

    def addView(self, view):
        self.views.append(view)

    def updateViews(self, status):
        if self.deck is None and status != "noDeck":
            raise Exception("updateViews() called with no deck. status=%s" % status)
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

    def reset(self, count=True, priorities=False, runHooks=True):
        if self.deck:
            self.deck.refreshSession()
            if priorities:
                self.deck.updateAllPriorities()
            self.deck.reset()
            if runHooks:
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
                self.updateViews(state)
                if self.state == "studyScreen":
                    return self.moveToState("studyScreen")
                else:
                    return self.moveToState("getQuestion")
            else:
                return self.moveToState("noDeck")
        elif state == "auto":
            self.currentCard = None
            self.lastCard = None
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
            self.help.hide()
            self.currentCard = None
            self.lastCard = None
            self.disableDeckMenuItems()
            # hide all deck-associated dialogs
            self.closeAllDeckWindows()
            self.showDeckBrowser()
        elif state == "getQuestion":
            # stop anything playing
            clearAudioQueue()
            if self.deck.isEmpty():
                return self.moveToState("deckEmpty")
            else:
                # timeboxing only supported using the standard scheduler
                if not self.deck.finishScheduler:
                    if (self.config['showStudyScreen'] and
                        not self.deck.sessionStartTime):
                        return self.moveToState("studyScreen")
                    if self.deck.sessionLimitReached():
                        self.showToolTip(_("Session limit reached."))
                        self.moveToState("studyScreen")
                        # switch to timeboxing screen
                        self.mainWin.tabWidget.setCurrentIndex(2)
                        return
                if not self.currentCard:
                    self.currentCard = self.deck.getCard()
                if self.currentCard:
                    if self.lastCard:
                        if self.lastCard.id == self.currentCard.id:
                            pass
                            # if self.currentCard.combinedDue > time.time():
                            #     # if the same card is being shown and it's not
                            #     # due yet, give up
                            #     return self.moveToState("deckFinished")
                    self.enableCardMenuItems()
                    return self.moveToState("showQuestion")
                else:
                    return self.moveToState("deckFinished")
        elif state == "deckEmpty":
            self.switchToWelcomeScreen()
            self.disableCardMenuItems()
        elif state == "deckFinished":
            self.currentCard = None
            self.deck.s.flush()
            self.hideButtons()
            self.disableCardMenuItems()
            self.switchToCongratsScreen()
            self.mainWin.learnMoreButton.setEnabled(
                not not self.deck.newCount)
            self.startRefreshTimer()
            self.bodyView.setState(state)
            # focus finish button
            self.mainWin.finishButton.setFocus()
            runHook('deckFinished')
        elif state == "showQuestion":
            self.reviewingStarted = True
            # ensure cwd set to media dir
            self.deck.mediaDir()
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
            self.mainWin.actionRepeatAudio.setEnabled(True)
            self.editor.saveFieldsNow()
            self.mainWin.buttonStack.show()
            return self.reset()
        elif state == "studyScreen":
            self.currentCard = None
            if self.deck.finishScheduler:
                self.deck.finishScheduler()
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
        self.lastQuality = quality
        self.lastCard = self.currentCard
        self.currentCard = None
        if self.config['saveAfterAnswer']:
            num = self.config['saveAfterAnswerNum']
            stats = self.deck.getStats()
            if stats['gTotal'] % num == 0:
                self.save()
        self.moveToState("getQuestion")

    def onCardAnsweredHook(self, cardId, isLeech):
        if not isLeech:
            self.setNotice()
            return
        txt = (_("""\
<b>%s</b>... is a <a href="http://ichi2.net/anki/wiki/Leeches">leech</a>.""")
               % stripHTML(stripSounds(self.currentCard.question)).\
               replace("\n", " ")[0:30])
        if isLeech and self.deck.s.scalar(
            "select 1 from cards where id = :id and priority < 1", id=cardId):
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
         self.deck.newCountToday))
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

    def switchToBlankScreen(self):
        self.mainWin.mainStack.setCurrentIndex(0)
        self.hideButtons()

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
        self.mainWin.mainStack.setCurrentIndex(6)

    def switchToDecksScreen(self):
        self.mainWin.mainStack.setCurrentIndex(5)
        self.hideButtons()

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
        outer.setSpacing(0)
        outer.setContentsMargins(0,0,0,0)
        outer.addStretch(0)
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
        f.setPixelSize(self.config['typeAnswerFontSize'])
        self.typeAnswerField.setFont(f)
        # add some extra space as layout is wrong on osx
        self.typeAnswerField.setFixedHeight(
            self.typeAnswerField.sizeHint().height() + 10)
        vbox = QVBoxLayout()
        vbox.setSpacing(0)
        vbox.setContentsMargins(0,0,0,0)
        vbox.addWidget(self.typeAnswerField)
        self.typeAnswerShowButton = QPushButton(_("Show Answer"))
        hbox = QHBoxLayout()
        hbox.setContentsMargins(0,0,0,0)
        hbox.addWidget(self.typeAnswerShowButton)
        vbox.addLayout(hbox)
        self.connect(self.typeAnswerShowButton, SIGNAL("clicked()"),
                     lambda: self.moveToState("showAnswer"))
        outer.addLayout(vbox)
        outer.addStretch(0)
        self.mainWin.typeAnswerPage.setLayout(outer)

    def hideButtons(self):
        self.mainWin.buttonStack.hide()

    def showAnswerButton(self):
        if self.currentCard.cardModel.typeAnswer:
            self.mainWin.buttonStack.setCurrentIndex(2)
            self.typeAnswerField.setFocus()
            self.typeAnswerField.setText("")
        else:
            self.mainWin.buttonStack.setCurrentIndex(0)
            self.mainWin.showAnswerButton.setFocus()
        self.mainWin.buttonStack.show()

    def showEaseButtons(self):
        self.updateEaseButtons()
        self.mainWin.buttonStack.setCurrentIndex(1)
        self.mainWin.buttonStack.show()
        self.mainWin.buttonStack.setLayoutDirection(Qt.LeftToRight)
        if self.learningButtons():
            self.mainWin.easeButton2.setText(_("Good"))
            self.mainWin.easeButton3.setText(_("Easy"))
            self.mainWin.easeButton4.setText(_("Very Easy"))
        else:
            self.mainWin.easeButton2.setText(_("Hard"))
            self.mainWin.easeButton3.setText(_("Good"))
            self.mainWin.easeButton4.setText(_("Easy"))
        getattr(self.mainWin, "easeButton%d" % self.defaultEaseButton()).\
                              setFocus()

    def learningButtons(self):
        return not self.currentCard.successive

    def defaultEaseButton(self):
        if not self.currentCard.successive:
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
                txt = _("Soon")
                if self.config['colourTimes']:
                    txt = '<span style="color: #700"><b>%s</b></span>' % txt
                l.setText(txt)
            else:
                txt = self.deck.nextIntervalStr(
                    self.currentCard, i)
                txt = "<b>" + txt + "</b>"
                if i == self.defaultEaseButton() and self.config['colourTimes']:
                    txt = '<span style="color: #070">' + txt + '</span>'
                l.setText(txt)

    # Deck loading & saving: backend
    ##########################################################################

    def setupBackupDir(self):
        anki.deck.backupDir = os.path.join(
            self.config.configPath, "backups")

    def loadDeck(self, deckPath, sync=True, interactive=True, uprecent=True):
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
            if hasattr(e, 'data') and e.data.get('type') == 'inuse':
                if interactive:
                    ui.utils.showWarning(_("Deck is already open."))
                else:
                    return
            else:
                ui.utils.showCritical(_("""\
File is corrupt or not an Anki database. Click help for more info.\n
Debug info:\n%s""") % traceback.format_exc(), help="DeckErrors")
            self.moveToState("noDeck")
            return 0
        if uprecent:
            self.updateRecentFiles(self.deck.path)
        if (sync and self.config['syncOnLoad']
            and self.deck.syncName):
            if self.syncDeck(interactive=False):
                return True
        self.setupMedia(self.deck)
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
        runHook("loadDeck")
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
             if not self.deck or self.deck.path != x and
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
        # allow focusOut to save
        if self.inMainWindow() or not self.app.activeWindow():
            self.saveAndClose()
        else:
            self.app.activeWindow().close()

    def saveAndClose(self, hideWelcome=False, parent=None):
        "(Auto)save and close. Prompt if necessary. True if okay to proceed."
        # allow any focusOut()s to run first
        self.setFocus()
        if not parent:
            parent = self
        self.hideWelcome = hideWelcome
        self.closeAllDeckWindows()
        synced = False
        if self.deck is not None:
            if self.deck.finishScheduler:
                self.deck.finishScheduler()
                self.deck.reset()
            # update counts
            for d in self.browserDecks:
                if d['path'] == self.deck.path:
                    d['due'] = self.deck.failedSoonCount + self.deck.revCount
                    d['new'] = self.deck.newCountToday
                    d['mod'] = self.deck.modified
                    d['time'] = self.deck._dailyStats.reviewTime
                    d['reps'] = self.deck._dailyStats.reps
            if self.deck.modifiedSinceSave():
                if (self.deck.path is None or
                    (not self.config['saveOnClose'] and
                     not self.config['syncOnLoad'])):
                    # backed in memory or autosave/sync off, must confirm
                    while 1:
                        res = ui.unsaved.ask(parent)
                        if res == ui.unsaved.save:
                            if self.save(required=True):
                                break
                        elif res == ui.unsaved.cancel:
                            self.hideWelcome = False
                            return False
                        else:
                            break
            # auto sync (saving automatically)
            if self.config['syncOnLoad'] and self.deck.syncName:
                # force save, the user may not have set passwd/etc
                self.deck.save()
                if self.syncDeck(False, reload=False):
                    synced = True
                    while self.deckPath:
                        self.app.processEvents()
                        time.sleep(0.1)
                    self.hideWelcome = False
                    return True
            # auto save
            if self.config['saveOnClose'] or self.config['syncOnLoad']:
                if self.deck:
                    self.save()
            # close if the deck wasn't already closed by a failed sync
            if self.deck:
                self.deck.rollback()
                self.deck.close()
                self.deck = None
        if not hideWelcome and not synced:
            self.moveToState("noDeck")
        self.hideWelcome = False
        return True

    def inMainWindow(self):
        if not self.app.activeWindow():
            # make sure window is shown
            self.setWindowState(self.windowState() & ~Qt.WindowMinimized)
        return True
        # FIXME: no longer necessary?
        return self.app.activeWindow() == self

    def onNew(self, path=None, prompt=None):
        if not self.inMainWindow() and not path: return
        if not self.saveAndClose(hideWelcome=True): return
        register = not path
        bad = ":/\\"
        name = _("mydeck")
        if not path:
            if not prompt:
                prompt = _("Please give your deck a name:")
            while 1:
                name = ui.utils.getOnlyText(
                    prompt, default=name, title=_("New Deck"))
                if not name:
                    self.moveToState("noDeck")
                    return
                found = False
                for c in bad:
                    if c in name:
                        ui.utils.showInfo(
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
                if ui.utils.askUser(_("That deck already exists. Overwrite?"),
                                    defaultno=True):
                    os.unlink(path)
                else:
                    self.moveToState("noDeck")
                    return
        self.deck = DeckStorage.Deck(path)
        self.deck.initUndo()
        self.deck.addModel(BasicModel())
        self.deck.save()
        if register:
            self.updateRecentFiles(self.deck.path)
        self.browserLastRefreshed = 0
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
        self.browserLastRefreshed = 0
        self.moveToState("initial")

    def onGetSharedDeck(self):
        if not self.inMainWindow(): return
        ui.getshared.GetShared(self, 0)
        self.browserLastRefreshed = 0

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
        self.closeAllDeckWindows()
        self.deck = self.deck.saveAs(file)
        self.deck.initUndo()
        self.updateTitleBar()
        self.updateRecentFiles(self.deck.path)
        self.browserLastRefreshed = 0
        self.moveToState("initial")
        return file

    # Deck browser
    ##########################################################################

    def setupDeckBrowser(self):
        class PaddedScroll(QScrollArea):
            def sizeHint(self):
                hint = QScrollArea.sizeHint(self)
                if sys.platform.startswith("darwin"):
                    m = 500
                else:
                    m = 450
                return QSize(max(hint.width(), m), hint.height())
        self.decksScrollArea = PaddedScroll()
        self.decksScrollArea.setFrameStyle(QFrame.NoFrame)
        self.decksScrollArea.setWidgetResizable(True)
        self.mainWin.verticalLayout_14.insertWidget(2, self.decksScrollArea)
        self.decksFrame = QFrame()
        self.connect(self.mainWin.downloadDeckButton,
                     SIGNAL("clicked()"),
                     self.onGetSharedDeck)
        self.connect(self.mainWin.newDeckButton,
                     SIGNAL("clicked()"),
                     self.onNew)
        self.connect(self.mainWin.importDeckButton,
                     SIGNAL("clicked()"),
                     self.onImport)
        self.browserLastRefreshed = 0
        self.browserDecks = []

    def refreshBrowserDecks(self, forget=False):
        self.browserDecks = []
        if not self.config['recentDeckPaths']:
            return
        toRemove = []
        self.startProgress(max=len(self.config['recentDeckPaths']),
                           immediate=True)
        for c, d in enumerate(self.config['recentDeckPaths']):
            if ui.splash.finished:
                self.updateProgress(_("Checking deck %(x)d of %(y)d...") % {
                    'x': c+1, 'y': len(self.config['recentDeckPaths'])})
            if not os.path.exists(d):
                if forget:
                    toRemove.append(d)
                continue
            try:
                mod = os.stat(d)[stat.ST_MTIME]
                deck = DeckStorage.Deck(d, backup=False)
                self.browserDecks.append({
                    'path': d,
                    'name': deck.name(),
                    'due': deck.failedSoonCount + deck.revCount,
                    'new': deck.newCountToday,
                    'mod': deck.modified,
                    'time': deck._dailyStats.reviewTime,
                    'reps': deck._dailyStats.reps,
                    })
                deck.close()
                try:
                    os.utime(d, (mod, mod))
                except:
                    # some misbehaving filesystems may fail here
                    pass
            except Exception, e:
                if "File is in use" in unicode(e):
                    continue
                else:
                    toRemove.append(d)
        for d in toRemove:
            self.config['recentDeckPaths'].remove(d)
        self.config.save()
        if ui.splash.finished:
            self.finishProgress()
        self.browserLastRefreshed = time.time()
        self.reorderBrowserDecks()

    def reorderBrowserDecks(self):
        if self.config['deckBrowserOrder'] == 0:
            self.browserDecks.sort(key=itemgetter('mod'),
                                   reverse=True)
        else:
            def custcmp(a, b):
                x = cmp(not not b['due'], not not a['due'])
                if x:
                    return x
                x = cmp(not not b['new'], not not a['new'])
                if x:
                    return x
                return cmp(a['mod'], b['mod'])
            self.browserDecks.sort(cmp=custcmp)

    def forceBrowserRefresh(self):
        self.browserLastRefreshed = 0
        self.showDeckBrowser()

    def showDeckBrowser(self):
        self.switchToBlankScreen()
        import sip
        focusButton = None
        # remove all widgets from layout & layout itself
        self.moreMenus = []
        if self.decksFrame.layout():
            while 1:
                obj = self.decksFrame.layout().takeAt(0)
                if not obj:
                    break
                if obj.widget():
                    obj.widget().deleteLater()
            self.app.processEvents(QEventLoop.DeferredDeletion)
            sip.delete(self.decksFrame.layout())
        # build new layout
        layout = QGridLayout()
        self.decksFrame.setLayout(layout)
        if sys.platform.startswith("darwin"):
            layout.setSpacing(6)
        else:
            layout.setSpacing(2)
        if (time.time() - self.browserLastRefreshed >
            self.config['deckBrowserRefreshPeriod']):
            self.refreshBrowserDecks()
        else:
            self.reorderBrowserDecks()
        if self.browserDecks:
            layout.addWidget(QLabel(_("<b>Deck</b>")), 0, 0)
            layout.setColumnStretch(0, 1)
            l = QLabel(_("<b>Due<br>Today</b>"))
            l.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
            layout.addWidget(l, 0, 1)
            layout.setColumnMinimumWidth(2, 10)
            l = QLabel(_("<b>New<br>Today</b>"))
            l.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
            layout.addWidget(l, 0, 3)
            layout.setColumnMinimumWidth(4, 10)
            for c, deck in enumerate(self.browserDecks):
                # name
                n = deck['name']
                lim = self.config['deckBrowserNameLength']
                if len(n) > lim:
                    n = n[:lim] + "..."
                mod = _("%s ago") % anki.utils.fmtTimeSpan(
                    time.time() - deck['mod'])
                mod = "<font size=-1>%s</font>" % mod
                l = QLabel("%d. <b>%s</b><br>&nbsp;&nbsp;&nbsp;&nbsp;%s" %
                           (c+1, n, mod))
                l.setWordWrap(True)
                layout.addWidget(l, c+1, 0)
                # due
                col = '<b><font color=#0000ff>%s</font></b>'
                if deck['due'] > 0:
                    s = col % str(deck['due'])
                else:
                    s = ""
                l = QLabel(s)
                l.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
                layout.addWidget(l, c+1, 1)
                # new
                if deck['new']:
                    s = str(deck['new'])
                else:
                    s = ""
                l = QLabel(s)
                l.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
                layout.addWidget(l, c+1, 3)
                # open
                openButton = QPushButton(_("Open"))
                if c < 9:
                    if sys.platform.startswith("darwin"):
                        extra = ""
                        # appears to be broken on osx
                        #extra = _(" (Command+Option+%d)") % (c+1)
                        #openButton.setShortcut(_("Ctrl+Alt+%d" % (c+1)))
                    else:
                        extra = _(" (Alt+%d)") % (c+1)
                        openButton.setShortcut(_("Alt+%d" % (c+1)))
                else:
                    extra = ""
                openButton.setToolTip(_("Open this deck%s") % extra)
                self.connect(openButton, SIGNAL("clicked()"),
                             lambda d=deck['path']: self.loadDeck(d))
                layout.addWidget(openButton, c+1, 5)
                if c == 0:
                    focusButton = openButton
                # more
                moreButton = QPushButton(_("More"))
                if sys.platform.startswith("win32") and \
                   self.config['alternativeTheme']:
                        moreButton.setFixedHeight(24)
                moreMenu = QMenu()
                a = moreMenu.addAction(QIcon(":/icons/edit-undo.png"),
                                       _("Hide From List"))
                a.connect(a, SIGNAL("triggered()"),
                          lambda c=c: self.onDeckBrowserForget(c))
                a = moreMenu.addAction(QIcon(":/icons/editdelete.png"),
                                       _("Delete"))
                a.connect(a, SIGNAL("triggered()"),
                          lambda c=c: self.onDeckBrowserDelete(c))
                moreButton.setMenu(moreMenu)
                self.moreMenus.append(moreMenu)
                layout.addWidget(moreButton, c+1, 6)
            refresh = QPushButton(_("Refresh"))
            refresh.setToolTip(_("Check due counts again (F5)"))
            refresh.setShortcut(_("F5"))
            self.connect(refresh, SIGNAL("clicked()"),
                         self.forceBrowserRefresh)
            layout.addItem(QSpacerItem(1,20, QSizePolicy.Preferred,
                                       QSizePolicy.Preferred), c+2, 5)
            layout.addWidget(refresh, c+3, 5)
            more = QPushButton(_("More"))
            moreMenu = QMenu()
            a = moreMenu.addAction(QIcon(":/icons/edit-undo.png"),
                                   _("Forget Inaccessible Decks"))
            a.connect(a, SIGNAL("triggered()"),
                      self.onDeckBrowserForgetInaccessible)
            more.setMenu(moreMenu)
            layout.addWidget(more, c+3, 6)
            self.moreMenus.append(moreMenu)
            # make sure top labels don't expand
            layout.addItem(QSpacerItem(1,1, QSizePolicy.Expanding,
                                       QSizePolicy.Expanding),
                           c+4, 5)
            # summarize
            reps = 0
            mins = 0
            revC = 0
            newC = 0
            for d in self.browserDecks:
                reps += d['reps']
                mins += d['time']
                revC += d['due']
                newC += d['new']
            line1 = ngettext(
                "Studied <b>%(reps)d card</b> in <b>%(time)s</b> today.",
                "Studied <b>%(reps)d cards</b> in <b>%(time)s</b> today.",
                reps) % {
                'reps': reps,
                'time': anki.utils.fmtTimeSpan(mins, point=2),
                }
            rev = ngettext(
                "<b><font color=#0000ff>%d</font></b> review",
                "<b><font color=#0000ff>%d</font></b> reviews",
                revC) % revC
            new = ngettext("<b>%d</b> new card", "<b>%d</b> new cards", newC) % newC
            line2 = _("Due: %(rev)s, %(new)s") % {
                'rev': rev, 'new': new}
            self.mainWin.deckBrowserSummary.setText(line1 + "<br>" + line2)
        else:
            l = QLabel(_("""\
<br>
<font size=+1>
Welcome to Anki! Click <b>'Download'</b> to get started. You can return here
later by using File>Close.
</font>
<br>
"""))
            l.setWordWrap(True)
            layout.addWidget(l, 0, 0)
        self.decksScrollArea.setWidget(self.decksFrame)
        if focusButton:
            focusButton.setFocus()
        # do this last
        self.switchToDecksScreen()

    def onDeckBrowserForget(self, c):
        if ui.utils.askUser(_("""\
Hide %s from the list? You can File>Open it again later.""") %
                            self.browserDecks[c]['name']):
            self.config['recentDeckPaths'].remove(self.browserDecks[c]['path'])
            del self.browserDecks[c]
            self.doLater(100, self.showDeckBrowser)

    def onDeckBrowserDelete(self, c):
        deck = self.browserDecks[c]['path']
        if ui.utils.askUser(_("""\
Delete %s? If this deck is synchronized the online version will \
not be touched.""") %
                            self.browserDecks[c]['name']):
            del self.browserDecks[c]
            os.unlink(deck)
            try:
                shutil.rmtree(re.sub(".anki$", ".media", deck))
            except OSError:
                pass
            self.config['recentDeckPaths'].remove(deck)
            self.doLater(100, self.showDeckBrowser)

    def onDeckBrowserForgetInaccessible(self):
        self.refreshBrowserDecks(forget=True)

    def doLater(self, msecs, func):
        timer = QTimer(self)
        timer.setSingleShot(True)
        timer.start(msecs)
        self.connect(timer, SIGNAL("timeout()"), func)

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
            if self.config['syncOnProgramOpen']:
                self.hideWelcome = True
                self.syncDeck(interactive=False)
            self.prepareForExit()
            event.accept()
            self.app.quit()

    # Anchor clicks
    ##########################################################################

    def onWelcomeAnchor(self, str):
        if str == "back":
            self.saveAndClose()
        if str == "addfacts":
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
            QWebPage.DelegateAllLinks)
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
        self.editor.clayout.setShortcut("")
        self.editor.onFactValid = self.onFactValid
        self.editor.onFactInvalid = self.onFactInvalid
        self.editor.resetOnEdit = False
        # editor
        self.connect(self.mainWin.saveEditorButton, SIGNAL("clicked()"),
                     lambda: self.moveToState("saveEdit"))


    def showEditor(self):
        self.mainWin.buttonStack.hide()
        self.switchToEditScreen()
        self.editor.setFact(self.currentCard.fact)
        self.editor.card = self.currentCard

    def onFactValid(self, fact):
        self.mainWin.saveEditorButton.setEnabled(True)

    def onFactInvalid(self, fact):
        self.mainWin.saveEditorButton.setEnabled(False)

    # Study screen
    ##########################################################################

    def setupStudyScreen(self):
        self.mainWin.buttonStack.hide()
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
        self.connect(self.mainWin.minuteLimit,
                     SIGNAL("textChanged(QString)"), self.onMinuteLimitChanged)
        self.connect(self.mainWin.questionLimit,
                     SIGNAL("textChanged(QString)"), self.onQuestionLimitChanged)
        self.connect(self.mainWin.newPerDay,
                     SIGNAL("textChanged(QString)"), self.onNewLimitChanged)
        self.connect(self.mainWin.startReviewingButton,
                     SIGNAL("clicked()"),
                     self.onStartReview)
        self.connect(self.mainWin.newCardOrder,
                     SIGNAL("activated(int)"), self.onNewCardOrderChanged)
        self.connect(self.mainWin.failedCardMax,
                     SIGNAL("editingFinished()"),
                     self.onFailedMaxChanged)
        self.connect(self.mainWin.newCategories,
                     SIGNAL("clicked()"), self.onNewCategoriesClicked)
        self.connect(self.mainWin.revCategories,
                     SIGNAL("clicked()"), self.onRevCategoriesClicked)
        self.mainWin.tabWidget.setCurrentIndex(self.config['studyOptionsScreen'])

    def onNewCategoriesClicked(self):
        ui.activetags.show(self, "new")

    def onRevCategoriesClicked(self):
        ui.activetags.show(self, "rev")

    def onFailedMaxChanged(self):
        try:
            v = int(self.mainWin.failedCardMax.text())
            if v == 1 or v < 0:
                v = 2
            self.deck.failedCardMax = v
        except ValueError:
            pass
        self.mainWin.failedCardMax.setText(str(self.deck.failedCardMax))
        self.deck.flushMod()

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

    def onQuestionLimitChanged(self, qstr):
        try:
            val = int(self.mainWin.questionLimit.text())
            if self.deck.sessionRepLimit == val:
                return
            self.deck.sessionRepLimit = val
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
                self.deck.startProgress()
                self.deck.updateProgress(_("Ordering..."))
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
        self.mainWin.newCategoryLabel.setText(new)
        if self.deck.getVar("revActive") or self.deck.getVar("revInactive"):
            rev = labels[1]
        else:
            rev = labels[0]
        self.mainWin.revCategoryLabel.setText(rev)

    def updateStudyStats(self):
        self.mainWin.buttonStack.hide()
        self.deck.reset()
        self.updateActives()
        wasReached = self.deck.sessionLimitReached()
        sessionColour = '<font color=#0000ff>%s</font>'
        cardColour = '<font color=#0000ff>%s</font>'
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
        if (not dyest and not dtoday) or not self.config['showStudyStats']:
            self.haveYesterday = False
            stats1 = ""
        else:
            self.haveYesterday = True
            stats1 = (
                "<td>%s</td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td>" % stats1)
        self.mainWin.optionsLabel.setText("""\
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

        self.mainWin.optionsLabel.setToolTip(statToolTip)

    def showStudyScreen(self):
        # forget last card
        self.lastCard = None
        self.switchToStudyScreen()
        self.updateStudyStats()
        self.mainWin.startReviewingButton.setFocus()
        self.setupStudyOptions()
        self.mainWin.studyOptionsFrame.setMaximumWidth(500)
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
        self.mainWin.failedCardMax.setText(unicode(self.deck.failedCardMax))

    def onStartReview(self):
        def uf(obj, field, value):
            if getattr(obj, field) != value:
                setattr(obj, field, value)
                self.deck.flushMod()
        self.mainWin.studyOptionsFrame.hide()
        # make sure the size is updated before button stack shown
        self.app.processEvents()
        uf(self.deck, 'newCardSpacing',
           self.mainWin.newCardScheduling.currentIndex())
        uf(self.deck, 'revCardOrder',
           self.mainWin.revCardOrder.currentIndex())
        pol = self.deck.getFailedCardPolicy()
        if (pol != 5 and pol !=
            self.mainWin.failedCardsOption.currentIndex()):
            self.deck.setFailedCardPolicy(
                self.mainWin.failedCardsOption.currentIndex())
            self.deck.flushMod()
        self.deck.reset()
        if not self.deck.finishScheduler:
            self.deck.startSession()
        self.config['studyOptionsScreen'] = self.mainWin.tabWidget.currentIndex()
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
        mw.toolBar.addAction(mw.actionAddcards)
        mw.toolBar.addAction(mw.actionEditCurrent)
        mw.toolBar.addAction(mw.actionEditLayout)
        mw.toolBar.addAction(mw.actionEditdeck)
        mw.toolBar.addAction(mw.actionStudyOptions)
        mw.toolBar.addAction(mw.actionGraphs)
        mw.toolBar.addAction(mw.actionMarkCard)
        mw.toolBar.addAction(mw.actionRepeatAudio)
        mw.toolBar.addAction(mw.actionClose)
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

    # Tools - statistics
    ##########################################################################

    def onDeckStats(self):
        txt = anki.stats.DeckStats(self.deck).report()
        self.help.showText(txt)

    def onCardStats(self):
        addHook("showQuestion", self.onCardStats)
        addHook("deckFinished", self.onCardStats)
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
        removeHook("deckFinished", self.onCardStats)

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
        ui.dialogs.get("AddCards", self)

    def onEditDeck(self):
        ui.dialogs.get("CardList", self)

    def onEditCurrent(self):
        self.moveToState("editCurrentFact")

    def onCardLayout(self):
        ui.clayout.CardLayout(self, 0, self.currentCard.fact,
                              card=self.currentCard)

    def onDeckProperties(self):
        self.deckProperties = ui.deckproperties.DeckProperties(self, self.deck)

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

    def onCram(self, cardIds=[]):
        te = ui.tagedit.TagEdit(self)
        te.setDeck(self.deck, "all")
        diag = ui.utils.GetTextDialog(
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
                ui.utils.showInfo(_("No cards matched the provided tags."))

    def onShare(self, tags):
        pwd = os.getcwd()
        # open tmp deck
        (e, path) = self._copyToTmpDeck(name="shared.anki", tags=tags)
        if not e.exportedCards:
            ui.utils.showInfo(_("No cards matched the provided tags."))
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

    def setLang(self):
        "Set the user interface language."
        try:
            locale.setlocale(locale.LC_ALL, '')
        except:
            pass
        languageDir=os.path.join(ankiqt.modDir, "locale")
        self.languageTrans = gettext.translation('ankiqt', languageDir,
                                            languages=[self.config["interfaceLang"]],
                                            fallback=True)
        self.installTranslation()
        if getattr(self, 'mainWin', None):
            self.mainWin.retranslateUi(self)
        anki.lang.setLang(self.config["interfaceLang"], local=False)
        self.updateTitleBar()
        if self.config['interfaceLang'] in ("he","ar","fa") and \
               not self.config['forceLTR']:
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

    def setupSync(self):
        if not self.config['syncDisableWhenMoved']:
            anki.deck.Deck.checkSyncHash = lambda self: True

    def syncDeck(self, interactive=True, onlyMerge=False, reload=True):
        "Synchronise a deck with the server."
        if not self.inMainWindow() and interactive and interactive!=-1: return
        self.setNotice()
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
                    and self.deck.s.scalar("select 1 from media limit 1")):
                    ui.utils.showInfo(_("""\
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
                    ui.utils.showInfo(_("""\
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
        self.mainWin.welcomeText.setText(u"")
        self.syncThread = ui.sync.Sync(self, u, p, interactive, onlyMerge)
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
        return self.syncThread.ok

    def decksToSync(self):
        ok = []
        for d in self.config['recentDeckPaths']:
            if os.path.exists(d):
                ok.append(d)
        return ok

    def onConflict(self, deckName):
        diag = ui.utils.askUserDialog(_("""\
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
        diag = ui.utils.askUserDialog(_("""\
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
        self.mainWin.buttonStack.show()
        try:
            try:
                if self.hideWelcome:
                    # no deck load & no deck browser, as we're about to quit or do
                    # something manually
                    pass
                else:
                    if self.loadAfterSync == -1:
                        # after sync all, so refresh browser list
                        self.browserLastRefreshed = 0
                        self.moveToState("noDeck")
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
                        self.loadDeck(self.deckPath, sync=False)
                    else:
                        self.moveToState("noDeck")
            except:
                self.moveToState("noDeck")
                raise
        finally:
            self.deckPath = None
            self.syncFinished = True

    def selectSyncDeck(self, decks):
        name = ui.sync.DeckChooser(self, decks).getName()
        self.syncName = name
        if name:
            # name chosen
            p = os.path.join(self.documentDir, name + ".anki")
            if os.path.exists(p):
                d = ui.utils.askUserDialog(_("""\
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
        self.moveToState("noDeck")
        self.syncFinished = True

    def setSyncStatus(self, text, *args):
        self.mainWin.welcomeText.append("<font size=+2>" + text + "</font>")

    def syncClockOff(self, diff):
        ui.utils.showWarning(
            _("The time or date on your computer is not correct.\n") +
            ngettext("It is off by %d second.\n\n",
                "It is off by %d seconds.\n\n", diff) % diff +
            _("Since this can cause many problems with syncing,\n"
              "syncing is disabled until you fix the problem.")
            )
        self.onSyncFinished()

    def showSyncWarning(self, text):
        ui.utils.showWarning(text, self)
        self.setStatus("")

    def badUserPass(self):
        ui.preferences.Preferences(self, self.config).dialog.tabWidget.\
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
        ui.utils.showWarning(_(
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
        "Save",
        "SaveAs",
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

    def connectMenuActions(self):
        m = self.mainWin
        s = SIGNAL("triggered()")
        self.connect(m.actionNew, s, self.onNew)
        self.connect(m.actionOpenOnline, s, self.onOpenOnline)
        self.connect(m.actionDownloadSharedDeck, s, self.onGetSharedDeck)
        self.connect(m.actionDownloadSharedPlugin, s, self.onGetSharedPlugin)
        self.connect(m.actionOpenRecent, s, self.onSwitchToDeck)
        self.connect(m.actionOpen, s, self.onOpen)
        self.connect(m.actionSave, s, self.onSave)
        self.connect(m.actionSaveAs, s, self.onSaveAs)
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
        self.connect(m.actionFullDatabaseCheck, s, self.onCheckDB)
        self.connect(m.actionOptimizeDatabase, s, self.onOptimizeDB)
        self.connect(m.actionCheckMediaDatabase, s, self.onCheckMediaDB)
        self.connect(m.actionDownloadMissingMedia, s, self.onDownloadMissingMedia)
        self.connect(m.actionLocalizeMedia, s, self.onLocalizeMedia)
        self.connect(m.actionCram, s, self.onCram)
        self.connect(m.actionOpenPluginFolder, s, self.onOpenPluginFolder)
        self.connect(m.actionEnableAllPlugins, s, self.onEnableAllPlugins)
        self.connect(m.actionDisableAllPlugins, s, self.onDisableAllPlugins)
        self.connect(m.actionReleaseNotes, s, self.onReleaseNotes)
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
        runHook("enableDeckMenuItems", enabled)

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
        self.mainWin.actionEditCurrent.setEnabled(False)
        self.mainWin.actionEditLayout.setEnabled(False)
	self.mainWin.actionMarkCard.setEnabled(False)
	self.mainWin.actionSuspendCard.setEnabled(False)
	self.mainWin.actionDelete.setEnabled(False)
	self.mainWin.actionBuryFact.setEnabled(False)
        self.mainWin.actionRepeatAudio.setEnabled(False)
        runHook("disableCardMenuItems")

    def enableCardMenuItems(self):
        self.maybeEnableUndo()
        snd = (hasSound(self.currentCard.question) or
               (hasSound(self.currentCard.answer) and
                self.state != "getQuestion"))
	self.mainWin.actionEditLayout.setEnabled(True)
        self.mainWin.actionRepeatAudio.setEnabled(snd)
	self.mainWin.actionMarkCard.setEnabled(True)
	self.mainWin.actionSuspendCard.setEnabled(True)
	self.mainWin.actionDelete.setEnabled(True)
	self.mainWin.actionBuryFact.setEnabled(True)
        enableEdits = (not self.config['preventEditUntilAnswer'] or
                       self.state != "getQuestion")
        self.mainWin.actionEditCurrent.setEnabled(enableEdits)
        self.mainWin.actionEditdeck.setEnabled(enableEdits)
        runHook("enableCardMenuItems")

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
        self.connect(self.autoUpdate, SIGNAL("newMsg"), self.newMsg)
        self.connect(self.autoUpdate, SIGNAL("clockIsOff"), self.clockIsOff)
        self.autoUpdate.start()

    def newVerAvail(self, data):
        if self.config['suppressUpdate'] < data['latestVersion']:
            ui.update.askAndUpdate(self, data)

    def newMsg(self, data):
        ui.update.showMessages(self, data)

    def clockIsOff(self, diff):
        if diff < 0:
            ret = _("late")
        else:
            ret = _("early")
        ui.utils.showWarning(
            _("The time or date on your computer is not correct.\n") +
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
                ui.utils.showInfo(p[1])

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

    # Custom styles
    ##########################################################################

    def setupStyle(self):
        ui.utils.applyStyles(self)

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
        clearAudioQueue()
        if (not self.currentCard.cardModel.questionInAnswer
            or self.state == "showQuestion") and \
            self.config['repeatQuestionAudio']:
            playFromText(self.currentCard.question)
        if self.state != "showQuestion":
            playFromText(self.currentCard.answer)

    def onRecordNoiseProfile(self):
        from ankiqt.ui.sound import recordNoiseProfile
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

    def startProgress(self, max=0, min=0, title=None, immediate=False):
        if self.mainThread != QThread.currentThread():
            return
        self.setBusy()
        if not self.progressWins:
            parent = self.progressParent or self.app.activeWindow() or self
            p = ui.utils.ProgressWin(parent, max, min, title, immediate)
        else:
            p = None
        self.progressWins.append(p)

    def updateProgress(self, label=None, value=None, process=True):
        if self.mainThread != QThread.currentThread():
            return
        if len(self.progressWins) == 1:
            self.progressWins[0].update(label, value, process)
        else:
            # just redraw
            if process:
                self.app.processEvents()

    def finishProgress(self):
        if self.mainThread != QThread.currentThread():
            return
        if self.progressWins:
            p = self.progressWins.pop()
            if p:
                p.finish()
        if not self.progressWins:
            self.unsetBusy()

    def clearProgress(self):
        # recover on error
        self.progressWins = []
        self.finishProgress()

    def onDbProgress(self):
        if self.mainThread != QThread.currentThread():
            return
        self.setBusy()
        self.inDbHandler = True
        if self.progressWins:
            self.progressWins[0].maybeShow()
        self.app.processEvents(QEventLoop.ExcludeUserInputEvents)
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

    # Media locations
    ##########################################################################

    def setupMedia(self, deck):
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
            import ankiqt.ui.dropbox as db
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
            ui.utils.showInfo(_("""\
A file called right-click-me.txt has been placed in DropBox's public folder. \
After clicking OK, this folder will appear. Please right click on the file (\
command+click on a Mac), choose DropBox>Copy Public Link, and paste the \
link into Anki."""))
            # open folder and text prompt
            self.onOpenPluginFolder(deck.mediaPrefix)
            txt = ui.utils.getText(_("Paste path here:"), parent=self)
            if txt[0]:
                fail = False
                if not txt[0].lower().startswith("http"):
                    fail = True
                if not txt[0].lower().endswith("right-click-me.txt"):
                    fail = True
                if fail:
                    ui.utils.showInfo(_("""\
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
        if self.errorOccurred:
            ui.utils.showWarning(_(
                "Please restart Anki before checking the DB."))
            return
        if not ui.utils.askUser(_("""\
This operation will find and fix some common problems.<br>
<br>
On the next sync, all cards will be sent to the server.<br>
Any changes on the server since your last sync will be lost.<br>
<br>
<b>This operation is not undoable.</b><br>
Proceed?""")):
            return
        ret = self.deck.fixIntegrity()
        if ret == "ok":
            ret = True
            ui.utils.showInfo(_("No problems found."))
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
        ui.utils.showText(report, parent=self, type="text")

    def onDownloadMissingMedia(self):
        res = downloadMissing(self.deck)
        if res is None:
            ui.utils.showInfo(_("No media URL defined for this deck."),
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
        ui.utils.showInfo(msg)

    def onLocalizeMedia(self):
        if not ui.utils.askUser(_("""\
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
        ui.utils.showText(msg, parent=self, type="text")

    def addHook(self, *args):
        addHook(*args)

    # System specific misc
    ##########################################################################

    def setupSystemHacks(self):
        self.setupDocumentDir()
        self.changeLayoutSpacing()
        addHook("macLoadEvent", self.onMacLoad)
        if sys.platform.startswith("darwin"):
            self.setUnifiedTitleAndToolBarOnMac(True)
            self.mainWin.actionMarkCard.setShortcut(_("Alt+m"))
            self.mainWin.verticalLayout_14.setContentsMargins(2,2,2,2)
            # mac users expect a minimize option
            self.minimizeShortcut = QShortcut("Ctrl+m", self)
            self.connect(self.minimizeShortcut, SIGNAL("activated()"),
                         self.onMacMinimize)
            self.hideAccelerators()
            self.hideStatusTips()
        if sys.platform.startswith("win32"):
            self.mainWin.deckBrowserOuterFrame.setFrameStyle(QFrame.Panel)
            self.mainWin.frame_2.setFrameStyle(QFrame.Panel)
            self.mainWin.studyOptionsFrame.setFrameStyle(QFrame.Panel)

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

    def changeLayoutSpacing(self):
        if sys.platform.startswith("darwin"):
            self.mainWin.studyOptionsReviewBar.setContentsMargins(0, 20, 0, 0)

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
            p = self.mainWin.deckBrowserOuterFrame.sizePolicy()
            p.setHorizontalStretch(1)
            self.mainWin.deckBrowserOuterFrame.setSizePolicy(p)
            self.mainWin.decksLabel.hide()
            self.mainWin.decksLine.hide()
            self.mainWin.studyOptsLabel.hide()

    def setupBackups(self):
        # set backups
        anki.deck.numBackups = self.config['numBackups']
