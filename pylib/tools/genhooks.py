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
    Hook(name="leech", args=["card: Card"], legacy_hook="leech"),
    Hook(name="odue_invalid"),
    Hook(name="mod_schema", args=["proceed: bool"], return_type="bool"),
    Hook(
        name="remove_notes",
        args=["col: anki.storage._Collection", "ids: List[int]"],
        legacy_hook="remNotes",
    ),
    Hook(
        name="deck_created",
        args=["deck: Dict[str, Any]"],
        legacy_hook="newDeck",
        legacy_no_args=True,
    ),
    Hook(name="exported_media_files", args=["count: int"]),
    Hook(
        name="create_exporters_list",
        args=["exporters: List[Tuple[str, Any]]"],
        legacy_hook="exportersList",
    ),
    Hook(
        name="prepare_searches",
        args=["searches: Dict[str, Callable]"],
        legacy_hook="search",
    ),
    Hook(
        name="note_type_created",
        args=["notetype: Dict[str, Any]"],
        legacy_hook="newModel",
        legacy_no_args=True,
    ),
    Hook(name="sync_stage", args=["stage: str"], legacy_hook="sync"),
    Hook(name="sync_progress_message", args=["msg: str"], legacy_hook="syncMsg"),
    Hook(name="http_data_sent", args=["bytes: int"]),
    Hook(name="http_data_received", args=["bytes: int"]),
    Hook(
        name="tag_created", args=["tag: str"], legacy_hook="newTag", legacy_no_args=True
    ),
    Hook(
        name="modify_fields_for_rendering",
        args=["fields: Dict[str, str]", "notetype: Dict[str, Any]", "data: QAData",],
    ),
    Hook(
        name="original_card_template",
        args=["template: str", "question_side: bool"],
        return_type="str",
    ),
    Hook(
        name="rendered_card_template",
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
    ),
    Hook(
        name="field_replacement",
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
