# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from anki.utils import parseTags, canonifyTags, joinTags
import re, sys

class TagEdit(QLineEdit):

    def __init__(self, parent, *args):
        QLineEdit.__init__(self, parent, *args)
        self.model = QStringListModel()
        self.completer = TagCompleter(self.model, parent, self)
        self.completer.setCompletionMode(QCompleter.PopupCompletion)
        self.completer.setCaseSensitivity(Qt.CaseInsensitive)
        self.setCompleter(self.completer)

    def setDeck(self, deck, tags="user"):
        "Set the current deck, updating list of available tags."
        self.deck = deck
        tags = self.deck.allTags()
        tags.sort(key=lambda x: x.lower())
        self.model.setStringList(
            QStringList(tags))

    def addTags(self, tags):
        l = list(set([unicode(x) for x in list(self.model.stringList())] +
                 tags))
        l.sort(key=lambda x: x.lower())
        self.model.setStringList(QStringList(l))

    def focusOutEvent(self, evt):
        QLineEdit.focusOutEvent(self, evt)
        self.emit(SIGNAL("lostFocus"))

class TagCompleter(QCompleter):

    def __init__(self, model, parent, edit, *args):
        QCompleter.__init__(self, model, parent)
        self.tags = []
        self.edit = edit
        self.cursor = None

    def splitPath(self, str):
        str = unicode(str).strip()
        str = re.sub("  +", " ", str)
        self.tags = parseTags(str)
        self.tags.append(u"")
        p = self.edit.cursorPosition()
        self.cursor = str.count(" ", 0, p)
        return QStringList(self.tags[self.cursor])

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
