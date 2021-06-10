# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import sys

import aqt

from .browser import Browser, PreviewDialog

# aliases for legacy pathnames
from .sidebar import (
    SidebarItem,
    SidebarItemType,
    SidebarModel,
    SidebarSearchBar,
    SidebarStage,
    SidebarTool,
    SidebarToolbar,
    SidebarTreeView,
)
from .table import (
    CardState,
    Cell,
    CellRow,
    Column,
    Columns,
    DataModel,
    ItemId,
    ItemList,
    ItemState,
    NoteState,
    SearchContext,
    StatusDelegate,
    Table,
)

sys.modules["aqt.sidebar"] = sys.modules["aqt.browser.sidebar"]
aqt.sidebar = sys.modules["aqt.browser.sidebar"]  # type: ignore
sys.modules["aqt.previewer"] = sys.modules["aqt.browser.previewer"]
aqt.previewer = sys.modules["aqt.browser.previewer"]  # type: ignore
