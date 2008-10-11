# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *

import re, os
import ankiqt

def showWarning(text, parent=None):
    "Show a small warning with an OK button."
    if not parent:
        parent = ankiqt.mw
    QMessageBox.warning(parent, "Anki", text)

def showInfo(text, parent=None):
    "Show a small info window with an OK button."
    if not parent:
        parent = ankiqt.mw
    QMessageBox.information(parent, "Anki", text)

def askUser(text, parent=None):
    "Show a yes/no question. Return true if yes."
    if not parent:
        parent = ankiqt.mw
    r = QMessageBox.question(parent, "Anki", text,
                             QMessageBox.Yes | QMessageBox.No)
    return r == QMessageBox.Yes

def getText(prompt, parent=None):
    if not parent:
        parent = ankiqt.mw
    (text, ok) = QInputDialog.getText(parent, "Anki", prompt)
    if not ok:
        return None
    return unicode(text)

def getFile(parent, title, dir, key):
    "Ask the user for a file. Use DIR as config variable."
    dirkey = dir+"Directory"
    file = unicode(QFileDialog.getOpenFileName(
        parent, title, ankiqt.mw.config.get(dirkey, ""), key))
    if file:
        dir = os.path.dirname(file)
        ankiqt.mw.config[dirkey] = dir
    return file

def getSaveFile(parent, title, dir, key, ext):
    "Ask the user for a file to save. Use DIR as config variable."
    dirkey = dir+"Directory"
    file = unicode(QFileDialog.getSaveFileName(
        parent, title, ankiqt.mw.config.get(dirkey, ""), key,
        None, QFileDialog.DontConfirmOverwrite))
    if file:
        # add extension
        if not file.lower().endswith(ext):
            file += ext
        # save new default
        dir = os.path.dirname(file)
        ankiqt.mw.config[dirkey] = dir
        # check if it exists
        if os.path.exists(file):
            if not askUser(
                _("This file exists. Are you sure you want to overwrite it?"),
                parent):
                return None
    return file

def saveGeom(widget, key):
    key += "Geom"
    ankiqt.mw.config[key] = widget.saveGeometry()

def restoreGeom(widget, key):
    key += "Geom"
    if ankiqt.mw.config.get(key):
        widget.restoreGeometry(ankiqt.mw.config[key])
