// Copyright: Anki Software, LLC and contributors
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
use fsrs::ComputeParametersVersion;
use fsrs::DEFAULT_PARAMETERS;
use fsrs::FSRS;
use fsrs::FSRS6_DEFAULT_PARAMETERS;
use tracing::debug;
use tracing::warn;

use super::FsrsVersion;
use crate::config::I32ConfigKey;
use crate::config::StringKey;
use crate::decks::NormalDeck;
use crate::prelude::*;
use crate::scheduler::fsrs::batch::ComputeParamsBatchInput;
use crate::scheduler::fsrs::memory_state::ComputeMemoryPresetProgress;
use crate::scheduler::fsrs::memory_state::ComputeMemoryProgress;
use crate::scheduler::fsrs::memory_state::UpdateMemoryStateEntry;
use crate::scheduler::fsrs::memory_state::UpdateMemoryStateRequest;
use crate::scheduler::fsrs::params::ignore_revlogs_before_ms_from_config;
use crate::scheduler::fsrs::params::ComputeAllParamsProgress;
use crate::scheduler::fsrs::params::DynamicDesiredRetentionSimulatorOptions;
use crate::scheduler::fsrs::params::PrepareComputeParamsInput;
use crate::scheduler::states::fuzz::StoredReviewFuzzConfig;
use crate::search::JoinSearches;
use crate::search::Negated;
use crate::search::Node;
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
    pub load_balancer_enabled: bool,
    pub fsrs_short_term_with_steps_enabled: bool,
    pub fsrs_learning_queues_disabled: bool,
    pub fsrs_reschedule: bool,
    pub fsrs_health_check: bool,
    pub review_fuzz_config: StoredReviewFuzzConfig,
}

#[derive(PartialEq)]
struct DynamicDrConfig<'a> {
    enabled: bool,
    params: &'a [f32],
    weights: &'a [f32],
    avg_drs: &'a [f32],
    retention_min: f32,
    retention_max: f32,
    fsrs_eq_weights: &'a [f32],
    fsrs_eq_drs: &'a [f32],
    fixed_target_weights: &'a [f32],
    fixed_target_drs: &'a [f32],
    clamp: bool,
}

fn dynamic_dr_config(config: &DeckConfig) -> DynamicDrConfig<'_> {
    DynamicDrConfig {
        enabled: config.inner.fsrs_dynamic_desired_retention_enabled,
        params: &config.inner.fsrs_dynamic_desired_retention_params,
        weights: &config.inner.fsrs_dynamic_desired_retention_weights,
        avg_drs: &config.inner.fsrs_dynamic_desired_retention_avg_drs,
        retention_min: config.inner.fsrs_dynamic_desired_retention_min,
        retention_max: config.inner.fsrs_dynamic_desired_retention_max,
        fsrs_eq_weights: &config.inner.fsrs_dynamic_desired_retention_fsrs_eq_weights,
        fsrs_eq_drs: &config.inner.fsrs_dynamic_desired_retention_fsrs_eq_drs,
        fixed_target_weights: &config
            .inner
            .fsrs_dynamic_desired_retention_fixed_target_weights,
        fixed_target_drs: &config.inner.fsrs_dynamic_desired_retention_fixed_target_drs,
        clamp: config.inner.fsrs_dynamic_desired_retention_clamp,
    }
}

impl Collection {
    /// Information required for the deck options screen.
    pub fn get_deck_configs_for_update(
        &mut self,
        deck: DeckId,
    ) -> Result<anki_proto::deck_config::DeckConfigsForUpdate> {
        let mut defaults = DeckConfig::default();
        defaults.inner.fsrs_params_6 = FSRS6_DEFAULT_PARAMETERS.into();
        defaults.inner.fsrs_params_7 = DEFAULT_PARAMETERS.into();
        defaults.inner.fsrs_version = FsrsVersion::Seven as i32;
        let last_optimize = self.get_config_i32(I32ConfigKey::LastFsrsOptimize) as u32;
        let days_since_last_fsrs_optimize = if last_optimize > 0 {
            self.timing_today()?
                .days_elapsed
                .saturating_sub(last_optimize)
        } else {
            0
        };
        let review_fuzz_config = self.stored_review_fuzz_config();
        Ok(anki_proto::deck_config::DeckConfigsForUpdate {
            review_fuzz_enabled: review_fuzz_config.enabled,
            review_fuzz_base: review_fuzz_config.base,
            review_fuzz_factor_short: review_fuzz_config.factor_short,
            review_fuzz_factor_mid: review_fuzz_config.factor_mid,
            review_fuzz_factor_long: review_fuzz_config.factor_long,
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
            load_balancer_enabled: self.get_config_bool(BoolKey::LoadBalancerEnabled),
            fsrs_short_term_with_steps_enabled: self
                .get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled),
            fsrs_learning_queues_disabled: self
                .get_config_bool(BoolKey::FsrsLearningQueuesDisabled),
            fsrs_health_check: self.get_config_bool(BoolKey::FsrsHealthCheck),
            fsrs_legacy_evaluate: self.get_config_bool(BoolKey::FsrsLegacyEvaluate),
            days_since_last_fsrs_optimize,
            rwkv_profile_settings: None,
            rwkv_collection_counts: None,
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
        let mut limits = normal_deck_to_limits(normal, today);
        limits.rwkv_respect_subdeck_minimums = Some(deck.rwkv_respect_subdeck_minimums());

        Ok(CurrentDeck {
            name: deck.human_name(),
            config_id: normal.config_id,
            parent_config_ids: self
                .parent_config_ids(&deck)?
                .into_iter()
                .map(Into::into)
                .collect(),
            subtree_config_ids: self
                .subtree_config_ids(&deck)?
                .into_iter()
                .map(Into::into)
                .collect(),
            limits: Some(limits),
        })
    }

    /// Deck configs used by the selected deck and its descendants.
    fn subtree_config_ids(&self, deck: &Deck) -> Result<HashSet<DeckConfigId>> {
        Ok(self
            .storage
            .child_decks(deck)?
            .iter()
            .chain(iter::once(deck))
            .filter_map(|deck| deck.config_id())
            .collect())
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
        let previous_review_fuzz = self.stored_review_fuzz_config();
        let review_fuzz_changed = previous_review_fuzz != req.review_fuzz_config;
        let today = self
            .timing_today()?
            .next_day_at
            .adding_secs(-24 * 60 * 60)
            .date_string();

        // handle removals first
        for dcid in &req.removed_config_ids {
            self.remove_deck_config_inner(*dcid)?;
            configs_after_update.remove(dcid);
        }

        if req.mode == UpdateDeckConfigsMode::ComputeAllParams {
            self.compute_all_params(&mut req)?;
        }

        let config_count = req.configs.len();
        let mut save_progress = if req.mode == UpdateDeckConfigsMode::ComputeAllParams {
            let mut progress = self.new_progress_handler::<ComputeMemoryProgress>();
            progress.set(ComputeMemoryProgress {
                current_cards: 0,
                total_cards: config_count as u32,
                current_preset: 0,
                total_presets: config_count as u32,
                saving: true,
                presets: req
                    .configs
                    .iter()
                    .map(|config| ComputeMemoryPresetProgress {
                        name: config.name.clone(),
                        total_cards: 1,
                        saving: true,
                        ..Default::default()
                    })
                    .collect(),
                ..Default::default()
            })?;
            Some(progress)
        } else {
            None
        };

        // add/update provided configs
        for (idx, conf) in req.configs.iter_mut().enumerate() {
            if conf.inner.ignore_revlogs_before_date > today {
                today.clone_into(&mut conf.inner.ignore_revlogs_before_date);
            }

            // If the user has provided empty FSRS6 params, zero out any
            // old params as well, so we don't fall back on them, which would
            // be surprising as they're not shown in the GUI.
            if conf.inner.fsrs_params_6.is_empty() {
                conf.inner.fsrs_params_5.clear();
                conf.inner.fsrs_params_4.clear();
            }
            // check the provided parameters are valid before we save them
            FSRS::new(conf.fsrs_params())?;
            self.add_or_update_deck_config(conf)?;
            configs_after_update.insert(conf.id, conf.clone());
            if let Some(progress) = &mut save_progress {
                progress.update(false, |state| {
                    state.current_cards = idx as u32 + 1;
                    state.total_cards = config_count as u32;
                    state.current_preset = idx as u32 + 1;
                    state.total_presets = config_count as u32;
                    state.preset_name.clone_from(&conf.name);
                    state.saving = true;
                    if let Some(preset) = state.presets.get_mut(idx) {
                        preset.current_cards = 1;
                        preset.total_cards = 1;
                        preset.finished = true;
                        preset.saving = true;
                    }
                })?;
            }
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
        let rwkv_subdeck_ids: HashSet<_> =
            if req.limits.rwkv_apply_respect_subdeck_minimums_to_children
                && req.limits.rwkv_respect_subdeck_minimums.is_some()
            {
                let deck = self
                    .storage
                    .get_deck(req.target_deck_id)?
                    .or_not_found(req.target_deck_id)?;
                self.storage
                    .child_decks(&deck)?
                    .into_iter()
                    .map(|deck| deck.id)
                    .collect()
            } else {
                HashSet::new()
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
        if review_fuzz_changed {
            self.set_stored_review_fuzz_config(req.review_fuzz_config)?;
        }
        let mut deck_desired_retention: HashMap<DeckId, f32> = Default::default();
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
                let previous_preset_dr = previous_config.map(|c| c.inner.desired_retention);
                let previous_deck_dr = normal.desired_retention;
                let previous_dr = previous_deck_dr.or(previous_preset_dr);
                let previous_easy_days = previous_config.map(|c| &c.inner.easy_days_percentages);
                let previous_dynamic_dr = previous_config.map(dynamic_dr_config);

                // if a selected (sub)deck, or its old config was removed, update deck to point
                // to new config
                let change_config = selected_deck_ids.contains(&deck.id)
                    || !configs_after_update.contains_key(&previous_config_id);
                let rwkv_setting = (deck.id == req.target_deck_id
                    || rwkv_subdeck_ids.contains(&deck.id))
                .then_some(req.limits.rwkv_respect_subdeck_minimums)
                .flatten();
                let (current_config_id, current_deck_dr) =
                    if change_config || rwkv_setting.is_some() {
                        let mut updated = deck.clone();
                        let config_id = if change_config {
                            updated.normal_mut()?.config_id = selected_config.id.0;
                            update_deck_limits(updated.normal_mut()?, &req.limits, today);
                            selected_config.id
                        } else {
                            previous_config_id
                        };
                        if let Some(enabled) = rwkv_setting {
                            updated.set_rwkv_respect_subdeck_minimums(enabled)?;
                        }
                        self.update_deck_inner(&mut updated, deck, usn)?;
                        (config_id, updated.normal()?.desired_retention)
                    } else {
                        (previous_config_id, previous_deck_dr)
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
                let current_preset_dr = current_config.map(|c| c.inner.desired_retention);
                let current_dr = current_deck_dr.or(current_preset_dr);
                let current_easy_days = current_config.map(|c| &c.inner.easy_days_percentages);
                let current_dynamic_dr = current_config.map(dynamic_dr_config);
                if fsrs_toggled
                    || previous_params != current_params
                    || previous_dr != current_dr
                    || (req.fsrs_reschedule && previous_easy_days != current_easy_days)
                    || (req.fsrs_reschedule && previous_dynamic_dr != current_dynamic_dr)
                    || (req.fsrs_reschedule && review_fuzz_changed)
                {
                    decks_needing_memory_recompute
                        .entry(current_config_id)
                        .or_default()
                        .push(deck_id);
                }
                if let Some(desired_retention) = current_deck_dr {
                    deck_desired_retention.insert(deck_id, desired_retention);
                }
                self.adjust_remaining_steps_in_deck(deck_id, previous_config, current_config, usn)?;
            }
        }

        if !decks_needing_memory_recompute.is_empty() {
            let total_presets = decks_needing_memory_recompute.len() as u32;
            let input: Vec<UpdateMemoryStateEntry> = decks_needing_memory_recompute
                .into_iter()
                .enumerate()
                .map(|(idx, (conf_id, search))| {
                    let config = configs_after_update.get(&conf_id);
                    let params = config.and_then(|c| {
                        if req.fsrs {
                            Some(UpdateMemoryStateRequest {
                                params: c.fsrs_params().to_vec(),
                                preset_desired_retention: c.inner.desired_retention,
                                max_interval: c.inner.maximum_review_interval,
                                review_fuzz_config: req.review_fuzz_config.review_fuzz_config(),
                                reschedule: req.fsrs_reschedule,
                                historical_retention: c.inner.historical_retention,
                                deck_desired_retention: deck_desired_retention.clone(),
                            })
                        } else {
                            None
                        }
                    });
                    Ok(UpdateMemoryStateEntry {
                        req: params,
                        search: Node::Search(SearchNode::DeckIdsWithoutChildren(
                            comma_separated_ids(&search),
                        )),
                        ignore_before: config
                            .map(ignore_revlogs_before_ms_from_config)
                            .unwrap_or(Ok(0.into()))?,
                        preset_name: config
                            .map(|config| config.name.clone())
                            .unwrap_or_else(|| "Preset".to_string()),
                        current_preset: idx as u32 + 1,
                        total_presets,
                    })
                })
                .collect::<Result<_>>()?;
            let input = self.resolve_memory_state_presets(input)?;
            self.update_memory_state(input)?;
        }

        self.set_config_string_inner(StringKey::CardStateCustomizer, &req.card_state_customizer)?;
        self.set_config_bool_inner(
            BoolKey::NewCardsIgnoreReviewLimit,
            req.new_cards_ignore_review_limit,
        )?;
        self.set_config_bool_inner(BoolKey::ApplyAllParentLimits, req.apply_all_parent_limits)?;
        self.set_config_bool_inner(BoolKey::LoadBalancerEnabled, req.load_balancer_enabled)?;
        self.set_config_bool_inner(
            BoolKey::FsrsShortTermWithStepsEnabled,
            req.fsrs_short_term_with_steps_enabled,
        )?;
        self.set_config_bool_inner(
            BoolKey::FsrsLearningQueuesDisabled,
            req.fsrs_learning_queues_disabled,
        )?;
        self.set_config_bool_inner(BoolKey::FsrsHealthCheck, req.fsrs_health_check)?;

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
        self.clear_progress();
        let mut anki_progress = self.new_progress_handler::<ComputeAllParamsProgress>();

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
        let mut jobs = Vec::with_capacity(req.configs.len());
        for (idx, config) in req.configs.iter().enumerate() {
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
            let current_params = config.selected_fsrs_params().to_vec();
            let prepared = self.prepare_compute_params(PrepareComputeParamsInput {
                search: &search,
                ignore_revlogs_before: ignore_revlogs_before_ms,
                current_params: &current_params,
                num_of_relearning_steps,
                include_same_day_reviews: fsrs7_optimize_include_same_day_reviews(config),
                enable_scheduling_penalties: fsrs7_enable_scheduling_penalties(config),
                model_version_override: Some(
                    match FsrsVersion::try_from(config.inner.fsrs_version)
                        .unwrap_or(FsrsVersion::Seven)
                    {
                        FsrsVersion::Seven => ComputeParametersVersion::Fsrs7,
                        _ => ComputeParametersVersion::Fsrs6,
                    },
                ),
                dynamic_desired_retention_enabled: config
                    .inner
                    .fsrs_dynamic_desired_retention_enabled,
                historical_retention: config.inner.historical_retention,
                desired_retention: config.inner.desired_retention,
                dynamic_desired_retention_simulator_options:
                    DynamicDesiredRetentionSimulatorOptions::default(),
            })?;
            anki_progress.check_cancelled()?;
            if prepared.target_counts.total_targets == 0 {
                debug!(preset = config.name, "skipping FSRS preset with no reviews");
            }
            jobs.push(ComputeParamsBatchInput {
                index: idx,
                name: config.name.clone(),
                prepared,
            });
        }

        for output in self.compute_params_batch_with_progress(jobs, anki_progress)? {
            match output.result {
                Ok(params) => {
                    if params.fsrs_items == 0 {
                        continue;
                    }
                    debug!(preset = output.name, params = ?params.params, "optimized FSRS preset");
                    *selected_fsrs_params_mut(&mut req.configs[output.index]) = params.params;
                    if !params.fsrs_dynamic_desired_retention_params.is_empty() {
                        req.configs[output.index]
                            .inner
                            .fsrs_dynamic_desired_retention_params =
                            params.fsrs_dynamic_desired_retention_params;
                        req.configs[output.index]
                            .inner
                            .fsrs_dynamic_desired_retention_weights =
                            params.fsrs_dynamic_desired_retention_weights;
                        req.configs[output.index]
                            .inner
                            .fsrs_dynamic_desired_retention_avg_drs =
                            params.fsrs_dynamic_desired_retention_avg_drs;
                        req.configs[output.index]
                            .inner
                            .fsrs_dynamic_desired_retention_fsrs_eq_weights =
                            params.fsrs_dynamic_desired_retention_fsrs_eq_weights;
                        req.configs[output.index]
                            .inner
                            .fsrs_dynamic_desired_retention_fsrs_eq_drs =
                            params.fsrs_dynamic_desired_retention_fsrs_eq_drs;
                        req.configs[output.index]
                            .inner
                            .fsrs_dynamic_desired_retention_fixed_target_weights =
                            params.fsrs_dynamic_desired_retention_fixed_target_weights;
                        req.configs[output.index]
                            .inner
                            .fsrs_dynamic_desired_retention_fixed_target_drs =
                            params.fsrs_dynamic_desired_retention_fixed_target_drs;
                        req.configs[output.index]
                            .inner
                            .fsrs_dynamic_desired_retention_min =
                            params.fsrs_dynamic_desired_retention_min;
                        req.configs[output.index]
                            .inner
                            .fsrs_dynamic_desired_retention_max =
                            params.fsrs_dynamic_desired_retention_max;
                    }
                }
                Err(AnkiError::Interrupted) => return Err(AnkiError::Interrupted),
                Err(err) => {
                    warn!(preset = output.name, error = %err, "failed to optimize FSRS preset");
                }
            }
        }
        let today = self.timing_today()?.days_elapsed as i32;
        self.set_config_i32_inner(I32ConfigKey::LastFsrsOptimize, today)?;
        Ok(())
    }
}

fn selected_fsrs_params_mut(config: &mut DeckConfig) -> &mut Vec<f32> {
    match FsrsVersion::try_from(config.inner.fsrs_version).unwrap_or(FsrsVersion::Seven) {
        FsrsVersion::Seven => &mut config.inner.fsrs_params_7,
        FsrsVersion::Six => &mut config.inner.fsrs_params_6,
        FsrsVersion::Five => &mut config.inner.fsrs_params_5,
        FsrsVersion::Four => &mut config.inner.fsrs_params_4,
    }
}

pub(crate) fn fsrs7_optimize_include_same_day_reviews(config: &DeckConfig) -> Option<bool> {
    match FsrsVersion::try_from(config.inner.fsrs_version).unwrap_or(FsrsVersion::Seven) {
        FsrsVersion::Seven => {}
        _ => return None,
    }

    serde_json::from_slice::<serde_json::Value>(&config.inner.other)
        .ok()?
        .get("fsrs7IncludeSameDayOptimize")?
        .as_bool()
}

pub(crate) fn fsrs7_enable_scheduling_penalties(config: &DeckConfig) -> bool {
    match FsrsVersion::try_from(config.inner.fsrs_version).unwrap_or(FsrsVersion::Seven) {
        FsrsVersion::Seven => {}
        _ => return false,
    }

    serde_json::from_slice::<serde_json::Value>(&config.inner.other)
        .ok()
        .and_then(|other| {
            other
                .get("fsrs7EnableSchedulingPenalties")
                .and_then(serde_json::Value::as_bool)
        })
        .unwrap_or(false)
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
        desired_retention: deck.desired_retention,
        ..Default::default()
    }
}

fn update_deck_limits(deck: &mut NormalDeck, limits: &Limits, today: u32) {
    deck.review_limit = limits.review;
    deck.new_limit = limits.new;
    update_day_limit(&mut deck.review_limit_today, limits.review_today, today);
    update_day_limit(&mut deck.new_limit_today, limits.new_today, today);
    deck.desired_retention = limits.desired_retention;
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
    use crate::timestamp::TimestampSecs;

    #[test]
    fn fsrs7_optimize_include_same_day_reviews_reads_stored_flag() -> Result<()> {
        let mut config = DeckConfig::default();
        config.inner.fsrs_version = FsrsVersion::Seven as i32;
        config.inner.other = serde_json::to_vec(&serde_json::json!({
            "fsrs7IncludeSameDayOptimize": false,
        }))?;

        assert_eq!(
            fsrs7_optimize_include_same_day_reviews(&config),
            Some(false)
        );

        config.inner.other = serde_json::to_vec(&serde_json::json!({
            "fsrs7IncludeSameDayOptimize": true,
        }))?;
        assert_eq!(fsrs7_optimize_include_same_day_reviews(&config), Some(true));
        Ok(())
    }

    #[test]
    fn fsrs7_optimize_include_same_day_reviews_defaults_when_missing() {
        let mut config = DeckConfig::default();
        config.inner.fsrs_version = FsrsVersion::Seven as i32;

        assert_eq!(fsrs7_optimize_include_same_day_reviews(&config), None);
    }

    #[test]
    fn fsrs7_optimize_include_same_day_reviews_ignores_older_versions() -> Result<()> {
        let mut config = DeckConfig::default();
        config.inner.fsrs_version = FsrsVersion::Six as i32;
        config.inner.other = serde_json::to_vec(&serde_json::json!({
            "fsrs7IncludeSameDayOptimize": false,
        }))?;

        assert_eq!(fsrs7_optimize_include_same_day_reviews(&config), None);
        Ok(())
    }

    #[test]
    fn fsrs7_enable_scheduling_penalties_reads_stored_flag() -> Result<()> {
        let mut config = DeckConfig::default();
        config.inner.fsrs_version = FsrsVersion::Seven as i32;
        config.inner.other = serde_json::to_vec(&serde_json::json!({
            "fsrs7EnableSchedulingPenalties": true,
        }))?;

        assert!(fsrs7_enable_scheduling_penalties(&config));

        config.inner.other = serde_json::to_vec(&serde_json::json!({
            "fsrs7EnableSchedulingPenalties": false,
        }))?;
        assert!(!fsrs7_enable_scheduling_penalties(&config));
        Ok(())
    }

    #[test]
    fn fsrs7_enable_scheduling_penalties_defaults_when_missing() {
        let mut config = DeckConfig::default();
        config.inner.fsrs_version = FsrsVersion::Seven as i32;

        assert!(!fsrs7_enable_scheduling_penalties(&config));
    }

    #[test]
    fn fsrs7_enable_scheduling_penalties_ignores_older_versions() -> Result<()> {
        let mut config = DeckConfig::default();
        config.inner.fsrs_version = FsrsVersion::Six as i32;
        config.inner.other = serde_json::to_vec(&serde_json::json!({
            "fsrs7EnableSchedulingPenalties": true,
        }))?;

        assert!(!fsrs7_enable_scheduling_penalties(&config));
        Ok(())
    }

    #[test]
    fn deck_options_recalculation_uses_matching_presets_for_each_card() -> Result<()> {
        use crate::card::CardQueue;
        use crate::card::CardType;
        use crate::card::FsrsMemoryState;
        use crate::revlog::RevlogEntry;
        use crate::revlog::RevlogId;
        use crate::revlog::RevlogReviewKind;
        use crate::scheduler::fsrs::legacy_fsrs_params;
        use crate::scheduler::fsrs::preset::AddonFsrsPreset;
        use crate::scheduler::fsrs::preset::AddonFsrsVersion;
        use crate::scheduler::fsrs::preset::FsrsPresetOverlay;
        use crate::scheduler::fsrs::preset::FsrsPresetRule;
        use crate::scheduler::fsrs::preset::FSRS_PRESET_OVERLAY_CONFIG_KEY;
        use crate::scheduler::states::fuzz::minimum_review_fuzz_interval;
        use crate::scheduler::states::fuzz::with_review_fuzz;

        fn assert_state(actual: FsrsMemoryState, expected: FsrsMemoryState) {
            for (actual, expected) in [
                (actual.stability, expected.stability),
                (actual.stability_internal, expected.stability_internal),
                (
                    actual.stability_fast.unwrap_or(actual.stability_internal),
                    expected
                        .stability_fast
                        .unwrap_or(expected.stability_internal),
                ),
                (actual.difficulty, expected.difficulty),
            ] {
                assert!((actual - expected).abs() < 0.001, "{actual} != {expected}");
            }
        }

        for reschedule in [false, true] {
            let mut col = Collection::new();
            let timing = col.timing_today()?;
            col.update_default_deck_config(|config| {
                config.fsrs_version = FsrsVersion::Seven as i32;
                config.desired_retention = 0.9;
            });
            let mut deck = col.get_deck(DeckId(1))?.unwrap().as_ref().clone();
            deck.normal_mut()?.desired_retention = Some(0.94);
            col.update_deck(&mut deck)?;
            let mut six = fsrs::FSRS6_DEFAULT_PARAMETERS.to_vec();
            let mut seven = fsrs::DEFAULT_PARAMETERS.to_vec();
            for value in &mut six[..4] {
                *value *= 0.5;
            }
            for value in &mut seven[..4] {
                *value *= 1.7;
            }
            col.set_config(
                FSRS_PRESET_OVERLAY_CONFIG_KEY,
                &FsrsPresetOverlay {
                    presets: vec![
                        AddonFsrsPreset {
                            id: "addon:test:first".into(),
                            name: "First match".into(),
                            fsrs_version: AddonFsrsVersion::Six,
                            params: six,
                            desired_retention: 0.81,
                            historical_retention: 0.71,
                            ..Default::default()
                        },
                        AddonFsrsPreset {
                            id: "addon:test:second".into(),
                            name: "Second match".into(),
                            fsrs_version: AddonFsrsVersion::Seven,
                            params: seven,
                            desired_retention: 0.86,
                            historical_retention: 0.76,
                            ignore_revlogs_before_date: chrono::DateTime::from_timestamp(
                                timing.next_day_at.0 - 18 * 86_400,
                                0,
                            )
                            .unwrap()
                            .format("%Y-%m-%d")
                            .to_string(),
                            ..Default::default()
                        },
                    ],
                    rules: vec![
                        FsrsPresetRule {
                            search: "Front:first prop:ivl=10".into(),
                            preset_id: "addon:test:first".into(),
                        },
                        FsrsPresetRule {
                            search: "(Front:first or Front:second)".into(),
                            preset_id: "addon:test:second".into(),
                        },
                    ],
                    ..Default::default()
                },
            )?;
            let nt = col.get_notetype_by_name("Basic")?.unwrap();
            let mut originals = Vec::new();
            let mut expected = Vec::new();
            for (index, front) in ["first", "second", "fallback"].into_iter().enumerate() {
                let mut note = nt.new_note();
                note.set_field(0, front)?;
                col.add_note(&mut note, DeckId(1))?;
                let mut card = col.storage.all_cards_of_note(note.id)?.remove(0);
                card.ctype = CardType::Review;
                card.queue = CardQueue::Review;
                card.reps = 3;
                card.interval = 10;
                card.due = timing.days_elapsed as i32 + 3;
                col.storage.update_card(&card)?;
                for (days_ago, kind, interval, previous) in [
                    (20, RevlogReviewKind::Learning, 1, 0),
                    (17, RevlogReviewKind::Review, 3, 1),
                    (7, RevlogReviewKind::Review, 10, 3),
                ] {
                    col.storage.add_revlog_entry(
                        &RevlogEntry {
                            id: RevlogId(
                                (timing.next_day_at.0 - days_ago * 86_400 - 3600) * 1000
                                    + index as i64,
                            ),
                            cid: card.id,
                            button_chosen: 3,
                            review_kind: kind,
                            interval,
                            last_interval: previous,
                            ease_factor: 2500,
                            ..Default::default()
                        },
                        false,
                    )?;
                }
                let preset = col.fsrs_preset_for_card(&card)?;
                let computed = col.compute_memory_state(card.id)?;
                let state: FsrsMemoryState = computed.state.unwrap().into();
                expected.push((preset, state, computed.desired_retention, computed.decay));
                originals.push(card);
            }
            // New cards never acquire a memory state during a bulk rewrite.
            let mut new_note = nt.new_note();
            col.add_note(&mut new_note, DeckId(1))?;
            let new_card = col.storage.all_cards_of_note(new_note.id)?.remove(0);
            let output = col.get_deck_configs_for_update(DeckId(1))?;
            let review_fuzz_config = Default::default();
            col.update_deck_configs(UpdateDeckConfigsRequest {
                target_deck_id: DeckId(1),
                configs: output
                    .all_config
                    .into_iter()
                    .map(|c| c.config.unwrap().into())
                    .collect(),
                removed_config_ids: vec![],
                mode: UpdateDeckConfigsMode::Normal,
                card_state_customizer: String::new(),
                limits: Limits {
                    desired_retention: Some(0.94),
                    ..Default::default()
                },
                new_cards_ignore_review_limit: false,
                load_balancer_enabled: false,
                fsrs_short_term_with_steps_enabled: false,
                fsrs_learning_queues_disabled: false,
                apply_all_parent_limits: false,
                fsrs: true,
                fsrs_reschedule: reschedule,
                fsrs_health_check: true,
                review_fuzz_config,
            })?;
            for (original, (preset, state, retention, decay)) in originals.iter().zip(&expected) {
                let card = col.storage.get_card(original.id)?.unwrap();
                assert_state(card.memory_state.unwrap(), *state);
                assert_eq!(card.desired_retention, Some(*retention), "{}", preset.name);
                assert!(
                    (card.decay.unwrap() - decay).abs() < 0.001,
                    "{}",
                    preset.name
                );
                if reschedule {
                    let model = fsrs::FSRS::new(legacy_fsrs_params(&preset.params))?;
                    let interval = model.next_interval_for_state((*state).into(), *retention);
                    let fuzz_config = review_fuzz_config.review_fuzz_config();
                    let expected_interval = with_review_fuzz(
                        original.get_fuzz_factor(true),
                        interval,
                        minimum_review_fuzz_interval(interval, 3, 36500, fuzz_config).max(1),
                        36500,
                        fuzz_config,
                    );
                    assert_eq!(card.interval, expected_interval, "{}", preset.name);
                    assert_eq!(
                        card.due,
                        timing.days_elapsed as i32 - 7 + card.interval as i32
                    );
                } else {
                    assert_eq!(card.interval, original.interval);
                    assert_eq!(card.due, original.due);
                    assert_eq!(col.storage.get_revlog_entries_for_card(card.id)?.len(), 3);
                }
            }
            assert_eq!(col.storage.get_card(new_card.id)?.unwrap(), new_card);
            col.undo()?;
            for original in &originals {
                assert_eq!(
                    col.storage.get_card(original.id)?.unwrap().memory_state,
                    original.memory_state
                );
                assert_eq!(
                    col.storage.get_card(original.id)?.unwrap().interval,
                    original.interval
                );
            }
            col.redo()?;
            for (original, (_, state, retention, _)) in originals.iter().zip(&expected) {
                let card = col.storage.get_card(original.id)?.unwrap();
                assert_state(card.memory_state.unwrap(), *state);
                assert_eq!(card.desired_retention, Some(*retention));
            }
        }
        Ok(())
    }

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
        col.set_config_bool_inner(BoolKey::LoadBalancerEnabled, false)?;
        col.set_config_bool_inner(BoolKey::FsrsShortTermWithStepsEnabled, false)?;
        col.set_config_bool_inner(BoolKey::FsrsLearningQueuesDisabled, false)?;
        col.set_config_bool_inner(BoolKey::FsrsHealthCheck, true)?;

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
            load_balancer_enabled: false,
            fsrs_short_term_with_steps_enabled: false,
            fsrs_learning_queues_disabled: false,
            apply_all_parent_limits: false,
            fsrs: false,
            fsrs_reschedule: false,
            fsrs_health_check: true,
            review_fuzz_config: Default::default(),
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
    fn current_deck_reports_subtree_config_ids() -> Result<()> {
        let mut col = Collection::new();
        let mut child_config = DeckConfig {
            name: "Child preset".into(),
            ..Default::default()
        };
        col.add_or_update_deck_config(&mut child_config)?;

        let mut child = col.get_or_create_normal_deck("Default::child")?;
        child.normal_mut()?.config_id = child_config.id.0;
        col.add_or_update_deck(&mut child)?;

        let output = col.get_deck_configs_for_update(DeckId(1))?;
        let subtree_config_ids: HashSet<_> = output
            .current_deck
            .unwrap()
            .subtree_config_ids
            .into_iter()
            .collect();

        assert_eq!(
            subtree_config_ids,
            HashSet::from([DeckConfigId(1).0, child_config.id.0])
        );
        Ok(())
    }

    #[test]
    fn rwkv_subdeck_option_applies_only_to_requested_decks() -> Result<()> {
        let mut col = Collection::new();
        let parent = col.get_or_create_normal_deck("Language")?;
        let child = col.get_or_create_normal_deck("Language::Japanese")?;
        let grandchild = col.get_or_create_normal_deck("Language::Japanese::Vocab")?;
        let outside = col.get_or_create_normal_deck("Outside")?;
        assert_eq!(parent.config_id(), child.config_id());
        assert_eq!(child.config_id(), outside.config_id());

        let output = col.get_deck_configs_for_update(parent.id)?;
        let current = output.current_deck.unwrap();
        assert_eq!(
            current
                .limits
                .as_ref()
                .unwrap()
                .rwkv_respect_subdeck_minimums,
            Some(false)
        );
        let config = output
            .all_config
            .into_iter()
            .find_map(|entry| entry.config.filter(|config| config.id == current.config_id))
            .unwrap();
        let mut request = anki_proto::deck_config::UpdateDeckConfigsRequest {
            target_deck_id: parent.id.0,
            configs: vec![config],
            limits: current.limits,
            ..Default::default()
        };
        let limits = request.limits.as_mut().unwrap();
        limits.rwkv_respect_subdeck_minimums = Some(true);
        col.update_deck_configs(request.clone().into())?;
        assert!(col
            .get_deck(parent.id)?
            .unwrap()
            .rwkv_respect_subdeck_minimums());
        assert!(!col
            .get_deck(child.id)?
            .unwrap()
            .rwkv_respect_subdeck_minimums());
        assert!(!col
            .get_deck(outside.id)?
            .unwrap()
            .rwkv_respect_subdeck_minimums());

        request
            .limits
            .as_mut()
            .unwrap()
            .rwkv_apply_respect_subdeck_minimums_to_children = true;
        col.update_deck_configs(request.clone().into())?;
        for deck in [&child, &grandchild] {
            let saved = col.get_deck(deck.id)?.unwrap();
            assert!(saved.rwkv_respect_subdeck_minimums());
            assert_eq!(saved.config_id(), deck.config_id());
        }
        assert!(!col
            .get_deck(outside.id)?
            .unwrap()
            .rwkv_respect_subdeck_minimums());

        col.undo()?;
        assert!(col
            .get_deck(parent.id)?
            .unwrap()
            .rwkv_respect_subdeck_minimums());
        assert!(!col
            .get_deck(child.id)?
            .unwrap()
            .rwkv_respect_subdeck_minimums());
        assert!(!col
            .get_deck(grandchild.id)?
            .unwrap()
            .rwkv_respect_subdeck_minimums());

        request
            .limits
            .as_mut()
            .unwrap()
            .rwkv_respect_subdeck_minimums = None;
        col.update_deck_configs(request.into())?;
        assert!(col
            .get_deck(parent.id)?
            .unwrap()
            .rwkv_respect_subdeck_minimums());
        assert!(!col
            .get_deck(child.id)?
            .unwrap()
            .rwkv_respect_subdeck_minimums());
        Ok(())
    }

    #[test]
    fn fsrs7_params_are_preserved_on_update() -> Result<()> {
        let mut col = Collection::new();
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
            load_balancer_enabled: false,
            fsrs_short_term_with_steps_enabled: false,
            fsrs_learning_queues_disabled: false,
            apply_all_parent_limits: false,
            fsrs: false,
            fsrs_reschedule: false,
            fsrs_health_check: true,
            review_fuzz_config: Default::default(),
        };
        let expected = vec![0.1, 0.2, 0.3];
        input.configs[0].inner.fsrs_params_7 = expected.clone();
        col.update_deck_configs(input)?;

        let stored = col.get_deck_config(DeckConfigId(1), true)?.unwrap();
        assert_eq!(stored.inner.fsrs_params_7, expected);
        Ok(())
    }

    #[test]
    fn incompatible_fsrs7_params_use_fsrs7_defaults_on_update() -> Result<()> {
        let mut col = Collection::new();
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
            load_balancer_enabled: false,
            fsrs_short_term_with_steps_enabled: false,
            fsrs_learning_queues_disabled: false,
            apply_all_parent_limits: false,
            fsrs: false,
            fsrs_reschedule: false,
            fsrs_health_check: true,
            review_fuzz_config: Default::default(),
        };
        let expected = vec![
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
            1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        ];
        input.configs[0].inner.fsrs_params_6 = vec![1.0; 21];
        input.configs[0].inner.fsrs_params_7 = expected.clone();
        col.update_deck_configs(input)?;

        let stored = col.get_deck_config(DeckConfigId(1), true)?.unwrap();
        assert_eq!(stored.inner.fsrs_params_7, expected);
        assert_eq!(stored.fsrs_params(), fsrs::DEFAULT_PARAMETERS);
        Ok(())
    }

    #[test]
    fn valid_35_param_fsrs7_is_preferred_on_update() -> Result<()> {
        let mut col = Collection::new();
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
            load_balancer_enabled: false,
            fsrs_short_term_with_steps_enabled: false,
            fsrs_learning_queues_disabled: false,
            apply_all_parent_limits: false,
            fsrs: false,
            fsrs_reschedule: false,
            fsrs_health_check: true,
            review_fuzz_config: Default::default(),
        };
        let expected: Vec<f32> = (0..34).map(|i| 0.1 + i as f32 * 0.01).collect();
        input.configs[0].inner.fsrs_params_6 = vec![1.0; 21];
        input.configs[0].inner.fsrs_params_7 = expected.clone();
        col.update_deck_configs(input)?;

        let stored = col.get_deck_config(DeckConfigId(1), true)?.unwrap();
        assert_eq!(stored.fsrs_params(), &expected);
        Ok(())
    }

    #[test]
    fn fsrs_short_term_with_steps_flag_roundtrip() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool_inner(BoolKey::FsrsShortTermWithStepsEnabled, true)?;
        col.set_config_bool_inner(BoolKey::FsrsLearningQueuesDisabled, true)?;
        let output = col.get_deck_configs_for_update(DeckId(1))?;
        assert!(output.fsrs_short_term_with_steps_enabled);
        assert!(output.fsrs_learning_queues_disabled);

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
            load_balancer_enabled: false,
            fsrs_short_term_with_steps_enabled: false,
            fsrs_learning_queues_disabled: false,
            apply_all_parent_limits: false,
            fsrs: false,
            fsrs_reschedule: false,
            fsrs_health_check: true,
            review_fuzz_config: Default::default(),
        };
        col.update_deck_configs(input.clone())?;
        assert!(!col.get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled));
        assert!(!col.get_config_bool(BoolKey::FsrsLearningQueuesDisabled));

        input.fsrs_short_term_with_steps_enabled = true;
        input.fsrs_learning_queues_disabled = true;
        col.update_deck_configs(input)?;
        assert!(col.get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled));
        assert!(col.get_config_bool(BoolKey::FsrsLearningQueuesDisabled));
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

    #[test]
    fn should_clamp_ignore_revlogs_before_date_to_today() {
        let mut col = Collection::new();
        let today = col
            .timing_today()
            .unwrap()
            .next_day_at
            .adding_secs(-24 * 60 * 60)
            .date_string();
        let output = col.get_deck_configs_for_update(DeckId(1)).unwrap();
        let base_input = UpdateDeckConfigsRequest {
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
            load_balancer_enabled: false,
            fsrs_short_term_with_steps_enabled: false,
            fsrs_learning_queues_disabled: false,
            apply_all_parent_limits: false,
            fsrs: false,
            fsrs_reschedule: false,
            fsrs_health_check: true,
            review_fuzz_config: Default::default(),
        };

        // future date should be clamped to today
        let mut future_input = base_input.clone();
        future_input.configs[0].inner.ignore_revlogs_before_date =
            TimestampSecs::now().adding_secs(86_400).date_string();
        assert!(col.update_deck_configs(future_input).is_ok());

        let updated = col.get_deck_configs_for_update(DeckId(1)).unwrap();
        let updated_config: DeckConfig = updated.all_config[0].config.clone().unwrap().into();
        assert_eq!(updated_config.inner.ignore_revlogs_before_date, today);

        // past dates should be left unchanged
        let past_date = TimestampSecs::now().adding_secs(-86_400).date_string();
        let mut past_input = base_input.clone();
        past_input.configs[0].inner.ignore_revlogs_before_date = past_date.clone();
        assert!(col.update_deck_configs(past_input).is_ok());
        let updated2 = col.get_deck_configs_for_update(DeckId(1)).unwrap();
        let updated_config2: DeckConfig = updated2.all_config[0].config.clone().unwrap().into();
        assert_eq!(updated_config2.inner.ignore_revlogs_before_date, past_date);

        // today's date should also be left unchanged
        let mut today_input = base_input.clone();
        today_input.configs[0].inner.ignore_revlogs_before_date = today.clone();
        assert!(col.update_deck_configs(today_input).is_ok());
        let updated3 = col.get_deck_configs_for_update(DeckId(1)).unwrap();
        let updated_config3: DeckConfig = updated3.all_config[0].config.clone().unwrap().into();
        assert_eq!(updated_config3.inner.ignore_revlogs_before_date, today);

        // empty dates should be saved as is and handled by
        // ignore_revlogs_before_date_to_ms
        let mut today_input = base_input;
        today_input.configs[0].inner.ignore_revlogs_before_date = "".to_string();
        assert!(col.update_deck_configs(today_input).is_ok());
        let updated3 = col.get_deck_configs_for_update(DeckId(1)).unwrap();
        let updated_config3: DeckConfig = updated3.all_config[0].config.clone().unwrap().into();
        assert_eq!(
            updated_config3.inner.ignore_revlogs_before_date,
            "".to_string()
        );
    }
}
