# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# fixme: make sure not to optimize imports on this file

import sip
import os

# fix buggy ubuntu12.04 display of language selector
os.environ["LIBOVERLAY_SCROLLBAR"] = "0"

from anki.utils import isWin, isMac

from PyQt5.Qt import *
# trigger explicit message in case of missing libraries
# instead of silently failing to import
from PyQt5.QtWebEngineWidgets import QWebEnginePage

def debug():
  from PyQt5.QtCore import pyqtRemoveInputHook
  from pdb import set_trace
  pyqtRemoveInputHook()
  set_trace()

import sys, traceback

if os.environ.get("DEBUG"):
    def info(type, value, tb):
        from PyQt5.QtCore import pyqtRemoveInputHook
        for line in traceback.format_exception(type, value, tb):
            sys.stdout.write(line)
        pyqtRemoveInputHook()
        from pdb import pm
        pm()
    sys.excepthook = info

qtmajor = (QT_VERSION & 0xff0000) >> 16
qtminor = (QT_VERSION & 0x00ff00) >> 8
qtpoint = QT_VERSION & 0xff

if qtmajor != 5 or qtminor != 9:
    raise Exception("Anki only supports Qt 5.9.x at this time.")

# GUI code assumes python 3.6+
if sys.version_info[0] < 3 or sys.version_info[1] < 6:
    raise Exception("Anki requires Python 3.6+")
