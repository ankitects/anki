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
        name="notes_will_delete",
        args=["col: anki.storage._Collection", "ids: List[int]"],
        legacy_hook="remNotes",
    ),
    Hook(
        name="deck_did_create",
        args=["deck: Dict[str, Any]"],
        legacy_hook="newDeck",
        legacy_no_args=True,
    ),
    Hook(name="media_files_did_export", args=["count: int"]),
    Hook(
        name="exporters_list_did_create",
        args=["exporters: List[Tuple[str, Any]]"],
        legacy_hook="exportersList",
    ),
    Hook(
        name="search_terms_did_prepare",
        args=["searches: Dict[str, Callable]"],
        legacy_hook="search",
    ),
    Hook(
        name="note_type_did_create",
        args=["notetype: Dict[str, Any]"],
        legacy_hook="newModel",
        legacy_no_args=True,
    ),
    Hook(name="sync_stage_did_change", args=["stage: str"], legacy_hook="sync"),
    Hook(name="sync_progress_did_change", args=["msg: str"], legacy_hook="syncMsg"),
    Hook(name="http_data_did_send", args=["bytes: int"]),
    Hook(name="http_data_did_receive", args=["bytes: int"]),
    Hook(
        name="tag_did_create",
        args=["tag: str"],
        legacy_hook="newTag",
        legacy_no_args=True,
    ),
    Hook(
        name="fields_will_render",
        args=["fields: Dict[str, str]", "notetype: Dict[str, Any]", "data: QAData",],
        doc="Can modify the available fields prior to rendering.",
    ),
    Hook(
        name="card_template_will_render",
        args=["template: str", "question_side: bool"],
        return_type="str",
        doc="Can modify the the card template used for rendering.",
    ),
    Hook(
        name="card_template_did_render",
        args=[
            "text: str",
            "side: str",
            "fields: Dict[str, str]",
            "notetype: Dict[str, Any]",
            "data: QAData",
            # the hook in latex.py needs access to the collection and
            # can't rely on the GUI's mw.col
            "col: anki.storage._Collection",
        ],
        return_type="str",
        legacy_hook="mungeQA",
        doc="Can modify the resulting text after rendering completes.",
    ),
    Hook(
        name="card_template_filter_will_apply",
        args=[
            "field_text: str",
            "field_name: str",
            "filter_name: str",
            "fields: Dict[str, str]",
        ],
        return_type="str",
    ),
]

if __name__ == "__main__":
    path = os.path.join(os.path.dirname(__file__), "..", "anki", "hooks.py")
    update_file(path, hooks)
