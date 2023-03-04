// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Updating configs in bulk, from the deck options screen.

use std::collections::HashMap;
use std::collections::HashSet;
use std::iter;

use crate::config::StringKey;
use crate::decks::NormalDeck;
use crate::pb;
use crate::pb::deckconfig::deck_configs_for_update::current_deck::Limits;
use crate::pb::deckconfig::deck_configs_for_update::ConfigWithExtra;
use crate::pb::deckconfig::deck_configs_for_update::CurrentDeck;
use crate::pb::decks::deck::normal::DayLimit;
use crate::prelude::*;
use crate::search::JoinSearches;
use crate::search::SearchNode;

#[derive(Debug, Clone)]
pub struct UpdateDeckConfigsRequest {
    pub target_deck_id: DeckId,
    /// Deck will be set to last provided deck config.
    pub configs: Vec<DeckConfig>,
    pub removed_config_ids: Vec<DeckConfigId>,
    pub apply_to_children: bool,
    pub card_state_customizer: String,
    pub limits: Limits,
    pub new_cards_ignore_review_limit: bool,
}

impl Collection {
    /// Information required for the deck options screen.
    pub fn get_deck_configs_for_update(
        &mut self,
        deck: DeckId,
    ) -> Result<pb::deckconfig::DeckConfigsForUpdate> {
        Ok(pb::deckconfig::DeckConfigsForUpdate {
            all_config: self.get_deck_config_with_extra_for_update()?,
            current_deck: Some(self.get_current_deck_for_update(deck)?),
            defaults: Some(DeckConfig::default().into()),
            schema_modified: self
                .storage
                .get_collection_timestamps()?
                .schema_changed_since_sync(),
            v3_scheduler: self.get_config_bool(BoolKey::Sched2021),
            card_state_customizer: self.get_config_string(StringKey::CardStateCustomizer),
            new_cards_ignore_review_limit: self.get_config_bool(BoolKey::NewCardsIgnoreReviewLimit),
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
        let deck = self.get_deck(deck)?.or_not_found(deck)?;
        let normal = deck.normal()?;
        let today = self.timing_today()?.days_elapsed;

        Ok(CurrentDeck {
            name: deck.human_name(),
            config_id: normal.config_id,
            parent_config_ids: self
                .parent_config_ids(&deck)?
                .into_iter()
                .map(Into::into)
                .collect(),
            limits: Some(normal.to_limits(today)),
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
        require!(!input.configs.is_empty(), "config not provided");
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
                .or_not_found(input.target_deck_id)?;
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
        let today = self.timing_today()?.days_elapsed;
        let selected_config = input.configs.last().unwrap();
        for deck in self.storage.get_all_decks()? {
            if let Ok(normal) = deck.normal() {
                let deck_id = deck.id;

                // previous order
                let previous_config_id = DeckConfigId(normal.config_id);
                let previous_config = configs_before_update.get(&previous_config_id);
                let previous_order = previous_config
                    .map(|c| c.inner.new_card_insert_order())
                    .unwrap_or_default();

                // if a selected (sub)deck, or its old config was removed, update deck to point
                // to new config
                let current_config_id = if selected_deck_ids.contains(&deck.id)
                    || !configs_after_update.contains_key(&previous_config_id)
                {
                    let mut updated = deck.clone();
                    updated.normal_mut()?.config_id = selected_config.id.0;
                    updated.normal_mut()?.update_limits(&input.limits, today);
                    self.update_deck_inner(&mut updated, deck, usn)?;
                    selected_config.id
                } else {
                    previous_config_id
                };

                // if new order differs, deck needs re-sorting
                let current_config = configs_after_update.get(&current_config_id);
                let current_order = current_config
                    .map(|c| c.inner.new_card_insert_order())
                    .unwrap_or_default();
                if previous_order != current_order {
                    self.sort_deck(deck_id, current_order, usn)?;
                }

                self.adjust_remaining_steps_in_deck(deck_id, previous_config, current_config, usn)?;
            }
        }

        self.set_config_string_inner(StringKey::CardStateCustomizer, &input.card_state_customizer)?;
        self.set_config_bool_inner(
            BoolKey::NewCardsIgnoreReviewLimit,
            input.new_cards_ignore_review_limit,
        )?;

        Ok(())
    }

    /// Adjust the remaining steps of cards in the given deck according to the
    /// config change.
    pub(crate) fn adjust_remaining_steps_in_deck(
        &mut self,
        deck: DeckId,
        previous_config: Option<&DeckConfig>,
        current_config: Option<&DeckConfig>,
        usn: Usn,
    ) -> Result<()> {
        if let (Some(old), Some(new)) = (previous_config, current_config) {
            for (search, old_steps, new_steps) in [
                (
                    SearchBuilder::learning_cards(),
                    &old.inner.learn_steps,
                    &new.inner.learn_steps,
                ),
                (
                    SearchBuilder::relearning_cards(),
                    &old.inner.relearn_steps,
                    &new.inner.relearn_steps,
                ),
            ] {
                if old_steps == new_steps {
                    continue;
                }
                let search = search.clone().and(SearchNode::from_deck_id(deck, false));
                for mut card in self.all_cards_for_search(search)? {
                    self.adjust_remaining_steps(&mut card, old_steps, new_steps, usn)?;
                }
            }
        }
        Ok(())
    }
}

impl NormalDeck {
    fn to_limits(&self, today: u32) -> Limits {
        Limits {
            review: self.review_limit,
            new: self.new_limit,
            review_today: self.review_limit_today.map(|limit| limit.limit),
            new_today: self.new_limit_today.map(|limit| limit.limit),
            review_today_active: self
                .review_limit_today
                .map(|limit| limit.today == today)
                .unwrap_or_default(),
            new_today_active: self
                .new_limit_today
                .map(|limit| limit.today == today)
                .unwrap_or_default(),
        }
    }

    fn update_limits(&mut self, limits: &Limits, today: u32) {
        self.review_limit = limits.review;
        self.new_limit = limits.new;
        update_day_limit(&mut self.review_limit_today, limits.review_today, today);
        update_day_limit(&mut self.new_limit_today, limits.new_today, today);
    }
}

fn update_day_limit(day_limit: &mut Option<DayLimit>, new_limit: Option<u32>, today: u32) {
    if let Some(limit) = new_limit {
        day_limit.replace(DayLimit { limit, today });
    } else if let Some(limit) = day_limit {
        // instead of setting to None, only make sure today is in the past,
        // thus preserving last used value
        limit.today = limit.today.min(today - 1);
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::collection::open_test_collection;
    use crate::deckconfig::NewCardInsertOrder;
    use crate::tests::open_test_collection_with_learning_card;
    use crate::tests::open_test_collection_with_relearning_card;

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

        // add the keys so it doesn't trigger a change below
        col.set_config_string_inner(StringKey::CardStateCustomizer, "")?;
        col.set_config_bool_inner(BoolKey::NewCardsIgnoreReviewLimit, false)?;

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
            limits: Limits::default(),
            new_cards_ignore_review_limit: false,
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

    #[test]
    fn should_increase_remaining_learning_steps_if_unpassed_learning_step_added() {
        let mut col = open_test_collection_with_learning_card();
        col.set_default_learn_steps(vec![1., 10., 100.]);
        assert_eq!(col.get_first_card().remaining_steps, 3);
    }

    #[test]
    fn should_keep_remaining_learning_steps_if_unpassed_relearning_step_added() {
        let mut col = open_test_collection_with_learning_card();
        col.set_default_relearn_steps(vec![1., 10., 100.]);
        assert_eq!(col.get_first_card().remaining_steps, 2);
    }

    #[test]
    fn should_keep_remaining_learning_steps_if_passed_learning_step_added() {
        let mut col = open_test_collection_with_learning_card();
        col.answer_good();
        col.set_default_learn_steps(vec![1., 1., 10.]);
        assert_eq!(col.get_first_card().remaining_steps, 1);
    }

    #[test]
    fn should_keep_at_least_one_remaining_learning_step() {
        let mut col = open_test_collection_with_learning_card();
        col.answer_good();
        col.set_default_learn_steps(vec![1.]);
        assert_eq!(col.get_first_card().remaining_steps, 1);
    }

    #[test]
    fn should_increase_remaining_relearning_steps_if_unpassed_relearning_step_added() {
        let mut col = open_test_collection_with_relearning_card();
        col.set_default_relearn_steps(vec![1., 10., 100.]);
        assert_eq!(col.get_first_card().remaining_steps, 3);
    }

    #[test]
    fn should_keep_remaining_relearning_steps_if_unpassed_learning_step_added() {
        let mut col = open_test_collection_with_relearning_card();
        col.set_default_learn_steps(vec![1., 10., 100.]);
        assert_eq!(col.get_first_card().remaining_steps, 1);
    }

    #[test]
    fn should_keep_remaining_relearning_steps_if_passed_relearning_step_added() {
        let mut col = open_test_collection_with_relearning_card();
        col.set_default_relearn_steps(vec![10., 100.]);
        col.answer_good();
        col.set_default_relearn_steps(vec![1., 10., 100.]);
        assert_eq!(col.get_first_card().remaining_steps, 1);
    }

    #[test]
    fn should_keep_at_least_one_remaining_relearning_step() {
        let mut col = open_test_collection_with_relearning_card();
        col.set_default_relearn_steps(vec![10., 100.]);
        col.answer_good();
        col.set_default_relearn_steps(vec![1.]);
        assert_eq!(col.get_first_card().remaining_steps, 1);
    }
}
