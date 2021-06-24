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
    config::StringKey,
    prelude::*,
};

#[derive(Debug, Clone)]
pub struct UpdateDeckConfigsRequest {
    pub target_deck_id: DeckId,
    /// Deck will be set to last provided deck config.
    pub configs: Vec<DeckConfig>,
    pub removed_config_ids: Vec<DeckConfigId>,
    pub apply_to_children: bool,
    pub card_state_customizer: String,
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
            v3_scheduler: self.get_config_bool(BoolKey::Sched2021),
            have_addons: false,
            card_state_customizer: self.get_config_string(StringKey::CardStateCustomizer),
        })
    }

    /// Information required for the deck options screen.
    pub fn update_deck_configs(&mut self, input: UpdateDeckConfigsRequest) -> Result<OpOutput<()>> {
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

    fn get_deck_config_use_counts(&self) -> Result<HashMap<DeckConfigId, usize>> {
        let mut counts = HashMap::new();
        for deck in self.storage.get_all_decks()? {
            if let Ok(normal) = deck.normal() {
                *counts.entry(DeckConfigId(normal.config_id)).or_default() += 1;
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
    fn parent_config_ids(&self, deck: &Deck) -> Result<HashSet<DeckConfigId>> {
        Ok(self
            .storage
            .parent_decks(deck)?
            .iter()
            .filter_map(|deck| {
                deck.normal()
                    .ok()
                    .map(|normal| DeckConfigId(normal.config_id))
            })
            .collect())
    }

    fn update_deck_configs_inner(&mut self, mut input: UpdateDeckConfigsRequest) -> Result<()> {
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
            self.add_or_update_deck_config(conf)?;
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
                let previous_config_id = DeckConfigId(normal.config_id);
                let previous_order = configs_before_update
                    .get(&previous_config_id)
                    .map(|c| c.inner.new_card_insert_order())
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
                    .map(|c| c.inner.new_card_insert_order())
                    .unwrap_or_default();
                if previous_order != current_order {
                    self.sort_deck(deck_id, current_order, usn)?;
                }
            }
        }

        self.set_config_string_inner(StringKey::CardStateCustomizer, &input.card_state_customizer)?;

        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::{collection::open_test_collection, deckconfig::NewCardInsertOrder};

    #[test]
    fn updating() -> Result<()> {
        let mut col = open_test_collection();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note1 = nt.new_note();
        col.add_note(&mut note1, DeckId(1))?;
        let card1_id = col.storage.card_ids_of_notes(&[note1.id])?[0];
        for _ in 0..9 {
            let mut note = nt.new_note();
            col.add_note(&mut note, DeckId(1))?;
        }

        // add the key so it doesn't trigger a change below
        col.set_config_string_inner(StringKey::CardStateCustomizer, "")?;

        // pretend we're in sync
        let stamps = col.storage.get_collection_timestamps()?;
        col.storage.set_last_sync(stamps.schema_change)?;

        let full_sync_required = |col: &mut Collection| -> bool {
            col.storage
                .get_collection_timestamps()
                .unwrap()
                .schema_changed_since_sync()
        };
        let reset_card1_pos = |col: &mut Collection| {
            let mut card = col.storage.get_card(card1_id).unwrap().unwrap();
            // set it out of bounds, so we can be sure it has changed
            card.due = 0;
            col.storage.update_card(&card).unwrap();
        };
        let card1_pos = |col: &mut Collection| col.storage.get_card(card1_id).unwrap().unwrap().due;

        // if nothing changed, no changes should be made
        let output = col.get_deck_configs_for_update(DeckId(1))?;
        let mut input = UpdateDeckConfigsRequest {
            target_deck_id: DeckId(1),
            configs: output
                .all_config
                .into_iter()
                .map(|c| c.config.unwrap().into())
                .collect(),
            removed_config_ids: vec![],
            apply_to_children: false,
            card_state_customizer: "".to_string(),
        };
        assert!(!col.update_deck_configs(input.clone())?.changes.had_change());

        // modifying a value should update the config, but not the deck
        input.configs[0].inner.new_per_day += 1;
        let changes = col.update_deck_configs(input.clone())?.changes.changes;
        assert!(!changes.deck);
        assert!(changes.deck_config);
        assert!(!changes.card);

        // adding a new config will update the deck as well
        let new_config = DeckConfig {
            id: DeckConfigId(0),
            ..input.configs[0].clone()
        };
        input.configs.push(new_config);
        let changes = col.update_deck_configs(input.clone())?.changes.changes;
        assert!(changes.deck);
        assert!(changes.deck_config);
        assert!(!changes.card);
        let allocated_id = col.get_deck(DeckId(1))?.unwrap().normal()?.config_id;
        assert_ne!(allocated_id, 0);
        assert_ne!(allocated_id, 1);

        // changing the order will cause the cards to be re-sorted
        assert_eq!(card1_pos(&mut col), 1);
        reset_card1_pos(&mut col);
        assert_eq!(card1_pos(&mut col), 0);
        input.configs[1].inner.new_card_insert_order = NewCardInsertOrder::Random as i32;
        assert!(col.update_deck_configs(input.clone())?.changes.changes.card);
        assert_ne!(card1_pos(&mut col), 0);

        // removing the config will assign the selected config (default in this case),
        // and as default has normal sort order, that will reset the order again
        assert!(!full_sync_required(&mut col));
        reset_card1_pos(&mut col);
        input.configs.remove(1);
        input.removed_config_ids.push(DeckConfigId(allocated_id));
        col.update_deck_configs(input)?;
        let current_id = col.get_deck(DeckId(1))?.unwrap().normal()?.config_id;
        assert_eq!(current_id, 1);
        assert_eq!(card1_pos(&mut col), 1);
        // should have forced a full sync
        assert!(full_sync_required(&mut col));

        Ok(())
    }
}
