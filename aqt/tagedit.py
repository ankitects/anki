# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import re, sys

class TagEdit(QLineEdit):

    # 0 = tags, 1 = groups
    def __init__(self, parent, type=0):
        QLineEdit.__init__(self, parent)
        self.deck = None
        self.model = QStringListModel()
        self.type = type
        if type == 0:
            self.completer = TagCompleter(self.model, parent, self)
        else:
            self.completer = QCompleter(self.model, parent)
        self.completer.setCompletionMode(QCompleter.UnfilteredPopupCompletion)
        self.completer.setCaseSensitivity(Qt.CaseInsensitive)
        self.setCompleter(self.completer)

    def setDeck(self, deck):
        "Set the current deck, updating list of available tags."
        self.deck = deck
        if self.type == 0:
            l = sorted(self.deck.tags.all())
        else:
            l = self.deck.groups.all()
        self.model.setStringList(l)

    def addTags(self, tags):
        l = list(set([unicode(x) for x in list(self.model.stringList())] +
                 tags))
        l.sort(key=lambda x: x.lower())
        self.model.setStringList(l)

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
        self.tags = self.parent.deck.tags.split(str)
        self.tags.append(u"")
        p = self.edit.cursorPosition()
        self.cursor = str.count(" ", 0, p)
        return self.tags[self.cursor]

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
