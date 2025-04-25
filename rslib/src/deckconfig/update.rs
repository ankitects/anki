// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Updating configs in bulk, from the deck options screen.

use std::collections::HashMap;
use std::collections::HashSet;
use std::iter;

use anki_proto::deck_config::deck_configs_for_update::current_deck::Limits;
use anki_proto::deck_config::deck_configs_for_update::ConfigWithExtra;
use anki_proto::deck_config::deck_configs_for_update::CurrentDeck;
use anki_proto::deck_config::UpdateDeckConfigsMode;
use anki_proto::decks::deck::normal::DayLimit;
use fsrs::DEFAULT_PARAMETERS;
use fsrs::FSRS;

use crate::config::I32ConfigKey;
use crate::config::StringKey;
use crate::decks::NormalDeck;
use crate::prelude::*;
use crate::scheduler::fsrs::memory_state::UpdateMemoryStateEntry;
use crate::scheduler::fsrs::memory_state::UpdateMemoryStateRequest;
use crate::scheduler::fsrs::params::ignore_revlogs_before_ms_from_config;
use crate::search::JoinSearches;
use crate::search::Negated;
use crate::search::SearchNode;
use crate::search::StateKind;
use crate::storage::comma_separated_ids;

#[derive(Debug, Clone)]
pub struct UpdateDeckConfigsRequest {
    pub target_deck_id: DeckId,
    /// Deck will be set to last provided deck config.
    pub configs: Vec<DeckConfig>,
    pub removed_config_ids: Vec<DeckConfigId>,
    pub mode: UpdateDeckConfigsMode,
    pub card_state_customizer: String,
    pub limits: Limits,
    pub new_cards_ignore_review_limit: bool,
    pub apply_all_parent_limits: bool,
    pub fsrs: bool,
    pub fsrs_reschedule: bool,
}

impl Collection {
    /// Information required for the deck options screen.
    pub fn get_deck_configs_for_update(
        &mut self,
        deck: DeckId,
    ) -> Result<anki_proto::deck_config::DeckConfigsForUpdate> {
        let mut defaults = DeckConfig::default();
        defaults.inner.fsrs_params_6 = DEFAULT_PARAMETERS.into();
        let last_optimize = self.get_config_i32(I32ConfigKey::LastFsrsOptimize) as u32;
        let days_since_last_fsrs_optimize = if last_optimize > 0 {
            self.timing_today()?
                .days_elapsed
                .saturating_sub(last_optimize)
        } else {
            0
        };
        Ok(anki_proto::deck_config::DeckConfigsForUpdate {
            all_config: self.get_deck_config_with_extra_for_update()?,
            current_deck: Some(self.get_current_deck_for_update(deck)?),
            defaults: Some(defaults.into()),
            schema_modified: self
                .storage
                .get_collection_timestamps()?
                .schema_changed_since_sync(),
            card_state_customizer: self.get_config_string(StringKey::CardStateCustomizer),
            new_cards_ignore_review_limit: self.get_config_bool(BoolKey::NewCardsIgnoreReviewLimit),
            apply_all_parent_limits: self.get_config_bool(BoolKey::ApplyAllParentLimits),
            fsrs: self.get_config_bool(BoolKey::Fsrs),
            days_since_last_fsrs_optimize,
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
        // pre-fill empty fsrs params with older params
        config.iter_mut().for_each(|c| {
            if c.inner.fsrs_params_6.is_empty() {
                c.inner.fsrs_params_6 = if c.inner.fsrs_params_5.is_empty() {
                    c.inner.fsrs_params_4.clone()
                } else {
                    c.inner.fsrs_params_5.clone()
                };
            }
        });

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
            limits: Some(normal_deck_to_limits(normal, today)),
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

    fn update_deck_configs_inner(&mut self, mut req: UpdateDeckConfigsRequest) -> Result<()> {
        require!(!req.configs.is_empty(), "config not provided");
        let configs_before_update = self.storage.get_deck_config_map()?;
        let mut configs_after_update = configs_before_update.clone();

        // handle removals first
        for dcid in &req.removed_config_ids {
            self.remove_deck_config_inner(*dcid)?;
            configs_after_update.remove(dcid);
        }

        if req.mode == UpdateDeckConfigsMode::ComputeAllParams {
            self.compute_all_params(&mut req)?;
        }

        // add/update provided configs
        for conf in &mut req.configs {
            // If the user has provided empty FSRS6 params, zero out any
            // old params as well, so we don't fall back on them, which would
            // be surprising as they're not shown in the GUI.
            if conf.inner.fsrs_params_6.is_empty() {
                conf.inner.fsrs_params_5.clear();
                conf.inner.fsrs_params_4.clear();
            }
            // check the provided parameters are valid before we save them
            FSRS::new(Some(conf.fsrs_params()))?;
            self.add_or_update_deck_config(conf)?;
            configs_after_update.insert(conf.id, conf.clone());
        }

        // get selected deck and possibly children
        let selected_deck_ids: HashSet<_> = if req.mode == UpdateDeckConfigsMode::ApplyToChildren {
            let deck = self
                .storage
                .get_deck(req.target_deck_id)?
                .or_not_found(req.target_deck_id)?;
            self.storage
                .child_decks(&deck)?
                .iter()
                .chain(iter::once(&deck))
                .map(|d| d.id)
                .collect()
        } else {
            [req.target_deck_id].iter().cloned().collect()
        };

        // loop through all normal decks
        let usn = self.usn()?;
        let today = self.timing_today()?.days_elapsed;
        let selected_config = req.configs.last().unwrap();
        let mut decks_needing_memory_recompute: HashMap<DeckConfigId, Vec<DeckId>> =
            Default::default();
        let fsrs_toggled = self.get_config_bool(BoolKey::Fsrs) != req.fsrs;
        if fsrs_toggled {
            self.set_config_bool_inner(BoolKey::Fsrs, req.fsrs)?;
        }
        for deck in self.storage.get_all_decks()? {
            if let Ok(normal) = deck.normal() {
                let deck_id = deck.id;

                // previous order & params
                let previous_config_id = DeckConfigId(normal.config_id);
                let previous_config = configs_before_update.get(&previous_config_id);
                let previous_order = previous_config
                    .map(|c| c.inner.new_card_insert_order())
                    .unwrap_or_default();
                let previous_params = previous_config.map(|c| c.fsrs_params());
                let previous_retention = previous_config.map(|c| c.inner.desired_retention);
                let previous_easy_days = previous_config.map(|c| &c.inner.easy_days_percentages);

                // if a selected (sub)deck, or its old config was removed, update deck to point
                // to new config
                let current_config_id = if selected_deck_ids.contains(&deck.id)
                    || !configs_after_update.contains_key(&previous_config_id)
                {
                    let mut updated = deck.clone();
                    updated.normal_mut()?.config_id = selected_config.id.0;
                    update_deck_limits(updated.normal_mut()?, &req.limits, today);
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

                // if params differ, memory state needs to be recomputed
                let current_params = current_config.map(|c| c.fsrs_params());
                let current_retention = current_config.map(|c| c.inner.desired_retention);
                let current_easy_days = current_config.map(|c| &c.inner.easy_days_percentages);
                if fsrs_toggled
                    || previous_params != current_params
                    || previous_retention != current_retention
                    || (req.fsrs_reschedule && previous_easy_days != current_easy_days)
                {
                    decks_needing_memory_recompute
                        .entry(current_config_id)
                        .or_default()
                        .push(deck_id);
                }

                self.adjust_remaining_steps_in_deck(deck_id, previous_config, current_config, usn)?;
            }
        }

        if !decks_needing_memory_recompute.is_empty() {
            let input: Vec<UpdateMemoryStateEntry> = decks_needing_memory_recompute
                .into_iter()
                .map(|(conf_id, search)| {
                    let config = configs_after_update.get(&conf_id);
                    let params = config.and_then(|c| {
                        if req.fsrs {
                            Some(UpdateMemoryStateRequest {
                                params: c.fsrs_params().clone(),
                                desired_retention: c.inner.desired_retention,
                                max_interval: c.inner.maximum_review_interval,
                                reschedule: req.fsrs_reschedule,
                                historical_retention: c.inner.historical_retention,
                            })
                        } else {
                            None
                        }
                    });
                    Ok(UpdateMemoryStateEntry {
                        req: params,
                        search: SearchNode::DeckIdsWithoutChildren(comma_separated_ids(&search)),
                        ignore_before: config
                            .map(ignore_revlogs_before_ms_from_config)
                            .unwrap_or(Ok(0.into()))?,
                    })
                })
                .collect::<Result<_>>()?;
            self.update_memory_state(input)?;
        }

        self.set_config_string_inner(StringKey::CardStateCustomizer, &req.card_state_customizer)?;
        self.set_config_bool_inner(
            BoolKey::NewCardsIgnoreReviewLimit,
            req.new_cards_ignore_review_limit,
        )?;
        self.set_config_bool_inner(BoolKey::ApplyAllParentLimits, req.apply_all_parent_limits)?;

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
    fn compute_all_params(&mut self, req: &mut UpdateDeckConfigsRequest) -> Result<()> {
        require!(req.fsrs, "FSRS must be enabled");

        // frontend didn't include any unmodified deck configs, so we need to fill them
        // in
        let changed_configs: HashSet<_> = req.configs.iter().map(|c| c.id).collect();
        let previous_last = req.configs.pop().or_invalid("no configs provided")?;
        for config in self.storage.all_deck_config()? {
            if !changed_configs.contains(&config.id) {
                req.configs.push(config);
            }
        }
        // other parts of the code expect the currently-selected preset to come last
        req.configs.push(previous_last);

        // calculate and apply params to each preset
        let config_len = req.configs.len() as u32;
        for (idx, config) in req.configs.iter_mut().enumerate() {
            let search = if config.inner.param_search.trim().is_empty() {
                SearchNode::Preset(config.name.clone())
                    .and(SearchNode::State(StateKind::Suspended).negated())
                    .try_into_search()?
                    .to_string()
            } else {
                config.inner.param_search.clone()
            };
            let ignore_revlogs_before_ms = ignore_revlogs_before_ms_from_config(config)?;
            let num_of_relearning_steps = config.inner.relearn_steps.len();
            match self.compute_params(
                &search,
                ignore_revlogs_before_ms,
                idx as u32 + 1,
                config_len,
                config.fsrs_params(),
                num_of_relearning_steps,
            ) {
                Ok(params) => {
                    println!("{}: {:?}", config.name, params.params);
                    config.inner.fsrs_params_6 = params.params;
                }
                Err(AnkiError::Interrupted) => return Err(AnkiError::Interrupted),
                Err(err) => {
                    println!("{}: {}", config.name, err)
                }
            }
            let today = self.timing_today()?.days_elapsed as i32;
            self.set_config_i32_inner(I32ConfigKey::LastFsrsOptimize, today)?;
        }
        Ok(())
    }
}

fn normal_deck_to_limits(deck: &NormalDeck, today: u32) -> Limits {
    Limits {
        review: deck.review_limit,
        new: deck.new_limit,
        review_today: deck.review_limit_today.map(|limit| limit.limit),
        new_today: deck.new_limit_today.map(|limit| limit.limit),
        review_today_active: deck
            .review_limit_today
            .map(|limit| limit.today == today)
            .unwrap_or_default(),
        new_today_active: deck
            .new_limit_today
            .map(|limit| limit.today == today)
            .unwrap_or_default(),
    }
}

fn update_deck_limits(deck: &mut NormalDeck, limits: &Limits, today: u32) {
    deck.review_limit = limits.review;
    deck.new_limit = limits.new;
    update_day_limit(&mut deck.review_limit_today, limits.review_today, today);
    update_day_limit(&mut deck.new_limit_today, limits.new_today, today);
}

fn update_day_limit(day_limit: &mut Option<DayLimit>, new_limit: Option<u32>, today: u32) {
    if let Some(limit) = new_limit {
        day_limit.replace(DayLimit { limit, today });
    } else {
        // if the collection was created today, the
        // "preserve last value" hack below won't work
        // clear "future" limits as well (from imports)
        day_limit.take_if(|limit| limit.today == 0 || limit.today > today);
        if let Some(limit) = day_limit {
            // instead of setting to None, only make sure today is in the past,
            // thus preserving last used value
            limit.today = limit.today.min(today.saturating_sub(1));
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::deckconfig::NewCardInsertOrder;
    use crate::tests::open_test_collection_with_learning_card;
    use crate::tests::open_test_collection_with_relearning_card;

    #[test]
    fn updating() -> Result<()> {
        let mut col = Collection::new();
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
        col.set_config_bool_inner(BoolKey::ApplyAllParentLimits, false)?;

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
            mode: UpdateDeckConfigsMode::Normal,
            card_state_customizer: "".to_string(),
            limits: Limits::default(),
            new_cards_ignore_review_limit: false,
            apply_all_parent_limits: false,
            fsrs: false,
            fsrs_reschedule: false,
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
