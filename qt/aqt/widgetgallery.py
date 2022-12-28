# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import aqt
import aqt.main
from aqt.qt import QDialog, qconnect
from aqt.theme import AnkiStyles
from aqt.utils import is_mac, restoreGeom, saveGeom


class WidgetGallery(QDialog):
    silentlyClose = True

    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        super().__init__(mw)
        self.mw = mw.weakref()

        self.form = aqt.forms.widgets.Ui_Dialog()
        self.form.setupUi(self)
        restoreGeom(self, "WidgetGallery")

        qconnect(
            self.form.disableCheckBox.stateChanged,
            lambda: self.form.testGrid.setEnabled(
                not self.form.disableCheckBox.isChecked()
            ),
        )

        self.form.styleComboBox.addItems(
            [member.name.lower().capitalize() for member in AnkiStyles]
        )
        self.form.styleComboBox.setCurrentIndex(
            1
            if self.mw.pm.force_fusion_styles()
            else 2
            if self.mw.pm.force_native_styles() or is_mac
            else 0
        )
        self.form.forceCheckBox.setChecked(self.mw.pm.has_forced_style())

        qconnect(
            self.form.styleComboBox.currentIndexChanged,
            self.mw.pm.set_forced_style,
        )

    def reject(self) -> None:
        super().reject()
        if not self.form.forceCheckBox.isChecked():
            self.mw.pm.unset_forced_styles()

        saveGeom(self, "WidgetGallery")
