# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# coding: utf-8

"""End-to-end test of the MCAT fork's concept-aware engine (FR-3).

Exercises the new ConceptsService RPCs (concept_mastery + concept_aware_queue)
through the Python backend on a real in-memory collection, and confirms the
queries are read-only (undo state untouched). NO AI: the signal is a
deterministic function of FSRS retrievability and caller-supplied topic weights.
"""

from anki import concepts_pb2
from tests.shared import getEmptyCol


def _taxonomy() -> concepts_pb2.ConceptTaxonomy:
    return concepts_pb2.ConceptTaxonomy(
        rules=[
            concepts_pb2.ConceptRule(
                id="1A", topic_weight=2.0, tag_patterns=["mcat::biochem*"]
            ),
            concepts_pb2.ConceptRule(
                id="4C", topic_weight=3.0, tag_patterns=["mcat::physics::*"]
            ),
        ]
    )


def _add_note(col, front: str, tags: list[str]):
    note = col.newNote()
    note["Front"] = front
    note["Back"] = "back"
    note.tags = tags
    col.addNote(note)
    return note


def test_concept_mastery_from_python():
    col = getEmptyCol()
    _add_note(col, "enzyme", ["mcat::biochem::enzymes"])
    _add_note(col, "optics", ["mcat::physics::optics"])
    _add_note(col, "unrelated", ["misc"])  # maps to no concept

    resp = col._backend.concept_mastery(taxonomy=_taxonomy(), search="")
    by_id = {e.concept_id: e for e in resp}

    # Both taxonomy concepts are reported, even with new (no-memory-state) cards.
    assert set(by_id) == {"1A", "4C"}
    assert by_id["1A"].cards_total == 1
    assert by_id["4C"].cards_total == 1
    # New cards have no memory state -> excluded from avg_recall, weakness=1.0,
    # so NTR == topic_weight.
    assert by_id["1A"].cards_mastered == 0
    assert by_id["1A"].avg_recall == 0.0
    assert abs(by_id["1A"].ntr - 2.0) < 1e-9
    assert abs(by_id["4C"].ntr - 3.0) < 1e-9


def test_concept_aware_queue_is_read_only():
    col = getEmptyCol()
    _add_note(col, "optics", ["mcat::physics::optics"])

    # capture undo/redo status before the query
    undo_before = col.undo_status()

    resp = col._backend.concept_aware_queue(taxonomy=_taxonomy(), search="")

    # the new card is due and surfaces with its concept
    assert len(resp) == 1
    assert list(resp[0].concepts) == ["4C"]
    assert resp[0].priority > 0

    # the query recorded nothing undoable: status is unchanged
    undo_after = col.undo_status()
    assert undo_after.undo == undo_before.undo
    assert undo_after.redo == undo_before.redo
    # collection is still usable (no corruption); a normal op still works
    assert col.card_count() == 1
