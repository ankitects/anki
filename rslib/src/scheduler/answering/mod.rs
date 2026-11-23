// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod current;
mod learning;
mod preview;
mod relearning;
mod review;
mod revlog;

#[cfg(test)]
mod preview_s90_tests;

use anki_i18n::I18n;
use fsrs::NextStates;
use fsrs::FSRS;
use rand::prelude::*;
use rand::rngs::StdRng;
use revlog::RevlogEntryPartial;

use super::queue::BuryMode;
use super::queue::QueueEntry;
use super::states::load_balancer::LoadBalancerContext;
use super::states::steps::LearningSteps;
use super::states::CardState;
use super::states::FilteredState;
use super::states::NormalState;
use super::states::ReviewState;
use super::states::SchedulingStates;
use super::states::StateContext;
use super::timespan::answer_button_time_collapsible;
use super::timing::SchedTimingToday;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::config::BoolKey;
use crate::deckconfig::DeckConfig;
use crate::deckconfig::LeechAction;
use crate::decks::Deck;
use crate::prelude::*;
use crate::revlog::RevlogReviewKind;
use crate::scheduler::addon_desired_retention::AddonRetentionRules;
use crate::scheduler::addon_desired_retention::RetentionMatch;
use crate::scheduler::fsrs::dynamic_desired_retention::DynamicDesiredRetentionStates;
use crate::scheduler::fsrs::memory_state::fsrs_interval_at_retrievability_for_params;
use crate::scheduler::fsrs::memory_state::fsrs_item_for_memory_state;
use crate::scheduler::fsrs::memory_state::fsrs_memory_state_for_params;
use crate::scheduler::fsrs::memory_state::fsrs_memory_state_for_s90;
use crate::scheduler::fsrs::memory_state::get_decay_from_params;
use crate::scheduler::fsrs::params_fingerprint;
use crate::scheduler::fsrs::preset::FsrsPreset;
use crate::scheduler::fsrs::round_to_two_decimals;
use crate::scheduler::fsrs::uses_fractional_intervals;
use crate::scheduler::rwkv::card_reviewed_today;
use crate::scheduler::states::fuzz::ReviewFuzzConfig;
use crate::scheduler::states::PreviewState;
use crate::search::SearchNode;
use crate::storage::FsrsReviewRetrievabilityCacheRow;
use crate::storage::FsrsReviewRetrievabilitySampleRole;

const AUTO_SUSPEND_TAG: &str = "auto-suspend";
const LEECH_TAG: &str = "leech";

#[derive(Copy, Clone)]
pub enum Rating {
    Again,
    Hard,
    Good,
    Easy,
}

pub struct CardAnswer {
    pub card_id: CardId,
    pub current_state: CardState,
    pub new_state: CardState,
    pub rating: Rating,
    pub answered_at: TimestampMillis,
    pub milliseconds_taken: u32,
    pub custom_data: Option<String>,
    pub desired_retention_override: Option<f32>,
    pub desired_retention_override_is_final: bool,
    pub rwkv_s90: Option<f32>,
    pub rwkv_retrievability: Option<f32>,
    pub rwkv_review_kind: Option<u32>,
    pub from_queue: bool,
}

impl CardAnswer {
    fn cap_answer_secs(&mut self, max_secs: u32) {
        self.milliseconds_taken = self.milliseconds_taken.min(max_secs * 1000);
    }
}

/// Holds the information required to determine a given card's
/// current state, and to apply a state change to it.
struct CardStateUpdater {
    card: Card,
    deck: Deck,
    original_deck: Deck,
    config: DeckConfig,
    fsrs_preset: FsrsPreset,
    timing: SchedTimingToday,
    now: TimestampSecs,
    fuzz_seed: Option<u64>,
    review_fuzz_config: ReviewFuzzConfig,
    /// Set if FSRS is enabled.
    fsrs_next_states: Option<NextStates>,
    /// Set if FSRS is enabled.
    desired_retention: Option<f32>,
    /// Set if FSRS has a pre-answer retrievability for this review.
    fsrs_review_retrievability: Option<f32>,
    /// Set if dynamic DR is enabled; indexed by answer button order.
    dynamic_desired_retentions: Option<[f32; 4]>,
    fsrs_short_term_with_steps: bool,
    fsrs_learning_queues_disabled: bool,
    count_review_lapse: bool,
    fsrs_allow_short_term: bool,
}

impl CardStateUpdater {
    /// Returns information required when transitioning from one card state to
    /// another with `next_states()`. This separate structure decouples the
    /// state handling code from the rest of the Anki codebase.
    pub(crate) fn state_context<'a>(
        &'a self,
        load_balancer_ctx: Option<LoadBalancerContext<'a>>,
    ) -> Result<StateContext<'a>> {
        let fsrs_again_s90 = if self.config.inner.leech_only_if_young {
            self.fsrs_next_states
                .as_ref()
                .map(|states| {
                    fsrs_memory_state_for_params(
                        &self.fsrs_preset.params,
                        fsrs::MemoryState {
                            stability: states.again.memory.stability,
                            difficulty: states.again.memory.difficulty,
                            stability_fast: states.again.memory.stability_fast,
                        },
                    )
                    .map(|state| state.stability)
                })
                .transpose()?
        } else {
            None
        };

        Ok(StateContext {
            fuzz_factor: get_fuzz_factor(self.fuzz_seed),
            steps: self.learn_steps(),
            graduating_interval_good: self.config.inner.graduating_interval_good,
            graduating_interval_easy: self.config.inner.graduating_interval_easy,
            initial_ease_factor: self.config.inner.initial_ease,
            hard_multiplier: self.config.inner.hard_multiplier,
            easy_multiplier: self.config.inner.easy_multiplier,
            interval_multiplier: self.config.inner.interval_multiplier,
            review_fuzz_config: self.review_fuzz_config,
            maximum_review_interval: self.config.inner.maximum_review_interval,
            fsrs_minimum_interval_secs: self.config.inner.fsrs_minimum_interval_secs,
            leech_threshold: self.config.inner.leech_threshold,
            count_review_lapse: self.count_review_lapse,
            leech_only_if_young: self.config.inner.leech_only_if_young,
            fsrs_again_s90,
            load_balancer_ctx: load_balancer_ctx
                .map(|load_balancer_ctx| load_balancer_ctx.set_fuzz_seed(self.fuzz_seed)),
            relearn_steps: self.relearn_steps(),
            lapse_multiplier: self.config.inner.lapse_multiplier,
            minimum_lapse_interval: self.config.inner.minimum_lapse_interval,
            in_filtered_deck: self.deck.is_filtered(),
            preview_delays: if let DeckKind::Filtered(deck) = &self.deck.kind {
                PreviewDelays {
                    again: deck.preview_again_secs,
                    hard: deck.preview_hard_secs,
                    good: deck.preview_good_secs,
                }
            } else {
                Default::default()
            },
            fsrs_next_states: self.fsrs_next_states.clone(),
            fsrs_short_term_with_steps_enabled: self.fsrs_short_term_with_steps,
            fsrs_learning_queues_disabled: self.fsrs_learning_queues_disabled,
            fsrs_allow_short_term: self.fsrs_allow_short_term,
            fsrs_fractional_intervals: uses_fractional_intervals(&self.fsrs_preset.params),
        })
    }

    fn learn_steps(&self) -> LearningSteps<'_> {
        LearningSteps::new(&self.config.inner.learn_steps)
    }

    fn relearn_steps(&self) -> LearningSteps<'_> {
        LearningSteps::new(&self.config.inner.relearn_steps)
    }

    fn secs_until_rollover(&self) -> u32 {
        self.timing.next_day_at.elapsed_secs_since(self.now) as u32
    }

    fn into_card(self) -> Card {
        self.card
    }

    fn memory_state_for_storage(
        &self,
        memory_state: Option<crate::card::FsrsMemoryState>,
    ) -> Result<Option<crate::card::FsrsMemoryState>> {
        memory_state
            .map(|state| {
                fsrs_memory_state_for_params(
                    &self.fsrs_preset.params,
                    fsrs::MemoryState {
                        stability: state.stability_internal,
                        difficulty: state.difficulty,
                        stability_fast: state.stability_fast.unwrap_or(state.stability_internal),
                    },
                )
            })
            .transpose()
    }

    fn apply_study_state(
        &mut self,
        current: CardState,
        next: CardState,
        rating: Rating,
    ) -> Result<RevlogEntryPartial> {
        let revlog = match next {
            CardState::Normal(normal) => {
                // transitioning from filtered state?
                if let CardState::Filtered(filtered) = &current {
                    match filtered {
                        FilteredState::Preview(_) => {
                            invalid_input!("should set finished=true, not return different state")
                        }
                        FilteredState::Rescheduling(_) => {
                            // card needs to be removed from normal filtered deck, then scheduled
                            // normally
                            self.card.remove_from_filtered_deck_before_reschedule();
                        }
                    }
                }
                // apply normal scheduling
                self.apply_normal_study_state(current, normal, rating)?
            }
            CardState::Filtered(filtered) => {
                self.ensure_filtered()?;
                match filtered {
                    FilteredState::Preview(next) => self.apply_preview_state(current, next),
                    FilteredState::Rescheduling(next) => {
                        let revlog =
                            self.apply_normal_study_state(current, next.original_state, rating)?;
                        self.card.original_due = self.card.due;

                        revlog
                    }
                }
            }
        };

        Ok(revlog)
    }

    fn apply_normal_study_state(
        &mut self,
        current: CardState,
        next: NormalState,
        rating: Rating,
    ) -> Result<RevlogEntryPartial> {
        self.card.reps += 1;
        self.card.desired_retention = self
            .dynamic_desired_retentions
            .map(|retentions| retentions[rating.index()])
            .or(self.desired_retention);

        let revlog = match next {
            NormalState::New(next) => self.apply_new_state(current, next),
            NormalState::Learning(next) => self.apply_learning_state(current, next)?,
            NormalState::Review(next) => self.apply_review_state(current, next)?,
            NormalState::Relearning(next) => self.apply_relearning_state(current, next)?,
        };

        if next.leeched() && self.config.inner.leech_action() == LeechAction::Suspend {
            self.card.queue = CardQueue::Suspended;
        }

        Ok(revlog)
    }

    fn ensure_filtered(&self) -> Result<()> {
        require!(
            self.card.original_deck_id.0 != 0,
            "card answering can't transition into filtered state",
        );
        Ok(())
    }
}

#[derive(Debug, Default)]
pub(crate) struct PreviewDelays {
    pub again: u32,
    pub hard: u32,
    pub good: u32,
}

impl Rating {
    fn as_number(self) -> u8 {
        match self {
            Rating::Again => 1,
            Rating::Hard => 2,
            Rating::Good => 3,
            Rating::Easy => 4,
        }
    }

    fn index(self) -> usize {
        self.as_number() as usize - 1
    }
}

fn addon_review_state_mut(state: &mut CardState) -> Option<&mut ReviewState> {
    match state {
        CardState::Normal(NormalState::Review(review)) => Some(review),
        CardState::Normal(NormalState::Relearning(relearning)) => Some(&mut relearning.review),
        _ => None,
    }
}

fn apply_addon_interval_bounds(state: &mut CardState, matched: RetentionMatch) {
    if let Some(review) = addon_review_state_mut(state) {
        if let Some(minimum) = matched.minimum_interval {
            review.scheduled_days = review.scheduled_days.max(minimum);
        }
        if let Some(maximum) = matched.maximum_interval {
            review.scheduled_days = review.scheduled_days.min(maximum);
        }
    }
}

fn apply_addon_grade_target(state: &mut CardState, target: f32, ctx: &CardStateUpdater) -> bool {
    let Some(review) = addon_review_state_mut(state) else {
        return false;
    };
    let Some(memory) = review.memory_state else {
        return false;
    };
    let stability = if memory.stability_internal.is_finite() && memory.stability_internal > 0.0 {
        memory.stability_internal
    } else {
        memory.stability
    };
    if !stability.is_finite() || stability <= 0.0 {
        return false;
    }
    match fsrs_interval_at_retrievability_for_params(&ctx.fsrs_preset.params, stability, target) {
        Ok(interval) if interval.is_finite() && interval > 0.0 => {
            let mut days = (interval + 0.5) as u32;
            days = days.max(1);
            let maximum = ctx.config.inner.maximum_review_interval;
            if maximum > 0 {
                days = days.min(maximum);
            }
            review.scheduled_days = days;
            true
        }
        Ok(_) => false,
        Err(error) => {
            tracing::warn!(?error, "Dynamic DR grade interval failed");
            false
        }
    }
}

/// An external scheduler's unrounded intervals in days (RWKV-Curve), one per
/// answer button, in place of FSRS's; the state machine then applies the
/// short-interval, fuzz and load-balance rules to them as usual.
fn use_external_intervals(state_ctx: &mut StateContext, intervals: [Option<f32>; 4]) {
    if let Some(states) = state_ctx.fsrs_next_states.as_mut() {
        for (item, interval) in [
            &mut states.again,
            &mut states.hard,
            &mut states.good,
            &mut states.easy,
        ]
        .into_iter()
        .zip(intervals)
        {
            if let Some(interval) = interval.filter(|days| days.is_finite() && *days > 0.0) {
                item.interval = interval;
            }
        }
        state_ctx.fsrs_fractional_intervals = true;
    }
}

impl Collection {
    /// Return the next states that will be applied for each answer button.
    pub fn get_scheduling_states(&mut self, cid: CardId) -> Result<SchedulingStates> {
        self.get_scheduling_states_with_desired_retention_override(cid, None)
    }

    pub fn get_scheduling_states_with_desired_retention_override(
        &mut self,
        cid: CardId,
        desired_retention_override: Option<f32>,
    ) -> Result<SchedulingStates> {
        let card = self.storage.get_card(cid)?.or_not_found(cid)?;
        self.get_scheduling_states_inner(card, desired_retention_override)
            .map(|r| r.0)
    }

    pub(crate) fn get_scheduling_states_inner(
        &mut self,
        card: Card,
        desired_retention_override: Option<f32>,
    ) -> Result<(SchedulingStates, Card)> {
        let (addon_rules, addon_match) = self.addon_retention_for_card(&card)?;
        let base_override = desired_retention_override.or(addon_match.map(|value| value.retention));
        let note_id = card.note_id;
        let ctx = self.card_state_updater(card, base_override)?;
        let current = ctx.current_card_state();
        // RWKV-Curve (a Curve preset, or Instant's Curve due dates) without the
        // desktop's Python: the offline runtime gives the intervals its
        // reviewer hands to `scheduling_states_with_intervals`. None (FSRS's
        // intervals) when there is no offline runtime, e.g. on the desktop.
        let curve = if ctx.config.inner.rwkv_review_enabled && ctx.fsrs_next_states.is_some() {
            let targets = ctx
                .dynamic_desired_retentions
                .unwrap_or([ctx.desired_retention.unwrap_or(0.9); 4]);
            self.rwkv_offline_curve(
                ctx.card.id,
                &current,
                targets,
                ctx.config.inner.rwkv_review_enforce_grade_order,
                &ctx.timing,
            )
        } else {
            None
        };

        let load_balancer_ctx = self.review_load_balancer_ctx(&ctx, note_id)?;

        let mut state_ctx = ctx.state_context(load_balancer_ctx)?;
        if let Some(intervals) = curve {
            use_external_intervals(&mut state_ctx, intervals);
        }
        let mut states = current.next_states(&state_ctx);
        states.dynamic_desired_retentions = ctx.dynamic_desired_retentions;
        states.dynamic_desired_retention_enabled =
            ctx.fsrs_preset.dynamic_desired_retention.is_some();
        if let Some(rules) = addon_rules.as_ref() {
            self.apply_addon_retention_states(
                &mut states,
                &ctx,
                rules,
                addon_match,
                base_override,
            )?;
        }
        Ok((states, ctx.into_card()))
    }

    fn addon_retention_for_card(
        &mut self,
        card: &Card,
    ) -> Result<(Option<AddonRetentionRules>, Option<RetentionMatch>)> {
        let Some(rules) = AddonRetentionRules::from_collection(self) else {
            return Ok((None, None));
        };
        let result = rules
            .matches(self, std::slice::from_ref(card))?
            .get(&card.id)
            .copied();
        Ok((Some(rules), result))
    }

    fn apply_addon_retention_states(
        &mut self,
        states: &mut SchedulingStates,
        ctx: &CardStateUpdater,
        rules: &AddonRetentionRules,
        matched: Option<RetentionMatch>,
        base: Option<f32>,
    ) -> Result<()> {
        if rules.has_grade_rules() {
            for (index, state) in [
                &mut states.again,
                &mut states.hard,
                &mut states.good,
                &mut states.easy,
            ]
            .into_iter()
            .enumerate()
            {
                let Some(target) = rules.target_for_grade(base, index) else {
                    continue;
                };
                if ctx.config.inner.fsrs_dynamic_desired_retention_enabled {
                    // Match the desktop add-on's native Dynamic DR branch.
                    let source = match self.card_state_updater(ctx.card.clone(), Some(target)) {
                        Ok(source) => source,
                        Err(error) => {
                            tracing::warn!(?error, "Dynamic DR grade states failed");
                            continue;
                        }
                    };
                    if !rules.has_grade_rule(index) || source.dynamic_desired_retentions.is_none() {
                        let current = source.current_card_state();
                        let load_balancer =
                            self.review_load_balancer_ctx(&source, source.card.note_id)?;
                        let source_states =
                            current.next_states(&source.state_context(load_balancer)?);
                        *state = match index {
                            0 => source_states.again,
                            1 => source_states.hard,
                            2 => source_states.good,
                            _ => source_states.easy,
                        };
                        continue;
                    }
                }
                let _ = apply_addon_grade_target(state, target, ctx);
            }
        }
        if let Some(matched) = matched {
            for state in [
                &mut states.again,
                &mut states.hard,
                &mut states.good,
                &mut states.easy,
            ] {
                apply_addon_interval_bounds(state, matched);
            }
        }
        Ok(())
    }

    fn review_load_balancer_ctx(
        &self,
        ctx: &CardStateUpdater,
        note_id: NoteId,
    ) -> Result<Option<LoadBalancerContext<'_>>> {
        let Some(load_balancer) = self
            .state
            .card_queues
            .as_ref()
            .and_then(|card_queues| card_queues.load_balancer.as_ref())
        else {
            return Ok(None);
        };
        let Some(deck_config_id) = ctx.original_deck.config_id() else {
            return Ok(None);
        };
        let note_id = self
            .get_deck_config(deck_config_id, false)?
            .map(|deck_config| deck_config.inner.bury_reviews)
            .unwrap_or(false)
            .then_some(note_id);
        Ok(Some(load_balancer.review_context(note_id, deck_config_id)))
    }

    /// Build answer states after replacing any supplied unrounded intervals.
    pub fn scheduling_states_with_intervals(
        &mut self,
        cid: CardId,
        intervals: [Option<f32>; 4],
    ) -> Result<SchedulingStates> {
        let card = self.storage.get_card(cid)?.or_not_found(cid)?;
        let (addon_rules, addon_match) = self.addon_retention_for_card(&card)?;
        let base_override = addon_match.map(|value| value.retention);
        let note_id = card.note_id;
        let ctx = self.card_state_updater(card, base_override)?;
        let current = ctx.current_card_state();
        let load_balancer_ctx = self.review_load_balancer_ctx(&ctx, note_id)?;
        let mut state_ctx = ctx.state_context(load_balancer_ctx)?;
        use_external_intervals(&mut state_ctx, intervals);
        let mut states = current.next_states(&state_ctx);
        states.dynamic_desired_retentions = ctx.dynamic_desired_retentions;
        states.dynamic_desired_retention_enabled =
            ctx.fsrs_preset.dynamic_desired_retention.is_some();
        if let Some(rules) = addon_rules.as_ref() {
            self.apply_addon_retention_states(
                &mut states,
                &ctx,
                rules,
                addon_match,
                base_override,
            )?;
        }
        Ok(states)
    }

    /// Describe the next intervals, to display on the answer buttons.
    pub fn describe_next_states(&mut self, choices: &SchedulingStates) -> Result<Vec<String>> {
        let collapse_time = self.learn_ahead_secs();
        let now = TimestampSecs::now();
        let timing = self.timing_for_timestamp(now)?;
        let secs_until_rollover = timing.next_day_at.elapsed_secs_since(now).max(0) as u32;
        let show_fuzz_delta = self.get_config_bool(BoolKey::ShowFuzzDeltaAboveAnswerButtons);

        Ok(vec![
            describe_next_state(
                choices.again,
                secs_until_rollover,
                collapse_time,
                show_fuzz_delta,
                &self.tr,
            ),
            describe_next_state(
                choices.hard,
                secs_until_rollover,
                collapse_time,
                show_fuzz_delta,
                &self.tr,
            ),
            describe_next_state(
                choices.good,
                secs_until_rollover,
                collapse_time,
                show_fuzz_delta,
                &self.tr,
            ),
            describe_next_state(
                choices.easy,
                secs_until_rollover,
                collapse_time,
                show_fuzz_delta,
                &self.tr,
            ),
        ])
    }

    /// Answer card, writing its new state to the database.
    /// Provided [CardAnswer] has its answer time capped to deck preset.
    pub fn answer_card(&mut self, answer: &mut CardAnswer) -> Result<OpOutput<()>> {
        let output = self.transact(Op::AnswerCard, |col| col.answer_card_inner(answer))?;
        self.rwkv_offline_record_answer_kind(answer, output.output.0, output.output.1);
        Ok(output.map(|_| ()))
    }

    pub(crate) fn answer_card_inner(
        &mut self,
        answer: &mut CardAnswer,
    ) -> Result<(RevlogId, bool)> {
        let card = self
            .storage
            .get_card(answer.card_id)?
            .or_not_found(answer.card_id)?;
        let original = card.clone();
        let usn = self.usn()?;

        let (rules, matched) = self.addon_retention_for_card(&card)?;
        let mut addon_grade_target = None;
        if let Some(rules) = rules.filter(|_| !answer.desired_retention_override_is_final) {
            let base = answer
                .desired_retention_override
                .or(matched.map(|value| value.retention));
            answer.desired_retention_override = rules.target_for_grade(base, answer.rating.index());
            if rules.has_grade_rule(answer.rating.index()) {
                addon_grade_target = answer.desired_retention_override;
            }
        }
        if answer.desired_retention_override_is_final {
            require!(
                answer
                    .desired_retention_override
                    .is_some_and(|value| { value.is_finite() && value > 0.0 && value <= 1.0 }),
                "a final desired retention override must be greater than 0 and at most 1",
            );
        }
        let mut updater = self.card_state_updater(card, answer.desired_retention_override)?;
        if let Some(target) = addon_grade_target {
            // Only opt in if the supplied state actually has the interval the
            // grade rule would produce. Unsupported or externally modified
            // states retain native DR.
            let mut state = answer.new_state;
            if updater.dynamic_desired_retentions.is_some()
                && apply_addon_grade_target(&mut state, target, &updater)
            {
                if let Some(matched) = matched {
                    apply_addon_interval_bounds(&mut state, matched);
                }
                answer.desired_retention_override_is_final = state == answer.new_state;
            }
        }
        if answer.desired_retention_override_is_final {
            // The supplied scheduling state already uses the final target. Do
            // not replace it with a second native Dynamic DR mapping on save.
            updater.dynamic_desired_retentions = None;
        }
        answer.cap_answer_secs(updater.config.inner.cap_answer_time_to_secs);
        let current_state = updater.current_card_state();
        let is_preview = matches!(
            current_state,
            CardState::Filtered(FilteredState::Preview(_))
        );
        // If the states aren't equal, it's probably because some time has passed.
        // Try to fix this by setting elapsed_secs equal.
        self.set_elapsed_secs_equal(&current_state, &mut answer.current_state);
        require!(
            current_state == answer.current_state,
            "card was modified: {current_state:#?} {:#?}",
            answer.current_state,
        );

        // Older imports and unlogged resets can leave a new card with active
        // history. Preview answers do not start a new learning sequence.
        if original.ctype == CardType::New && !is_preview {
            self.prepare_rwkv_new_card_reset(&mut updater.card)?;
        }

        let mut revlog_partial =
            updater.apply_study_state(current_state, answer.new_state, answer.rating)?;
        let config_active = updater.config.inner.rwkv_review_enabled
            || updater.config.inner.rwkv_review_instant_order_enabled;
        if answer.rwkv_review_kind.is_none() {
            answer.rwkv_review_kind = self.rwkv_offline_answer_review_kind(
                answer.card_id,
                &current_state,
                &answer.new_state,
                config_active,
                answer.answered_at,
                &updater.timing,
            )?;
        }
        if !is_preview && answer.rwkv_s90.is_none() && updater.config.inner.rwkv_review_enabled {
            // the S90 of the Curve answer shown, as the desktop's
            // `set_answer_rwkv_metadata` sends it
            answer.rwkv_s90 = self.rwkv_offline_curve_s90(answer.card_id, answer.rating.index());
        }
        if let Some(review_kind) = answer.rwkv_review_kind {
            require!(review_kind <= 3, "invalid RWKV review kind");
            revlog_partial.set_review_kind(match review_kind {
                0 => RevlogReviewKind::Learning,
                1 => RevlogReviewKind::Review,
                2 => RevlogReviewKind::Relearning,
                3 => RevlogReviewKind::Filtered,
                _ => unreachable!(),
            });
        }
        let revlog_id = self.add_partial_revlog(revlog_partial, usn, answer)?;
        if let Some(prediction) = updater.fsrs_review_retrievability {
            if let Err(err) = self.storage.set_fsrs_review_retrievability_predictions(
                &[FsrsReviewRetrievabilityCacheRow {
                    revlog_id,
                    prediction,
                    sample_role: FsrsReviewRetrievabilitySampleRole::PostOptimization,
                    fold_index: -1,
                }],
                "fsrs_review",
            ) {
                tracing::warn!(?err, "failed to store FSRS review retrievability cache");
            }
        }
        if let Some(prediction) = answer.rwkv_retrievability {
            require!(
                prediction.is_finite() && (0.0..=1.0).contains(&prediction),
                "invalid RWKV retrievability"
            );
            if let Err(err) = self.storage.set_rwkv_review_retrievability_prediction(
                revlog_id,
                prediction,
                "rwkv_review",
            ) {
                tracing::warn!(?err, "failed to store RWKV review retrievability cache");
            }
        }

        self.update_deck_stats_from_answer(usn, answer, &updater, original.queue)?;
        self.maybe_bury_siblings(&original, &updater.config)?;
        let timing = updater.timing;
        let deckconfig_id = updater.original_deck.config_id();
        if let Some(rwkv_s90) = answer.rwkv_s90 {
            require!(rwkv_s90.is_finite() && rwkv_s90 > 0.0, "invalid RWKV S90");
            if !is_preview {
                match &mut updater.card.memory_state {
                    Some(memory_state) => memory_state.stability = rwkv_s90,
                    None => {
                        updater.card.memory_state = Some(fsrs_memory_state_for_s90(
                            &updater.fsrs_preset.params,
                            rwkv_s90,
                        )?);
                    }
                }
            }
        }
        let mut card = updater.into_card();
        if !is_preview {
            card.last_review_time = Some(answer.answered_at.as_secs());
        }
        if let Some(data) = answer.custom_data.take() {
            card.custom_data = data;
            card.validate_custom_data()?;
        }

        // kuma3: what Undo shows again when the queues can't record the answer
        let entry_before = answer
            .from_queue
            .then(|| QueueEntry::of_queued_card(&original))
            .flatten();
        self.update_card_inner(&mut card, original, usn)?;
        if answer.new_state.leeched() {
            self.add_leech_tags(card.note_id, card.queue == CardQueue::Suspended)?;
        }

        if card.queue == CardQueue::Review {
            if let Some(load_balancer) = self
                .state
                .card_queues
                .as_mut()
                .and_then(|card_queues| card_queues.load_balancer.as_mut())
            {
                if let Some(deckconfig_id) = deckconfig_id {
                    load_balancer.add_card(card.id, card.note_id, deckconfig_id, card.interval)
                }
            }
        }

        // Handle queue updates based on from_queue flag
        if answer.from_queue {
            self.update_queues_after_answering_card(
                &card,
                timing,
                matches!(
                    answer.new_state,
                    CardState::Filtered(FilteredState::Preview(PreviewState {
                        finished: true,
                        ..
                    }))
                ),
                entry_before,
            )?;
        }

        Ok((revlog_id, config_active))
    }

    fn maybe_bury_siblings(&mut self, card: &Card, config: &DeckConfig) -> Result<()> {
        let bury_mode = BuryMode::from_deck_config(config);
        if bury_mode.any_burying() {
            self.bury_siblings(card, card.note_id, bury_mode)?;
        }
        Ok(())
    }

    fn add_partial_revlog(
        &mut self,
        partial: RevlogEntryPartial,
        usn: Usn,
        answer: &CardAnswer,
    ) -> Result<RevlogId> {
        let revlog = partial.into_revlog_entry(
            usn,
            answer.card_id,
            answer.rating.as_number(),
            answer.answered_at,
            answer.milliseconds_taken,
        );
        self.add_revlog_entry_undoable(revlog)
    }

    fn update_deck_stats_from_answer(
        &mut self,
        usn: Usn,
        answer: &CardAnswer,
        updater: &CardStateUpdater,
        from_queue: CardQueue,
    ) -> Result<()> {
        let mut new_delta = 0;
        let mut review_delta = 0;
        match from_queue {
            CardQueue::New => new_delta += 1,
            CardQueue::Review | CardQueue::DayLearn
                if updater.config.inner.same_day_reviews_ignore_review_limit
                    && card_reviewed_today(&updater.card, updater.timing) => {}
            CardQueue::Review | CardQueue::DayLearn => review_delta += 1,
            _ => {}
        }
        self.update_deck_stats(
            updater.timing.days_elapsed,
            usn,
            anki_proto::scheduler::UpdateStatsRequest {
                deck_id: updater.deck.id.0,
                new_delta,
                review_delta,
                millisecond_delta: answer.milliseconds_taken as i32,
            },
        )
    }

    pub fn fsrs_enabled(&self) -> bool {
        self.state
            .card_queues
            .as_ref()
            .map(|queues| queues.fsrs_enabled)
            .unwrap_or_else(|| self.get_config_bool(BoolKey::Fsrs))
    }

    fn fsrs_short_term_with_steps_enabled(&self) -> bool {
        self.state
            .card_queues
            .as_ref()
            .map(|queues| queues.fsrs_short_term_with_steps)
            .unwrap_or_else(|| self.get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled))
    }

    fn card_state_updater(
        &mut self,
        mut card: Card,
        desired_retention_override: Option<f32>,
    ) -> Result<CardStateUpdater> {
        let timing = self.timing_today()?;
        let now = TimestampSecs::now();
        let deck = self
            .storage
            .get_deck(card.deck_id)?
            .or_not_found(card.deck_id)?;
        let home_deck = if card.original_deck_id.0 == 0 {
            &deck
        } else {
            &self
                .storage
                .get_deck(card.original_deck_id)?
                .or_not_found(card.original_deck_id)?
        };
        let home_deck_id = home_deck.id;
        let home_deck_config_id = home_deck.config_id().or_invalid("home deck is filtered")?;
        let config = self
            .storage
            .get_deck_config(home_deck_config_id)?
            .unwrap_or_default();
        let fsrs_preset = self.fsrs_preset_for_card(&card)?;

        let desired_retention = desired_retention_override.unwrap_or(fsrs_preset.desired_retention);
        let fsrs_enabled = self.fsrs_enabled();
        let last_review_time = if card.last_review_time.is_some() {
            card.last_review_time
        } else {
            self.storage.time_of_last_review(card.id)?
        };
        let count_review_lapse =
            !last_review_time.is_some_and(|last_review_time| timing.is_today(last_review_time));
        let mut elapsed_days_for_log = None;
        let mut fsrs_review_retrievability = None;
        let mut dynamic_desired_retention = None::<DynamicDesiredRetentionStates>;
        let fsrs_next_states = if fsrs_enabled {
            let params = &fsrs_preset.params;
            let fsrs = FSRS::new(params)?;
            card.decay = Some(get_decay_from_params(params));
            if card.memory_state.is_none() && card.ctype != CardType::New {
                // Card has been moved or imported into an FSRS deck after params were set,
                // and will need its initial memory state to be calculated based on review
                // history.
                let revlog = self.revlog_for_srs(SearchNode::CardIds(card.id.to_string()))?;
                let item = fsrs_item_for_memory_state(
                    &fsrs,
                    params,
                    revlog,
                    timing.next_day_at,
                    fsrs_preset.historical_retention,
                    fsrs_preset.ignore_revlogs_before_ms()?,
                )?;
                card.set_memory_state(&fsrs, params, item, fsrs_preset.historical_retention)?;
            }
            let days_elapsed = last_review_time
                .map(|last_review_time| {
                    fsrs_elapsed_days(
                        &card,
                        last_review_time,
                        timing.next_day_at,
                        now,
                        uses_fractional_intervals(&fsrs_preset.params),
                    )
                })
                .unwrap_or_default();
            elapsed_days_for_log = Some(days_elapsed);
            let current_memory_state = card.memory_state.map(Into::into);
            fsrs_review_retrievability = current_memory_state
                .map(|state| fsrs.current_retrievability(state, days_elapsed))
                .filter(|retrievability| {
                    retrievability.is_finite() && (0.0..=1.0).contains(retrievability)
                });
            if let Some(dynamic_dr) = fsrs_preset.dynamic_desired_retention.as_ref() {
                if let Some(scheduling_desired_retention) =
                    dynamic_dr.scheduling_target(desired_retention)?
                {
                    if scheduling_desired_retention != desired_retention {
                        tracing::debug!(
                            requested_desired_retention = round_to_two_decimals(desired_retention),
                            scheduling_desired_retention =
                                round_to_two_decimals(scheduling_desired_retention),
                            "Dynamic DR target outside range; using clamped target"
                        );
                    }
                    let dynamic_states = dynamic_dr.next_states(
                        &fsrs,
                        current_memory_state,
                        scheduling_desired_retention,
                        days_elapsed,
                    )?;
                    let states = dynamic_states.states.clone();
                    dynamic_desired_retention = Some(dynamic_states);
                    Some(states)
                } else {
                    tracing::debug!(
                        desired_retention = round_to_two_decimals(desired_retention),
                        "Dynamic DR target outside range; using fixed desired retention"
                    );
                    Some(fsrs.next_states_with_elapsed_days(
                        current_memory_state,
                        desired_retention,
                        days_elapsed,
                    )?)
                }
            } else {
                Some(fsrs.next_states_with_elapsed_days(
                    current_memory_state,
                    desired_retention,
                    days_elapsed,
                )?)
            }
        } else {
            None
        };
        if let Some(states) = &fsrs_next_states {
            tracing::debug!(
                card_id = card.id.0,
                preset_id = ?fsrs_preset.id,
                preset_name = fsrs_preset.name.as_str(),
                fsrs_version = ?fsrs_preset.fsrs_version,
                params_len = fsrs_preset.params.len(),
                params_fingerprint = format_args!("{:016x}", params_fingerprint(&fsrs_preset.params)),
                desired_retention = round_to_two_decimals(desired_retention),
                desired_retention_overridden = desired_retention_override.is_some(),
                dynamic_desired_retention = dynamic_desired_retention.is_some(),
                dynamic_desired_retention_weight = dynamic_desired_retention
                    .as_ref()
                    .map(|value| round_to_two_decimals(value.cost_weight)),
                elapsed_days = elapsed_days_for_log.map(round_to_two_decimals),
                current_s90 = card.memory_state.map(|state| round_to_two_decimals(state.stability)),
                current_internal_stability = card
                    .memory_state
                    .map(|state| round_to_two_decimals(state.stability_internal)),
                current_difficulty = card
                    .memory_state
                    .map(|state| round_to_two_decimals(state.difficulty)),
                again_internal_stability = round_to_two_decimals(states.again.memory.stability),
                hard_internal_stability = round_to_two_decimals(states.hard.memory.stability),
                good_internal_stability = round_to_two_decimals(states.good.memory.stability),
                easy_internal_stability = round_to_two_decimals(states.easy.memory.stability),
                again_interval = round_to_two_decimals(states.again.interval),
                hard_interval = round_to_two_decimals(states.hard.interval),
                good_interval = round_to_two_decimals(states.good.interval),
                easy_interval = round_to_two_decimals(states.easy.interval),
                "computed FSRS scheduling states"
            );
        }
        let desired_retention = fsrs_enabled.then_some(desired_retention);
        let dynamic_desired_retentions =
            dynamic_desired_retention.map(|value| value.desired_retentions);
        let fsrs_short_term_with_steps = self.fsrs_short_term_with_steps_enabled();
        let fsrs_learning_queues_disabled =
            fsrs_enabled && self.get_config_bool(BoolKey::FsrsLearningQueuesDisabled);
        let fsrs_allow_short_term = if fsrs_enabled {
            let params = &fsrs_preset.params;
            if params.len() >= 19 {
                params[17] > 0.0 && params[18] > 0.0
            } else if params.is_empty() {
                // fallback to true when using default params
                true
            } else {
                false
            }
        } else {
            false
        };
        let original_deck = self
            .storage
            .get_deck(home_deck_id)?
            .or_not_found(home_deck_id)?;
        Ok(CardStateUpdater {
            fuzz_seed: get_fuzz_seed(&card, false),
            review_fuzz_config: self.review_fuzz_config(),
            card,
            deck,
            original_deck,
            config,
            fsrs_preset,
            timing,
            now,
            fsrs_next_states,
            desired_retention,
            fsrs_review_retrievability,
            dynamic_desired_retentions,
            fsrs_short_term_with_steps,
            fsrs_learning_queues_disabled,
            count_review_lapse,
            fsrs_allow_short_term,
        })
    }

    pub(crate) fn home_deck_config(
        &self,
        config_id: Option<DeckConfigId>,
        home_deck_id: DeckId,
    ) -> Result<DeckConfig> {
        let config_id = if let Some(config_id) = config_id {
            config_id
        } else {
            let home_deck = self
                .storage
                .get_deck(home_deck_id)?
                .or_not_found(home_deck_id)?;
            home_deck.config_id().or_invalid("home deck is filtered")?
        };

        Ok(self.storage.get_deck_config(config_id)?.unwrap_or_default())
    }

    fn add_leech_tags(&mut self, nid: NoteId, auto_suspended: bool) -> Result<()> {
        let tags = if auto_suspended {
            format!("{LEECH_TAG} {AUTO_SUSPEND_TAG}")
        } else {
            LEECH_TAG.to_string()
        };
        self.add_tags_to_notes_inner(&[nid], &tags)?;
        Ok(())
    }

    /// Update the elapsed time of the answer state to match the current state.
    ///
    /// Since the state calculation takes the current time into account, the
    /// elapsed_secs will probably be different for the two states. This is fine
    /// for elapsed_secs, but we set the two values equal to easily compare
    /// the other values of the two states.
    fn set_elapsed_secs_equal(&self, current_state: &CardState, answer_state: &mut CardState) {
        if let (Some(current_state), Some(answer_state)) = (
            match current_state {
                CardState::Normal(normal_state) => Some(normal_state),
                CardState::Filtered(FilteredState::Rescheduling(resched_filter_state)) => {
                    Some(&resched_filter_state.original_state)
                }
                _ => None,
            },
            match answer_state {
                CardState::Normal(normal_state) => Some(normal_state),
                CardState::Filtered(FilteredState::Rescheduling(resched_filter_state)) => {
                    Some(&mut resched_filter_state.original_state)
                }
                _ => None,
            },
        ) {
            match (current_state, answer_state) {
                (NormalState::Learning(answer), NormalState::Learning(current)) => {
                    current.elapsed_secs = answer.elapsed_secs;
                }
                (NormalState::Relearning(answer), NormalState::Relearning(current)) => {
                    current.learning.elapsed_secs = answer.learning.elapsed_secs;
                }
                _ => {} // Other states don't use elapsed_secs.
            }
        }
    }
}

pub(crate) fn fsrs_elapsed_days(
    card: &Card,
    last_review_time: TimestampSecs,
    next_day_at: TimestampSecs,
    now: TimestampSecs,
    fractional: bool,
) -> f32 {
    if fractional
        || matches!(card.queue, CardQueue::Learn)
            && matches!(card.ctype, CardType::Learn | CardType::Relearn)
    {
        (now.elapsed_secs_since(last_review_time).max(0) as f32) / 86_400.0
    } else {
        next_day_at.elapsed_days_since(last_review_time) as f32
    }
}

fn describe_next_state(
    state: CardState,
    secs_until_rollover: u32,
    collapse_time: u32,
    show_fuzz_delta: bool,
    tr: &I18n,
) -> String {
    let seconds = state
        .interval_kind()
        .maybe_as_days(secs_until_rollover)
        .as_seconds();
    let mut label = answer_button_time_collapsible(seconds, collapse_time, tr);
    if show_fuzz_delta {
        if let Some(fuzz_delta_days) = displayed_fuzz_delta_days(state) {
            if fuzz_delta_days != 0 {
                label.push_str(&format!(" ({fuzz_delta_days:+}d)"));
            }
        }
    }
    label
}

fn displayed_fuzz_delta_days(state: CardState) -> Option<i32> {
    match state {
        CardState::Normal(NormalState::Review(state)) => Some(state.fuzz_delta_days),
        CardState::Filtered(FilteredState::Rescheduling(state)) => match state.original_state {
            NormalState::Review(state) => Some(state.fuzz_delta_days),
            _ => None,
        },
        _ => None,
    }
}

#[cfg(test)]
pub mod test_helpers {
    use super::*;

    pub struct PostAnswerState {
        pub card_id: CardId,
        pub new_state: CardState,
    }

    impl Collection {
        pub(crate) fn answer_again(&mut self) -> PostAnswerState {
            self.answer(|states| states.again, Rating::Again).unwrap()
        }

        #[allow(dead_code)]
        pub(crate) fn answer_hard(&mut self) -> PostAnswerState {
            self.answer(|states| states.hard, Rating::Hard).unwrap()
        }

        pub(crate) fn answer_good(&mut self) -> PostAnswerState {
            self.answer(|states| states.good, Rating::Good).unwrap()
        }

        pub(crate) fn answer_easy(&mut self) -> PostAnswerState {
            self.answer(|states| states.easy, Rating::Easy).unwrap()
        }

        fn answer<F>(&mut self, get_state: F, rating: Rating) -> Result<PostAnswerState>
        where
            F: FnOnce(&SchedulingStates) -> CardState,
        {
            let queued = self.get_next_card()?.unwrap();
            let states = queued.states.expect("queued card has scheduling states");
            let new_state = get_state(&states);
            self.answer_card(&mut CardAnswer {
                card_id: queued.card.id,
                current_state: states.current,
                new_state,
                rating,
                answered_at: TimestampMillis::now(),
                milliseconds_taken: 0,
                custom_data: None,
                desired_retention_override: None,
                desired_retention_override_is_final: false,
                rwkv_s90: None,
                rwkv_retrievability: None,
                rwkv_review_kind: None,
                from_queue: true,
            })?;
            Ok(PostAnswerState {
                card_id: queued.card.id,
                new_state,
            })
        }
    }
}

impl Card {
    /// If for_reschedule is true, we use card.reps - 1 to match the previous
    /// review.
    pub(crate) fn get_fuzz_factor(&self, for_reschedule: bool) -> Option<f32> {
        get_fuzz_factor(get_fuzz_seed(self, for_reschedule))
    }
}

/// Return a consistent seed for a given card at a given number of reps.
/// If for_reschedule is true, we use card.reps - 1 to match the previous
/// review.
pub(crate) fn get_fuzz_seed(card: &Card, for_reschedule: bool) -> Option<u64> {
    let reps = if for_reschedule {
        card.reps.saturating_sub(1)
    } else {
        card.reps
    };
    get_fuzz_seed_for_id_and_reps(card.id, reps)
}

/// If in test environment, disable fuzzing.
fn get_fuzz_seed_for_id_and_reps(card_id: CardId, card_reps: u32) -> Option<u64> {
    if *crate::PYTHON_UNIT_TESTS || cfg!(test) {
        None
    } else {
        Some((card_id.0 as u64).wrapping_add(card_reps as u64))
    }
}

/// Return a fuzz factor from the range `0.0..1.0`, using the provided seed.
/// None if seed is None.
fn get_fuzz_factor(seed: Option<u64>) -> Option<f32> {
    seed.map(|s| StdRng::seed_from_u64(s).random_range(0.0..1.0))
}

#[cfg(test)]
pub(crate) mod test {
    use super::*;
    use crate::card::CardQueue;
    use crate::card::CardType;
    use crate::card::FsrsMemoryState;
    use crate::config::BoolKey;
    use crate::deckconfig::FsrsVersion;
    use crate::deckconfig::LeechAction;
    use crate::deckconfig::ReviewCardOrder;
    use crate::deckconfig::ReviewMix;
    use crate::ops::Op;
    use crate::scheduler::fsrs::preset::AddonFsrsPreset;
    use crate::scheduler::fsrs::preset::AddonFsrsVersion;
    use crate::scheduler::fsrs::preset::FsrsPresetOverlay;
    use crate::scheduler::fsrs::preset::FsrsPresetRule;
    use crate::scheduler::fsrs::preset::FSRS_PRESET_OVERLAY_CONFIG_KEY;
    use crate::scheduler::states::LearnState;
    use crate::scheduler::states::NewState;
    use crate::scheduler::states::NormalState;
    use crate::scheduler::states::ReviewState;
    use crate::search::SortMode;
    use crate::tests::NoteAdder;

    fn current_state(col: &mut Collection, card_id: CardId) -> CardState {
        col.get_scheduling_states(card_id).unwrap().current
    }

    fn low_retention_fsrs7_params() -> Vec<f32> {
        vec![
            0.0113, 0.7801, 2.2056, 17.8287, 5.7900, 0.4527, 3.1686, 2.1464, 0.2876, 1.2004,
            0.4385, 0.0057, 0.8110, 0.2112, 0.5439, 1.7069, 0.9438, 0.3588, 3.6203, 0.3262, 0.0060,
            0.2524, 2.6739, 0.5529, 1.3967, 2.5000, 0.9966, 0.0630, 0.2528, 0.6248, 0.9734, 0.1204,
            0.6260, 0.1575,
        ]
    }

    const SHORT_TERM_RELEARNING_RETENTION: f32 = 0.85;

    #[test]
    fn fsrs7_uses_fractional_elapsed_time_for_review_cards() {
        const HOUR: i64 = 3_600;
        const DAY: i64 = 86_400;
        let monday = 1_000 * DAY;
        let last_review = TimestampSecs(monday + 23 * HOUR);
        let now = TimestampSecs(monday + 2 * DAY + 5 * HOUR);
        let mut card = Card::new(NoteId(1), 0, DeckId(1), 0);
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;

        assert_eq!(fsrs_elapsed_days(&card, last_review, now, now, true), 1.25);
        assert_eq!(fsrs_elapsed_days(&card, now, now, last_review, true), 0.0);
    }

    fn add_due_review_card(
        col: &mut Collection,
        interval: u32,
        lapses: u32,
        memory_state: Option<FsrsMemoryState>,
    ) -> Result<CardId> {
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let mut card = col.get_first_card();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = interval;
        card.due = col.timing_today()?.days_elapsed as i32;
        card.lapses = lapses;
        card.memory_state = memory_state;
        card.last_review_time = Some(TimestampSecs::now().adding_secs(-(interval as i64) * 86_400));
        col.storage.update_card(&card)?;
        col.clear_study_queues();

        Ok(card.id)
    }

    #[test]
    fn rwkv_s90_answer_preserves_undo_and_internal_fsrs_stability() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        let cid = add_due_review_card(
            &mut col,
            10,
            0,
            Some(FsrsMemoryState {
                stability: 10.0,
                stability_internal: 10.0,
                stability_fast: None,
                difficulty: 5.0,
            }),
        )?;

        let states = col.get_scheduling_states(cid)?;
        let mut new_state = states.good;
        let CardState::Normal(NormalState::Review(review)) = &mut new_state else {
            panic!("expected Good to be a review state");
        };
        review.memory_state = Some(FsrsMemoryState {
            stability: 1.0,
            stability_internal: 7.0,
            stability_fast: None,
            difficulty: 5.0,
        });

        col.answer_card(&mut CardAnswer {
            card_id: cid,
            current_state: states.current,
            new_state,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: None,
            desired_retention_override_is_final: false,
            rwkv_s90: Some(20.0),
            rwkv_retrievability: Some(0.62),
            rwkv_review_kind: None,
            from_queue: true,
        })?;

        let card = col.storage.get_card(cid)?.unwrap();
        let memory_state = card.memory_state.unwrap();
        assert_eq!(memory_state.stability, 20.0);
        assert_eq!(memory_state.stability_internal, 7.0);
        let cached_retrievability: f32 = col.storage.db.query_row(
            "select prediction from search_stats_rwkv_review_retrievability",
            [],
            |row| row.get(0),
        )?;
        assert!((cached_retrievability - 0.62).abs() < 1e-6);
        assert_eq!(col.can_undo(), Some(&Op::AnswerCard));

        Ok(())
    }

    #[test]
    fn rwkv_s90_answer_without_memory_state_stores_matching_fsrs7_state() -> Result<()> {
        let mut col = Collection::new();
        let cid = add_due_review_card(&mut col, 10, 0, None)?;
        let states = col.get_scheduling_states(cid)?;

        col.answer_card(&mut CardAnswer {
            card_id: cid,
            current_state: states.current,
            new_state: states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: None,
            desired_retention_override_is_final: false,
            rwkv_s90: Some(20.0),
            rwkv_retrievability: None,
            rwkv_review_kind: None,
            from_queue: true,
        })?;

        let memory_state = col.storage.get_card(cid)?.unwrap().memory_state.unwrap();
        let fsrs = FSRS::new(&fsrs::DEFAULT_PARAMETERS)?;
        let s90 = fsrs.interval_at_retrievability(memory_state.into(), 0.9);
        assert_eq!(memory_state.stability, 20.0);
        assert!((s90 - 20.0).abs() < 0.01, "{s90}");
        Ok(())
    }

    #[test]
    fn rwkv_review_kind_is_written_in_answer_transaction() -> Result<()> {
        let mut col = Collection::new();
        let cid = add_due_review_card(&mut col, 10, 0, None)?;
        let states = col.get_scheduling_states(cid)?;

        col.answer_card(&mut CardAnswer {
            card_id: cid,
            current_state: states.current,
            new_state: states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: None,
            desired_retention_override_is_final: false,
            rwkv_s90: None,
            rwkv_retrievability: None,
            rwkv_review_kind: Some(RevlogReviewKind::Relearning as u32),
            from_queue: true,
        })?;

        let revlogs = col.storage.get_revlog_entries_for_card(cid)?;
        assert_eq!(revlogs.len(), 1);
        assert_eq!(revlogs[0].review_kind, RevlogReviewKind::Relearning);
        assert_eq!(col.can_undo(), Some(&Op::AnswerCard));

        Ok(())
    }

    #[test]
    fn same_day_review_does_not_consume_another_daily_limit_credit() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::FsrsLearningQueuesDisabled, true, false)?;
        col.update_default_deck_config(|config| {
            config.same_day_reviews_ignore_review_limit = true;
        });
        let card_id = add_due_review_card(&mut col, 10, 0, None)?;
        let states = col.get_scheduling_states(card_id)?;

        col.answer_card(&mut CardAnswer {
            card_id,
            current_state: states.current,
            new_state: states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: None,
            desired_retention_override_is_final: false,
            rwkv_s90: None,
            rwkv_retrievability: None,
            rwkv_review_kind: None,
            from_queue: true,
        })?;

        let timing = col.timing_today()?;
        let mut card = col.storage.get_card(card_id)?.unwrap();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.due = timing.days_elapsed as i32;
        col.storage.update_card(&card)?;
        let states = col.get_scheduling_states(card_id)?;
        col.answer_card(&mut CardAnswer {
            card_id,
            current_state: states.current,
            new_state: states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: None,
            desired_retention_override_is_final: false,
            rwkv_s90: None,
            rwkv_retrievability: None,
            rwkv_review_kind: None,
            from_queue: true,
        })?;

        let deck = col.get_deck(DeckId(1))?.unwrap();
        assert_eq!(deck.common.review_studied, 1);
        Ok(())
    }

    #[test]
    fn review_does_not_count_same_day_lapses() -> Result<()> {
        for (fsrs_enabled, skip_queues, reviewed_today, stored_review_time, expected_lapses) in [
            (true, true, true, true, 7),
            (true, true, true, false, 7),
            (true, true, false, true, 8),
            (true, false, true, true, 7),
            (true, false, false, true, 8),
            (false, true, true, true, 7),
            (false, false, true, true, 7),
            (false, false, true, false, 7),
            (false, false, false, true, 8),
            (false, false, false, false, 8),
        ] {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::Fsrs, fsrs_enabled, false)?;
            col.set_config_bool(BoolKey::FsrsLearningQueuesDisabled, skip_queues, false)?;
            col.set_config_bool(BoolKey::FsrsShortTermWithStepsEnabled, true, false)?;
            col.update_default_deck_config(|config| {
                config.leech_threshold = 8;
                config.leech_action = LeechAction::Suspend as i32;
            });
            let card_id = add_due_review_card(
                &mut col,
                1,
                7,
                Some(FsrsMemoryState {
                    stability: 1.0,
                    stability_internal: 1.0,
                    stability_fast: None,
                    difficulty: 5.0,
                }),
            )?;
            let timing = col.timing_today()?;
            let mut card = col.storage.get_card(card_id)?.unwrap();
            let last_review_time = if reviewed_today {
                timing.now
            } else {
                timing.next_day_at.adding_secs(-86_401)
            };
            card.last_review_time = stored_review_time.then_some(last_review_time);
            col.storage.update_card(&card)?;
            if !stored_review_time {
                col.storage.add_revlog_entry(
                    &crate::revlog::RevlogEntry {
                        id: last_review_time.as_millis().into(),
                        cid: card_id,
                        button_chosen: 1,
                        interval: 1,
                        ease_factor: 2500,
                        review_kind: RevlogReviewKind::Review,
                        ..Default::default()
                    },
                    true,
                )?;
            }
            let states = col.get_scheduling_states(card_id)?;
            assert_eq!(states.again.review_state().unwrap().lapses, expected_lapses);
            assert_eq!(states.again.leeched(), expected_lapses == 8);

            col.answer_card(&mut CardAnswer {
                card_id,
                current_state: states.current,
                new_state: states.again,
                rating: Rating::Again,
                answered_at: TimestampMillis::now(),
                milliseconds_taken: 0,
                custom_data: None,
                desired_retention_override: None,
                desired_retention_override_is_final: false,
                rwkv_s90: None,
                rwkv_retrievability: None,
                rwkv_review_kind: None,
                from_queue: false,
            })?;

            let card = col.storage.get_card(card_id)?.unwrap();
            assert_eq!(card.lapses, expected_lapses);
            assert_eq!(card.queue == CardQueue::Suspended, expected_lapses == 8);
            let note = col.storage.get_note(card.note_id)?.unwrap();
            assert_eq!(
                note.tags.iter().any(|tag| tag == LEECH_TAG),
                expected_lapses == 8
            );
            assert_eq!(
                col.storage.get_revlog_entries_for_card(card_id)?.len(),
                if stored_review_time { 1 } else { 2 }
            );
            col.undo()?;
            assert_eq!(col.storage.get_card(card_id)?.unwrap().lapses, 7);
            col.redo()?;
            assert_eq!(
                col.storage.get_card(card_id)?.unwrap().lapses,
                expected_lapses
            );
        }
        Ok(())
    }

    #[test]
    fn grade_now_does_not_count_same_day_lapse_after_successful_review() -> Result<()> {
        for fsrs_enabled in [false, true] {
            for filtered in [false, true] {
                let mut col = Collection::new();
                col.set_config_bool(BoolKey::Fsrs, fsrs_enabled, false)?;
                col.set_config_bool(BoolKey::FsrsLearningQueuesDisabled, false, false)?;
                col.update_default_deck_config(|config| {
                    config.leech_threshold = 8;
                    config.leech_action = LeechAction::Suspend as i32;
                });
                let card_id = add_due_review_card(&mut col, 10, 7, None)?;
                col.grade_now(anki_proto::scheduler::GradeNowRequest {
                    card_ids: vec![card_id.into()],
                    rating: anki_proto::scheduler::card_answer::Rating::Good as i32,
                    card_options: vec![],
                })?;

                if filtered {
                    let mut deck = Deck::new_filtered();
                    deck.filtered_mut()?.search_terms[0].search = format!("cid:{card_id}");
                    col.add_or_update_deck(&mut deck)?;
                    assert_eq!(col.rebuild_filtered_deck(deck.id)?.output, 1);
                    assert!(matches!(
                        col.get_scheduling_states(card_id)?.current,
                        CardState::Filtered(FilteredState::Rescheduling(_))
                    ));
                }

                col.grade_now(anki_proto::scheduler::GradeNowRequest {
                    card_ids: vec![card_id.into()],
                    rating: anki_proto::scheduler::card_answer::Rating::Again as i32,
                    card_options: vec![],
                })?;

                let card = col.storage.get_card(card_id)?.unwrap();
                assert_eq!(card.lapses, 7);
                assert_ne!(card.queue, CardQueue::Suspended);
                let note = col.storage.get_note(card.note_id)?.unwrap();
                assert!(!note.tags.iter().any(|tag| tag == LEECH_TAG));
                assert_eq!(col.storage.get_revlog_entries_for_card(card_id)?.len(), 2);
            }
        }
        Ok(())
    }

    #[test]
    fn rwkv_instant_failure_obeys_same_day_and_lapse_settings() -> Result<()> {
        for allow_same_day in [false, true] {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::Fsrs, true, false)?;
            col.set_config_bool(BoolKey::FsrsLearningQueuesDisabled, true, false)?;
            col.set_config_bool(
                BoolKey::FsrsShortTermWithStepsEnabled,
                allow_same_day,
                false,
            )?;
            col.update_default_deck_config(|config| {
                config.rwkv_review_instant_order_enabled = true;
                config.rwkv_review_allow_same_day_review = true;
                config.rwkv_review_min_intervening_reviews = 0;
                config.rwkv_review_min_elapsed_secs = 0;
                config.leech_threshold = 2;
                config.leech_action = LeechAction::Suspend as i32;
            });
            let card_id = add_due_review_card(
                &mut col,
                1,
                0,
                Some(FsrsMemoryState {
                    stability: 1.0,
                    stability_internal: 1.0,
                    stability_fast: None,
                    difficulty: 5.0,
                }),
            )?;
            col.set_rwkv_review_queue_scores(
                DeckId(1),
                std::collections::HashMap::from([(card_id, 0.1)]),
            )?;
            assert_eq!(col.answer_again().card_id, card_id);
            let card = col.storage.get_card(card_id)?.unwrap();
            assert_eq!(card.queue, CardQueue::Review);
            assert_eq!(card.lapses, 1);
            assert!(card.due > col.timing_today()?.days_elapsed as i32);

            col.set_rwkv_review_queue_scores(
                DeckId(1),
                std::collections::HashMap::from([(card_id, 0.1)]),
            )?;
            if allow_same_day {
                assert_eq!(col.answer_again().card_id, card_id);
                let card = col.storage.get_card(card_id)?.unwrap();
                assert_eq!(card.queue, CardQueue::Review);
                assert_eq!(card.lapses, 1);
                assert_eq!(col.storage.get_revlog_entries_for_card(card_id)?.len(), 2);
            } else {
                assert!(col.get_next_card()?.is_none());
            }
        }
        Ok(())
    }

    #[test]
    fn describe_next_states_includes_fuzz_delta() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::ShowFuzzDeltaAboveAnswerButtons, true, false)?;
        let states = SchedulingStates {
            current: NewState::default().into(),
            again: ReviewState {
                scheduled_days: 4,
                fuzz_delta_days: 1,
                ..Default::default()
            }
            .into(),
            hard: ReviewState {
                scheduled_days: 3,
                ..Default::default()
            }
            .into(),
            good: ReviewState {
                scheduled_days: 5,
                fuzz_delta_days: -1,
                ..Default::default()
            }
            .into(),
            easy: LearnState {
                scheduled_secs: 600,
                elapsed_secs: 0,
                remaining_steps: 1,
                memory_state: None,
            }
            .into(),
            dynamic_desired_retentions: None,
            dynamic_desired_retention_enabled: false,
        };

        let labels = col.describe_next_states(&states)?;

        assert_eq!(labels[0], "4d (+1d)");
        assert_eq!(labels[1], "3d");
        assert_eq!(labels[2], "5d (-1d)");
        assert_eq!(labels[3], "<10m");

        Ok(())
    }

    // Test that deck-specific desired retention is used when available
    #[test]
    fn deck_specific_desired_retention() -> Result<()> {
        let mut col = Collection::new();

        // Enable FSRS
        col.set_config_bool(BoolKey::Fsrs, true, false)?;

        // Create a deck with specific desired retention
        let deck_id = DeckId(1);
        let deck = col.get_deck(deck_id)?.unwrap();
        let mut deck_clone = (*deck).clone();
        deck_clone.normal_mut().unwrap().desired_retention = Some(0.85);
        col.update_deck(&mut deck_clone)?;

        // Create a card in this deck
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, deck_id)?;

        // Get the card using search_cards
        let cards = col.search_cards(note.id, SortMode::NoOrder)?;
        let card = col.storage.get_card(cards[0])?.unwrap();

        // Test that the card state updater uses deck-specific desired retention
        let updater = col.card_state_updater(card, None)?;

        // Print debug information
        println!("FSRS enabled: {}", col.get_config_bool(BoolKey::Fsrs));
        println!("Desired retention: {:?}", updater.desired_retention);

        // Verify that the desired retention is from the deck, not the config
        assert_eq!(updater.desired_retention, Some(0.85));

        Ok(())
    }

    #[test]
    fn leech_only_if_young_gates_sm2_tagging_and_suspension() -> Result<()> {
        let answer_with_lapse_multiplier = |lapse_multiplier: f32| -> Result<(Card, Vec<String>)> {
            let mut col = Collection::new();
            col.update_default_deck_config(|config| {
                config.leech_action = LeechAction::Suspend as i32;
                config.leech_threshold = 2;
                config.leech_only_if_young = true;
                config.lapse_multiplier = lapse_multiplier;
            });
            add_due_review_card(&mut col, 100, 1, None)?;

            let post_answer = col.answer_again();
            let card = col.storage.get_card(post_answer.card_id)?.unwrap();
            let tags = col.storage.get_note(card.note_id)?.unwrap().tags;
            Ok((card, tags))
        };

        let (mature_card, mature_tags) = answer_with_lapse_multiplier(0.5)?;
        assert_eq!(mature_card.interval, 50);
        assert_ne!(mature_card.queue, CardQueue::Suspended);
        assert!(mature_tags.is_empty());

        let (young_card, young_tags) = answer_with_lapse_multiplier(0.1)?;
        assert_eq!(young_card.interval, 10);
        assert_eq!(young_card.queue, CardQueue::Suspended);
        assert!(young_tags.iter().any(|tag| tag == LEECH_TAG));
        assert!(young_tags.iter().any(|tag| tag == AUTO_SUSPEND_TAG));

        Ok(())
    }

    #[test]
    fn desired_retention_override_recomputes_states_and_is_saved() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.update_default_deck_config(|config| {
            config.fsrs_version = FsrsVersion::Seven as i32;
            config.fsrs_params_7 = low_retention_fsrs7_params();
            config.desired_retention = 0.65;
        });

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let mut card = col.get_first_card();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 10;
        card.due = col.timing_today()?.days_elapsed as i32;
        card.memory_state = Some(FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: None,
            difficulty: 5.0,
        });
        card.last_review_time = Some(TimestampSecs::now().adding_secs(-5 * 86_400));
        col.storage.update_card(&card)?;

        let default_states = col.get_scheduling_states(card.id)?;
        let override_states =
            col.get_scheduling_states_with_desired_retention_override(card.id, Some(0.95))?;

        let CardState::Normal(NormalState::Review(default_good)) = default_states.good else {
            panic!("expected default Good to be review");
        };
        let CardState::Normal(NormalState::Review(override_good)) = override_states.good else {
            panic!("expected override Good to be review");
        };
        assert!(
            override_good.scheduled_days < default_good.scheduled_days,
            "higher desired retention should produce a shorter Good interval"
        );

        col.answer_card(&mut CardAnswer {
            card_id: card.id,
            current_state: override_states.current,
            new_state: override_states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: Some(0.95),
            desired_retention_override_is_final: false,
            rwkv_s90: None,
            rwkv_retrievability: None,
            rwkv_review_kind: None,
            from_queue: true,
        })?;

        let card = col.storage.get_card(card.id)?.unwrap();
        assert_eq!(card.desired_retention, Some(0.95));

        Ok(())
    }

    #[test]
    fn final_desired_retention_override_preserves_the_answer_target() -> Result<()> {
        use crate::services::SchedulerService;

        fn answer(is_final: bool) -> Result<(f32, f32)> {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::Fsrs, true, false)?;
            col.update_default_deck_config(|config| {
                config.fsrs_dynamic_desired_retention_enabled = true;
                config.fsrs_dynamic_desired_retention_params = vec![0.0; 15];
                config.fsrs_dynamic_desired_retention_weights = vec![0.0, 15.0];
                config.fsrs_dynamic_desired_retention_avg_drs = vec![0.8, 0.9];
                config.fsrs_dynamic_desired_retention_min = 0.75;
                config.fsrs_dynamic_desired_retention_max = 0.95;
            });
            NoteAdder::basic(&mut col).add(&mut col);
            let card_id = col.answer_easy().card_id;
            col.set_due_date(&[card_id], "0", None)?;
            let states =
                col.get_scheduling_states_with_desired_retention_override(card_id, Some(0.85))?;
            let mapped = states.dynamic_desired_retentions.unwrap()[2];
            let states: anki_proto::scheduler::SchedulingStates = states.into();
            let _ = SchedulerService::answer_card(
                &mut col,
                anki_proto::scheduler::CardAnswer {
                    card_id: card_id.0,
                    current_state: states.current,
                    new_state: states.good,
                    rating: anki_proto::scheduler::card_answer::Rating::Good as i32,
                    answered_at_millis: TimestampMillis::now().0,
                    desired_retention_override: Some(0.85),
                    desired_retention_override_is_final: is_final,
                    ..Default::default()
                },
            )?;
            Ok((col.get_first_card().desired_retention.unwrap(), mapped))
        }

        let (saved_base, mapped) = answer(false)?;
        let (saved_final, _) = answer(true)?;
        assert_eq!(saved_base, (mapped * 100.0).round() / 100.0);
        assert_ne!(saved_base, 0.85);
        assert_eq!(saved_final, 0.85);
        Ok(())
    }

    #[test]
    fn final_desired_retention_override_requires_a_valid_target() -> Result<()> {
        use crate::services::SchedulerService;

        let mut col = Collection::new();
        NoteAdder::basic(&mut col).add(&mut col);
        let card = col.get_first_card();
        let states: anki_proto::scheduler::SchedulingStates =
            col.get_scheduling_states(card.id)?.into();
        for target in [None, Some(0.0), Some(1.1), Some(f32::NAN)] {
            let result = SchedulerService::answer_card(
                &mut col,
                anki_proto::scheduler::CardAnswer {
                    card_id: card.id.0,
                    current_state: states.current.clone(),
                    new_state: states.good.clone(),
                    rating: anki_proto::scheduler::card_answer::Rating::Good as i32,
                    answered_at_millis: TimestampMillis::now().0,
                    desired_retention_override: target,
                    desired_retention_override_is_final: true,
                    ..Default::default()
                },
            );
            assert!(result.is_err());
            assert_eq!(col.get_first_card(), card);
        }
        Ok(())
    }

    #[test]
    fn desired_retention_override_outside_dynamic_dr_range_uses_fixed_dr() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.update_default_deck_config(|config| {
            config.fsrs_version = FsrsVersion::Seven as i32;
            config.fsrs_params_7 = low_retention_fsrs7_params();
            config.desired_retention = 0.85;
            config.fsrs_dynamic_desired_retention_enabled = true;
            config.fsrs_dynamic_desired_retention_params = vec![0.0; 15];
            config.fsrs_dynamic_desired_retention_weights = vec![0.0, 15.0];
            config.fsrs_dynamic_desired_retention_avg_drs = vec![0.8, 0.9];
            config.fsrs_dynamic_desired_retention_min = 0.75;
            config.fsrs_dynamic_desired_retention_max = 0.95;
        });

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let mut card = col.get_first_card();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 10;
        card.due = col.timing_today()?.days_elapsed as i32;
        card.memory_state = Some(FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: None,
            difficulty: 5.0,
        });
        card.last_review_time = Some(TimestampSecs::now().adding_secs(-5 * 86_400));
        col.storage.update_card(&card)?;

        let updater = col.card_state_updater(card.clone(), Some(0.95))?;
        let fixed_states =
            col.get_scheduling_states_with_desired_retention_override(card.id, Some(0.95))?;
        let dynamic_states =
            col.get_scheduling_states_with_desired_retention_override(card.id, Some(0.85))?;

        assert_eq!(updater.desired_retention, Some(0.95));
        assert!(updater.dynamic_desired_retentions.is_none());
        assert!(fixed_states.dynamic_desired_retentions.is_none());
        assert!(dynamic_states.dynamic_desired_retentions.is_some());

        Ok(())
    }

    #[test]
    fn desired_retention_override_outside_dynamic_dr_range_can_clamp() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.update_default_deck_config(|config| {
            config.fsrs_version = FsrsVersion::Seven as i32;
            config.fsrs_params_7 = low_retention_fsrs7_params();
            config.desired_retention = 0.85;
            config.fsrs_dynamic_desired_retention_enabled = true;
            config.fsrs_dynamic_desired_retention_params = vec![0.0; 15];
            config.fsrs_dynamic_desired_retention_weights = vec![0.0, 15.0];
            config.fsrs_dynamic_desired_retention_avg_drs = vec![0.8, 0.9];
            config.fsrs_dynamic_desired_retention_min = 0.75;
            config.fsrs_dynamic_desired_retention_max = 0.95;
            config.fsrs_dynamic_desired_retention_clamp = true;
        });

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        let mut card = col.get_first_card();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 10;
        card.due = col.timing_today()?.days_elapsed as i32;
        card.memory_state = Some(FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: None,
            difficulty: 5.0,
        });
        card.last_review_time = Some(TimestampSecs::now().adding_secs(-5 * 86_400));
        col.storage.update_card(&card)?;

        let states =
            col.get_scheduling_states_with_desired_retention_override(card.id, Some(0.95))?;

        assert!(states.dynamic_desired_retentions.is_some());

        Ok(())
    }

    #[test]
    fn desired_retention_override_takes_precedence_over_addon_preset() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.update_default_deck_config(|config| {
            config.fsrs_version = FsrsVersion::Seven as i32;
            config.fsrs_params_7 = low_retention_fsrs7_params();
            config.desired_retention = 0.65;
        });
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &FsrsPresetOverlay {
                presets: vec![AddonFsrsPreset {
                    id: "addon:test:matched".into(),
                    name: "Matched".into(),
                    fsrs_version: AddonFsrsVersion::Seven,
                    params: low_retention_fsrs7_params(),
                    desired_retention: 0.8,
                    historical_retention: 0.9,
                    ignore_revlogs_before_date: String::new(),
                    ..Default::default()
                }],
                rules: vec![FsrsPresetRule {
                    search: "front".into(),
                    preset_id: "addon:test:matched".into(),
                }],
                simulator_rules: Vec::new(),
            },
        )?;

        NoteAdder::basic(&mut col)
            .fields(&["front", "back"])
            .add(&mut col);
        let mut card = col.get_first_card();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 10;
        card.due = col.timing_today()?.days_elapsed as i32;
        card.memory_state = Some(FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: None,
            difficulty: 5.0,
        });
        card.last_review_time = Some(TimestampSecs::now().adding_secs(-5 * 86_400));
        col.storage.update_card(&card)?;

        let overlay_updater = col.card_state_updater(card.clone(), None)?;
        assert_eq!(overlay_updater.desired_retention, Some(0.8));

        let override_states =
            col.get_scheduling_states_with_desired_retention_override(card.id, Some(0.95))?;
        col.answer_card(&mut CardAnswer {
            card_id: card.id,
            current_state: override_states.current,
            new_state: override_states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: Some(0.95),
            desired_retention_override_is_final: false,
            rwkv_s90: None,
            rwkv_retrievability: None,
            rwkv_review_kind: None,
            from_queue: true,
        })?;

        let card = col.storage.get_card(card.id)?.unwrap();
        assert_eq!(card.desired_retention, Some(0.95));

        Ok(())
    }

    #[test]
    fn addon_preset_dynamic_dr_is_exposed_on_scheduling_states() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.update_default_deck_config(|config| {
            config.fsrs_version = FsrsVersion::Seven as i32;
            config.fsrs_params_7 = low_retention_fsrs7_params();
            config.desired_retention = 0.65;
        });
        col.set_config(
            FSRS_PRESET_OVERLAY_CONFIG_KEY,
            &FsrsPresetOverlay {
                presets: vec![AddonFsrsPreset {
                    id: "addon:test:matched".into(),
                    name: "Matched".into(),
                    fsrs_version: AddonFsrsVersion::Seven,
                    params: low_retention_fsrs7_params(),
                    desired_retention: 0.9,
                    historical_retention: 0.9,
                    ignore_revlogs_before_date: String::new(),
                    fsrs_dynamic_desired_retention_enabled: true,
                    fsrs_dynamic_desired_retention_params: vec![0.0; 15],
                    fsrs_dynamic_desired_retention_weights: vec![0.0, 15.0],
                    fsrs_dynamic_desired_retention_avg_drs: vec![0.9, 0.8],
                    fsrs_dynamic_desired_retention_min: 0.75,
                    fsrs_dynamic_desired_retention_max: 0.95,
                    ..Default::default()
                }],
                rules: vec![FsrsPresetRule {
                    search: "front".into(),
                    preset_id: "addon:test:matched".into(),
                }],
                simulator_rules: Vec::new(),
            },
        )?;

        NoteAdder::basic(&mut col)
            .fields(&["front", "back"])
            .add(&mut col);
        let mut card = col.get_first_card();
        card.ctype = CardType::Review;
        card.queue = CardQueue::Review;
        card.interval = 10;
        card.due = col.timing_today()?.days_elapsed as i32;
        card.memory_state = Some(FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: None,
            difficulty: 5.0,
        });
        card.last_review_time = Some(TimestampSecs::now().adding_secs(-5 * 86_400));
        col.storage.update_card(&card)?;

        let states =
            col.get_scheduling_states_with_desired_retention_override(card.id, Some(0.9))?;

        assert!(states.dynamic_desired_retention_enabled);
        assert!(states.dynamic_desired_retentions.is_some());

        Ok(())
    }

    #[test]
    fn fsrs_relearning_good_uses_fractional_same_day_elapsed_time() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.set_config_bool(BoolKey::FsrsShortTermWithStepsEnabled, true, false)?;
        col.update_default_deck_config(|config| {
            config.fsrs_version = FsrsVersion::Seven as i32;
            config.fsrs_params_7 = low_retention_fsrs7_params();
            config.desired_retention = SHORT_TERM_RELEARNING_RETENTION;
            config.relearn_steps = vec![];
        });

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let mut card = col.get_first_card();
        let now = TimestampSecs::now();
        card.ctype = CardType::Relearn;
        card.queue = CardQueue::Learn;
        card.due = now.0 as i32;
        card.interval = 1;
        card.lapses = 1;
        card.remaining_steps = 0;
        card.memory_state = Some(FsrsMemoryState {
            stability: 0.0001,
            stability_internal: 0.0001,
            stability_fast: None,
            difficulty: 9.796331,
        });
        card.last_review_time = Some(now.adding_secs(-105));
        col.storage.update_card(&card)?;

        let states = col.get_scheduling_states(card.id)?;
        let CardState::Normal(NormalState::Relearning(state)) = states.good else {
            panic!("expected Good to stay in short-term relearning");
        };
        let good_minutes = state.learning.scheduled_secs as f32 / 60.0;
        assert!(
            good_minutes > 30.0,
            "same-day Good should use elapsed seconds and grow beyond the stuck minute interval, got {good_minutes}m"
        );
        assert!(
            good_minutes < 12.0 * 60.0,
            "regression setup should still exercise short-term relearning, got {good_minutes}m"
        );
        Ok(())
    }

    #[test]
    fn fsrs_relearning_good_with_tiny_stability_writes_revlog() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.set_config_bool(BoolKey::FsrsShortTermWithStepsEnabled, true, false)?;
        col.update_default_deck_config(|config| {
            config.fsrs_version = FsrsVersion::Seven as i32;
            config.fsrs_params_7 = low_retention_fsrs7_params();
            config.desired_retention = SHORT_TERM_RELEARNING_RETENTION;
            config.relearn_steps = vec![];
        });

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let mut card = col.get_first_card();
        let now = TimestampSecs::now();
        card.ctype = CardType::Relearn;
        card.queue = CardQueue::Learn;
        card.due = now.0 as i32;
        card.interval = 1;
        card.lapses = 1;
        card.remaining_steps = 0;
        card.memory_state = Some(FsrsMemoryState {
            stability: 0.0001,
            stability_internal: 0.0001,
            stability_fast: None,
            difficulty: 9.796331,
        });
        card.last_review_time = Some(now.adding_secs(-105));
        col.storage.update_card(&card)?;

        let revlogs_before = col.storage.get_revlog_entries_for_card(card.id)?.len();
        let states = col.get_scheduling_states(card.id)?;
        col.answer_card(&mut CardAnswer {
            card_id: card.id,
            current_state: states.current,
            new_state: states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: None,
            desired_retention_override_is_final: false,
            rwkv_s90: None,
            rwkv_retrievability: None,
            rwkv_review_kind: None,
            from_queue: true,
        })?;

        let revlogs_after = col.storage.get_revlog_entries_for_card(card.id)?.len();
        assert_eq!(revlogs_after, revlogs_before + 1);
        let card = col.storage.get_card(card.id)?.unwrap();
        assert_eq!(card.reps, 1);
        assert_eq!(card.ctype, CardType::Relearn);
        Ok(())
    }

    #[test]
    fn fractional_scheduling_obeys_same_day_setting_with_empty_steps() -> Result<()> {
        for allow_same_day in [false, true] {
            for skip_learning_queues in [false, true] {
                let mut col = Collection::new();
                col.set_config_bool(BoolKey::Fsrs, true, false)?;
                col.set_config_bool(
                    BoolKey::FsrsShortTermWithStepsEnabled,
                    allow_same_day,
                    false,
                )?;
                col.set_config_bool(
                    BoolKey::FsrsLearningQueuesDisabled,
                    skip_learning_queues,
                    false,
                )?;
                col.update_default_deck_config(|config| {
                    config.fsrs_version = FsrsVersion::Seven as i32;
                    config.learn_steps = vec![];
                    config.relearn_steps = vec![];
                });
                NoteAdder::basic(&mut col).add(&mut col);
                let mut card = col.get_first_card();
                for card_type in [
                    CardType::New,
                    CardType::Learn,
                    CardType::Review,
                    CardType::Relearn,
                ] {
                    card.ctype = card_type;
                    card.queue = match card_type {
                        CardType::New => CardQueue::New,
                        CardType::Review => CardQueue::Review,
                        _ => CardQueue::Learn,
                    };
                    card.remaining_steps = 0;
                    card.interval = 1;
                    card.last_review_time = Some(TimestampSecs::now().adding_secs(-60));
                    card.memory_state = Some(FsrsMemoryState {
                        stability: 1.0,
                        stability_internal: 1.0,
                        stability_fast: Some(1.0),
                        difficulty: 5.0,
                    });
                    col.storage.update_card(&card)?;
                    // RWKV-Curve supplies unrounded intervals through this same path.
                    let states = col.scheduling_states_with_intervals(
                        card.id,
                        [Some(0.1), Some(0.2), Some(0.3), Some(0.4)],
                    )?;
                    for state in [states.again, states.hard, states.good, states.easy] {
                        if allow_same_day && !skip_learning_queues {
                            let learning = match state {
                                CardState::Normal(NormalState::Learning(learning)) => learning,
                                CardState::Normal(NormalState::Relearning(relearning)) => {
                                    relearning.learning
                                }
                                _ => panic!("expected a short-term state: {state:?}"),
                            };
                            assert!(learning.scheduled_secs > 0);
                            assert!(learning.scheduled_secs < 43_200);
                        } else {
                            let CardState::Normal(NormalState::Review(review)) = state else {
                                panic!(
                                    "expected Review for {card_type:?}, same-day={allow_same_day}, skip={skip_learning_queues}: {state:?}"
                                );
                            };
                            assert!(review.scheduled_days >= 1);
                        }
                    }
                }
            }
        }
        Ok(())
    }

    #[test]
    fn fsrs_learning_queue_bypass_keeps_rwkv_relearning_answer_in_review_queue() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.set_config_bool(BoolKey::FsrsShortTermWithStepsEnabled, true, false)?;
        col.set_config_bool(BoolKey::FsrsLearningQueuesDisabled, true, false)?;
        col.update_default_deck_config(|config| {
            config.fsrs_version = FsrsVersion::Seven as i32;
            config.fsrs_params_7 = low_retention_fsrs7_params();
            config.desired_retention = 0.65;
            config.learn_steps = vec![1.0, 10.0];
            config.relearn_steps = vec![10.0];
        });

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let new_card = col.get_first_card();
        let states = col.get_scheduling_states(new_card.id)?;
        assert!(matches!(
            states.good,
            CardState::Normal(NormalState::Review(_))
        ));

        let now = TimestampSecs::now();
        let mut relearn_card = new_card;
        relearn_card.ctype = CardType::Relearn;
        relearn_card.queue = CardQueue::Learn;
        relearn_card.due = now.0 as i32;
        relearn_card.interval = 1;
        relearn_card.lapses = 1;
        relearn_card.remaining_steps = 0;
        relearn_card.memory_state = Some(FsrsMemoryState {
            stability: 0.0001,
            stability_internal: 0.0001,
            stability_fast: None,
            difficulty: 9.796331,
        });
        relearn_card.last_review_time = Some(now.adding_secs(-105));
        col.storage.update_card(&relearn_card)?;

        let states = col.get_scheduling_states(relearn_card.id)?;
        assert!(matches!(
            states.again,
            CardState::Normal(NormalState::Review(_))
        ));

        col.update_default_deck_config(|config| config.relearn_steps = vec![]);
        let states = col.get_scheduling_states(relearn_card.id)?;
        assert!(matches!(
            states.good,
            CardState::Normal(NormalState::Review(_))
        ));

        col.answer_card(&mut CardAnswer {
            card_id: relearn_card.id,
            current_state: states.current,
            new_state: states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            desired_retention_override: None,
            desired_retention_override_is_final: false,
            rwkv_s90: None,
            rwkv_retrievability: None,
            rwkv_review_kind: Some(RevlogReviewKind::Relearning as u32),
            from_queue: true,
        })?;

        let card = col.storage.get_card(relearn_card.id)?.unwrap();
        assert_eq!(card.ctype, CardType::Review);
        assert_eq!(card.queue, CardQueue::Review);
        let revlogs = col.storage.get_revlog_entries_for_card(card.id)?;
        assert_eq!(revlogs.len(), 1);
        assert_eq!(revlogs[0].review_kind, RevlogReviewKind::Relearning);

        col.update_default_deck_config(|config| {
            config.review_order = ReviewCardOrder::RetrievabilityAscending as i32;
            config.rwkv_review_instant_order_enabled = true;
            config.rwkv_review_allow_same_day_review = true;
            config.rwkv_review_min_intervening_reviews = 0;
            config.rwkv_review_min_elapsed_secs = 0;
        });
        col.set_rwkv_review_queue_scores(
            DeckId(1),
            std::collections::HashMap::from([(card.id, 0.1)]),
        )?;

        assert_eq!(col.counts(), [0, 0, 1]);
        let queued = col.get_next_card()?.unwrap();
        assert_eq!(queued.card.id, card.id);
        assert_eq!(queued.card.ctype, CardType::Review);
        assert_eq!(queued.card.queue, CardQueue::Review);
        Ok(())
    }

    #[test]
    fn get_scheduling_states_inner_fills_missing_fsrs_fields_on_returned_card() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        // Graduate without FSRS so the stored card has no decay/dr.
        // This could happen if the card is moved between decks in an earlier version.
        col.answer_easy();
        let card_id = col.get_first_card().id;
        let mut stored = col.storage.get_card(card_id)?.unwrap();
        stored.memory_state = None;
        col.storage.update_card(&stored)?;

        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.clear_study_queues();

        let input = col.storage.get_card(card_id)?.unwrap();
        assert!(input.memory_state.is_none());

        let (_, returned) = col.get_scheduling_states_inner(input, None)?;
        assert!(returned.memory_state.is_some());

        let stored = col.storage.get_card(card_id)?.unwrap();
        assert!(stored.memory_state.is_none());

        Ok(())
    }

    // make sure the 'current' state for a card matches the
    // state we applied to it
    #[test]
    fn state_application() -> Result<()> {
        let mut col = Collection::new();
        if col.timing_today()?.near_cutoff() {
            return Ok(());
        }
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        // new->learning
        let post_answer = col.answer_again();
        let mut current = current_state(&mut col, post_answer.card_id);
        col.set_elapsed_secs_equal(&post_answer.new_state, &mut current);
        assert_eq!(post_answer.new_state, current);
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        assert_eq!(card.queue, CardQueue::Learn);
        assert_eq!(card.remaining_steps, 2);

        // learning step
        col.storage.db.execute_batch("update cards set due=0")?;
        col.clear_study_queues();
        let post_answer = col.answer_good();
        let mut current = current_state(&mut col, post_answer.card_id);
        col.set_elapsed_secs_equal(&post_answer.new_state, &mut current);
        assert_eq!(post_answer.new_state, current);
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        assert_eq!(card.queue, CardQueue::Learn);
        assert_eq!(card.remaining_steps, 1);

        // graduation
        col.storage.db.execute_batch("update cards set due=0")?;
        col.clear_study_queues();
        let mut post_answer = col.answer_good();
        // compensate for shifting the due date
        if let CardState::Normal(NormalState::Review(state)) = &mut post_answer.new_state {
            state.elapsed_days = 1;
        };
        let mut current = current_state(&mut col, post_answer.card_id);
        col.set_elapsed_secs_equal(&post_answer.new_state, &mut current);
        assert_eq!(post_answer.new_state, current);
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        assert_eq!(card.queue, CardQueue::Review);
        assert_eq!(card.interval, 1);
        assert_eq!(card.remaining_steps, 0);

        // answering a review card again; easy boost
        col.storage.db.execute_batch("update cards set due=0")?;
        col.clear_study_queues();
        let mut post_answer = col.answer_easy();
        if let CardState::Normal(NormalState::Review(state)) = &mut post_answer.new_state {
            state.elapsed_days = 4;
        };
        let mut current = current_state(&mut col, post_answer.card_id);
        col.set_elapsed_secs_equal(&post_answer.new_state, &mut current);
        assert_eq!(post_answer.new_state, current);
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        assert_eq!(card.queue, CardQueue::Review);
        assert_eq!(card.interval, 4);
        assert_eq!(card.ease_factor, 2650);

        // Same-day failure after graduation enters relearning without adding a lapse.
        col.storage.db.execute_batch("update cards set due=0")?;
        col.clear_study_queues();
        let mut post_answer = col.answer_again();
        if let CardState::Normal(NormalState::Relearning(state)) = &mut post_answer.new_state {
            state.review.elapsed_days = 1;
        };
        let mut current = current_state(&mut col, post_answer.card_id);
        col.set_elapsed_secs_equal(&post_answer.new_state, &mut current);
        assert_eq!(post_answer.new_state, current);
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        assert_eq!(card.queue, CardQueue::Learn);
        assert_eq!(card.ctype, CardType::Relearn);
        assert_eq!(card.interval, 1);
        assert_eq!(card.ease_factor, 2450);
        assert_eq!(card.lapses, 0);

        // failed in relearning
        col.storage.db.execute_batch("update cards set due=0")?;
        col.clear_study_queues();
        let mut post_answer = col.answer_again();
        if let CardState::Normal(NormalState::Relearning(state)) = &mut post_answer.new_state {
            state.review.elapsed_days = 1;
        };
        let mut current = current_state(&mut col, post_answer.card_id);
        col.set_elapsed_secs_equal(&post_answer.new_state, &mut current);
        assert_eq!(post_answer.new_state, current);
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        assert_eq!(card.queue, CardQueue::Learn);
        assert_eq!(card.lapses, 0);

        // re-graduating
        col.storage.db.execute_batch("update cards set due=0")?;
        col.clear_study_queues();
        let mut post_answer = col.answer_good();
        if let CardState::Normal(NormalState::Review(state)) = &mut post_answer.new_state {
            state.elapsed_days = 1;
        };
        let mut current = current_state(&mut col, post_answer.card_id);
        col.set_elapsed_secs_equal(&post_answer.new_state, &mut current);
        assert_eq!(post_answer.new_state, current);
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        assert_eq!(card.queue, CardQueue::Review);
        assert_eq!(card.interval, 1);

        Ok(())
    }

    pub(crate) fn v3_test_collection(cards: usize) -> Result<(Collection, Vec<CardId>)> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        for _ in 0..cards {
            let mut note = Note::new(&nt);
            col.add_note(&mut note, DeckId(1))?;
        }
        let cids = col.search_cards("", SortMode::NoOrder)?;
        Ok((col, cids))
    }

    macro_rules! assert_counts {
        ($col:ident, $new:expr, $learn:expr, $review:expr) => {{
            let tree = $col.deck_tree(Some(TimestampSecs::now())).unwrap();
            assert_eq!(tree.new_count, $new);
            assert_eq!(tree.learn_count, $learn);
            assert_eq!(tree.review_count, $review);
            let queued = $col.get_queued_cards(1, false, false).unwrap();
            assert_eq!(queued.new_count, $new);
            assert_eq!(queued.learning_count, $learn);
            assert_eq!(queued.review_count, $review);
        }};
    }

    #[test]
    fn new_limited_by_reviews() -> Result<()> {
        let (mut col, cids) = v3_test_collection(4)?;
        // The final answer schedules a learning card a 10-minute step ahead. Run
        // shortly before the daily cutoff (e.g. 3:50-4:00 GMT with the default 4am
        // rollover), that step crosses into the next day and the card is no longer
        // counted as intraday learning, so the expected counts break. Skip in that
        // window, as the sibling timing-sensitive tests do.
        if col.timing_today()?.near_cutoff() {
            return Ok(());
        }
        col.set_due_date(&cids[0..2], "0", None)?;
        // set a limit of 3 reviews, which should give us 2 reviews and 1 new card
        let mut conf = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
        conf.inner.reviews_per_day = 3;
        conf.inner.set_new_mix(ReviewMix::BeforeReviews);
        col.storage.update_deck_conf(&conf)?;

        assert_counts!(col, 1, 0, 2);
        // first card is the new card
        col.answer_good();
        assert_counts!(col, 0, 1, 2);
        // then the two reviews
        col.answer_good();
        assert_counts!(col, 0, 1, 1);
        col.answer_good();
        assert_counts!(col, 0, 1, 0);
        // after the final 10 minute step, the queues should be empty
        col.answer_good();
        assert_counts!(col, 0, 0, 0);

        Ok(())
    }

    #[test]
    fn elapsed_secs() -> Result<()> {
        let mut col = Collection::new();
        let mut conf = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        // Need to set col age for interday learning test, arbitrary
        col.storage
            .db
            .execute_batch("update col set crt=1686045847")?;
        // Fails when near cutoff since it assumes inter- and intraday learning
        if col.timing_today()?.near_cutoff() {
            return Ok(());
        }
        col.add_note(&mut note, DeckId(1))?;
        // 5942.7 minutes for just over four days
        conf.inner.learn_steps = vec![1.0, 10.5, 15.0, 20.0, 5942.7];
        col.storage.update_deck_conf(&conf)?;

        // Intraday learning, review same day
        let expected_elapsed_secs = 662;
        let post_answer = col.answer_good();
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        let shift_due_time = card.due - expected_elapsed_secs;
        assert_elapsed_secs_approx_equal(
            &mut col,
            shift_due_time,
            post_answer,
            expected_elapsed_secs,
        )?;

        // Intraday learning, learn ahead
        let expected_elapsed_secs = 212;
        let post_answer = col.answer_good();
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        let shift_due_time = card.due - expected_elapsed_secs;
        assert_elapsed_secs_approx_equal(
            &mut col,
            shift_due_time,
            post_answer,
            expected_elapsed_secs,
        )?;

        // Intraday learning, review two (and some) days later
        let expected_elapsed_secs = 184092;
        let post_answer = col.answer_good();
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        let shift_due_time = card.due - expected_elapsed_secs;
        assert_elapsed_secs_approx_equal(
            &mut col,
            shift_due_time,
            post_answer,
            expected_elapsed_secs,
        )?;

        // Interday learning four (and some) days, review three days late
        let expected_elapsed_secs = 7 * 86_400;
        let post_answer = col.answer_good();
        let now = TimestampSecs::now();
        let timing = col.timing_for_timestamp(now)?;
        let col_age = timing.days_elapsed as i32;
        let shift_due_time = col_age - 3; // Three days late
        assert_elapsed_secs_approx_equal(
            &mut col,
            shift_due_time,
            post_answer,
            expected_elapsed_secs,
        )?;

        Ok(())
    }

    fn assert_elapsed_secs_approx_equal(
        col: &mut Collection,
        shift_due_time: i32,
        post_answer: test_helpers::PostAnswerState,
        expected_elapsed_secs: i32,
    ) -> Result<()> {
        // Change due time to fake card answer_time,
        // works since answer_time is calculated as due - last_ivl
        let update_due_string = format!("update cards set due={shift_due_time}");
        col.storage.db.execute_batch(&update_due_string)?;
        col.clear_study_queues();
        let current_card_state = current_state(col, post_answer.card_id);
        let state = match current_card_state {
            CardState::Normal(NormalState::Learning(state)) => state,
            _ => panic!("State is not Normal: {current_card_state:?}"),
        };
        let elapsed_secs = state.elapsed_secs as i32;
        // Give a 1 second leeway when the test runs on the off chance
        // that the test runs as a second rolls over.
        assert!(
            (elapsed_secs - expected_elapsed_secs).abs() <= 1,
            "elapsed_secs: {elapsed_secs} != expected_elapsed_secs: {expected_elapsed_secs}"
        );

        Ok(())
    }

    #[test]
    fn cached_fsrs_flags_follow_config_changes_removal_and_undo() -> Result<()> {
        let mut col = Collection::new();
        crate::tests::NoteAdder::basic(&mut col).add(&mut col);
        // No active queue: scheduling falls back to config.
        col.set_config_bool(BoolKey::Fsrs, true, false)?;
        col.set_config_bool(BoolKey::FsrsShortTermWithStepsEnabled, true, false)?;
        assert!(col.fsrs_enabled());
        assert!(col.fsrs_short_term_with_steps_enabled());
        col.get_queued_cards(1, false, true)?;
        assert!(col.state.card_queues.as_ref().unwrap().fsrs_enabled);
        assert!(
            col.state
                .card_queues
                .as_ref()
                .unwrap()
                .fsrs_short_term_with_steps
        );

        for key in [BoolKey::Fsrs, BoolKey::FsrsShortTermWithStepsEnabled] {
            col.set_config_bool(key, false, true)?;
            assert!(
                col.state.card_queues.is_some(),
                "config writes preserve the queue"
            );
            assert_eq!(col.fsrs_enabled(), col.get_config_bool(BoolKey::Fsrs));
            assert_eq!(
                col.fsrs_short_term_with_steps_enabled(),
                col.get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled)
            );
            col.undo()?;
            assert!(col.fsrs_enabled());
            assert!(col.fsrs_short_term_with_steps_enabled());
            col.redo()?;
            assert!(!col.get_config_bool(key));
            assert_eq!(col.fsrs_enabled(), col.get_config_bool(BoolKey::Fsrs));
            assert_eq!(
                col.fsrs_short_term_with_steps_enabled(),
                col.get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled)
            );
            col.undo()?;

            let key_str: &str = key.into();
            col.remove_config(key_str)?;
            assert_eq!(col.fsrs_enabled(), col.get_config_bool(BoolKey::Fsrs));
            assert_eq!(
                col.fsrs_short_term_with_steps_enabled(),
                col.get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled)
            );
            col.undo()?;
            assert!(col.fsrs_enabled());
            assert!(col.fsrs_short_term_with_steps_enabled());
        }
        Ok(())
    }
}
