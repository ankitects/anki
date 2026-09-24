// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use fsrs::MemoryState;
use fsrs::FSRS;

use super::memory_state::fsrs_items_for_memory_states;
use super::memory_state::fsrs_memory_state_for_fsrs;
use super::memory_state::get_decay_from_params;
use super::memory_state::ComputeMemoryProgress;
use super::params::ignore_revlogs_before_ms_from_config;
use crate::card::FsrsMemoryState;
use crate::prelude::*;
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
    let state = scale_state_to_interval(fsrs, shape, s90, 0.9);
    Some(FsrsMemoryState {
        stability: s90,
        stability_internal: state.stability,
        stability_fast: Some(state.stability_fast),
        difficulty: state.difficulty,
    })
}

const STABILITY_MIN: f32 = 0.0001;
const STABILITY_MAX: f32 = 36_500.0;

/// Scale both traces of `shape` until its forgetting curve reaches
/// `retention` at `interval`, while preserving the trace ratio and difficulty.
pub(crate) fn scale_state_to_interval(
    fsrs: &FSRS,
    shape: MemoryState,
    interval: f32,
    retention: f32,
) -> MemoryState {
    const TOLERANCE: f64 = 1e-5;
    const MAX_STEPS: usize = 64;

    let ratio = shape.stability_fast / shape.stability;
    if !(ratio.is_finite()
        && ratio > 0.0
        && interval.is_finite()
        && interval > 0.0
        && retention > 0.0
        && retention < 1.0)
    {
        return shape;
    }

    let target = (interval.clamp(STABILITY_MIN, STABILITY_MAX) as f64).ln();
    let state_at = |log_stability: f64| {
        let stability = (log_stability.exp() as f32).clamp(STABILITY_MIN, STABILITY_MAX);
        MemoryState {
            stability,
            stability_fast: (stability * ratio).clamp(STABILITY_MIN, STABILITY_MAX),
            difficulty: shape.difficulty,
        }
    };
    let error_at = |log_stability: f64| {
        let reached = fsrs.interval_at_retrievability(state_at(log_stability), retention);
        (reached.max(f32::MIN_POSITIVE) as f64).ln() - target
    };

    let mut low = (STABILITY_MIN as f64).ln();
    let mut high = (STABILITY_MAX as f64).ln();
    let mut x = (shape.stability.clamp(STABILITY_MIN, STABILITY_MAX) as f64).ln();
    let mut error = error_at(x);
    let mut previous: Option<(f64, f64)> = None;
    for _ in 0..MAX_STEPS {
        if !error.is_finite() || error.abs() <= TOLERANCE {
            break;
        }
        if error < 0.0 {
            low = x;
        } else {
            high = x;
        }
        let mut next = match previous {
            Some((previous_x, previous_error)) if error != previous_error => {
                x - error * (x - previous_x) / (error - previous_error)
            }
            _ => x - error,
        };
        if !(next > low && next < high) {
            next = 0.5 * (low + high);
        }
        previous = Some((x, error));
        x = next;
        error = error_at(x);
    }

    if error.is_finite() {
        state_at(x)
    } else {
        shape
    }
}

impl Collection {
    pub(crate) fn fsrs_config_for_card(&mut self, card: &Card) -> Result<DeckConfig> {
        let deck = self
            .get_deck(card.original_or_current_deck_id())?
            .or_not_found(card.deck_id)?;
        let id = deck.config_id().or_invalid("card has no home preset")?;
        self.get_deck_config(id, true)?.or_not_found(id)
    }

    pub(crate) fn incomplete_fsrs7_card_ids(&self) -> Result<Vec<CardId>> {
        let decks = self.storage.get_decks_map()?;
        let configs = self.storage.get_deck_config_map()?;
        let mut ids = Vec::new();
        for (id, home_deck) in self.storage.cards_with_incomplete_fsrs_state()? {
            if decks
                .get(&home_deck)
                .and_then(|deck| deck.config_id())
                .and_then(|id| configs.get(&id))
                .is_some_and(|config| config.fsrs_params().len() == 34)
            {
                ids.push(id);
            }
        }
        Ok(ids)
    }

    pub(crate) fn repair_incomplete_fsrs7_states(&mut self) -> Result<usize> {
        if self.server {
            return Ok(0);
        }
        let ids = self.incomplete_fsrs7_card_ids()?;
        if ids.is_empty() {
            return Ok(0);
        }
        self.transact_no_undo_after_sync(|col| col.rebuild_fsrs_states_inner(&ids))
    }

    /// Caller supplies a transaction. Rebuild under the destination preset,
    /// without changing due dates, queues or intervals.
    pub(crate) fn rebuild_fsrs_states_inner(&mut self, ids: &[CardId]) -> Result<usize> {
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
        let timing = self.timing_today()?;
        let usn = self.usn()?;
        let mut progress = self.new_progress_handler::<ComputeMemoryProgress>();
        progress.update(false, |p| p.total_cards = ids.len() as u32)?;
        let mut repaired = 0;
        for (id, cards) in groups {
            let config = &configs[&id];
            let fsrs = FSRS::new(config.fsrs_params())?;
            let ids: Vec<_> = cards.iter().map(|card| card.id).collect();
            let revlog = self.revlog_for_srs(SearchNode::CardIds(comma_separated_ids(&ids)))?;
            let mut items: HashMap<_, _> = fsrs_items_for_memory_states(
                &fsrs,
                revlog,
                timing.next_day_at,
                config.inner.historical_retention,
                ignore_revlogs_before_ms_from_config(config)?,
            )?
            .into_iter()
            .collect();
            for mut card in cards {
                let original = card.clone();
                let stored = card.memory_state.unwrap();
                card.memory_state = Some(if let Some(item) = items.remove(&card.id).flatten() {
                    let state = fsrs.memory_state(item.item, item.starting_state)?;
                    card.last_review_time = self.storage.time_of_last_review(card.id)?;
                    fsrs_memory_state_for_fsrs(&fsrs, state)
                } else {
                    fsrs_memory_state_for_s90_and_difficulty(
                        &fsrs,
                        stored.stability,
                        stored.difficulty,
                    )
                    .or_invalid("invalid incomplete FSRS memory state")?
                });
                let deck = &decks[&card.original_or_current_deck_id()];
                card.desired_retention = Some(deck.effective_desired_retention(config));
                card.decay = Some(get_decay_from_params(config.fsrs_params()));
                self.update_card_inner(&mut card, original, usn)?;
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
        assert_eq!(col.repair_incomplete_fsrs7_states()?, 1);
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
        assert_eq!(col.repair_incomplete_fsrs7_states()?, 1);
        let card = col.storage.get_card(cid)?.unwrap();
        let state = card.memory_state.unwrap();
        assert_eq!((card.due, card.interval), (123, 30));
        assert_eq!((state.stability, state.difficulty), (20.0, 6.0));
        let fsrs = FSRS::new(&fsrs::DEFAULT_PARAMETERS)?;
        assert!((fsrs.interval_at_retrievability(state.into(), 0.9) - 20.0).abs() < 0.01);
        assert!(col.incomplete_fsrs7_card_ids()?.is_empty());
        assert_eq!(col.repair_incomplete_fsrs7_states()?, 0);
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
