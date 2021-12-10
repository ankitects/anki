# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Generate code for hook handling, and insert it into anki/hooks.py.

To add a new hook, update the hooks list below, then send a pull request
that includes the changes to this file.

In most cases, hooks are better placed in genhooks_gui.py.
"""

import sys

from hookslib import Hook, write_file

# Hook/filter list
######################################################################

hooks = [
    Hook(
        name="card_did_leech",
        args=["card: Card"],
        legacy_hook="leech",
        doc="Called by v1/v2 scheduler when a card is marked as a leech.",
    ),
    Hook(name="card_odue_was_invalid"),
    Hook(name="schema_will_change", args=["proceed: bool"], return_type="bool"),
    Hook(
        name="notes_will_be_deleted",
        args=["col: anki.collection.Collection", "ids: Sequence[anki.notes.NoteId]"],
        legacy_hook="remNotes",
    ),
    Hook(name="media_files_did_export", args=["count: int"]),
    Hook(
        name="exporters_list_created",
        args=["exporters: list[tuple[str, Any]]"],
        legacy_hook="exportersList",
    ),
    Hook(
        name="media_file_filter",
        args=["txt: str"],
        return_type="str",
        doc="""Allows manipulating the file path that media will be read from""",
    ),
    Hook(
        name="field_filter",
        args=[
            "field_text: str",
            "field_name: str",
            "filter_name: str",
            "ctx: anki.template.TemplateRenderContext",
        ],
        return_type="str",
        doc="""Allows you to define custom {{filters:..}}
        
        Your add-on can check filter_name to decide whether it should modify
        field_text or not before returning it.""",
    ),
    Hook(
        name="note_will_flush",
        args=["note: Note"],
        doc="Allow to change a note before it is added/updated in the database.",
    ),
    Hook(
        name="card_will_flush",
        args=["card: Card"],
        doc="Allow to change a card before it is added/updated in the database.",
    ),
    Hook(
        name="card_did_render",
        args=[
            "output: anki.template.TemplateRenderOutput",
            "ctx: anki.template.TemplateRenderContext",
        ],
        doc="Can modify the resulting text after rendering completes.",
    ),
    Hook(
        name="schedv2_did_answer_review_card",
        args=["card: anki.cards.Card", "ease: int", "early: bool"],
    ),
    Hook(
        name="scheduler_new_limit_for_single_deck",
        args=["count: int", "deck: anki.decks.DeckDict"],
        return_type="int",
        doc="""Allows changing the number of new card for this deck (without
        considering descendants).""",
    ),
    Hook(
        name="scheduler_review_limit_for_single_deck",
        args=["count: int", "deck: anki.decks.DeckDict"],
        return_type="int",
        doc="""Allows changing the number of rev card for this deck (without
        considering descendants).""",
    ),
    Hook(
        name="importing_importers",
        args=["importers: list[tuple[str, Any]]"],
        doc="""Allows updating the list of importers.
        The resulting list is not saved and should be changed each time the
        filter is called.
        
        NOTE: Updates to the import/export code are expected in the coming 
        months, and this hook may be replaced with another solution at that 
        time. Tracked on https://github.com/ankitects/anki/issues/1018""",
    ),
    # obsolete
    Hook(
        name="deck_added",
        args=["deck: anki.decks.DeckDict"],
        doc="Obsolete, do not use.",
    ),
    Hook(
        name="note_type_added",
        args=["notetype: anki.models.NotetypeDict"],
        doc="Obsolete, do not use.",
    ),
    Hook(
        name="sync_stage_did_change",
        args=["stage: str"],
        doc="Obsolete, do not use.",
    ),
    Hook(
        name="sync_progress_did_change",
        args=["msg: str"],
        doc="Obsolete, do not use.",
    ),
]

prefix = """\
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# This file is automatically generated; edit tools/genhooks.py instead.
# Please import from anki.hooks instead of this file.

# pylint: disable=unused-import

from __future__ import annotations

from typing import Any, Callable, Sequence
import anki
import anki.hooks
from anki.cards import Card
from anki.notes import Note
"""

suffix = ""

if __name__ == "__main__":
    path = sys.argv[1]
    write_file(path, hooks, prefix, suffix)
