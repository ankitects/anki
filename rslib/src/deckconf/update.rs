// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::{HashMap, HashSet};

use pb::deck_config_for_update::{ConfigWithExtra, CurrentDeck};

use crate::{backend_proto as pb, prelude::*};

impl Collection {
    /// Information required for the deck options screen.
    pub fn get_deck_config_for_update(&mut self, deck: DeckId) -> Result<pb::DeckConfigForUpdate> {
        Ok(pb::DeckConfigForUpdate {
            all_config: self.get_deck_config_with_extra_for_update()?,
            current_deck: Some(self.get_current_deck_for_update(deck)?),
            defaults: Some(DeckConf::default().into()),
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

        let mut parent_new_limit = u32::MAX;
        let mut parent_review_limit = u32::MAX;
        for config_id in self.parent_config_ids(&deck)? {
            if let Some(config) = self.storage.get_deck_config(config_id)? {
                parent_new_limit = parent_new_limit.min(config.inner.new_per_day);
                parent_review_limit = parent_review_limit.min(config.inner.reviews_per_day);
            }
        }

        Ok(CurrentDeck {
            name: deck.name.clone(),
            config_id: deck.normal()?.config_id,
            parent_new_limit,
            parent_review_limit,
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
}
