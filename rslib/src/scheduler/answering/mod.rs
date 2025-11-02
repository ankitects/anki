// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod current;
mod learning;
mod preview;
mod relearning;
mod review;
mod revlog;

use fsrs::NextStates;
use fsrs::FSRS;
use rand::prelude::*;
use rand::rngs::StdRng;
use revlog::RevlogEntryPartial;

use super::fsrs::params::ignore_revlogs_before_ms_from_config;
use super::queue::BuryMode;
use super::states::load_balancer::LoadBalancerContext;
use super::states::steps::LearningSteps;
use super::states::CardState;
use super::states::FilteredState;
use super::states::NormalState;
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
use crate::scheduler::fsrs::memory_state::fsrs_item_for_memory_state;
use crate::scheduler::fsrs::memory_state::get_decay_from_params;
use crate::scheduler::states::PreviewState;
use crate::search::SearchNode;

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
    config: DeckConfig,
    timing: SchedTimingToday,
    now: TimestampSecs,
    fuzz_seed: Option<u64>,
    /// Set if FSRS is enabled.
    fsrs_next_states: Option<NextStates>,
    /// Set if FSRS is enabled.
    desired_retention: Option<f32>,
    fsrs_short_term_with_steps: bool,
    fsrs_allow_short_term: bool,
}

impl CardStateUpdater {
    /// Returns information required when transitioning from one card state to
    /// another with `next_states()`. This separate structure decouples the
    /// state handling code from the rest of the Anki codebase.
    pub(crate) fn state_context<'a>(
        &'a self,
        load_balancer_ctx: Option<LoadBalancerContext<'a>>,
    ) -> StateContext<'a> {
        StateContext {
            fuzz_factor: get_fuzz_factor(self.fuzz_seed),
            steps: self.learn_steps(),
            graduating_interval_good: self.config.inner.graduating_interval_good,
            graduating_interval_easy: self.config.inner.graduating_interval_easy,
            initial_ease_factor: self.config.inner.initial_ease,
            hard_multiplier: self.config.inner.hard_multiplier,
            easy_multiplier: self.config.inner.easy_multiplier,
            interval_multiplier: self.config.inner.interval_multiplier,
            maximum_review_interval: self.config.inner.maximum_review_interval,
            leech_threshold: self.config.inner.leech_threshold,
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
            fsrs_allow_short_term: self.fsrs_allow_short_term,
        }
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

    fn apply_study_state(
        &mut self,
        current: CardState,
        next: CardState,
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
                self.apply_normal_study_state(current, normal)
            }
            CardState::Filtered(filtered) => {
                self.ensure_filtered()?;
                match filtered {
                    FilteredState::Preview(next) => self.apply_preview_state(current, next),
                    FilteredState::Rescheduling(next) => {
                        let revlog = self.apply_normal_study_state(current, next.original_state);
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
    ) -> RevlogEntryPartial {
        self.card.reps += 1;
        self.card.desired_retention = self.desired_retention;

        let revlog = match next {
            NormalState::New(next) => self.apply_new_state(current, next),
            NormalState::Learning(next) => self.apply_learning_state(current, next),
            NormalState::Review(next) => self.apply_review_state(current, next),
            NormalState::Relearning(next) => self.apply_relearning_state(current, next),
        };

        if next.leeched() && self.config.inner.leech_action() == LeechAction::Suspend {
            self.card.queue = CardQueue::Suspended;
        }

        revlog
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
}

impl Collection {
    /// Return the next states that will be applied for each answer button.
    pub fn get_scheduling_states(&mut self, cid: CardId) -> Result<SchedulingStates> {
        let card = self.storage.get_card(cid)?.or_not_found(cid)?;
        let note_id = card.note_id;

        let ctx = self.card_state_updater(card)?;
        let current = ctx.current_card_state();

        let load_balancer_ctx = if let Some(load_balancer) = self
            .state
            .card_queues
            .as_ref()
            .and_then(|card_queues| card_queues.load_balancer.as_ref())
        {
            // Only get_deck_config when load balancer is enabled
            if let Some(deck_config_id) = ctx.deck.config_id() {
                let note_id = self
                    .get_deck_config(deck_config_id, false)?
                    .map(|deck_config| deck_config.inner.bury_reviews)
                    .unwrap_or(false)
                    .then_some(note_id);
                Some(load_balancer.review_context(note_id, deck_config_id))
            } else {
                None
            }
        } else {
            None
        };

        let state_ctx = ctx.state_context(load_balancer_ctx);
        Ok(current.next_states(&state_ctx))
    }

    /// Describe the next intervals, to display on the answer buttons.
    pub fn describe_next_states(&mut self, choices: &SchedulingStates) -> Result<Vec<String>> {
        let collapse_time = self.learn_ahead_secs();
        let now = TimestampSecs::now();
        let timing = self.timing_for_timestamp(now)?;
        let secs_until_rollover = timing.next_day_at.elapsed_secs_since(now).max(0) as u32;

        Ok(vec![
            answer_button_time_collapsible(
                choices
                    .again
                    .interval_kind()
                    .maybe_as_days(secs_until_rollover)
                    .as_seconds(),
                collapse_time,
                &self.tr,
            ),
            answer_button_time_collapsible(
                choices
                    .hard
                    .interval_kind()
                    .maybe_as_days(secs_until_rollover)
                    .as_seconds(),
                collapse_time,
                &self.tr,
            ),
            answer_button_time_collapsible(
                choices
                    .good
                    .interval_kind()
                    .maybe_as_days(secs_until_rollover)
                    .as_seconds(),
                collapse_time,
                &self.tr,
            ),
            answer_button_time_collapsible(
                choices
                    .easy
                    .interval_kind()
                    .maybe_as_days(secs_until_rollover)
                    .as_seconds(),
                collapse_time,
                &self.tr,
            ),
        ])
    }

    /// Answer card, writing its new state to the database.
    /// Provided [CardAnswer] has its answer time capped to deck preset.
    pub fn answer_card(&mut self, answer: &mut CardAnswer) -> Result<OpOutput<()>> {
        self.transact(Op::AnswerCard, |col| col.answer_card_inner(answer))
    }

    pub(crate) fn answer_card_inner(&mut self, answer: &mut CardAnswer) -> Result<()> {
        let card = self
            .storage
            .get_card(answer.card_id)?
            .or_not_found(answer.card_id)?;
        let original = card.clone();
        let usn = self.usn()?;

        let mut updater = self.card_state_updater(card)?;
        answer.cap_answer_secs(updater.config.inner.cap_answer_time_to_secs);
        let current_state = updater.current_card_state();
        // If the states aren't equal, it's probably because some time has passed.
        // Try to fix this by setting elapsed_secs equal.
        self.set_elapsed_secs_equal(&current_state, &mut answer.current_state);
        require!(
            current_state == answer.current_state,
            "card was modified: {current_state:#?} {:#?}",
            answer.current_state,
        );

        let revlog_partial = updater.apply_study_state(current_state, answer.new_state)?;
        self.add_partial_revlog(revlog_partial, usn, answer)?;

        self.update_deck_stats_from_answer(usn, answer, &updater, original.queue)?;
        self.maybe_bury_siblings(&original, &updater.config)?;
        let timing = updater.timing;
        let deckconfig_id = updater.deck.config_id();
        let mut card = updater.into_card();
        if !matches!(
            answer.current_state,
            CardState::Filtered(FilteredState::Preview(_))
        ) {
            card.last_review_time = Some(answer.answered_at.as_secs());
        }
        if let Some(data) = answer.custom_data.take() {
            card.custom_data = data;
            card.validate_custom_data()?;
        }

        self.update_card_inner(&mut card, original, usn)?;
        if answer.new_state.leeched() {
            self.add_leech_tag(card.note_id)?;
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
            )?;
        }

        Ok(())
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
    ) -> Result<()> {
        let revlog = partial.into_revlog_entry(
            usn,
            answer.card_id,
            answer.rating.as_number(),
            answer.answered_at,
            answer.milliseconds_taken,
        );
        self.add_revlog_entry_undoable(revlog)?;
        Ok(())
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

    fn card_state_updater(&mut self, mut card: Card) -> Result<CardStateUpdater> {
        let timing = self.timing_today()?;
        let deck = self
            .storage
            .get_deck(card.deck_id)?
            .or_not_found(card.deck_id)?;
        let home_deck = self
            .storage
            .get_deck(card.original_or_current_deck_id())?
            .or_not_found(card.original_or_current_deck_id())?;
        let config = self
            .storage
            .get_deck_config(home_deck.config_id().or_invalid("home deck is filtered")?)?
            .unwrap_or_default();

        let desired_retention = home_deck.effective_desired_retention(&config);
        let fsrs_enabled = self.get_config_bool(BoolKey::Fsrs);
        let fsrs_next_states = if fsrs_enabled {
            let params = config.fsrs_params();
            let fsrs = FSRS::new(Some(params))?;
            card.decay = Some(get_decay_from_params(params));
            if card.memory_state.is_none() && card.ctype != CardType::New {
                // Card has been moved or imported into an FSRS deck after params were set,
                // and will need its initial memory state to be calculated based on review
                // history.
                let revlog = self.revlog_for_srs(SearchNode::CardIds(card.id.to_string()))?;
                let item = fsrs_item_for_memory_state(
                    &fsrs,
                    revlog,
                    timing.next_day_at,
                    config.inner.historical_retention,
                    ignore_revlogs_before_ms_from_config(&config)?,
                )?;
                card.set_memory_state(&fsrs, item, config.inner.historical_retention)?;
            }
            let days_elapsed = if let Some(last_review_time) = card.last_review_time {
                timing.next_day_at.elapsed_days_since(last_review_time) as u32
            } else {
                self.storage
                    .time_of_last_review(card.id)?
                    .map(|ts| timing.next_day_at.elapsed_days_since(ts))
                    .unwrap_or_default() as u32
            };
            Some(fsrs.next_states(
                card.memory_state.map(Into::into),
                desired_retention,
                days_elapsed,
            )?)
        } else {
            None
        };
        let desired_retention = fsrs_enabled.then_some(desired_retention);
        let fsrs_short_term_with_steps =
            self.get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled);
        let fsrs_allow_short_term = if fsrs_enabled {
            let params = config.fsrs_params();
            if params.len() >= 19 {
                params[17] > 0.0 && params[18] > 0.0
            } else {
                false
            }
        } else {
            false
        };
        Ok(CardStateUpdater {
            fuzz_seed: get_fuzz_seed(&card, false),
            card,
            deck,
            config,
            timing,
            now: TimestampSecs::now(),
            fsrs_next_states,
            desired_retention,
            fsrs_short_term_with_steps,
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

    fn add_leech_tag(&mut self, nid: NoteId) -> Result<()> {
        self.add_tags_to_notes_inner(&[nid], "leech")?;
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
            let new_state = get_state(&queued.states);
            self.answer_card(&mut CardAnswer {
                card_id: queued.card.id,
                current_state: queued.states.current,
                new_state,
                rating,
                answered_at: TimestampMillis::now(),
                milliseconds_taken: 0,
                custom_data: None,
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
    use crate::card::CardType;
    use crate::deckconfig::ReviewMix;
    use crate::search::SortMode;

    fn current_state(col: &mut Collection, card_id: CardId) -> CardState {
        col.get_scheduling_states(card_id).unwrap().current
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
        let updater = col.card_state_updater(card)?;

        // Print debug information
        println!("FSRS enabled: {}", col.get_config_bool(BoolKey::Fsrs));
        println!("Desired retention: {:?}", updater.desired_retention);

        // Verify that the desired retention is from the deck, not the config
        assert_eq!(updater.desired_retention, Some(0.85));

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

        // lapsing it
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
        assert_eq!(card.lapses, 1);

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
        assert_eq!(card.lapses, 1);

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
            let queued = $col.get_queued_cards(1, false).unwrap();
            assert_eq!(queued.new_count, $new);
            assert_eq!(queued.learning_count, $learn);
            assert_eq!(queued.review_count, $review);
        }};
    }

    // FIXME: This fails between 3:50-4:00 GMT
    #[test]
    fn new_limited_by_reviews() -> Result<()> {
        let (mut col, cids) = v3_test_collection(4)?;
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
}
