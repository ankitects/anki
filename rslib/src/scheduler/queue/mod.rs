// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod builder;
pub(crate) use builder::intersperser::Intersperser;
mod entry;
mod learning;
mod main;
mod rwkv;
pub(crate) mod undo;

use std::collections::HashMap;
use std::collections::HashSet;
use std::collections::VecDeque;

use anki_proto::scheduler::SchedulingContext;
pub(crate) use builder::DueCard;
pub(crate) use builder::DueCardKind;
pub(crate) use builder::DueCardWithState;
pub(crate) use builder::NewCard;
pub(crate) use entry::QueueEntry;
pub(crate) use entry::QueueEntryKind;
pub(crate) use learning::LearningQueueEntry;
pub(crate) use main::MainQueueEntry;
pub(crate) use main::MainQueueEntryKind;

use self::rwkv::RwkvLearningRepeatGuards;
use self::undo::CutoffSnapshot;
use self::undo::QueueUpdate;
use super::states::SchedulingStates;
use super::timing::SchedTimingToday;
use crate::prelude::*;
use crate::scheduler::rwkv::RwkvReviewScoreEligibility;
use crate::scheduler::states::load_balancer::LoadBalancer;
use crate::timestamp::TimestampSecs;

#[derive(Debug)]
pub(crate) struct CardQueues {
    counts: Counts,
    main: VecDeque<MainQueueEntry>,
    intraday_learning: VecDeque<LearningQueueEntry>,
    current_day: u32,
    learn_ahead_secs: i64,
    build_time: TimestampMillis,
    /// Updated each time a card is answered, and by get_queued_cards() when the
    /// counts are zero. Ensures we don't show a newly-due learning card after a
    /// user returns from editing a review card.
    current_learning_cutoff: TimestampSecs,
    shown_top_card: Option<CardId>,
    non_news_sorted_by_retrievability: bool,
    deferred_rwkv_reviews: HashMap<CardId, DeferredRwkvReview>,
    rwkv_learning_repeat_guards: Option<RwkvLearningRepeatGuards>,
    blocked_rwkv_learning_cards: HashSet<CardId>,
    new_review_mix_progress: NewReviewMixProgress,
    pub(crate) load_balancer: Option<LoadBalancer>,
    pub(crate) fsrs_enabled: bool,
    pub(crate) fsrs_short_term_with_steps: bool,
}

/// Session-only mixing progress, carried across RWKV score-driven rebuilds.
#[derive(Debug, Clone, Copy)]
pub(crate) struct NewReviewMixProgress {
    deck_id: DeckId,
    day: u32,
    new_answered: usize,
    review_answered: usize,
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct DeferredRwkvReview {
    pub(crate) required_intervening_reviews: Option<u32>,
    pub(crate) intervening_reviews: Option<u32>,
    pub(crate) eligible_at: Option<TimestampSecs>,
}

impl DeferredRwkvReview {
    pub(crate) fn from_eligibility(
        eligibility: RwkvReviewScoreEligibility,
        now: TimestampSecs,
    ) -> Option<Self> {
        match eligibility {
            RwkvReviewScoreEligibility::Deferred {
                required_intervening_reviews,
                intervening_reviews,
                remaining_elapsed_secs,
            } => Some(Self {
                required_intervening_reviews,
                intervening_reviews,
                eligible_at: remaining_elapsed_secs
                    .map(|remaining| now.adding_secs(remaining.into())),
            }),
            RwkvReviewScoreEligibility::Eligible | RwkvReviewScoreEligibility::Blocked => None,
        }
    }

    fn is_ready(self, now: TimestampSecs) -> bool {
        self.required_intervening_reviews.is_none_or(|required| {
            self.intervening_reviews
                .is_some_and(|reviews| reviews >= required)
        }) && self
            .eligible_at
            .is_none_or(|eligible_at| now >= eligible_at)
    }
}

#[derive(Debug, Copy, Clone)]
pub struct Counts {
    pub new: usize,
    pub learning: usize,
    pub review: usize,
}

impl Counts {
    fn all_zero(self) -> bool {
        self.new == 0 && self.learning == 0 && self.review == 0
    }
}

#[derive(Debug, Clone)]
pub struct QueuedCard {
    pub card: Card,
    pub kind: QueueEntryKind,
    pub states: Option<SchedulingStates>,
    pub context: SchedulingContext,
}

#[derive(Debug)]
pub struct QueuedCards {
    pub cards: Vec<QueuedCard>,
    pub new_count: usize,
    pub learning_count: usize,
    pub review_count: usize,
}

/// When we encounter a card with new or review burying enabled, all future
/// siblings need to be buried, regardless of their own settings.
#[derive(Default, Debug, Clone, Copy)]
pub(crate) struct BuryMode {
    pub(crate) bury_new: bool,
    pub(crate) bury_reviews: bool,
    pub(crate) bury_interday_learning: bool,
}

impl Collection {
    pub fn get_next_card(&mut self) -> Result<Option<QueuedCard>> {
        self.get_queued_cards(1, false, false)
            .map(|queued| queued.cards.first().cloned())
    }

    pub fn get_queued_cards(
        &mut self,
        fetch_limit: usize,
        intraday_learning_only: bool,
        skip_scheduling_states: bool,
    ) -> Result<QueuedCards> {
        let queues = self.get_queues()?;
        let counts = queues.counts();
        let entries: Vec<_> = if intraday_learning_only {
            queues
                .intraday_now_iter()
                .chain(queues.intraday_ahead_iter())
                .map(Into::into)
                .collect()
        } else {
            queues.iter().take(fetch_limit).collect()
        };
        if fetch_limit == 1 && !intraday_learning_only {
            let shown = entries
                .first()
                .map(|entry| (entry.card_id(), entry.mtime()));
            queues.mark_top_card_shown(shown.map(|(card_id, _)| card_id));
            self.state.shown_card = shown;
        }
        let cards: Vec<_> = entries
            .into_iter()
            .map(|entry| {
                let card = self
                    .storage
                    .get_card(entry.card_id())?
                    .or_not_found(entry.card_id())?;
                require!(
                    card.mtime == entry.mtime(),
                    "bug: card modified without updating queue: id:{} card:{} entry:{}",
                    card.id,
                    card.mtime,
                    entry.mtime()
                );

                let (next_states, card) = if skip_scheduling_states {
                    (None, card)
                } else {
                    let (states, card) = self.get_scheduling_states_inner(card, None)?;
                    (Some(states), card)
                };

                Ok(QueuedCard {
                    context: new_scheduling_context(self, &card)?,
                    card,
                    states: next_states,
                    kind: entry.kind(),
                })
            })
            .collect::<Result<_>>()?;
        Ok(QueuedCards {
            cards,
            new_count: counts.new,
            learning_count: counts.learning,
            review_count: counts.review,
        })
    }
}

fn new_scheduling_context(col: &mut Collection, card: &Card) -> Result<SchedulingContext> {
    Ok(SchedulingContext {
        deck_name: col
            .get_deck(card.original_or_current_deck_id())?
            .or_not_found(card.deck_id)?
            .human_name(),
        seed: card.review_seed(),
        decay: card.decay,
        desired_retention: card.desired_retention,
    })
}

impl CardQueues {
    pub(crate) fn main_contains(&self, card_id: CardId) -> bool {
        self.main.iter().any(|entry| entry.id == card_id)
    }

    pub(crate) fn update_deferred_rwkv_intervening_reviews(
        &mut self,
        intervening_reviews_by_card_id: &HashMap<CardId, u32>,
    ) -> bool {
        let now = TimestampSecs::now();
        let mut rebuild_required = false;
        for (card_id, intervening_reviews) in intervening_reviews_by_card_id {
            if let Some(deferred) = self.deferred_rwkv_reviews.get_mut(card_id) {
                deferred.intervening_reviews = Some(*intervening_reviews);
                rebuild_required |= deferred.is_ready(now);
            }
        }
        rebuild_required
    }

    pub(crate) fn defer_rwkv_review(&mut self, card_id: CardId, deferred: DeferredRwkvReview) {
        self.deferred_rwkv_reviews.insert(card_id, deferred);
    }

    pub(crate) fn remove_deferred_rwkv_review(&mut self, card_id: CardId) {
        self.deferred_rwkv_reviews.remove(&card_id);
    }

    fn deferred_rwkv_review_is_ready(&self) -> bool {
        let now = TimestampSecs::now();
        self.deferred_rwkv_reviews
            .values()
            .any(|deferred| deferred.is_ready(now))
    }

    fn mark_top_card_shown(&mut self, card_id: Option<CardId>) {
        self.shown_top_card = card_id;
    }

    fn preserve_current_card(&mut self, card_id: CardId) -> Result<()> {
        if let Some(position) = self.main.iter().position(|entry| entry.id == card_id) {
            let entry = self.main.remove(position).unwrap();
            self.main.push_front(entry);
        } else {
            require!(
                self.intraday_learning
                    .iter()
                    .any(|entry| entry.id == card_id),
                "current card missing from rebuilt queue"
            );
        }
        self.mark_top_card_shown(Some(card_id));
        Ok(())
    }

    /// An iterator over the card queues, in the order the cards will
    /// be presented.
    fn iter(&self) -> impl Iterator<Item = QueueEntry> + '_ {
        let intraday_now = (!self.non_news_sorted_by_retrievability)
            .then_some(())
            .into_iter()
            .flat_map(|_| self.intraday_now_iter().map(Into::into));
        intraday_now
            .chain(
                self.main
                    .iter()
                    .filter(|entry| !self.rwkv_blocks_learning_card(entry.id))
                    .map(Into::into),
            )
            .chain(self.intraday_ahead_iter().map(Into::into))
    }

    /// Remove the provided card from the top of the queues and
    /// adjust the counts. If it was not at the top, return an error.
    fn pop_entry(&mut self, id: CardId) -> Result<QueueEntry> {
        if let Some(pos) = self.intraday_learning.iter().position(|e| e.id == id) {
            let entry = self.intraday_learning.remove(pos).unwrap();
            // FIXME:
            // under normal circumstances this should not go below 0, but currently
            // the Python unit tests answer learning cards before they're due
            self.counts.learning = self.counts.learning.saturating_sub(1);
            self.shown_top_card = None;
            Ok(entry.into())
        } else if self
            .main
            .iter()
            .find(|entry| !self.rwkv_blocks_learning_card(entry.id))
            .is_some_and(|entry| entry.id == id)
        {
            let entry = self.pop_main().unwrap();
            self.shown_top_card = None;
            Ok(entry.into())
        } else {
            invalid_input!("not at top of queue")
        }
    }

    fn push_undo_entry(&mut self, entry: QueueEntry) {
        // Undo restores the last answered card, including a learning card
        // preserved through a queue rebuild before its repeat minimums elapsed.
        self.shown_top_card = (self.rwkv_learning_repeat_guards.is_some()
            && entry.kind() == QueueEntryKind::Learning)
            .then_some(entry.card_id());
        match entry {
            QueueEntry::IntradayLearning(entry) => self.push_intraday_learning(entry),
            QueueEntry::Main(entry) => self.push_main(entry),
        }
    }

    /// Return the current due counts. If there are no due cards, the learning
    /// cutoff is updated to the current time first, and any newly-due learning
    /// cards are added to the counts.
    pub(crate) fn counts(&mut self) -> Counts {
        if self.counts.all_zero() {
            // we discard the returned undo information in this case
            self.update_learning_cutoff_and_count();
        }
        self.counts_snapshot()
    }

    /// Inspect the current visible counts without advancing the learning
    /// cutoff.
    pub(crate) fn counts_snapshot(&self) -> Counts {
        Counts {
            learning: if self.blocked_rwkv_learning_cards.is_empty() {
                self.counts.learning
            } else {
                self.counts
                    .learning
                    .saturating_sub(self.blocked_rwkv_learning_count())
            },
            ..self.counts
        }
    }

    fn is_stale(&self, current_day: u32) -> bool {
        self.current_day != current_day
    }

    fn due_intraday_needs_retrievability_resort(&self) -> bool {
        self.non_news_sorted_by_retrievability
            && self
                .intraday_learning
                .front()
                .map(|entry| entry.due <= TimestampSecs::now())
                .unwrap_or(false)
    }
}

impl Collection {
    pub(crate) fn rebuild_queued_cards_preserving_current_card(
        &mut self,
        current_card_id: CardId,
    ) -> Result<QueuedCards> {
        let deck = self.get_current_deck()?;
        self.clear_queues_if_day_changed()?;
        let card = self
            .storage
            .get_card(current_card_id)?
            .or_not_found(current_card_id)?;
        let queues = self.build_queues_with_current_card(deck.id, Some(&card))?;
        self.state.card_queues = Some(queues);
        self.state.pending_new_review_mix = None;
        self.refresh_rwkv_learning_repeat_guards(&deck)?;
        let counts = self.state.card_queues.as_mut().unwrap().counts();
        Ok(QueuedCards {
            cards: vec![],
            new_count: counts.new,
            learning_count: counts.learning,
            review_count: counts.review,
        })
    }

    /// This is automatically done when transact() is called for everything
    /// except card answers, so unless you are modifying state outside of a
    /// transaction, you probably don't need this.
    pub(crate) fn clear_study_queues(&mut self) {
        self.state.card_queues = None;
        self.state.pending_new_review_mix = None;
    }

    pub(crate) fn invalidate_rwkv_review_queue(&mut self) {
        if let Some(queues) = self.state.card_queues.take() {
            self.state.pending_new_review_mix = Some(queues.new_review_mix_progress);
        }
    }

    pub(crate) fn maybe_clear_study_queues_after_op(&mut self, op: &OpChanges) {
        if op.op != Op::AnswerCard && op.requires_study_queue_rebuild() {
            self.clear_study_queues();
        }
    }

    pub(crate) fn update_queues_after_answering_card(
        &mut self,
        card: &Card,
        timing: SchedTimingToday,
        is_finished_preview: bool,
        entry_before: Option<QueueEntry>,
    ) -> Result<()> {
        if self
            .state
            .shown_card
            .is_some_and(|(card_id, _)| card_id == card.id)
        {
            self.state.shown_card = None;
        }
        if let Some(queues) = &mut self.state.card_queues {
            let Ok(entry) = queues.pop_entry(card.id) else {
                // kuma3: a card answered from outside the queue's top (kumapie rates a word's
                // own cards wherever they are): the queue is built again when it's next needed.
                self.clear_study_queues();
                self.save_unqueued_answer_undo(entry_before);
                return Ok(());
            };
            let requeued_learning = if is_finished_preview {
                None
            } else {
                queues.maybe_requeue_learning_card(card, timing)
            };
            let cutoff_snapshot = queues.update_learning_cutoff_and_count();
            let queue_build_time = queues.build_time;
            self.save_queue_update_undo(Box::new(QueueUpdate {
                entry,
                learning_requeue: requeued_learning,
                queue_build_time,
                cutoff_snapshot,
            }));
        } else {
            // we currently allow the queues to be empty for unit tests;
            // kuma3: RWKV drops them when its scores change
            self.save_unqueued_answer_undo(entry_before);
        }

        Ok(())
    }

    /// kuma3: an answer the queues could not record (RWKV had dropped them, or
    /// the card was not on top): Undo still shows the card first
    /// ([CollectionState::shown_card]). The build time matches no queue, so
    /// Undo builds them again.
    fn save_unqueued_answer_undo(&mut self, entry: Option<QueueEntry>) {
        if let Some(entry) = entry {
            self.save_queue_update_undo(Box::new(QueueUpdate {
                entry,
                learning_requeue: None,
                queue_build_time: TimestampMillis(0),
                cutoff_snapshot: CutoffSnapshot {
                    learning_count: 0,
                    learning_cutoff: TimestampSecs(0),
                },
            }));
        }
    }

    /// Get the card queues, building if necessary.
    pub(crate) fn get_queues(&mut self) -> Result<&mut CardQueues> {
        let deck = self.get_current_deck()?;
        self.clear_queues_if_day_changed()?;
        if self.state.card_queues.is_none()
            || self
                .state
                .card_queues
                .as_ref()
                .map(|queues| {
                    queues.due_intraday_needs_retrievability_resort()
                        || queues.deferred_rwkv_review_is_ready()
                })
                .unwrap_or(false)
        {
            self.rwkv_offline_before_queue_build(deck.id);
            // A card the fork pinned (rebuild_queued_cards_preserving_current_card)
            // stays first; else kuma3's shown card, if unchanged since shown.
            let pinned = self
                .state
                .card_queues
                .as_ref()
                .and_then(|queues| queues.shown_top_card);
            let shown = match (pinned, self.state.shown_card) {
                (Some(pinned), shown) if shown.is_none_or(|(id, _)| id != pinned) => {
                    self.storage.get_card(pinned)?
                }
                _ => self.unchanged_shown_card()?,
            };
            let queues = match shown {
                Some(shown) => self
                    .build_queues_with_current_card(deck.id, Some(&shown))
                    .or_else(|err| {
                        tracing::debug!(?err, "shown card not kept first");
                        self.build_queues(deck.id)
                    })?,
                None => self.build_queues(deck.id)?,
            };
            self.state.card_queues = Some(queues);
            self.state.pending_new_review_mix = None;
        }

        self.refresh_rwkv_learning_repeat_guards(&deck)?;
        Ok(self.state.card_queues.as_mut().unwrap())
    }

    /// [CollectionState::shown_card] if it is still as it was shown: answered
    /// elsewhere (kumapie, a sync), buried or suspended, it is not kept.
    fn unchanged_shown_card(&mut self) -> Result<Option<Card>> {
        let Some((card_id, mtime)) = self.state.shown_card else {
            return Ok(None);
        };
        Ok(self
            .storage
            .get_card(card_id)?
            .filter(|card| card.mtime == mtime))
    }

    /// kuma3: the cards [deck_id]'s study queue would show today (RWKV-Instant
    /// and the daily limits, as studying it builds it), built on the side
    /// for kumapie (kuma3/due). The current deck and Undo are left alone:
    /// selecting each deck to read its queue was a "Select Deck" undo step,
    /// and the day's first queue read discards Undo. The deck list
    /// (deck_due_tree) unburies on a new day, so call it first.
    pub fn deck_queue(&mut self, deck_id: DeckId) -> Result<Vec<(CardId, QueueEntryKind)>> {
        self.rwkv_offline_before_queue_build(deck_id);
        let queues = self.build_queues(deck_id)?;
        Ok(queues.iter().map(|e| (e.card_id(), e.kind())).collect())
    }

    // Returns queues if they are valid and have not been rebuilt. If build time has
    // changed, they are cleared.
    pub(crate) fn get_or_invalidate_queues(
        &mut self,
        build_time: TimestampMillis,
    ) -> Result<Option<&mut CardQueues>> {
        self.clear_queues_if_day_changed()?;
        let same_build = self
            .state
            .card_queues
            .as_ref()
            .map(|q| q.build_time == build_time)
            .unwrap_or_default();
        if same_build {
            Ok(self.state.card_queues.as_mut())
        } else {
            self.clear_study_queues();
            Ok(None)
        }
    }

    fn clear_queues_if_day_changed(&mut self) -> Result<()> {
        let timing = self.timing_today()?;
        let day_rolled_over = self
            .state
            .card_queues
            .as_ref()
            .map(|q| q.is_stale(timing.days_elapsed))
            .unwrap_or(false);
        if day_rolled_over {
            self.state.shown_card = None;
            self.discard_undo_and_study_queues();
            self.unbury_on_day_rollover(timing.days_elapsed)?;
        }
        Ok(())
    }
}

// test helpers
#[cfg(test)]
impl Collection {
    pub(crate) fn counts(&mut self) -> [usize; 3] {
        self.get_queued_cards(1, false, false)
            .map(|q| [q.new_count, q.learning_count, q.review_count])
            .unwrap_or([0; 3])
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::tests::NoteAdder;

    #[test]
    fn get_queues_keeps_pinned_card_first_when_rebuilding_for_ready_deferred_review() -> Result<()>
    {
        let mut col = Collection::new();
        let first_note = NoteAdder::basic(&mut col).add(&mut col);
        let first_card = col.storage.card_ids_of_notes(&[first_note.id])?[0];
        let pinned_note = NoteAdder::basic(&mut col).add(&mut col);
        let pinned_card = col.storage.card_ids_of_notes(&[pinned_note.id])?[0];
        assert_eq!(
            col.get_queued_cards(1, false, true)?.cards[0].card.id,
            first_card
        );

        col.rebuild_queued_cards_preserving_current_card(pinned_card)?;
        col.state.card_queues.as_mut().unwrap().defer_rwkv_review(
            first_card,
            DeferredRwkvReview {
                required_intervening_reviews: None,
                intervening_reviews: None,
                eligible_at: None,
            },
        );

        let queued = col.get_queued_cards(1, false, true)?;

        assert_eq!(queued.cards[0].card.id, pinned_card);
        assert_eq!(
            col.state.card_queues.as_ref().unwrap().shown_top_card,
            Some(pinned_card)
        );
        Ok(())
    }

    #[test]
    fn deferred_rwkv_review_requires_every_guard_to_be_ready() {
        let now = TimestampSecs(100);
        let mut deferred = DeferredRwkvReview {
            required_intervening_reviews: Some(2),
            intervening_reviews: Some(1),
            eligible_at: Some(TimestampSecs(99)),
        };
        assert!(!deferred.is_ready(now));

        deferred.intervening_reviews = Some(2);
        deferred.eligible_at = Some(TimestampSecs(101));
        assert!(!deferred.is_ready(now));

        deferred.eligible_at = Some(now);
        assert!(deferred.is_ready(now));
    }

    // kuma3: kumapie answers a word's own cards wherever they are in the queue (not
    // only its top).
    mod answering_below_the_top {
        use super::*;
        use crate::card::CardQueue;
        use crate::card::CardType;
        use crate::scheduler::answering::CardAnswer;
        use crate::scheduler::answering::Rating;
        use crate::tests::NoteAdder;

        /// A collection with three cards in its queue: new ones, or review ones
        /// due today.
        fn three_cards(review: bool) -> Result<(Collection, Vec<CardId>)> {
            let mut col = Collection::new();
            for _ in 0..3 {
                NoteAdder::basic(&mut col).add(&mut col);
            }
            if review {
                for _ in 0..3 {
                    col.answer_easy();
                }
                col.storage.db.execute_batch("UPDATE cards SET due = 0")?;
                col.clear_study_queues();
            }
            let ids = col
                .get_queued_cards(3, false, false)?
                .cards
                .iter()
                .map(|c| c.card.id)
                .collect();
            Ok((col, ids))
        }

        /// Answers the card at [index] of the built queue, as kumapie does
        /// through kuma3's provider.
        fn answer_at(col: &mut Collection, index: usize, rating: Rating) -> Result<()> {
            let queued = col.get_queued_cards(3, false, false)?.cards.remove(index);
            let states = queued.states.unwrap();
            let new_state = match rating {
                Rating::Again => states.again,
                Rating::Hard => states.hard,
                Rating::Good => states.good,
                Rating::Easy => states.easy,
            };
            col.answer_card(&mut CardAnswer {
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
            Ok(())
        }

        /// Again on the card at [index]: it goes to (re)learning, nothing is
        /// lost, the others keep their order.
        fn again_at(review: bool, index: usize) -> Result<()> {
            let (mut col, ids) = three_cards(review)?;
            answer_at(&mut col, index, Rating::Again)?;
            let again = col.storage.get_card(ids[index])?.unwrap();
            assert_eq!(again.queue, CardQueue::Learn);
            assert_eq!(
                again.ctype,
                if review {
                    CardType::Relearn
                } else {
                    CardType::Learn
                }
            );
            let rest: Vec<CardId> = ids.iter().copied().filter(|id| *id != ids[index]).collect();
            let queued = col.get_queued_cards(3, false, false)?;
            assert_eq!(
                queued
                    .cards
                    .iter()
                    .map(|c| c.card.id)
                    .take(2)
                    .collect::<Vec<_>>(),
                rest
            );
            assert_eq!(queued.learning_count, 1);
            assert_eq!(
                if review {
                    queued.review_count
                } else {
                    queued.new_count
                },
                2
            );
            Ok(())
        }

        #[test]
        fn good_on_the_second_card() -> Result<()> {
            let (mut col, ids) = three_cards(false)?;
            answer_at(&mut col, 1, Rating::Good)?;
            assert_ne!(col.storage.get_card(ids[1])?.unwrap().queue, CardQueue::New);
            assert_eq!(col.get_next_card()?.unwrap().card.id, ids[0]);
            Ok(())
        }

        #[test]
        fn again_on_the_middle_new_card() -> Result<()> {
            again_at(false, 1)
        }

        #[test]
        fn again_on_the_last_new_card() -> Result<()> {
            again_at(false, 2)
        }

        #[test]
        fn again_on_the_middle_review_card() -> Result<()> {
            again_at(true, 1)
        }

        #[test]
        fn again_on_the_last_review_card() -> Result<()> {
            again_at(true, 2)
        }
    }

    /// kumapie's kuma3/due: another deck's queue without selecting it, and Undo
    /// still undoes the last answer.
    #[test]
    fn deck_queue_leaves_current_deck_and_undo() -> Result<()> {
        use crate::tests::DeckAdder;
        use crate::tests::NoteAdder;
        let mut col = Collection::new();
        let other = DeckAdder::new("other").add(&mut col);
        for _ in 0..2 {
            NoteAdder::basic(&mut col).add(&mut col);
        }
        for _ in 0..3 {
            NoteAdder::basic(&mut col).deck(other.id).add(&mut col);
        }
        col.answer_good();
        let current = col.get_current_deck_id();
        let undo = col.undo_status().undo;
        assert!(undo.is_some());

        let queue = col.deck_queue(other.id)?;
        assert_eq!(queue.len(), 3);
        assert!(queue.iter().all(|(_, kind)| *kind == QueueEntryKind::New));
        assert_eq!(col.get_current_deck_id(), current);
        assert_eq!(col.undo_status().undo, undo);

        // the same cards as selecting the deck and reading its queue
        col.set_current_deck(other.id)?;
        let selected: Vec<_> = col
            .get_queued_cards(100, false, true)?
            .cards
            .iter()
            .map(|c| c.card.id)
            .collect();
        assert_eq!(
            queue.iter().map(|(id, _)| *id).collect::<Vec<_>>(),
            selected
        );
        Ok(())
    }
}
