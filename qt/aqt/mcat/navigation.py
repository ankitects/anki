# Copyright: Aryan Verma and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""MCAT navigation wiring (Phase 1).

Two small, additive behaviours that make the MCAT dashboard the app's hub:

* **Land on the dashboard at startup.** After the main window finishes
  initialising, the MCAT dashboard opens, so opening the app drops the student
  on the hub rather than straight into the deck list.
* **A "Dashboard" link in the flashcards toolbar.** The deck browser is the
  original Anki view (not one of our dialogs), so its redirect back to the hub
  is added to the top toolbar via the documented hook.

The dialog surfaces (quiz, Memory panel) carry their own "Dashboard" button.
Together every surface has a way back to the hub. Everything is registered via
`gui_hooks`, so it stays additive and easy to carry across upstream merges.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

import aqt
from aqt import gui_hooks

if TYPE_CHECKING:
    import aqt.toolbar

# Guard so repeated setup calls (e.g. profile switches rebuilding menus) don't
# stack duplicate hook subscribers.
_registered = False


def setup_mcat_navigation() -> None:
    """Register the startup redirect and the flashcards-toolbar link once."""
    global _registered
    if _registered:
        return
    _registered = True
    gui_hooks.main_window_did_init.append(_show_dashboard_on_startup)
    gui_hooks.top_toolbar_did_init_links.append(_add_dashboard_toolbar_link)


def _show_dashboard_on_startup() -> None:
    from aqt.mcat.dashboard import show_dashboard
    from aqt.qt import QTimer

    mw = aqt.mw
    if mw is None:
        return
    # Defer to the next event-loop turn so the main window is painted first;
    # the dashboard then appears on top of it.
    QTimer.singleShot(100, lambda: show_dashboard(mw))


def _add_dashboard_toolbar_link(
    links: list[str], toolbar: "aqt.toolbar.Toolbar"
) -> None:
    from aqt.mcat.dashboard import show_dashboard

    link = toolbar.create_link(
        "mcat_dashboard",
        "Dashboard",
        lambda: show_dashboard(toolbar.mw),
        tip="Open the MCAT dashboard",
        id="mcat_dashboard",
    )
    links.append(link)
