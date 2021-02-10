# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from typing import Any

from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.utils import TR, HelpPage, shortcut, tr


class DeckChooser(QHBoxLayout):
    def __init__(
        self, mw: AnkiQt, widget: QWidget, label: bool = True, start: Any = None
    ) -> None:
        QHBoxLayout.__init__(self)
        self._widget = widget  # type: ignore
        self.mw = mw
        self.label = label
        self.setContentsMargins(0, 0, 0, 0)
        self.setSpacing(8)
        self.setupDecks()
        self._widget.setLayout(self)
        gui_hooks.current_note_type_did_change.append(self.onModelChangeNew)

    def setupDecks(self) -> None:
        if self.label:
            self.deckLabel = QLabel(tr(TR.DECKS_DECK))
            self.addWidget(self.deckLabel)
        # decks box
        self.deck = QPushButton(clicked=self.onDeckChange)  # type: ignore
        self.deck.setAutoDefault(False)
        self.deck.setToolTip(shortcut(tr(TR.QT_MISC_TARGET_DECK_CTRLANDD)))
        QShortcut(QKeySequence("Ctrl+D"), self._widget, activated=self.onDeckChange)  # type: ignore
        self.addWidget(self.deck)
        # starting label
        if self.mw.col.conf.get("addToCur", True):
            col = self.mw.col
            did = col.conf["curDeck"]
            if col.decks.isDyn(did):
                # if they're reviewing, try default to current card
                c = self.mw.reviewer.card
                if self.mw.state == "review" and c:
                    if not c.odid:
                        did = c.did
                    else:
                        did = c.odid
                else:
                    did = 1
            self.setDeckName(
                self.mw.col.decks.nameOrNone(did) or tr(TR.QT_MISC_DEFAULT)
            )
        else:
            self.setDeckName(
                self.mw.col.decks.nameOrNone(self.mw.col.models.current()["did"])
                or tr(TR.QT_MISC_DEFAULT)
            )
        # layout
        sizePolicy = QSizePolicy(QSizePolicy.Policy(7), QSizePolicy.Policy(0))
        self.deck.setSizePolicy(sizePolicy)

    def show(self) -> None:
        self._widget.show()  # type: ignore

    def hide(self) -> None:
        self._widget.hide()  # type: ignore

    def cleanup(self) -> None:
        gui_hooks.current_note_type_did_change.remove(self.onModelChangeNew)

    def onModelChangeNew(self, unused: Any = None) -> None:
        self.onModelChange()

    def onModelChange(self) -> None:
        if not self.mw.col.conf.get("addToCur", True):
            self.setDeckName(
                self.mw.col.decks.nameOrNone(self.mw.col.models.current()["did"])
                or tr(TR.QT_MISC_DEFAULT)
            )

    def onDeckChange(self) -> None:
        from aqt.studydeck import StudyDeck

        current = self.deckName()
        ret = StudyDeck(
            self.mw,
            current=current,
            accept=tr(TR.ACTIONS_CHOOSE),
            title=tr(TR.QT_MISC_CHOOSE_DECK),
            help=HelpPage.EDITING,
            cancel=False,
            parent=self._widget,
            geomKey="selectDeck",
        )
        if ret.name:
            self.setDeckName(ret.name)

    def setDeckName(self, name: str) -> None:
        self.deck.setText(name.replace("&", "&&"))
        self._deckName = name

    def deckName(self) -> str:
        return self._deckName

    def selectedId(self) -> int:
        # save deck name
        name = self.deckName()
        if not name.strip():
            did = 1
        else:
            did = self.mw.col.decks.id(name)
        return did
