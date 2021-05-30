# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from enum import Enum, auto
from typing import Callable, Tuple

import aqt
from aqt.qt import *
from aqt.theme import theme_manager
from aqt.utils import tr


class SidebarTool(Enum):
    SELECT = auto()
    SEARCH = auto()


class SidebarToolbar(QToolBar):
    _tools: Tuple[Tuple[SidebarTool, str, Callable[[], str]], ...] = (
        (SidebarTool.SEARCH, ":/icons/magnifying_glass.svg", tr.actions_search),
        (SidebarTool.SELECT, ":/icons/select.svg", tr.actions_select),
    )

    def __init__(self, sidebar: aqt.browser.sidebar.SidebarTreeView) -> None:
        super().__init__()
        self.sidebar = sidebar
        self._action_group = QActionGroup(self)
        qconnect(self._action_group.triggered, self._on_action_group_triggered)
        self._setup_tools()
        self.setIconSize(QSize(16, 16))
        self.setSizePolicy(QSizePolicy.Fixed, QSizePolicy.Fixed)
        self.setStyle(QStyleFactory.create("fusion"))

    def _setup_tools(self) -> None:
        for row, tool in enumerate(self._tools):
            action = self.addAction(
                theme_manager.icon_from_resources(tool[1]), tool[2]()
            )
            action.setCheckable(True)
            action.setShortcut(f"Alt+{row + 1}")
            self._action_group.addAction(action)
        # always start with first tool
        active = 0
        self._action_group.actions()[active].setChecked(True)
        self.sidebar.tool = self._tools[active][0]

    def _on_action_group_triggered(self, action: QAction) -> None:
        index = self._action_group.actions().index(action)
        self.sidebar.tool = self._tools[index][0]
