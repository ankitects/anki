# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from enum import Enum

from aqt.qt import QEvent, QObject, QSplitter, Qt


class BrowserLayout(Enum):
    AUTO = "auto"
    VERTICAL = "vertical"
    HORIZONTAL = "horizontal"


class QSplitterHandleEventFilter(QObject):
    """Event filter that equalizes QSplitter panes on double-clicking the handle"""

    def __init__(self, splitter: QSplitter):
        super().__init__(splitter)
        self._splitter = splitter

    def eventFilter(self, object: QObject | None, event: QEvent | None) -> bool:
        assert event is not None

        if event.type() == QEvent.Type.MouseButtonDblClick:
            splitter_parent = self._splitter.parentWidget()

            assert splitter_parent is not None

            if self._splitter.orientation() == Qt.Orientation.Horizontal:
                half_size = splitter_parent.width() // 2
            else:
                half_size = splitter_parent.height() // 2
            self._splitter.setSizes([half_size, half_size])
            return True

        return super().eventFilter(object, event)
