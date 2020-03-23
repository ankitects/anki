# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
A Python implementation of some backend commands.

Unimplemented commands will be forwarded on to the Rust backend.
"""

from __future__ import annotations

from typing import Any, Dict, Tuple

import anki  # pylint: disable=unused-import
import anki.backend_pb2 as pb


class PythonBackend:
    def __init__(self, col: anki.storage._Collection):
        self.col = col

    def run_command_bytes(self, input: bytes) -> bytes:
        pb_input = pb.BackendInput()
        pb_input.ParseFromString(input)

        pb_output = self.run_command(pb_input)

        output = pb_output.SerializeToString()
        return output

    def run_command(self, input: pb.BackendInput) -> pb.BackendOutput:
        kind = input.WhichOneof("value")
        handler = getattr(self, kind, None)
        # run the equivalent of the following, based on available method names
        # if kind == "deck_tree":
        #     return pb.BackendOutput(deck_tree=self.deck_tree(input.deck_tree))
        if handler is not None:
            input_variant = getattr(input, kind)
            output_variant = handler(input_variant)
            output_args: Dict[str, Any] = {kind: output_variant}
            output = pb.BackendOutput(**output_args)
            return output
        else:
            # forward any unknown commands onto the Rust backend
            return self.col.backend._run_command(input)

    def deck_tree(self, _input: pb.Empty) -> pb.DeckTreeOut:
        native = self.col.sched.deckDueTree()
        return native_deck_tree_to_proto(native)

    # def find_cards(self, input: pb.FindCardsIn) -> pb.FindCardsOut:
    #     cids = self.col.findCards(input.search)
    #     return pb.FindCardsOut(card_ids=cids)
    #
    # def browser_rows(self, input: pb.BrowserRowsIn) -> pb.BrowserRowsOut:
    #     sort_fields = []
    #     for cid in input.card_ids:
    #         sort_fields.append(
    #             self.col.db.scalar(
    #                 "select sfld from notes n,cards c where n.id=c.nid and c.id=?", cid
    #             )
    #         )
    #     return pb.BrowserRowsOut(sort_fields=sort_fields)


def native_deck_tree_to_proto(native):
    top = pb.DeckTreeNode(children=[native_deck_node_to_proto(c) for c in native])
    out = pb.DeckTreeOut(top=top)
    return out


def native_deck_node_to_proto(native: Tuple) -> pb.DeckTreeNode:
    return pb.DeckTreeNode(
        # fixme: need to decide whether full list
        # should be included or just tail element
        names=[native[0]],
        deck_id=native[1],
        review_count=native[2],
        learn_count=native[3],
        new_count=native[4],
        children=[native_deck_node_to_proto(c) for c in native[5]],
        # fixme: currently hard-coded
        collapsed=False,
    )
