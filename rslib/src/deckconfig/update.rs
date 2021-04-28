// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Updating configs in bulk, from the deck options screen.

use std::{
    collections::{HashMap, HashSet},
    iter,
};

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
        let configs_before_update = self.storage.get_deck_config_map()?;
        let mut configs_after_update = configs_before_update.clone();

        // handle removals first
        for dcid in &input.removed_config_ids {
            self.remove_deck_config_inner(*dcid)?;
            configs_after_update.remove(dcid);
        }

        // add/update provided configs
        for conf in &mut input.configs {
            self.add_or_update_deck_config(conf, false)?;
            configs_after_update.insert(conf.id, conf.clone());
        }

        // get selected deck and possibly children
        let selected_deck_ids: HashSet<_> = if input.apply_to_children {
            let deck = self
                .storage
                .get_deck(input.target_deck_id)?
                .ok_or(AnkiError::NotFound)?;
            self.storage
                .child_decks(&deck)?
                .iter()
                .chain(iter::once(&deck))
                .map(|d| d.id)
                .collect()
        } else {
            [input.target_deck_id].iter().cloned().collect()
        };

        // loop through all normal decks
        let usn = self.usn()?;
        let selected_config = input.configs.last().unwrap();
        for deck in self.storage.get_all_decks()? {
            if let Ok(normal) = deck.normal() {
                let deck_id = deck.id;

                // previous order
                let previous_config_id = DeckConfId(normal.config_id);
                let previous_order = configs_before_update
                    .get(&previous_config_id)
                    .map(|c| c.inner.new_card_order())
                    .unwrap_or_default();

                // if a selected (sub)deck, or its old config was removed, update deck to point to new config
                let current_config_id = if selected_deck_ids.contains(&deck.id)
                    || !configs_after_update.contains_key(&previous_config_id)
                {
                    let mut updated = deck.clone();
                    updated.normal_mut()?.config_id = selected_config.id.0;
                    self.update_deck_inner(&mut updated, deck, usn)?;
                    selected_config.id
                } else {
                    previous_config_id
                };

                // if new order differs, deck needs re-sorting
                let current_order = configs_after_update
                    .get(&current_config_id)
                    .map(|c| c.inner.new_card_order())
                    .unwrap_or_default();
                if previous_order != current_order {
                    self.sort_deck(deck_id, current_order, usn)?;
                }
            }
        }

        Ok(())
    }
}
