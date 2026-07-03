# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import aqt
import aqt.main
from aqt.qt import QDialog, QWidget, qconnect
from aqt.theme import WidgetStyle
from aqt.utils import restoreGeom, saveGeom


class WidgetGallery(QDialog):
    silentlyClose = True

    def __init__(self, parent: QWidget) -> None:
        assert aqt.mw
        super().__init__(parent)

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
            [member.name.lower().capitalize() for member in WidgetStyle]
        )
        self.form.styleComboBox.setCurrentIndex(aqt.mw.pm.get_widget_style())
        qconnect(
            self.form.styleComboBox.currentIndexChanged,
            aqt.mw.pm.set_widget_style,
        )

    def reject(self) -> None:
        super().reject()
        saveGeom(self, "WidgetGallery")
