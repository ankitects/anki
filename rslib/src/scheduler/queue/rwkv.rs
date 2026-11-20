// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::main::MainQueueEntryKind;
use super::CardQueues;
use crate::card::CardQueue;
use crate::prelude::*;
use crate::scheduler::rwkv::rwkv_repeat_spacing_eligibility;
use crate::scheduler::rwkv::RwkvReviewScoreEligibility;

#[derive(Debug, Clone, Copy)]
pub(super) struct RwkvLearningRepeatGuards {
    pub min_intervening_reviews: u32,
    pub min_elapsed_secs: u32,
}

impl CardQueues {
    pub(super) fn rwkv_blocks_learning_card(&self, card_id: CardId) -> bool {
        self.shown_top_card != Some(card_id) && self.blocked_rwkv_learning_cards.contains(&card_id)
    }

    pub(super) fn blocked_rwkv_learning_count(&self) -> usize {
        self.intraday_learning
            .iter()
            .filter(|entry| {
                entry.due <= self.current_learn_ahead_cutoff()
                    && self.rwkv_blocks_learning_card(entry.id)
            })
            .count()
            + self
                .main
                .iter()
                .filter(|entry| self.rwkv_blocks_learning_card(entry.id))
                .count()
    }
}

impl Collection {
    /// Refresh selection guards without moving learning due times, rebuilding
    /// the review queue, or changing the queue snapshots used by undo/redo.
    pub(super) fn refresh_rwkv_learning_repeat_guards(&mut self, deck: &Deck) -> Result<()> {
        let queues = self.state.card_queues.as_ref().unwrap();
        let Some(guards) = queues.rwkv_learning_repeat_guards else {
            return Ok(());
        };
        if guards.min_intervening_reviews == 0 && guards.min_elapsed_secs == 0 {
            return Ok(());
        }
        let card_ids: Vec<_> = queues
            .intraday_learning
            .iter()
            .map(|entry| entry.id)
            .chain(
                queues
                    .main
                    .iter()
                    .filter(|entry| entry.kind == MainQueueEntryKind::InterdayLearning)
                    .map(|entry| entry.id),
            )
            .collect();
        if card_ids.is_empty() {
            self.state
                .card_queues
                .as_mut()
                .unwrap()
                .blocked_rwkv_learning_cards
                .clear();
            return Ok(());
        }

        let mut cards = self.all_cards_for_ids(&card_ids, false)?;
        self.populate_rwkv_last_review_times(&mut cards)?;
        let deck_ids = self.storage.deck_id_with_children(deck)?;
        let intervening_reviews = self
            .storage
            .recent_rwkv_intervening_reviews(&deck_ids, guards.min_intervening_reviews)?;
        let now = TimestampSecs::now();
        let blocked_cards = |min_intervening_reviews| {
            cards
                .iter()
                .filter(|card| {
                    matches!(card.queue, CardQueue::Learn | CardQueue::DayLearn)
                        && !matches!(
                            rwkv_repeat_spacing_eligibility(
                                card.last_review_time
                                    .map(|last| now.elapsed_secs_since_clamped(last)),
                                min_intervening_reviews,
                                guards.min_elapsed_secs,
                                intervening_reviews.get(&card.id).copied(),
                            ),
                            RwkvReviewScoreEligibility::Eligible
                        )
                })
                .map(|card| card.id)
                .collect()
        };
        let queues = self.state.card_queues.as_mut().unwrap();
        queues.blocked_rwkv_learning_cards = blocked_cards(guards.min_intervening_reviews);
        if !queues.blocked_rwkv_learning_cards.is_empty()
            && queues.iter().all(|entry| {
                queues.shown_top_card == Some(entry.card_id())
                    && queues
                        .blocked_rwkv_learning_cards
                        .contains(&entry.card_id())
            })
        {
            let blocked_count = queues.blocked_rwkv_learning_cards.len();
            queues.blocked_rwkv_learning_cards = blocked_cards(0);
            tracing::debug!(
                relaxed = blocked_count - queues.blocked_rwkv_learning_cards.len(),
                still_blocked = queues.blocked_rwkv_learning_cards.len(),
                "Relaxed RWKV learning intervening-review minimum because no other card was selectable"
            );
        }
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn repeat_spacing_requires_both_minimums_at_the_boundary() {
        for (seconds, reviews, eligible) in [
            (Some(89), Some(3), false),
            (Some(90), Some(2), false),
            (Some(90), Some(3), true),
            (None, None, true),
        ] {
            assert_eq!(
                matches!(
                    rwkv_repeat_spacing_eligibility(seconds, 3, 90, reviews),
                    RwkvReviewScoreEligibility::Eligible
                ),
                eligible,
                "elapsed seconds: {seconds:?}, other reviews: {reviews:?}"
            );
        }
    }
}
