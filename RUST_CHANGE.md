# The Rust change: `TopicMastery`

Required by §8. This is the one-page note on why the change belongs in Rust,
what it touches upstream, and how it was verified.

## What it does

A new `StatsService` RPC. Given a search, a topic prefix, and a minimum review
count, it returns per topic:

| field | meaning |
|---|---|
| `card_count` | cards carrying the topic tag |
| `reviewed_card_count` | of those, cards with at least one graded review |
| `review_count` / `passed_review_count` | graded reviews, and those not answered "Again" |
| `mean_retrievability` | mean FSRS retrievability, absent if no card has a memory state |
| `mean_stability_days` | mean FSRS stability, same condition |
| `average_recall` | passes ÷ reviews — **absent** below the review threshold |

Plus `untagged_card_count` and `total_card_count` at the top level.

A "topic" is a tag. Cards count toward every topic they carry, because exam
content outlines overlap and a card can legitimately belong to two areas.

## Why this belongs in Rust, not Python or Swift

**1. It is the only place both apps can agree.**

This is the decisive reason. The desktop app and the iOS companion must show
the *same* mastery number for the same collection. Anki's architecture already
answers this: scheduling and collection logic live in `rslib` behind a protobuf
boundary, and every client — desktop, AnkiDroid, AnkiMobile — calls the same
Rust. Computing mastery anywhere above that boundary means writing it twice and
watching the two implementations drift. §8's requirement that the change "ships
to the phone too" is not an arbitrary hoop; it is a restatement of how Anki is
built.

**2. The data volume belongs on the storage side of the boundary.**

The query reads every card in the search plus its full review history. On the
50,000-card deck §10 benchmarks against, computing this above the FFI boundary
means marshalling every card row and every revlog row into Python or Swift on
each dashboard load, against a p95 budget of under one second and a refresh
budget of 500 ms. Aggregating in Rust returns one small message per topic
instead — tens of rows rather than hundreds of thousands.

**3. Retrievability is already a Rust concept.**

`mean_retrievability` needs FSRS's forgetting curve, evaluated per card from its
stored memory state, elapsed time, and decay. The `fsrs` crate is already linked
into `rslib` and already used by `stats/graphs/retrievability.rs`. Reimplementing
the curve in Swift would create a second definition of the most load-bearing
number in the app.

**4. The give-up rule is a correctness property, not a display choice.**

§5 requires the app to refuse to score when it lacks data. If that rule lives in
the UI, every client re-implements it and any client can forget it. Here,
`average_recall` is an `optional float` that is simply **absent** below the
threshold — not zero, not `-1`. A client cannot render a number the engine
declined to produce. That is the difference between a system that knows when it
does not know and one that merely displays a caveat.

## How it fits Anki's conventions

The change is **purely additive** — 112 inserted lines, no upstream line
modified or deleted:

- The RPC is declared on the existing `StatsService` in `proto/anki/stats.proto`
  rather than in a new service, since it is a stats query.
- The implementation follows `stats/graphs/`: search into the temp table via
  `search_cards_into_table`, then read through `all_searched_cards()` and
  `get_revlog_entries_for_searched_cards_after_stamp()`.
- The new SQL lives in its own `.sql` file loaded with `include_str!`, matching
  `storage/note/get.sql` and friends.
- "Correct" is not redefined. The query reuses
  `RevlogEntry::has_rating_and_affects_scheduling()` and Anki's existing
  convention that button 1 is the only failing answer — the same definition
  behind Anki's true-retention statistics.

## Upstream files touched

| file | change |
|---|---|
| `proto/anki/stats.proto` | +1 rpc, +2 messages |
| `rslib/src/stats/mod.rs` | +1 `mod` declaration |
| `rslib/src/stats/service.rs` | +1 trait method delegating to the impl |
| `rslib/src/storage/note/mod.rs` | +1 helper, `tags_for_searched_cards()` |
| `pylib/tests/test_stats.py` | +1 test |

New files:

- `rslib/src/stats/topic_mastery.rs` — implementation and Rust tests
- `rslib/src/storage/note/tags_for_searched_cards.sql`

## Verification

**4 Rust unit tests** (`rslib/src/stats/topic_mastery.rs`):

1. `card_contributes_to_every_tagged_topic` — multi-tag aggregation
2. `cards_outside_the_prefix_are_reported_as_untagged` — prefix filtering, and
   that non-matching cards are surfaced rather than dropped
3. `recall_is_withheld_until_enough_reviews_exist` — the give-up rule
4. `query_is_read_only_and_leaves_undo_intact` — undo and corruption safety

**1 Python test** (`pylib/tests/test_stats.py::test_topic_mastery`) — exercises
the RPC through the generated Python bindings.

```bash
cargo test -p anki --lib --features rustls topic_mastery
PYTHONPATH=pylib:out/pylib:out/qt ANKI_TEST_MODE=1 \
  out/pyenv/bin/pytest pylib/tests/test_stats.py
```

### Undo and corruption safety

The query is read-only: it opens no write transaction and records no undo entry.
Test 4 proves this rather than asserting it. It performs a genuinely undoable
operation (adding a note), captures `undo_status()`, runs the query three times,
and asserts both the pending undo step and the undo counter are unchanged — a
write of any kind would move one of them. It then confirms undo still works,
that the query reflects the undone state, and that `check_database()` returns
`CheckDatabaseOutput::default()`, i.e. it found no problems at all.

### Performance

Not yet measured. §10's targets (dashboard first load under 1 s, refresh under
500 ms, reported as p50/p95/worst on a 50,000-card deck) will be filled in by
`make bench`. This section stays empty until those numbers exist rather than
carrying an estimate.
