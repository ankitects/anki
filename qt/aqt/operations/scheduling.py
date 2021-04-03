# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Optional, Sequence

import aqt
from anki.cards import CardId
from anki.collection import CARD_TYPE_NEW, Config
from anki.decks import DeckId
from anki.notes import NoteId
from anki.scheduler import FilteredDeckForUpdate
from aqt import AnkiQt
from aqt.main import PerformOpOptionalSuccessCallback
from aqt.qt import *
from aqt.utils import disable_help_button, getText, tooltip, tr


def set_due_date_dialog(
    *,
    mw: aqt.AnkiQt,
    parent: QWidget,
    card_ids: Sequence[CardId],
    config_key: Optional[Config.String.Key.V],
) -> None:
    if not card_ids:
        return

    default_text = (
        mw.col.get_config_string(config_key) if config_key is not None else ""
    )
    prompt = "\n".join(
        [
            tr.scheduling_set_due_date_prompt(cards=len(card_ids)),
            tr.scheduling_set_due_date_prompt_hint(),
        ]
    )
    (days, success) = getText(
        prompt=prompt,
        parent=parent,
        default=default_text,
        title=tr.actions_set_due_date(),
    )
    if not success or not days.strip():
        return

    mw.perform_op(
        lambda: mw.col.sched.set_due_date(card_ids, days, config_key),
        success=lambda _: tooltip(
            tr.scheduling_set_due_date_done(cards=len(card_ids)),
            parent=parent,
        ),
    )


def forget_cards(
    *, mw: aqt.AnkiQt, parent: QWidget, card_ids: Sequence[CardId]
) -> None:
    if not card_ids:
        return

    mw.perform_op(
        lambda: mw.col.sched.schedule_cards_as_new(card_ids),
        success=lambda _: tooltip(
            tr.scheduling_forgot_cards(cards=len(card_ids)), parent=parent
        ),
    )


def reposition_new_cards_dialog(
    *, mw: AnkiQt, parent: QWidget, card_ids: Sequence[CardId]
) -> None:
    assert mw.col.db
    row = mw.col.db.first(
        f"select min(due), max(due) from cards where type={CARD_TYPE_NEW} and odid=0"
    )
    assert row
    (min_position, max_position) = row
    min_position = max(min_position or 0, 0)
    max_position = max_position or 0

    d = QDialog(parent)
    disable_help_button(d)
    d.setWindowModality(Qt.WindowModal)
    frm = aqt.forms.reposition.Ui_Dialog()
    frm.setupUi(d)

    txt = tr.browsing_queue_top(val=min_position)
    txt += "\n" + tr.browsing_queue_bottom(val=max_position)
    frm.label.setText(txt)

    frm.start.selectAll()
    if not d.exec_():
        return

    start = frm.start.value()
    step = frm.step.value()
    randomize = frm.randomize.isChecked()
    shift = frm.shift.isChecked()

    reposition_new_cards(
        mw=mw,
        parent=parent,
        card_ids=card_ids,
        starting_from=start,
        step_size=step,
        randomize=randomize,
        shift_existing=shift,
    )


def reposition_new_cards(
    *,
    mw: AnkiQt,
    parent: QWidget,
    card_ids: Sequence[CardId],
    starting_from: int,
    step_size: int,
    randomize: bool,
    shift_existing: bool,
) -> None:
    mw.perform_op(
        lambda: mw.col.sched.reposition_new_cards(
            card_ids=card_ids,
            starting_from=starting_from,
            step_size=step_size,
            randomize=randomize,
            shift_existing=shift_existing,
        ),
        success=lambda out: tooltip(
            tr.browsing_changed_new_position(count=out.count), parent=parent
        ),
    )


def suspend_cards(
    *,
    mw: AnkiQt,
    card_ids: Sequence[CardId],
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(lambda: mw.col.sched.suspend_cards(card_ids), success=success)


def suspend_note(
    *,
    mw: AnkiQt,
    note_id: NoteId,
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.taskman.run_in_background(
        lambda: mw.col.card_ids_of_note(note_id),
        lambda future: suspend_cards(mw=mw, card_ids=future.result(), success=success),
    )


def unsuspend_cards(*, mw: AnkiQt, card_ids: Sequence[CardId]) -> None:
    mw.perform_op(lambda: mw.col.sched.unsuspend_cards(card_ids))


def bury_cards(
    *,
    mw: AnkiQt,
    card_ids: Sequence[CardId],
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.perform_op(lambda: mw.col.sched.bury_cards(card_ids), success=success)


def bury_note(
    *,
    mw: AnkiQt,
    note_id: NoteId,
    success: PerformOpOptionalSuccessCallback = None,
) -> None:
    mw.taskman.run_in_background(
        lambda: mw.col.card_ids_of_note(note_id),
        lambda future: bury_cards(mw=mw, card_ids=future.result(), success=success),
    )


def rebuild_filtered_deck(*, mw: AnkiQt, deck_id: DeckId) -> None:
    mw.perform_op(lambda: mw.col.sched.rebuild_filtered_deck(deck_id))


def empty_filtered_deck(*, mw: AnkiQt, deck_id: DeckId) -> None:
    mw.perform_op(lambda: mw.col.sched.empty_filtered_deck(deck_id))


def add_or_update_filtered_deck(
    *,
    mw: AnkiQt,
    deck: FilteredDeckForUpdate,
    success: PerformOpOptionalSuccessCallback,
) -> None:
    mw.perform_op(
        lambda: mw.col.sched.add_or_update_filtered_deck(deck),
        success=success,
    )
