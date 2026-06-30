# Why the engine change belongs in Rust, not Python

> One-page note for the Wednesday hand-in (spec §7a item 3 / PRD §6.4). Describes the
> change as actually built and tested (7 Rust unit tests + 2 Python tests passing).

## The change, in one line

A concept-aware review queue + a per-concept **Need-to-Review (NTR)** signal + a **mastery
query** RPC, added to Anki's Rust core. NTR orders due/new cards by `topic weight × concept
weakness`, where `weakness = 1 − mean FSRS retrievability` of the cards tagged to a concept;
the mastery query returns, per concept, `{cards_total, cards_mastered (retrievability ≥ 0.9),
avg_recall, ntr}`. Fully **deterministic — no AI**.

- New protobuf service/RPCs: `proto/anki/concepts.proto` → `ConceptsService` with
  `ConceptAwareQueue(ConceptAwareQueueRequest) → ConceptAwareQueueResponse` and
  `ConceptMastery(ConceptMasteryRequest) → ConceptMasteryResponse`; messages `ConceptRule`,
  `ConceptTaxonomy`, `ConceptAwareQueueEntry`, `ConceptMasteryEntry`.
- Core implementation: `rslib/src/concepts/mod.rs` (NTR logic + set-based loader) and
  `rslib/src/concepts/service.rs` (trait impl on `Collection`); registered via one line each in
  `rslib/src/lib.rs` and `rslib/proto/src/lib.rs`.
- The taxonomy is **caller-supplied per request** (from FR-2's `mcat/taxonomy.json`), so the
  engine never reads a file and stays decoupled from the concept data.

## Why Rust, not Python — the argument

**1. Performance on 50,000 cards.** The concept-aware queue and the mastery query have to
scan/aggregate every due card and its concept tags and DSR state. The spec's latency targets
are hard: button-press ack p95 < 50 ms, next card p95 < 100 ms, dashboard first load
p95 < 1 s on a 50k-card deck (spec §10). Doing per-concept aggregation in Python would mean
pulling every card's tags + FSRS state across the protobuf bridge and recomputing
retrievability per refresh. In Rust it runs in-process: a single SQL join (`cards → notes`)
into `search_cids`, one pass to match tags against the taxonomy and aggregate DSR — no
per-card round-trips, no serialization tax. _(Benchmark numbers on the real 50k AnKing deck
are deferred to the deck-import round; the implementation is already set-based and O(cards),
not N+1.)_

**2. One shared engine ships to desktop _and_ phone.** The engine is shared: the same Rust
backend powers the desktop app and the AnkiDroid-based phone build (spec §3, "share the
engine, do not rewrite it"). Putting NTR + scheduling in Rust means the phone gets the exact
same behavior **for free** — no second implementation, no drift. If this lived in Python
(`aqt`/`pylib`), the phone would either need a re-implementation (which the spec says "does
not count") or wouldn't get the feature at all. _(Phone verification is deferred to the AnkiDroid round;
because the RPC lives in the shared Rust backend it ships to the phone build unchanged.)_

**3. Type-safe protobuf contract.** Anki's Python and TypeScript APIs are generated from the
`.proto` definitions, so exposing the new queue + mastery query as protobuf RPCs gives every
layer (Python `_backend.py`, the web frontend) a **type-safe, generated** binding instead of
hand-rolled glue. The contract is defined once; callers can't drift from it. Concretely, the
Python method `col._backend.concept_mastery(taxonomy=…, search="")` and the
`anki.concepts_pb2` types were generated automatically the moment `concepts.proto` was added —
the score panel consumes them with zero hand-written glue.

**4. Correctness lives next to the scheduler.** NTR is both an input to and an output of FSRS
DSR. Keeping it in the same place as the FSRS scheduler keeps interval math valid and lets
**undo** and the collection-integrity guarantees cover it (spec §7a: undo must work, no
corruption). A Python-side queue layered on top would bypass the undo/transaction machinery. Both new RPCs
are **read-only** (the queue only re-orders an already-selected due/new set; neither mutates
cards or FSRS state), so undo is structurally unaffected. Proof: the Rust test
`concepts::service::test::queue_returns_due_cards_only_and_does_not_mutate` asserts USN and the
undo stack are unchanged after a query, and the Python test
`test_concept_aware_queue_is_read_only` asserts `col.undo_status()` is identical before/after
and the collection remains usable.

## What stays in Python

The minimal score surface (Memory score + coverage display) stays in `aqt`, since it's
presentation, not hot-path engine logic. It calls the Rust mastery query via the generated
binding.

## Scope / additive-ness

The change is kept **additive** where possible (new RPCs + new queue mode rather than
rewriting existing scheduler paths) to limit future upstream-merge cost — see
`files-touched.md`. Summary: 5 existing upstream files modified (each a 1–2 line additive
edit or a new self-contained block), plus new fork-only files; **overall merge difficulty
LOW** — no FSRS scheduler-core or queue-building code was modified.
