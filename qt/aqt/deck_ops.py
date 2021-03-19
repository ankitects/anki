# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Sequence

from anki.lang import TR
from aqt import AnkiQt, QWidget
from aqt.utils import tooltip, tr


def remove_decks(
    *,
    mw: AnkiQt,
    parent: QWidget,
    deck_ids: Sequence[int],
) -> None:
    mw.perform_op(
        lambda: mw.col.decks.remove(deck_ids),
        success=lambda out: tooltip(
            tr(TR.BROWSING_CARDS_DELETED, count=out.count), parent=parent
        ),
    )
