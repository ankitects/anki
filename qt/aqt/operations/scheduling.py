# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Sequence

import aqt
import aqt.forms
from anki.cards import CardId
from anki.collection import (
    CARD_TYPE_NEW,
    Collection,
    Config,
    OpChanges,
    OpChangesWithCount,
    OpChangesWithId,
)
from anki.decks import DeckId
from anki.notes import NoteId
from anki.scheduler import CustomStudyRequest, FilteredDeckForUpdate, UnburyDeck
from anki.scheduler.base import ScheduleCardsAsNew
from anki.scheduler.v3 import CardAnswer
from anki.scheduler.v3 import Scheduler as V3Scheduler
from aqt.operations import CollectionOp
from aqt.qt import *
from aqt.utils import disable_help_button, getText, tooltip, tr


def set_due_date_dialog(
    *,
    parent: QWidget,
    card_ids: Sequence[CardId],
    config_key: Config.String.V | None,
) -> CollectionOp[OpChanges] | None:
    assert aqt.mw
    if not card_ids:
        return None

    default_text = (
        aqt.mw.col.get_config_string(config_key) if config_key is not None else ""
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
        return None
    else:
        return CollectionOp(
            parent, lambda col: col.sched.set_due_date(card_ids, days, config_key)
        ).success(
            lambda _: tooltip(
                tr.scheduling_set_due_date_done(cards=len(card_ids)),
                parent=parent,
            )
        )


def forget_cards(
    *,
    parent: QWidget,
    card_ids: Sequence[CardId],
    context: ScheduleCardsAsNew.Context.V | None = None,
) -> CollectionOp[OpChanges] | None:
    assert aqt.mw

    dialog = QDialog(parent)
    disable_help_button(dialog)
    form = aqt.forms.forget.Ui_Dialog()
    form.setupUi(dialog)

    if context is not None:
        defaults = aqt.mw.col.sched.schedule_cards_as_new_defaults(context)
        form.restore_position.setChecked(defaults.restore_position)
        form.reset_counts.setChecked(defaults.reset_counts)

    if not dialog.exec():
        return None

    restore_position = form.restore_position.isChecked()
    reset_counts = form.reset_counts.isChecked()

    return CollectionOp(
        parent,
        lambda col: col.sched.schedule_cards_as_new(
            card_ids,
            restore_position=restore_position,
            reset_counts=reset_counts,
            context=context,
        ),
    ).success(
        lambda _: tooltip(
            tr.scheduling_forgot_cards(cards=len(card_ids)), parent=parent
        )
    )


def reposition_new_cards_dialog(
    *, parent: QWidget, card_ids: Sequence[CardId]
) -> CollectionOp[OpChangesWithCount] | None:
    from aqt import mw

    assert mw
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
    d.setWindowModality(Qt.WindowModality.WindowModal)
    frm = aqt.forms.reposition.Ui_Dialog()
    frm.setupUi(d)

    txt = tr.browsing_queue_top(val=min_position)
    txt += "\n" + tr.browsing_queue_bottom(val=max_position)
    frm.label.setText(txt)

    frm.start.selectAll()
    if not d.exec():
        return None

    start = frm.start.value()
    step = frm.step.value()
    randomize = frm.randomize.isChecked()
    shift = frm.shift.isChecked()

    return reposition_new_cards(
        parent=parent,
        card_ids=card_ids,
        starting_from=start,
        step_size=step,
        randomize=randomize,
        shift_existing=shift,
    )


def reposition_new_cards(
    *,
    parent: QWidget,
    card_ids: Sequence[CardId],
    starting_from: int,
    step_size: int,
    randomize: bool,
    shift_existing: bool,
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(
        parent,
        lambda col: col.sched.reposition_new_cards(
            card_ids=card_ids,
            starting_from=starting_from,
            step_size=step_size,
            randomize=randomize,
            shift_existing=shift_existing,
        ),
    ).success(
        lambda out: tooltip(
            tr.browsing_changed_new_position(count=out.count), parent=parent
        )
    )


def suspend_cards(
    *,
    parent: QWidget,
    card_ids: Sequence[CardId],
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(parent, lambda col: col.sched.suspend_cards(card_ids))


def suspend_note(
    *,
    parent: QWidget,
    note_ids: Sequence[NoteId],
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(parent, lambda col: col.sched.suspend_notes(note_ids))


def unsuspend_cards(
    *, parent: QWidget, card_ids: Sequence[CardId]
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.sched.unsuspend_cards(card_ids))


def bury_cards(
    *,
    parent: QWidget,
    card_ids: Sequence[CardId],
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(parent, lambda col: col.sched.bury_cards(card_ids))


def bury_notes(
    *,
    parent: QWidget,
    note_ids: Sequence[NoteId],
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(parent, lambda col: col.sched.bury_notes(note_ids))


def unbury_cards(
    *, parent: QWidget, card_ids: Sequence[CardId]
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.sched.unbury_cards(card_ids))


def rebuild_filtered_deck(
    *, parent: QWidget, deck_id: DeckId
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(parent, lambda col: col.sched.rebuild_filtered_deck(deck_id))


def empty_filtered_deck(*, parent: QWidget, deck_id: DeckId) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.sched.empty_filtered_deck(deck_id))


def add_or_update_filtered_deck(
    *,
    parent: QWidget,
    deck: FilteredDeckForUpdate,
) -> CollectionOp[OpChangesWithId]:
    return CollectionOp(parent, lambda col: col.sched.add_or_update_filtered_deck(deck))


def unbury_deck(
    *,
    parent: QWidget,
    deck_id: DeckId,
    mode: UnburyDeck.Mode.V = UnburyDeck.ALL,
) -> CollectionOp[OpChanges]:
    return CollectionOp(
        parent, lambda col: col.sched.unbury_deck(deck_id=deck_id, mode=mode)
    )


def answer_card(
    *,
    parent: QWidget,
    answer: CardAnswer,
) -> CollectionOp[OpChanges]:
    def answer_v3(col: Collection) -> OpChanges:
        assert isinstance(col.sched, V3Scheduler)
        return col.sched.answer_card(answer)

    return CollectionOp(parent, answer_v3)


def custom_study(
    *,
    parent: QWidget,
    request: CustomStudyRequest,
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.sched.custom_study(request))
