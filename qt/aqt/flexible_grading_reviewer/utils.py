# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import anki.collection
import aqt
from anki.consts import REVLOG_RESCHED
from aqt import tr
from aqt.qt import *


def ease_to_answer_key(ease: int) -> str:
    return (
        tr.actions_shortcut_key(val=aqt.mw.pm.get_answer_key(ease))
        if aqt.mw.pm.get_answer_key(ease)
        else ""
    )


def ease_to_answer_key_short(ease: int) -> str:
    return ease_to_answer_key(ease).split(":")[1].strip()


def prev_day_cutoff_ms(col: anki.collection.Collection) -> int:
    return (col.sched.day_cutoff - 86_400) * 1000


def studied_today_count(col: anki.collection.Collection) -> int:
    return col.db.scalar(
        """ SELECT COUNT(*) FROM revlog WHERE type != ? AND id > ? """,
        REVLOG_RESCHED,
        prev_day_cutoff_ms(col),
    )


def clear_layout(layout: QLayout) -> None:
    """Remove all widgets from a layout and delete them."""
    while layout.count():
        child = layout.takeAt(0)
        if child.widget():
            widget = child.widget()
            widget.setParent(None)
            widget.deleteLater()
        elif child.layout():
            clear_layout(child.layout())
