# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from concurrent.futures import Future
from typing import List

import aqt
from anki.collection import Config
from anki.lang import TR
from aqt.qt import *
from aqt.utils import getText, showWarning, tooltip, tr


def set_due_date_dialog(
    *,
    mw: aqt.AnkiQt,
    parent: QDialog,
    card_ids: List[int],
    default_key: Config.String.Key.V,
    on_done: Callable[[], None],
) -> None:
    if not card_ids:
        return

    default = mw.col.get_config_string(default_key)
    prompt = "\n".join(
        [
            tr(TR.SCHEDULING_SET_DUE_DATE_PROMPT, cards=len(card_ids)),
            tr(TR.SCHEDULING_SET_DUE_DATE_PROMPT_HINT),
        ]
    )

    (days, success) = getText(
        prompt=prompt,
        parent=parent,
        default=default,
        title=tr(TR.ACTIONS_SET_DUE_DATE),
    )
    if not success or not days.strip():
        return

    def set_due() -> None:
        mw.col.sched.set_due_date(card_ids, days)
        if days != default:
            mw.col.set_config_string(default_key, days)

    def after_set(fut: Future) -> None:
        try:
            fut.result()
        except Exception as e:
            showWarning(str(e))
            on_done()
            return

        tooltip(
            tr(TR.SCHEDULING_SET_DUE_DATE_DONE, cards=len(card_ids)),
            parent=parent,
        )

        on_done()

    mw.checkpoint(tr(TR.ACTIONS_SET_DUE_DATE))
    mw.taskman.with_progress(set_due, after_set)


def forget_cards(
    *, mw: aqt.AnkiQt, parent: QDialog, card_ids: List[int], on_done: Callable[[], None]
) -> None:
    if not card_ids:
        return

    def on_done_wrapper(fut: Future) -> None:
        try:
            fut.result()
        except Exception as e:
            showWarning(str(e))
        else:
            tooltip(tr(TR.SCHEDULING_FORGOT_CARDS, cards=len(card_ids)), parent=parent)

        on_done()

    mw.checkpoint(tr(TR.ACTIONS_FORGET))
    mw.taskman.with_progress(
        lambda: mw.col.sched.schedule_cards_as_new(card_ids), on_done_wrapper
    )
