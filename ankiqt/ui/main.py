# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *

# fixme: sample files read only, need to copy

import os, sys, re, types, gettext, stat, traceback
import copy, shutil, time, glob

from PyQt4.QtCore import *
from PyQt4.QtGui import *
from anki import DeckStorage
from anki.errors import *
from anki.sound import hasSound, playFromText
from anki.utils import addTags, deleteTags, parseTags
from anki.media import rebuildMediaDir
from anki.db import OperationalError
from anki.stdmodels import BasicModel
import anki.latex
import anki.lang
import ankiqt
ui = ankiqt.ui
config = ankiqt.config

class AnkiQt(QMainWindow):
    def __init__(self, app, config, args):
        QMainWindow.__init__(self)
        if sys.platform.startswith("darwin"):
            qt_mac_set_menubar_icons(False)
        ankiqt.mw = self
        self.app = app
        self.config = config
        self.deck = None
        self.state = "initial"
        self.views = []
        self.setLang()
        self.setupFonts()
        self.setupBackupDir()
        self.setupHooks()
        self.loadPlugins()
        self.mainWin = ankiqt.forms.main.Ui_MainWindow()
        self.mainWin.setupUi(self)
        self.rebuildPluginsMenu()
        self.alterShortcuts()
        self.help = ui.help.HelpArea(self.mainWin.helpFrame, self.config, self)
	self.trayIcon = ui.tray.AnkiTrayIcon( self )
        self.connectMenuActions()
        if self.config['mainWindowGeom']:
            self.restoreGeometry(self.config['mainWindowGeom'])
        self.bodyView = ui.view.View(self, self.mainWin.mainText,
                                     self.mainWin.mainTextFrame)
        self.addView(self.bodyView)
        self.statusView = ui.status.StatusView(self)
        self.addView(self.statusView)
        self.setupButtons()
        self.setupAnchors()
        self.setupToolbar()
        self.show()
        if sys.platform.startswith("darwin"):
            self.setUnifiedTitleAndToolBarOnMac(True)
            pass
        # load deck
        try:
            self.maybeLoadLastDeck(args)
        finally:
            self.setEnabled(True)
            # the focus is not set while disabled, so fetch card again
            self.moveToState("auto")
        # run after-init hook
        try:
            self.runHook('init')
        except:
            ui.utils.showWarning(_("Broken plugin:\n\n%s") %
                                 traceback.format_exc())
        # check for updates
        self.setupAutoUpdate()

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

    def reset(self):
        if self.deck:
            self.deck.refresh()
            self.deck.updateAllPriorities()
            self.rebuildQueue()

    def rebuildQueue(self):
        # qt on mac is misbehaving
        mac = sys.platform.startswith("darwin")
        if not mac: self.setEnabled(False)
        self.mainWin.mainText.clear()
        self.mainWin.mainText.setHtml(_("<h1>Building revision queue..</h1>"))
        self.app.processEvents()
        self.deck.rebuildQueue()
        if not mac: self.setEnabled(True)
        self.moveToState("initial")

    def moveToState(self, state):
        if state == "initial":
            # reset current card and load again
            self.currentCard = None
            self.lastCard = None
            if self.deck:
                self.mainWin.menu_Lookup.setEnabled(True)
                self.enableDeckMenuItems()
                self.updateRecentFilesMenu()
                self.updateViews(state)
                return self.moveToState("getQuestion")
            else:
                return self.moveToState("noDeck")
        elif state == "auto":
            self.currentCard = None
            if self.deck:
                return self.moveToState("getQuestion")
            else:
                return self.moveToState("noDeck")
        # save the new & last state
        self.lastState = getattr(self, "state", None)
        self.state = state
        self.updateTitleBar()
        if state == "noDeck":
            self.help.hide()
            self.currentCard = None
            self.lastCard = None
            self.disableDeckMenuItems()
            self.resetButtons()
            # hide all deck-associated dialogs
            ui.dialogs.closeAll()
        elif state == "getQuestion":
            self.deck._countsDirty = True
            if self.deck.cardCount() == 0:
                return self.moveToState("deckEmpty")
            else:
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
            self.resetButtons()
            self.disableCardMenuItems()
            self.mainWin.menu_Lookup.setEnabled(False)
        elif state == "deckFinished":
            self.deck.s.flush()
            self.resetButtons()
            self.mainWin.menu_Lookup.setEnabled(False)
            self.disableCardMenuItems()
            self.startRefreshTimer()
            self.bodyView.setState(state)
        elif state == "showQuestion":
            if self.deck.mediaDir():
                os.chdir(self.deck.mediaDir())
            self.resetButtons()
            self.showAnswerButton()
            self.updateMarkAction()
            self.runHook('showQuestion')
        elif state == "showAnswer":
            self.currentCard.stopTimer()
            self.resetButtons()
            self.showEaseButtons()
            self.enableCardMenuItems()
        self.updateViews(state)

    def keyPressEvent(self, evt):
        "Show answer on RET or register answer."
        if self.state == "showQuestion":
            if evt.key() in (Qt.Key_Enter,
                             Qt.Key_Return):
                evt.accept()
                return self.moveToState("showAnswer")
        elif self.state == "showAnswer":
            key = unicode(evt.text())
            if key and key >= "0" and key <= "4":
                # user entered a quality setting
                num=int(key)
                evt.accept()
                return self.cardAnswered(num)
        evt.ignore()

    def cardAnswered(self, quality):
        "Reschedule current card and move back to getQuestion state."
        # copy card for undo
        self.lastCardBackup = copy.copy(self.currentCard)
        # remove card from session before updating it
        self.deck.s.expunge(self.currentCard)
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
                self.saveDeck()
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
                sys.stderr.write("earliest time returned negative value\n")
                return
            t = QTimer(self)
            t.setSingleShot(True)
            self.connect(t, SIGNAL("timeout()"), self.refreshStatus)
            t.start((delay+1)*1000)

    def refreshStatus(self):
        "If triggered when the deck is finished, reset state."
        if self.state == "deckFinished":
            # don't try refresh if the deck is closed during a sync
            if self.deck:
                self.deck.checkDailyStats()
                self.deck.markExpiredCardsDue()
                self.moveToState("getQuestion")
        if self.state != "deckFinished":
            if self.refreshTimer:
                self.refreshTimer.stop()
                self.refreshTimer = None

    # Buttons
    ##########################################################################

    def setupButtons(self):
        self.outerButtonBox = QHBoxLayout(self.mainWin.buttonWidget)
        self.outerButtonBox.setMargin(3)
        self.outerButtonBox.setSpacing(0)
        self.innerButtonWidget = None

    def resetButtons(self):
        # this round-about process is trying to work around a bug in qt
        if self.lastState == self.state:
            return
        if self.innerButtonWidget:
            self.outerButtonBox.removeWidget(self.innerButtonWidget)
            self.innerButtonWidget.deleteLater()
        self.innerButtonWidget = QWidget()
        self.outerButtonBox.addWidget(self.innerButtonWidget)
        self.buttonBox = QVBoxLayout(self.innerButtonWidget)
        self.buttonBox.setSpacing(3)
        self.buttonBox.setMargin(3)
        if self.config['easeButtonHeight'] == "tall":
            self.easeButtonHeight = 50
        else:
            if sys.platform.startswith("darwin"):
                self.easeButtonHeight = 35
            else:
                self.easeButtonHeight = 25

    def showAnswerButton(self):
        if self.lastState == self.state:
            return
        button = QPushButton(_("Show answer"))
        button.setFixedHeight(self.easeButtonHeight)
        self.buttonBox.addWidget(button)
        button.setFocus()
        button.setDefault(True)
        self.connect(button, SIGNAL("clicked()"),
                     lambda: self.moveToState("showAnswer"))

    def getSpacer(self, hpolicy=QSizePolicy.Preferred):
        return QSpacerItem(20, 20,
                           hpolicy,
                           QSizePolicy.Preferred)

    def showEaseButtons(self):
        # if the state hasn't changed, do nothing
        if self.lastState == self.state:
            return
        # gather next intervals
        nextInts = {}
        for i in range(5):
            s=self.deck.nextIntervalStr(self.currentCard, i)
            nextInts["ease%d" % i] = s
        # button grid
        grid = QGridLayout()
        grid.setSpacing(3)
        if self.config['show3AnswerButtons']:
            rng = (1, 4)
        else:
            rng = (0, 5)
        button3 = self.showCompactEaseButtons(grid, nextInts, rng)
        hbox = QHBoxLayout()
        hbox.addStretch()
        hbox.addLayout(grid)
        hbox.addStretch()
        self.buttonBox.addLayout(hbox)
        button3.setFocus()

    def showCompactEaseButtons(self, grid, nextInts, rng):
        text = (
            (_("<b>%(ease0)s</b>"),
             _("<b>Reset.</b><br>You've completely forgotten.")),
            (_("<b>%(ease1)s</b>"),
             _("<b>Too difficult.</b><br>Show this card again soon.")),
            (_("<b>%(ease2)s</b>"),
             _("<b>Challenging.</b><br>Wait a little longer next time.")),
            (_("<b>%(ease3)s</b>"),
             _("<b>Comfortable.</b><br>Wait longer next time.")),
            (_("<b>%(ease4)s</b>"),
             _("<b>Too easy.</b><br>Wait a lot longer next time.")))
        button3 = None
        for i in range(*rng):
            if not self.config['suppressEstimates']:
                label = QLabel(self.withInterfaceFont(text[i][0] % nextInts))
                label.setAlignment(Qt.AlignHCenter)
                grid.addWidget(label, 0, (i*2)+1)
            button = QPushButton(str(i))
            button.setFixedHeight(self.easeButtonHeight)
            if rng[0] == 1:
                button.setFixedWidth(120)
            button.setToolTip(text[i][1])
            self.connect(button, SIGNAL("clicked()"),
                lambda i=i: self.cardAnswered(i))
            #button.setFixedWidth(70)
            if i == 3:
                button3 = button
            grid.addWidget(button, 1, (i*2)+1)
        return button3

    def withInterfaceFont(self, text):
        family = self.config["interfaceFontFamily"]
        size = self.config["interfaceFontSize"]
        colour = self.config["interfaceColour"]
        css = ('.interface {font-family: "%s"; font-size: %spx; color: %s}\n' %
               (family, size, colour))
        css = "<style>\n" + css + "</style>\n"
        text = css + '<span class="interface">' + text + "</span>"
        return text

    # Hooks
    ##########################################################################

    def setupHooks(self):
        self.hooks = {}

    def addHook(self, hookName, func):
        if not self.hooks.get(hookName, None):
            self.hooks[hookName] = []
        if func not in self.hooks[hookName]:
            self.hooks[hookName].append(func)

    def removeHook(self, hookName, func):
        hook = self.hooks.get(hookName, [])
        if func in hook:
            hook.remove(func)

    def runHook(self, hookName, *args):
        hook = self.hooks.get(hookName, None)
        if hook:
            for func in hook:
                func(*args)

    # Deck loading & saving: backend
    ##########################################################################

    def setupBackupDir(self):
        anki.deck.backupDir = os.path.join(
            self.config.configPath, "backups")

    def loadDeck(self, deckPath, sync=True):
        "Load a deck and update the user interface. Maybe sync."
        # return None if error should be reported
        # return 0 to fail with no error
        # return True on success
        try:
            self.pauseViews()
            if not self.saveAndClose():
                return 0
        finally:
            self.restoreViews()
        if not os.path.exists(deckPath):
            return
        try:
            self.deck = DeckStorage.Deck(deckPath, rebuild=False)
        except (IOError, ImportError):
            return
        except DeckWrongFormatError, e:
            self.importOldDeck(deckPath)
            if not self.deck:
                return
        except DeckAccessError, e:
            if e.data.get('type') == 'inuse':
                ui.utils.showWarning(_("Unable to load the same deck twice."))
                return 0
            return
        self.updateRecentFiles(self.deck.path)
        if sync and self.config['syncOnLoad']:
            if self.syncDeck(interactive=False):
                return
        try:
            self.rebuildQueue()
        except OperationalError:
            ui.utils.showWarning(_(
                "Error building queue. Attempting recovery.."))
            self.onCheckDB()
            # try again
            self.rebuildQueue()
        return True

    def importOldDeck(self, deckPath):
        from anki.importing.anki03 import Anki03Importer
        # back up the old file
        newPath = re.sub("\.anki$", ".anki-v3", deckPath)
        while os.path.exists(newPath):
            newPath += "-1"
        os.rename(deckPath, newPath)
        try:
            self.deck = DeckStorage.Deck(deckPath)
            imp = Anki03Importer(self.deck, newPath)
            imp.doImport()
        except DeckWrongFormatError, e:
            ui.utils.showWarning(_(
                "An error occurred while upgrading:\n%s") % `e.data`)
            return
        self.rebuildQueue()

    def maybeLoadLastDeck(self, args):
        "Open the last deck if possible."
        # try a command line argument if available
        try:
            if args:
                f = unicode(args[0], sys.getfilesystemencoding())
                return self.loadDeck(f)
        except:
            sys.stderr.write("Error loading deck path.\n")
            traceback.print_exc()
        # try recent deck paths
        for path in self.config['recentDeckPaths']:
            try:
                r = self.loadDeck(path)
                if r == 0:
                    # in use
                    continue
                return r
            except:
                sys.stderr.write("Error loading last deck.\n")
                traceback.print_exc()
        self.onNew()

    def getDefaultDir(self, save=False):
        "Try and get default dir from most recently opened file."
        defaultDir = ""
        if self.config['recentDeckPaths']:
            latest = self.config['recentDeckPaths'][0]
            defaultDir = os.path.dirname(latest)
        else:
            if save:
                defaultDir = unicode(os.path.expanduser("~/"),
                                     sys.getfilesystemencoding())
            else:
                samples = self.getSamplesDir()
                if samples:
                    return samples
        return defaultDir

    def getSamplesDir(self):
        path = os.path.join(ankiqt.runningDir, "libanki")
        if not os.path.exists(path):
            path = os.path.join(
                os.path.join(ankiqt.runningDir, ".."), "libanki")
            if not os.path.exists(path):
                path = ankiqt.runningDir
        if sys.platform.startswith("win32"):
            path = os.path.split(
                os.path.split(ankiqt.runningDir)[0])[0]
        elif sys.platform.startswith("darwin"):
            path = ankiqt.runningDir + "/../../.."
        else:
            path = os.path.join(path, "anki")
        path = os.path.join(path, "samples")
        path = os.path.normpath(path)
        if os.path.exists(path):
            if sys.platform.startswith("darwin"):
                return self.openMacSamplesDir(path)
            return path
        return ""

    def openMacSamplesDir(self, path):
        # some versions of macosx don't allow the open dialog to point inside
        # a .App file, it seems - so we copy the files onto the desktop.
        newDir = os.path.expanduser("~/Documents/Anki 0.3 Sample Decks")
        import shutil
        if os.path.exists(newDir):
            files = os.listdir(path)
            for file in files:
                loc = os.path.join(path, file)
                if not os.path.exists(os.path.join(newDir, file)):
                    shutil.copy2(loc, newDir)
            return newDir
        shutil.copytree(path, newDir)
        return newDir

    def updateRecentFiles(self, path):
        "Add the current deck to the list of recent files."
        path = os.path.normpath(path)
        if path in self.config['recentDeckPaths']:
            self.config['recentDeckPaths'].remove(path)
        self.config['recentDeckPaths'].insert(0, path)
        del self.config['recentDeckPaths'][4:]
        self.config.save()
        self.updateRecentFilesMenu()

    def updateRecentFilesMenu(self):
        if not self.config['recentDeckPaths']:
            self.mainWin.menuOpenRecent.setEnabled(False)
            return
        self.mainWin.menuOpenRecent.setEnabled(True)
        self.mainWin.menuOpenRecent.clear()
        n = 1
        for file in self.config['recentDeckPaths']:
            a = QAction(self)
            if not sys.platform.startswith("darwin"):
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

    def saveAndClose(self, exit=False):
        "(Auto)save and close. Prompt if necessary. True if okay to proceed."
        cramming = False
        if self.deck is not None:
            oldName = self.deck.name()
            cramming = oldName == "cram"
            # sync (saving automatically)
            if self.config['syncOnClose'] and self.deck.syncName and not cramming:
                self.syncDeck(False, reload=False)
                while self.deckPath:
                    self.app.processEvents()
                    time.sleep(0.1)
                return True
            # save
            if self.deck.modifiedSinceSave() and not cramming:
                if self.config['saveOnClose'] or self.config['syncOnClose']:
                    self.saveDeck()
                else:
                    res = ui.unsaved.ask(self)
                    if res == ui.unsaved.save:
                        self.saveDeck()
                    elif res == ui.unsaved.cancel:
                        return False
                    elif res == ui.unsaved.discard:
                        pass
            # close
            self.deck.rollback()
            self.deck = None
        if not exit:
            if cramming:
                self.loadRecent(0)
            else:
                self.moveToState("noDeck")
        return True

    def onNew(self):
        if not self.saveAndClose(exit=True): return
        self.deck = DeckStorage.Deck()
        self.deck.addModel(BasicModel())
        self.saveDeck()
        self.moveToState("initial")

    def ensureSyncParams(self):
        if not self.config['syncUsername'] or not self.config['syncPassword']:
            d = QDialog(self)
            vbox = QVBoxLayout()
            l = QLabel(_(
                '<h1>Online Account</h1>'
                'To use your free <a href="http://anki.ichi2.net/">online account</a>,<br>'
                "please enter your details below.<br>"))
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
            bb = QDialogButtonBox(QDialogButtonBox.Ok)
            self.connect(bb, SIGNAL("accepted()"), d.accept)
            vbox.addWidget(bb)
            d.setLayout(vbox)
            d.exec_()
            self.config['syncUsername'] = unicode(user.text())
            self.config['syncPassword'] = unicode(passwd.text())

    def onOpenOnline(self):
        self.ensureSyncParams()
        if not self.saveAndClose(exit=True): return
        self.deck = DeckStorage.Deck()
        # ensure all changes come to us
        self.deck.modified = 0
        self.deck.s.commit()
        self.deck.syncName = "something"
        self.deck.lastLoaded = self.deck.modified
        if self.config['syncUsername'] and self.config['syncPassword']:
            if self.syncDeck(onlyMerge=True):
                return
        self.deck = None
        self.moveToState("initial")

    def onOpen(self, samples=False):
        key = _("Deck files (*.anki)")
        if samples: defaultDir = self.getSamplesDir()
        else: defaultDir = self.getDefaultDir()
        file = QFileDialog.getOpenFileName(self, _("Open deck"),
                                           defaultDir, key)
        file = unicode(file)
        if not file:
            return False
        if samples:
            # we need to copy into a writeable location
            new = DeckStorage.newDeckPath()
            shutil.copyfile(file, new)
            file = new
        ret = self.loadDeck(file)
        if not ret:
            if ret is None:
                ui.utils.showWarning(_("Unable to load file."))
            self.deck = None
            return False
        else:
            self.updateRecentFiles(file)
            return True

    def onOpenSamples(self):
        self.onOpen(samples=True)

    def onSave(self):
        if self.deck.modifiedSinceSave():
            self.saveDeck()
        else:
            self.setStatus(_("Deck is not modified."))

            self.updateTitleBar()

    def onSaveAs(self):
        "Prompt for a file name, then save."
        title = _("Save deck")
        dir = os.path.dirname(self.deck.path)
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
        if os.path.exists(file):
            # check for existence after extension
            if not ui.utils.askUser(
                "This file exists. Are you sure you want to overwrite it?"):
                return
        self.deck = self.deck.saveAs(file)
        self.updateTitleBar()
        self.moveToState("initial")

    def saveDeck(self):
        self.setStatus(_("Saving.."))
        self.deck.save()
        self.updateRecentFiles(self.deck.path)
        self.updateTitleBar()
        self.setStatus(_("Saving..done"))

    # Opening and closing the app
    ##########################################################################

    def prepareForExit(self):
        "Save config and window geometry."
        self.runHook('quit')
        self.help.hide()
        self.config['mainWindowGeom'] = self.saveGeometry()
        # save config
        try:
            self.config.save()
        except (IOError, OSError), e:
            ui.utils.showWarning(_("Anki was unable to save your "
                                   "configuration file:\n%s" % e))

    def closeEvent(self, event):
        "User hit the X button, etc."
        if not self.saveAndClose(exit=True):
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
        elif str == "sample":
            self.onOpenSamples()
        elif str == "open":
            self.onOpen()
        elif str == "openrem":
            self.onOpenOnline()
        if str == "addfacts":
            if not self.deck:
                self.onNew()
            self.onAddCard()

    def setupAnchors(self):
        self.anchorPrefixes = {
            'welcome': self.onWelcomeAnchor,
            }
        self.connect(self.mainWin.mainText,
                     SIGNAL("anchorClicked(QUrl)"),
                     self.anchorClicked)

    def anchorClicked(self, url):
        # prevent the link being handled
        self.mainWin.mainText.setSource(QUrl(""))
        addr = unicode(url.toString())
        fields = addr.split(":")
        if len(fields) > 1 and fields[0] in self.anchorPrefixes:
            self.anchorPrefixes[fields[0]](*fields[1:])
        else:
            # open in browser
            QDesktopServices.openUrl(QUrl(url))

    # Toolbar
    ##########################################################################

    def setupToolbar(self):
        mw = self.mainWin
        if not self.config['showToolbar']:
            self.removeToolBar(mw.toolBar)
            mw.toolBar.hide()
        if self.config['simpleToolbar']:
            self.removeToolBar(mw.toolBar)
            mw.toolBar.hide()
            mw.toolBar = QToolBar(self)
            mw.toolBar.addAction(mw.actionAddcards)
            mw.toolBar.addAction(mw.actionEditdeck)
            mw.toolBar.addAction(mw.actionRepeatAudio)
            mw.toolBar.addAction(mw.actionMarkCard)
            mw.toolBar.addAction(mw.actionGraphs)
            mw.toolBar.addAction(mw.actionDisplayProperties)
            self.addToolBar(Qt.TopToolBarArea, mw.toolBar)
        mw.toolBar.setIconSize(QSize(self.config['iconSize'],
                                     self.config['iconSize']))

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
            self.setStatus(_("No expression in current card."))

    def onLookupMeaning(self):
        self.initLookup()
        try:
            self.lookup.alc(self.currentCard.fact['Meaning'])
        except KeyError:
            self.setStatus(_("No meaning in current card."))

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
        rep += _("<a href=py:miss>Missing Kanji</a><br>")
        self.help.showText(rep, py={"miss": self.onMissingStats})

    def onMissingStats(self):
        ks = anki.stats.KanjiStats(self.deck)
        ks.genKanjiSets()
        self.help.showText(ks.missingReport())

    def onDeckStats(self):
        txt = anki.stats.DeckStats(self.deck).report()
        self.help.showText(txt)

    def onCardStats(self):
        self.addHook("showQuestion", self.onCardStats)
        self.addHook("helpChanged", self.removeCardStatsHook)
        txt = ""
        if self.currentCard:
            txt += _("<h1>Current card</h1>")
            txt += anki.stats.CardStats(self.deck, self.currentCard).report()
        if self.lastCard and self.lastCard != self.currentCard:
            txt += _("<h1>Last card</h1>")
            txt += anki.stats.CardStats(self.deck, self.lastCard).report()
        if not txt:
            txt = _("No current card or last card.")
        self.help.showText(txt, key="cardStats")

    def removeCardStatsHook(self):
        "Remove the update hook if the help menu was changed."
        if self.help.currentKey != "cardStats":
            self.removeHook("showQuestion", self.onCardStats)

    def onShowGraph(self):
        self.setStatus(_("Loading graphs (may take time).."))
        self.app.processEvents()
        import anki.graphs
        if anki.graphs.graphsAvailable():
            try:
                ui.dialogs.get("Graphs", self, self.deck)
            except (ImportError, ValueError):
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

    def onKanjiOccur(self):
        self.setStatus(_("Generating report (may take time).."))
        self.app.processEvents()
        import tempfile
        (fd, name) = tempfile.mkstemp(suffix=".html")
        f = os.fdopen(fd, 'w')
        ko = anki.stats.KanjiOccurStats(self.deck)
        ko.reportFile(f)
        f.close()
        if sys.platform == "win32":
            url = "file:///"
        else:
            url = "file://"
        url += os.path.abspath(name)
        QDesktopServices.openUrl(QUrl(url))

    # Marking, suspending and undoing
    ##########################################################################

    def onMark(self, toggled):
        if self.currentCard.hasTag("Marked"):
            self.currentCard.tags = deleteTags("Marked", self.currentCard.tags)
        else:
            self.currentCard.tags = addTags("Marked", self.currentCard.tags)
        self.currentCard.setModified()
        self.deck.setModified()

    def onSuspend(self):
        self.currentCard.tags = addTags("Suspended", self.currentCard.tags)
        self.deck.updatePriority(self.currentCard)
        self.currentCard.setModified()
        self.deck.setModified()
        self.lastScheduledTime = None
        self.moveToState("initial")

    def onUndoAnswer(self):
        # quick and dirty undo for now
        self.currentCard = None
        self.deck.s.flush()
        self.lastCardBackup.toDB(self.deck.s)
        self.reset()

    # Other menu operations
    ##########################################################################

    def onAddCard(self):
        ui.dialogs.get("AddCards", self)

    def onEditDeck(self):
        ui.dialogs.get("CardList", self)

    def onDeckProperties(self):
        self.deckProperties = ui.deckproperties.DeckProperties(self)

    def onModelProperties(self):
        if self.currentCard:
            model = self.currentCard.fact.model
        else:
            model = self.deck.currentModel
        ui.modelproperties.ModelProperties(self, model)

    def onDisplayProperties(self):
        ui.dialogs.get("DisplayProperties", self)

    def onPrefs(self):
        ui.preferences.Preferences(self, self.config)

    def onReportBug(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appIssueTracker))

    def onForum(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appForum))

    def onAbout(self):
        ui.about.show(self)

    def onActiveTags(self):
        ui.activetags.show(self)

    # Importing & exporting
    ##########################################################################

    def onImport(self):
        if self.deck is None:
            self.onNew()
        if self.deck is not None:
            ui.importing.ImportDialog(self)

    def onExport(self):
        ui.exporting.ExportDialog(self)

    # Cramming
    ##########################################################################

    def onCram(self):
        (s, ret) = QInputDialog.getText(self, _("Anki"), _("Tags to cram:"))
        if not ret:
            return
        s = unicode(s)
        self.deck.save()
        # open tmp deck
        import tempfile
        dir = tempfile.mkdtemp(prefix="anki-cram")
        path = os.path.join(dir, "cram.anki")
        from anki.exporting import AnkiExporter
        e = AnkiExporter(self.deck)
        if s:
            e.limitTags = parseTags(s)
        e.exportInto(path)
        # load
        self.loadDeck(path)
        self.config['recentDeckPaths'].pop(0)
        self.deck.newCardsPerDay = 999999
        self.deck.delay0 = 300
        self.deck.delay1 = 600
        self.deck.hardIntervalMin = 0.01388
        self.deck.hardIntervalMax = 0.02083
        self.deck.midIntervalMin = 0.0416
        self.deck.midIntervalMax = 0.0486
        self.deck.easyIntervalMin = 0.2083
        self.deck.easyIntervalMax = 0.25
        self.rebuildQueue()

    # Language handling
    ##########################################################################

    def setLang(self):
        "Set the user interface language."
        languageDir=os.path.join(ankiqt.modDir, "locale")
        self.languageTrans = gettext.translation('ankiqt', languageDir,
                                            languages=[self.config["interfaceLang"]],
                                            fallback=True)
        self.installTranslation()
        if getattr(self, 'mainWin', None):
            self.mainWin.retranslateUi(self)
            self.alterShortcuts()
        anki.lang.setLang(self.config["interfaceLang"])
        self.updateTitleBar()

    def getTranslation(self, text):
        return self.languageTrans.ugettext(text)

    def installTranslation(self):
        import __builtin__
        __builtin__.__dict__['_'] = self.getTranslation

    # Syncing
    ##########################################################################

    def syncDeck(self, interactive=True, create=False, onlyMerge=False,
                 reload=True, checkSources=True):
        "Synchronise a deck with the server."
        # vet input
        self.ensureSyncParams()
        u=self.config['syncUsername']
        p=self.config['syncPassword']
        if not u or not p:
            return
        if self.deck and not self.deck.syncName:
            if interactive:
                self.onDeckProperties()
            return
        if self.deck is None and self.deckPath is None:
            # qt on linux incorrectly accepts shortcuts for disabled actions
            return
        if self.deck:
            # save first, so we can rollback on failure
            self.deck.save()
            self.deck.close()
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
            self.deck = None
            self.loadAfterSync = reload
        # bug triggered by preferences dialog - underlying c++ widgets are not
        # garbage collected until the middle of the child thread
        import gc; gc.collect()
        self.bodyView.clearWindow()
        self.bodyView.flush()
        self.syncThread = ui.sync.Sync(self, u, p, interactive, create,
                                       onlyMerge, self.sourcesToCheck)
        self.connect(self.syncThread, SIGNAL("setStatus"), self.setSyncStatus)
        self.connect(self.syncThread, SIGNAL("showWarning"), self.showSyncWarning)
        self.connect(self.syncThread, SIGNAL("moveToState"), self.moveToState)
        self.connect(self.syncThread, SIGNAL("noMatchingDeck"), self.selectSyncDeck)
        self.connect(self.syncThread, SIGNAL("syncClockOff"), self.syncClockOff)
        self.connect(self.syncThread, SIGNAL("cleanNewDeck"), self.cleanNewDeck)
        self.connect(self.syncThread, SIGNAL("syncFinished"), self.syncFinished)
        self.syncThread.start()
        self.setEnabled(False)
        while not self.syncThread.isFinished():
            self.app.processEvents()
            self.syncThread.wait(100)
        self.setEnabled(True)
        return self.syncThread.ok

    def syncFinished(self):
        "Reopen after sync finished."
        if self.loadAfterSync:
            self.loadDeck(self.deckPath, sync=False)
            self.deck.syncName = self.syncName
            self.deck.s.flush()
            self.deck.s.commit()
        else:
            self.moveToState("noDeck")
        self.deckPath = None

    def selectSyncDeck(self, decks, create=True):
        name = ui.sync.DeckChooser(self, decks, create).getName()
        self.syncName = name
        if name:
            if name == self.syncName:
                self.syncDeck(create=True)
            else:
                self.syncDeck()
        else:
            if not create:
                # called via 'new' - close
                self.cleanNewDeck()
            else:
                self.syncFinished()

    def cleanNewDeck(self):
        "Unload a new deck if an initial sync failed."
        self.deck = None
        self.moveToState("initial")

    def setSyncStatus(self, text, *args):
        self.setStatus(text, *args)
        self.mainWin.mainText.append("<font size=+6>" + text + "</font>")

    def syncClockOff(self, diff):
        ui.utils.showWarning(
            _("Your computer clock is not set to the correct time.\n"
              "It is off by %d seconds.\n\n"
              "Since this can cause many problems with syncing,\n"
              "syncing is disabled until you fix the problem.")
            % diff)
        self.syncFinished()

    def showSyncWarning(self, text):
        ui.utils.showWarning(text, self)
        self.setStatus("")

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
        "ModelProperties",
        "UndoAnswer",
        "Export",
        "MarkCard",
        "Graphs",
        "Dstats",
        "Kstats",
        "Cstats",
        )

    deckRelatedMenus = (
        "Tools",
        "Advanced",
        )

    def connectMenuActions(self):
        m = self.mainWin
        s = SIGNAL("triggered()")
        self.connect(m.actionNew, s, self.onNew)
        self.connect(m.actionOpenOnline, s, self.onOpenOnline)
        self.connect(m.actionOpen, s, self.onOpen)
        self.connect(m.actionOpenSamples, s, self.onOpenSamples)
        self.connect(m.actionSave, s, self.onSave)
        self.connect(m.actionSaveAs, s, self.onSaveAs)
        self.connect(m.actionClose, s, self.saveAndClose)
        self.connect(m.actionExit, s, self, SLOT("close()"))
        self.connect(m.actionSyncdeck, s, self.syncDeck)
        self.connect(m.actionDeckProperties, s, self.onDeckProperties)
        self.connect(m.actionDisplayProperties, s,self.onDisplayProperties)
        self.connect(m.actionAddcards, s, self.onAddCard)
        self.connect(m.actionEditdeck, s, self.onEditDeck)
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
        self.connect(m.actionModelProperties, s, self.onModelProperties)
        self.connect(m.actionRepeatQuestionAudio, s, self.onRepeatQuestion)
        self.connect(m.actionRepeatAnswerAudio, s, self.onRepeatAnswer)
        self.connect(m.actionRepeatAudio, s, self.onRepeatAudio)
        self.connect(m.actionUndoAnswer, s, self.onUndoAnswer)
        self.connect(m.actionCheckDatabaseIntegrity, s, self.onCheckDB)
        self.connect(m.actionOptimizeDatabase, s, self.onOptimizeDB)
        self.connect(m.actionMergeModels, s, self.onMergeModels)
        self.connect(m.actionCheckMediaDatabase, s, self.onCheckMediaDB)
        self.connect(m.actionCram, s, self.onCram)
        self.connect(m.actionGetPlugins, s, self.onGetPlugins)
        self.connect(m.actionOpenPluginFolder, s, self.onOpenPluginFolder)
        self.connect(m.actionEnableAllPlugins, s, self.onEnableAllPlugins)
        self.connect(m.actionDisableAllPlugins, s, self.onDisableAllPlugins)
        self.connect(m.actionActiveTags, s, self.onActiveTags)

    def enableDeckMenuItems(self, enabled=True):
        "setEnabled deck-related items."
        for item in self.deckRelatedMenus:
            getattr(self.mainWin, "menu" + item).setEnabled(enabled)
        for item in self.deckRelatedMenuItems:
            getattr(self.mainWin, "action" + item).setEnabled(enabled)

    def disableDeckMenuItems(self):
        "Disable deck-related items."
        self.enableDeckMenuItems(enabled=False)

    def updateTitleBar(self):
        "Display the current deck and card count in the titlebar."
        title=ankiqt.appName + " " + ankiqt.appVersion
        if self.deck != None:
            deckpath = self.deck.name()
            if self.deck.modifiedSinceSave():
                deckpath += "*"
            title = _("%(path)s (%(facts)d facts, %(cards)d cards)"
                      " - %(title)s") % {
                "path": deckpath,
                "title": title,
                "cards": self.deck.cardCount(),
                "facts": self.deck.factCount(),
                }
        self.setWindowTitle(title)

    def setStatus(self, text, timeout=3000):
        self.mainWin.statusbar.showMessage(text, timeout)

    def onStartHere(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appHelpSite))

    def alterShortcuts(self):
        if sys.platform.startswith("darwin"):
            self.mainWin.actionAddcards.setShortcut(_("Ctrl+D"))
            self.mainWin.actionClose.setShortcut("")

    def updateMarkAction(self):
        self.mainWin.actionMarkCard.blockSignals(True)
        if self.currentCard.hasTag("Marked"):
            self.mainWin.actionMarkCard.setChecked(True)
        else:
            self.mainWin.actionMarkCard.setChecked(False)
        self.mainWin.actionMarkCard.blockSignals(False)

    def disableCardMenuItems(self):
        self.mainWin.actionUndoAnswer.setEnabled(not not self.lastCard)
        self.mainWin.actionMarkCard.setEnabled(False)
        self.mainWin.actionSuspendCard.setEnabled(False)
        self.mainWin.actionRepeatQuestionAudio.setEnabled(False)
        self.mainWin.actionRepeatAnswerAudio.setEnabled(False)
        self.mainWin.actionRepeatAudio.setEnabled(False)

    def enableCardMenuItems(self):
        self.mainWin.actionUndoAnswer.setEnabled(not not self.lastCard)
        self.mainWin.actionMarkCard.setEnabled(True)
        self.mainWin.actionSuspendCard.setEnabled(True)
        self.mainWin.actionRepeatQuestionAudio.setEnabled(
            hasSound(self.currentCard.question))
        self.mainWin.actionRepeatAnswerAudio.setEnabled(
            hasSound(self.currentCard.answer) and self.state != "getQuestion")
        self.mainWin.actionRepeatAudio.setEnabled(
            self.mainWin.actionRepeatQuestionAudio.isEnabled() or
            self.mainWin.actionRepeatAnswerAudio.isEnabled())

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
            _("Your computer clock is not set to the correct time.\n"
              "It is %(sec)d seconds %(type)s.\n"
              " Please ensure it is set correctly and then restart Anki.")
            % { "sec": abs(diff),
                "type": ret }
         )

    # Plugins
    ##########################################################################

    def pluginsFolder(self):
        dir = self.config.configPath
        file = os.path.join(dir, "custom.py")
        return os.path.join(dir, "plugins")

    def loadPlugins(self):
        plugdir = self.pluginsFolder()
        sys.path.insert(0, plugdir)
        plugins = self.enabledPlugins()
        plugins.sort()
        for plugin in plugins:
            try:
                nopy = plugin.replace(".py", "")
                __import__(nopy)
            except:
                print "Error in %s" % plugin
                traceback.print_exc()

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

    def onOpenPluginFolder(self):
        if sys.platform == "win32":
            # reuse our process handling code from latex
            anki.latex.call(["explorer", self.pluginsFolder().encode(
                sys.getfilesystemencoding())])
        else:
            QDesktopServices.openUrl(QUrl("file://" + self.pluginsFolder()))

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

    # Font localisation
    ##########################################################################

    def setupFonts(self):
        for (s, p) in anki.fonts.substitutions():
            QFont.insertSubstitution(s, p)

    # Sounds
    ##########################################################################

    def onRepeatQuestion(self):
        playFromText(self.currentCard.question)

    def onRepeatAnswer(self):
        playFromText(self.currentCard.answer)

    def onRepeatAudio(self):
        playFromText(self.currentCard.question)
        if self.state != "showQuestion":
            playFromText(self.currentCard.answer)

    # Advanced features
    ##########################################################################

    def onCheckDB(self):
        "True if no problems"
        ret = self.deck.fixIntegrity()
        if ret == "ok":
            ret = _("""\
No problems found. Some data structures have been rebuilt in case
they were causing problems. On the next sync, all cards will be
sent to the server.""")
            ui.utils.showInfo(ret)
            ret = True
        else:
            ret = _("Problems found:\n%s") % ret
            ui.utils.showWarning(ret)
            ret = False
        self.rebuildQueue()
        return ret

    def onOptimizeDB(self):
        size = self.deck.optimize()
        ui.utils.showInfo("Database optimized.\nShrunk by %d bytes" % size)

    def onMergeModels(self):
        ret = self.deck.canMergeModels()
        if ret[0] == "ok":
            if not ret[1]:
                ui.utils.showInfo(_(
                    "No models found to merge. If you want to merge models,\n"
                    "all models must have the same name, and must not be\n"
                    "from another person's deck."))
                return
            if ui.utils.askUser(_(
                "Would you like to merge models that have the same name?")):
                self.deck.mergeModels(ret[1])
                ui.utils.showInfo(_("Merge complete."))
        else:
            ui.utils.showWarning(_("""%s.
Anki can only merge models if they have exactly
the same field count and card count.""") % ret[1])

    def onCheckMediaDB(self):
        mb = QMessageBox(self)
        mb.setText(_("""\
Would you like to remove unused files from the media directory, and
tag or delete references to missing files?"""))
        bTag = QPushButton("Tag facts missing media")
        mb.addButton(bTag, QMessageBox.RejectRole)
        bDelete = QPushButton("Delete references to missing media")
        mb.addButton(bDelete, QMessageBox.RejectRole)
        bCancel = QPushButton("Cancel")
        mb.addButton(bCancel, QMessageBox.RejectRole)
        mb.exec_()
        if mb.clickedButton() == bTag:
            (missing, unused) = rebuildMediaDir(self.deck, False)
        elif mb.clickedButton() == bDelete:
            (missing, unused) = rebuildMediaDir(self.deck, True)
        else:
            return
        ui.utils.showInfo(_(
                "%(a)d missing references.\n"
                "%(b)d unused files removed.") % {
            'a': missing,
            'b': unused})
