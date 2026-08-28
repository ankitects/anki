# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from copy import deepcopy
from unittest.mock import MagicMock, patch

from anki.collection import Preferences as PreferencesProto
from aqt.preferences import Preferences


def make_prefs() -> PreferencesProto:
    prefs = PreferencesProto()
    prefs.scheduling.learn_ahead_secs = 20 * 60
    prefs.scheduling.rollover = 4
    prefs.reviewing.show_remaining_due_counts = True
    prefs.reviewing.show_intervals_on_buttons = True
    prefs.reviewing.time_limit_secs = 0
    prefs.reviewing.hide_audio_play_buttons = False
    prefs.reviewing.interrupt_audio_when_answering = False
    prefs.editing.adding_defaults_to_current_deck = True
    prefs.editing.paste_images_as_png = False
    prefs.editing.paste_strips_formatting = True
    prefs.editing.render_latex = False
    prefs.editing.default_search_text = ""
    prefs.editing.ignore_accents_in_search = True
    prefs.backups.daily = 2
    prefs.backups.weekly = 1
    prefs.backups.monthly = 1
    prefs.backups.minimum_interval_mins = 1
    return prefs


def make_form(prefs: PreferencesProto) -> MagicMock:
    """Build a form mock whose widgets report the values already in `prefs`."""
    form = MagicMock()
    form.lrnCutoff.value.return_value = int(prefs.scheduling.learn_ahead_secs / 60)
    form.dayOffset.value.return_value = prefs.scheduling.rollover
    form.showProgress.isChecked.return_value = prefs.reviewing.show_remaining_due_counts
    form.showEstimates.isChecked.return_value = (
        prefs.reviewing.show_intervals_on_buttons
    )
    form.timeLimit.value.return_value = int(prefs.reviewing.time_limit_secs / 60)
    form.showPlayButtons.isChecked.return_value = (
        not prefs.reviewing.hide_audio_play_buttons
    )
    form.interrupt_audio.isChecked.return_value = (
        prefs.reviewing.interrupt_audio_when_answering
    )
    form.useCurrent.currentIndex.return_value = (
        0 if prefs.editing.adding_defaults_to_current_deck else 1
    )
    form.pastePNG.isChecked.return_value = prefs.editing.paste_images_as_png
    form.paste_strips_formatting.isChecked.return_value = (
        prefs.editing.paste_strips_formatting
    )
    form.render_latex.isChecked.return_value = prefs.editing.render_latex
    form.default_search_text.text.return_value = prefs.editing.default_search_text
    form.ignore_accents_in_search.isChecked.return_value = (
        prefs.editing.ignore_accents_in_search
    )
    form.daily_backups.value.return_value = prefs.backups.daily
    form.weekly_backups.value.return_value = prefs.backups.weekly
    form.monthly_backups.value.return_value = prefs.backups.monthly
    form.minutes_between_backups.value.return_value = (
        prefs.backups.minimum_interval_mins
    )
    return form


def make_dialog(prefs: PreferencesProto, form: MagicMock) -> Preferences:
    dialog = Preferences.__new__(Preferences)
    dialog.mw = MagicMock()
    dialog.form = form
    dialog.prefs = prefs
    dialog.old_prefs = deepcopy(prefs)
    return dialog


@patch("aqt.preferences.set_preferences")
def test_update_collection_skips_backend_when_unchanged(
    mock_set_preferences: MagicMock,
) -> None:
    prefs = make_prefs()
    form = make_form(prefs)
    dialog = make_dialog(prefs, form)

    on_done = MagicMock()
    dialog.update_collection(on_done)

    mock_set_preferences.assert_not_called()
    dialog.mw.apply_collection_options.assert_called_once()
    on_done.assert_called_once()


@patch("aqt.preferences.set_preferences")
def test_update_collection_calls_backend_when_changed(
    mock_set_preferences: MagicMock,
) -> None:
    prefs = make_prefs()
    form = make_form(prefs)
    form.dayOffset.value.return_value = prefs.scheduling.rollover + 1
    dialog = make_dialog(prefs, form)

    on_done = MagicMock()
    dialog.update_collection(on_done)

    mock_set_preferences.assert_called_once_with(
        parent=dialog, preferences=dialog.prefs
    )
    op = mock_set_preferences.return_value
    op.success.assert_called_once()
    op.success.return_value.run_in_background.assert_called_once()

    # on_done() is only invoked once the backend op's success callback runs
    on_done.assert_not_called()
    success_callback = op.success.call_args.args[0]
    success_callback()
    dialog.mw.apply_collection_options.assert_called_once()
    on_done.assert_called_once()
