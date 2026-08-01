# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import json

from anki import probe_gen
from anki.probe_gen import CardFact, gate_reject_reason
from tests.shared import getEmptyCol

FACT = CardFact(
    card_id=1,
    note_id=2,
    question="Competitive inhibitor - effect on Km and Vmax?",
    answer="Km increases, Vmax unchanged",
    tags=["biochem"],
)

GOOD_PROBE = (
    "An assay shows the same maximum rate but twice the substrate needed for "
    "half-maximal velocity. What kind of inhibition is this?"
)


def test_gate_accepts_a_real_probe():
    assert gate_reject_reason(FACT, GOOD_PROBE, "Competitive inhibition") is None


def test_gate_rejects_answer_leak():
    leaky = "Under competitive inhibition, Km increases, Vmax unchanged - true?"
    assert gate_reject_reason(FACT, leaky, "Km increases, Vmax unchanged") == (
        "answer_leak"
    )


def test_gate_rejects_near_copy_of_source_question():
    synonym_swap = "Competitive inhibitor - effect upon Km and Vmax?"
    assert gate_reject_reason(FACT, synonym_swap, "Km up, Vmax same") == "too_similar"


def test_gate_rejects_empty():
    assert gate_reject_reason(FACT, "", "something") == "empty"


def test_baseline_probe_passes_its_own_gate():
    candidate = probe_gen.baseline_probe(FACT)
    assert candidate.rejected is None
    assert gate_reject_reason(FACT, candidate.question, candidate.answer) is None
    assert candidate.provenance["generator"] == "baseline"
    assert candidate.provenance["prompt_sha256"]


def test_baseline_pipeline_stores_probes_with_provenance():
    """End-to-end with no API key: cards in, probes stored, rate reported."""
    col = getEmptyCol()
    for front, back in [("capital of France", "Paris"), ("2 + 2", "4")]:
        note = col.newNote()
        note["Front"] = front
        note["Back"] = back
        col.addNote(note)

    stats = probe_gen.generate_for_deck(col, "Default", baseline=True)
    assert stats.cards == 2
    assert stats.stored == 2
    assert stats.candidates == 2

    card_id = col.find_cards("")[0]
    probes = col._backend.get_probes(card_id)
    assert len(probes) == 1
    provenance = json.loads(probes[0].provenance)
    assert provenance["generator"] == "baseline"
    assert provenance["model"] and provenance["date"] and provenance["prompt_sha256"]
    assert probes[0].citation.startswith(f"card:{card_id}")

    # a second run skips cards that already have a probe
    again = probe_gen.generate_for_deck(col, "Default", baseline=True)
    assert again.skipped_existing == 2
    assert again.stored == 0


def test_dry_run_stores_nothing():
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "capital of France"
    note["Back"] = "Paris"
    col.addNote(note)

    stats = probe_gen.generate_for_deck(col, "Default", baseline=True, dry_run=True)
    assert stats.stored == 1
    assert not col._backend.get_probes(col.find_cards("")[0])


def test_rejected_candidates_are_counted_not_stored(monkeypatch):
    """A generator that returns garbage must produce a non-zero rejection rate."""
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "capital of France"
    note["Back"] = "Paris"
    col.addNote(note)

    def leaky(fact):
        return probe_gen.Candidate(
            fact=fact,
            question=f"Which city is {fact.answer}?",
            answer=fact.answer,
            provenance={"generator": "test"},
        )

    monkeypatch.setattr(probe_gen, "baseline_probe", leaky)
    stats = probe_gen.generate_for_deck(col, "Default", baseline=True)
    assert stats.stored == 0
    assert stats.rejected == {"answer_leak": 1}
    assert stats.rejection_rate() == 1.0
    assert not col._backend.get_probes(col.find_cards("")[0])


class _FakeResponse:
    stop_reason = "end_turn"

    def __init__(self, payload):
        block = type("Block", (), {"type": "text", "text": json.dumps(payload)})()
        self.content = [block]


def _fake_client(responses):
    """Return a stand-in anthropic client yielding `responses` in order."""
    calls = iter(responses)

    class Messages:
        @staticmethod
        def create(**_kwargs):
            return _FakeResponse(next(calls))

    return type("Client", (), {"messages": Messages()})()


def test_claude_probe_records_provenance(monkeypatch):
    monkeypatch.setattr(
        probe_gen,
        "_client",
        lambda: _fake_client([{"question": GOOD_PROBE, "answer": "Competitive"}]),
    )
    candidate = probe_gen.claude_probe(FACT)
    assert candidate.question == GOOD_PROBE
    assert candidate.provenance["generator"] == "claude"
    assert candidate.provenance["model"] == probe_gen.GENERATION_MODEL
    assert len(candidate.provenance["prompt_sha256"]) == 64


def test_verifier_rejects_a_different_fact(monkeypatch):
    candidate = probe_gen.Candidate(FACT, GOOD_PROBE, "Competitive", {})
    monkeypatch.setattr(
        probe_gen,
        "_client",
        lambda: _fake_client(
            [{"same_fact": False, "reason": "tests a different fact"}]
        ),
    )
    probe_gen.claude_verify(candidate)
    assert candidate.rejected == "verifier_reject"
    assert candidate.provenance["verifier"]["reason"] == "tests a different fact"


def test_verifier_accepts_and_records_verdict(monkeypatch):
    candidate = probe_gen.Candidate(FACT, GOOD_PROBE, "Competitive", {})
    monkeypatch.setattr(
        probe_gen,
        "_client",
        lambda: _fake_client([{"same_fact": True, "reason": "same fact"}]),
    )
    probe_gen.claude_verify(candidate)
    assert candidate.rejected is None
    assert candidate.provenance["verifier"]["same_fact"] is True


def test_api_failure_leaves_collection_untouched(monkeypatch):
    """A failed generation must be counted, not stored, and not half-written."""
    col = getEmptyCol()
    note = col.newNote()
    note["Front"] = "capital of France"
    note["Back"] = "Paris"
    col.addNote(note)

    def boom():
        raise RuntimeError("connection reset")

    monkeypatch.setattr(probe_gen, "_client", boom)
    stats = probe_gen.generate_for_deck(col, "Default")
    assert stats.api_errors == 1
    assert stats.candidates == 0
    assert stats.stored == 0
    assert not col._backend.get_probes(col.find_cards("")[0])
