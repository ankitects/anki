// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::collections::HashSet;

use fsrs::MemoryState;
use fsrs::FSRS;

use super::memory_state::for_each_replayed_state;
use super::memory_state::fsrs_items_for_memory_states;
use super::memory_state::get_decay_from_params;
use super::memory_state::ComputeMemoryProgress;
use super::params::ignore_revlogs_before_ms_from_config;
use super::ReplaySync;
use crate::card::FsrsMemoryState;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::search::SearchNode;
use crate::storage::comma_separated_ids;

/// Approximate missing internal traces while preserving the supplied S90 and
/// difficulty. This is not recovery of the original dual-trace state: prefer
/// revlog replay when history is available. Used for legacy API
/// writes/fallbacks.
pub(crate) fn fsrs_memory_state_for_s90_and_difficulty(
    fsrs: &FSRS,
    s90: f32,
    difficulty: f32,
) -> Option<FsrsMemoryState> {
    if !s90.is_finite() || s90 <= 0.0 || !difficulty.is_finite() {
        return None;
    }
    if fsrs.version() != fsrs::ModelVersion::Fsrs7 {
        return Some(FsrsMemoryState {
            stability: s90,
            stability_internal: s90,
            stability_fast: None,
            difficulty: difficulty.clamp(1.0, 10.0),
        });
    }
    let shape = fsrs.memory_state_from_sm2(2.5, s90, 0.9).ok()?;
    let shape = MemoryState {
        difficulty: difficulty.clamp(1.0, 10.0),
        ..shape
    };
    let state = fsrs.scale_memory_state_to_interval(shape, s90, 0.9).ok()?;
    Some(FsrsMemoryState {
        stability: s90,
        stability_internal: state.stability,
        stability_fast: Some(state.stability_fast),
        difficulty: state.difficulty,
    })
}

/// Stores the preset's desired retention and the decay of `params` on the
/// card, for legacy consumers of these scalar fields.
pub(super) fn set_preset_retention_and_decay(
    card: &mut Card,
    decks: &HashMap<DeckId, Deck>,
    config: &DeckConfig,
    params: &[f32],
) {
    card.desired_retention = Some(
        decks
            .get(&card.original_or_current_deck_id())
            .map_or(config.inner.desired_retention, |deck| {
                deck.effective_desired_retention(config)
            }),
    );
    card.decay = Some(get_decay_from_params(params));
}

/// An invalid cutoff is editable in Deck Options. Defer this preset without
/// discarding its cutoff or preventing other presets from being repaired.
pub(super) fn migration_history_cutoff(config: &DeckConfig) -> Option<TimestampMillis> {
    ignore_revlogs_before_ms_from_config(config).inspect_err(|err| {
        tracing::warn!(preset = %config.name, %err, "FSRS replay deferred; correct the ignore-before date in Deck Options");
    }).ok()
}

impl Collection {
    /// Replay the eligible cards with usable history under the destination
    /// preset. Callers decide how to handle cards without history.
    pub(super) fn replay_fsrs_states(
        &mut self,
        fsrs: &FSRS,
        search: SearchNode,
        eligible: impl Iterator<Item = CardId>,
        config: &DeckConfig,
        cutoff: TimestampMillis,
        progress: &mut ThrottlingProgressHandler<ComputeMemoryProgress>,
    ) -> Result<HashMap<CardId, FsrsMemoryState>> {
        let next_day_at = self.timing_today()?.next_day_at;
        let eligible: HashSet<_> = eligible.collect();
        let revlog = self.revlog_for_srs(search)?;
        let items = fsrs_items_for_memory_states(
            fsrs,
            revlog,
            next_day_at,
            config.inner.historical_retention,
            cutoff,
        )?
        .into_iter()
        .filter(|(id, _)| eligible.contains(id))
        .filter_map(|(id, item)| item.map(|item| (id, item)))
        .collect();
        let mut states = HashMap::new();
        for_each_replayed_state(fsrs, items, |id, state| {
            states.insert(id, state);
            progress.update(true, |_| ())
        })?;
        Ok(states)
    }

    pub(crate) fn incomplete_fsrs7_card_ids(&self) -> Result<Vec<CardId>> {
        let decks = self.storage.get_decks_map()?;
        let configs = self.storage.get_deck_config_map()?;
        let eligible: HashSet<_> = configs
            .values()
            .filter(|config| config.uses_fsrs7() && migration_history_cutoff(config).is_some())
            .map(|config| config.id)
            .collect();
        let home_decks: Vec<_> = decks
            .values()
            .filter(|deck| deck.config_id().is_some_and(|id| eligible.contains(&id)))
            .map(|deck| deck.id)
            .collect();
        self.storage
            .cards_with_incomplete_fsrs_state(Some(&home_decks))
    }

    pub(crate) fn repair_incomplete_fsrs7_states(&mut self, sync: ReplaySync) -> Result<usize> {
        if self.server {
            return Ok(0);
        }
        let ids = self.incomplete_fsrs7_card_ids()?;
        if ids.is_empty() {
            return Ok(0);
        }
        self.transact_no_undo_after_sync(|col| col.rebuild_fsrs_states_inner(&ids, sync))
    }

    /// Caller supplies a transaction. Rebuild under the destination preset,
    /// without changing due dates, queues or intervals.
    pub(crate) fn rebuild_fsrs_states_inner(
        &mut self,
        ids: &[CardId],
        sync: ReplaySync,
    ) -> Result<usize> {
        if ids.is_empty() {
            return Ok(0);
        }
        let decks = self.storage.get_decks_map()?;
        let configs = self.storage.get_deck_config_map()?;
        let mut groups: HashMap<DeckConfigId, Vec<Card>> = HashMap::new();
        for card in self.all_cards_for_search(SearchNode::CardIds(comma_separated_ids(ids)))? {
            if card.memory_state.is_none() {
                continue;
            }
            let deck = decks
                .get(&card.original_or_current_deck_id())
                .or_not_found(card.deck_id)?;
            let id = deck.config_id().or_invalid("card has no home preset")?;
            configs.get(&id).or_not_found(id)?;
            groups.entry(id).or_default().push(card);
        }
        let usn = self.usn()?;
        let mut progress = self.new_progress_handler::<ComputeMemoryProgress>();
        progress.update(false, |p| p.total_cards = ids.len() as u32)?;
        let mut repaired = 0;
        for (id, cards) in groups {
            let config = &configs[&id];
            let Some(cutoff) = migration_history_cutoff(config) else {
                continue;
            };
            let fsrs = FSRS::new(config.fsrs_params())?;
            let ids: Vec<_> = cards.iter().map(|card| card.id).collect();
            let mut states = self.replay_fsrs_states(
                &fsrs,
                SearchNode::CardIds(comma_separated_ids(&ids)),
                ids.into_iter(),
                config,
                cutoff,
                &mut progress,
            )?;
            for mut card in cards {
                let original = card.clone();
                let stored = card.memory_state.unwrap();
                card.memory_state = Some(if let Some(state) = states.remove(&card.id) {
                    card.last_review_time = self.storage.time_of_last_review(card.id)?;
                    state
                } else {
                    fsrs_memory_state_for_s90_and_difficulty(
                        &fsrs,
                        stored.stability,
                        stored.difficulty,
                    )
                    .or_invalid("invalid incomplete FSRS memory state")?
                });
                set_preset_retention_and_decay(&mut card, &decks, config, config.fsrs_params());
                card.usn = sync.card_usn(original.usn, usn);
                self.update_card_undoable(&mut card, original)?;
                repaired += 1;
                progress.update(true, |p| p.current_cards += 1)?;
            }
        }
        Ok(repaired)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::tests::NoteAdder;

    #[test]
    fn incomplete_states_prefer_fractional_history_over_scalar_fallback() -> Result<()> {
        use crate::revlog::RevlogEntry;
        use crate::revlog::RevlogReviewKind;
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id])?[0];
        col.storage.db.execute(
            "update cards set data=? where id=?",
            (r#"{"s":20,"d":6}"#, cid),
        )?;
        for (i, seconds) in [1_700_000_000, 1_700_000_600].into_iter().enumerate() {
            col.storage.add_revlog_entry(
                &RevlogEntry {
                    id: RevlogId(seconds * 1000),
                    cid,
                    button_chosen: 3,
                    interval: if i == 0 { -600 } else { 10 },
                    review_kind: RevlogReviewKind::Learning,
                    ..Default::default()
                },
                false,
            )?;
        }
        assert_eq!(col.repair_incomplete_fsrs7_states(ReplaySync::Preserve)?, 1);
        let repaired = col.storage.get_card(cid)?.unwrap();
        let model = FSRS::new(&fsrs::DEFAULT_PARAMETERS)?;
        let expected = model.memory_state(
            fsrs::FSRSItem {
                reviews: vec![
                    fsrs::FSRSReview {
                        rating: 3,
                        delta_t: 0.0,
                    },
                    fsrs::FSRSReview {
                        rating: 3,
                        delta_t: 600.0 / 86_400.0,
                    },
                ],
            },
            None,
        )?;
        let actual: MemoryState = repaired.memory_state.unwrap().into();
        assert!((actual.stability - expected.stability).abs() < 0.001);
        assert!((actual.stability_fast - expected.stability_fast).abs() < 0.001);
        assert!((actual.difficulty - expected.difficulty).abs() < 0.001);
        assert_eq!(
            repaired.last_review_time,
            Some(TimestampSecs(1_700_000_600))
        );
        Ok(())
    }

    #[test]
    fn incomplete_states_preserve_s90_and_scheduling_without_history() -> Result<()> {
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id])?[0];
        col.storage.db.execute(
            "update cards set type=2, queue=2, due=123, ivl=30, data=? where id=?",
            (r#"{ "s": 20, "d": 6, "s_int": null, "s_fast": 2 }"#, cid),
        )?;
        assert_eq!(col.repair_incomplete_fsrs7_states(ReplaySync::Preserve)?, 1);
        let card = col.storage.get_card(cid)?.unwrap();
        let state = card.memory_state.unwrap();
        assert_eq!((card.due, card.interval), (123, 30));
        assert_eq!((state.stability, state.difficulty), (20.0, 6.0));
        let fsrs = FSRS::new(&fsrs::DEFAULT_PARAMETERS)?;
        assert!((fsrs.interval_at_retrievability(state.into(), 0.9) - 20.0).abs() < 0.01);
        assert!(col.incomplete_fsrs7_card_ids()?.is_empty());
        assert_eq!(col.repair_incomplete_fsrs7_states(ReplaySync::Preserve)?, 0);
        assert_eq!(col.storage.get_card(cid)?.unwrap(), card);
        Ok(())
    }

    #[test]
    fn invalid_legacy_state_is_rejected() -> Result<()> {
        let fsrs = FSRS::new(&fsrs::DEFAULT_PARAMETERS)?;
        for (s, d) in [(0.0, 5.0), (f32::NAN, 5.0), (20.0, f32::INFINITY)] {
            assert!(fsrs_memory_state_for_s90_and_difficulty(&fsrs, s, d).is_none());
        }
        Ok(())
    }
}
