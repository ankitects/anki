# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Generate code for hook handling, and insert it into anki/hooks.py.

To add a new hook:
- update the hooks list below
- run 'make develop'
- send a pull request that includes the changes to this file and hooks.py
"""

import os
from hookslib import Hook, update_file

# Hook/filter list
######################################################################

hooks = [
    Hook(name="card_did_leech", args=["card: Card"], legacy_hook="leech"),
    Hook(name="card_odue_was_invalid"),
    Hook(name="schema_will_change", args=["proceed: bool"], return_type="bool"),
    Hook(
        name="notes_will_be_deleted",
        args=["col: anki.storage._Collection", "ids: List[int]"],
        legacy_hook="remNotes",
    ),
    Hook(
        name="deck_added",
        args=["deck: Dict[str, Any]"],
        legacy_hook="newDeck",
        legacy_no_args=True,
    ),
    Hook(name="media_files_did_export", args=["count: int"]),
    Hook(
        name="exporters_list_created",
        args=["exporters: List[Tuple[str, Any]]"],
        legacy_hook="exportersList",
    ),
    Hook(
        name="search_terms_prepared",
        args=["searches: Dict[str, Callable]"],
        legacy_hook="search",
    ),
    Hook(
        name="note_type_added",
        args=["notetype: Dict[str, Any]"],
        legacy_hook="newModel",
        legacy_no_args=True,
    ),
    Hook(name="sync_stage_did_change", args=["stage: str"], legacy_hook="sync"),
    Hook(name="sync_progress_did_change", args=["msg: str"], legacy_hook="syncMsg"),
    Hook(
        name="tag_added", args=["tag: str"], legacy_hook="newTag", legacy_no_args=True,
    ),
    Hook(
        name="field_filter",
        args=[
            "field_text: str",
            "field_name: str",
            "filter_name: str",
            "ctx: TemplateRenderContext",
        ],
        return_type="str",
        doc="""Allows you to define custom {{filters:..}}
        
        Your add-on can check filter_name to decide whether it should modify
        field_text or not before returning it.""",
    ),
    Hook(
        name="card_did_render",
        args=["text: Tuple[str, str]", "ctx: TemplateRenderContext",],
        return_type="Tuple[str, str]",
        doc="Can modify the resulting text after rendering completes.",
    ),
]

if __name__ == "__main__":
    path = os.path.join(os.path.dirname(__file__), "..", "anki", "hooks.py")
    update_file(path, hooks)
