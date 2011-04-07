# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import sys
from aqt.utils import showText

class ErrorHandler(QObject):
    "Catch stderr and write into buffer."
    ivl = 100

    def __init__(self, mw):
        QObject.__init__(self, mw)
        self.mw = mw
        self.timer = None
        self.connect(self, SIGNAL("errorTimer"), self._setTimer)
        self.pool = ""
        sys.stderr = self

    def write(self, data):
        # make sure we have unicode
        if not isinstance(data, unicode):
            data = unicode(data, "utf8", "replace")
        # dump to stdout
        sys.stdout.write(data.encode("utf-8"))
        # save in buffer
        self.pool += data
        # and update timer
        self.setTimer()

    def setTimer(self):
        # we can't create a timer from a different thread, so we post a
        # message to the object on the main thread
        self.emit(SIGNAL("errorTimer"))

    def _setTimer(self):
        if not self.timer:
            self.timer = QTimer(self.mw)
            self.mw.connect(self.timer, SIGNAL("timeout()"), self.onTimeout)
        self.timer.setInterval(self.ivl)
        self.timer.setSingleShot(True)
        self.timer.start()

    def onTimeout(self):
        error = self.pool
        self.pool = ""
        stdText = _("""\
An error occurred. It may have been caused by a harmless bug, <br>
or your deck may have a problem.
<p>To confirm it's not a problem with your deck, please run
<b>Tools > Advanced > Check Database</b>.
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
        txt = txt + "<div style='white-space: pre-wrap'>" + error + "</div>"
        showText(txt, type="html")
        self.mw.progress.clear()
