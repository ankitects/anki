# Copyright: Anki Software, LLC and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import types
from typing import cast
from unittest.mock import MagicMock, patch

from anki.collection import OpChanges
from aqt.browser.browser import Browser
from aqt.browser.previewer import Previewer
from aqt.reviewer import RefreshNeeded, Reviewer


def make_previewer(
    *,
    state: str = "question",
    card_id: int = 1,
    note_mod: int = 100,
    flags: int = 0,
    ord_: int = 0,
    did: int = 1,
    deck_name: str = "Default",
    notetype_mod: int = 200,
) -> Previewer:
    previewer = cast(Previewer, MagicMock(spec=Previewer))
    previewer._state = state  # type: ignore[assignment]
    card = MagicMock()
    card.id = card_id
    card.flags = flags
    card.ord = ord_
    card.current_deck_id.return_value = did
    note = MagicMock()
    note.mod = note_mod
    card.note.return_value = note
    card.note_type.return_value = {"mod": notetype_mod}
    previewer.card.return_value = card
    previewer.mw = MagicMock()
    previewer.mw.col.decks.name.return_value = deck_name
    return previewer


def test_previewer_state_changes_on_flag_deck_ord_and_notetype_mod() -> None:
    baseline = Previewer._state_and_mod(make_previewer())
    assert baseline == Previewer._state_and_mod(make_previewer())
    assert baseline != Previewer._state_and_mod(make_previewer(flags=1))
    assert baseline != Previewer._state_and_mod(make_previewer(ord_=1))
    assert baseline != Previewer._state_and_mod(make_previewer(did=2))
    assert baseline != Previewer._state_and_mod(make_previewer(deck_name="Renamed"))
    assert baseline != Previewer._state_and_mod(make_previewer(notetype_mod=201))


def test_browser_operation_renders_preview_on_deck_change() -> None:
    browser = cast(Browser, MagicMock(spec=Browser))
    browser.editor = None
    browser.table = MagicMock()
    browser.sidebar = MagicMock()
    changes = OpChanges(deck=True)

    with patch("aqt.browser.browser.current_window", return_value=browser):
        Browser.on_operation_did_execute(browser, changes, None)

    browser._renderPreview.assert_called_once_with()


def make_reviewer(
    *, state: str, old_q: str, old_a: str, new_q: str, new_a: str
) -> Reviewer:
    reviewer = cast(Reviewer, MagicMock(spec=Reviewer))
    reviewer.state = state
    reviewer.mw = MagicMock()
    reviewer._reload_and_redraw_if_changed = types.MethodType(  # type: ignore[method-assign]
        Reviewer._reload_and_redraw_if_changed, reviewer
    )

    old_output = MagicMock()
    old_output.question_and_style.return_value = old_q
    old_output.answer_and_style.return_value = old_a

    new_output = MagicMock()
    new_output.question_and_style.return_value = new_q
    new_output.answer_and_style.return_value = new_a

    card = MagicMock()
    card._render_output = old_output
    card.render_output.return_value = new_output
    reviewer.card = card
    return reviewer


def test_reviewer_flag_redraws_only_when_rendered_output_changes() -> None:
    for state in ("question", "answer"):
        changed = make_reviewer(
            state=state,
            old_q="q flag0",
            old_a="a flag0",
            new_q="q flag1",
            new_a="a flag1",
        )
        changed._refresh_needed = RefreshNeeded.FLAG
        Reviewer.refresh_if_needed(changed)
        changed._redraw_current_card.assert_called_once_with()  # type: ignore[attr-defined]
        changed._update_flag_icon.assert_called_once_with()  # type: ignore[attr-defined]

        unchanged = make_reviewer(
            state=state,
            old_q="q",
            old_a="a",
            new_q="q",
            new_a="a",
        )
        unchanged._refresh_needed = RefreshNeeded.FLAG
        Reviewer.refresh_if_needed(unchanged)
        unchanged._redraw_current_card.assert_not_called()  # type: ignore[attr-defined]
        unchanged._update_flag_icon.assert_called_once_with()  # type: ignore[attr-defined]


def test_reviewer_mark_redraws_only_when_rendered_output_changes() -> None:
    for state in ("question", "answer"):
        changed = make_reviewer(
            state=state,
            old_q="q ",
            old_a="a ",
            new_q="q marked",
            new_a="a marked",
        )
        assert changed.card is not None
        changed.card.note.return_value.has_tag.return_value = False
        with patch("aqt.reviewer.add_tags_to_notes") as add_tags:
            Reviewer.toggle_mark_on_current_note(changed)
            redraw_mark = add_tags.return_value.success.call_args.args[0]
            redraw_mark(MagicMock())
        changed._redraw_current_card.assert_called_once_with()  # type: ignore[attr-defined]
        changed._update_mark_icon.assert_called_once_with()  # type: ignore[attr-defined]

        unchanged = make_reviewer(
            state=state,
            old_q="q",
            old_a="a",
            new_q="q",
            new_a="a",
        )
        assert unchanged.card is not None
        unchanged.card.note.return_value.has_tag.return_value = True
        with patch("aqt.reviewer.remove_tags_from_notes") as remove_tags:
            Reviewer.toggle_mark_on_current_note(unchanged)
            redraw_mark = remove_tags.return_value.success.call_args.args[0]
            redraw_mark(MagicMock())
        unchanged._redraw_current_card.assert_not_called()  # type: ignore[attr-defined]
        unchanged._update_mark_icon.assert_called_once_with()  # type: ignore[attr-defined]
