# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time
from aqt.qt import *

# fixme: if mw->subwindow opens a progress dialog with mw as the parent, mw
# gets raised on finish on compiz. perhaps we should be using the progress
# dialog as the parent?

# Progress info
##########################################################################

class ProgressManager(object):

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
        try:
            db.set_progress_handler(self._dbProgress, 10000)
        except:
            print """\
Your pysqlite2 is too old. Anki will appear frozen during long operations."""

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

    # DB-safe timers
    ##########################################################################
    # QTimer may fire in processEvents(). We provide a custom timer which
    # automatically defers until the DB is not busy.

    def timer(self, ms, func, repeat):
        def handler():
            if self.inDB:
                # retry in 100ms
                self.timer(100, func, False)
            else:
                func()
        t = QTimer(self.mw)
        if not repeat:
            t.setSingleShot(True)
        t.connect(t, SIGNAL("timeout()"), handler)
        t.start(ms)
        return t

    # Creating progress dialogs
    ##########################################################################

    class ProgressNoCancel(QProgressDialog):
        def closeEvent(self, evt):
            evt.ignore()
        def keyPressEvent(self, evt):
            if evt.key() == Qt.Key_Escape:
                evt.ignore()

    def start(self, max=0, min=0, label=None, parent=None, immediate=False):
        self._levels += 1
        if self._levels > 1:
            return
        # setup window
        parent = parent or self.app.activeWindow() or self.mw
        label = label or _("Processing...")
        self._win = self.ProgressNoCancel(label, "", min, max, parent)
        self._win.setWindowTitle("Anki")
        self._win.setCancelButton(None)
        self._win.setAutoClose(False)
        self._win.setAutoReset(False)
        self._win.setWindowModality(Qt.ApplicationModal)
        # we need to manually manage minimum time to show, as qt gets confused
        # by the db handler
        self._win.setMinimumDuration(100000)
        if immediate:
            self._shown = True
            self._win.show()
            self.app.processEvents()
        else:
            self._shown = False
        self._counter = min
        self._min = min
        self._max = max
        self._firstTime = time.time()
        self._lastUpdate = time.time()
        self._disabled = False

    def update(self, label=None, value=None, process=True, maybeShow=True):
        #print self._min, self._counter, self._max, label, time.time() - self._lastTime
        if maybeShow:
            self._maybeShow()
        elapsed = time.time() - self._lastUpdate
        if label:
            self._win.setLabelText(label)
        if self._max and self._shown:
            self._counter = value or (self._counter+1)
            self._win.setValue(self._counter)
        if process and elapsed >= 0.2:
            self.app.processEvents(QEventLoop.ExcludeUserInputEvents)
            self._lastUpdate = time.time()

    def finish(self):
        self._levels -= 1
        self._levels = max(0, self._levels)
        if self._levels == 0 and self._win:
            self._win.cancel()
            self._unsetBusy()

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
            self._shown = True
            self._win.show()
            self._setBusy()

    def _setBusy(self):
        self._disabled = True
        self.mw.app.setOverrideCursor(QCursor(Qt.WaitCursor))

    def _unsetBusy(self):
        self._disabled = False
        self.app.restoreOverrideCursor()

    def busy(self):
        "True if processing."
        return self._levels
