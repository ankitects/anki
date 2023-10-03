// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::scheduler::ComputeMemoryStateResponse;
use fsrs::FSRSItem;
use fsrs::FSRS;
use itertools::Itertools;

use crate::card::CardType;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::scheduler::fsrs::weights::single_card_revlog_to_items;
use crate::scheduler::fsrs::weights::Weights;
use crate::scheduler::states::fuzz::with_review_fuzz;
use crate::search::JoinSearches;
use crate::search::Negated;
use crate::search::SearchNode;
use crate::search::StateKind;

#[derive(Debug, Clone, Copy, Default)]
pub struct ComputeMemoryProgress {
    pub current_cards: u32,
    pub total_cards: u32,
}

#[derive(Debug)]
pub(crate) struct UpdateMemoryStateRequest {
    pub weights: Weights,
    pub desired_retention: f32,
    pub max_interval: u32,
    pub reschedule: bool,
}

impl Collection {
    /// For each provided set of weights, locate cards with the provided search,
    /// and update their memory state.
    /// Should be called inside a transaction.
    /// If Weights are None, it means the user disabled FSRS, and the existing
    /// memory state should be removed.
    pub(crate) fn update_memory_state(
        &mut self,
        entries: Vec<(Option<UpdateMemoryStateRequest>, Vec<SearchNode>)>,
    ) -> Result<()> {
        let timing = self.timing_today()?;
        let usn = self.usn()?;
        for (req, search) in entries {
            let search = SearchBuilder::any(search.into_iter())
                .and(SearchNode::State(StateKind::New).negated());
            let revlog = self.revlog_for_srs(search)?;
            let reschedule = req.as_ref().map(|e| e.reschedule).unwrap_or_default();
            let last_reviews = if reschedule {
                Some(get_last_reviews(&revlog))
            } else {
                None
            };
            let items = fsrs_items_for_memory_state(revlog, timing.next_day_at);
            let desired_retention = req.as_ref().map(|w| w.desired_retention);
            let fsrs = FSRS::new(req.as_ref().map(|w| &w.weights[..]))?;
            let mut progress = self.new_progress_handler::<ComputeMemoryProgress>();
            progress.update(false, |s| s.total_cards = items.len() as u32)?;
            for (idx, (card_id, item)) in items.into_iter().enumerate() {
                progress.update(true, |state| state.current_cards = idx as u32 + 1)?;
                let mut card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
                let original = card.clone();
                if let Some(req) = &req {
                    card.set_memory_state(&fsrs, item.clone());
                    card.desired_retention = desired_retention;
                    // if rescheduling
                    if let Some(reviews) = &last_reviews {
                        // and we have a last review time for the card
                        if let Some(last_review) = reviews.get(&card.id) {
                            let days_elapsed =
                                timing.next_day_at.elapsed_days_since(*last_review) as i32;
                            // and the card's not new
                            if let Some(state) = &card.memory_state {
                                // or in (re)learning
                                if card.ctype == CardType::Review {
                                    // reschedule it
                                    let original_interval = card.interval;
                                    let interval = fsrs.next_interval(
                                        Some(state.stability),
                                        card.desired_retention.unwrap(),
                                        0,
                                    ) as f32;
                                    card.interval = with_review_fuzz(
                                        card.get_fuzz_factor(),
                                        interval,
                                        1,
                                        req.max_interval,
                                    );
                                    let due = if card.original_due != 0 {
                                        &mut card.original_due
                                    } else {
                                        &mut card.due
                                    };
                                    *due = (timing.days_elapsed as i32) - days_elapsed
                                        + card.interval as i32;
                                    self.log_manually_scheduled_review(
                                        &card,
                                        original_interval,
                                        usn,
                                    )?;
                                }
                            }
                        }
                    }
                } else {
                    card.memory_state = None;
                    card.desired_retention = None;
                }
                self.update_card_inner(&mut card, original, usn)?;
            }
        }
        Ok(())
    }

    pub fn compute_memory_state(&mut self, card_id: CardId) -> Result<ComputeMemoryStateResponse> {
        let mut card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
        let deck_id = card.original_deck_id.or(card.deck_id);
        let deck = self.get_deck(deck_id)?.or_not_found(card.deck_id)?;
        let conf_id = DeckConfigId(deck.normal()?.config_id);
        let config = self
            .storage
            .get_deck_config(conf_id)?
            .or_not_found(conf_id)?;
        let desired_retention = config.inner.desired_retention;
        let fsrs = FSRS::new(Some(&config.inner.fsrs_weights))?;
        let revlog = self.revlog_for_srs(SearchNode::CardIds(card.id.to_string()))?;
        let item = single_card_revlog_to_item(revlog, self.timing_today()?.next_day_at);
        card.set_memory_state(&fsrs, item);
        Ok(ComputeMemoryStateResponse {
            state: card.memory_state.map(Into::into),
            desired_retention,
        })
    }
}

impl Card {
    pub(crate) fn set_memory_state(&mut self, fsrs: &FSRS, item: Option<FSRSItem>) {
        self.memory_state = item
            .map(|i| fsrs.memory_state(i, None))
            .or_else(|| {
                if self.ctype == CardType::New {
                    None
                } else {
                    Some(fsrs.memory_state_from_sm2(self.ease_factor(), self.interval as f32))
                }
            })
            .map(Into::into);
    }
}

/// When updating memory state, FSRS only requires the last FSRSItem that
/// contains the full history.
pub(crate) fn fsrs_items_for_memory_state(
    revlogs: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
) -> Vec<(CardId, Option<FSRSItem>)> {
    revlogs
        .into_iter()
        .group_by(|r| r.cid)
        .into_iter()
        .map(|(card_id, group)| {
            (
                card_id,
                single_card_revlog_to_item(group.collect(), next_day_at),
            )
        })
        .collect()
}

/// Return a map of cards to the last time they were reviewed.
fn get_last_reviews(revlogs: &[RevlogEntry]) -> HashMap<CardId, TimestampSecs> {
    let mut out = HashMap::new();
    revlogs
        .iter()
        .group_by(|r| r.cid)
        .into_iter()
        .for_each(|(card_id, group)| {
            let mut last_ts = TimestampSecs::zero();
            for entry in group.into_iter().filter(|r| r.button_chosen >= 1) {
                last_ts = entry.id.as_secs();
            }
            if last_ts != TimestampSecs::zero() {
                out.insert(card_id, last_ts);
            }
        });
    out
}

/// When calculating memory state, only the last FSRSItem is required.
pub(crate) fn single_card_revlog_to_item(
    entries: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
) -> Option<FSRSItem> {
    let items = single_card_revlog_to_items(entries, next_day_at, false);
    items.and_then(|mut i| i.pop())
}

#[cfg(test)]
mod tests {
    use fsrs::MemoryState;

    use super::super::weights::tests::fsrs_items;
    use super::*;
    use crate::revlog::RevlogReviewKind;
    use crate::scheduler::fsrs::weights::tests::convert;
    use crate::scheduler::fsrs::weights::tests::review;
    use crate::scheduler::fsrs::weights::tests::revlog;

    #[test]
    fn bypassed_learning_is_handled() {
        // cards without any learning steps due to truncated history still have memory
        // state calculated
        assert_eq!(
            convert(
                &[
                    RevlogEntry {
                        ease_factor: 2500,
                        ..revlog(RevlogReviewKind::Manual, 7)
                    },
                    revlog(RevlogReviewKind::Review, 6),
                ],
                false,
            ),
            fsrs_items!([review(0)])
        );
    }

    #[test]
    fn zero_history_is_handled() {
        // when the history is empty, no items are produced
        assert_eq!(convert(&[], false), None);
        // but memory state should still be inferred, by using the card's current state
        let mut card = Card {
            ctype: CardType::Review,
            interval: 100,
            ease_factor: 1300,
            ..Default::default()
        };
        card.set_memory_state(&FSRS::new(Some(&[])).unwrap(), None);
        assert_eq!(
            card.memory_state,
            Some(
                MemoryState {
                    stability: 100.0,
                    difficulty: 9.692858
                }
                .into()
            )
        );
    }
}
