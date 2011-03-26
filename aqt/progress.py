# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time
from PyQt4.QtGui import *
from PyQt4.QtCore import *

# Progress info
##########################################################################

class ProgressManager(object):

    def __init__(self, mw):
        self.mw = mw
        self.app = QApplication.instance()
        self._win = None
        self._levels = 0
        self._mainThread = QThread.currentThread()

    # SQLite progress handler
    ##########################################################################

    def setupDB(self):
        "Install a handler in the current deck."
        self.lastDbProgress = 0
        self.inDB = False
        self.mw.deck.db.set_progress_handler(self._dbProgress, 100000)

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
        if self._mainThread != QThread.currentThread():
            return
        # ensure timers don't fire
        self.inDB = True
        # handle GUI events
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
                self.timer(100, func, repeat)
            else:
                func()
        t = QTimer(self.mw)
        if not repeat:
            t.setSingleShot(True)
        t.connect(t, SIGNAL("timeout()"), handler)
        t.start(ms)

    # Creating progress dialogs
    ##########################################################################

    def start(self, max=0, min=0, label=None, parent=None, immediate=False):
        self._levels += 1
        if self._levels > 1:
            return
        # setup window
        parent = parent or self.app.activeWindow() or self.mw
        label = label or _("Processing...")
        self._win = QProgressDialog(label, "", min, max, parent)
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
        else:
            self._shown = False
        self._counter = min
        self._min = min
        self._max = max
        self._firstTime = time.time()
        self._lastTime = time.time()
        self._disabled = False

    def update(self, label=None, value=None, process=True, maybeShow=True):
        #print self._min, self._counter, self._max, label, time.time() - self._lastTime
        if maybeShow:
            self._maybeShow()
        self._lastTime = time.time()
        if label:
            self._win.setLabelText(label)
        if self._max and self._shown:
            self._counter = value or (self._counter+1)
            self._win.setValue(self._counter)
        if process:
            self.app.processEvents(QEventLoop.ExcludeUserInputEvents)

    def finish(self):
        self._levels -= 1
        if self._levels == 0:
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
