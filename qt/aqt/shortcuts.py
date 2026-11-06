# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from typing import TYPE_CHECKING

from anki.utils import is_mac
from aqt.utils import tr

if TYPE_CHECKING:
    from aqt.profiles import ProfileManager


@dataclass(frozen=True)
class ShortcutDefinition:
    id: str
    # labels are looked up lazily, as translations aren't loaded at import time
    _label: Callable[[], str]
    default_key: str
    _category: Callable[[], str]

    @property
    def label(self) -> str:
        return self._label()

    @property
    def category(self) -> str:
        return self._category()


def _menu_label(text: str) -> str:
    "Strip the accelerator marker and trailing ellipsis from a menu label."
    return text.replace("&", "").removesuffix("...")


def _flag_label(index: int) -> str:
    from aqt import mw

    if mw is not None:
        return mw.flags.get_flag(index).label
    return f"{tr.browsing_flag()} {index}"


def _answer(id: str, label: Callable[[], str], key: str) -> ShortcutDefinition:
    return ShortcutDefinition(
        id, label, key, tr.preferences_keybindings_category_answer_keys
    )


def _review(id: str, label: Callable[[], str], key: str) -> ShortcutDefinition:
    return ShortcutDefinition(
        id, label, key, tr.preferences_keybindings_category_review
    )


def _overview(id: str, label: Callable[[], str], key: str) -> ShortcutDefinition:
    return ShortcutDefinition(
        id, label, key, tr.preferences_keybindings_category_overview
    )


def _general(id: str, label: Callable[[], str], key: str) -> ShortcutDefinition:
    return ShortcutDefinition(
        id, label, key, tr.preferences_keybindings_category_general
    )


def _menu(id: str, label: Callable[[], str], key: str) -> ShortcutDefinition:
    return ShortcutDefinition(
        id, lambda: _menu_label(label()), key, tr.preferences_keybindings_category_menus
    )


# Action ids are stored in the profile, so they must not be renamed. The prefix
# before the first dot is the screen the shortcut is active on; "global"
# shortcuts are active everywhere.
SHORTCUT_DEFINITIONS = [
    _answer("review.answer.again", tr.studying_again, "1"),
    _answer("review.answer.hard", tr.studying_hard, "2"),
    _answer("review.answer.good", tr.studying_good, "3"),
    _answer("review.answer.easy", tr.studying_easy, "4"),
    _review("review.edit", tr.studying_edit, "e"),
    _review("review.show_answer", tr.studying_show_answer, "Space"),
    _review("review.more", tr.studying_more, "m"),
    _review("review.replay_audio", tr.actions_replay_audio, "r"),
    *[
        _review(
            f"review.flag.{index}",
            lambda index=index: _flag_label(index),  # type: ignore[misc]
            f"Ctrl+{index}",
        )
        for index in range(1, 8)
    ],
    _review("review.mark", tr.studying_mark_note, "*"),
    _review("review.bury_note", tr.studying_bury_note, "="),
    _review("review.bury_card", tr.studying_bury_card, "-"),
    _review("review.suspend_note", tr.studying_suspend_note, "!"),
    _review("review.suspend_card", tr.actions_suspend_card, "@"),
    _review("review.forget", tr.actions_forget_card, "Ctrl+Alt+N"),
    _review("review.create_copy", tr.actions_create_copy, "Ctrl+Alt+E"),
    _review(
        "review.delete_note",
        tr.studying_delete_note,
        "Ctrl+Backspace" if is_mac else "Ctrl+Delete",
    ),
    _review("review.set_due", tr.actions_set_due_date, "Ctrl+Shift+D"),
    _review("review.replay_recorded", tr.studying_replay_own_voice, "v"),
    _review("review.record", tr.studying_record_own_voice, "Shift+V"),
    _review("review.options", tr.actions_options, "o"),
    _review("review.card_info", tr.actions_card_info, "i"),
    _review("review.previous_card_info", tr.actions_previous_card_info, "Ctrl+Alt+I"),
    _review("review.undo", lambda: _menu_label(tr.qt_accel_undo()), "u"),
    _review("review.pause_audio", tr.studying_pause_audio, "5"),
    _review("review.seek_backward", tr.studying_audio_5s, "6"),
    _review("review.seek_forward", tr.studying_audio_and5s, "7"),
    _review("review.auto_advance", tr.actions_auto_advance, "Shift+A"),
    _overview("overview.options", tr.actions_options, "o"),
    _overview("overview.rebuild", tr.actions_rebuild, "r"),
    _overview("overview.empty", tr.studying_empty, "e"),
    _overview("overview.custom_study", tr.actions_custom_study, "c"),
    _overview("overview.unbury", tr.studying_unbury, "u"),
    _general("global.debug_console", tr.qt_misc_debug_console, "Ctrl+:"),
    _general("global.decks", tr.actions_decks, "d"),
    _general("global.study", tr.decks_study, "s"),
    _general("global.add", tr.actions_add, "a"),
    _general("global.browse", tr.qt_misc_browse, "b"),
    _general("global.stats", tr.qt_misc_stats, "t"),
    _general("global.sync", tr.qt_misc_sync, "y"),
    _menu("global.switch_profile", tr.qt_accel_switch_profile, "Ctrl+Shift+P"),
    _menu("global.import", tr.qt_accel_import, "Ctrl+Shift+I"),
    _menu("global.export", tr.qt_accel_export, "Ctrl+E"),
    _menu("global.exit", tr.qt_accel_exit, "Ctrl+Q"),
    _menu("global.undo", tr.qt_accel_undo, "Ctrl+Z"),
    _menu("global.redo", tr.qt_accel_redo, "Ctrl+Shift+Z"),
    _menu("global.study_deck", tr.qt_misc_study_deck, "/"),
    _menu("global.create_filtered", tr.qt_misc_create_filtered_deck, "F"),
    _menu("global.note_types", tr.qt_misc_manage_note_types, "Ctrl+Shift+N"),
    _menu("global.addons", tr.qt_misc_addons, "Ctrl+Shift+A"),
    _menu("global.preferences", tr.qt_accel_preferences, "Ctrl+P"),
    _menu("global.reset_zoom", tr.qt_accel_reset_zoom, "Ctrl+0"),
    _menu(
        "global.full_screen",
        tr.qt_accel_full_screen,
        "Ctrl+Meta+F" if is_mac else "F11",
    ),
    _menu("global.guide", tr.qt_accel_guide, "F1"),
]

SHORTCUTS_BY_ID = {definition.id: definition for definition in SHORTCUT_DEFINITIONS}

# Main window menu actions whose shortcuts are configurable, keyed by action id
MENU_ACTIONS = {
    "global.switch_profile": "actionSwitchProfile",
    "global.import": "actionImport",
    "global.export": "actionExport",
    "global.exit": "actionExit",
    "global.undo": "actionUndo",
    "global.redo": "actionRedo",
    "global.study_deck": "actionStudyDeck",
    "global.create_filtered": "actionCreateFiltered",
    "global.note_types": "actionNoteTypes",
    "global.addons": "actionAdd_ons",
    "global.preferences": "actionPreferences",
    "global.reset_zoom": "actionResetZoom",
    "global.full_screen": "actionFullScreen",
    "global.guide": "actionDocumentation",
}


def get_shortcut(pm: ProfileManager, action_id: str) -> str:
    "The user's key for action_id, or its default."
    return pm.get_shortcut(action_id, SHORTCUTS_BY_ID[action_id].default_key)
