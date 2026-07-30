# Brainlift Mastery Query Rationale

The Friday Rust primitive is a read-only mastery query because the evidence
contract must be identical on desktop and phone clients. Anki already routes
protobuf services through `rslib` to generated Python, TypeScript, and mobile
interfaces. Keeping the formulas, availability thresholds, and reason codes in
Rust prevents each client from producing a different answer or silently
weakening the give-up rule.

## Evidence Contract

The request supplies stable topic name/tag pairs from the MCAT outline. The
response always returns every requested topic, including uncovered topics. A
topic becomes covered after two qualifying reviews.

Qualifying reviews have a scheduling rating from 1 through 4. Manual,
rescheduled, and non-rescheduling preview entries are excluded. A button above
1 is a successful review.

- **Memory** uses ordinary rated reviews and becomes available after 10.
- **Performance** uses a timestamped
  `brainlift::evidence::performance::<unix-seconds>` marker and becomes
  available after 10. Only reviews at or after that cutoff count as held-out
  Performance; older history remains Memory evidence.
- **Readiness** requires both scores plus joint coverage of at least 80% of
  requested topics.

Every score includes an explicit available/abstained state, estimate, 95%
Wilson interval, coverage, confidence, latest qualifying review time,
machine-readable reasons, and supporting review counts. The response repeats
all thresholds so clients render the Rust decision instead of recalculating it.

Per-topic average recall is successful ordinary reviews divided by qualifying
ordinary reviews. Per-topic mastery is the conservative lower endpoint of that
95% Wilson interval. This distinguishes an observed average from an
evidence-aware mastery estimate.

The Friday MCAT Readiness formula is:

```text
472 + 56 * mean(memory recall, held-out performance accuracy)
```

The interval applies the same mapping to the mean of the two Wilson endpoints.
This deliberately simple mapping is visible in the response and can be
replaced by Sunday calibration evidence. No Readiness number is returned until
both independent evidence sources and the joint coverage rule pass.

## Read-Only and Performance Design

The query streams qualifying revlog rows through one join across revlog,
cards, and notes, then filters requested topic tags while aggregating. It does
not materialize cards or review history in Rust. A benchmarked search-table
prefilter took about 1.2 seconds on the 50,000-card fixture; the single-pass
scan took about 39 milliseconds. Memory use is bounded by the requested topic
count rather than collection size.

The implementation does not call `card_stats()`, modify cards, add revlog
entries, update collection timestamps, or create undo operations. Tests compare
collection and schema timestamps, undo/redo state, card and revlog counts, and
SQLite integrity before and after the query.

The benchmark builds a 50,000-card, 50,000-review, 100-topic fixture and prints
median, p95, and worst query latency before Criterion's normal measurement.
Half of the notes are held-out Performance evidence, so the measured query
exercises both evidence classifications:

```sh
PROTOC=/opt/homebrew/bin/protoc cargo bench -p anki --features bench \
  brainlift/score_snapshot_50k_cards
```

The Friday dashboard budget is 200 ms p95, 500 ms worst case, and less than
64 MiB additional process memory for the query. The streaming aggregation is
designed to stay below the memory ceiling; the benchmark output records the
machine-specific latency evidence.

On the implementation machine, the 50,000-card fixture with one rated review
per card reported 37 ms median, 39 ms p95, and 39 ms worst case.

## Upstream Files Touched

- `proto/anki/stats.proto`
- `rslib/src/stats/mod.rs`
- `rslib/src/stats/service.rs`
- `rslib/src/stats/brainlift.rs`
- `rslib/src/storage/mod.rs`
- `rslib/src/storage/revlog/mod.rs`
- `rslib/benches/benchmark.rs`
- `pylib/anki/collection.py`
- `pylib/tests/test_stats.py`

Generated Rust, Python, TypeScript, and mobile bridge files are intentionally
not edited. Anki's existing protobuf generation pipeline produces them from the
new stats RPC.
