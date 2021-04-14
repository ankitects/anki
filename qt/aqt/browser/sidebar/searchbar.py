# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
from aqt import colors
from aqt.browser.sidebar import _want_right_border
from aqt.qt import *
from aqt.theme import theme_manager


class SidebarSearchBar(QLineEdit):
    def __init__(self, sidebar: aqt.browser.sidebar.SidebarTreeView) -> None:
        QLineEdit.__init__(self, sidebar)
        self.setPlaceholderText(sidebar.col.tr.browsing_sidebar_filter())
        self.sidebar = sidebar
        self.timer = QTimer(self)
        self.timer.setInterval(600)
        self.timer.setSingleShot(True)
        self.setFrame(False)
        border = theme_manager.color(colors.MEDIUM_BORDER)
        styles = [
            "padding: 1px",
            "padding-left: 3px",
            f"border-bottom: 1px solid {border}",
        ]
        if _want_right_border():
            styles.append(
                f"border-right: 1px solid {border}",
            )

        self.setStyleSheet("QLineEdit { %s }" % ";".join(styles))

        qconnect(self.timer.timeout, self.onSearch)
        qconnect(self.textChanged, self.onTextChanged)

    def onTextChanged(self, text: str) -> None:
        if not self.timer.isActive():
            self.timer.start()

    def onSearch(self) -> None:
        self.sidebar.search_for(self.text())

    def keyPressEvent(self, evt: QKeyEvent) -> None:
        if evt.key() in (Qt.Key_Up, Qt.Key_Down):
            self.sidebar.setFocus()
        elif evt.key() in (Qt.Key_Enter, Qt.Key_Return):
            self.onSearch()
        else:
            QLineEdit.keyPressEvent(self, evt)
