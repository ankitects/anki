# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import Optional

from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.utils import HelpPage, shortcut, tr


class ModelChooser(QHBoxLayout):
    "New code should prefer NotetypeChooser."

    def __init__(
        self,
        mw: AnkiQt,
        widget: QWidget,
        label: bool = True,
        on_activated: Optional[Callable[[], None]] = None,
    ) -> None:
        """If provided, on_activated() will be called when the button is clicked,
        and the caller can call .onModelChange() to pull up the dialog when they
        are ready."""
        QHBoxLayout.__init__(self)
        self._widget = widget  # type: ignore
        self.mw = mw
        self.deck = mw.col
        self.label = label
        if on_activated:
            self.on_activated = on_activated
        else:
            self.on_activated = self.onModelChange
        self.setContentsMargins(0, 0, 0, 0)
        self.setSpacing(8)
        self.setupModels()
        gui_hooks.state_did_reset.append(self.onReset)
        self._widget.setLayout(self)

    def setupModels(self) -> None:
        if self.label:
            self.modelLabel = QLabel(tr.notetypes_type())
            self.addWidget(self.modelLabel)
        # models box
        self.models = QPushButton()
        self.models.setToolTip(shortcut(tr.qt_misc_change_note_type_ctrlandn()))
        QShortcut(QKeySequence("Ctrl+N"), self._widget, activated=self.on_activated)  # type: ignore
        self.models.setAutoDefault(False)
        self.addWidget(self.models)
        qconnect(self.models.clicked, self.onModelChange)
        # layout
        sizePolicy = QSizePolicy(QSizePolicy.Policy(7), QSizePolicy.Policy(0))
        self.models.setSizePolicy(sizePolicy)
        self.updateModels()

    def cleanup(self) -> None:
        gui_hooks.state_did_reset.remove(self.onReset)

    def onReset(self) -> None:
        self.updateModels()

    def show(self) -> None:
        self._widget.show()  # type: ignore

    def hide(self) -> None:
        self._widget.hide()  # type: ignore

    def onEdit(self) -> None:
        import aqt.models

        aqt.models.Models(self.mw, self._widget)

    def onModelChange(self) -> None:
        from aqt.studydeck import StudyDeck

        current = self.deck.models.current()["name"]
        # edit button
        edit = QPushButton(tr.qt_misc_manage(), clicked=self.onEdit)  # type: ignore

        def nameFunc() -> list[str]:
            return [nt.name for nt in self.deck.models.all_names_and_ids()]

        def callback(ret: StudyDeck) -> None:
            if not ret.name:
                return
            m = self.deck.models.by_name(ret.name)
            self.deck.conf["curModel"] = m["id"]
            cdeck = self.deck.decks.current()
            cdeck["mid"] = m["id"]
            self.deck.decks.save(cdeck)
            gui_hooks.current_note_type_did_change(current)
            self.mw.reset()

        StudyDeck(
            self.mw,
            names=nameFunc,
            accept=tr.actions_choose(),
            title=tr.qt_misc_choose_note_type(),
            help=HelpPage.NOTE_TYPE,
            current=current,
            parent=self._widget,
            buttons=[edit],
            cancel=True,
            geomKey="selectModel",
            callback=callback,
        )

    def updateModels(self) -> None:
        self.models.setText(self.deck.models.current()["name"].replace("&", "&&"))
