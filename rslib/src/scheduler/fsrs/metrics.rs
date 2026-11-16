// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::cmp::Ordering;
use std::collections::HashMap;
use std::hash::Hasher;

use fnv::FnvHasher;
use fsrs::FSRS;

use crate::card::FsrsMemoryState;
use crate::deckconfig::ReviewCardOrder;
use crate::decks::FilteredSearchOrder;
use crate::prelude::*;
use crate::scheduler::timing::SchedTimingToday;

/// Card orders that need each card's preset model, so they are sorted in Rust
/// instead of SQL by the review queue and filtered decks.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum ExactFsrsOrder {
    Retrievability { descending: bool },
    RelativeOverdueness,
}

impl ExactFsrsOrder {
    pub(crate) fn for_review_order(order: ReviewCardOrder) -> Option<Self> {
        match order {
            ReviewCardOrder::RetrievabilityAscending => {
                Some(Self::Retrievability { descending: false })
            }
            ReviewCardOrder::RetrievabilityDescending => {
                Some(Self::Retrievability { descending: true })
            }
            ReviewCardOrder::RelativeOverdueness => Some(Self::RelativeOverdueness),
            _ => None,
        }
    }

    pub(crate) fn for_filtered_order(order: FilteredSearchOrder) -> Option<Self> {
        match order {
            FilteredSearchOrder::RetrievabilityAscending => {
                Some(Self::Retrievability { descending: false })
            }
            FilteredSearchOrder::RetrievabilityDescending => {
                Some(Self::Retrievability { descending: true })
            }
            FilteredSearchOrder::RelativeOverdueness => Some(Self::RelativeOverdueness),
            _ => None,
        }
    }

    /// Orders by key (reversed if descending), then by a hash of id and mtime
    /// so equal keys are shuffled reproducibly, then by id. Keys are always
    /// finite (see [FsrsMetricContext::exact_order_key]), so `total_cmp`
    /// agrees with `partial_cmp`.
    pub(crate) fn compare(self, a: &ExactOrderKey, b: &ExactOrderKey) -> Ordering {
        let order = a.value.total_cmp(&b.value);
        let order = if matches!(self, Self::Retrievability { descending: true }) {
            order.reverse()
        } else {
            order
        };
        order
            .then_with(|| a.hash.cmp(&b.hash))
            .then_with(|| a.id.cmp(&b.id))
    }
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct ExactOrderKey {
    value: f32,
    hash: i64,
    id: CardId,
}

impl ExactOrderKey {
    pub(crate) fn new(value: f32, id: CardId, mtime: TimestampSecs) -> Self {
        let mut hasher = FnvHasher::default();
        hasher.write_i64(id.0);
        hasher.write_i64(mtime.0);
        Self {
            value,
            hash: hasher.finish() as i64,
            id,
        }
    }
}

/// SM-2 relative overdueness, used for cards without a memory state.
fn sm2_relative_overdueness_key(due: i32, interval: u32, timing: SchedTimingToday) -> f32 {
    let due = due as i64;
    let review_day = due.saturating_sub(interval as i64);
    let days_elapsed = if due > 365_000 {
        (timing.next_day_at.0 as u32).saturating_sub(due as u32) / 86_400
    } else {
        timing.days_elapsed.saturating_sub(review_day as u32)
    };
    -((days_elapsed as f32) + 0.001) / (interval as f32).max(1.0)
}

/// Resolves each card's home preset and reuses one FSRS model per preset.
///
/// FSRS-7 retrievability depends on both stability traces, difficulty, and the
/// complete parameter vector, so card data alone is not sufficient.
pub(crate) struct FsrsMetricContext<'a> {
    decks: &'a HashMap<DeckId, Deck>,
    configs: &'a HashMap<DeckConfigId, DeckConfig>,
    models: HashMap<DeckConfigId, FSRS>,
}

impl<'a> FsrsMetricContext<'a> {
    pub(crate) fn new(
        decks: &'a HashMap<DeckId, Deck>,
        configs: &'a HashMap<DeckConfigId, DeckConfig>,
    ) -> Self {
        Self {
            decks,
            configs,
            models: HashMap::new(),
        }
    }

    pub(crate) fn current_retrievability(
        &mut self,
        card: &Card,
        state: FsrsMemoryState,
        elapsed_days: f32,
    ) -> Result<f32> {
        self.current_retrievability_for_deck(
            card.original_or_current_deck_id(),
            state,
            elapsed_days,
        )
    }

    /// Current retrievability of a card with a memory state.
    pub(crate) fn card_retrievability(
        &mut self,
        card: &Card,
        timing: &SchedTimingToday,
    ) -> Result<Option<f32>> {
        card.memory_state
            .map(|state| {
                self.current_retrievability(card, state, card.days_since_last_review(timing))
            })
            .transpose()
    }

    pub(crate) fn current_retrievability_for_deck(
        &mut self,
        deck_id: DeckId,
        state: FsrsMemoryState,
        elapsed_days: f32,
    ) -> Result<f32> {
        let config_id = self.config_id_for_deck(deck_id)?;
        let retrievability = self
            .model(config_id)?
            .current_retrievability(state.into(), elapsed_days.max(0.0));
        require!(retrievability.is_finite(), "invalid FSRS parameter values");
        Ok(retrievability)
    }

    pub(crate) fn relative_overdueness_for_deck(
        &mut self,
        deck_id: DeckId,
        state: FsrsMemoryState,
        elapsed_days: f32,
    ) -> Result<f32> {
        let deck = self
            .decks
            .get(&deck_id)
            .or_invalid("missing deck for card")?;
        let config_id = deck
            .config_id()
            .or_invalid("card belongs to a filtered deck")?;
        let config = self
            .configs
            .get(&config_id)
            .or_invalid("missing deck config for card")?;
        let desired_retention = deck.effective_desired_retention(config);
        let interval = self
            .model(config_id)?
            .interval_at_retrievability(state.into(), desired_retention.clamp(0.0001, 0.9999));
        let key = -elapsed_days.max(0.0) / interval.max(0.0001);
        require!(key.is_finite(), "invalid FSRS parameter values");
        Ok(key)
    }

    /// Sort key of a card in its home deck for `order`. `memory` is the card's
    /// memory state and days since its last review; without it, the key is
    /// SM-2 relative overdueness from `due` (original or current) and
    /// `interval`.
    pub(crate) fn exact_order_key(
        &mut self,
        order: ExactFsrsOrder,
        home_deck_id: DeckId,
        memory: Option<(FsrsMemoryState, f32)>,
        due: i32,
        interval: u32,
        timing: SchedTimingToday,
    ) -> Result<f32> {
        match (memory, order) {
            (Some((state, elapsed_days)), ExactFsrsOrder::Retrievability { .. }) => {
                self.current_retrievability_for_deck(home_deck_id, state, elapsed_days)
            }
            (Some((state, elapsed_days)), ExactFsrsOrder::RelativeOverdueness) => {
                self.relative_overdueness_for_deck(home_deck_id, state, elapsed_days)
            }
            (None, _) => Ok(sm2_relative_overdueness_key(due, interval, timing)),
        }
    }

    fn config_id_for_deck(&self, deck_id: DeckId) -> Result<DeckConfigId> {
        self.decks
            .get(&deck_id)
            .or_invalid("missing deck for card")?
            .config_id()
            .or_invalid("card belongs to a filtered deck")
    }

    fn model(&mut self, config_id: DeckConfigId) -> Result<&FSRS> {
        if !self.models.contains_key(&config_id) {
            let config = self
                .configs
                .get(&config_id)
                .or_invalid("missing deck config for card")?;
            self.models
                .insert(config_id, FSRS::new(config.fsrs_params())?);
        }
        Ok(self.models.get(&config_id).unwrap())
    }
}

#[cfg(test)]
mod tests {
    use fsrs::DEFAULT_PARAMETERS;

    use super::*;
    use crate::card::Card;

    #[test]
    fn exact_order_descending_reverses_keys_but_not_tie_breaks() {
        let low = ExactOrderKey::new(0.2, CardId(3), TimestampSecs(0));
        let high = ExactOrderKey::new(0.8, CardId(1), TimestampSecs(0));
        let tie = ExactOrderKey::new(0.8, CardId(2), TimestampSecs(0));
        let ascending =
            ExactFsrsOrder::for_filtered_order(FilteredSearchOrder::RetrievabilityAscending)
                .unwrap();
        let descending =
            ExactFsrsOrder::for_filtered_order(FilteredSearchOrder::RetrievabilityDescending)
                .unwrap();

        assert_eq!(ascending.compare(&low, &high), Ordering::Less);
        assert_eq!(descending.compare(&low, &high), Ordering::Greater);
        assert_eq!(
            ascending.compare(&high, &tie),
            descending.compare(&high, &tie)
        );
        assert_eq!(
            ExactFsrsOrder::for_filtered_order(FilteredSearchOrder::RelativeOverdueness),
            Some(ExactFsrsOrder::RelativeOverdueness)
        );
        assert_eq!(
            ExactFsrsOrder::for_filtered_order(FilteredSearchOrder::Random),
            None
        );
    }

    #[test]
    fn metrics_use_the_home_decks_current_preset_and_retention() -> Result<()> {
        let mut col = Collection::new();
        let mut first_deck = col.get_or_create_normal_deck("Default")?;
        let first_config_id = first_deck.config_id().unwrap();
        let mut first_config = col.get_deck_config(first_config_id, false)?.unwrap();
        first_config.inner.fsrs_params_7 = DEFAULT_PARAMETERS.to_vec();
        col.add_or_update_deck_config(&mut first_config)?;
        first_deck.normal_mut()?.desired_retention = Some(0.8);
        col.add_or_update_deck(&mut first_deck)?;

        let mut second_config = DeckConfig {
            name: "second".into(),
            ..Default::default()
        };
        second_config.inner.fsrs_params_7 = DEFAULT_PARAMETERS.to_vec();
        second_config.inner.fsrs_params_7[24] = 0.9;
        col.add_or_update_deck_config(&mut second_config)?;
        let mut second_deck = col.get_or_create_normal_deck("second")?;
        second_deck.normal_mut()?.config_id = second_config.id.0;
        col.add_or_update_deck(&mut second_deck)?;

        let decks = col.storage.get_decks_map()?;
        let configs = col.storage.get_deck_config_map()?;
        let mut metrics = FsrsMetricContext::new(&decks, &configs);
        let state = FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: Some(5.0),
            difficulty: 8.0,
        };
        let first_card = Card {
            deck_id: first_deck.id,
            desired_retention: Some(0.99),
            ..Default::default()
        };
        let second_card = Card {
            deck_id: second_deck.id,
            ..Default::default()
        };

        let first_r = metrics.current_retrievability(&first_card, state, 20.0)?;
        let second_r = metrics.current_retrievability(&second_card, state, 20.0)?;
        assert_ne!(first_r, second_r);

        let actual_key = metrics.relative_overdueness_for_deck(first_card.deck_id, state, 20.0)?;
        let expected_interval = FSRS::new(configs[&first_config_id].fsrs_params())?
            .interval_at_retrievability(state.into(), 0.8);
        assert!((actual_key - (-20.0 / expected_interval)).abs() < 1e-6);
        Ok(())
    }
}
