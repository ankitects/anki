# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import List, Optional, Sequence

import aqt
from anki.collection import Config
from anki.lang import TR
from aqt import AnkiQt
from aqt.main import PerformOpOptionalSuccessCallback
from aqt.qt import *
from aqt.utils import getText, tooltip, tr


def set_due_date_dialog(
    *,
    mw: aqt.AnkiQt,
    parent: QWidget,
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


def forget_cards(*, mw: aqt.AnkiQt, parent: QWidget, card_ids: List[int]) -> None:
    if not card_ids:
        return

    mw.perform_op(
        lambda: mw.col.sched.schedule_cards_as_new(card_ids),
        success=lambda _: tooltip(
            tr(TR.SCHEDULING_FORGOT_CARDS, cards=len(card_ids)), parent=parent
        ),
    )


def suspend_cards(
    *,
    mw: AnkiQt,
    card_ids: Sequence[int],
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(lambda: mw.col.sched.suspend_cards(card_ids), success=success)


def suspend_note(
    *,
    mw: AnkiQt,
    note_id: int,
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.taskman.run_in_background(
        lambda: mw.col.card_ids_of_note(note_id),
        lambda future: suspend_cards(mw=mw, card_ids=future.result(), success=success),
    )


def unsuspend_cards(*, mw: AnkiQt, card_ids: Sequence[int]) -> None:
    mw.perform_op(lambda: mw.col.sched.unsuspend_cards(card_ids))


def bury_cards(
    *,
    mw: AnkiQt,
    card_ids: Sequence[int],
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(lambda: mw.col.sched.bury_cards(card_ids), success=success)


def bury_note(
    *,
    mw: AnkiQt,
    note_id: int,
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.taskman.run_in_background(
        lambda: mw.col.card_ids_of_note(note_id),
        lambda future: bury_cards(mw=mw, card_ids=future.result(), success=success),
    )
