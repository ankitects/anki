// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto,
    card::{CardQueue, CardType},
    deckconf::DeckConf,
    decks::{Deck, DeckKind},
    prelude::*,
    revlog::{RevlogEntry, RevlogReviewKind},
};

use super::{
    cutoff::SchedTimingToday,
    states::{
        steps::LearningSteps, CardState, FilteredState, IntervalKind, LearnState, NewState,
        NextCardStates, NormalState, PreviewState, RelearnState, ReschedulingFilterState,
        ReviewState, StateContext,
    },
    timespan::answer_button_time_collapsible,
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

// FIXME: suspension
// fixme: fuzz learning intervals, graduating intervals
// fixme: 4 buttons for previewing
// fixme: log previewing
// fixme: - undo

/// Information needed when answering a card.
struct AnswerContext {
    deck: Deck,
    config: DeckConf,
    timing: SchedTimingToday,
    now: TimestampSecs,
    fuzz_seed: Option<u64>,
}

impl AnswerContext {
    fn state_context(&self) -> StateContext<'_> {
        StateContext {
            fuzz_seed: self.fuzz_seed,
            steps: self.learn_steps(),
            graduating_interval_good: self.config.inner.graduating_interval_good,
            graduating_interval_easy: self.config.inner.graduating_interval_easy,
            hard_multiplier: self.config.inner.hard_multiplier,
            easy_multiplier: self.config.inner.easy_multiplier,
            interval_multiplier: self.config.inner.interval_multiplier,
            maximum_review_interval: self.config.inner.maximum_review_interval,
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

    fn secs_until_rollover(&self) -> u32 {
        (self.timing.next_day_at - self.now.0).max(0) as u32
    }

    fn normal_study_state(
        &self,
        ctype: CardType,
        due: i32,
        interval: u32,
        lapses: u32,
        ease_factor: f32,
        remaining_steps: u32,
    ) -> NormalState {
        match ctype {
            CardType::New => NormalState::New(NewState {
                position: due.max(0) as u32,
            }),
            CardType::Learn => {
                LearnState {
                    scheduled_secs: self.learn_steps().current_delay_secs(remaining_steps),
                    remaining_steps,
                }
            }
            .into(),
            CardType::Review => ReviewState {
                scheduled_days: interval,
                elapsed_days: ((interval as i32) - (due - self.timing.days_elapsed as i32)).max(0)
                    as u32,
                ease_factor,
                lapses,
            }
            .into(),
            CardType::Relearn => RelearnState {
                learning: LearnState {
                    scheduled_secs: self.relearn_steps().current_delay_secs(remaining_steps),
                    remaining_steps,
                },
                review: ReviewState {
                    scheduled_days: interval,
                    elapsed_days: interval,
                    ease_factor,
                    lapses,
                },
            }
            .into(),
        }
    }

    // FIXME: context depends on deck conf, but card passed in later - needs rethink
    fn current_card_state(&self, card: &Card) -> CardState {
        let interval = card.interval;
        let lapses = card.lapses;
        let ease_factor = card.ease_factor();
        let remaining_steps = card.remaining_steps();
        let ctype = card.ctype;

        let due = match &self.deck.kind {
            DeckKind::Normal(_) => {
                // if not in a filtered deck, ensure due time is not before today,
                // which avoids tripping up test_nextIvl() in the Python tests
                if matches!(ctype, CardType::Review) {
                    card.due.min(self.timing.days_elapsed as i32)
                } else {
                    card.due
                }
            }
            DeckKind::Filtered(_) => {
                if card.original_due != 0 {
                    card.original_due
                } else {
                    // v2 scheduler resets original_due on first answer
                    card.due
                }
            }
        };

        let normal_state =
            self.normal_study_state(ctype, due, interval, lapses, ease_factor, remaining_steps);

        match &self.deck.kind {
            // normal decks have normal state
            DeckKind::Normal(_) => normal_state.into(),
            // filtered decks wrap the normal state
            DeckKind::Filtered(filtered) => {
                if filtered.reschedule {
                    ReschedulingFilterState {
                        original_state: normal_state,
                    }
                    .into()
                } else {
                    PreviewState {
                        scheduled_secs: filtered.preview_delay * 60,
                        original_state: normal_state,
                    }
                    .into()
                }
            }
        }
    }

    fn learn_steps(&self) -> LearningSteps<'_> {
        LearningSteps::new(&self.config.inner.learn_steps)
    }

    fn relearn_steps(&self) -> LearningSteps<'_> {
        LearningSteps::new(&self.config.inner.relearn_steps)
    }
}

impl Card {
    fn apply_study_state(
        &mut self,
        current: CardState,
        next: CardState,
        ctx: &AnswerContext,
    ) -> Result<Option<RevlogEntryPartial>> {
        // any non-preview answer resets card.odue and increases reps
        if !matches!(current, CardState::Filtered(FilteredState::Preview(_))) {
            self.reps += 1;
            self.original_due = 0;
        }

        match next {
            CardState::Normal(normal) => match normal {
                NormalState::New(next) => self.apply_new_state(current, next, ctx),
                NormalState::Learning(next) => self.apply_learning_state(current, next, ctx),
                NormalState::Review(next) => self.apply_review_state(current, next, ctx),
                NormalState::Relearning(next) => self.apply_relearning_state(current, next, ctx),
            },
            CardState::Filtered(filtered) => match filtered {
                FilteredState::Preview(next) => self.apply_preview_state(current, next, ctx),
                FilteredState::Rescheduling(next) => {
                    self.apply_rescheduling_state(current, next, ctx)
                }
            },
        }
    }

    fn apply_new_state(
        &mut self,
        current: CardState,
        next: NewState,
        ctx: &AnswerContext,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.ctype = CardType::New;
        self.queue = CardQueue::New;
        self.due = next.position as i32;

        Ok(RevlogEntryPartial::maybe_new(
            current,
            next.into(),
            0.0,
            ctx.secs_until_rollover(),
        ))
    }

    fn apply_learning_state(
        &mut self,
        current: CardState,
        next: LearnState,
        ctx: &AnswerContext,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.remaining_steps = next.remaining_steps;
        self.ctype = CardType::Learn;

        let interval = next
            .interval_kind()
            .maybe_as_days(ctx.secs_until_rollover());
        match interval {
            IntervalKind::InSecs(secs) => {
                self.queue = CardQueue::Learn;
                self.due = TimestampSecs::now().0 as i32 + secs as i32;
            }
            IntervalKind::InDays(days) => {
                self.queue = CardQueue::DayLearn;
                self.due = (ctx.timing.days_elapsed + days) as i32;
            }
        }

        Ok(RevlogEntryPartial::maybe_new(
            current,
            next.into(),
            0.0,
            ctx.secs_until_rollover(),
        ))
    }

    fn apply_review_state(
        &mut self,
        current: CardState,
        next: ReviewState,
        ctx: &AnswerContext,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.remove_from_filtered_deck_before_reschedule();

        self.queue = CardQueue::Review;
        self.ctype = CardType::Review;
        self.interval = next.scheduled_days;
        self.due = (ctx.timing.days_elapsed + next.scheduled_days) as i32;
        self.ease_factor = (next.ease_factor * 1000.0).round() as u16;
        self.lapses = next.lapses;

        Ok(RevlogEntryPartial::maybe_new(
            current,
            next.into(),
            next.ease_factor,
            ctx.secs_until_rollover(),
        ))
    }

    fn apply_relearning_state(
        &mut self,
        current: CardState,
        next: RelearnState,
        ctx: &AnswerContext,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.interval = next.review.scheduled_days;
        self.remaining_steps = next.learning.remaining_steps;
        self.ctype = CardType::Relearn;

        let interval = next
            .interval_kind()
            .maybe_as_days(ctx.secs_until_rollover());
        match interval {
            IntervalKind::InSecs(secs) => {
                self.queue = CardQueue::Learn;
                self.due = TimestampSecs::now().0 as i32 + secs as i32;
            }
            IntervalKind::InDays(days) => {
                self.queue = CardQueue::DayLearn;
                self.due = (ctx.timing.days_elapsed + days) as i32;
            }
        }

        Ok(RevlogEntryPartial::maybe_new(
            current,
            next.into(),
            next.review.ease_factor,
            ctx.secs_until_rollover(),
        ))
    }

    // fixme: check learning card moved into preview
    // restores correctly in both learn and day-learn case
    fn apply_preview_state(
        &mut self,
        current: CardState,
        next: PreviewState,
        ctx: &AnswerContext,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.ensure_filtered()?;
        self.queue = CardQueue::PreviewRepeat;

        let interval = next.interval_kind();
        match interval {
            IntervalKind::InSecs(secs) => {
                self.due = TimestampSecs::now().0 as i32 + secs as i32;
            }
            IntervalKind::InDays(_days) => {
                unreachable!()
            }
        }

        Ok(RevlogEntryPartial::maybe_new(
            current,
            next.into(),
            0.0,
            ctx.secs_until_rollover(),
        ))
    }

    // fixme: better name
    fn apply_rescheduling_state(
        &mut self,
        current: CardState,
        next: ReschedulingFilterState,
        ctx: &AnswerContext,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.ensure_filtered()?;
        self.apply_study_state(current, next.original_state.into(), ctx)
    }

    fn ensure_filtered(&self) -> Result<()> {
        if self.original_deck_id.0 == 0 {
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
pub struct RevlogEntryPartial {
    interval: IntervalKind,
    last_interval: IntervalKind,
    ease_factor: f32,
    review_kind: RevlogReviewKind,
}

impl RevlogEntryPartial {
    fn maybe_new(
        current: CardState,
        next: CardState,
        ease_factor: f32,
        secs_until_rollover: u32,
    ) -> Option<Self> {
        current.revlog_kind().map(|review_kind| {
            let next_interval = next.interval_kind().maybe_as_days(secs_until_rollover);
            let current_interval = current.interval_kind().maybe_as_days(secs_until_rollover);

            RevlogEntryPartial {
                interval: next_interval,
                last_interval: current_interval,
                ease_factor,
                review_kind,
            }
        })
    }

    fn into_revlog_entry(
        self,
        usn: Usn,
        cid: CardID,
        button_chosen: u8,
        answered_at: TimestampMillis,
        taken_millis: u32,
    ) -> RevlogEntry {
        RevlogEntry {
            id: answered_at,
            cid,
            usn,
            button_chosen,
            interval: self.interval.as_revlog_interval(),
            last_interval: self.last_interval.as_revlog_interval(),
            ease_factor: (self.ease_factor * 1000.0).round() as u32,
            taken_millis,
            review_kind: self.review_kind,
        }
    }
}

impl Collection {
    pub fn describe_next_states(&self, choices: NextCardStates) -> Result<Vec<String>> {
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

    pub fn answer_card(&mut self, answer: &CardAnswer) -> Result<()> {
        self.transact(None, |col| col.answer_card_inner(answer))
    }

    fn answer_card_inner(&mut self, answer: &CardAnswer) -> Result<()> {
        let mut card = self
            .storage
            .get_card(answer.card_id)?
            .ok_or(AnkiError::NotFound)?;
        let answer_context = self.answer_context(&card)?;
        let current_state = answer_context.current_card_state(&card);
        if current_state != answer.current_state {
            // fixme: unique error
            return Err(AnkiError::invalid_input(format!(
                "card was modified: {:#?} {:#?}",
                current_state, answer.current_state,
            )));
        }
        let original = card.clone();
        let usn = self.usn()?;

        if let Some(revlog_partial) =
            card.apply_study_state(current_state, answer.new_state, &answer_context)?
        {
            let button_chosen = answer.rating.as_number();
            let revlog = revlog_partial.into_revlog_entry(
                usn,
                answer.card_id,
                button_chosen,
                answer.answered_at,
                answer.milliseconds_taken,
            );
            self.storage.add_revlog_entry(&revlog)?;
        }
        self.update_card(&mut card, &original, usn)?;

        // fixme: we're reusing code used by python, which means re-feteching the target deck
        // - might want to avoid that in the future
        self.update_deck_stats(
            answer_context.timing.days_elapsed,
            usn,
            backend_proto::UpdateStatsIn {
                deck_id: answer_context.deck.id.0,
                new_delta: if matches!(current_state, CardState::Normal(NormalState::New(_))) {
                    1
                } else {
                    0
                },
                review_delta: if matches!(current_state, CardState::Normal(NormalState::Review(_)))
                {
                    1
                } else {
                    0
                },
                millisecond_delta: answer.milliseconds_taken as i32,
            },
        )?;

        Ok(())
    }

    fn answer_context(&mut self, card: &Card) -> Result<AnswerContext> {
        let timing = self.timing_today()?;
        Ok(AnswerContext {
            // fixme: fetching deck twice
            deck: self
                .storage
                .get_deck(card.deck_id)?
                .ok_or(AnkiError::NotFound)?,
            config: self.deck_config_for_card(card)?,
            timing,
            now: TimestampSecs::now(),
            fuzz_seed: get_fuzz_seed(card),
        })
    }

    pub fn get_next_card_states(&mut self, cid: CardID) -> Result<NextCardStates> {
        let card = self.storage.get_card(cid)?.ok_or(AnkiError::NotFound)?;
        let ctx = self.answer_context(&card)?;
        let current = ctx.current_card_state(&card);
        let state_ctx = ctx.state_context();
        Ok(current.next_states(&state_ctx))
    }
}

/// Return a consistent seed for a given card at a given number of reps.
/// If in test environment, disable fuzzing.
fn get_fuzz_seed(card: &Card) -> Option<u64> {
    if *crate::timestamp::TESTING {
        None
    } else {
        Some((card.id.0 as u64).wrapping_add(card.reps as u64))
    }
}
