// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod current;
mod learning;
mod preview;
mod relearning;
mod review;
mod revlog;
mod undo;

use crate::{
    backend_proto,
    card::CardQueue,
    deckconf::{DeckConf, LeechAction},
    decks::Deck,
    prelude::*,
};

use revlog::RevlogEntryPartial;

use super::{
    states::{
        steps::LearningSteps, CardState, FilteredState, NextCardStates, NormalState, StateContext,
    },
    timespan::answer_button_time_collapsible,
    timing::SchedTimingToday,
};

#[derive(Copy, Clone)]
pub enum Rating {
    Again,
    Hard,
    Good,
    Easy,
}

pub struct CardAnswer {
    pub card_id: CardID,
    pub current_state: CardState,
    pub new_state: CardState,
    pub rating: Rating,
    pub answered_at: TimestampMillis,
    pub milliseconds_taken: u32,
}

// fixme: log preview review

/// Holds the information required to determine a given card's
/// current state, and to apply a state change to it.
struct CardStateUpdater {
    card: Card,
    deck: Deck,
    config: DeckConf,
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
            fuzz_seed: self.fuzz_seed,
            steps: self.learn_steps(),
            graduating_interval_good: self.config.inner.graduating_interval_good,
            graduating_interval_easy: self.config.inner.graduating_interval_easy,
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
        (self.timing.next_day_at - self.now.0).max(0) as u32
    }

    fn into_card(self) -> Card {
        self.card
    }

    fn apply_study_state(
        &mut self,
        current: CardState,
        next: CardState,
    ) -> Result<Option<RevlogEntryPartial>> {
        let revlog = match next {
            CardState::Normal(normal) => {
                // transitioning from filtered state?
                if let CardState::Filtered(filtered) = &current {
                    match filtered {
                        FilteredState::Preview(_) => {
                            return Err(AnkiError::invalid_input(
                                "should set finished=true, not return different state",
                            ));
                        }
                        FilteredState::Rescheduling(_) => {
                            // card needs to be removed from normal filtered deck, then scheduled normally
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
        }?;

        Ok(revlog)
    }

    fn apply_normal_study_state(
        &mut self,
        current: CardState,
        next: NormalState,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.card.reps += 1;
        self.card.original_due = 0;

        let revlog = match next {
            NormalState::New(next) => self.apply_new_state(current, next),
            NormalState::Learning(next) => self.apply_learning_state(current, next),
            NormalState::Review(next) => self.apply_review_state(current, next),
            NormalState::Relearning(next) => self.apply_relearning_state(current, next),
        }?;

        if next.leeched() && self.config.inner.leech_action() == LeechAction::Suspend {
            self.card.queue = CardQueue::Suspended;
        }

        Ok(revlog)
    }

    fn ensure_filtered(&self) -> Result<()> {
        if self.card.original_deck_id.0 == 0 {
            Err(AnkiError::invalid_input(
                "card answering can't transition into filtered state",
            ))
        } else {
            Ok(())
        }
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
    pub fn get_next_card_states(&mut self, cid: CardID) -> Result<NextCardStates> {
        let card = self.storage.get_card(cid)?.ok_or(AnkiError::NotFound)?;
        let ctx = self.card_state_updater(card)?;
        let current = ctx.current_card_state();
        let state_ctx = ctx.state_context();
        Ok(current.next_states(&state_ctx))
    }

    /// Describe the next intervals, to display on the answer buttons.
    pub fn describe_next_states(&mut self, choices: NextCardStates) -> Result<Vec<String>> {
        let collapse_time = self.learn_ahead_secs();
        let now = TimestampSecs::now();
        let timing = self.timing_for_timestamp(now)?;
        let secs_until_rollover = (timing.next_day_at - now.0).max(0) as u32;

        Ok(vec![
            answer_button_time_collapsible(
                choices
                    .again
                    .interval_kind()
                    .maybe_as_days(secs_until_rollover)
                    .as_seconds(),
                collapse_time,
                &self.i18n,
            ),
            answer_button_time_collapsible(
                choices
                    .hard
                    .interval_kind()
                    .maybe_as_days(secs_until_rollover)
                    .as_seconds(),
                collapse_time,
                &self.i18n,
            ),
            answer_button_time_collapsible(
                choices
                    .good
                    .interval_kind()
                    .maybe_as_days(secs_until_rollover)
                    .as_seconds(),
                collapse_time,
                &self.i18n,
            ),
            answer_button_time_collapsible(
                choices
                    .easy
                    .interval_kind()
                    .maybe_as_days(secs_until_rollover)
                    .as_seconds(),
                collapse_time,
                &self.i18n,
            ),
        ])
    }

    /// Answer card, writing its new state to the database.
    pub fn answer_card(&mut self, answer: &CardAnswer) -> Result<()> {
        self.transact(Some(UndoableOpKind::AnswerCard), |col| {
            col.answer_card_inner(answer)
        })
    }

    fn answer_card_inner(&mut self, answer: &CardAnswer) -> Result<()> {
        let card = self
            .storage
            .get_card(answer.card_id)?
            .ok_or(AnkiError::NotFound)?;
        let original = card.clone();
        let usn = self.usn()?;

        let mut updater = self.card_state_updater(card)?;
        let current_state = updater.current_card_state();
        if current_state != answer.current_state {
            return Err(AnkiError::invalid_input(format!(
                "card was modified: {:#?} {:#?}",
                current_state, answer.current_state,
            )));
        }
        if let Some(revlog_partial) = updater.apply_study_state(current_state, answer.new_state)? {
            self.add_partial_revlog(revlog_partial, usn, &answer)?;
        }
        self.update_deck_stats_from_answer(usn, &answer, &updater)?;
        self.maybe_bury_siblings(&original, &updater.config)?;
        let timing = updater.timing;
        let mut card = updater.into_card();
        self.update_card_inner(&mut card, &original, usn)?;
        if answer.new_state.leeched() {
            self.add_leech_tag(card.note_id)?;
        }

        self.update_queues_after_answering_card(&card, timing)?;

        Ok(())
    }

    fn maybe_bury_siblings(&mut self, card: &Card, config: &DeckConf) -> Result<()> {
        if config.inner.bury_new || config.inner.bury_reviews {
            self.bury_siblings(
                card.id,
                card.note_id,
                config.inner.bury_new,
                config.inner.bury_reviews,
            )?;
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
    ) -> Result<()> {
        self.update_deck_stats(
            updater.timing.days_elapsed,
            usn,
            backend_proto::UpdateStatsIn {
                deck_id: updater.deck.id.0,
                new_delta: if matches!(answer.current_state, CardState::Normal(NormalState::New(_)))
                {
                    1
                } else {
                    0
                },
                review_delta: if matches!(
                    answer.current_state,
                    CardState::Normal(NormalState::Review(_))
                ) {
                    1
                } else {
                    0
                },
                millisecond_delta: answer.milliseconds_taken as i32,
            },
        )
    }

    fn card_state_updater(&mut self, card: Card) -> Result<CardStateUpdater> {
        let timing = self.timing_today()?;
        let deck = self
            .storage
            .get_deck(card.deck_id)?
            .ok_or(AnkiError::NotFound)?;
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
        config_id: Option<DeckConfID>,
        home_deck_id: DeckID,
    ) -> Result<DeckConf> {
        let config_id = if let Some(config_id) = config_id {
            config_id
        } else {
            let home_deck = self
                .storage
                .get_deck(home_deck_id)?
                .ok_or(AnkiError::NotFound)?;
            home_deck.config_id().ok_or(AnkiError::NotFound)?
        };

        Ok(self.storage.get_deck_config(config_id)?.unwrap_or_default())
    }

    fn add_leech_tag(&mut self, nid: NoteID) -> Result<()> {
        self.update_note_tags(nid, |tags| tags.push("leech".into()))
    }
}

/// Return a consistent seed for a given card at a given number of reps.
/// If in test environment, disable fuzzing.
fn get_fuzz_seed(card: &Card) -> Option<u64> {
    if *crate::timestamp::TESTING || cfg!(test) {
        None
    } else {
        Some((card.id.0 as u64).wrapping_add(card.reps as u64))
    }
}
