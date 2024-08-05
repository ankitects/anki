# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Callable

from anki.collection import OpChanges
from anki.models import NotetypeId
from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.utils import HelpPage, shortcut, tr


class NotetypeChooser(QHBoxLayout):
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

    _selected_notetype_id: NotetypeId

    def __init__(
        self,
        *,
        mw: AnkiQt,
        widget: QWidget,
        starting_notetype_id: NotetypeId,
        on_button_activated: Callable[[], None] | None = None,
        on_notetype_changed: Callable[[NotetypeId], None] | None = None,
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
        gui_hooks.operation_did_execute.append(self.on_operation_did_execute)
        self._selected_notetype_id = NotetypeId(0)
        # triggers UI update; avoid firing changed hook on startup
        self.on_notetype_changed = None
        self.selected_notetype_id = starting_notetype_id
        self.on_notetype_changed = on_notetype_changed

    def _setup_ui(self, show_label: bool) -> None:
        self.setContentsMargins(0, 0, 0, 0)
        self.setSpacing(8)

        if show_label:
            self.label = QLabel(tr.notetypes_type())
            self.addWidget(self.label)

        # button
        self.button = QPushButton()
        self.button.setToolTip(shortcut(tr.qt_misc_change_note_type_ctrlandn()))
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
        gui_hooks.operation_did_execute.remove(self.on_operation_did_execute)

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
        edit = QPushButton(tr.qt_misc_manage())
        qconnect(edit.clicked, self.onEdit)

        def nameFunc() -> list[str]:
            return sorted(n.name for n in self.mw.col.models.all_names_and_ids())

        def callback(ret: StudyDeck) -> None:
            if not ret.name:
                return
            notetype = self.mw.col.models.by_name(ret.name)
            if (id := notetype["id"]) != self._selected_notetype_id:
                self.selected_notetype_id = id

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

    @property
    def selected_notetype_id(self) -> NotetypeId:
        # theoretically this should not be necessary, as we're listening to
        # resets
        self._ensure_selected_notetype_valid()

        return self._selected_notetype_id

    @selected_notetype_id.setter
    def selected_notetype_id(self, id: NotetypeId) -> None:
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
            self.selected_notetype_id = NotetypeId(
                self.mw.col.models.all_names_and_ids()[0].id
            )

    def _update_button_label(self) -> None:
        self.button.setText(self.selected_notetype_name().replace("&", "&&"))

    def on_operation_did_execute(
        self, changes: OpChanges, handler: object | None
    ) -> None:
        if changes.notetype:
            self._update_button_label()
