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

    resp = col._backend.concept_mastery(
        taxonomy=_taxonomy(), search="", question_stats=[]
    )
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


def test_question_stats_change_ntr_via_python():
    col = getEmptyCol()
    _add_note(col, "enzyme", ["mcat::biochem::enzymes"])  # concept 1A

    # Baseline: a new card has no memory state, so NTR == topic_weight (2.0).
    base = col._backend.concept_mastery(
        taxonomy=_taxonomy(), search="", question_stats=[]
    )
    assert abs({e.concept_id: e for e in base}["1A"].ntr - 2.0) < 1e-9

    # Feed poor question performance for 1A; NTR must drop below the no-evidence
    # default as the wrong/attempt error rate (3/4 = 0.75) now drives weakness.
    stats = [concepts_pb2.ConceptQuestionStat(concept_id="1A", attempts=4, correct=1)]
    resp = col._backend.concept_mastery(
        taxonomy=_taxonomy(), search="", question_stats=stats
    )
    by_id = {e.concept_id: e for e in resp}
    assert by_id["1A"].questions_total == 4
    assert by_id["1A"].questions_correct == 1
    assert abs(by_id["1A"].question_accuracy - 0.25) < 1e-9
    # weakness 0.75 * topic_weight 2.0 = 1.5
    assert abs(by_id["1A"].ntr - 1.5) < 1e-9
    # Card recall is untouched -- questions feed NTR, not the Memory score.
    assert by_id["1A"].avg_recall == 0.0


def test_concept_aware_queue_is_read_only():
    col = getEmptyCol()
    _add_note(col, "optics", ["mcat::physics::optics"])

    # capture undo/redo status before the query
    undo_before = col.undo_status()

    resp = col._backend.concept_aware_queue(
        taxonomy=_taxonomy(), search="", question_stats=[]
    )

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
