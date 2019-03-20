# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time
from aqt.qt import *
import aqt.forms
from anki.lang import _

# fixme: if mw->subwindow opens a progress dialog with mw as the parent, mw
# gets raised on finish on compiz. perhaps we should be using the progress
# dialog as the parent?

# Progress info
##########################################################################

class ProgressManager:

    def __init__(self, mw):
        self.mw = mw
        self.app = QApplication.instance()
        self.inDB = False
        self.blockUpdates = False
        self._win = None
        self._levels = 0

    # SQLite progress handler
    ##########################################################################

    def setupDB(self, db):
        "Install a handler in the current DB."
        self.lastDbProgress = 0
        self.inDB = False
        db.set_progress_handler(self._dbProgress, 10000)

    def _dbProgress(self):
        "Called from SQLite."
        # do nothing if we don't have a progress window
        if not self._win:
            return
        # make sure we're not executing too frequently
        if (time.time() - self.lastDbProgress) < 0.01:
            return
        self.lastDbProgress = time.time()
        # and we're in the main thread
        if not self.mw.inMainThread():
            return
        # ensure timers don't fire
        self.inDB = True
        # handle GUI events
        if not self.blockUpdates:
            self._maybeShow()
            self.app.processEvents(QEventLoop.ExcludeUserInputEvents)
        self.inDB = False

    # Safer timers
    ##########################################################################
    # QTimer may fire in processEvents(). We provide a custom timer which
    # automatically defers until the DB is not busy, and avoids running
    # while a progress window is visible.

    def timer(self, ms, func, repeat, requiresCollection=True):
        def handler():
            if self.inDB or self._levels:
                # retry in 100ms
                self.timer(100, func, False, requiresCollection)
            elif not self.mw.col and requiresCollection:
                # ignore timer events that fire after collection has been
                # unloaded
                print("Ignored progress func as collection unloaded: %s" % repr(func))
            else:
                func()
        t = QTimer(self.mw)
        if not repeat:
            t.setSingleShot(True)
        t.timeout.connect(handler)
        t.start(ms)
        return t

    # Creating progress dialogs
    ##########################################################################

    class ProgressDialog(QDialog):
        def __init__(self, parent):
            QDialog.__init__(self, parent)
            self.form = aqt.forms.progress.Ui_Dialog()
            self.form.setupUi(self)
            self._closingDown = False
            self.wantCancel = False

        def cancel(self):
            self._closingDown = True
            self.hide()

        def closeEvent(self, evt):
            if self._closingDown:
                evt.accept()
            else:
                self.wantCancel  = True
                evt.ignore()

        def keyPressEvent(self, evt):
            if evt.key() == Qt.Key_Escape:
                evt.ignore()
                self.wantCancel  = True

    def start(self, max=0, min=0, label=None, parent=None, immediate=False):
        self._levels += 1
        if self._levels > 1:
            return
        # setup window
        parent = parent or self.app.activeWindow()
        if not parent and self.mw.isVisible():
            parent = self.mw

        label = label or _("Processing...")
        self._win = self.ProgressDialog(parent)
        self._win.form.progressBar.setMinimum(min)
        self._win.form.progressBar.setMaximum(max)
        self._win.form.progressBar.setTextVisible(False)
        self._win.form.label.setText(label)
        self._win.setWindowTitle("Anki")
        self._win.setWindowModality(Qt.ApplicationModal)
        self._win.setMinimumWidth(300)
        if immediate:
            self._showWin()
        else:
            self._shown = False
        self._counter = min
        self._min = min
        self._max = max
        self._firstTime = time.time()
        self._lastUpdate = time.time()
        self._updating = False
        return self._win

    def update(self, label=None, value=None, process=True, maybeShow=True):
        #print self._min, self._counter, self._max, label, time.time() - self._lastTime
        if self._updating:
            return
        if maybeShow:
            self._maybeShow()
        if not self._shown:
            return
        elapsed = time.time() - self._lastUpdate
        if label:
            self._win.form.label.setText(label)
        if self._max:
            self._counter = value or (self._counter+1)
            self._win.form.progressBar.setValue(self._counter)
        if process and elapsed >= 0.2:
            self._updating = True
            self.app.processEvents(QEventLoop.ExcludeUserInputEvents)
            self._updating = False
            self._lastUpdate = time.time()

    def finish(self):
        self._levels -= 1
        self._levels = max(0, self._levels)
        if self._levels == 0 and self._win:
            self._closeWin()

    def clear(self):
        "Restore the interface after an error."
        if self._levels:
            self._levels = 1
            self.finish()

    def _maybeShow(self):
        if not self._levels:
            return
        if self._shown:
            self.update(maybeShow=False)
            return
        delta = time.time() - self._firstTime
        if delta > 0.5:
            self._showWin()

    def _showWin(self):
        self._shown = time.time()
        self._win.show()
        self._setBusy()

    def _closeWin(self):
        if self._shown:
            while True:
                # give the window system a second to present
                # window before we close it again - fixes
                # progress window getting stuck, especially
                # on ubuntu 16.10+
                elap = time.time() - self._shown
                if elap >= 0.5:
                    break
                self.app.processEvents(QEventLoop.ExcludeUserInputEvents)
        self._win.cancel()
        self._win = None
        self._shown = False
        self._unsetBusy()

    def _setBusy(self):
        self.mw.app.setOverrideCursor(QCursor(Qt.WaitCursor))

    def _unsetBusy(self):
        self.app.restoreOverrideCursor()

    def busy(self):
        "True if processing."
        return self._levels
