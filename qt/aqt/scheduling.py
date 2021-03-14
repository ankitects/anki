# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import List, Optional

import aqt
from anki.collection import Config
from anki.lang import TR
from aqt.qt import *
from aqt.utils import getText, tooltip, tr


def set_due_date_dialog(
    *,
    mw: aqt.AnkiQt,
    parent: QDialog,
    card_ids: List[int],
    config_key: Optional[Config.String.Key.V],
) -> None:
    if not card_ids:
        return

    default_text = (
        mw.col.get_config_string(config_key) if config_key is not None else ""
    )
    prompt = "\n".join(
        [
            tr(TR.SCHEDULING_SET_DUE_DATE_PROMPT, cards=len(card_ids)),
            tr(TR.SCHEDULING_SET_DUE_DATE_PROMPT_HINT),
        ]
    )
    (days, success) = getText(
        prompt=prompt,
        parent=parent,
        default=default_text,
        title=tr(TR.ACTIONS_SET_DUE_DATE),
    )
    if not success or not days.strip():
        return

    mw.perform_op(
        lambda: mw.col.sched.set_due_date(card_ids, days, config_key),
        success=lambda _: tooltip(
            tr(TR.SCHEDULING_SET_DUE_DATE_DONE, cards=len(card_ids)),
            parent=parent,
        ),
    )


def forget_cards(*, mw: aqt.AnkiQt, parent: QDialog, card_ids: List[int]) -> None:
    if not card_ids:
        return

    mw.perform_op(
        lambda: mw.col.sched.schedule_cards_as_new(card_ids),
        success=lambda _: tooltip(
            tr(TR.SCHEDULING_FORGOT_CARDS, cards=len(card_ids)), parent=parent
        ),
    )
