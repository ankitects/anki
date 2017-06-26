# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import sys, traceback
import cgi

from anki.lang import _
from aqt.qt import *
from aqt.utils import showText, showWarning

if not os.environ.get("DEBUG"):
    def excepthook(etype,val,tb):
        sys.stderr.write("Caught exception:\n%s%s\n" % (
            ''.join(traceback.format_tb(tb)),
            '{0}: {1}'.format(etype, val)))
    sys.excepthook = excepthook

class ErrorHandler(QObject):
    "Catch stderr and write into buffer."
    ivl = 100

    errorTimer = pyqtSignal()

    def __init__(self, mw):
        QObject.__init__(self, mw)
        self.mw = mw
        self.timer = None
        self.errorTimer.connect(self._setTimer)
        self.pool = ""
        self._oldstderr = sys.stderr
        sys.stderr = self

    def unload(self):
        sys.stderr = self._oldstderr
        sys.excepthook = None

    def write(self, data):
        # dump to stdout
        sys.stdout.write(data)
        # save in buffer
        self.pool += data
        # and update timer
        self.setTimer()

    def setTimer(self):
        # we can't create a timer from a different thread, so we post a
        # message to the object on the main thread
        self.errorTimer.emit()

    def _setTimer(self):
        if not self.timer:
            self.timer = QTimer(self.mw)
            self.timer.timeout.connect(self.onTimeout)
        self.timer.setInterval(self.ivl)
        self.timer.setSingleShot(True)
        self.timer.start()

    def tempFolderMsg(self):
        return _("""\
The permissions on your system's temporary folder are incorrect, and Anki is \
not able to correct them automatically. Please search for 'temp folder' in the \
Anki manual for more information.""")

    def onTimeout(self):
        error = cgi.escape(self.pool)
        self.pool = ""
        self.mw.progress.clear()
        if "abortSchemaMod" in error:
            return
        if "Pyaudio not" in error:
            return showWarning(_("Please install PyAudio"))
        if "install mplayer" in error:
            return showWarning(_("Please install mplayer"))
        if "no default input" in error.lower():
            return showWarning(_("Please connect a microphone, and ensure "
                                 "other programs are not using the audio device."))
        if "invalidTempFolder" in error:
            return showWarning(self.tempFolderMsg())
        if "Beautiful Soup is not an HTTP client" in error:
            return
        if "disk I/O error" in error:
            return showWarning(_("""\
An error occurred while accessing the database.

Possible causes:

- Antivirus, firewall, backup, or synchronization software may be \
  interfering with Anki. Try disabling such software and see if the \
  problem goes away.
- Your disk may be full.
- The Documents/Anki folder may be on a network drive.
- Files in the Documents/Anki folder may not be writeable.
- Your hard disk may have errors.

It's a good idea to run Tools>Check Database to ensure your collection \
is not corrupt.
"""))
        stdText = _("""\
An error occurred. It may have been caused by a harmless bug, <br>
or your deck may have a problem.
<p>To confirm it's not a problem with your deck, please run
<b>Tools &gt; Check Database</b>.
<p>If that doesn't fix the problem, please copy the following<br>
into a bug report:""")
        pluginText = _("""\
An error occurred in an add-on.<br><br>
Please start Anki while holding down the shift key, and see if<br>
the error goes away.<br><br>
If the error goes away, please report the issue on the add-on<br>
forum: %s
<br><br>If the error occurs even with add-ons disabled, please<br>
report the issue on our support site.
""")
        pluginText %= "https://anki.tenderapp.com/discussions/add-ons"
        if "addon" in error:
            txt = pluginText
        else:
            txt = stdText
        # show dialog
        txt = txt + "<div style='white-space: pre-wrap'>" + error + "</div>"
        showText(txt, type="html")
