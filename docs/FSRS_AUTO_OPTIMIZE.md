# FSRS Auto Optimize

## Constraints

- Auto-optimize is not only "save new FSRS params".
- When deck config changes are applied, Anki may recompute memory state for affected cards.
- That recomputation rewrites `cards.data` and bumps card `mtime`.
- `cards.data` stores FSRS memory state plus desired retention and decay.
- Deck config params and card rows do not sync as one unit.
- Deck configs merge by config `mtime`.
- Cards merge later as whole rows by card `mtime`.
- This means two devices can produce different FSRS state for the same card even if they eventually agree on the preset params.

## Main Risk

The dangerous case is not only:

- optimize on device A
- optimize on device B

It is also:

- review on device A
- auto-optimize on device B before sync
- sync both devices later

In that case, device B can compute memory state from incomplete review history.
During sync, revlogs are merged before cards, but card FSRS state is not recomputed after revlog merge.
If device B's card row has the newer `mtime`, it can win even though its FSRS state was computed without the latest review.

## Related Code

### Optimization / FSRS update path

- Deck options UI:
  - `ts/routes/deck-options/FsrsOptions.svelte`
- Deck config update and recompute trigger:
  - `rslib/src/deckconfig/update.rs`
- Memory-state recomputation:
  - `rslib/src/scheduler/fsrs/memory_state.rs`
- FSRS param computation:
  - `rslib/src/scheduler/fsrs/params.rs`
- Card FSRS serialization in `cards.data`:
  - `rslib/src/storage/card/data.rs`

### Sync path

- Normal sync flow:
  - `rslib/src/sync/collection/normal.rs`
- Unchunked change merge for deck configs:
  - `rslib/src/sync/collection/changes.rs`
- Chunk merge for revlogs/cards/notes:
  - `rslib/src/sync/collection/chunks.rs`
- Sync sanity check:
  - `rslib/src/sync/collection/sanity.rs`

## Important Current Behavior

### Deck options UI

`Optimize Current Preset` computes new params into the deck options form state.
It does not by itself rewrite cards immediately.
The card rewrite happens when the deck config changes are saved and `update_memory_state()` runs.

`Optimize All Presets` goes through the bulk update path and then the same recompute machinery.

### Deck config apply path

When deck configs are updated, Anki checks whether FSRS-related inputs changed:

- params
- desired retention
- easy days when rescheduling is enabled
- FSRS enabled/disabled

If yes, it queues decks for memory-state recomputation.

### Memory-state update path

`update_memory_state()`:

- gathers revlog history for the searched cards
- computes new memory state
- stores `memory_state`, `desired_retention`, and `decay` on cards
- optionally reschedules cards
- saves each card through `update_card_inner()`

That save bumps card `mtime` and marks the card as sync-relevant.

### Sync merge path

The sync logic currently:

- merges revlogs
- merges cards
- keeps the newer card row based on `mtime` / pending sync state

Card merge is a whole-row overwrite, not a field-wise FSRS merge.
So the winner card row brings its own `data`, interval, due, and other card state with it.

## How Conflicts Happen

### Scenario 1: review on one device, optimize on another

1. Device A reviews a card but has not synced yet.
2. Device B auto-optimizes and recomputes that card's FSRS state without seeing A's review.
3. Device B saves newer card data and newer `mtime`.
4. Sync merges revlogs first, so the review eventually exists everywhere.
5. Sync then merges cards by `mtime`, and B's stale FSRS state can win.

Result:

- final revlog history may be complete
- final card FSRS state may still be stale

### Scenario 2: both devices optimize

1. Device A optimizes with one visible review set.
2. Device B optimizes with another visible review set.
3. Both rewrite overlapping cards.
4. Sync picks card winners by `mtime`.

Result:

- preset params may converge
- card-level FSRS state may still depend on whichever device wrote last

### Scenario 3: config and cards disagree after sync

Deck configs sync in the unchunked phase.
Cards sync in the chunked phase.
So it is possible to end with:

- params from one side
- card FSRS state computed using older params or older review history from the other side

## Revlog Table Approach

The revlog idea is attractive because it would give optimization-related events timestamps.
But by itself it does not look sufficient.

Reasons:

- current FSRS history building filters to entries that have a rating and affect scheduling
- manual/rescheduled-style entries are treated specially
- entries with no rating do not currently participate as normal FSRS review history

So adding a virtual revlog entry alone does not automatically fix stale memory-state resolution.

At minimum, a revlog-based approach would still need post-sync recomputation for affected cards.

## Current Read

The safer direction looks closer to:

- detect cards whose FSRS-relevant data may conflict during sync
- finish merging revlogs and other sync data
- recompute FSRS state and interval for those specific cards after sync data is complete

That matches the concern that the winning card row may not include all reviews even when the final merged revlog does.

## When To Reconcile

The important nuance is that reconciliation must consider:

- data that was just synced down from the other side
- data that already exists locally but is still pending upload

So the reconcile step should not run while applying each incoming card row.
It should run after remote data has been merged into the local collection, but before local pending card chunks are gathered and uploaded.

In the current normal sync flow, the intended place is conceptually:

1. deletions
2. unchunked changes, including deck configs
3. remote chunks merged into local DB
4. FSRS reconcile pass for affected cards
5. gather local pending chunks and upload them

This matters because, at that point:

- remote revlogs have already been inserted locally
- local pending revlogs are also already present locally
- merged deck config state is available locally
- recomputed card state can become the version that gets uploaded in the same sync

If reconciliation happens too early:

- later incoming card merges can overwrite it
- it may compute from incomplete merged state
- it may miss deck-config changes that arrived earlier in the sync

## Detect vs Recompute

It likely makes sense to separate:

- detection of cards that need FSRS reconciliation
- actual recomputation of those cards

Detection can happen during merge when a remote card overlaps a local pending card and FSRS-relevant data differs.
But recomputation should wait until the remote merge phase is complete.

So the sync code likely wants a temporary set such as:

- `cards_needing_fsrs_reconcile`

That set can be populated during incoming merge, then processed once after remote chunks are done.

## Why Not Recompute During Card Merge

`add_or_update_card_if_newer()` is a reasonable place to detect candidates.
It is not a good place to recompute them.

Reasons:

- it runs card-by-card
- revlogs and other related rows may still be mid-merge
- later incoming rows may still change the final local state

So:

- detect during card merge
- recompute after remote merge is complete

## Test Strategy

The best test shape is an integration test using the existing two-device sync harness in:

- `rslib/src/sync/collection/tests.rs`

Useful scenarios:

### Scenario A

- create shared starting state
- full upload from `col1`
- full download into `col2`
- review card on `col1`
- optimize / recompute on `col2`
- sync `col1`
- sync `col2`
- assert final card state is based on full merged review history

### Scenario B

- shared starting state
- optimize on both sides with different visible review history
- sync both sides
- assert no stale card FSRS state remains

### Scenario C

- change params on one side
- keep overlapping card updates on the other side
- sync both sides
- assert final params and per-card FSRS state are consistent

## First Test To Build Later

The first regression test should model:

- review on device A
- optimize on device B before sync
- sync both devices

That is the clearest case where:

- all revlogs can be present after sync
- but the kept card state can still be computed from incomplete history
