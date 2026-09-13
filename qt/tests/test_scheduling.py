# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from unittest.mock import MagicMock, patch

import pytest

import anki.lang
from anki.cards import CardId
from anki.collection import Config
from aqt.operations.scheduling import set_due_date_dialog


@pytest.mark.parametrize("lang", ["en", "zz"])
@pytest.mark.parametrize("fsrs", [True, False, None])
def test_set_due_date_dialog_hint_matches_scheduler(
    lang: str, fsrs: bool | None
) -> None:
    previous_lang = anki.lang.current_lang
    try:
        anki.lang.set_lang(lang)
        with (
            patch("aqt.mw") as mw,
            patch(
                "aqt.operations.scheduling.getText", return_value=("", False)
            ) as get_text,
        ):
            mw.col.get_config.return_value = fsrs

            set_due_date_dialog(
                parent=MagicMock(), card_ids=[CardId(1)], config_key=None
            )

            prompt = get_text.call_args.kwargs["prompt"]
            assert "0 = today" in prompt
            assert "3-7 = random choice of 3-7 days" in prompt
            if fsrs:
                assert "1 = tomorrow" in prompt
                assert "!" not in prompt
            else:
                assert "1! = tomorrow + change interval to 1" in prompt
    finally:
        anki.lang.set_lang(previous_lang)


@pytest.mark.parametrize("days, success", [("1", False), ("", True), ("  ", True)])
def test_set_due_date_dialog_does_not_schedule_without_input(
    days: str, success: bool
) -> None:
    with (
        patch("aqt.mw"),
        patch("aqt.operations.scheduling.getText", return_value=(days, success)),
        patch("aqt.operations.scheduling.CollectionOp") as operation,
    ):
        result = set_due_date_dialog(
            parent=MagicMock(), card_ids=[CardId(1)], config_key=None
        )

        assert result is None
        operation.assert_not_called()


def test_set_due_date_dialog_skips_empty_selection() -> None:
    with patch("aqt.mw"), patch("aqt.operations.scheduling.getText") as get_text:
        assert (
            set_due_date_dialog(parent=MagicMock(), card_ids=[], config_key=None)
            is None
        )
        get_text.assert_not_called()


@pytest.mark.parametrize("fsrs", [True, False])
@pytest.mark.parametrize("days", ["1", "3-7", "1!"])
def test_set_due_date_dialog_preserves_scheduling_input(fsrs: bool, days: str) -> None:
    with (
        patch("aqt.mw") as mw,
        patch(
            "aqt.operations.scheduling.getText", return_value=(days, True)
        ) as get_text,
        patch("aqt.operations.scheduling.CollectionOp") as operation,
    ):
        mw.col.get_config.return_value = fsrs
        mw.col.get_config_string.return_value = "3-7"
        card_ids = [CardId(1)]
        config_key = Config.String.SET_DUE_BROWSER

        set_due_date_dialog(
            parent=MagicMock(), card_ids=card_ids, config_key=config_key
        )

        assert get_text.call_args.kwargs["default"] == "3-7"
        col = MagicMock()
        operation.call_args.args[1](col)
        col.sched.set_due_date.assert_called_once_with(card_ids, days, config_key)
