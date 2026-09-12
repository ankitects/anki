# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from collections.abc import Callable, Iterator
from types import SimpleNamespace
from typing import cast

import pytest
from PyQt6.QtTest import QTest

import anki.lang
from aqt.browser import Browser
from aqt.browser.sidebar.item import SidebarItem, SidebarItemType
from aqt.browser.sidebar.tree import SidebarTreeView
from aqt.operations import QueryOp
from aqt.qt import QApplication, QModelIndex, Qt, sip


class SidebarFixture:
    def __init__(self, view: SidebarTreeView) -> None:
        self.view = view
        self.pending: list[Callable[[], None]] = []
        self.expanded: dict[str, bool] = {}

    def root(self) -> SidebarItem:
        root = SidebarItem("", "", item_type=SidebarItemType.ROOT)
        today = SidebarItem(
            "Today",
            "",
            item_type=SidebarItemType.TODAY_ROOT,
            expanded=self.expanded.get("Today", False),
            on_expanded=lambda value: self.expanded.update(Today=value),
        )
        today.add_child(SidebarItem("Overdue", "", item_type=SidebarItemType.TODAY))
        root.add_child(today)
        tags = SidebarItem(
            "Tags",
            "",
            item_type=SidebarItemType.TAG_ROOT,
            expanded=self.expanded.get("Tags", False),
            on_expanded=lambda value: self.expanded.update(Tags=value),
        )
        for number in range(40):
            tags.add_child(
                SidebarItem(f"Tag {number}", "", item_type=SidebarItemType.TAG)
            )
        tags.children[0].expanded = True
        tags.children[0].add_child(
            SidebarItem(
                "Nested", "", item_type=SidebarItemType.TAG, name_prefix="Tag 0::"
            )
        )
        root.add_child(tags)
        saved = SidebarItem(
            "Saved Searches", "", item_type=SidebarItemType.SAVED_SEARCH_ROOT
        )
        saved.add_child(
            SidebarItem("Saved", "", item_type=SidebarItemType.SAVED_SEARCH)
        )
        root.add_child(saved)
        return root

    def index(self, name: str) -> QModelIndex:
        item = self.view.find_item(lambda item: item.name == name)
        assert item is not None
        return self.view.model().index_for_item(item)

    def complete(self) -> None:
        self.pending.pop(0)()
        QApplication.processEvents()


@pytest.fixture
def sidebar(monkeypatch: pytest.MonkeyPatch) -> Iterator[SidebarFixture]:
    monkeypatch.setenv("QT_QPA_PLATFORM", "offscreen")
    app = QApplication.instance() or QApplication([])
    monkeypatch.setattr(anki.lang, "current_lang", anki.lang.current_lang)
    monkeypatch.setattr(anki.lang, "current_i18n", anki.lang.current_i18n)
    monkeypatch.setattr(
        anki.lang.tr_legacyglobal, "backend", anki.lang.tr_legacyglobal.backend
    )
    anki.lang.set_lang("en")
    col = SimpleNamespace(tr=SimpleNamespace(browsing_sidebar_filter=lambda: "Filter"))
    browser = cast(Browser, SimpleNamespace(mw=SimpleNamespace(col=col)))
    view = SidebarTreeView(browser)
    fixture = SidebarFixture(view)
    monkeypatch.setattr(view, "_root_tree", fixture.root)

    def queue(query: QueryOp[SidebarItem]) -> None:
        root = query._op(browser.mw.col)
        fixture.pending.append(lambda: query._success(root))

    monkeypatch.setattr(QueryOp, "run_in_background", queue)
    view.resize(320, 200)
    view.show()
    view.refresh()
    fixture.complete()
    try:
        yield fixture
    finally:
        view.searchBar.timer.stop()
        view.cleanup()
        view.close()
        sip.delete(view)
        app.processEvents()


@pytest.mark.parametrize("clear", ["", "   ", "keyboard"])
def test_clear_filter_does_not_reopen_collapsed_result(
    sidebar: SidebarFixture, clear: str
) -> None:
    view = sidebar.view
    view.searchBar.setText("overdue")
    QTest.keyClick(view.searchBar, Qt.Key.Key_Return)
    assert view.isExpanded(sidebar.index("Today"))
    assert view.currentIndex().data() == "Overdue"
    if clear == "keyboard":
        QTest.keyClick(
            view.searchBar, Qt.Key.Key_Backspace, Qt.KeyboardModifier.ControlModifier
        )
        assert not view.searchBar.text()
    else:
        view.searchBar.setText(clear)
    # Deliver the scheduled search without a wall-clock wait.
    view.searchBar.timer.stop()
    view.searchBar.timer.timeout.emit()
    sidebar.complete()
    assert view.current_search is None
    assert not view.isExpanded(sidebar.index("Today"))
    view.expand(sidebar.index("Today"))
    view.collapse(sidebar.index("Today"))
    old_model = view.model()
    view.refresh()
    sidebar.complete()
    assert view.model() is not old_model
    assert not view.isExpanded(sidebar.index("Today"))
    assert view.currentIndex().data() == "Overdue"
    assert not view.model().item_for_index(sidebar.index("Overdue")).is_highlighted()
    assert sidebar.expanded["Today"] is False


@pytest.mark.parametrize("term", ["overdue", "no matching item"])
def test_active_filter_survives_refresh(sidebar: SidebarFixture, term: str) -> None:
    view = sidebar.view
    view.search_for(term)
    view.refresh()
    sidebar.complete()
    assert view.current_search == term
    assert view.isColumnHidden(0) == (term != "overdue")
    assert view.isExpanded(sidebar.index("Today")) == (term == "overdue")
    assert not sidebar.expanded


def test_clear_filter_while_refresh_pending(sidebar: SidebarFixture) -> None:
    view = sidebar.view
    view.search_for("overdue")
    view.refresh()
    view.search_for("")
    sidebar.complete()
    sidebar.complete()
    assert view.current_search is None
    assert not view.isExpanded(sidebar.index("Today"))
    assert not view.isColumnHidden(0)


def test_explicit_saved_search_target_is_revealed(sidebar: SidebarFixture) -> None:
    view = sidebar.view
    view.refresh(SidebarItem("Saved", "", item_type=SidebarItemType.SAVED_SEARCH))
    sidebar.complete()
    assert view.isExpanded(sidebar.index("Saved Searches"))
    assert view.currentIndex().data() == "Saved"


@pytest.mark.parametrize("auto_scroll", [False, True])
def test_collapsed_ancestor_stays_collapsed_after_refresh(
    sidebar: SidebarFixture, auto_scroll: bool
) -> None:
    view = sidebar.view
    view.expand(sidebar.index("Tags"))
    view.expand(sidebar.index("Tag 0"))
    view.setCurrentIndex(sidebar.index("Nested"))
    view.collapse(sidebar.index("Tags"))
    view.setAutoScroll(auto_scroll)
    view.refresh()
    sidebar.complete()
    assert not view.isExpanded(sidebar.index("Tags"))
    assert view.isExpanded(sidebar.index("Tag 0"))
    assert view.currentIndex().data() == "Nested"
    assert view.hasAutoScroll() == auto_scroll


def test_refresh_scrolls_to_expanded_item_outside_viewport(
    sidebar: SidebarFixture,
) -> None:
    view = sidebar.view
    view.expand(sidebar.index("Tags"))
    view.setCurrentIndex(sidebar.index("Tag 39"))
    view.verticalScrollBar().setValue(0)
    assert (
        not view.viewport().rect().intersects(view.visualRect(sidebar.index("Tag 39")))
    )
    view.refresh()
    sidebar.complete()
    assert view.currentIndex().data() == "Tag 39"
    assert view.viewport().rect().intersects(view.visualRect(sidebar.index("Tag 39")))


def test_latest_filter_is_used_when_refresh_completes(sidebar: SidebarFixture) -> None:
    view = sidebar.view
    view.search_for("overdue")
    view.refresh()
    view.search_for("tag 39")
    sidebar.complete()
    assert view.current_search == "tag 39"
    assert view.model().item_for_index(sidebar.index("Tag 39")).is_highlighted()
    assert not view.model().item_for_index(sidebar.index("Overdue")).is_highlighted()
    assert view.isExpanded(sidebar.index("Tags"))
    assert not view.isExpanded(sidebar.index("Today"))
