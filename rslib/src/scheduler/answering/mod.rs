// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod current;
mod learning;
mod preview;
mod relearning;
mod review;
mod revlog;

use rand::prelude::*;
use rand::rngs::StdRng;
use revlog::RevlogEntryPartial;

use super::queue::BuryMode;
use super::states::steps::LearningSteps;
use super::states::CardState;
use super::states::FilteredState;
use super::states::NormalState;
use super::states::SchedulingStates;
use super::states::StateContext;
use super::timespan::answer_button_time_collapsible;
use super::timing::SchedTimingToday;
use crate::card::CardQueue;
use crate::deckconfig::DeckConfig;
use crate::deckconfig::LeechAction;
use crate::decks::Deck;
use crate::pb;
use crate::prelude::*;

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
}

impl CardStateUpdater {
    /// Returns information required when transitioning from one card state to
    /// another with `next_states()`. This separate structure decouples the
    /// state handling code from the rest of the Anki codebase.
    pub(crate) fn state_context(&self) -> StateContext<'_> {
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
            relearn_steps: self.relearn_steps(),
            lapse_multiplier: self.config.inner.lapse_multiplier,
            minimum_lapse_interval: self.config.inner.minimum_lapse_interval,
            in_filtered_deck: self.deck.is_filtered(),
            preview_step: if let DeckKind::Filtered(deck) = &self.deck.kind {
                deck.preview_delay
            } else {
                0
            },
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
                        self.apply_normal_study_state(current, next.original_state)
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
        self.card.original_due = 0;

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
        let ctx = self.card_state_updater(card)?;
        let current = ctx.current_card_state();
        let state_ctx = ctx.state_context();
        Ok(current.next_states(&state_ctx))
    }

    /// Describe the next intervals, to display on the answer buttons.
    pub fn describe_next_states(&mut self, choices: SchedulingStates) -> Result<Vec<String>> {
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

    fn answer_card_inner(&mut self, answer: &mut CardAnswer) -> Result<()> {
        let card = self
            .storage
            .get_card(answer.card_id)?
            .or_not_found(answer.card_id)?;
        let original = card.clone();
        let usn = self.usn()?;

        let mut updater = self.card_state_updater(card)?;
        answer.cap_answer_secs(updater.config.inner.cap_answer_time_to_secs);
        let current_state = updater.current_card_state();
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
        let mut card = updater.into_card();
        if let Some(data) = answer.custom_data.take() {
            card.custom_data = data;
            card.validate_custom_data()?;
        }
        self.update_card_inner(&mut card, original, usn)?;
        if answer.new_state.leeched() {
            self.add_leech_tag(card.note_id)?;
        }

        self.update_queues_after_answering_card(&card, timing)
    }

    fn maybe_bury_siblings(&mut self, card: &Card, config: &DeckConfig) -> Result<()> {
        let bury_mode = BuryMode::from_deck_config(config);
        if bury_mode.any_burying() {
            self.bury_siblings(card.id, card.note_id, bury_mode)?;
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
            pb::scheduler::UpdateStatsRequest {
                deck_id: updater.deck.id.0,
                new_delta,
                review_delta,
                millisecond_delta: answer.milliseconds_taken as i32,
            },
        )
    }

    fn card_state_updater(&mut self, card: Card) -> Result<CardStateUpdater> {
        let timing = self.timing_today()?;
        let deck = self
            .storage
            .get_deck(card.deck_id)?
            .or_not_found(card.deck_id)?;
        let config = self.home_deck_config(deck.config_id(), card.original_deck_id)?;
        Ok(CardStateUpdater {
            fuzz_seed: get_fuzz_seed(&card),
            card,
            deck,
            config,
            timing,
            now: TimestampSecs::now(),
        })
    }

    fn home_deck_config(
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
            })?;
            Ok(PostAnswerState {
                card_id: queued.card.id,
                new_state,
            })
        }
    }
}

/// Return a consistent seed for a given card at a given number of reps.
/// If in test environment, disable fuzzing.
fn get_fuzz_seed(card: &Card) -> Option<u64> {
    if *crate::PYTHON_UNIT_TESTS || cfg!(test) {
        None
    } else {
        Some((card.id.0 as u64).wrapping_add(card.reps as u64))
    }
}

/// Return a fuzz factor from the range `0.0..1.0`, using the provided seed.
/// None if seed is None.
fn get_fuzz_factor(seed: Option<u64>) -> Option<f32> {
    seed.map(|s| StdRng::seed_from_u64(s).gen_range(0.0..1.0))
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::card::CardType;
    use crate::collection::open_test_collection;
    use crate::deckconfig::ReviewMix;
    use crate::search::SortMode;

    fn current_state(col: &mut Collection, card_id: CardId) -> CardState {
        col.get_scheduling_states(card_id).unwrap().current
    }

    // make sure the 'current' state for a card matches the
    // state we applied to it
    #[test]
    fn state_application() -> Result<()> {
        let mut col = open_test_collection();
        if col.timing_today()?.near_cutoff() {
            return Ok(());
        }
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;

        // new->learning
        let post_answer = col.answer_again();
        assert_eq!(
            post_answer.new_state,
            current_state(&mut col, post_answer.card_id)
        );
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        assert_eq!(card.queue, CardQueue::Learn);
        assert_eq!(card.remaining_steps, 2);

        // learning step
        col.storage.db.execute_batch("update cards set due=0")?;
        col.clear_study_queues();
        let post_answer = col.answer_good();
        assert_eq!(
            post_answer.new_state,
            current_state(&mut col, post_answer.card_id)
        );
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
        assert_eq!(
            post_answer.new_state,
            current_state(&mut col, post_answer.card_id)
        );
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
        assert_eq!(
            post_answer.new_state,
            current_state(&mut col, post_answer.card_id)
        );
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
        assert_eq!(
            post_answer.new_state,
            current_state(&mut col, post_answer.card_id)
        );
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
        assert_eq!(
            post_answer.new_state,
            current_state(&mut col, post_answer.card_id)
        );
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
        assert_eq!(
            post_answer.new_state,
            current_state(&mut col, post_answer.card_id)
        );
        let card = col.storage.get_card(post_answer.card_id)?.unwrap();
        assert_eq!(card.queue, CardQueue::Review);
        assert_eq!(card.interval, 1);

        Ok(())
    }

    fn v3_test_collection(cards: usize) -> Result<(Collection, Vec<CardId>)> {
        let mut col = open_test_collection();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        for _ in 0..cards {
            let mut note = Note::new(&nt);
            col.add_note(&mut note, DeckId(1))?;
        }
        col.set_config_bool(BoolKey::Sched2021, true, false)?;
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
}
