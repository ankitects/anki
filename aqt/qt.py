# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# imports are all in this file to make moving to pyside easier in the future

import sip, os
sip.setapi('QString', 2)
sip.setapi('QVariant', 2)
sip.setapi('QUrl', 2)
from PyQt4.QtCore import *
from PyQt4.QtGui import *
from PyQt4.QtWebKit import QWebPage, QWebView, QWebSettings
from PyQt4.QtNetwork import QLocalServer, QLocalSocket
from PyQt4 import pyqtconfig

def debug():
  from PyQt4.QtCore import pyqtRemoveInputHook
  from pdb import set_trace
  pyqtRemoveInputHook()
  set_trace()

if os.environ.get("DEBUG"):
    import sys, traceback
    def info(type, value, tb):
        from PyQt4.QtCore import pyqtRemoveInputHook
        for line in traceback.format_exception(type, value, tb):
            sys.stdout.write(line)
        pyqtRemoveInputHook()
        from pdb import pm
        pm()
    sys.excepthook = info
