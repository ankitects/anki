# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from typing import List, Optional

from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.utils import TR, HelpPage, shortcut, tr


class NoteTypeChooser(QHBoxLayout):
    """
    Unlike the older modelchooser, this does not modify the "current model",
    so changes made here do not affect other parts of the UI. To read the
    currently selected notetype id, use .selected_notetype_id.

    By default, a chooser will pop up when the button is pressed. You can
    override this by providing `on_button_activated`. Call .choose_notetype()
    to run the normal behaviour.

    `on_notetype_changed` will be called with the new notetype ID if the user
    selects a different notetype, or if the currently-selected notetype is
    deleted.
    """

    def __init__(
        self,
        *,
        mw: AnkiQt,
        widget: QWidget,
        starting_notetype_id: int,
        on_button_activated: Optional[Callable[[], None]] = None,
        on_notetype_changed: Optional[Callable[[int], None]] = None,
        show_prefix_label: bool = True,
    ) -> None:
        QHBoxLayout.__init__(self)
        self._widget = widget  # type: ignore
        self.mw = mw
        if on_button_activated:
            self.on_button_activated = on_button_activated
        else:
            self.on_button_activated = self.choose_notetype
        self._setup_ui(show_label=show_prefix_label)
        gui_hooks.state_did_reset.append(self.reset_state)
        self._selected_notetype_id = 0
        # triggers UI update; avoid firing changed hook on startup
        self.on_notetype_changed = None
        self.selected_notetype_id = starting_notetype_id
        self.on_notetype_changed = on_notetype_changed

    def _setup_ui(self, show_label: bool) -> None:
        self.setContentsMargins(0, 0, 0, 0)
        self.setSpacing(8)

        if show_label:
            self.label = QLabel(tr(TR.NOTETYPES_TYPE))
            self.addWidget(self.label)

        # button
        self.button = QPushButton()
        self.button.setToolTip(shortcut(tr(TR.QT_MISC_CHANGE_NOTE_TYPE_CTRLANDN)))
        qconnect(
            QShortcut(QKeySequence("Ctrl+N"), self._widget).activated,
            self.on_button_activated,
        )
        self.button.setAutoDefault(False)
        self.addWidget(self.button)
        qconnect(self.button.clicked, self.on_button_activated)
        sizePolicy = QSizePolicy(QSizePolicy.Policy(7), QSizePolicy.Policy(0))
        self.button.setSizePolicy(sizePolicy)
        self._widget.setLayout(self)

    def cleanup(self) -> None:
        gui_hooks.state_did_reset.remove(self.reset_state)

    def reset_state(self) -> None:
        self._ensure_selected_notetype_valid()

    def show(self) -> None:
        self._widget.show()  # type: ignore

    def hide(self) -> None:
        self._widget.hide()  # type: ignore

    def onEdit(self) -> None:
        import aqt.models

        aqt.models.Models(self.mw, self._widget)

    def choose_notetype(self) -> None:
        from aqt.studydeck import StudyDeck

        current = self.selected_notetype_name()

        # edit button
        edit = QPushButton(tr(TR.QT_MISC_MANAGE))
        qconnect(edit.clicked, self.onEdit)

        def nameFunc() -> List[str]:
            return sorted(self.mw.col.models.allNames())

        ret = StudyDeck(
            self.mw,
            names=nameFunc,
            accept=tr(TR.ACTIONS_CHOOSE),
            title=tr(TR.QT_MISC_CHOOSE_NOTE_TYPE),
            help=HelpPage.NOTE_TYPE,
            current=current,
            parent=self._widget,
            buttons=[edit],
            cancel=True,
            geomKey="selectModel",
        )
        if not ret.name:
            return

        notetype = self.mw.col.models.byName(ret.name)
        if (id := notetype["id"]) != self._selected_notetype_id:
            self.selected_notetype_id = id

    @property
    def selected_notetype_id(self) -> int:
        # theoretically this should not be necessary, as we're listening to
        # resets
        self._ensure_selected_notetype_valid()

        return self._selected_notetype_id

    @selected_notetype_id.setter
    def selected_notetype_id(self, id: int) -> None:
        if id != self._selected_notetype_id:
            self._selected_notetype_id = id
            self._ensure_selected_notetype_valid()
            self._update_button_label()
            if func := self.on_notetype_changed:
                func(self._selected_notetype_id)

    def selected_notetype_name(self) -> str:
        return self.mw.col.models.get(self.selected_notetype_id)["name"]

    def _ensure_selected_notetype_valid(self) -> None:
        if not self.mw.col.models.get(self._selected_notetype_id):
            self.selected_notetype_id = self.mw.col.models.all_names_and_ids()[0].id

    def _update_button_label(self) -> None:
        self.button.setText(self.selected_notetype_name().replace("&", "&&"))
