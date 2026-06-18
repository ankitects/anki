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
    let mut cards_needing_fsrs_reconcile = HashMap::new();

    self.progress
        .update(false, |p| p.stage = SyncStage::Syncing)?;

    debug!("start");
    self.start_and_process_deletions(&state).await?;
    debug!("unchunked changes");
    self.process_unchunked_changes(&state).await?;
    debug!("begin stream from server");
    self.process_chunks_from_server(&state, &mut cards_needing_fsrs_reconcile)
        .await?;
    debug!(
        cards = cards_needing_fsrs_reconcile.len(),
        "reconciling fsrs state"
    );
    self.col
        .reconcile_fsrs_state_after_sync(cards_needing_fsrs_reconcile)?;
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

## 2. Detect Candidates During Remote Card Merge

File:

- `rslib/src/sync/collection/chunks.rs`

Code:

```rust
fn merge_cards(
    &self,
    entries: Vec<CardEntry>,
    pending_usn: Usn,
    cards_needing_fsrs_reconcile: &mut HashMap<CardId, bool>,
) -> Result<()> {
    for entry in entries {
        self.add_or_update_card_if_newer(entry, pending_usn, cards_needing_fsrs_reconcile)?;
    }
    Ok(())
}

fn add_or_update_card_if_newer(
    &self,
    entry: CardEntry,
    pending_usn: Usn,
    cards_needing_fsrs_reconcile: &mut HashMap<CardId, bool>,
) -> Result<()> {
    let proceed = if let Some(existing_card) = self.storage.get_card(entry.id)? {
        if existing_card.usn.is_pending_sync(pending_usn)
            && card_needs_fsrs_reconcile(&existing_card, &entry)
        {
            cards_needing_fsrs_reconcile
                .entry(entry.id)
                .and_modify(|should_reschedule| {
                    *should_reschedule |= card_schedule_differs(&existing_card, &entry)
                })
                .or_insert_with(|| card_schedule_differs(&existing_card, &entry));
        }
        !existing_card.usn.is_pending_sync(pending_usn) || existing_card.mtime < entry.mtime
    } else {
        true
    };
    if proceed {
        let card = entry.into();
        self.storage.add_or_update_card(&card)?;
    }
    Ok(())
}
```

Why this is necessary:

- Detection belongs in the incoming card merge path because that is where both versions are visible at once.
- The condition is intentionally limited to **locally pending** cards, because that is the case where we know the local side still has unsynced state that can conflict with the incoming remote row.
- The `bool` in the map is used to remember whether schedule fields also diverged, so later reconciliation knows whether interval/due needs to be recomputed as well.
- The code still preserves the existing winner rule for the raw row merge. The reconcile pass is a follow-up correction, not a replacement for the current sync protocol.

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
- Schedule differences are included because stale FSRS state is not only about `cards.data`; it can also show up as wrong interval or due values.

## 4. Group Reconciliation By Current Deck Config

File:

- `rslib/src/scheduler/fsrs/memory_state.rs`

Code:

```rust
pub(crate) fn reconcile_fsrs_state_after_sync(
    &mut self,
    cards_needing_fsrs_reconcile: HashMap<CardId, bool>,
) -> Result<()> {
    if cards_needing_fsrs_reconcile.is_empty() || !self.get_config_bool(BoolKey::Fsrs) {
        return Ok(());
    }

    let mut card_ids_by_config: HashMap<DeckConfigId, Vec<CardId>> = HashMap::new();
    let mut deck_desired_retention: HashMap<DeckId, f32> = HashMap::new();
    for &card_id in cards_needing_fsrs_reconcile.keys() {
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
- `get_last_revlog_info()` is captured once here because later steps need consistent review timing for both `last_review_time` and optional rescheduling.

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
        card.last_review_time = last_revlog_info
            .get(&card_id)
            .and_then(|info| info.last_reviewed_at);
        self.update_reconciled_card_after_sync(&mut card, usn)?;
    }
    Ok(())
}
```

Why this is necessary:

- Cards without a valid FSRS item still need their derived metadata refreshed.
- In particular, `decay`, `desired_retention`, and `last_review_time` can still be stale even when `memory_state` remains `None`.
- This mirrors the existing distinction in the FSRS update code between cards that produce an item and cards that do not.

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

## 8. Only Reschedule When The Conflict Included Schedule Fields

File:

- `rslib/src/scheduler/fsrs/memory_state.rs`

Code:

```rust
let schedule_reconcile_cards: HashSet<_> = card_ids
    .iter()
    .copied()
    .filter(|card_id| {
        cards_needing_fsrs_reconcile
            .get(card_id)
            .copied()
            .unwrap_or_default()
    })
    .collect();
```

```rust
if schedule_reconcile_cards.contains(&card_id) {
    self.reschedule_reconciled_card_after_sync(
        &mut card,
        fsrs,
        last_revlog_info.get(&card_id),
        timing,
        max_interval,
    )?;
}
```

Why this is necessary:

- Not every FSRS conflict means the card’s due/interval needs to change.
- Separating “FSRS metadata differs” from “schedule-relevant fields differ” avoids gratuitous interval churn.
- This keeps the first implementation narrower and reduces the chance of changing scheduling when the only stale fields were in `cards.data`.

## 9. Reuse Existing Rescheduling Logic Shape

File:

- `rslib/src/scheduler/fsrs/memory_state.rs`

Code:

```rust
fn reschedule_reconciled_card_after_sync(
    &mut self,
    card: &mut Card,
    fsrs: &FSRS,
    last_revlog_info: Option<&LastRevlogInfo>,
    timing: SchedTimingToday,
    max_interval: u32,
) -> Result<()> {
    let Some(last_info) = last_revlog_info else {
        return Ok(());
    };
    let Some(last_review) = last_info.last_reviewed_at else {
        return Ok(());
    };
    if !(card.ctype == CardType::Review && card.queue != CardQueue::Suspended) {
        return Ok(());
    };
    // ...
    let interval = fsrs.next_interval(
        Some(
            card.memory_state
                .expect("memory state set before rescheduling")
                .stability,
        ),
        card.desired_retention
            .expect("desired retention set before rescheduling"),
        0,
    );
    // ...
}
```

Why this is necessary:

- The reschedule behavior needs to stay aligned with the existing FSRS update path as much as possible.
- The guard conditions prevent the reconcile pass from inventing schedule changes for cards that should not be rescheduled at all.
- The logic uses merged review timing and the recomputed memory state, so the resulting interval is based on the final post-sync view rather than either stale pre-sync row.

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
    cards = cards_needing_fsrs_reconcile.len(),
    schedule_cards = cards_needing_fsrs_reconcile
        .values()
        .filter(|&&needs_schedule_reconcile| needs_schedule_reconcile)
        .count(),
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
  - how many cards needed full schedule repair?
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

Related base FSRS behavior already covered outside sync:

| Test                      | What it proves                                                                  |
| ------------------------- | ------------------------------------------------------------------------------- |
| `no_req_clears_fsrs_data` | The memory-state update path clears FSRS data when no FSRS request is provided. |
