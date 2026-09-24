// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use fsrs::FSRS;

use crate::card::FsrsMemoryState;
use crate::prelude::*;

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

    pub(crate) fn relative_overdueness(
        &mut self,
        card: &Card,
        state: FsrsMemoryState,
        elapsed_days: f32,
    ) -> Result<f32> {
        self.relative_overdueness_for_deck(card.original_or_current_deck_id(), state, elapsed_days)
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

        let actual_key = metrics.relative_overdueness(&first_card, state, 20.0)?;
        let expected_interval = FSRS::new(configs[&first_config_id].fsrs_params())?
            .interval_at_retrievability(state.into(), 0.8);
        assert!((actual_key - (-20.0 / expected_interval)).abs() < 1e-6);
        Ok(())
    }
}
