# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/copyleft/agpl.html

from __future__ import annotations

from collections.abc import Callable, Sequence

import aqt
import aqt.customstudy
import aqt.forms
from anki.lang import with_collapsed_whitespace
from anki.scheduler.base import CustomStudyDefaults
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom, showWarning, tr


class TagLimit(QDialog):
    def __init__(
        self,
        parent: QWidget,
        tags: Sequence[CustomStudyDefaults.Tag],
        on_success: Callable[[list[str], list[str]], None],
    ) -> None:
        "Ask user to select tags. on_success() will be called with selected included and excluded tags."
        QDialog.__init__(self, parent, Qt.WindowType.Window)
        self.tags = tags
        self.form = aqt.forms.taglimit.Ui_Dialog()
        self.form.setupUi(self)
        self.on_success = on_success
        disable_help_button(self)
        s = QShortcut(
            QKeySequence("ctrl+d"),
            self.form.activeList,
            context=Qt.ShortcutContext.WidgetShortcut,
        )
        qconnect(s.activated, self.form.activeList.clearSelection)
        s = QShortcut(
            QKeySequence("ctrl+d"),
            self.form.inactiveList,
            context=Qt.ShortcutContext.WidgetShortcut,
        )
        qconnect(s.activated, self.form.inactiveList.clearSelection)
        self.build_tag_lists()
        restoreGeom(self, "tagLimit")
        self.open()

    def build_tag_lists(self) -> None:
        def add_tag(tag: str, select: bool, list: QListWidget) -> None:
            item = QListWidgetItem(tag.replace("_", " "))
            list.addItem(item)
            if select:
                idx = list.indexFromItem(item)
                list_selection_model = list.selectionModel()
                assert list_selection_model is not None
                list_selection_model.select(
                    idx, QItemSelectionModel.SelectionFlag.Select
                )

        had_included_tag = False

        for tag in self.tags:
            if tag.include:
                had_included_tag = True
            add_tag(tag.name, tag.include, self.form.activeList)
            add_tag(tag.name, tag.exclude, self.form.inactiveList)

        if had_included_tag:
            self.form.activeCheck.setChecked(True)

    def reject(self) -> None:
        QDialog.reject(self)

    def accept(self) -> None:
        include_tags = []
        exclude_tags = []
        want_active = self.form.activeCheck.isChecked()
        for c, tag in enumerate(self.tags):
            # active
            if want_active:
                item = self.form.activeList.item(c)
                idx = self.form.activeList.indexFromItem(item)
                active_list_selection_model = self.form.activeList.selectionModel()
                assert active_list_selection_model is not None
                if active_list_selection_model.isSelected(idx):
                    include_tags.append(tag.name)
            # inactive
            item = self.form.inactiveList.item(c)
            idx = self.form.inactiveList.indexFromItem(item)
            inactive_list_selection_model = self.form.inactiveList.selectionModel()
            assert inactive_list_selection_model is not None
            if inactive_list_selection_model.isSelected(idx):
                exclude_tags.append(tag.name)

        if (len(include_tags) + len(exclude_tags)) > 100:
            showWarning(with_collapsed_whitespace(tr.errors_100_tags_max()))
            return

        saveGeom(self, "tagLimit")
        QDialog.accept(self)

        self.on_success(include_tags, exclude_tags)
