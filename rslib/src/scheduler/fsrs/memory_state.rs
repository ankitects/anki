// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::scheduler::ComputeMemoryStateResponse;
use fsrs::FSRSItem;
use fsrs::MemoryState;
use fsrs::FSRS;
use itertools::Itertools;

use crate::card::CardType;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::revlog::RevlogReviewKind;
use crate::scheduler::fsrs::weights::single_card_revlog_to_items;
use crate::scheduler::fsrs::weights::Weights;
use crate::scheduler::states::fuzz::with_review_fuzz;
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
    pub sm2_retention: f32,
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
        entries: Vec<(Option<UpdateMemoryStateRequest>, SearchNode)>,
    ) -> Result<()> {
        let timing = self.timing_today()?;
        let usn = self.usn()?;
        for (req, search) in entries {
            let search =
                SearchBuilder::all([search.into(), SearchNode::State(StateKind::New).negated()]);
            let revlog = self.revlog_for_srs(search)?;
            let reschedule = req.as_ref().map(|e| e.reschedule).unwrap_or_default();
            let last_reviews = if reschedule {
                Some(get_last_reviews(&revlog))
            } else {
                None
            };
            let fsrs = FSRS::new(req.as_ref().map(|w| &w.weights[..]).or(Some([].as_slice())))?;
            let sm2_retention = req.as_ref().map(|w| w.sm2_retention);
            let items = fsrs_items_for_memory_state(
                &fsrs,
                revlog,
                timing.next_day_at,
                sm2_retention.unwrap_or(0.9),
            );
            let desired_retention = req.as_ref().map(|w| w.desired_retention);
            let mut progress = self.new_progress_handler::<ComputeMemoryProgress>();
            progress.update(false, |s| s.total_cards = items.len() as u32)?;
            for (idx, (card_id, item)) in items.into_iter().enumerate() {
                progress.update(true, |state| state.current_cards = idx as u32 + 1)?;
                let mut card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
                let original = card.clone();
                if let Some(req) = &req {
                    card.set_memory_state(&fsrs, item, sm2_retention.unwrap());
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
        let sm2_retention = config.inner.sm2_retention;
        let fsrs = FSRS::new(Some(&config.inner.fsrs_weights))?;
        let revlog = self.revlog_for_srs(SearchNode::CardIds(card.id.to_string()))?;
        let item = single_card_revlog_to_item(
            &fsrs,
            revlog,
            self.timing_today()?.next_day_at,
            sm2_retention,
        );
        card.set_memory_state(&fsrs, item, sm2_retention);
        Ok(ComputeMemoryStateResponse {
            state: card.memory_state.map(Into::into),
            desired_retention,
        })
    }
}

impl Card {
    pub(crate) fn set_memory_state(
        &mut self,
        fsrs: &FSRS,
        item: Option<FsrsItemWithStartingState>,
        sm2_retention: f32,
    ) {
        self.memory_state = item
            .map(|i| fsrs.memory_state(i.item, i.starting_state))
            .or_else(|| {
                if self.ctype == CardType::New {
                    None
                } else {
                    // no valid revlog entries; infer state from current card state
                    Some(fsrs.memory_state_from_sm2(
                        self.ease_factor(),
                        self.interval as f32,
                        sm2_retention,
                    ))
                }
            })
            .map(Into::into);
    }
}

#[derive(Debug)]
pub(crate) struct FsrsItemWithStartingState {
    pub item: FSRSItem,
    /// When revlogs have been truncated, this stores the initial state at first
    /// review
    pub starting_state: Option<MemoryState>,
}

/// When updating memory state, FSRS only requires the last FSRSItem that
/// contains the full history.
pub(crate) fn fsrs_items_for_memory_state(
    fsrs: &FSRS,
    revlogs: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    sm2_retention: f32,
) -> Vec<(CardId, Option<FsrsItemWithStartingState>)> {
    revlogs
        .into_iter()
        .group_by(|r| r.cid)
        .into_iter()
        .map(|(card_id, group)| {
            (
                card_id,
                single_card_revlog_to_item(fsrs, group.collect(), next_day_at, sm2_retention),
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

/// When calculating memory state, only the last FSRSItem is required. If the
/// revlog is non-empty and no learning steps have been detected (indicative of
/// a truncated revlog), we return the starting state inferred from the first
/// revlog entry, so that the first review is not treated as if started from
/// scratch.
pub(crate) fn single_card_revlog_to_item(
    fsrs: &FSRS,
    entries: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    sm2_retention: f32,
) -> Option<FsrsItemWithStartingState> {
    let have_learning = entries
        .iter()
        .any(|e| e.review_kind == RevlogReviewKind::Learning);
    if have_learning {
        let items = single_card_revlog_to_items(entries, next_day_at, false);
        Some(FsrsItemWithStartingState {
            item: items.unwrap().pop().unwrap(),
            starting_state: None,
        })
    } else if let Some(first_review) = entries.iter().find(|e| e.button_chosen > 0) {
        let ease_factor = if first_review.ease_factor == 0 {
            2500
        } else {
            first_review.ease_factor
        };
        let interval = first_review.interval.max(1);
        let starting_state =
            fsrs.memory_state_from_sm2(ease_factor as f32 / 1000.0, interval as f32, sm2_retention);
        let items = single_card_revlog_to_items(entries, next_day_at, false);
        items.and_then(|mut items| {
            let mut item = items.pop().unwrap();
            item.reviews.remove(0);
            if item.reviews.is_empty() {
                None
            } else {
                Some(FsrsItemWithStartingState {
                    item,
                    starting_state: Some(starting_state),
                })
            }
        })
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use fsrs::MemoryState;

    use super::*;
    use crate::card::FsrsMemoryState;
    use crate::revlog::RevlogReviewKind;
    use crate::scheduler::fsrs::weights::tests::convert;
    use crate::scheduler::fsrs::weights::tests::revlog;

    #[test]
    fn bypassed_learning_is_handled() {
        // cards without any learning steps due to truncated history still have memory
        // state calculated
        let fsrs = FSRS::new(Some(&[])).unwrap();
        let item = single_card_revlog_to_item(
            &fsrs,
            vec![
                RevlogEntry {
                    ease_factor: 2500,
                    interval: 100,
                    ..revlog(RevlogReviewKind::Review, 100)
                },
                revlog(RevlogReviewKind::Review, 1),
            ],
            TimestampSecs::now(),
            0.9,
        )
        .unwrap();
        assert_eq!(
            item.starting_state,
            Some(MemoryState {
                stability: 99.999954,
                difficulty: 4.4642887
            })
        );
        let mut card = Card::default();
        card.set_memory_state(&fsrs, Some(item), 0.9);
        assert_eq!(
            card.memory_state,
            Some(FsrsMemoryState {
                stability: 248.47868,
                difficulty: 4.468946
            })
        );
        // but if there's only a single revlog entry, we'll fall back on current card
        // state
        let item = single_card_revlog_to_item(
            &fsrs,
            vec![RevlogEntry {
                ease_factor: 2500,
                interval: 100,
                ..revlog(RevlogReviewKind::Review, 100)
            }],
            TimestampSecs::now(),
            0.9,
        );
        assert!(item.is_none());
        card.interval = 123;
        card.ease_factor = 2000;
        card.ctype = CardType::Review;
        card.set_memory_state(&fsrs, item, 0.9);
        assert_eq!(
            card.memory_state,
            Some(FsrsMemoryState {
                stability: 122.99994,
                difficulty: 6.5147324,
            })
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
        card.set_memory_state(&FSRS::new(Some(&[])).unwrap(), None, 0.9);
        assert_eq!(
            card.memory_state,
            Some(
                MemoryState {
                    stability: 99.999954,
                    difficulty: 9.692858
                }
                .into()
            )
        );
    }
}
