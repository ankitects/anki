# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from anki.utils import parseTags

class TagEdit(QLineEdit):

    def __init__(self, parent, *args):
        QLineEdit.__init__(self, parent, *args)
        self.model = QStringListModel()
        self.completer = TagCompleter(self.model, parent)
        self.completer.setCompletionMode(QCompleter.UnfilteredPopupCompletion)
        self.completer.setCaseSensitivity(Qt.CaseInsensitive)
        self.setCompleter(self.completer)

    def setDeck(self, deck):
        "Set the current deck, updating list of available tags."
        self.deck = deck
        tags = self.deck.allTags()
        self.model.setStringList(
            QStringList(sorted(tags)))

    def keyPressEvent(self, evt):
        if evt.key() in (Qt.Key_Enter,
                         Qt.Key_Return):
            evt.accept()
            cur = self.completer.currentCompletion()
            if cur and not str(cur).strip().endswith(","):
                self.setText(self.completer.currentCompletion())
            else:
                self.completer.popup().close()
        else:
            QLineEdit.keyPressEvent(self, evt)

class TagCompleter(QCompleter):

    def __init__(self, *args):
        QCompleter.__init__(self, *args)
        self.tags = []

    def splitPath(self, str):
        self.tags = parseTags(unicode(str))
        if self.tags:
            return QStringList(self.tags[-1])
        return QStringList("")

    def pathFromIndex(self, idx):
        ret = QCompleter.pathFromIndex(self, idx)
        self.tags = self.tags[0:-1]
        self.tags.append(unicode(ret))
        return ", ".join(self.tags)
