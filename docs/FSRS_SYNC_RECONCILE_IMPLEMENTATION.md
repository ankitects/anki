# FSRS Sync Reconcile Implementation

This note explains the first sync-safety implementation for FSRS.

The goal of this change is narrow:

- detect when a local pending card and an incoming remote card disagree on FSRS-relevant state
- finish merging remote data first
- recompute affected cards from merged local truth
- upload the reconciled card as a normal pending sync change

This does **not** change the `cards.data` format.

## 1. Reconcile In The Right Sync Phase

File:

- `rslib/src/sync/collection/normal.rs`

Code:

```rust
async fn normal_sync_inner(&mut self, mut state: ClientSyncState) -> error::Result<SyncOutput> {
    let mut fsrs_reconcile_candidates = FsrsReconcileCandidates::default();

    self.progress
        .update(false, |p| p.stage = SyncStage::Syncing)?;

    debug!("start");
    self.start_and_process_deletions(&state).await?;
    debug!("unchunked changes");
    self.process_unchunked_changes(&state).await?;
    debug!("begin stream from server");
    self.process_chunks_from_server(&state, &mut fsrs_reconcile_candidates)
        .await?;
    debug!(
        cards = fsrs_reconcile_candidates.cards.len(),
        cards_with_new_reviews = fsrs_reconcile_candidates.cards_with_new_reviews.len(),
        schedule_cards = fsrs_reconcile_candidates.cards_to_reschedule().len(),
        "reconciling fsrs state"
    );
    self.col
        .reconcile_fsrs_state_after_sync(fsrs_reconcile_candidates)?;
    debug!("begin stream to server");
    self.send_chunks_to_server(&state).await?;
```

Why this is necessary:

- The reconcile pass must run **after** remote revlogs and card rows have been merged locally.
- It must also run **before** local chunks are gathered for upload.
- That is the only point where the local DB can see both:
  - just-downloaded remote history
  - still-pending local history
- This avoids trusting either stale pre-sync card row as the final truth.

## 2. Detect Candidates During Remote Merge

File:

- `rslib/src/sync/collection/chunks.rs`

Code:

```rust
#[derive(Debug, Default)]
pub(crate) struct FsrsReconcileCandidates {
    pub(crate) cards: HashSet<CardId>,
    pub(crate) cards_with_new_reviews: HashSet<CardId>,
}

fn merge_revlog(
    &self,
    entries: Vec<RevlogEntry>,
    fsrs_reconcile_candidates: &mut FsrsReconcileCandidates,
) -> Result<()> {
    for entry in entries {
        let added = self.storage.add_revlog_entry(&entry, false)?.is_some();
        if added && entry.has_rating_and_affects_scheduling() {
            fsrs_reconcile_candidates
                .cards_with_new_reviews
                .insert(entry.cid);
        }
    }
    Ok(())
}

fn add_or_update_card_if_newer(
    &self,
    entry: CardEntry,
    pending_usn: Usn,
    fsrs_reconcile_candidates: &mut FsrsReconcileCandidates,
) -> Result<()> {
    let proceed = if let Some(existing_card) = self.storage.get_card(entry.id)? {
        if existing_card.usn.is_pending_sync(pending_usn)
            && card_needs_fsrs_reconcile(&existing_card, &entry)
        {
            fsrs_reconcile_candidates.cards.insert(entry.id);
        }
        !existing_card.usn.is_pending_sync(pending_usn) || existing_card.mtime < entry.mtime
    } else {
        true
    };
    // ...
}
```

Why this is necessary:

- Detection belongs in the incoming card merge path because that is where both versions are visible at once.
- The condition is intentionally limited to **locally pending** cards, because that is the case where we know the local side still has unsynced state that can conflict with the incoming remote row.
- `cards_with_new_reviews` records which cards received a new rating review in this download. `add_revlog_entry` returns `Some` only when the row was inserted, so entries already present locally do not count; resets, manual reschedules and cramming entries do not count either.
- Chunks interleave revlog and card rows, so whether a card is rescheduled is decided only after the whole download, as `cards ∩ cards_with_new_reviews`.
- The code still preserves the existing winner rule for the raw row merge. The reconcile pass is a follow-up correction, not a replacement for the current sync protocol.
- The server applies chunks with a throwaway `FsrsReconcileCandidates` and never reconciles.

## 3. Define What Counts As An FSRS-Relevant Conflict

File:

- `rslib/src/sync/collection/chunks.rs`

Code:

```rust
fn card_needs_fsrs_reconcile(existing: &Card, incoming: &CardEntry) -> bool {
    let incoming_data = CardData::from_str(&incoming.data);
    let incoming_has_fsrs = incoming_data.memory_state().is_some()
        || incoming_data.fsrs_desired_retention.is_some()
        || incoming_data.decay.is_some()
        || existing.memory_state.is_some()
        || existing.desired_retention.is_some()
        || existing.decay.is_some();

    incoming_has_fsrs
        && (existing.memory_state != incoming_data.memory_state()
            || existing.desired_retention != incoming_data.fsrs_desired_retention
            || existing.decay != incoming_data.decay
            || existing.last_review_time != incoming_data.last_review_time
            || card_schedule_differs(existing, incoming))
}
```

Why this is necessary:

- We do not want to reconcile every overlapping card update.
- We only care about cards that are already carrying FSRS-derived state or whose FSRS-derived state clearly diverges.
- `last_review_time` is included because it affects FSRS behavior and can become stale independently of raw memory state values.
- Schedule differences are included because stale FSRS state is not only about `cards.data`; it can also show up as wrong interval or due values. A schedule difference alone (deck move, suspend, flag) only gets the FSRS metadata reconciled; it never reschedules the card (section 8).

## 4. Group Reconciliation By Current Deck Config

File:

- `rslib/src/scheduler/fsrs/memory_state.rs`

Code:

```rust
pub(crate) fn reconcile_fsrs_state_after_sync(
    &mut self,
    candidates: FsrsReconcileCandidates,
) -> Result<()> {
    if candidates.cards.is_empty() || !self.get_config_bool(BoolKey::Fsrs) {
        return Ok(());
    }
    let cards_to_reschedule = candidates.cards_to_reschedule();

    let mut card_ids_by_config: HashMap<DeckConfigId, Vec<CardId>> = HashMap::new();
    let mut deck_desired_retention: HashMap<DeckId, f32> = HashMap::new();
    for &card_id in &candidates.cards {
        let card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
        let deck_id = card.original_or_current_deck_id();
        let deck = self.get_deck(deck_id)?.or_not_found(deck_id)?;
        let config_id = deck.config_id().or_invalid("home deck is filtered")?;
        card_ids_by_config
            .entry(config_id)
            .or_default()
            .push(card_id);
        if let Ok(normal) = deck.normal() {
            if let Some(desired_retention) = normal.desired_retention {
                deck_desired_retention.insert(deck_id, desired_retention);
            }
        }
    }
```

Why this is necessary:

- Reconciliation must use the **current deck config** that the card belongs to after merge, not the config that either side happened to use before sync.
- Grouping by config lets the implementation reuse the existing batch FSRS logic instead of recomputing card-by-card with duplicated setup.
- `original_or_current_deck_id()` matters because filtered-deck cards still need to resolve through their home deck config.
- Deck-specific desired retention is collected separately so the recompute uses the actual effective retention for each card.

## 5. Rebuild From Merged Local Revlogs

File:

- `rslib/src/scheduler/fsrs/memory_state.rs`

Code:

```rust
let timing = self.timing_today()?;
let usn = self.usn()?;
for (config_id, card_ids) in card_ids_by_config {
    let config = self
        .storage
        .get_deck_config(config_id)?
        .or_not_found(config_id)?;
    let revlog =
        self.revlog_for_srs(SearchNode::CardIds(comma_separated_ids(&card_ids)))?;
    let fsrs = FSRS::new(Some(config.fsrs_params()))?;
    let last_revlog_info = get_last_revlog_info(&revlog);
    let synced_revlog_info = synced_revlog_info(&revlog);
    let items = fsrs_items_for_memory_states(
        &fsrs,
        revlog,
        timing.next_day_at,
        config.inner.historical_retention,
        ignore_revlogs_before_ms_from_config(&config)?,
    )?;
```

Why this is necessary:

- This is the core of the approach: do not trust either old card row, just rebuild from the merged local revlog/config state.
- The code uses the same FSRS item builder as the existing memory-state update flow. That keeps behavior aligned with the rest of the codebase.
- `get_last_revlog_info()` is captured once here because later steps need consistent review timing for `last_review_time`.
- `synced_revlog_info()` summarizes, per card, the schedule set by the newest rating review (section 8) and whether the history ends in a reset (section 6). It is built before `revlog` is moved into the FSRS item builder. `get_last_revlog_info()` is unchanged for its other callers.

## 6. Preserve Existing FSRS Semantics For Itemless Cards

File:

- `rslib/src/scheduler/fsrs/memory_state.rs`

Code:

```rust
fn reconcile_itemless_cards_after_sync(
    &mut self,
    cards: Vec<CardId>,
    last_revlog_info: &HashMap<CardId, LastRevlogInfo>,
    mut set_decay_and_desired_retention: impl FnMut(&mut Card),
    usn: Usn,
) -> Result<()> {
    for card_id in cards {
        let mut card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
        set_decay_and_desired_retention(&mut card);
        card.memory_state = None;
        set_reconciled_last_review_time(&mut card, last_revlog_info, synced_revlog_info);
        self.update_reconciled_card_after_sync(&mut card, usn)?;
    }
    Ok(())
}
```

Why this is necessary:

- Cards without a valid FSRS item still need their derived metadata refreshed.
- In particular, `decay`, `desired_retention`, and `last_review_time` can still be stale even when `memory_state` remains `None`.
- This mirrors the existing distinction in the FSRS update code between cards that produce an item and cards that do not.
- `last_review_time` is taken from the merged log, except when the last rating-or-reset entry is a reset (Forget). Forget keeps `last_review_time`, while `get_last_revlog_info()` clears `last_reviewed_at` at a reset, so copying it would undo what Forget left. A set due date (Manual entry with a non-zero ease) is not a reset.

## 7. Write Back Without Using Undo-Oriented Update Paths

File:

- `rslib/src/scheduler/fsrs/memory_state.rs`

Code:

```rust
fn update_reconciled_card_after_sync(&mut self, card: &mut Card, usn: Usn) -> Result<()> {
    card.set_modified(usn);
    self.storage.update_card(card)
}
```

Why this is necessary:

- Sync is already running inside its own transaction and is not an undoable UI operation.
- Reusing `update_card_inner()` here would drag the reconcile step through the normal undo-oriented card update path.
- This helper keeps the intended sync behavior:
  - bump card `mtime`
  - mark it with the current sync USN
  - persist it directly

## 8. Only Reschedule When New Reviews Arrived

File:

- `rslib/src/scheduler/fsrs/memory_state.rs`

Code:

```rust
let schedule_reconcile_cards: HashSet<_> = card_ids
    .iter()
    .copied()
    .filter(|card_id| cards_to_reschedule.contains(card_id))
    .collect();
```

Why this is necessary:

- A flagged card is rescheduled only if this download inserted a new rating review for it. Deck moves, (un)suspend, flags, FSRS-metadata-only differences, and remote resets or manual reschedules never trigger a reschedule.
- `card_schedule_differs()` still flags a card (section 3), so a deck move gets its desired retention, decay and memory state reconciled, but keeps its schedule.

## 9. Take The Schedule From The Newest Review

File:

- `rslib/src/scheduler/fsrs/memory_state.rs`

Code:

```rust
fn reschedule_reconciled_card_after_sync(
    card: &mut Card,
    review_schedule: Option<&ReviewSchedule>,
    timing: SchedTimingToday,
) {
    let Some(schedule) = review_schedule else {
        return;
    };
    if !(card.ctype == CardType::Review && card.queue != CardQueue::Suspended) {
        return;
    }
    let days_since_review = timing.next_day_at.elapsed_days_since(schedule.reviewed_at) as i32;
    card.interval = schedule.interval;
    let due = if card.original_due != 0 {
        &mut card.original_due
    } else {
        &mut card.due
    };
    *due = (timing.days_elapsed as i32) - days_since_review + schedule.interval as i32;
}
```

Why this is necessary:

- The device that did not answer must converge on the schedule the answering device picked. That schedule is recorded in the newest rating review's revlog `interval`: the answer path writes the card's final `scheduled_days` there, after fuzz and load balancing.
- Recomputing the interval with `next_interval()` and fuzz is not enough. Since upstream's fuzz-floor fix (#4826), the answer path chains the floors of Hard, Good and Easy (`good >= hard + 1`), so a recomputed single interval can be one day shorter than what the answering device chose.
- `review_schedule` is `None`, and the local schedule is kept, when there is no rating review, when the newest one left the card in (re)learning (`interval <= 0`), or when a later Manual or Rescheduled entry (set due date, Forget, reschedule on change) owns the schedule.
- The guards keep (re)learning and suspended cards on their local schedule. No rescheduled revlog entry is written: this is a sync-time repair, not a user action.

## 10. The Tests Cover The Reconciliation Matrix

File:

- `rslib/src/sync/collection/tests.rs`

Code:

```rust
#[tokio::test]
async fn fsrs_stale_card_state_is_reconciled_during_sync() -> Result<()> {
    // ...
    col1.answer_good();

    let config = col2.get_deck_config(DeckConfigId(1), false)?.unwrap();
    let request = UpdateMemoryStateRequest {
        params: config.fsrs_params().clone(),
        preset_desired_retention: config.inner.desired_retention,
        historical_retention: config.inner.historical_retention,
        max_interval: config.inner.maximum_review_interval,
        reschedule: false,
        deck_desired_retention: HashMap::new(),
    };
    col2.transact(Op::UpdateDeckConfig, |col| {
        col.update_memory_state(vec![UpdateMemoryStateEntry {
            req: Some(request),
            search: SearchNode::CardIds(card_id.to_string()),
            ignore_before,
        }])?;
        Ok(())
    })?;

    let stale_card = col2.storage.get_card(card_id)?.unwrap();
    let reviewed_card = col1.storage.get_card(card_id)?.unwrap();
    assert!(
        stale_card.memory_state != reviewed_card.memory_state
            || stale_card.last_review_time != reviewed_card.last_review_time
            || stale_card.interval != reviewed_card.interval
            || stale_card.due != reviewed_card.due
    );

    let out = ctx.normal_sync(&mut col1).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);
    let out = ctx.normal_sync(&mut col2).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);

    let reconciled_card = col2.storage.get_card(card_id)?.unwrap();
    let reviewed_card = col1.storage.get_card(card_id)?.unwrap();
    assert_eq!(reconciled_card.memory_state, reviewed_card.memory_state);
    assert_eq!(reconciled_card.last_review_time, reviewed_card.last_review_time);
    assert_eq!(reconciled_card.interval, reviewed_card.interval);
    assert_eq!(reconciled_card.due, reviewed_card.due);
```

Why this is necessary:

- The original stale-state case is still the core failure shape:
  - device A reviews
  - device B recomputes stale FSRS state without that review
  - sync must converge onto the merged truth
- The coverage was then expanded to protect the other non-obvious branches in the implementation:
  - metadata-only conflicts that must not reschedule
  - itemless reconciliation
  - per-preset grouping
  - filtered-deck reconciliation via `original_or_current_deck_id()`
  - deck-level desired retention overrides within one preset batch
  - mixed batches where one card needs schedule reconciliation and another only needs metadata reconciliation
  - same-card reviews on both devices before sync, which must rebuild memory state from the merged review history
- Using the real sync harness is important because the ordering of merge and upload phases is exactly what the implementation relies on.

Code:

```rust
#[tokio::test]
async fn fsrs_state_is_recomputed_from_reviews_on_both_devices() -> Result<()> {
    // ...
    col1.answer_good();
    // ...
    col2.answer_easy();

    let out = ctx.normal_sync(&mut col1).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);
    let out = ctx.normal_sync(&mut col2).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);

    let merged_card = col2.storage.get_card(card_id)?.unwrap();
    let pre_converged_card = col1.storage.get_card(card_id)?.unwrap();
    assert_eq!(col2.storage.get_revlog_entries_for_card(card_id)?.len(), 3);
    assert_eq!(col1.storage.get_revlog_entries_for_card(card_id)?.len(), 2);
    assert!(
        merged_card.memory_state != pre_converged_card.memory_state
            || merged_card.last_review_time != pre_converged_card.last_review_time
            || merged_card.interval != pre_converged_card.interval
            || merged_card.due != pre_converged_card.due
    );

    let out = ctx.normal_sync(&mut col1).await;
    assert_eq!(out.required, SyncActionRequired::NoChanges);
    // both sides now converge onto the merged-history state
}
```

Why this is necessary:

- This is the strongest remaining correctness case for the reconciliation design.
- It proves the implementation is not only repairing stale `card.data`; it is also handling the case where both devices independently add new reviews to the same card before syncing.
- The intermediate assertion intentionally checks that the second syncing device moves to merged-history state before the first device has caught up.

## 11. Add Minimal Debug Logging For Operability

Files:

- `rslib/src/sync/collection/normal.rs`
- `rslib/src/scheduler/fsrs/memory_state.rs`

Code:

```rust
debug!(
    cards = fsrs_reconcile_candidates.cards.len(),
    cards_with_new_reviews = fsrs_reconcile_candidates.cards_with_new_reviews.len(),
    schedule_cards = fsrs_reconcile_candidates.cards_to_reschedule().len(),
    "reconciling fsrs state"
);
```

```rust
debug!(
    config_id = config_id.0,
    cards = card_ids.len(),
    cards_with_items = items.len(),
    itemless_cards = cards_without_items.len(),
    schedule_cards = schedule_reconcile_cards.len(),
    "recomputing fsrs state after sync"
);
```

Why this is necessary:

- The sync path is hard to reason about from user reports alone.
- These logs give just enough signal to answer:
  - was FSRS reconciliation triggered at all?
  - how many cards received new reviews, and how many of those needed schedule repair?
  - how were the cards split by config and itemless/itemful paths?
- The logging stays intentionally aggregate-only. It avoids per-card noise and does not add new behavior.

## Verified Commands

These are the targeted checks that were run after the change:

```bash
cargo test -p anki fsrs_ -- --nocapture
cargo test -p anki sync::collection::tests -- --nocapture
cargo fmt --all
```

## Test Matrix

The current sync reconciliation coverage is:

| Test                                                               | What it proves                                                                                                      |
| ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------- |
| `fsrs_stale_card_state_is_reconciled_during_sync`                  | One device reviews, the other recomputes stale FSRS state, and sync converges to merged truth.                      |
| `fsrs_metadata_conflict_is_reconciled_without_rescheduling`        | FSRS-only metadata conflicts are repaired without changing `due` or `interval`.                                     |
| `fsrs_itemless_card_state_is_cleared_during_sync`                  | Itemless reconciliation clears stale FSRS fields and refreshes retention/decay metadata.                            |
| `fsrs_conflicts_are_reconciled_per_preset`                         | Cards from different presets/configs are reconciled with the correct config grouping.                               |
| `fsrs_reconciliation_uses_original_deck_for_filtered_cards`        | Filtered-deck metadata reconciliation resolves through `original_or_current_deck_id()`.                             |
| `fsrs_reconciliation_respects_deck_overrides_within_one_preset`    | Two decks sharing one preset still get their own deck-level desired retention override in the same batch.           |
| `fsrs_mixed_schedule_and_metadata_conflicts_reconcile_selectively` | In one preset batch, one card can be rescheduled while another only gets metadata repair.                           |
| `fsrs_filtered_card_schedule_conflict_uses_original_deck`          | Filtered-deck schedule reconciliation uses the home deck and updates the home-deck schedule through `original_due`. |
| `fsrs_state_is_recomputed_from_reviews_on_both_devices`            | The same card reviewed on both devices before sync is recomputed from the merged review history.                    |
| `fsrs_reconciles_when_server_copy_is_newer`                        | When the server row wins on mtime, the stale local FSRS edit is still repaired and both devices converge.           |
| `fsrs_deck_move_is_not_rescheduled`                                | A deck move with no new review gets the new deck's desired retention but keeps its schedule.                        |
| `fsrs_deck_move_with_new_review_takes_reviewed_schedule`           | A deck move on one device plus a review on the other: the card takes the reviewed schedule.                         |
| `fsrs_set_due_date_after_arriving_review_keeps_local_schedule`     | A set due date after the arriving review owns the schedule, so the card is not rescheduled.                         |
| `fsrs_relearning_card_keeps_local_schedule`                        | A card in relearning keeps its local schedule; its memory state follows the merged log.                             |
| `fsrs_suspended_card_keeps_local_schedule`                         | A suspended card keeps its local schedule; its memory state follows the merged log.                                 |
| `sm2_sync_conflict_is_unchanged`                                   | With FSRS off, sync conflicts resolve exactly as before.                                                            |
| `fsrs_reconcile_without_load_balancer_converges`                   | Rescheduling converges with the load balancer disabled.                                                             |
| `fsrs_forgotten_card_is_not_restored_by_sync`                      | A card forgotten after a remote review stays new and keeps its `last_review_time`.                                  |
| `fsrs_reconcile_uses_params_changed_on_other_device`               | Reconciliation uses FSRS parameters changed on the other device.                                                    |

Unit tests for candidate detection (`chunks.rs`) and the merged-log summary (`memory_state.rs`):

| Test                                                                      | What it proves                                                                                    |
| ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| `card_not_in_collection_is_not_flagged_for_fsrs_reconcile`                | A card that is new to this collection is not flagged.                                             |
| `locally_unmodified_card_is_not_flagged_for_fsrs_reconcile`               | A card without pending local changes is not flagged.                                              |
| `card_without_fsrs_data_on_either_side_is_not_flagged_for_fsrs_reconcile` | Cards with no FSRS data on either side are not flagged.                                           |
| `identical_incoming_card_is_not_flagged_for_fsrs_reconcile`               | An identical incoming row is not flagged.                                                         |
| `memory_state_difference_is_flagged_for_fsrs_reconcile`                   | A memory state difference flags the card.                                                         |
| `last_review_time_difference_is_flagged_for_fsrs_reconcile`               | A `last_review_time` difference flags the card.                                                   |
| `due_and_interval_difference_is_flagged_for_fsrs_reconcile`               | A schedule difference flags the card.                                                             |
| `new_rating_review_is_recorded_as_new_review`                             | `merge_revlog` records a newly inserted rating review.                                            |
| `review_already_present_locally_is_not_recorded_as_new_review`            | An entry that already exists locally is not a new review.                                         |
| `new_reset_entry_is_not_recorded_as_new_review`                           | A downloaded reset (Manual, ease 0) is not a new review.                                          |
| `new_cramming_entry_is_not_recorded_as_new_review`                        | A downloaded cramming entry (Filtered, ease 0) is not a new review.                               |
| `only_flagged_cards_with_new_reviews_are_rescheduled`                     | The reschedule set is the flagged cards that received new reviews.                                |
| `newest_rating_review_sets_synced_schedule`                               | The newest rating review provides the schedule taken by reconciliation.                           |
| `learning_answer_leaves_no_synced_schedule`                               | A newest answer that left the card in (re)learning provides no schedule.                          |
| `set_due_date_after_review_leaves_no_synced_schedule`                     | A later set due date removes the review schedule and is not a reset.                              |
| `history_ending_in_reset_is_detected`                                     | A history ending in a reset is detected; a later review clears it, a later set due date does not. |

Related base FSRS behavior already covered outside sync:

| Test                      | What it proves                                                                  |
| ------------------------- | ------------------------------------------------------------------------------- |
| `no_req_clears_fsrs_data` | The memory-state update path clears FSRS data when no FSRS request is provided. |
