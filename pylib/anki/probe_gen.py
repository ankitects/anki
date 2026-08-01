# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Ascent fork: probe generation pipeline.

Turns existing cards into probes (reworded variants of the same fact — see
rslib/src/probe/mod.rs and docs in the fork README) and stores them through
the existing `AddProbe` backend rpc. Two generators:

- `claude`: the Claude API (`ANTHROPIC_API_KEY` required). Candidates are
  additionally checked by a second model call that must confirm the probe
  tests the same fact.
- `baseline`: deterministic question/answer inversion. No network, no key.
  Deliberately worse; it exists so the app works with AI off and as the
  comparison arm for the AI probes.

Every candidate — from either generator — passes through a quality gate that
rejects probes which contain their own answer, are trivially similar to the
source question, or (claude only) fail the same-fact verifier. Rejection
counts are measured and reported, never estimated.

Run via `just probe-gen <collection> --deck NAME [--dry-run] [--baseline]`.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import dataclasses
import difflib
import hashlib
import json
import os
import re
import sys
import time
from collections.abc import Callable
from typing import Any

from anki import scheduler_pb2
from anki.cards import CardId
from anki.collection import Collection, StripHtmlMode

GENERATION_MODEL = "claude-sonnet-5"
# ponytail: thread pool, not the Message Batches API; switch to batches if
# runs regularly exceed ~10k cards and the 50% discount matters.
MAX_WORKERS = 4
SIMILARITY_LIMIT = 0.85

GENERATION_SYSTEM_PROMPT = """You write probes for a spaced-repetition app.

A probe is a reworded version of a flashcard: the same underlying fact, but a
deliberately unfamiliar surface form. A student who merely memorised the
card's wording must not be able to pattern-match the probe; a student who
understands the concept must still be able to answer it.

Rules:
- Test exactly the fact on the card. Do not test a related or adjacent fact.
- Do not reuse the card's phrasing. A synonym swap is a failure.
- The probe question must not contain or give away its own answer.
- Prefer application/scenario framings over definition lookups when the fact
  allows it.
- The expected answer should be short and gradeable, like a flashcard answer.

Return the probe question and its expected answer."""

VERIFIER_SYSTEM_PROMPT = """You are a strict reviewer of flashcard probes.

Given a source card (question and answer) and a candidate probe, decide
whether the probe is answerable from exactly the same fact the card tests —
no more, no less. Reject probes that are ambiguous, test a different or
additional fact, contain their own answer, or are a trivial rewording of the
source question. When unsure, reject."""

PROBE_SCHEMA = {
    "type": "object",
    "properties": {
        "question": {"type": "string"},
        "answer": {"type": "string"},
    },
    "required": ["question", "answer"],
    "additionalProperties": False,
}

VERDICT_SCHEMA = {
    "type": "object",
    "properties": {
        "same_fact": {"type": "boolean"},
        "reason": {"type": "string"},
    },
    "required": ["same_fact", "reason"],
    "additionalProperties": False,
}


@dataclasses.dataclass
class CardFact:
    card_id: int
    note_id: int
    question: str
    answer: str
    tags: list[str]


@dataclasses.dataclass
class Candidate:
    fact: CardFact
    question: str
    answer: str
    provenance: dict
    rejected: str | None = None


@dataclasses.dataclass
class Stats:
    cards: int = 0
    skipped_existing: int = 0
    candidates: int = 0
    stored: int = 0
    api_errors: int = 0
    rejected: dict[str, int] = dataclasses.field(default_factory=dict)

    def reject(self, reason: str) -> None:
        self.rejected[reason] = self.rejected.get(reason, 0) + 1

    @property
    def rejected_total(self) -> int:
        return sum(self.rejected.values())

    def rejection_rate(self) -> float:
        return self.rejected_total / self.candidates if self.candidates else 0.0


# Quality gate
##############################################################################


def _normalize(text: str) -> str:
    return re.sub(r"[\W_]+", " ", text.lower()).strip()


def gate_reject_reason(fact: CardFact, question: str, answer: str) -> str | None:
    """Reason a candidate probe must be rejected, or None if it passes.

    Deterministic checks only; the same-fact verifier is separate.
    """
    norm_q = _normalize(question)
    norm_a = _normalize(answer)
    if not norm_q or not norm_a:
        return "empty"
    if norm_a in norm_q:
        return "answer_leak"
    if difflib.SequenceMatcher(None, _normalize(fact.question), norm_q).ratio() >= (
        SIMILARITY_LIMIT
    ):
        return "too_similar"
    return None


# Generators
##############################################################################


def _provenance(generator: str, model: str, prompt_text: str) -> dict:
    return {
        "generator": generator,
        "model": model,
        "date": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "prompt_sha256": hashlib.sha256(prompt_text.encode()).hexdigest(),
    }


def baseline_probe(fact: CardFact) -> Candidate:
    """Deterministic question/answer inversion. Works with no API key."""
    question = (
        f'One of your cards has this as its answer: "{fact.answer}". '
        "What is that card asking?"
    )
    return Candidate(
        fact=fact,
        question=question,
        answer=fact.question,
        provenance=_provenance("baseline", "qa-inversion-v1", question),
    )


def _generation_prompt(fact: CardFact) -> str:
    tags = f"\nTags: {' '.join(fact.tags)}" if fact.tags else ""
    return (
        f"Card question: {fact.question}\nCard answer: {fact.answer}{tags}\n\n"
        "Write one probe for this card."
    )


def _client() -> Any:
    """Seam: the Anthropic client, imported lazily so no-AI runs need no SDK."""
    import anthropic

    return anthropic.Anthropic()


def claude_probe(fact: CardFact, model: str = GENERATION_MODEL) -> Candidate:
    client = _client()
    prompt = _generation_prompt(fact)
    response = client.messages.create(
        model=model,
        max_tokens=1024,
        system=GENERATION_SYSTEM_PROMPT,
        messages=[{"role": "user", "content": prompt}],
        output_config={"format": {"type": "json_schema", "schema": PROBE_SCHEMA}},
    )
    if response.stop_reason == "refusal":
        raise RuntimeError("generation request refused")
    data = json.loads(next(b.text for b in response.content if b.type == "text"))
    provenance = _provenance("claude", model, GENERATION_SYSTEM_PROMPT + prompt)
    return Candidate(fact, data["question"], data["answer"], provenance)


def claude_verify(candidate: Candidate, model: str = GENERATION_MODEL) -> None:
    """Ask a second model call whether the probe tests the same fact.

    Marks the candidate rejected on a negative verdict; records the verdict
    in provenance either way.
    """
    client = _client()
    fact = candidate.fact
    response = client.messages.create(
        model=model,
        max_tokens=1024,
        system=VERIFIER_SYSTEM_PROMPT,
        messages=[
            {
                "role": "user",
                "content": (
                    f"Source question: {fact.question}\n"
                    f"Source answer: {fact.answer}\n"
                    f"Probe question: {candidate.question}\n"
                    f"Probe answer: {candidate.answer}"
                ),
            }
        ],
        output_config={"format": {"type": "json_schema", "schema": VERDICT_SCHEMA}},
    )
    if response.stop_reason == "refusal":
        raise RuntimeError("verifier request refused")
    verdict = json.loads(next(b.text for b in response.content if b.type == "text"))
    candidate.provenance["verifier"] = {"model": model, **verdict}
    if not verdict["same_fact"]:
        candidate.rejected = "verifier_reject"


# Pipeline
##############################################################################


def _card_fact(col: Collection, card_id: CardId) -> CardFact:
    card = col.get_card(card_id)
    output = card.render_output()
    strip = lambda text: col._backend.strip_html(
        text=text, mode=StripHtmlMode.NORMAL
    ).strip()
    question = strip(output.question_text)
    answer = strip(output.answer_text.split("<hr id=answer>")[-1])
    return CardFact(
        card_id=card.id,
        note_id=card.nid,
        question=question,
        answer=answer,
        tags=card.note().tags,
    )


def _make_candidate(fact: CardFact, baseline: bool, model: str) -> Candidate:
    if baseline:
        candidate = baseline_probe(fact)
    else:
        candidate = claude_probe(fact, model)
    candidate.rejected = gate_reject_reason(fact, candidate.question, candidate.answer)
    if candidate.rejected is None and not baseline:
        claude_verify(candidate, model)
    return candidate


def generate_for_deck(
    col: Collection,
    deck: str,
    *,
    baseline: bool = False,
    dry_run: bool = False,
    limit: int = 0,
    model: str = GENERATION_MODEL,
    log: Callable[[str], None] = lambda _msg: None,
) -> Stats:
    stats = Stats()
    card_ids = list(col.find_cards(f'deck:"{deck}"'))
    if limit:
        card_ids = card_ids[:limit]

    facts = []
    for card_id in card_ids:
        stats.cards += 1
        if col._backend.get_probes(card_id):
            stats.skipped_existing += 1
            continue
        facts.append(_card_fact(col, card_id))

    # API calls run in threads on plain data; all collection access stays on
    # this thread. Baseline is pure computation but the same path keeps one
    # code shape.
    candidates: list[Candidate] = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
        futures = {
            pool.submit(_make_candidate, fact, baseline, model): fact for fact in facts
        }
        for future in concurrent.futures.as_completed(futures):
            fact = futures[future]
            try:
                candidates.append(future.result())
            except Exception as exc:
                stats.api_errors += 1
                log(f"card {fact.card_id}: generation failed: {exc}")

    for candidate in sorted(candidates, key=lambda c: c.fact.card_id):
        stats.candidates += 1
        if candidate.rejected:
            stats.reject(candidate.rejected)
            log(f"card {candidate.fact.card_id}: rejected ({candidate.rejected})")
            continue
        if not dry_run:
            probe = scheduler_pb2.Probe(
                card_id=candidate.fact.card_id,
                question=candidate.question,
                answer=candidate.answer,
                citation=f"card:{candidate.fact.card_id} note:{candidate.fact.note_id}",
                provenance=json.dumps(candidate.provenance, sort_keys=True),
            )
            col._backend.add_probe(probe)
        stats.stored += 1
        log(f"card {candidate.fact.card_id}: probe {'(dry-run) ' if dry_run else ''}ok")
    return stats


# CLI
##############################################################################


def _report(stats: Stats, dry_run: bool) -> str:
    lines = [
        f"cards in deck:      {stats.cards}",
        f"skipped (existing): {stats.skipped_existing}",
        f"candidates:         {stats.candidates}",
        f"api errors:         {stats.api_errors}",
        f"{'accepted (dry-run)' if dry_run else 'stored'}: {stats.stored}",
        f"rejected:           {stats.rejected_total} "
        f"(rate {stats.rejection_rate():.1%})",
    ]
    for reason, count in sorted(stats.rejected.items()):
        lines.append(f"  - {reason}: {count}")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("collection", help="path to a .anki2 collection file")
    parser.add_argument("--deck", required=True, help="deck name")
    parser.add_argument(
        "--dry-run", action="store_true", help="generate and gate, but store nothing"
    )
    parser.add_argument(
        "--baseline",
        action="store_true",
        help="use the deterministic no-AI generator",
    )
    parser.add_argument("--limit", type=int, default=0, help="max cards to process")
    parser.add_argument("--model", default=GENERATION_MODEL)
    args = parser.parse_args()

    if not args.baseline and not os.environ.get("ANTHROPIC_API_KEY"):
        print(
            "ANTHROPIC_API_KEY is not set; export it, or pass --baseline for the "
            "deterministic generator.",
            file=sys.stderr,
        )
        return 2

    col = Collection(args.collection)
    try:
        if not col.decks.by_name(args.deck):
            print(f"deck not found: {args.deck}", file=sys.stderr)
            return 2
        stats = generate_for_deck(
            col,
            args.deck,
            baseline=args.baseline,
            dry_run=args.dry_run,
            limit=args.limit,
            model=args.model,
            log=print,
        )
    finally:
        col.close()
    print(_report(stats, args.dry_run))
    return 0


if __name__ == "__main__":
    sys.exit(main())
