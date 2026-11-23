// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use std::collections::HashMap;
use std::collections::HashSet;
use std::sync::Arc;
use std::sync::Mutex;
use std::time::Instant;

use anki_proto::deck_config::deck_config::config::ReviewCardOrder;
use anki_proto::scheduler::SimulateFsrsPresetWorkload;
use anki_proto::scheduler::SimulateFsrsReviewRequest;
use anki_proto::scheduler::SimulateFsrsReviewResponse;
use anki_proto::scheduler::SimulateFsrsWorkloadResponse;
use fsrs::simulate;
use fsrs::simulate_summary;
use fsrs::simulate_summary_with_card_update_and_event_fn;
use fsrs::simulate_summary_with_card_update_fn;
use fsrs::simulate_with_card_update_fn;
use fsrs::CostAdrPolicy;
use fsrs::SimulationEvent;
use fsrs::SimulatorCardUpdateFn;
use fsrs::SimulatorCardUpdatePhase;
use fsrs::SimulatorConfig;
use fsrs::SimulatorEventFn;
use fsrs::DEFAULT_PARAMETERS;
use fsrs::FSRS;
use itertools::Itertools;
use rayon::prelude::*;

use super::legacy_fsrs_params;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::card::FsrsMemoryState;
use crate::prelude::*;
use crate::scheduler::fsrs::dynamic_desired_retention::DynamicDesiredRetention;
use crate::scheduler::fsrs::dynamic_desired_retention::DynamicDesiredRetentionFields;
use crate::scheduler::fsrs::memory_state::memory_state_from_sm2_with_params;
use crate::scheduler::fsrs::preset::FsrsPreset;
use crate::scheduler::fsrs::preset::FsrsPresetId;
use crate::scheduler::fsrs::preset::FsrsPresetSimulatorRule;
use crate::scheduler::fsrs::review_time_model::build_help_me_decide_review_time_model_from_revlogs;
use crate::scheduler::fsrs::review_time_model::install_review_time_cost_fn;
use crate::scheduler::fsrs::review_time_model::HelpMeDecideReviewTimeModel;
use crate::scheduler::fsrs::review_time_model::R_BUCKET_COUNT;
use crate::scheduler::fsrs::review_time_model::S_BUCKET_COUNT_FOR_UI;
use crate::scheduler::rwkv::relative_overdueness;
use crate::scheduler::states::fuzz::ReviewFuzzConfig;
use crate::scheduler::states::load_balancer::parse_easy_days_percentages;
use crate::search::SortMode;

const WORKLOAD_MIN_DR: u32 = 30;
const WORKLOAD_MAX_DR: u32 = 99;

fn create_review_priority_fn(
    review_order: ReviewCardOrder,
    _deck_size: usize,
) -> Option<fsrs::ReviewPriorityFn> {
    match review_order {
        ReviewCardOrder::Day | ReviewCardOrder::DayThenDeck | ReviewCardOrder::DeckThenDay => {
            Some(fsrs::ReviewPriorityFn::new(|card| {
                stable_card_hash(card.id)
            }))
        }
        ReviewCardOrder::IntervalsAscending => Some(fsrs::ReviewPriorityFn::new(|card| {
            scaled_i32(card.interval, 100.0)
        })),
        ReviewCardOrder::IntervalsDescending => Some(fsrs::ReviewPriorityFn::new(|card| {
            -scaled_i32(card.interval, 100.0)
        })),
        ReviewCardOrder::EaseAscending => Some(fsrs::ReviewPriorityFn::new(|card| {
            -scaled_i32(card.difficulty, 100.0)
        })),
        ReviewCardOrder::EaseDescending => Some(fsrs::ReviewPriorityFn::new(|card| {
            scaled_i32(card.difficulty, 100.0)
        })),
        ReviewCardOrder::RetrievabilityAscending => Some(fsrs::ReviewPriorityFn::new(|card| {
            scaled_i32(card.retrievability(), 1000.0)
        })),
        ReviewCardOrder::RetrievabilityDescending => Some(fsrs::ReviewPriorityFn::new(|card| {
            -scaled_i32(card.retrievability(), 1000.0)
        })),
        ReviewCardOrder::RelativeOverdueness => Some(fsrs::ReviewPriorityFn::new(|card| {
            scaled_i32(
                relative_overdueness(card.retrievability(), card.desired_retention),
                1000.0,
            )
        })),
        ReviewCardOrder::Random => Some(fsrs::ReviewPriorityFn::new(|card| {
            stable_card_hash(card.id)
        })),
        ReviewCardOrder::Added => Some(fsrs::ReviewPriorityFn::new(|card| {
            card.id.clamp(i32::MIN as i64, i32::MAX as i64) as i32
        })),
        ReviewCardOrder::ReverseAdded => Some(fsrs::ReviewPriorityFn::new(|card| {
            -(card.id.clamp(i32::MIN as i64, i32::MAX as i64) as i32)
        })),
    }
}

fn scaled_i32(value: f32, scale: f32) -> i32 {
    if value.is_finite() {
        (value * scale)
            .round()
            .clamp(i32::MIN as f32, i32::MAX as f32) as i32
    } else {
        0
    }
}

fn stable_card_hash(id: i64) -> i32 {
    let mut value = id as u64;
    value ^= value >> 33;
    value = value.wrapping_mul(0xff51afd7ed558ccd);
    value ^= value >> 33;
    value = value.wrapping_mul(0xc4ceb9fe1a85ec53);
    value ^= value >> 33;
    (value >> 32) as i32
}

pub(crate) fn is_included_card(c: &Card) -> bool {
    c.queue != CardQueue::Suspended
        && c.queue != CardQueue::PreviewRepeat
        && c.ctype != CardType::New
}

fn help_me_decide_timing_line(
    total_elapsed_ms: u128,
    review_time_model_elapsed_ms: u128,
    workload_sweep_elapsed_ms: u128,
) -> String {
    format!(
        "[help-me-decide timing] total={}ms review_time_model={}ms workload_sweep={}ms",
        total_elapsed_ms, review_time_model_elapsed_ms, workload_sweep_elapsed_ms
    )
}

#[derive(Clone)]
struct SimulationDynamicDesiredRetention {
    dynamic_desired_retention: DynamicDesiredRetention,
    policy: CostAdrPolicy,
}

#[derive(Clone)]
struct SimulationPreset {
    name: String,
    parameters: Option<Arc<Vec<f32>>>,
    dynamic_desired_retention: Option<SimulationDynamicDesiredRetention>,
}

#[derive(Clone)]
struct SimulationPresetForTarget {
    name: String,
    parameters: Option<Arc<Vec<f32>>>,
    dynamic_desired_retention: Option<SimulationDynamicDesiredRetentionForTarget>,
}

#[derive(Clone)]
struct SimulationDynamicDesiredRetentionForTarget {
    policy: CostAdrPolicy,
    cost_weight: f32,
}

#[derive(Clone)]
struct SimulationPresetRoute {
    card_ids: Option<HashSet<i64>>,
    min_reps: Option<u32>,
    max_reps: Option<u32>,
    min_interval_days: Option<f32>,
    max_interval_days: Option<f32>,
    preset: SimulationPreset,
}

struct SimulationPresetRouter {
    fallback: SimulationPreset,
    presets_by_card: HashMap<i64, SimulationPreset>,
    routes: Vec<SimulationPresetRoute>,
}

fn simulation_dynamic_desired_retention(
    req: &SimulateFsrsReviewRequest,
) -> Result<Option<SimulationDynamicDesiredRetention>> {
    if !req.simulate_dynamic_desired_retention {
        return Ok(None);
    }
    if req.fsrs_dynamic_desired_retention_params.is_empty() {
        return Ok(None);
    }

    let dynamic_desired_retention =
        DynamicDesiredRetention::from_fields(DynamicDesiredRetentionFields {
            policy_params: req.fsrs_dynamic_desired_retention_params.clone(),
            calibration_weights: req.fsrs_dynamic_desired_retention_weights.clone(),
            calibration_avg_drs: req.fsrs_dynamic_desired_retention_avg_drs.clone(),
            fsrs_equivalent_weights: req.fsrs_dynamic_desired_retention_fsrs_eq_weights.clone(),
            fsrs_equivalent_drs: req.fsrs_dynamic_desired_retention_fsrs_eq_drs.clone(),
            fixed_target_weights: req
                .fsrs_dynamic_desired_retention_fixed_target_weights
                .clone(),
            fixed_target_drs: req.fsrs_dynamic_desired_retention_fixed_target_drs.clone(),
            retention_min: req.fsrs_dynamic_desired_retention_min,
            retention_max: req.fsrs_dynamic_desired_retention_max,
            clamp_target: req.fsrs_dynamic_desired_retention_clamp,
            max_interval_days: Some(req.max_interval as f32),
        })?;
    let policy = dynamic_desired_retention.policy()?;

    Ok(Some(SimulationDynamicDesiredRetention {
        dynamic_desired_retention,
        policy,
    }))
}

impl SimulationDynamicDesiredRetention {
    fn from_dynamic_desired_retention(
        dynamic_desired_retention: DynamicDesiredRetention,
    ) -> Result<Self> {
        let policy = dynamic_desired_retention.policy()?;

        Ok(Self {
            dynamic_desired_retention,
            policy,
        })
    }

    fn for_target(
        &self,
        desired_retention: f32,
    ) -> Result<Option<SimulationDynamicDesiredRetentionForTarget>> {
        let Some(target) = self
            .dynamic_desired_retention
            .scheduling_target(desired_retention)?
        else {
            return Ok(None);
        };
        let cost_weight = self
            .dynamic_desired_retention
            .cost_weight_for_average_dr(target)?;
        Ok(Some(SimulationDynamicDesiredRetentionForTarget {
            policy: self.policy.clone(),
            cost_weight,
        }))
    }
}

impl SimulationPreset {
    fn for_target(&self, desired_retention: f32) -> Result<SimulationPresetForTarget> {
        Ok(SimulationPresetForTarget {
            name: self.name.clone(),
            parameters: self.parameters.clone(),
            dynamic_desired_retention: self
                .dynamic_desired_retention
                .as_ref()
                .map(|dynamic| dynamic.for_target(desired_retention))
                .transpose()?
                .flatten(),
        })
    }
}

impl SimulationPresetForTarget {
    fn apply_before_memory_update(&self, card: &mut fsrs::Card, desired_retention: f32) {
        if let Some(parameters) = &self.parameters {
            card.parameters = parameters.clone();
        }
        card.desired_retention = desired_retention;
    }

    fn desired_retention_after_memory_update(
        &self,
        card: &fsrs::Card,
        desired_retention: f32,
    ) -> f32 {
        self.dynamic_desired_retention
            .as_ref()
            .map(|dynamic| {
                dynamic.policy.evaluate_retention(
                    card.stability,
                    card.difficulty,
                    dynamic.cost_weight,
                )
            })
            .unwrap_or(desired_retention)
    }

    fn apply_after_memory_update(&self, card: &mut fsrs::Card, desired_retention: f32) {
        if let Some(parameters) = &self.parameters {
            card.parameters = parameters.clone();
        }
        card.desired_retention =
            self.desired_retention_after_memory_update(card, desired_retention);
    }
}

impl SimulationPresetRoute {
    fn matches_card(&self, card_id: i64) -> bool {
        self.card_ids
            .as_ref()
            .is_none_or(|card_ids| card_ids.contains(&card_id))
    }

    fn matches_reps(&self, reps: u32) -> bool {
        self.min_reps.is_none_or(|min| reps >= min) && self.max_reps.is_none_or(|max| reps <= max)
    }

    fn matches_interval(&self, interval: f32) -> bool {
        if (self.min_interval_days.is_some() || self.max_interval_days.is_some())
            && !interval.is_finite()
        {
            return false;
        }
        self.min_interval_days.is_none_or(|min| interval >= min)
            && self.max_interval_days.is_none_or(|max| interval <= max)
    }

    fn matches_state(&self, card_id: i64, reps: u32, interval: f32) -> bool {
        self.matches_card(card_id) && self.matches_reps(reps) && self.matches_interval(interval)
    }

    fn for_target(&self, desired_retention: f32) -> Result<SimulationPresetRouteForTarget> {
        Ok(SimulationPresetRouteForTarget {
            card_ids: self.card_ids.clone(),
            min_reps: self.min_reps,
            max_reps: self.max_reps,
            min_interval_days: self.min_interval_days,
            max_interval_days: self.max_interval_days,
            preset: self.preset.for_target(desired_retention)?,
        })
    }
}

#[derive(Clone)]
struct SimulationPresetRouteForTarget {
    card_ids: Option<HashSet<i64>>,
    min_reps: Option<u32>,
    max_reps: Option<u32>,
    min_interval_days: Option<f32>,
    max_interval_days: Option<f32>,
    preset: SimulationPresetForTarget,
}

impl SimulationPresetRouteForTarget {
    fn matches(&self, card: &fsrs::Card) -> bool {
        if (self.min_interval_days.is_some() || self.max_interval_days.is_some())
            && !card.interval.is_finite()
        {
            return false;
        }
        self.card_ids
            .as_ref()
            .is_none_or(|card_ids| card_ids.contains(&card.id))
            && self.min_reps.is_none_or(|min| card.reps >= min)
            && self.max_reps.is_none_or(|max| card.reps <= max)
            && self
                .min_interval_days
                .is_none_or(|min| card.interval >= min)
            && self
                .max_interval_days
                .is_none_or(|max| card.interval <= max)
    }
}

impl SimulationPresetRouter {
    fn preset_for_card_state(&self, card_id: i64, reps: u32, interval: f32) -> &SimulationPreset {
        self.routes
            .iter()
            .find(|route| route.matches_state(card_id, reps, interval))
            .map(|route| &route.preset)
            .or_else(|| self.presets_by_card.get(&card_id))
            .unwrap_or(&self.fallback)
    }

    fn parameters_for_card_state(
        &self,
        card_id: i64,
        reps: u32,
        interval: f32,
    ) -> Option<Arc<Vec<f32>>> {
        self.preset_for_card_state(card_id, reps, interval)
            .parameters
            .clone()
    }

    fn active_preset_name_for_card(&self, card: &fsrs::Card) -> &str {
        self.routes
            .iter()
            .find(|route| route.matches_state(card.id, card.reps, card.interval))
            .map(|route| route.preset.name.as_str())
            .or_else(|| {
                self.presets_by_card
                    .get(&card.id)
                    .map(|preset| preset.name.as_str())
            })
            .unwrap_or(&self.fallback.name)
    }

    fn card_update_fn(&self, desired_retention: f32) -> Result<SimulatorCardUpdateFn> {
        let fallback = self.fallback.for_target(desired_retention)?;
        let presets_by_card = self
            .presets_by_card
            .iter()
            .map(|(card_id, preset)| Ok((*card_id, preset.for_target(desired_retention)?)))
            .collect::<Result<HashMap<_, _>>>()?;
        let routes = self
            .routes
            .iter()
            .map(|route| route.for_target(desired_retention))
            .collect::<Result<Vec<_>>>()?;
        Ok(SimulatorCardUpdateFn::new(move |card, phase| {
            let preset = routes
                .iter()
                .find(|route| route.matches(card))
                .map(|route| &route.preset)
                .or_else(|| presets_by_card.get(&card.id))
                .unwrap_or(&fallback);
            match phase {
                SimulatorCardUpdatePhase::BeforeMemoryUpdate => {
                    preset.apply_before_memory_update(card, desired_retention);
                }
                SimulatorCardUpdatePhase::AfterMemoryUpdate => {
                    preset.apply_after_memory_update(card, desired_retention);
                }
            }
        }))
    }

    fn card_update_fn_for_simulation(
        &self,
        desired_retention: f32,
    ) -> Result<Option<SimulatorCardUpdateFn>> {
        if self.routes.is_empty() {
            self.static_dynamic_desired_retention_card_update_fn(desired_retention)
        } else {
            self.card_update_fn(desired_retention).map(Some)
        }
    }

    fn static_dynamic_desired_retention_card_update_fn(
        &self,
        desired_retention: f32,
    ) -> Result<Option<SimulatorCardUpdateFn>> {
        let fallback = self.fallback.for_target(desired_retention)?;
        let presets_by_card = self
            .presets_by_card
            .iter()
            .map(|(card_id, preset)| Ok((*card_id, preset.for_target(desired_retention)?)))
            .collect::<Result<HashMap<_, _>>>()?;
        if fallback.dynamic_desired_retention.is_none()
            && presets_by_card
                .values()
                .all(|preset| preset.dynamic_desired_retention.is_none())
        {
            return Ok(None);
        }

        Ok(Some(SimulatorCardUpdateFn::new(
            move |card, phase| match phase {
                SimulatorCardUpdatePhase::BeforeMemoryUpdate => {
                    card.desired_retention = desired_retention;
                }
                SimulatorCardUpdatePhase::AfterMemoryUpdate => {
                    let preset = presets_by_card.get(&card.id).unwrap_or(&fallback);
                    card.desired_retention =
                        preset.desired_retention_after_memory_update(card, desired_retention);
                }
            },
        )))
    }
}

fn simulation_fallback_preset(req: &SimulateFsrsReviewRequest) -> Result<SimulationPreset> {
    Ok(SimulationPreset {
        name: if req.workload_preset_label.is_empty() {
            "Preset".to_string()
        } else {
            req.workload_preset_label.clone()
        },
        parameters: None,
        dynamic_desired_retention: simulation_dynamic_desired_retention(req)?,
    })
}

fn simulation_preset_from_fsrs_preset(
    preset: FsrsPreset,
    max_interval: u32,
    apply_dynamic_desired_retention: bool,
) -> Result<SimulationPreset> {
    let dynamic_desired_retention = if apply_dynamic_desired_retention {
        preset
            .dynamic_desired_retention
            .map(|dynamic| {
                SimulationDynamicDesiredRetention::from_dynamic_desired_retention(
                    dynamic.with_max_interval_days(Some(max_interval as f32)),
                )
            })
            .transpose()?
    } else {
        None
    };
    Ok(SimulationPreset {
        name: preset.name,
        parameters: Some(Arc::new(normalized_fsrs_parameters(&preset.params)?)),
        dynamic_desired_retention,
    })
}

fn simulation_preset_route(
    rule: FsrsPresetSimulatorRule,
    preset: FsrsPreset,
    max_interval: u32,
    card_ids: Option<HashSet<i64>>,
    apply_dynamic_desired_retention: bool,
) -> Result<SimulationPresetRoute> {
    Ok(SimulationPresetRoute {
        card_ids,
        min_reps: rule.min_reps,
        max_reps: rule.max_reps,
        min_interval_days: rule.min_interval_days,
        max_interval_days: rule.max_interval_days,
        preset: simulation_preset_from_fsrs_preset(
            preset,
            max_interval,
            apply_dynamic_desired_retention,
        )?,
    })
}

fn simulation_addon_preset_for_card(
    card_id: CardId,
    preset: Arc<FsrsPreset>,
    max_interval: u32,
    apply_dynamic_desired_retention: bool,
) -> Result<Option<(i64, SimulationPreset)>> {
    if !matches!(preset.id, FsrsPresetId::Addon(_)) {
        return Ok(None);
    }

    Ok(Some((
        card_id.0,
        simulation_preset_from_fsrs_preset(
            Arc::unwrap_or_clone(preset),
            max_interval,
            apply_dynamic_desired_retention,
        )?,
    )))
}

impl Collection {
    fn simulation_route_card_ids(
        &mut self,
        rule: &FsrsPresetSimulatorRule,
        included_card_ids: &HashSet<CardId>,
        search_cache: &mut HashMap<String, HashSet<i64>>,
    ) -> Result<Option<HashSet<i64>>> {
        let Some(search) = rule.search.as_ref() else {
            return Ok(None);
        };
        if let Some(card_ids) = search_cache.get(search) {
            return Ok(Some(card_ids.clone()));
        }

        let card_ids = self
            .search_cards(search, SortMode::NoOrder)?
            .into_iter()
            .filter(|card_id| included_card_ids.contains(card_id))
            .map(|card_id| card_id.0)
            .collect::<HashSet<_>>();
        search_cache.insert(search.clone(), card_ids.clone());
        Ok(Some(card_ids))
    }

    fn simulation_preset_router(
        &mut self,
        req: &SimulateFsrsReviewRequest,
        cards: &[Card],
    ) -> Result<Option<SimulationPresetRouter>> {
        let apply_dynamic_desired_retention = req.simulate_dynamic_desired_retention;
        let mut fallback = simulation_fallback_preset(req)?;
        let presets_by_card = self
            .fsrs_presets_for_cards(cards)?
            .into_iter()
            .filter_map(|(card_id, preset)| {
                simulation_addon_preset_for_card(
                    card_id,
                    preset,
                    req.max_interval,
                    apply_dynamic_desired_retention,
                )
                .transpose()
            })
            .collect::<Result<HashMap<_, _>>>()?;
        let included_card_ids = cards.iter().map(|card| card.id).collect::<HashSet<_>>();
        let mut search_cache = HashMap::new();
        let mut routes = Vec::new();
        for (rule, preset) in self.fsrs_preset_simulator_rules()? {
            let card_ids =
                self.simulation_route_card_ids(&rule, &included_card_ids, &mut search_cache)?;
            routes.push(simulation_preset_route(
                rule,
                preset,
                req.max_interval,
                card_ids,
                apply_dynamic_desired_retention,
            )?);
        }
        if !presets_by_card.is_empty() || !routes.is_empty() {
            fallback.parameters = Some(Arc::new(normalized_fsrs_parameters(&req.params)?));
        }
        if fallback.dynamic_desired_retention.is_none()
            && presets_by_card.is_empty()
            && routes.is_empty()
        {
            return Ok(None);
        }

        Ok(Some(SimulationPresetRouter {
            fallback,
            presets_by_card,
            routes,
        }))
    }

    fn build_help_me_decide_review_time_model(
        &mut self,
        req: &SimulateFsrsReviewRequest,
        default_review_costs: [f32; 4],
    ) -> Result<HelpMeDecideReviewTimeModel> {
        let next_day_at = self.timing_today()?.next_day_at;
        let guard = self.search_cards_into_table(&req.search, SortMode::NoOrder)?;
        let revlogs = guard
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()?;
        drop(guard);
        build_help_me_decide_review_time_model_from_revlogs(
            &revlogs,
            &req.params,
            next_day_at,
            req.help_me_decide_enforce_monotonic_success_grade_probs
                .unwrap_or(false),
            default_review_costs,
        )
    }

    pub fn simulate_request_to_config(
        &mut self,
        req: &SimulateFsrsReviewRequest,
    ) -> Result<(SimulatorConfig, Vec<fsrs::Card>)> {
        let (config, cards, _) = self.simulate_request_to_config_inner(req, false)?;
        Ok((config, cards))
    }

    fn simulate_request_to_config_inner(
        &mut self,
        req: &SimulateFsrsReviewRequest,
        with_preset_router: bool,
    ) -> Result<(
        SimulatorConfig,
        Vec<fsrs::Card>,
        Option<SimulationPresetRouter>,
    )> {
        let guard = self.search_cards_into_table(&req.search, SortMode::NoOrder)?;
        let revlogs = guard
            .col
            .storage
            .get_revlog_entries_for_searched_cards_in_card_order()?;
        let mut cards = guard.col.storage.all_searched_cards()?;
        drop(guard);
        // calculate any missing memory state
        for c in &mut cards {
            if is_included_card(c) && c.memory_state.is_none() {
                self.compute_and_update_memory_state(c)?;
            }
        }
        let preset_router = if with_preset_router {
            let included_cards = cards
                .iter()
                .filter(|card| is_included_card(card))
                .cloned()
                .collect_vec();
            self.simulation_preset_router(req, &included_cards)?
        } else {
            None
        };
        let days_elapsed = self.timing_today().unwrap().days_elapsed as i32;
        let new_cards = cards
            .iter()
            .filter(|c| c.ctype == CardType::New && c.queue != CardQueue::Suspended)
            .count()
            + req.deck_size as usize;
        let filled_params = normalized_fsrs_parameters(&req.params)?;
        let shared_parameters = Arc::new(filled_params);
        let mut converted_cards = cards
            .into_iter()
            .filter(is_included_card)
            .filter_map(|mut c| {
                let card_parameters = preset_router
                    .as_ref()
                    .and_then(|router| {
                        router.parameters_for_card_state(c.id.0, c.reps, c.interval as f32)
                    })
                    .unwrap_or_else(|| shared_parameters.clone());
                let memory_state = match c.memory_state {
                    Some(state) => state,
                    // cards that lack memory states after compute_memory_state have no FSRS items,
                    // implying a truncated or ignored revlog
                    None => {
                        let fsrs = FSRS::new(&card_parameters).ok()?;
                        memory_state_from_sm2_with_params(
                            &fsrs,
                            &card_parameters,
                            c.ease_factor(),
                            c.interval as f32,
                            req.historical_retention,
                        )
                        .ok()?
                        .into()
                    }
                };
                // Simulator DR should reflect the request, regardless of any
                // stale per-card desired retention persisted on cards.
                apply_simulation_desired_retention(&mut c, req.desired_retention);
                Card::convert_with_options(
                    c,
                    days_elapsed,
                    memory_state,
                    req.desired_retention,
                    &card_parameters,
                )
            })
            .collect_vec();
        let introduced_today_count = self
            .search_cards(&format!("{} introduced:1", req.search), SortMode::NoOrder)?
            .len()
            .min(req.new_limit as usize);
        if req.new_limit > 0 {
            let new_cards = (0..new_cards).map(|i| fsrs::Card {
                id: -(i as i64),
                difficulty: f32::NEG_INFINITY,
                stability: 1e-8,              // Not filtered by fsrs-rs
                last_date: f32::NEG_INFINITY, // Treated as a new card in simulation
                due: ((introduced_today_count + i) / req.new_limit as usize) as f32,
                interval: f32::NEG_INFINITY,
                reps: 0,
                lapses: 0,
                desired_retention: req.desired_retention,
                parameters: shared_parameters.clone(),
            });
            converted_cards.extend(new_cards);
        }
        let deck_size = converted_cards.len();
        let p = self.get_optimal_retention_parameters(revlogs)?;

        let easy_days_percentages = parse_easy_days_percentages(&req.easy_days_percentages)?;
        let mut review_fuzz_config = ReviewFuzzConfig::default();
        if let Some(value) = req.review_fuzz_base {
            review_fuzz_config.base = value;
        }
        if let Some(value) = req.review_fuzz_factor_short {
            review_fuzz_config.factor_short = value;
        }
        if let Some(value) = req.review_fuzz_factor_mid {
            review_fuzz_config.factor_mid = value;
        }
        if let Some(value) = req.review_fuzz_factor_long {
            review_fuzz_config.factor_long = value;
        }
        let next_day_at = self.timing_today()?.next_day_at;

        let post_scheduling_fn = if self.get_config_bool(BoolKey::LoadBalancerEnabled) {
            let _ = (next_day_at, easy_days_percentages, review_fuzz_config);
            None
        } else {
            None
        };

        let review_priority_fn = req
            .review_order
            .try_into()
            .ok()
            .and_then(|order| create_review_priority_fn(order, deck_size));

        let config = SimulatorConfig {
            deck_size,
            learn_span: req.days_to_simulate as usize,
            max_cost_perday: f32::MAX,
            max_ivl: req.max_interval as f32,
            first_rating_prob: p.first_rating_prob,
            review_rating_prob: p.review_rating_prob,
            learn_limit: req.new_limit as usize,
            review_limit: req.review_limit as usize,
            new_cards_ignore_review_limit: req.new_cards_ignore_review_limit,
            suspend_after_lapses: req.suspend_after_lapse_count,
            post_scheduling_fn,
            review_priority_fn,
            review_rating_cost_fn: None,
            learning_step_transitions: p.learning_step_transitions,
            relearning_step_transitions: p.relearning_step_transitions,
            state_rating_costs: p.state_rating_costs,
            learning_step_count: req.learning_step_count as usize,
            relearning_step_count: req.relearning_step_count as usize,
        };

        Ok((config, converted_cards, preset_router))
    }

    pub fn simulate_review(
        &mut self,
        req: SimulateFsrsReviewRequest,
    ) -> Result<SimulateFsrsReviewResponse> {
        let (config, cards, preset_router) = self.simulate_request_to_config_inner(&req, true)?;
        let result = simulate_workload_for_desired_retention(
            &config,
            &req.params,
            &cards,
            req.desired_retention,
            preset_router.as_ref(),
        )?;
        Ok(SimulateFsrsReviewResponse {
            accumulated_knowledge_acquisition: result.memorized_cnt_per_day,
            daily_review_count: result
                .review_cnt_per_day
                .iter()
                .map(|x| *x as u32)
                .collect_vec(),
            daily_new_count: result
                .learn_cnt_per_day
                .iter()
                .map(|x| *x as u32)
                .collect_vec(),
            daily_time_cost: result.cost_per_day,
        })
    }

    pub fn simulate_workload(
        &mut self,
        req: SimulateFsrsReviewRequest,
    ) -> Result<SimulateFsrsWorkloadResponse> {
        let total_start = Instant::now();
        let (mut config, cards, preset_router) =
            self.simulate_request_to_config_inner(&req, true)?;
        let default_review_costs = config.state_rating_costs[1];
        let review_time_model_start = Instant::now();
        let model = self.build_help_me_decide_review_time_model(&req, default_review_costs)?;
        let review_time_model_elapsed_ms = review_time_model_start.elapsed().as_millis();
        let [review_time_again_seconds, review_time_hard_seconds, review_time_good_seconds, review_time_easy_seconds] =
            model.grade_flattened();
        let review_time_sample_counts = model.sample_counts_flattened();
        let model = Arc::new(model);
        install_review_time_cost_fn(&mut config, model.clone());
        let fallback_preset_name = workload_preset_label(&req);
        let workload_sweep_start = Instant::now();
        let sweep_context = WorkloadSweepContext {
            params: &req.params,
            cards: &cards,
            preset_router: preset_router.as_ref(),
            model: model.as_ref(),
            blend_alpha_override: req.help_me_decide_transition_blend_alpha,
            days_to_simulate: req.days_to_simulate as usize,
            split_workload_by_preset: req.split_workload_by_preset,
            fallback_preset_name: &fallback_preset_name,
        };
        let dr_workload = simulate_workload_sweep(&mut config, &sweep_context)?;
        let workload_sweep_elapsed_ms = workload_sweep_start.elapsed().as_millis();
        let reviewless_end_memorized = cards
            .iter()
            .fold(0., |p, c| p + c.retention_on(req.days_to_simulate as f32));
        let reviewless_end_weighted_memorized =
            weighted_memorized_for_cards(&cards, req.days_to_simulate as f32);
        let total_elapsed_ms = total_start.elapsed().as_millis();
        eprintln!(
            "{}",
            help_me_decide_timing_line(
                total_elapsed_ms,
                review_time_model_elapsed_ms,
                workload_sweep_elapsed_ms,
            )
        );
        Ok(SimulateFsrsWorkloadResponse {
            reviewless_end_memorized,
            reviewless_end_weighted_memorized,
            memorized: dr_workload.iter().map(|(k, v)| (*k, v.memorized)).collect(),
            cost: dr_workload.iter().map(|(k, v)| (*k, v.cost)).collect(),
            review_count: dr_workload
                .iter()
                .map(|(k, v)| (*k, v.review_count))
                .collect(),
            weighted_memorized: dr_workload
                .iter()
                .map(|(k, v)| (*k, v.weighted_memorized))
                .collect(),
            review_time_r_bucket_count: R_BUCKET_COUNT as u32,
            review_time_s_bucket_count: S_BUCKET_COUNT_FOR_UI as u32,
            review_time_again_seconds,
            review_time_hard_seconds,
            review_time_good_seconds,
            review_time_easy_seconds,
            review_time_sample_counts,
            review_time_again_coeffs: model
                .coeffs_for_group(HelpMeDecideReviewTimeModel::AGAIN_GROUP)
                .to_vec(),
            review_time_hard_coeffs: model
                .coeffs_for_group(HelpMeDecideReviewTimeModel::HARD_GROUP)
                .to_vec(),
            review_time_good_coeffs: model
                .coeffs_for_group(HelpMeDecideReviewTimeModel::GOOD_GROUP)
                .to_vec(),
            review_time_easy_coeffs: model
                .coeffs_for_group(HelpMeDecideReviewTimeModel::EASY_GROUP)
                .to_vec(),
            review_time_grade_weights: model.grade_weights().to_vec(),
            review_time_transition_probs: model.transition_probs_flattened(),
            review_time_transition_counts: model.transition_counts_flattened(),
            review_time_success_grade_probs: model.success_grade_probs_flattened(),
            review_time_success_grade_counts: model.success_grade_counts_flattened(),
            preset_workload: preset_workload_response(&dr_workload),
        })
    }
}

fn workload_preset_label(req: &SimulateFsrsReviewRequest) -> String {
    if req.workload_preset_label.is_empty() {
        "Preset".to_string()
    } else {
        req.workload_preset_label.clone()
    }
}

fn preset_workload_response(
    dr_workload: &HashMap<u32, WorkloadSweepPoint>,
) -> Vec<SimulateFsrsPresetWorkload> {
    let mut names = dr_workload
        .values()
        .flat_map(|point| point.preset_workload.keys().cloned())
        .collect::<HashSet<_>>()
        .into_iter()
        .collect_vec();
    names.sort();

    names
        .into_iter()
        .map(|name| {
            let mut cost = HashMap::new();
            let mut review_count = HashMap::new();
            let mut learn_count = HashMap::new();
            let mut memorized = HashMap::new();
            let mut weighted_memorized = HashMap::new();
            let mut reviewless_end_memorized = HashMap::new();
            let mut reviewless_end_weighted_memorized = HashMap::new();
            for (dr, workload) in dr_workload {
                if let Some(point) = workload.preset_workload.get(&name) {
                    cost.insert(*dr, point.cost);
                    review_count.insert(*dr, point.review_count);
                    learn_count.insert(*dr, point.learn_count);
                    memorized.insert(*dr, point.memorized);
                    weighted_memorized.insert(*dr, point.weighted_memorized);
                }
                if let Some(point) = workload.preset_reviewless_workload.get(&name) {
                    reviewless_end_memorized.insert(*dr, point.memorized);
                    reviewless_end_weighted_memorized.insert(*dr, point.weighted_memorized);
                }
            }
            SimulateFsrsPresetWorkload {
                name,
                cost,
                review_count,
                learn_count,
                memorized,
                weighted_memorized,
                reviewless_end_memorized,
                reviewless_end_weighted_memorized,
            }
        })
        .collect()
}

#[derive(Debug, PartialEq)]
struct WorkloadSweepPoint {
    memorized: f32,
    weighted_memorized: f32,
    cost: f32,
    review_count: u32,
    preset_workload: HashMap<String, PresetWorkloadPoint>,
    preset_reviewless_workload: HashMap<String, PresetReviewlessWorkloadPoint>,
}

#[derive(Clone, Debug, Default, PartialEq)]
struct PresetWorkloadPoint {
    memorized: f32,
    weighted_memorized: f32,
    cost: f32,
    review_count: u32,
    learn_count: u32,
}

#[derive(Clone, Debug, Default, PartialEq)]
struct PresetReviewlessWorkloadPoint {
    memorized: f32,
    weighted_memorized: f32,
}

struct WorkloadSweepContext<'a> {
    params: &'a [f32],
    cards: &'a [fsrs::Card],
    preset_router: Option<&'a SimulationPresetRouter>,
    model: &'a HelpMeDecideReviewTimeModel,
    blend_alpha_override: Option<f32>,
    days_to_simulate: usize,
    split_workload_by_preset: bool,
    fallback_preset_name: &'a str,
}

fn simulate_workload_sweep(
    config: &mut SimulatorConfig,
    context: &WorkloadSweepContext,
) -> Result<HashMap<u32, WorkloadSweepPoint>> {
    if config.post_scheduling_fn.is_some() {
        simulate_workload_sweep_sequential(config, context)
    } else {
        simulate_workload_sweep_parallel(config, context)
    }
}

fn simulate_workload_sweep_sequential(
    config: &mut SimulatorConfig,
    context: &WorkloadSweepContext,
) -> Result<HashMap<u32, WorkloadSweepPoint>> {
    let mut dr_workload = HashMap::with_capacity(workload_dr_count());
    let base_review_rating_prob = config.review_rating_prob;
    for dr in WORKLOAD_MIN_DR..=WORKLOAD_MAX_DR {
        let desired_retention = dr as f32 / 100.;
        config.review_rating_prob = context.model.success_review_rating_prob_for_retrievability(
            desired_retention,
            base_review_rating_prob,
            context.blend_alpha_override,
        );
        dr_workload.insert(
            dr,
            simulate_workload_sweep_point(config, context, desired_retention)?,
        );
    }
    Ok(dr_workload)
}

fn simulate_workload_sweep_parallel(
    config: &SimulatorConfig,
    context: &WorkloadSweepContext,
) -> Result<HashMap<u32, WorkloadSweepPoint>> {
    let base_review_rating_prob = config.review_rating_prob;
    (WORKLOAD_MIN_DR..=WORKLOAD_MAX_DR)
        .into_par_iter()
        .map(|dr| {
            let desired_retention = dr as f32 / 100.;
            let review_rating_prob = context.model.success_review_rating_prob_for_retrievability(
                desired_retention,
                base_review_rating_prob,
                context.blend_alpha_override,
            );
            let config = simulator_config_for_review_rating_prob(config, review_rating_prob);
            Ok((
                dr,
                simulate_workload_sweep_point(&config, context, desired_retention)?,
            ))
        })
        .collect()
}

fn workload_dr_count() -> usize {
    (WORKLOAD_MAX_DR - WORKLOAD_MIN_DR + 1) as usize
}

fn simulator_config_for_review_rating_prob(
    config: &SimulatorConfig,
    review_rating_prob: [f32; 3],
) -> SimulatorConfig {
    SimulatorConfig {
        deck_size: config.deck_size,
        learn_span: config.learn_span,
        max_cost_perday: config.max_cost_perday,
        max_ivl: config.max_ivl,
        first_rating_prob: config.first_rating_prob,
        review_rating_prob,
        learn_limit: config.learn_limit,
        review_limit: config.review_limit,
        new_cards_ignore_review_limit: config.new_cards_ignore_review_limit,
        suspend_after_lapses: config.suspend_after_lapses,
        post_scheduling_fn: None,
        review_priority_fn: config.review_priority_fn.clone(),
        review_rating_cost_fn: config.review_rating_cost_fn.clone(),
        learning_step_transitions: config.learning_step_transitions,
        relearning_step_transitions: config.relearning_step_transitions,
        state_rating_costs: config.state_rating_costs,
        learning_step_count: config.learning_step_count,
        relearning_step_count: config.relearning_step_count,
    }
}

fn simulate_workload_sweep_point(
    config: &SimulatorConfig,
    context: &WorkloadSweepContext,
    desired_retention: f32,
) -> Result<WorkloadSweepPoint> {
    let (result, preset_workload) = if context.split_workload_by_preset {
        simulate_workload_split_summary_for_desired_retention(
            config,
            context.params,
            context.cards,
            desired_retention,
            context.preset_router,
            context.days_to_simulate,
            context.fallback_preset_name,
        )?
    } else {
        (
            simulate_workload_summary_for_desired_retention(
                config,
                context.params,
                context.cards,
                desired_retention,
                context.preset_router,
            )?,
            HashMap::new(),
        )
    };
    Ok(WorkloadSweepPoint {
        memorized: result.memorized,
        weighted_memorized: weighted_memorized_for_cards(
            &result.cards,
            simulation_end_date(context.days_to_simulate),
        ),
        cost: result.cost,
        review_count: result.review_count as u32 + result.learn_count as u32,
        preset_workload,
        preset_reviewless_workload: preset_reviewless_workload_for_cards(
            context.cards,
            context.preset_router,
            context.fallback_preset_name,
            simulation_end_date(context.days_to_simulate),
        ),
    })
}

fn apply_simulation_desired_retention(card: &mut Card, desired_retention: f32) {
    card.desired_retention = Some(desired_retention);
}

fn apply_simulation_desired_retention_to_cards(cards: &mut [fsrs::Card], desired_retention: f32) {
    for card in cards {
        card.desired_retention = desired_retention;
    }
}

fn simulate_workload_for_desired_retention(
    config: &SimulatorConfig,
    params: &[f32],
    cards: &[fsrs::Card],
    desired_retention: f32,
    preset_router: Option<&SimulationPresetRouter>,
) -> Result<fsrs::SimulationResult> {
    let params = legacy_fsrs_params(params);
    let mut cards_for_dr = cards.to_vec();
    apply_simulation_desired_retention_to_cards(&mut cards_for_dr, desired_retention);
    if let Some(preset_router) = preset_router {
        if let Some(card_update_fn) =
            preset_router.card_update_fn_for_simulation(desired_retention)?
        {
            return Ok(simulate_with_card_update_fn(
                config,
                params,
                desired_retention,
                None,
                Some(cards_for_dr),
                &card_update_fn,
            )?);
        }
    }
    Ok(simulate(
        config,
        params,
        desired_retention,
        None,
        Some(cards_for_dr),
    )?)
}

fn simulate_workload_summary_for_desired_retention(
    config: &SimulatorConfig,
    params: &[f32],
    cards: &[fsrs::Card],
    desired_retention: f32,
    preset_router: Option<&SimulationPresetRouter>,
) -> Result<fsrs::SimulationSummaryResult> {
    let params = legacy_fsrs_params(params);
    let mut cards_for_dr = cards.to_vec();
    apply_simulation_desired_retention_to_cards(&mut cards_for_dr, desired_retention);
    if let Some(preset_router) = preset_router {
        if let Some(card_update_fn) =
            preset_router.card_update_fn_for_simulation(desired_retention)?
        {
            return Ok(simulate_summary_with_card_update_fn(
                config,
                params,
                desired_retention,
                None,
                Some(cards_for_dr),
                &card_update_fn,
            )?);
        }
    }
    Ok(simulate_summary(
        config,
        params,
        desired_retention,
        None,
        Some(cards_for_dr),
    )?)
}

fn simulate_workload_split_summary_for_desired_retention(
    config: &SimulatorConfig,
    params: &[f32],
    cards: &[fsrs::Card],
    desired_retention: f32,
    preset_router: Option<&SimulationPresetRouter>,
    days_to_simulate: usize,
    fallback_preset_name: &str,
) -> Result<(
    fsrs::SimulationSummaryResult,
    HashMap<String, PresetWorkloadPoint>,
)> {
    let params = legacy_fsrs_params(params);
    let mut cards_for_dr = cards.to_vec();
    apply_simulation_desired_retention_to_cards(&mut cards_for_dr, desired_retention);
    let preset_workload = Arc::new(Mutex::new(HashMap::<String, PresetWorkloadPoint>::new()));
    let event_fn = preset_workload_event_fn(
        desired_retention,
        preset_router,
        fallback_preset_name,
        preset_workload.clone(),
    )?;
    let card_update_fn = preset_router
        .map(|router| router.card_update_fn_for_simulation(desired_retention))
        .transpose()?
        .flatten();
    let result = simulate_summary_with_card_update_and_event_fn(
        config,
        params,
        desired_retention,
        None,
        Some(cards_for_dr),
        card_update_fn.as_ref(),
        &event_fn,
    )?;
    drop(event_fn);

    let mut preset_workload = Arc::try_unwrap(preset_workload)
        .unwrap_or_else(|_| unreachable!("simulation event recorder should not be shared"))
        .into_inner()
        .unwrap_or_else(|err| err.into_inner());
    add_final_preset_memorized(
        &mut preset_workload,
        &result.cards,
        preset_router,
        fallback_preset_name,
        simulation_end_date(days_to_simulate),
    );

    Ok((result, preset_workload))
}

fn preset_workload_event_fn(
    desired_retention: f32,
    preset_router: Option<&SimulationPresetRouter>,
    fallback_preset_name: &str,
    preset_workload: Arc<Mutex<HashMap<String, PresetWorkloadPoint>>>,
) -> Result<SimulatorEventFn> {
    let fallback_name = preset_router
        .map(|router| router.fallback.name.clone())
        .unwrap_or_else(|| fallback_preset_name.to_string());
    let presets_by_card = preset_router
        .map(|router| {
            router
                .presets_by_card
                .iter()
                .map(|(card_id, preset)| (*card_id, preset.name.clone()))
                .collect::<HashMap<_, _>>()
        })
        .unwrap_or_default();
    let routes = preset_router
        .map(|router| {
            router
                .routes
                .iter()
                .map(|route| route.for_target(desired_retention))
                .collect::<Result<Vec<_>>>()
        })
        .transpose()?
        .unwrap_or_default();

    Ok(SimulatorEventFn::new(
        move |card, event: SimulationEvent| {
            let preset_name = routes
                .iter()
                .find(|route| route.matches(card))
                .map(|route| route.preset.name.as_str())
                .or_else(|| presets_by_card.get(&card.id).map(String::as_str))
                .unwrap_or(&fallback_name);
            let mut preset_workload = preset_workload
                .lock()
                .unwrap_or_else(|err| err.into_inner());
            let point = preset_workload.entry(preset_name.to_string()).or_default();
            point.cost += event.cost;
            if event.is_learn {
                point.learn_count += 1;
            } else {
                point.review_count += 1;
            }
        },
    ))
}

fn add_final_preset_memorized(
    preset_workload: &mut HashMap<String, PresetWorkloadPoint>,
    cards: &[fsrs::Card],
    preset_router: Option<&SimulationPresetRouter>,
    fallback_preset_name: &str,
    date: f32,
) {
    for card in cards {
        if !(card.stability.is_finite() && card.stability > 0.0) {
            continue;
        }
        let preset_name = preset_router
            .map(|router| router.active_preset_name_for_card(card))
            .unwrap_or(fallback_preset_name);
        let point = preset_workload.entry(preset_name.to_string()).or_default();
        let retrievability = card.retention_on(date);
        point.memorized += retrievability;
        point.weighted_memorized += retrievability * stability_weight(card.stability);
    }
}

fn preset_reviewless_workload_for_cards(
    cards: &[fsrs::Card],
    preset_router: Option<&SimulationPresetRouter>,
    fallback_preset_name: &str,
    date: f32,
) -> HashMap<String, PresetReviewlessWorkloadPoint> {
    let mut workload = HashMap::<String, PresetReviewlessWorkloadPoint>::new();
    for card in cards {
        if !(card.stability.is_finite() && card.stability > 0.0) {
            continue;
        }
        let preset_name = preset_router
            .map(|router| router.active_preset_name_for_card(card))
            .unwrap_or(fallback_preset_name);
        let point = workload.entry(preset_name.to_string()).or_default();
        let retrievability = card.retention_on(date);
        point.memorized += retrievability;
        point.weighted_memorized += retrievability * stability_weight(card.stability);
    }
    workload
}

fn stability_weight(stability: f32) -> f32 {
    1.0 - ((-8.0 / 365.0) * stability).exp()
}

fn simulation_end_date(learn_span: usize) -> f32 {
    learn_span.saturating_sub(1) as f32
}

fn weighted_memorized_for_cards(cards: &[fsrs::Card], date: f32) -> f32 {
    cards
        .iter()
        .filter(|card| card.stability.is_finite() && card.stability > 0.0)
        .map(|card| card.retention_on(date) * stability_weight(card.stability))
        .sum()
}

impl Card {
    pub(crate) fn convert(
        card: Card,
        days_elapsed: i32,
        memory_state: FsrsMemoryState,
        desired_retention: f32,
        parameters: Arc<Vec<f32>>,
    ) -> Option<fsrs::Card> {
        Self::convert_with_options(
            card,
            days_elapsed,
            memory_state,
            desired_retention,
            &parameters,
        )
    }

    pub(crate) fn convert_with_options(
        card: Card,
        days_elapsed: i32,
        memory_state: FsrsMemoryState,
        default_desired_retention: f32,
        parameters: &Arc<Vec<f32>>,
    ) -> Option<fsrs::Card> {
        match card.queue {
            CardQueue::DayLearn | CardQueue::Review => {
                let due = card.original_or_current_due();
                let relative_due = due - days_elapsed;
                let last_date = (relative_due - card.interval as i32).min(0) as f32;
                Some(fsrs::Card {
                    id: card.id.0,
                    difficulty: memory_state.difficulty,
                    stability: memory_state.stability_internal,
                    last_date,
                    due: relative_due as f32,
                    interval: card.interval as f32,
                    reps: card.reps,
                    lapses: card.lapses,
                    desired_retention: card.desired_retention.unwrap_or(default_desired_retention),
                    parameters: parameters.clone(),
                })
            }
            CardQueue::New => None,
            CardQueue::Learn | CardQueue::SchedBuried | CardQueue::UserBuried => Some(fsrs::Card {
                id: card.id.0,
                difficulty: memory_state.difficulty,
                stability: memory_state.stability_internal,
                last_date: 0.0,
                due: 0.0,
                interval: card.interval as f32,
                reps: card.reps,
                lapses: card.lapses,
                desired_retention: card.desired_retention.unwrap_or(default_desired_retention),
                parameters: parameters.clone(),
            }),
            CardQueue::PreviewRepeat => None,
            CardQueue::Suspended => None,
        }
    }
}

pub(crate) fn normalized_fsrs_parameters(params: &[f32]) -> Result<Vec<f32>> {
    let converted = match params.len() {
        0 => DEFAULT_PARAMETERS.to_vec(),
        17 => {
            let mut parameters = params.to_vec();
            parameters[4] = parameters[5].mul_add(2.0, parameters[4]);
            parameters[5] = parameters[5].mul_add(3.0, 1.0).ln() / 3.0;
            parameters[6] += 0.5;
            parameters.extend_from_slice(&[0.0, 0.0, 0.0, fsrs::FSRS5_DEFAULT_DECAY]);
            parameters
        }
        19 => {
            let mut parameters = params.to_vec();
            parameters.extend_from_slice(&[0.0, fsrs::FSRS5_DEFAULT_DECAY]);
            parameters
        }
        21 => params.to_vec(),
        34 => params.to_vec(),
        _ => invalid_input!("invalid FSRS parameter count"),
    };
    if converted.iter().any(|w| !w.is_finite()) {
        invalid_input!("invalid FSRS parameter values")
    } else {
        Ok(converted)
    }
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;
    use std::sync::Arc;

    use anki_proto::scheduler::SimulateFsrsReviewRequest;
    use fsrs::Card as SimCard;
    use fsrs::SimulatorCardUpdatePhase;
    use fsrs::SimulatorConfig;
    use fsrs::DEFAULT_PARAMETERS;

    use super::apply_simulation_desired_retention;
    use super::apply_simulation_desired_retention_to_cards;
    use super::create_review_priority_fn;
    use super::simulate_workload_for_desired_retention;
    use super::simulation_dynamic_desired_retention;
    use super::simulation_fallback_preset;
    use super::SimulationPreset;
    use super::SimulationPresetRoute;
    use super::SimulationPresetRouter;
    use crate::card::Card;
    use crate::deckconfig::ReviewCardOrder;
    use crate::prelude::*;
    use crate::scheduler::fsrs::preset::AddonFsrsPreset;
    use crate::scheduler::fsrs::preset::AddonFsrsVersion;
    use crate::scheduler::fsrs::preset::FsrsPresetOverlay;
    use crate::scheduler::fsrs::preset::FsrsPresetRule;
    use crate::scheduler::fsrs::preset::FsrsPresetSimulatorRule;
    use crate::scheduler::fsrs::preset::FSRS_PRESET_OVERLAY_CONFIG_KEY;
    use crate::scheduler::fsrs::review_time_model::consume_review_repetition;
    use crate::scheduler::fsrs::review_time_model::include_repetitions_in_regression;
    use crate::scheduler::fsrs::review_time_model::install_review_time_cost_fn;
    use crate::scheduler::fsrs::review_time_model::HelpMeDecideReviewTimeModel;
    use crate::scheduler::fsrs::review_time_model::R_BUCKET_COUNT;
    use crate::tests::NoteAdder;

    fn no_transitions() -> [[u32; 4]; 4] {
        [[0u32; 4]; 4]
    }

    fn synthetic_fail_model() -> HelpMeDecideReviewTimeModel {
        HelpMeDecideReviewTimeModel::from_samples(
            &[
                (0.9, 5.0, 1.0, 5.0, 1, 27.3),
                (0.8, 7.0, 2.0, 6.0, 1, 40.6),
                (0.7, 9.0, 3.0, 7.0, 1, 53.9),
                (0.6, 6.0, 4.0, 6.5, 1, 47.2),
                (0.5, 8.0, 1.0, 5.5, 1, 40.5),
                (0.85, 10.0, 2.0, 7.5, 1, 52.45),
            ],
            no_transitions(),
            false,
            [20.0, 18.0, 12.0, 9.0],
        )
    }

    #[test]
    fn review_time_model_uses_five_percent_r_buckets() {
        assert_eq!(HelpMeDecideReviewTimeModel::r_bucket(1.0), 0);
        assert_eq!(HelpMeDecideReviewTimeModel::r_bucket(0.95), 0);
        assert_eq!(HelpMeDecideReviewTimeModel::r_bucket(0.949), 1);
        assert_eq!(HelpMeDecideReviewTimeModel::r_bucket(0.90), 1);
    }

    #[test]
    fn review_time_model_uses_linear_regression_on_retrievability() {
        let model = synthetic_fail_model();
        let high_r = model.cost_for(0.9, 7.0, 2.0, 6.0, 1);
        let low_r = model.cost_for(0.7, 7.0, 2.0, 6.0, 1);
        assert!(low_r > high_r);
    }

    #[test]
    fn help_me_decide_timing_line_includes_expected_fields() {
        assert_eq!(
            super::help_me_decide_timing_line(123, 45, 67),
            "[help-me-decide timing] total=123ms review_time_model=45ms workload_sweep=67ms"
        );
    }

    #[test]
    fn weighted_memorized_for_cards_uses_retrievability_times_stability_weight() {
        let parameters = Arc::new(DEFAULT_PARAMETERS.to_vec());
        let high_stability = SimCard {
            id: 1,
            stability: 365.0,
            last_date: 0.0,
            parameters: parameters.clone(),
            ..Default::default()
        };
        let low_stability = SimCard {
            id: 2,
            stability: 1.0,
            last_date: 0.0,
            parameters,
            ..Default::default()
        };
        let never_learned = SimCard {
            id: 3,
            stability: f32::NEG_INFINITY,
            ..Default::default()
        };
        let result = fsrs::SimulationResult {
            memorized_cnt_per_day: Vec::new(),
            review_cnt_per_day: Vec::new(),
            learn_cnt_per_day: Vec::new(),
            cost_per_day: Vec::new(),
            correct_cnt_per_day: Vec::new(),
            introduced_cnt_per_day: Vec::new(),
            cards: vec![high_stability, low_stability, never_learned],
        };

        let end_date = 9.0;
        let expected = result.cards[0].retention_on(end_date)
            * super::stability_weight(result.cards[0].stability)
            + result.cards[1].retention_on(end_date)
                * super::stability_weight(result.cards[1].stability);
        let weighted_memorized =
            super::weighted_memorized_for_cards(&result.cards, super::simulation_end_date(10));

        assert!((weighted_memorized - expected).abs() < 1e-6);
        assert!(super::stability_weight(365.0) > 0.99);
        assert!(super::stability_weight(1.0) < 0.03);
    }

    #[test]
    fn parallel_workload_sweep_matches_sequential_sweep() {
        let model = Arc::new(synthetic_fail_model());
        let cards = vec![SimCard {
            id: 1,
            difficulty: 5.0,
            stability: 10.0,
            last_date: -1.0,
            due: 0.0,
            interval: 1.0,
            reps: 2,
            lapses: 0,
            desired_retention: 0.85,
            parameters: Arc::new(DEFAULT_PARAMETERS.to_vec()),
        }];
        let config = || {
            let mut config = SimulatorConfig {
                deck_size: 1,
                learn_span: 7,
                learn_limit: 0,
                review_limit: 9999,
                review_rating_prob: [0.0, 1.0, 0.0],
                ..Default::default()
            };
            install_review_time_cost_fn(&mut config, model.clone());
            config
        };
        let mut sequential_config = config();
        let parallel_config = config();
        let context = super::WorkloadSweepContext {
            params: &DEFAULT_PARAMETERS,
            cards: &cards,
            preset_router: None,
            model: model.as_ref(),
            blend_alpha_override: None,
            days_to_simulate: 7,
            split_workload_by_preset: false,
            fallback_preset_name: "Preset",
        };

        let sequential =
            super::simulate_workload_sweep_sequential(&mut sequential_config, &context).unwrap();
        let parallel = super::simulate_workload_sweep_parallel(&parallel_config, &context).unwrap();

        assert_eq!(parallel, sequential);
        assert_eq!(parallel.len(), super::workload_dr_count());
        assert!(!parallel.contains_key(&(super::WORKLOAD_MIN_DR - 1)));
        assert!(parallel.contains_key(&super::WORKLOAD_MIN_DR));
        assert!(parallel.contains_key(&super::WORKLOAD_MAX_DR));
    }

    fn dynamic_desired_retention_request() -> SimulateFsrsReviewRequest {
        SimulateFsrsReviewRequest {
            simulate_dynamic_desired_retention: true,
            fsrs_dynamic_desired_retention_params: vec![0.0; 15],
            fsrs_dynamic_desired_retention_weights: vec![0.0, 15.0],
            fsrs_dynamic_desired_retention_avg_drs: vec![0.8, 0.9],
            fsrs_dynamic_desired_retention_min: 0.75,
            fsrs_dynamic_desired_retention_max: 0.95,
            max_interval: 36500,
            ..Default::default()
        }
    }

    #[test]
    fn dynamic_desired_retention_request_builds_simulation_policy() {
        let request = dynamic_desired_retention_request();
        let dynamic_desired_retention = simulation_dynamic_desired_retention(&request)
            .unwrap()
            .unwrap();

        assert!(dynamic_desired_retention
            .dynamic_desired_retention
            .scheduling_target(0.85)
            .unwrap()
            .is_some());
    }

    #[test]
    fn dynamic_desired_retention_empty_request_has_no_fallback_policy() {
        let request = SimulateFsrsReviewRequest {
            simulate_dynamic_desired_retention: true,
            ..Default::default()
        };

        assert!(simulation_dynamic_desired_retention(&request)
            .unwrap()
            .is_none());
    }

    #[test]
    fn simulator_preset_router_uses_addon_overlay_dynamic_preset_for_matching_cards() -> Result<()>
    {
        let mut col = Collection::new();
        let tagged_note = NoteAdder::basic(&mut col).add(&mut col);
        NoteAdder::basic(&mut col)
            .fields(&["other", "back"])
            .add(&mut col);
        col.add_tags_to_notes(&[tagged_note.id], "adr")?;
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &FsrsPresetOverlay {
                presets: vec![AddonFsrsPreset {
                    id: "addon:test:adr".into(),
                    name: "ADR".into(),
                    fsrs_version: AddonFsrsVersion::Seven,
                    params: DEFAULT_PARAMETERS.to_vec(),
                    desired_retention: 0.85,
                    historical_retention: 0.9,
                    ignore_revlogs_before_date: String::new(),
                    fsrs_dynamic_desired_retention_enabled: true,
                    fsrs_dynamic_desired_retention_params: vec![0.0; 15],
                    fsrs_dynamic_desired_retention_weights: vec![0.0, 15.0],
                    fsrs_dynamic_desired_retention_avg_drs: vec![0.8, 0.9],
                    fsrs_dynamic_desired_retention_fsrs_eq_weights: vec![0.0, 15.0],
                    fsrs_dynamic_desired_retention_fsrs_eq_drs: vec![0.95, 0.75],
                    fsrs_dynamic_desired_retention_min: 0.75,
                    fsrs_dynamic_desired_retention_max: 0.95,
                    ..Default::default()
                }],
                rules: vec![FsrsPresetRule {
                    search: "tag:adr".into(),
                    preset_id: "addon:test:adr".into(),
                }],
                simulator_rules: Vec::new(),
            },
        )?;
        let cards = col.all_cards_for_search("")?;
        let request = SimulateFsrsReviewRequest {
            simulate_dynamic_desired_retention: true,
            params: DEFAULT_PARAMETERS.to_vec(),
            max_interval: 36500,
            ..Default::default()
        };

        let router = col.simulation_preset_router(&request, &cards)?.unwrap();
        let tagged_card_id = cards
            .iter()
            .find(|card| card.note_id == tagged_note.id)
            .unwrap()
            .id
            .0;

        assert!(router.fallback.dynamic_desired_retention.is_none());
        assert!(router.routes.is_empty());
        assert_eq!(router.presets_by_card.len(), 1);
        assert!(router.presets_by_card.contains_key(&tagged_card_id));

        let update_fn = router.card_update_fn(0.9)?;
        let dynamic_for_target = router
            .presets_by_card
            .get(&tagged_card_id)
            .unwrap()
            .dynamic_desired_retention
            .as_ref()
            .unwrap()
            .for_target(0.9)?
            .unwrap();
        assert!((dynamic_for_target.cost_weight - 1.0).abs() < 1e-5);
        let mut card = SimCard {
            id: tagged_card_id,
            difficulty: 5.0,
            stability: 10.0,
            interval: 10.0,
            desired_retention: 0.9,
            parameters: Arc::new(vec![0.0; DEFAULT_PARAMETERS.len()]),
            ..Default::default()
        };

        update_fn(&mut card, SimulatorCardUpdatePhase::BeforeMemoryUpdate);
        assert_eq!(card.parameters.as_slice(), DEFAULT_PARAMETERS.as_slice());

        update_fn(&mut card, SimulatorCardUpdatePhase::AfterMemoryUpdate);
        let expected = dynamic_for_target.policy.evaluate_retention(
            card.stability,
            card.difficulty,
            dynamic_for_target.cost_weight,
        );
        assert!((card.desired_retention - expected).abs() < 1e-6);

        Ok(())
    }

    #[test]
    fn simulator_preset_router_uses_addon_params_when_dynamic_dr_toggle_is_off() -> Result<()> {
        let mut col = Collection::new();
        let tagged_note = NoteAdder::basic(&mut col).add(&mut col);
        NoteAdder::basic(&mut col)
            .fields(&["other", "back"])
            .add(&mut col);
        col.add_tags_to_notes(&[tagged_note.id], "fixed")?;
        let addon_params = vec![1.0; DEFAULT_PARAMETERS.len()];
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &FsrsPresetOverlay {
                presets: vec![AddonFsrsPreset {
                    id: "addon:test:fixed".into(),
                    name: "Fixed".into(),
                    fsrs_version: AddonFsrsVersion::Seven,
                    params: addon_params.clone(),
                    desired_retention: 0.85,
                    historical_retention: 0.9,
                    ignore_revlogs_before_date: String::new(),
                    fsrs_dynamic_desired_retention_enabled: true,
                    fsrs_dynamic_desired_retention_params: vec![0.0; 15],
                    fsrs_dynamic_desired_retention_weights: vec![0.0, 15.0],
                    fsrs_dynamic_desired_retention_avg_drs: vec![0.8, 0.9],
                    fsrs_dynamic_desired_retention_min: 0.75,
                    fsrs_dynamic_desired_retention_max: 0.95,
                    ..Default::default()
                }],
                rules: vec![FsrsPresetRule {
                    search: "tag:fixed".into(),
                    preset_id: "addon:test:fixed".into(),
                }],
                simulator_rules: Vec::new(),
            },
        )?;
        let cards = col.all_cards_for_search("")?;
        let request = SimulateFsrsReviewRequest {
            simulate_dynamic_desired_retention: false,
            params: DEFAULT_PARAMETERS.to_vec(),
            max_interval: 36500,
            ..Default::default()
        };

        let router = col.simulation_preset_router(&request, &cards)?.unwrap();
        let tagged_card_id = cards
            .iter()
            .find(|card| card.note_id == tagged_note.id)
            .unwrap()
            .id
            .0;

        let preset = router.presets_by_card.get(&tagged_card_id).unwrap();
        assert_eq!(preset.parameters.as_ref().unwrap().as_slice(), addon_params);
        assert!(preset.dynamic_desired_retention.is_none());

        let update_fn = router.card_update_fn(0.9)?;
        let mut card = SimCard {
            id: tagged_card_id,
            difficulty: 5.0,
            stability: 10.0,
            interval: 10.0,
            desired_retention: 0.75,
            parameters: Arc::new(DEFAULT_PARAMETERS.to_vec()),
            ..Default::default()
        };

        update_fn(&mut card, SimulatorCardUpdatePhase::BeforeMemoryUpdate);
        assert_eq!(card.parameters.as_slice(), addon_params);

        update_fn(&mut card, SimulatorCardUpdatePhase::AfterMemoryUpdate);
        assert_eq!(card.parameters.as_slice(), addon_params);
        assert_eq!(card.desired_retention, 0.9);

        Ok(())
    }

    #[test]
    fn simulator_preset_router_scopes_simulator_rule_search() -> Result<()> {
        let mut col = Collection::new();
        let tagged_note = NoteAdder::basic(&mut col).add(&mut col);
        NoteAdder::basic(&mut col)
            .fields(&["other", "back"])
            .add(&mut col);
        col.add_tags_to_notes(&[tagged_note.id], "route")?;
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &FsrsPresetOverlay {
                presets: vec![AddonFsrsPreset {
                    id: "addon:test:route".into(),
                    name: "Route".into(),
                    fsrs_version: AddonFsrsVersion::Six,
                    params: vec![1.0; 21],
                    desired_retention: 0.85,
                    historical_retention: 0.9,
                    ignore_revlogs_before_date: String::new(),
                    ..Default::default()
                }],
                rules: Vec::new(),
                simulator_rules: vec![FsrsPresetSimulatorRule {
                    preset_id: "addon:test:route".into(),
                    search: Some("tag:route".into()),
                    min_interval_days: Some(30.0),
                    ..Default::default()
                }],
            },
        )?;
        let cards = col.all_cards_for_search("")?;
        let request = SimulateFsrsReviewRequest {
            simulate_dynamic_desired_retention: false,
            params: DEFAULT_PARAMETERS.to_vec(),
            max_interval: 36500,
            ..Default::default()
        };

        let router = col.simulation_preset_router(&request, &cards)?.unwrap();
        let tagged_card_id = cards
            .iter()
            .find(|card| card.note_id == tagged_note.id)
            .unwrap()
            .id
            .0;
        let untagged_card_id = cards
            .iter()
            .find(|card| card.note_id != tagged_note.id)
            .unwrap()
            .id
            .0;

        let tagged_young = router
            .parameters_for_card_state(tagged_card_id, 0, 10.0)
            .unwrap();
        let tagged_mature = router
            .parameters_for_card_state(tagged_card_id, 0, 30.0)
            .unwrap();
        let untagged_mature = router
            .parameters_for_card_state(untagged_card_id, 0, 30.0)
            .unwrap();

        assert_eq!(tagged_young.as_slice(), DEFAULT_PARAMETERS.as_slice());
        assert_eq!(tagged_mature.as_slice(), vec![1.0; 21].as_slice());
        assert_eq!(untagged_mature.as_slice(), DEFAULT_PARAMETERS.as_slice());

        Ok(())
    }

    #[test]
    fn simulator_preset_router_applies_dynamic_rule_by_reps() {
        let request = dynamic_desired_retention_request();
        let dynamic_desired_retention = simulation_dynamic_desired_retention(&request)
            .unwrap()
            .unwrap();
        let dynamic_for_target = dynamic_desired_retention.for_target(0.85).unwrap().unwrap();
        let fallback_parameters = Arc::new(vec![0.0; DEFAULT_PARAMETERS.len()]);
        let routed_parameters = Arc::new(DEFAULT_PARAMETERS.to_vec());
        let router = SimulationPresetRouter {
            fallback: SimulationPreset {
                name: "Fallback".into(),
                parameters: Some(fallback_parameters.clone()),
                dynamic_desired_retention: None,
            },
            presets_by_card: HashMap::new(),
            routes: vec![SimulationPresetRoute {
                card_ids: None,
                min_reps: Some(1),
                max_reps: None,
                min_interval_days: Some(5.0),
                max_interval_days: Some(20.0),
                preset: SimulationPreset {
                    name: "Routed".into(),
                    parameters: Some(routed_parameters.clone()),
                    dynamic_desired_retention: Some(dynamic_desired_retention),
                },
            }],
        };
        let update_fn = router.card_update_fn(0.85).unwrap();
        let mut card = SimCard {
            difficulty: 5.0,
            stability: 10.0,
            reps: 0,
            interval: 10.0,
            ..Default::default()
        };

        update_fn(&mut card, SimulatorCardUpdatePhase::AfterMemoryUpdate);
        assert_eq!(card.desired_retention, 0.85);

        card.reps = 1;
        update_fn(&mut card, SimulatorCardUpdatePhase::BeforeMemoryUpdate);
        assert!(Arc::ptr_eq(&card.parameters, &routed_parameters));

        update_fn(&mut card, SimulatorCardUpdatePhase::AfterMemoryUpdate);
        let expected = dynamic_for_target.policy.evaluate_retention(
            card.stability,
            card.difficulty,
            dynamic_for_target.cost_weight,
        );
        assert!((card.desired_retention - expected).abs() < 1e-6);

        card.interval = 30.0;
        update_fn(&mut card, SimulatorCardUpdatePhase::BeforeMemoryUpdate);
        assert!(Arc::ptr_eq(&card.parameters, &fallback_parameters));
        update_fn(&mut card, SimulatorCardUpdatePhase::AfterMemoryUpdate);
        assert_eq!(card.desired_retention, 0.85);
    }

    #[test]
    fn review_priority_fn_reflects_review_order() {
        let short_low_r = SimCard {
            id: 1,
            difficulty: 3.0,
            stability: 5.0,
            last_date: -5.0,
            due: -1.0,
            interval: 5.0,
            reps: 0,
            lapses: 0,
            desired_retention: 0.9,
            parameters: std::sync::Arc::new(DEFAULT_PARAMETERS.to_vec()),
        };
        let long_high_r = SimCard {
            id: 2,
            difficulty: 7.0,
            stability: 50.0,
            last_date: -5.0,
            due: -5.0,
            interval: 50.0,
            reps: 0,
            lapses: 0,
            desired_retention: 0.9,
            parameters: std::sync::Arc::new(DEFAULT_PARAMETERS.to_vec()),
        };

        let interval_ascending =
            create_review_priority_fn(ReviewCardOrder::IntervalsAscending, 2).unwrap();
        assert!(interval_ascending(&short_low_r) < interval_ascending(&long_high_r));

        let interval_descending =
            create_review_priority_fn(ReviewCardOrder::IntervalsDescending, 2).unwrap();
        assert!(interval_descending(&short_low_r) > interval_descending(&long_high_r));

        let retrievability_ascending =
            create_review_priority_fn(ReviewCardOrder::RetrievabilityAscending, 2).unwrap();
        assert!(retrievability_ascending(&short_low_r) < retrievability_ascending(&long_high_r));

        let retrievability_descending =
            create_review_priority_fn(ReviewCardOrder::RetrievabilityDescending, 2).unwrap();
        assert!(retrievability_descending(&short_low_r) > retrievability_descending(&long_high_r));
    }

    #[test]
    fn review_priority_changes_limited_workload_simulation() {
        let short = SimCard {
            id: 1,
            difficulty: 5.0,
            stability: 10.0,
            last_date: -10.0,
            due: -1.0,
            interval: 10.0,
            reps: 0,
            lapses: 0,
            desired_retention: 0.9,
            parameters: std::sync::Arc::new(DEFAULT_PARAMETERS.to_vec()),
        };
        let long = SimCard {
            id: 2,
            difficulty: 5.0,
            stability: 50.0,
            last_date: -50.0,
            due: -5.0,
            interval: 50.0,
            reps: 0,
            lapses: 0,
            desired_retention: 0.9,
            parameters: std::sync::Arc::new(DEFAULT_PARAMETERS.to_vec()),
        };
        let cards = vec![short, long];
        let mut ascending_config = SimulatorConfig {
            deck_size: cards.len(),
            learn_span: 1,
            learn_limit: 0,
            review_limit: 1,
            review_rating_prob: [0.0, 1.0, 0.0],
            ..Default::default()
        };
        ascending_config.review_priority_fn =
            create_review_priority_fn(ReviewCardOrder::IntervalsAscending, cards.len());
        let mut descending_config = SimulatorConfig {
            deck_size: cards.len(),
            learn_span: 1,
            learn_limit: 0,
            review_limit: 1,
            review_rating_prob: [0.0, 1.0, 0.0],
            ..Default::default()
        };
        descending_config.review_priority_fn =
            create_review_priority_fn(ReviewCardOrder::IntervalsDescending, cards.len());

        let ascending = simulate_workload_for_desired_retention(
            &ascending_config,
            &DEFAULT_PARAMETERS,
            &cards,
            0.9,
            None,
        )
        .unwrap();
        let descending = simulate_workload_for_desired_retention(
            &descending_config,
            &DEFAULT_PARAMETERS,
            &cards,
            0.9,
            None,
        )
        .unwrap();

        assert_eq!(ascending.cards[0].last_date, 0.0);
        assert_eq!(ascending.cards[1].last_date, -50.0);
        assert_eq!(descending.cards[0].last_date, -10.0);
        assert_eq!(descending.cards[1].last_date, 0.0);
    }

    #[test]
    fn grade_matrix_uses_per_grade_regression() {
        let samples = vec![
            (0.9, 5.0, 2.0, 5.0, 1, 30.0),
            (0.9, 5.0, 2.0, 5.0, 2, 20.0),
            (0.9, 5.0, 2.0, 5.0, 3, 10.0),
            (0.9, 5.0, 2.0, 5.0, 4, 5.0),
            (0.8, 5.0, 2.0, 5.0, 1, 33.0),
            (0.8, 5.0, 2.0, 5.0, 2, 23.0),
            (0.8, 5.0, 2.0, 5.0, 3, 13.0),
            (0.8, 5.0, 2.0, 5.0, 4, 8.0),
            (0.7, 5.0, 2.0, 5.0, 1, 36.0),
            (0.7, 5.0, 2.0, 5.0, 2, 26.0),
            (0.7, 5.0, 2.0, 5.0, 3, 16.0),
            (0.7, 5.0, 2.0, 5.0, 4, 11.0),
        ];
        let model = HelpMeDecideReviewTimeModel::from_samples(
            &samples,
            no_transitions(),
            false,
            [1.0, 1.0, 1.0, 1.0],
        );
        let [again, hard, good, easy] = model.grade_flattened();
        let rb = HelpMeDecideReviewTimeModel::r_bucket(0.9);
        let idx = rb;
        assert!(again[idx] > hard[idx]);
        assert!(hard[idx] > good[idx]);
        assert!(good[idx] > easy[idx]);
    }

    #[test]
    fn sample_count_matrix_tracks_observed_samples() {
        let model = HelpMeDecideReviewTimeModel::from_samples(
            &[(0.9, 5.0, 2.0, 5.0, 1, 30.0), (0.9, 5.0, 2.0, 5.0, 3, 10.0)],
            no_transitions(),
            false,
            [1.0, 1.0, 1.0, 1.0],
        );
        let rb = HelpMeDecideReviewTimeModel::r_bucket(0.9);
        let counts = model.sample_counts_flattened();
        assert_eq!(counts[rb], 2);
    }

    #[test]
    fn review_time_model_falls_back_to_constant_with_single_sample() {
        let model = HelpMeDecideReviewTimeModel::from_samples(
            &[(0.7, 12.0, 2.0, 5.0, 2, 9.0)],
            no_transitions(),
            false,
            [1.0, 1.0, 1.0, 1.0],
        );
        assert!((model.cost_for(0.2, 0.0, 2.0, 5.0, 2) - 9.0).abs() < 0.0001);
        assert!((model.cost_for(0.9, 0.0, 2.0, 5.0, 2) - 9.0).abs() < 0.0001);
    }

    #[test]
    fn review_time_model_uses_stability_factor() {
        let model = synthetic_fail_model();
        let low_s = model.cost_for(0.8, 5.0, 2.0, 6.0, 1);
        let high_s = model.cost_for(0.8, 9.0, 2.0, 6.0, 1);
        assert!(high_s > low_s);
    }

    #[test]
    fn review_time_model_uses_repetition_factor() {
        let model = synthetic_fail_model();
        let low_reps = model.cost_for(0.8, 7.0, 1.0, 6.0, 1);
        let high_reps = model.cost_for(0.8, 7.0, 4.0, 6.0, 1);
        assert!(high_reps > low_reps);
    }

    #[test]
    fn review_time_model_review_cost_uses_card_repetitions() {
        let model = synthetic_fail_model();
        let low_reps = model.review_cost_for_rating(0.8, 7.0, 1.0, 6.0, 1);
        let high_reps = model.review_cost_for_rating(0.8, 7.0, 4.0, 6.0, 1);
        assert!(high_reps > low_reps);
    }

    #[test]
    fn review_time_cost_fn_uses_simulated_card_repetitions() {
        let mut config = SimulatorConfig::default();
        install_review_time_cost_fn(&mut config, Arc::new(synthetic_fail_model()));
        let cost_fn = config.review_rating_cost_fn.as_ref().unwrap();
        let low_reps = SimCard {
            stability: 7.0,
            difficulty: 6.0,
            reps: 1,
            ..Default::default()
        };
        let high_reps = SimCard {
            reps: 4,
            ..low_reps.clone()
        };

        assert!(cost_fn(&high_reps, 1, 0.8) > cost_fn(&low_reps, 1, 0.8));
    }

    #[test]
    fn review_time_model_uses_difficulty_factor() {
        let model = synthetic_fail_model();
        let low_d = model.cost_for(0.8, 7.0, 2.0, 5.0, 1);
        let high_d = model.cost_for(0.8, 7.0, 2.0, 8.0, 1);
        assert!(high_d > low_d);
    }

    #[test]
    fn grade_weights_default_to_uniform_without_transitions() {
        let model = HelpMeDecideReviewTimeModel::from_samples(
            &[
                (0.8, 5.0, 2.0, 5.0, 1, 20.0),
                (0.8, 5.0, 2.0, 5.0, 2, 20.0),
                (0.8, 5.0, 2.0, 5.0, 2, 20.0),
                (0.8, 5.0, 2.0, 5.0, 3, 20.0),
            ],
            no_transitions(),
            false,
            [1.0, 1.0, 1.0, 1.0],
        );
        let weights = model.grade_weights();
        assert!((weights[0] - 0.25).abs() < 1e-6);
        assert!((weights[1] - 0.25).abs() < 1e-6);
        assert!((weights[2] - 0.25).abs() < 1e-6);
        assert!((weights[3] - 0.25).abs() < 1e-6);
    }

    #[test]
    fn transition_probabilities_and_weights_follow_observed_chain() {
        let transitions = [
            [0, 0, 0, 0],
            [0, 0, 2, 0], // Hard -> Good
            [0, 0, 0, 3], // Good -> Easy
            [4, 0, 0, 0], /* Easy -> Again
                           * Again row has no outgoing transitions in this synthetic example. */
        ];
        let model = HelpMeDecideReviewTimeModel::from_samples(
            &[(0.8, 5.0, 2.0, 5.0, 2, 20.0)],
            transitions,
            false,
            [1.0, 1.0, 1.0, 1.0],
        );
        let probs = model.transition_probs_flattened();
        // Hard->Good = 1.0
        assert!((probs[6] - 1.0).abs() < 1e-6);
        // Good->Easy = 1.0
        assert!((probs[2 * 4 + 3] - 1.0).abs() < 1e-6);
        // Easy->Again = 1.0
        assert!((probs[3 * 4] - 1.0).abs() < 1e-6);
        let success_probs = model.success_review_rating_prob([0.2, 0.6, 0.2]);
        let sum = success_probs.iter().sum::<f32>();
        assert!((sum - 1.0).abs() < 1e-6);
    }

    #[test]
    fn success_grade_probability_depends_on_r_bucket() {
        let mut samples = Vec::new();
        for _ in 0..20 {
            samples.push((0.95, 5.0, 3.0, 5.0, 4, 8.0));
            samples.push((0.95, 5.0, 3.0, 5.0, 3, 9.0));
            samples.push((0.60, 5.0, 3.0, 5.0, 2, 12.0));
            samples.push((0.60, 5.0, 3.0, 5.0, 3, 10.0));
        }
        let model = HelpMeDecideReviewTimeModel::from_samples(
            &samples,
            no_transitions(),
            false,
            [1.0, 1.0, 1.0, 1.0],
        );
        let p_hi = model.success_review_rating_prob_for_retrievability(0.95, [0.2, 0.6, 0.2], None);
        let p_lo = model.success_review_rating_prob_for_retrievability(0.60, [0.2, 0.6, 0.2], None);
        // Easy at high R should exceed easy at low R.
        assert!(p_hi[2] > p_lo[2]);
        // Hard at low R should exceed hard at high R.
        assert!(p_lo[0] > p_hi[0]);
    }

    #[test]
    fn success_grade_probs_shrink_sparse_buckets_to_neighbor_trend() {
        let mut samples = Vec::new();
        // Neighboring buckets around low-R with stable ~20/80 hard/good split.
        for _ in 0..200 {
            samples.push((0.35, 5.0, 3.0, 5.0, 2, 12.0));
            samples.push((0.35, 5.0, 3.0, 5.0, 3, 10.0));
            samples.push((0.35, 5.0, 3.0, 5.0, 3, 10.0));
            samples.push((0.35, 5.0, 3.0, 5.0, 3, 10.0));
            samples.push((0.45, 5.0, 3.0, 5.0, 2, 12.0));
            samples.push((0.45, 5.0, 3.0, 5.0, 3, 10.0));
            samples.push((0.45, 5.0, 3.0, 5.0, 3, 10.0));
            samples.push((0.45, 5.0, 3.0, 5.0, 3, 10.0));
        }
        let model = HelpMeDecideReviewTimeModel::from_samples(
            &samples,
            no_transitions(),
            false,
            [1.0, 1.0, 1.0, 1.0],
        );
        let rb_sparse = HelpMeDecideReviewTimeModel::r_bucket(0.25);
        let probs = model.success_grade_probs_flattened();
        let p_hard_sparse = probs[rb_sparse * 3];
        let p_good_sparse = probs[rb_sparse * 3 + 1];
        // Should follow neighboring trend instead of defaulting to 1/3,1/3,1/3.
        assert!(p_hard_sparse < 0.30);
        assert!(p_good_sparse > 0.60);
    }

    #[test]
    fn success_grade_blend_alpha_override_works() {
        let transitions = [
            [0, 0, 0, 0],
            [0, 0, 10, 0], // Hard -> Good
            [0, 0, 0, 10], // Good -> Easy
            [10, 0, 0, 0], // Easy -> Again
        ];
        let mut samples = Vec::new();
        for _ in 0..20 {
            // High-R mostly Easy in bucket model
            samples.push((0.95, 5.0, 3.0, 5.0, 4, 8.0));
            samples.push((0.95, 5.0, 3.0, 5.0, 4, 8.0));
            samples.push((0.95, 5.0, 3.0, 5.0, 2, 12.0));
        }
        let model = HelpMeDecideReviewTimeModel::from_samples(
            &samples,
            transitions,
            false,
            [1.0, 1.0, 1.0, 1.0],
        );
        let p_r_only =
            model.success_review_rating_prob_for_retrievability(0.95, [0.2, 0.6, 0.2], Some(0.0));
        let p_t_only =
            model.success_review_rating_prob_for_retrievability(0.95, [0.2, 0.6, 0.2], Some(1.0));
        // R-only should prefer Easy, transition-only prior should lean away from Hard
        // in this setup.
        assert!(p_r_only[2] > p_r_only[0]);
        assert!(p_t_only[1] >= p_t_only[0]);
    }

    #[test]
    fn monotonic_toggle_enforces_hard_and_easy_trends() {
        let mut samples = Vec::new();
        // Intentionally noisy pattern across neighboring buckets.
        for _ in 0..120 {
            samples.push((0.62, 5.0, 3.0, 5.0, 2, 12.0)); // hard
            samples.push((0.62, 5.0, 3.0, 5.0, 3, 10.0)); // good
            samples.push((0.58, 5.0, 3.0, 5.0, 3, 10.0)); // good-heavy lower R
            samples.push((0.58, 5.0, 3.0, 5.0, 4, 8.0)); // easy noise
        }
        let model = HelpMeDecideReviewTimeModel::from_samples(
            &samples,
            no_transitions(),
            true,
            [1.0, 1.0, 1.0, 1.0],
        );
        let probs = model.success_grade_probs_flattened();
        for rb in 1..R_BUCKET_COUNT {
            let hard_prev = probs[(rb - 1) * 3];
            let hard_cur = probs[rb * 3];
            let easy_prev = probs[(rb - 1) * 3 + 2];
            let easy_cur = probs[rb * 3 + 2];
            // As R decreases with rb index, hard should not go down and easy should not go
            // up.
            assert!(hard_cur + 1e-6 >= hard_prev);
            assert!(easy_cur <= easy_prev + 1e-6);
        }
    }

    #[test]
    fn repetition_filter_for_regression_is_2_to_30_inclusive() {
        assert!(!include_repetitions_in_regression(1.0));
        assert!(include_repetitions_in_regression(2.0));
        assert!(include_repetitions_in_regression(30.0));
        assert!(!include_repetitions_in_regression(31.0));
    }

    #[test]
    fn review_repetition_counter_uses_review_events_only() {
        let mut prior_review_repetitions = 0;
        assert_eq!(
            consume_review_repetition(&mut prior_review_repetitions, false),
            None
        );
        assert_eq!(
            consume_review_repetition(&mut prior_review_repetitions, true),
            Some(0.0)
        );
        assert_eq!(
            consume_review_repetition(&mut prior_review_repetitions, false),
            None
        );
        assert_eq!(
            consume_review_repetition(&mut prior_review_repetitions, true),
            Some(1.0)
        );
        assert_eq!(
            consume_review_repetition(&mut prior_review_repetitions, true),
            Some(2.0)
        );
    }

    #[test]
    fn simulation_overrides_card_desired_retention_with_request_value() {
        let mut card = Card {
            desired_retention: Some(0.75),
            ..Default::default()
        };
        apply_simulation_desired_retention(&mut card, 0.9);
        assert_eq!(card.desired_retention, Some(0.9));
    }

    #[test]
    fn empty_simulation_params_keep_fsrs6_defaults() -> crate::error::Result<()> {
        let config = SimulatorConfig {
            deck_size: 10,
            learn_span: 30,
            ..Default::default()
        };
        let empty = simulate_workload_for_desired_retention(&config, &[], &[], 0.9, None)?;
        let explicit = simulate_workload_for_desired_retention(
            &config,
            &fsrs::FSRS6_DEFAULT_PARAMETERS,
            &[],
            0.9,
            None,
        )?;

        assert!(empty.review_cnt_per_day.iter().sum::<usize>() > 0);
        assert_eq!(empty.review_cnt_per_day, explicit.review_cnt_per_day);
        assert_eq!(empty.memorized_cnt_per_day, explicit.memorized_cnt_per_day);
        Ok(())
    }

    #[test]
    fn simulation_overrides_existing_sim_cards_for_each_workload_dr() {
        let config = SimulatorConfig {
            deck_size: 1,
            learn_span: 365,
            learn_limit: 0,
            review_limit: 9999,
            review_rating_prob: [0.0, 1.0, 0.0],
            ..Default::default()
        };
        let card = SimCard {
            id: 1,
            difficulty: 5.0,
            stability: 100.0,
            last_date: -10.0,
            due: 0.0,
            interval: 10.0,
            reps: 0,
            lapses: 0,
            desired_retention: 0.95,
            parameters: std::sync::Arc::new(DEFAULT_PARAMETERS.to_vec()),
        };
        let cards = vec![card];

        let low_dr = simulate_workload_for_desired_retention(
            &config,
            &DEFAULT_PARAMETERS,
            &cards,
            0.6,
            None,
        )
        .unwrap();
        let high_dr = simulate_workload_for_desired_retention(
            &config,
            &DEFAULT_PARAMETERS,
            &cards,
            0.9,
            None,
        )
        .unwrap();

        let low_reviews = low_dr.review_cnt_per_day.iter().sum::<usize>();
        let high_reviews = high_dr.review_cnt_per_day.iter().sum::<usize>();
        let low_interval = low_dr.cards[0].interval;
        let high_interval = high_dr.cards[0].interval;
        assert!(
            low_reviews <= high_reviews,
            "lower DR should not increase reviews when existing cards are overridden per DR"
        );
        assert!(
            low_interval > high_interval,
            "lower DR should produce longer intervals when existing cards are overridden per DR"
        );
    }

    #[test]
    fn dynamic_desired_retention_out_of_range_uses_fixed_workload_simulation() {
        let config = SimulatorConfig {
            deck_size: 1,
            learn_span: 365,
            learn_limit: 0,
            review_limit: 9999,
            review_rating_prob: [0.0, 1.0, 0.0],
            ..Default::default()
        };
        let card = SimCard {
            id: 1,
            difficulty: 5.0,
            stability: 100.0,
            last_date: -10.0,
            due: 0.0,
            interval: 10.0,
            reps: 0,
            lapses: 0,
            desired_retention: 0.9,
            parameters: std::sync::Arc::new(DEFAULT_PARAMETERS.to_vec()),
        };
        let cards = vec![card];
        let request = dynamic_desired_retention_request();
        let preset_router = SimulationPresetRouter {
            fallback: simulation_fallback_preset(&request).unwrap(),
            presets_by_card: HashMap::new(),
            routes: Vec::new(),
        };

        let fixed = simulate_workload_for_desired_retention(
            &config,
            &DEFAULT_PARAMETERS,
            &cards,
            0.95,
            None,
        )
        .unwrap();
        let dynamic_fallback = simulate_workload_for_desired_retention(
            &config,
            &DEFAULT_PARAMETERS,
            &cards,
            0.95,
            Some(&preset_router),
        )
        .unwrap();

        assert_eq!(
            fixed.review_cnt_per_day,
            dynamic_fallback.review_cnt_per_day
        );
        assert_eq!(fixed.learn_cnt_per_day, dynamic_fallback.learn_cnt_per_day);
        assert_eq!(fixed.cost_per_day, dynamic_fallback.cost_per_day);
    }

    #[test]
    fn dynamic_desired_retention_toggle_changes_workload_simulation() {
        let config = SimulatorConfig {
            deck_size: 1,
            learn_span: 1,
            learn_limit: 0,
            review_limit: 9999,
            review_rating_prob: [0.0, 1.0, 0.0],
            ..Default::default()
        };
        let card = SimCard {
            id: 1,
            difficulty: 5.0,
            stability: 10.0,
            last_date: -1.0,
            due: 0.0,
            interval: 1.0,
            reps: 0,
            lapses: 0,
            desired_retention: 0.85,
            parameters: std::sync::Arc::new(DEFAULT_PARAMETERS.to_vec()),
        };
        let cards = vec![card];
        let request = SimulateFsrsReviewRequest {
            fsrs_dynamic_desired_retention_fixed_target_weights: vec![1024.0],
            fsrs_dynamic_desired_retention_fixed_target_drs: vec![0.9],
            ..dynamic_desired_retention_request()
        };
        let preset_router = SimulationPresetRouter {
            fallback: simulation_fallback_preset(&request).unwrap(),
            presets_by_card: HashMap::new(),
            routes: Vec::new(),
        };
        let dynamic_for_target = preset_router
            .fallback
            .dynamic_desired_retention
            .as_ref()
            .unwrap()
            .for_target(0.85)
            .unwrap()
            .unwrap();
        assert_eq!(dynamic_for_target.cost_weight, 1024.0);
        assert!(
            dynamic_for_target
                .policy
                .evaluate_retention(10.0, 5.0, dynamic_for_target.cost_weight)
                < 0.85
        );

        let fixed = simulate_workload_for_desired_retention(
            &config,
            &DEFAULT_PARAMETERS,
            &cards,
            0.85,
            None,
        )
        .unwrap();
        let dynamic = simulate_workload_for_desired_retention(
            &config,
            &DEFAULT_PARAMETERS,
            &cards,
            0.85,
            Some(&preset_router),
        )
        .unwrap();

        assert_ne!(fixed.cards[0].interval, dynamic.cards[0].interval);
    }

    fn simulate_summary_with_full_router_update_fn(
        config: &SimulatorConfig,
        params: &[f32],
        cards: &[SimCard],
        desired_retention: f32,
        preset_router: &SimulationPresetRouter,
    ) -> fsrs::SimulationSummaryResult {
        let mut cards_for_dr = cards.to_vec();
        super::apply_simulation_desired_retention_to_cards(&mut cards_for_dr, desired_retention);
        let card_update_fn = preset_router.card_update_fn(desired_retention).unwrap();
        fsrs::simulate_summary_with_card_update_fn(
            config,
            params,
            desired_retention,
            None,
            Some(cards_for_dr),
            &card_update_fn,
        )
        .unwrap()
    }

    fn assert_summary_result_matches(
        left: &fsrs::SimulationSummaryResult,
        right: &fsrs::SimulationSummaryResult,
    ) {
        assert_eq!(left.memorized, right.memorized);
        assert_eq!(left.review_count, right.review_count);
        assert_eq!(left.learn_count, right.learn_count);
        assert_eq!(left.cost, right.cost);
        assert_eq!(left.cards.len(), right.cards.len());
    }

    #[test]
    fn fixed_no_route_preset_router_skips_card_update_fn_without_changing_summary() {
        let config = SimulatorConfig {
            deck_size: 1,
            learn_span: 30,
            learn_limit: 0,
            review_limit: 9999,
            review_rating_prob: [0.0, 1.0, 0.0],
            ..Default::default()
        };
        let parameters = Arc::new(DEFAULT_PARAMETERS.to_vec());
        let card = SimCard {
            id: 1,
            difficulty: 5.0,
            stability: 10.0,
            last_date: -1.0,
            due: 0.0,
            interval: 1.0,
            reps: 1,
            lapses: 0,
            desired_retention: 0.8,
            parameters: parameters.clone(),
        };
        let router = SimulationPresetRouter {
            fallback: SimulationPreset {
                name: "Fallback".into(),
                parameters: Some(Arc::new(DEFAULT_PARAMETERS.to_vec())),
                dynamic_desired_retention: None,
            },
            presets_by_card: HashMap::from([(
                1,
                SimulationPreset {
                    name: "Card preset".into(),
                    parameters: Some(parameters),
                    dynamic_desired_retention: None,
                },
            )]),
            routes: Vec::new(),
        };

        assert!(router.card_update_fn_for_simulation(0.9).unwrap().is_none());
        let fast = super::simulate_workload_summary_for_desired_retention(
            &config,
            &DEFAULT_PARAMETERS,
            std::slice::from_ref(&card),
            0.9,
            Some(&router),
        )
        .unwrap();
        let full = simulate_summary_with_full_router_update_fn(
            &config,
            &DEFAULT_PARAMETERS,
            &[card],
            0.9,
            &router,
        );

        assert_summary_result_matches(&fast, &full);
    }

    #[test]
    fn dynamic_no_route_preset_router_uses_static_adr_update_fn_without_changing_summary() {
        let config = SimulatorConfig {
            deck_size: 1,
            learn_span: 30,
            learn_limit: 0,
            review_limit: 9999,
            review_rating_prob: [0.0, 1.0, 0.0],
            ..Default::default()
        };
        let card = SimCard {
            id: 1,
            difficulty: 5.0,
            stability: 10.0,
            last_date: -1.0,
            due: 0.0,
            interval: 1.0,
            reps: 1,
            lapses: 0,
            desired_retention: 0.8,
            parameters: Arc::new(DEFAULT_PARAMETERS.to_vec()),
        };
        let request = SimulateFsrsReviewRequest {
            fsrs_dynamic_desired_retention_fixed_target_weights: vec![1024.0],
            fsrs_dynamic_desired_retention_fixed_target_drs: vec![0.9],
            ..dynamic_desired_retention_request()
        };
        let router = SimulationPresetRouter {
            fallback: simulation_fallback_preset(&request).unwrap(),
            presets_by_card: HashMap::new(),
            routes: Vec::new(),
        };

        assert!(router
            .card_update_fn_for_simulation(0.85)
            .unwrap()
            .is_some());
        let fast = super::simulate_workload_summary_for_desired_retention(
            &config,
            &DEFAULT_PARAMETERS,
            std::slice::from_ref(&card),
            0.85,
            Some(&router),
        )
        .unwrap();
        let full = simulate_summary_with_full_router_update_fn(
            &config,
            &DEFAULT_PARAMETERS,
            &[card],
            0.85,
            &router,
        );

        assert_summary_result_matches(&fast, &full);
    }

    #[test]
    fn split_workload_attributes_reviews_to_active_preset_at_each_rep() {
        let config = SimulatorConfig {
            deck_size: 1,
            learn_span: 90,
            learn_limit: 0,
            review_limit: 9999,
            review_rating_prob: [0.0, 1.0, 0.0],
            ..Default::default()
        };
        let card = SimCard {
            id: 1,
            difficulty: 5.0,
            stability: 5.0,
            last_date: -1.0,
            due: 0.0,
            interval: 1.0,
            reps: 1,
            lapses: 0,
            desired_retention: 0.9,
            parameters: Arc::new(DEFAULT_PARAMETERS.to_vec()),
        };
        let router = SimulationPresetRouter {
            fallback: SimulationPreset {
                name: "Fallback".into(),
                parameters: Some(Arc::new(DEFAULT_PARAMETERS.to_vec())),
                dynamic_desired_retention: None,
            },
            presets_by_card: HashMap::new(),
            routes: vec![SimulationPresetRoute {
                card_ids: None,
                min_reps: Some(2),
                max_reps: None,
                min_interval_days: None,
                max_interval_days: None,
                preset: SimulationPreset {
                    name: "Routed".into(),
                    parameters: Some(Arc::new(DEFAULT_PARAMETERS.to_vec())),
                    dynamic_desired_retention: None,
                },
            }],
        };

        let (result, split) = super::simulate_workload_split_summary_for_desired_retention(
            &config,
            &DEFAULT_PARAMETERS,
            std::slice::from_ref(&card),
            0.9,
            Some(&router),
            90,
            "Fallback",
        )
        .unwrap();

        assert!(split["Fallback"].review_count > 0);
        assert!(split["Routed"].review_count > 0);
        assert_eq!(
            split.values().map(|point| point.review_count).sum::<u32>(),
            result.review_count as u32
        );
        assert_eq!(
            split.values().map(|point| point.learn_count).sum::<u32>(),
            result.learn_count as u32
        );
        assert!((split.values().map(|point| point.cost).sum::<f32>() - result.cost).abs() < 1e-3);

        let reviewless_split = super::preset_reviewless_workload_for_cards(
            std::slice::from_ref(&card),
            Some(&router),
            "Fallback",
            super::simulation_end_date(90),
        );
        let reviewless_total = reviewless_split
            .values()
            .map(|point| point.memorized)
            .sum::<f32>();
        let global_reviewless = card.retention_on(super::simulation_end_date(90));
        assert!((reviewless_total - global_reviewless).abs() < 1e-6);
    }

    #[test]
    fn apply_simulation_desired_retention_overrides_all_sim_cards() {
        let mut cards = vec![
            SimCard {
                desired_retention: 0.8,
                ..Default::default()
            },
            SimCard {
                desired_retention: 0.95,
                ..Default::default()
            },
        ];
        apply_simulation_desired_retention_to_cards(&mut cards, 0.7);
        assert!(cards
            .iter()
            .all(|c| (c.desired_retention - 0.7).abs() < 1e-6));
    }
}

#[cfg(test)]
mod test {
    use anki_proto::scheduler::SimulateFsrsReviewRequest;

    use super::*;
    use crate::services::NotesService;

    fn base_request() -> SimulateFsrsReviewRequest {
        SimulateFsrsReviewRequest {
            params: vec![], // leave empty so fsrs fills defaults in tests
            desired_retention: 0.85f32,
            deck_size: 50u32,
            days_to_simulate: 20u32,
            new_limit: 5u32,
            review_limit: 1000u32,
            max_interval: 365u32,
            search: "".to_string(),
            new_cards_ignore_review_limit: false,
            easy_days_percentages: vec![0.0f32, 0.0f32, 0.0f32, 0.0f32, 0.0f32, 0.0f32, 0.0f32],
            review_order: ReviewCardOrder::Random as i32,
            suspend_after_lapse_count: Some(3u32),
            historical_retention: 0.9f32,
            learning_step_count: 3u32,
            relearning_step_count: 2u32,
            ..Default::default()
        }
    }

    #[test]
    fn simulate_workload_varies() {
        let mut col = Collection::new();

        let resp = col.simulate_workload(base_request()).unwrap();

        // Regression guard for #5101: the workload simulation must apply the per-card
        // desired retention, so cost has to grow with the target retention. A plain
        // "the values differ" check is not enough here: even with the bug present the
        // costs vary due to simulation noise, so we assert the actual trend instead by
        // comparing the low- and high-retention buckets.
        let cost = |dr: u32| resp.cost[&dr];
        let low_avg = (70..=79).map(cost).sum::<f32>() / 10.0;
        let high_avg = (90..=99).map(cost).sum::<f32>() / 10.0;

        assert!(
            high_avg > low_avg * 1.2,
            "expected workload cost to grow with desired retention, instead got \
             low_avg={low_avg}, high_avg={high_avg}, cost={:?}",
            resp.cost
        );
    }

    #[test]
    fn request_config_uses_requested_retention() -> Result<()> {
        // Regression guard for the other half of #5101 ("for normal simulator too"):
        // simulate_request_to_config must apply the *requested* desired retention to
        // every card, ignoring each card's individually stored value. This helper feeds
        // the normal simulator, the workload simulator and optimal-retention
        // computation, so pinning it here covers all three callers without simulation
        // noise.
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let mut card = col
            .storage
            .all_cards_of_note(note.id)?
            .into_iter()
            .next()
            .unwrap();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 100;
        card.memory_state = Some(FsrsMemoryState {
            stability: 100.0,
            stability_internal: 100.0,
            stability_fast: None,
            difficulty: 5.0,
        });
        // Deliberately differs from the requested retention below.
        card.desired_retention = Some(0.70);
        card.decay = Some(0.2);
        col.storage.update_card(&card)?;

        let req = SimulateFsrsReviewRequest {
            desired_retention: 0.95f32,
            // Isolate the existing review card: no synthetic new cards.
            deck_size: 0u32,
            new_limit: 0u32,
            ..base_request()
        };

        let (_config, cards) = col.simulate_request_to_config(&req)?;

        assert!(
            !cards.is_empty(),
            "expected the review card to be converted"
        );
        assert!(
            cards
                .iter()
                .all(|c| (c.desired_retention - 0.95).abs() < 1e-6),
            "converted cards must use the requested retention (0.95), instead got {:?}",
            cards.iter().map(|c| c.desired_retention).collect_vec()
        );

        Ok(())
    }

    #[test]
    fn simulate_uses_dr() {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false).unwrap();

        let note = crate::tests::NoteAdder::basic(&mut col).add(&mut col);
        let cids = col.cards_of_note(note.id.into()).unwrap().cids;
        let cids = cids.into_iter().map(CardId).collect_vec();
        col.grade_now(anki_proto::scheduler::GradeNowRequest {
            card_ids: cids.iter().map(|cid| cid.0).collect(),
            rating: 3,
            card_options: vec![],
        })
        .unwrap();

        let mut req = base_request();
        req.deck_size = 0;

        let (_, cards) = col.simulate_request_to_config(&req).unwrap();
        assert_eq!(cards.len(), 1);
        assert_eq!(cards[0].desired_retention, 0.85f32)
    }

    #[test]
    fn simulator_does_not_crash_with_review_priority_functions() {
        let mut col = Collection::new();
        let review_orders = [
            ReviewCardOrder::Day,
            ReviewCardOrder::DayThenDeck,
            ReviewCardOrder::DeckThenDay,
            ReviewCardOrder::IntervalsAscending,
            ReviewCardOrder::IntervalsDescending,
            ReviewCardOrder::EaseAscending,
            ReviewCardOrder::EaseDescending,
            ReviewCardOrder::RetrievabilityAscending,
            ReviewCardOrder::RetrievabilityDescending,
            ReviewCardOrder::RelativeOverdueness,
            ReviewCardOrder::Random,
            ReviewCardOrder::Added,
            ReviewCardOrder::ReverseAdded,
        ];

        let parameters = [
            &fsrs::DEFAULT_PARAMETERS[..21], /* FSRS-6 (Sliced due to the potential of more
                                              * parameters in the future) */
            &fsrs::DEFAULT_PARAMETERS[..19], // FSRS-5
            &fsrs::DEFAULT_PARAMETERS[..17], // FSRS-4
        ];

        for params in parameters {
            for review_order in review_orders {
                let req = SimulateFsrsReviewRequest {
                    params: params.to_vec(),
                    review_order: review_order as i32,
                    ..base_request()
                };
                let _ = col.simulate_review(req).unwrap();
            }
        }
    }

    fn infer_ctype_from_queue(queue: CardQueue) -> CardType {
        // None of the code in this file references the card type but
        // this is included to match up with actual functionality in case it ever does.
        match queue {
            CardQueue::Learn | CardQueue::PreviewRepeat => CardType::Learn,
            CardQueue::DayLearn | CardQueue::Review => CardType::Review,
            CardQueue::New => CardType::New,
            CardQueue::Suspended | CardQueue::SchedBuried | CardQueue::UserBuried => {
                CardType::Review
            }
        }
    }

    fn card_from_queue(queue: CardQueue) -> Card {
        Card {
            queue,
            ctype: infer_ctype_from_queue(queue),
            ..Default::default()
        }
    }

    fn convert(card: Card) -> Option<fsrs::Card> {
        let params = Arc::new(fsrs::check_and_fill_parameters(&[]).unwrap());
        let memory_state = FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: None,
            difficulty: 3.0,
        };
        Card::convert(card, 10, memory_state, 0.85, params.clone())
    }

    #[test]
    fn card_convert_clamps_last_date() {
        let future = convert(Card {
            queue: CardQueue::Review,
            ctype: CardType::Review,
            due: 20,
            original_due: 20,
            interval: 5,
            ..Default::default()
        })
        .unwrap();
        assert_eq!(future.last_date, 0.0);
        assert_eq!(future.due, 10.0);

        let past = convert(Card {
            queue: CardQueue::Review,
            ctype: CardType::Review,
            due: 20,
            original_due: 20,
            interval: 30,
            ..Default::default()
        })
        .unwrap();
        assert_eq!(past.last_date, -20.0);

        let filtered = convert(Card {
            queue: CardQueue::Review,
            ctype: CardType::Review,
            due: 7,
            original_due: 42,
            original_deck_id: DeckId(1),
            interval: 15,
            ..Default::default()
        })
        .unwrap();
        assert_eq!(filtered.due, 32.0);
    }

    #[test]
    fn card_convert_handles_learning_cards() {
        let learn = convert(Card {
            queue: CardQueue::Learn,
            ctype: CardType::Learn,
            interval: 10,
            ..Default::default()
        })
        .unwrap();
        assert_eq!(learn.last_date, 0.0);
        assert_eq!(learn.due, 0.0);
    }

    #[test]
    fn card_convert_resets_due_and_last_date_for_learn_and_buried_cards() {
        // Non-trivial due and interval, so that treating these queues like review
        // cards (due = 10, last_date = -5 here) would fail the assertions.
        for queue in [
            CardQueue::Learn,
            CardQueue::SchedBuried,
            CardQueue::UserBuried,
        ] {
            let converted = convert(Card {
                due: 20,
                original_due: 20,
                interval: 15,
                lapses: 2,
                ..card_from_queue(queue)
            })
            .unwrap();
            assert_eq!(converted.due, 0.0, "due for {queue:?}");
            assert_eq!(converted.last_date, 0.0, "last_date for {queue:?}");
            assert_eq!(converted.interval, 15.0, "interval for {queue:?}");
            assert_eq!(converted.lapses, 2, "lapses for {queue:?}");
        }
    }

    #[test]
    fn card_convert_handles_day_learn_cards() {
        // Cards in the day learning queue are converted like review cards: relative
        // due and interval are kept, unlike intraday learning cards.
        let day_learn = convert(Card {
            queue: CardQueue::DayLearn,
            ctype: CardType::Relearn,
            due: 20,
            original_due: 20,
            interval: 15,
            lapses: 2,
            ..Default::default()
        })
        .unwrap();
        assert_eq!(day_learn.due, 10.0);
        assert_eq!(day_learn.last_date, -5.0);
        assert_eq!(day_learn.interval, 15.0);
        assert_eq!(day_learn.lapses, 2);
    }

    #[test]
    fn card_convert_handles_queues() {
        for queue in [
            CardQueue::Review,
            CardQueue::DayLearn,
            CardQueue::Learn,
            CardQueue::SchedBuried,
            CardQueue::UserBuried,
        ] {
            assert!(convert(card_from_queue(queue)).is_some());
        }

        for queue in [
            CardQueue::New,
            CardQueue::PreviewRepeat,
            CardQueue::Suspended,
        ] {
            assert!(convert(card_from_queue(queue)).is_none());
        }
    }

    /// Adds a review card that has no memory state, as happens when its revlog
    /// is truncated or ignored, so the simulator has to fall back to SM-2.
    fn col_with_review_card_without_memory_state() -> Result<(Collection, Card)> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let mut card = col
            .storage
            .all_cards_of_note(note.id)?
            .into_iter()
            .next()
            .unwrap();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.due = 25;
        card.original_due = 25;
        card.interval = 20;
        card.ease_factor = 2500;
        card.memory_state = None;
        card.desired_retention = None;
        card.decay = None;
        col.storage.update_card(&card)?;
        Ok((col, card))
    }

    #[test]
    fn request_config_uses_sm2_fallback_and_fills_fsrs_values() -> Result<()> {
        let (mut col, card) = col_with_review_card_without_memory_state()?;

        // Use a decay that differs from the default so we can tell that it comes from
        // the request's params and not from a fallback.
        let mut params = fsrs::DEFAULT_PARAMETERS.to_vec();
        params[20] = 0.3;

        let req = SimulateFsrsReviewRequest {
            params: params.clone(),
            desired_retention: 0.91,
            deck_size: 1,
            new_limit: 0,
            ..base_request()
        };

        let (_, cards) = col.simulate_request_to_config(&req)?;

        assert_eq!(cards.len(), 1);
        // Make sure that the cards desired rentetion is filled in automatically
        assert!(
            (cards[0].desired_retention - 0.91).abs() < 1e-6,
            "expected desired retention to be 0.91, got {}",
            cards[0].desired_retention
        );
        // Parameter 20 controls decay, so this also checks that the decay comes from
        // the request.
        assert_eq!(
            *cards[0].parameters, params,
            "expected the converted card to carry the requested params"
        );
        // The memory state must be the one derived from the card's ease and interval
        let expected = FSRS::new(&req.params)?.memory_state_from_sm2(
            card.ease_factor(),
            card.interval as f32,
            req.historical_retention,
        )?;
        assert_eq!(cards[0].stability, expected.stability);
        assert_eq!(cards[0].difficulty, expected.difficulty);
        assert!(
            cards[0].stability > 0.0,
            "expected stability to be positive, got {}",
            cards[0].stability
        );
        assert!(
            cards[0].difficulty.is_finite(),
            "expected difficulty to be finite, got {}",
            cards[0].difficulty
        );
        assert!(
            cards[0].last_date <= 0.0,
            "expected last_date to be <= 0, got {}",
            cards[0].last_date
        );

        let card_1 = cards[0].clone();

        // Reset the card
        col.storage.update_card(&card)?;

        let req = SimulateFsrsReviewRequest {
            historical_retention: 0.80,
            ..req
        };

        let (_, cards) = col.simulate_request_to_config(&req)?;

        // If this errors then sm2 was not used as a fallback as it is the only part to
        // use historical_retention
        assert_ne!(
            card_1.stability, cards[0].stability,
            "expected stability to change with historical retention"
        );

        Ok(())
    }

    #[test]
    fn request_config_drops_cards_when_sm2_fallback_fails() -> Result<()> {
        let (mut col, _) = col_with_review_card_without_memory_state()?;

        let req = SimulateFsrsReviewRequest {
            deck_size: 1,
            new_limit: 0,
            ..base_request()
        };
        let (_, cards) = col.simulate_request_to_config(&req)?;
        assert_eq!(cards.len(), 1, "the card converts with a valid retention");

        // A historical retention of 1.0 makes the SM-2 conversion return an error
        // (infinite stability), so the card must be skipped instead of failing the
        // whole simulation.
        let req = SimulateFsrsReviewRequest {
            historical_retention: 1.0,
            ..req
        };
        let (config, cards) = col.simulate_request_to_config(&req)?;
        assert!(cards.is_empty(), "expected the card to be dropped");
        assert_eq!(config.deck_size, 0);

        Ok(())
    }

    #[test]
    fn request_config_keeps_requested_step_counts() -> Result<()> {
        let mut col = Collection::new();

        // Different from each other and from the base request (3 and 2), so that
        // swapping them or falling back to a default would be noticed.
        let req = SimulateFsrsReviewRequest {
            learning_step_count: 4,
            relearning_step_count: 5,
            ..base_request()
        };
        let (config, _) = col.simulate_request_to_config(&req)?;
        assert_eq!(config.learning_step_count, 4);
        assert_eq!(config.relearning_step_count, 5);

        // No steps at all must be preserved too, not replaced by a default.
        let req = SimulateFsrsReviewRequest {
            learning_step_count: 0,
            relearning_step_count: 0,
            ..base_request()
        };
        let (config, _) = col.simulate_request_to_config(&req)?;
        assert_eq!(config.learning_step_count, 0);
        assert_eq!(config.relearning_step_count, 0);

        Ok(())
    }

    #[test]
    fn simulate_request_to_config_counts_already_introduced_cards() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        col.set_config_bool(BoolKey::Fsrs, true, false).unwrap();

        // Answer one card so one card is introduced today, and then simulate with
        // three new cards to be introduced
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        col.answer_easy();

        let req = SimulateFsrsReviewRequest {
            desired_retention: 0.9,
            deck_size: 3, // 3 additional new cards to be introduced
            new_limit: 3,
            days_to_simulate: 1,
            search: "*".to_string(),
            ..base_request()
        };

        let (_, cards) = col.simulate_request_to_config(&req)?;

        // New cards are the only ones without a previous review.
        let new_card_dues = cards
            .iter()
            .filter(|c| c.last_date == f32::NEG_INFINITY)
            .map(|c| c.due)
            .collect_vec();

        // With a limit of 3 per day and 1 card already introduced today, only 2 of the
        // new cards fit in the first day. Ignoring the already introduced card would
        // give [0.0, 0.0, 0.0] instead.
        assert_eq!(new_card_dues, vec![0.0, 0.0, 1.0]);
        // The already introduced card is also part of the simulation.
        assert_eq!(cards.len(), 4);

        Ok(())
    }
}
