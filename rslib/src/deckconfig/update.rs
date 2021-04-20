// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Updating configs in bulk, from the deck config screen.

use std::collections::{HashMap, HashSet};

use crate::{
    backend_proto as pb,
    backend_proto::deck_configs_for_update::{ConfigWithExtra, CurrentDeck},
    prelude::*,
};

pub struct UpdateDeckConfigsIn {
    pub target_deck_id: DeckId,
    /// Deck will be set to last provided deck config.
    pub configs: Vec<DeckConfig>,
    pub removed_config_ids: Vec<DeckConfId>,
    pub apply_to_children: bool,
}

impl Collection {
    /// Information required for the deck options screen.
    pub fn get_deck_configs_for_update(
        &mut self,
        deck: DeckId,
    ) -> Result<pb::DeckConfigsForUpdate> {
        Ok(pb::DeckConfigsForUpdate {
            all_config: self.get_deck_config_with_extra_for_update()?,
            current_deck: Some(self.get_current_deck_for_update(deck)?),
            defaults: Some(DeckConfig::default().into()),
            schema_modified: self
                .storage
                .get_collection_timestamps()?
                .schema_changed_since_sync(),
        })
    }

    /// Information required for the deck options screen.
    pub fn update_deck_configs(&mut self, input: UpdateDeckConfigsIn) -> Result<OpOutput<()>> {
        self.transact(Op::UpdateDeckConfig, |col| {
            col.update_deck_configs_inner(input)
        })
    }
}

impl Collection {
    fn get_deck_config_with_extra_for_update(&self) -> Result<Vec<ConfigWithExtra>> {
        // grab the config and sort it
        let mut config = self.storage.all_deck_config()?;
        config.sort_unstable_by(|a, b| a.name.cmp(&b.name));

        // combine with use counts
        let counts = self.get_deck_config_use_counts()?;
        Ok(config
            .into_iter()
            .map(|config| ConfigWithExtra {
                use_count: counts.get(&config.id).cloned().unwrap_or_default() as u32,
                config: Some(config.into()),
            })
            .collect())
    }

    fn get_deck_config_use_counts(&self) -> Result<HashMap<DeckConfId, usize>> {
        let mut counts = HashMap::new();
        for deck in self.storage.get_all_decks()? {
            if let Ok(normal) = deck.normal() {
                *counts.entry(DeckConfId(normal.config_id)).or_default() += 1;
            }
        }

        Ok(counts)
    }

    fn get_current_deck_for_update(&mut self, deck: DeckId) -> Result<CurrentDeck> {
        let deck = self.get_deck(deck)?.ok_or(AnkiError::NotFound)?;

        Ok(CurrentDeck {
            name: deck.human_name(),
            config_id: deck.normal()?.config_id,
            parent_config_ids: self
                .parent_config_ids(&deck)?
                .into_iter()
                .map(Into::into)
                .collect(),
        })
    }

    /// Deck configs used by parent decks.
    fn parent_config_ids(&self, deck: &Deck) -> Result<HashSet<DeckConfId>> {
        Ok(self
            .storage
            .parent_decks(deck)?
            .iter()
            .filter_map(|deck| {
                deck.normal()
                    .ok()
                    .map(|normal| DeckConfId(normal.config_id))
            })
            .collect())
    }

    fn update_deck_configs_inner(&mut self, mut input: UpdateDeckConfigsIn) -> Result<()> {
        if input.configs.is_empty() {
            return Err(AnkiError::invalid_input("config not provided"));
        }

        // handle removals first
        for dcid in &input.removed_config_ids {
            self.remove_deck_config_inner(*dcid)?;
        }

        // add/update provided configs
        for conf in &mut input.configs {
            self.add_or_update_deck_config(conf, false)?;
        }

        // point current deck to last config
        let usn = self.usn()?;
        let config_id = input.configs.last().unwrap().id;
        let mut deck = self
            .storage
            .get_deck(input.target_deck_id)?
            .ok_or(AnkiError::NotFound)?;
        let original = deck.clone();
        deck.normal_mut()?.config_id = config_id.0;
        self.update_deck_inner(&mut deck, original, usn)?;

        if input.apply_to_children {
            for mut child_deck in self.storage.child_decks(&deck)? {
                let child_original = child_deck.clone();
                if let Ok(normal) = child_deck.normal_mut() {
                    normal.config_id = config_id.0;
                    self.update_deck_inner(&mut child_deck, child_original, usn)?;
                }
            }
        }

        Ok(())
    }
}
