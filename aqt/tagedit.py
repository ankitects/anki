# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import re

class TagEdit(QLineEdit):

    # 0 = tags, 1 = decks
    def __init__(self, parent, type=0):
        QLineEdit.__init__(self, parent)
        self.col = None
        self.model = QStringListModel()
        self.type = type
        if type == 0:
            self.completer = TagCompleter(self.model, parent, self)
        else:
            self.completer = QCompleter(self.model, parent)
        self.completer.setCompletionMode(QCompleter.PopupCompletion)
        self.completer.setCaseSensitivity(Qt.CaseInsensitive)
        self.setCompleter(self.completer)

    def setCol(self, col):
        "Set the current col, updating list of available tags."
        self.col = col
        if self.type == 0:
            l = sorted(self.col.tags.all())
        else:
            l = sorted(self.col.decks.allNames())
        self.model.setStringList(l)

    def focusInEvent(self, evt):
        QLineEdit.focusInEvent(self, evt)
        self.showCompleter()

    def keyPressEvent(self, evt):
        if evt.key() in (Qt.Key_Enter, Qt.Key_Return):
            self.hideCompleter()
            QWidget.keyPressEvent(self, evt)
            return
        QLineEdit.keyPressEvent(self, evt)
        if not evt.text():
            # if it's a modifier, don't show
            return
        if evt.key() not in (
            Qt.Key_Enter, Qt.Key_Return, Qt.Key_Escape, Qt.Key_Space,
            Qt.Key_Tab, Qt.Key_Backspace, Qt.Key_Delete):
            self.showCompleter()

    def showCompleter(self):
        self.completer.setCompletionPrefix(self.text())
        self.completer.complete()

    def focusOutEvent(self, evt):
        QLineEdit.focusOutEvent(self, evt)
        self.emit(SIGNAL("lostFocus"))
        self.completer.popup().hide()

    def hideCompleter(self):
        self.completer.popup().hide()

class TagCompleter(QCompleter):

    def __init__(self, model, parent, edit, *args):
        QCompleter.__init__(self, model, parent)
        self.tags = []
        self.edit = edit
        self.cursor = None

    def splitPath(self, str):
        str = unicode(str).strip()
        str = re.sub("  +", " ", str)
        self.tags = self.edit.col.tags.split(str)
        self.tags.append(u"")
        p = self.edit.cursorPosition()
        self.cursor = str.count(" ", 0, p)
        return [self.tags[self.cursor]]

    def pathFromIndex(self, idx):
        if self.cursor is None:
            return self.edit.text()
        ret = QCompleter.pathFromIndex(self, idx)
        self.tags[self.cursor] = unicode(ret)
        try:
            self.tags.remove(u"")
        except ValueError:
            pass
        return " ".join(self.tags)
