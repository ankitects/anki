# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import functools
import re
from collections.abc import Callable
from copy import deepcopy

import anki.lang
import aqt
import aqt.forms
import aqt.operations
from anki.collection import OpChanges
from anki.utils import is_mac
from aqt import AnkiQt, colors
from aqt.ankihub import ankihub_login, ankihub_logout
from aqt.operations.collection import set_preferences
from aqt.profiles import VideoDriver
from aqt.qt import *
from aqt.sync import sync_login
from aqt.theme import Theme, theme_manager
from aqt.url_schemes import show_url_schemes_dialog
from aqt.utils import (
    HelpPage,
    add_ellipsis_to_action_label,
    askUser,
    disable_help_button,
    is_win,
    openHelp,
    showInfo,
    showWarning,
    tr,
)
from aqt.webview import AnkiWebView, AnkiWebViewKind


def _shortcut_contexts_overlap(action_id: str, other_id: str) -> bool:
    "Global shortcuts are active on every screen, so they clash with all others."
    context, other_context = action_id.split(".", 1)[0], other_id.split(".", 1)[0]
    return context == other_context or "global" in (context, other_context)


class ShortcutEdit(QKeySequenceEdit):
    focusChanged = pyqtSignal(bool)
    # Unlike editingFinished, which fires on a timer while the user may still
    # cancel with Esc, this fires only once the edit is confirmed.
    shortcutCommitted = pyqtSignal()

    def setClearButtonEnabled(self, enable: bool) -> None:
        super().setClearButtonEnabled(enable)
        if enable:
            self._set_button_icon("mdi:close")

    def setPlaceholderText(self, text: str) -> None:
        if line_edit := self.findChild(QLineEdit):
            line_edit.setPlaceholderText(text)
            # Qt puts back its own placeholder whenever the key is cleared or
            # recorded, so ours is restored before each repaint
            line_edit.installEventFilter(self)
            self._placeholder = text

    def use_cell_style(self) -> None:
        "Drop the frame, and leave unset keys blank until they're being edited."
        # Qt restores the placeholder text whenever the key changes, so it's
        # hidden by making it transparent instead
        self._placeholder_only_on_focus = True
        self._show_placeholder(False)

    def _show_placeholder(self, show: bool) -> None:
        if not getattr(self, "_placeholder_only_on_focus", False):
            return
        if line_edit := self.findChild(QLineEdit):
            # the table's grid lines already outline the cell. A stylesheet
            # replaces the palette, so the placeholder colour is set here too.
            placeholder = "" if show else "placeholder-text-color: transparent;"
            line_edit.setStyleSheet(
                "QLineEdit { border: none; border-radius: 0; "
                f"background: transparent; {placeholder} }}"
            )

    def _set_button_icon(self, icon: str) -> None:
        self._clear_button = self.findChild(QToolButton)
        if self._clear_button is not None:
            self._clear_button.setIcon(theme_manager.icon_from_resources(icon))

    def focusInEvent(self, event: QFocusEvent | None) -> None:
        self._key_before_edit = self.keySequence()
        self._editing = True
        self._set_button_icon("mdi:check")
        self._show_placeholder(True)
        if self._clear_button is not None:
            self._clear_button.setToolTip(tr.preferences_keybindings_confirm())
            self._clear_button.installEventFilter(self)
        super().focusInEvent(event)
        self.focusChanged.emit(True)

    def focusOutEvent(self, event: QFocusEvent | None) -> None:
        super().focusOutEvent(event)
        self._editing = False
        self._set_button_icon("mdi:close")
        if self._clear_button is not None:
            self._clear_button.setToolTip("")
        self._show_placeholder(False)
        self.focusChanged.emit(False)
        if self.keySequence() != self._key_before_edit:
            self.shortcutCommitted.emit()

    def eventFilter(self, watched: QObject | None, event: QEvent | None) -> bool:
        if (
            isinstance(watched, QLineEdit)
            and event is not None
            and event.type() == QEvent.Type.Paint
            and (placeholder := getattr(self, "_placeholder", None)) is not None
            and watched.placeholderText() != placeholder
        ):
            watched.setPlaceholderText(placeholder)
        if (
            watched == getattr(self, "_clear_button", None)
            and getattr(self, "_editing", False)
            and event is not None
            and event.type() == QEvent.Type.MouseButtonPress
        ):
            self.clearFocus()
            event.accept()
            return True
        return super().eventFilter(watched, event)

    def keyPressEvent(self, event: QKeyEvent | None) -> None:
        if event is not None and event.key() in (
            Qt.Key.Key_Return,
            Qt.Key.Key_Enter,
            Qt.Key.Key_Escape,
        ):
            if event.key() == Qt.Key.Key_Escape:
                self.setKeySequence(self._key_before_edit)
            self.clearFocus()
            event.accept()
            return
        super().keyPressEvent(event)


class ShortcutTable(QTableWidget):
    """One category of the keybindings grid. It grows to fit its rows so all
    categories scroll together, and keeps its key columns lined up with the
    keybinding filter above."""

    def __init__(self, parent: QWidget, key_filter: QWidget) -> None:
        super().__init__(parent)
        self.key_filter = key_filter
        key_filter.installEventFilter(self)
        self.setColumnCount(3)
        # the section titles and layout make the columns clear enough
        header = self.horizontalHeader()
        assert header is not None
        header.hide()
        header.setSectionResizeMode(QHeaderView.ResizeMode.Fixed)
        # Grid lines draw each cell's right and bottom edges, so the table only
        # needs a left and top border.
        border = f"1px solid {theme_manager.var(colors.BORDER_SUBTLE)}"
        self.setStyleSheet(
            "QTableView { border: none; border-radius: 0; "
            f"border-left: {border}; border-top: {border}; }}"
        )
        vertical_header = self.verticalHeader()
        assert vertical_header is not None
        vertical_header.hide()
        vertical_header.setSectionResizeMode(QHeaderView.ResizeMode.Fixed)
        self.setEditTriggers(QAbstractItemView.EditTrigger.NoEditTriggers)
        self.setSelectionMode(QAbstractItemView.SelectionMode.NoSelection)
        self.setFocusPolicy(Qt.FocusPolicy.NoFocus)
        self.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)

    def add_row(self, label: str, action_id: str, editors: list[ShortcutEdit]) -> None:
        row = self.rowCount()
        self.insertRow(row)
        item = QTableWidgetItem(label)
        item.setFlags(Qt.ItemFlag.ItemIsEnabled)
        item.setData(Qt.ItemDataRole.UserRole, action_id)
        self.setItem(row, 0, item)
        for column, editor in enumerate(editors, start=1):
            editor.use_cell_style()
            self.setCellWidget(row, column, editor)
            vertical_header = self.verticalHeader()
            assert vertical_header is not None
            vertical_header.setDefaultSectionSize(editor.sizeHint().height())

    def fit_height(self) -> None:
        "Size the table to its visible rows, so it never needs to scroll."
        vertical_header = self.verticalHeader()
        assert vertical_header is not None
        margins = self.contentsMargins()
        self.setFixedHeight(vertical_header.length() + margins.top() + margins.bottom())

    def eventFilter(self, watched: QObject | None, event: QEvent | None) -> bool:
        if watched is self.key_filter and event is not None:
            if event.type() in (QEvent.Type.Move, QEvent.Type.Resize):
                self._update_column_widths()
        return super().eventFilter(watched, event)

    def resizeEvent(self, event: QResizeEvent | None) -> None:
        super().resizeEvent(event)
        self._update_column_widths()

    def _update_column_widths(self) -> None:
        viewport = self.viewport()
        assert viewport is not None
        width = viewport.width()
        # the filter lives outside the scroll area, so compare window positions
        window = self.window()
        actions = (
            self.key_filter.mapTo(window, QPoint()).x()
            - viewport.mapTo(window, QPoint()).x()
        )
        actions = max(0, min(actions, width))
        keys = width - actions
        self.setColumnWidth(0, actions)
        self.setColumnWidth(1, keys // 2)
        self.setColumnWidth(2, keys - keys // 2)


class Preferences(QDialog):
    def __init__(self, mw: AnkiQt) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self.prof = self.mw.pm.profile
        self.form = aqt.forms.preferences.Ui_Preferences()
        self.form.setupUi(self)
        for spinbox in (
            self.form.lrnCutoff,
            self.form.dayOffset,
            self.form.timeLimit,
            self.form.network_timeout,
        ):
            spinbox.setSuffix(f" {spinbox.suffix()}")

        disable_help_button(self)
        help_button = self.form.buttonBox.button(QDialogButtonBox.StandardButton.Help)
        assert help_button is not None
        help_button.setAutoDefault(False)

        close_button = self.form.buttonBox.button(QDialogButtonBox.StandardButton.Close)
        assert close_button is not None
        close_button.setAutoDefault(False)

        qconnect(
            self.form.buttonBox.helpRequested, lambda: openHelp(HelpPage.PREFERENCES)
        )

        self._setup_webview()
        self.silentlyClose = True
        self.setup_collection()
        self.setup_profile()
        self.setup_global()
        self.setup_configurable_shortcuts()
        self.show()

    def _setup_webview(self) -> None:
        self.web = AnkiWebView(kind=AnkiWebViewKind.PREFERENCES)
        layout = self.form.labsTab.layout()
        assert layout is not None
        layout.addWidget(self.web)
        self.web.load_sveltekit_page("preferences")

    def setup_configurable_shortcuts(self) -> None:
        from aqt.shortcuts import (
            ALTERNATE_SUFFIX,
            SHORTCUT_DEFINITIONS,
            default_for_slot,
            get_shortcut,
        )

        # the keybindings get their own tab, replacing the answer keys box
        group = self.form.preferences_answer_keys
        review_layout = self.form.tab_3.layout()
        assert review_layout is not None
        review_layout.removeWidget(group)
        group.deleteLater()
        tab = QWidget()
        self.form.tabWidget.insertTab(
            # after the Editing tab
            self.form.tabWidget.indexOf(self.form.tab_1) + 1,
            tab,
            tr.preferences_keybindings(),
        )
        layout = QVBoxLayout(tab)
        filters = QHBoxLayout()
        self.shortcut_search = QLineEdit(tab)
        self.shortcut_search.setPlaceholderText(
            tr.preferences_keybindings_search_action()
        )
        self.shortcut_search.setClearButtonEnabled(True)
        if clear_action := self.shortcut_search.findChild(
            QAction, "_q_qlineeditclearaction"
        ):
            clear_action.setIcon(theme_manager.icon_from_resources("mdi:close"))
        filters.addWidget(self.shortcut_search, 1)
        self.shortcut_key_filter = ShortcutEdit(tab)
        self.shortcut_key_filter.setClearButtonEnabled(True)
        self.shortcut_key_filter.setPlaceholderText(
            tr.preferences_keybindings_search_keybinding()
        )
        self.shortcut_key_filter.setToolTip(
            tr.preferences_keybindings_search_keybinding()
        )
        self.shortcut_key_filter.setAccessibleName(
            tr.preferences_keybindings_search_keybinding()
        )
        filters.addWidget(self.shortcut_key_filter, 1)
        layout.addLayout(filters)
        qconnect(
            self.shortcut_key_filter.focusChanged,
            self._set_shortcut_edit_hint,
        )

        scroll = QScrollArea(tab)
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        # let the tab's background show through, like the other tabs
        scroll.setStyleSheet("QScrollArea { background: transparent; }")
        viewport = scroll.viewport()
        assert viewport is not None
        viewport.setAutoFillBackground(False)
        sections = QWidget(scroll)
        sections_layout = QVBoxLayout(sections)
        sections_layout.setContentsMargins(0, 0, 0, 0)
        scroll.setWidget(sections)
        # setWidget() turns the fill on, which paints the window colour
        sections.setAutoFillBackground(False)
        layout.addWidget(scroll, 1)

        self.shortcut_editors: dict[str, ShortcutEdit] = {}
        self.shortcut_sections: list[tuple[QGroupBox, ShortcutTable]] = []
        tables: dict[str, ShortcutTable] = {}
        for definition in SHORTCUT_DEFINITIONS:
            table = tables.get(definition.category)
            if table is None:
                section = QGroupBox(definition.category, sections)
                QVBoxLayout(section).addWidget(
                    table := ShortcutTable(section, self.shortcut_key_filter)
                )
                sections_layout.addWidget(section)
                tables[definition.category] = table
                self.shortcut_sections.append((section, table))
            editors = []
            for slot_id in (definition.id, definition.id + ALTERNATE_SUFFIX):
                editor = ShortcutEdit(
                    QKeySequence(get_shortcut(self.mw.pm, slot_id)), table
                )
                editor.setClearButtonEnabled(True)
                if default := default_for_slot(slot_id):
                    editor.setToolTip(tr.preferences_keybindings_default(key=default))
                editors.append(editor)
                self.shortcut_editors[slot_id] = editor
                qconnect(editor.focusChanged, self._set_shortcut_edit_hint)
                qconnect(
                    editor.shortcutCommitted,
                    functools.partial(self._shortcut_edited, slot_id),
                )
            table.add_row(definition.label, definition.id, editors)
        # at the end of the list, so it's out of the way until scrolled to
        reset = QPushButton(tr.preferences_keybindings_reset(), sections)
        qconnect(reset.clicked, self._reset_shortcuts)
        sections_layout.addWidget(reset, alignment=Qt.AlignmentFlag.AlignRight)
        sections_layout.addStretch()

        self.shortcut_conflict_label = QLabel(tab)
        self.shortcut_conflict_label.setWordWrap(True)
        self.shortcut_conflict_label.setStyleSheet("color: #c62828")
        layout.addWidget(self.shortcut_conflict_label)
        # while a key is edited, the hint takes the place of the restart notice
        # at the bottom of the dialog, so the tab needs no line of its own
        self.shortcut_edit_hint = self.form.someSettingsLabel
        self._restart_notice = self.shortcut_edit_hint.text()
        qconnect(self.shortcut_search.textChanged, self._filter_shortcuts)
        qconnect(self.shortcut_key_filter.keySequenceChanged, self._filter_shortcuts)
        self._filter_shortcuts()
        self._update_shortcut_conflicts()

    def _set_shortcut_edit_hint(self, editing: bool) -> None:
        self.shortcut_edit_hint.setText(
            tr.preferences_keybindings_edit_hint() if editing else self._restart_notice
        )

    def _shortcut_edited(self, slot_id: str) -> None:
        "slot_id is an action id, or an action id plus ALTERNATE_SUFFIX."
        from aqt.shortcuts import SHORTCUT_SLOTS, definition_for_slot, get_shortcut

        editor = self.shortcut_editors[slot_id]
        key = editor.keySequence().toString(QKeySequence.SequenceFormat.PortableText)
        old_key = get_shortcut(self.mw.pm, slot_id)
        conflicts = [
            other
            for other in SHORTCUT_SLOTS
            if other != slot_id
            and _shortcut_contexts_overlap(other, slot_id)
            and QKeySequence(get_shortcut(self.mw.pm, other)).toString(
                QKeySequence.SequenceFormat.PortableText
            )
            == key
        ]
        if key and conflicts:
            other = conflicts[0]
            if len(conflicts) > 1 or not self._confirm_shortcut_swap(
                key, definition_for_slot(other).label
            ):
                editor.setKeySequence(QKeySequence(old_key))
                return
            self.mw.pm.set_shortcut(other, old_key)
            self.shortcut_editors[other].setKeySequence(QKeySequence(old_key))
        self.mw.pm.set_shortcut(slot_id, key)
        self._update_shortcut_conflicts()

    def _confirm_shortcut_swap(self, key: str, other_action: str) -> bool:
        dialog = QMessageBox(self)
        dialog.setIcon(QMessageBox.Icon.Warning)
        dialog.setWindowTitle(tr.preferences_keybindings())
        dialog.setText(
            tr.preferences_keybindings_conflict_prompt(key=key, action=other_action)
        )
        swap = dialog.addButton(
            tr.preferences_keybindings_swap(), QMessageBox.ButtonRole.AcceptRole
        )
        dialog.addButton(
            tr.preferences_keybindings_choose_another(),
            QMessageBox.ButtonRole.RejectRole,
        )
        dialog.exec()
        return dialog.clickedButton() == swap

    def _reset_shortcuts(self) -> None:
        from aqt.shortcuts import SHORTCUT_SLOTS, default_for_slot

        self.mw.pm.reset_shortcuts()
        for slot_id in SHORTCUT_SLOTS:
            self.shortcut_editors[slot_id].setKeySequence(
                QKeySequence(default_for_slot(slot_id))
            )
        self._update_shortcut_conflicts()

    def _filter_shortcuts(self, _value: object = None) -> None:
        from aqt.shortcuts import ALTERNATE_SUFFIX, SHORTCUTS_BY_ID

        query = self.shortcut_search.text().strip().casefold()
        key_query = self.shortcut_key_filter.keySequence().toString(
            QKeySequence.SequenceFormat.PortableText
        )
        for section, table in self.shortcut_sections:
            visible_rows = 0
            for row in range(table.rowCount()):
                item = table.item(row, 0)
                assert item is not None
                definition = SHORTCUTS_BY_ID[item.data(Qt.ItemDataRole.UserRole)]
                keys = {
                    self.shortcut_editors[slot_id]
                    .keySequence()
                    .toString(QKeySequence.SequenceFormat.PortableText)
                    for slot_id in (definition.id, definition.id + ALTERNATE_SUFFIX)
                }
                visible = query in definition.label.casefold() and (
                    not key_query or key_query in keys
                )
                table.setRowHidden(row, not visible)
                visible_rows += visible
            section.setHidden(visible_rows == 0)
            table.fit_height()

    def _update_shortcut_conflicts(self) -> None:
        from aqt.shortcuts import SHORTCUT_SLOTS, definition_for_slot

        bindings: dict[str, list[str]] = {}
        for slot_id in SHORTCUT_SLOTS:
            key = (
                self.shortcut_editors[slot_id]
                .keySequence()
                .toString(QKeySequence.SequenceFormat.PortableText)
            )
            if key:
                bindings.setdefault(QKeySequence(key).toString(), []).append(slot_id)
        conflicts = []
        for key, slot_ids in bindings.items():
            # an action bound twice to the same key only appears once
            clashing = dict.fromkeys(
                definition_for_slot(slot_id).label
                for slot_id in slot_ids
                if any(
                    other != slot_id and _shortcut_contexts_overlap(other, slot_id)
                    for other in slot_ids
                )
            )
            if len(clashing) > 1:
                conflicts.append(f"{key}: {', '.join(clashing)}")
        self.shortcut_conflict_label.setText(
            tr.preferences_keybindings_conflicts(conflicts="; ".join(conflicts))
            if conflicts
            else ""
        )
        self.shortcut_conflict_label.setVisible(bool(conflicts))

    def accept(self) -> None:
        self.accept_with_callback()

    def accept_with_callback(self, callback: Callable[[], None] | None = None) -> None:
        # avoid exception if main window is already closed
        if not self.mw.col:
            return

        def after_collection_update() -> None:
            self.update_profile()
            self.update_global()
            self.mw.pm.save()
            self.mw.refresh_shortcuts()
            self.done(0)
            aqt.dialogs.markClosed("Preferences")

            if callback:
                callback()

        self.update_collection(after_collection_update)
        self.web.cleanup()

    def reject(self) -> None:
        self.accept()

    # Preferences stored in the collection
    ######################################################################

    def setup_collection(self) -> None:
        self.prefs = self.mw.col.get_preferences()
        self.old_prefs = deepcopy(self.prefs)

        form = self.form

        scheduling = self.prefs.scheduling

        form.lrnCutoff.setValue(int(scheduling.learn_ahead_secs / 60.0))
        form.dayOffset.setValue(scheduling.rollover)

        reviewing = self.prefs.reviewing
        form.timeLimit.setValue(int(reviewing.time_limit_secs / 60.0))
        form.showEstimates.setChecked(reviewing.show_intervals_on_buttons)
        form.showProgress.setChecked(reviewing.show_remaining_due_counts)
        form.showPlayButtons.setChecked(not reviewing.hide_audio_play_buttons)
        form.interrupt_audio.setChecked(reviewing.interrupt_audio_when_answering)

        editing = self.prefs.editing
        form.useCurrent.setCurrentIndex(
            0 if editing.adding_defaults_to_current_deck else 1
        )
        form.paste_strips_formatting.setChecked(editing.paste_strips_formatting)
        form.ignore_accents_in_search.setChecked(editing.ignore_accents_in_search)
        form.pastePNG.setChecked(editing.paste_images_as_png)
        form.render_latex.setChecked(editing.render_latex)
        form.default_search_text.setText(editing.default_search_text)

        form.backup_explanation.setText(
            anki.lang.with_collapsed_whitespace(tr.preferences_backup_explanation())
        )
        form.daily_backups.setValue(self.prefs.backups.daily)
        form.weekly_backups.setValue(self.prefs.backups.weekly)
        form.monthly_backups.setValue(self.prefs.backups.monthly)
        form.minutes_between_backups.setValue(self.prefs.backups.minimum_interval_mins)

        add_ellipsis_to_action_label(self.form.url_schemes)
        qconnect(self.form.url_schemes.clicked, show_url_schemes_dialog)

    def update_collection(self, on_done: Callable[[], None]) -> None:
        form = self.form

        scheduling = self.prefs.scheduling
        scheduling.learn_ahead_secs = form.lrnCutoff.value() * 60
        scheduling.rollover = form.dayOffset.value()

        reviewing = self.prefs.reviewing
        reviewing.show_remaining_due_counts = form.showProgress.isChecked()
        reviewing.show_intervals_on_buttons = form.showEstimates.isChecked()
        reviewing.time_limit_secs = form.timeLimit.value() * 60
        reviewing.hide_audio_play_buttons = not self.form.showPlayButtons.isChecked()
        reviewing.interrupt_audio_when_answering = self.form.interrupt_audio.isChecked()

        editing = self.prefs.editing
        editing.adding_defaults_to_current_deck = not form.useCurrent.currentIndex()
        editing.paste_images_as_png = self.form.pastePNG.isChecked()
        editing.paste_strips_formatting = self.form.paste_strips_formatting.isChecked()
        editing.render_latex = self.form.render_latex.isChecked()
        editing.default_search_text = self.form.default_search_text.text()
        editing.ignore_accents_in_search = (
            self.form.ignore_accents_in_search.isChecked()
        )

        self.prefs.backups.daily = form.daily_backups.value()
        self.prefs.backups.weekly = form.weekly_backups.value()
        self.prefs.backups.monthly = form.monthly_backups.value()
        self.prefs.backups.minimum_interval_mins = form.minutes_between_backups.value()

        def after_prefs_update(changes: OpChanges | None = None) -> None:
            self.mw.apply_collection_options()
            on_done()

        if self.prefs == self.old_prefs:
            after_prefs_update()
        else:
            set_preferences(parent=self, preferences=self.prefs).success(
                after_prefs_update
            ).run_in_background()

    # Preferences stored in the profile
    ######################################################################

    def setup_profile(self) -> None:
        "Setup options stored in the user profile."
        self.setup_network()

    def update_profile(self) -> None:
        self.update_network()

    # Profile: network
    ######################################################################

    def setup_network(self) -> None:
        self.form.media_log.setText(tr.sync_media_log_button())
        qconnect(self.form.media_log.clicked, self.on_media_log)
        self.form.syncOnProgramOpen.setChecked(self.mw.pm.auto_syncing_enabled())
        self.form.syncMedia.setChecked(self.mw.pm.media_syncing_enabled())
        self.form.autoSyncMedia.setChecked(
            self.mw.pm.periodic_sync_media_minutes() != 0
        )
        self.form.custom_sync_url.setText(self.mw.pm.custom_sync_url())
        self.form.network_timeout.setValue(self.mw.pm.network_timeout())

        self.form.check_for_updates.setChecked(self.mw.pm.check_for_updates())
        qconnect(self.form.check_for_updates.stateChanged, self.mw.pm.set_update_check)

        self.form.check_for_addon_updates.setChecked(
            self.mw.pm.check_for_addon_updates()
        )
        qconnect(
            self.form.check_for_addon_updates.stateChanged,
            self.mw.pm.set_check_for_addon_updates,
        )

        self.update_login_status()
        qconnect(self.form.syncLogout.clicked, self.sync_logout)
        qconnect(self.form.syncLogin.clicked, self.sync_login)
        qconnect(self.form.syncAnkiHubLogout.clicked, self.ankihub_sync_logout)
        qconnect(self.form.syncAnkiHubLogin.clicked, self.ankihub_sync_login)

    def update_login_status(self) -> None:
        assert self.prof is not None
        if not self.prof.get("syncKey"):
            self.form.syncUser.setText(tr.preferences_ankiweb_intro())
            self.form.syncLogin.setVisible(True)
            self.form.syncLogout.setVisible(False)
        else:
            self.form.syncUser.setText(self.prof.get("syncUser", ""))
            self.form.syncLogin.setVisible(False)
            self.form.syncLogout.setVisible(True)

        if not self.mw.pm.ankihub_token():
            self.form.syncAnkiHubUser.setText(tr.preferences_ankihub_intro())
            self.form.syncAnkiHubLogin.setVisible(True)
            self.form.syncAnkiHubLogout.setVisible(False)
        else:
            self.form.syncAnkiHubUser.setText(self.mw.pm.ankihub_username())
            self.form.syncAnkiHubLogin.setVisible(False)
            self.form.syncAnkiHubLogout.setVisible(True)

    def on_media_log(self) -> None:
        self.mw.media_syncer.show_sync_log()

    def sync_login(self) -> None:
        def on_success():
            assert self.prof is not None
            if self.prof.get("syncKey"):
                self.update_login_status()
                self.confirm_sync_after_login()

        self.update_network()
        sync_login(self.mw, on_success)

    def sync_logout(self) -> None:
        if self.mw.media_syncer.is_syncing():
            showWarning("Can't log out while sync in progress.")
            return
        assert self.prof is not None
        self.prof["syncKey"] = None
        self.mw.col.media.force_resync()
        self.update_login_status()

    def ankihub_sync_login(self) -> None:
        def on_success():
            if self.mw.pm.ankihub_token():
                self.update_login_status()

        ankihub_login(self.mw, on_success)

    def ankihub_sync_logout(self) -> None:
        ankihub_token = self.mw.pm.ankihub_token()
        if ankihub_token is None:
            return
        ankihub_logout(self.mw, self.update_login_status, ankihub_token)

    def confirm_sync_after_login(self) -> None:
        from aqt import mw

        if askUser(tr.preferences_login_successful_sync_now(), parent=mw):
            self.accept_with_callback(self.mw.on_sync_button_clicked)

    def update_network(self) -> None:
        assert self.prof is not None
        self.prof["autoSync"] = self.form.syncOnProgramOpen.isChecked()
        self.prof["syncMedia"] = self.form.syncMedia.isChecked()
        self.mw.pm.set_periodic_sync_media_minutes(
            self.form.autoSyncMedia.isChecked() and 15 or 0
        )
        if self.form.fullSync.isChecked():
            self.mw.col.mod_schema(check=False)
        self.mw.pm.set_custom_sync_url(self.form.custom_sync_url.text())
        self.mw.pm.set_network_timeout(self.form.network_timeout.value())

    # Global preferences
    ######################################################################

    def setup_global(self) -> None:
        "Setup options global to all profiles."
        self.form.reduce_motion.setChecked(self.mw.pm.reduce_motion())
        qconnect(self.form.reduce_motion.stateChanged, self.mw.pm.set_reduce_motion)

        self.form.minimalist_mode.setChecked(self.mw.pm.minimalist_mode())
        qconnect(self.form.minimalist_mode.stateChanged, self.mw.pm.set_minimalist_mode)

        self.form.spacebar_rates_card.setChecked(self.mw.pm.spacebar_rates_card())
        qconnect(
            self.form.spacebar_rates_card.stateChanged,
            self.mw.pm.set_spacebar_rates_card,
        )

        hide_choices = [tr.preferences_full_screen_only(), tr.preferences_always()]

        self.form.hide_top_bar.setChecked(self.mw.pm.hide_top_bar())
        qconnect(self.form.hide_top_bar.stateChanged, self.mw.pm.set_hide_top_bar)
        qconnect(
            self.form.hide_top_bar.stateChanged,
            self.form.topBarComboBox.setVisible,
        )
        self.form.topBarComboBox.addItems(hide_choices)
        self.form.topBarComboBox.setCurrentIndex(self.mw.pm.top_bar_hide_mode())
        self.form.topBarComboBox.setVisible(self.form.hide_top_bar.isChecked())

        qconnect(
            self.form.topBarComboBox.currentIndexChanged,
            self.mw.pm.set_top_bar_hide_mode,
        )

        self.form.hide_bottom_bar.setChecked(self.mw.pm.hide_bottom_bar())
        qconnect(self.form.hide_bottom_bar.stateChanged, self.mw.pm.set_hide_bottom_bar)
        qconnect(
            self.form.hide_bottom_bar.stateChanged,
            self.form.bottomBarComboBox.setVisible,
        )
        self.form.bottomBarComboBox.addItems(hide_choices)
        self.form.bottomBarComboBox.setCurrentIndex(self.mw.pm.bottom_bar_hide_mode())
        self.form.bottomBarComboBox.setVisible(self.form.hide_bottom_bar.isChecked())

        qconnect(
            self.form.bottomBarComboBox.currentIndexChanged,
            self.mw.pm.set_bottom_bar_hide_mode,
        )

        self.form.uiScale.setValue(int(self.mw.pm.uiScale() * 100))
        themes = [
            tr.preferences_theme_follow_system(),
            tr.preferences_theme_light(),
            tr.preferences_theme_dark(),
        ]
        self.form.theme.addItems(themes)
        self.form.theme.setCurrentIndex(self.mw.pm.theme().value)
        qconnect(self.form.theme.currentIndexChanged, self.on_theme_changed)

        self.form.styleComboBox.addItems(["Anki"] + (["Native"] if not is_win else []))
        self.form.styleComboBox.setCurrentIndex(self.mw.pm.get_widget_style())
        qconnect(
            self.form.styleComboBox.currentIndexChanged,
            self.mw.pm.set_widget_style,
        )
        self.form.styleLabel.setVisible(not is_win)
        self.form.styleComboBox.setVisible(not is_win)
        qconnect(self.form.resetWindowSizes.clicked, self.on_reset_window_sizes)

        self.setup_language()
        self.setup_video_driver()

        self.setupOptions()

    def update_global(self) -> None:
        restart_required = self.update_video_driver()

        newScale = self.form.uiScale.value() / 100
        if newScale != self.mw.pm.uiScale():
            self.mw.pm.setUiScale(newScale)
            restart_required = True

        col = self.mw.col
        # This notification is disabled in safe mode to prevent it from triggering due to col._experiments always being empty.
        if not self.mw.safeMode and col._get_experiments_dirty() != col._experiments:
            restart_required = True

        if restart_required:
            showInfo(tr.preferences_changes_will_take_effect_when_you())

        self.updateOptions()

    def on_theme_changed(self, index: int) -> None:
        self.mw.set_theme(Theme(index))

    def on_reset_window_sizes(self) -> None:
        assert self.prof is not None
        regexp = re.compile(r"(Geom(etry)?|State|Splitter|Header)(\d+.\d+)?$")
        for key in list(self.prof.keys()):
            if regexp.search(key):
                del self.prof[key]
        showInfo(tr.preferences_reset_window_sizes_complete())

    # legacy - one of Henrik's add-ons is currently wrapping them

    def setupOptions(self) -> None:
        pass

    def updateOptions(self) -> None:
        pass

    # Global: language
    ######################################################################

    def setup_language(self) -> None:
        f = self.form
        f.lang.addItems([x[0] for x in anki.lang.langs])
        f.lang.setCurrentIndex(self.current_lang_index())
        qconnect(f.lang.currentIndexChanged, self.on_language_index_changed)

    def current_lang_index(self) -> int:
        codes = [x[1] for x in anki.lang.langs]
        lang = anki.lang.current_lang
        if lang in anki.lang.compatMap:
            lang = anki.lang.compatMap[lang]
        else:
            lang = lang.replace("-", "_")
        try:
            return codes.index(lang)
        except Exception:
            return codes.index("en_US")

    def on_language_index_changed(self, idx: int) -> None:
        code = anki.lang.langs[idx][1]
        self.mw.pm.setLang(code)
        showInfo(tr.preferences_please_restart_anki_to_complete_language(), parent=self)

    # Global: video driver
    ######################################################################

    def setup_video_driver(self) -> None:
        self.video_drivers = VideoDriver.all_for_platform()
        names = [video_driver_name_for_platform(d) for d in self.video_drivers]
        self.form.video_driver.addItems(names)
        self.form.video_driver.setCurrentIndex(
            self.video_drivers.index(self.mw.pm.video_driver())
        )

    def update_video_driver(self) -> bool:
        """Returns True if the video driver was changed."""
        new_driver = self.video_drivers[self.form.video_driver.currentIndex()]
        if new_driver != self.mw.pm.video_driver():
            self.mw.pm.set_video_driver(new_driver)
            return True
        return False


def video_driver_name_for_platform(driver: VideoDriver) -> str:
    if qtmajor < 6:
        if driver == VideoDriver.ANGLE:
            return tr.preferences_video_driver_angle()
        elif driver == VideoDriver.Software:
            if is_mac:
                return tr.preferences_video_driver_software_mac()
            else:
                return tr.preferences_video_driver_software_other()
        elif driver == VideoDriver.OpenGL:
            if is_mac:
                return tr.preferences_video_driver_opengl_mac()
            else:
                return tr.preferences_video_driver_opengl_other()

    label = driver.name
    if driver == VideoDriver.default_for_platform():
        label += f" ({tr.preferences_video_driver_default()})"

    return label
