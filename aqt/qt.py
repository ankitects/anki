# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# imports are all in this file to make moving to pyside easier in the future

import sip, os
from anki.utils import isWin, isMac
sip.setapi('QString', 2)
sip.setapi('QVariant', 2)
sip.setapi('QUrl', 2)
from PyQt4.QtCore import *
from PyQt4.QtGui import *
from PyQt4.QtWebKit import QWebPage, QWebView, QWebSettings
from PyQt4.QtNetwork import QLocalServer, QLocalSocket

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

qtmajor = (QT_VERSION & 0xff0000) >> 16
qtminor = (QT_VERSION & 0x00ff00) >> 8

# qt4.6 doesn't support ruby tags
if qtmajor <= 4 and qtminor <= 6:
  import anki.template.furigana
  anki.template.furigana.ruby = r'<span style="display: inline-block; text-align: center; line-height: 1; white-space: nowrap; vertical-align: baseline; margin: 0; padding: 0"><span style="display: block; text-decoration: none; line-height: 1.2; font-weight: normal; font-size: 0.64em">\2</span>\1</span>'

if isWin or isMac:
    # we no longer use this, but want it included in the mac+win builds
    # so we don't break add-ons that use it. any new add-ons should use
    # the above variables instead
    from PyQt4 import pyqtconfig
