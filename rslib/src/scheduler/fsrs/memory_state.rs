// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::scheduler::ComputeMemoryStateResponse;
use fsrs::FSRSItem;
use fsrs::MemoryState;
use fsrs::FSRS;
use itertools::Itertools;

use super::weights::ignore_revlogs_before_ms_from_config;
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

pub(crate) struct UpdateMemoryStateEntry {
    pub req: Option<UpdateMemoryStateRequest>,
    pub search: SearchNode,
    pub ignore_before: TimestampMillis,
}

impl Collection {
    /// For each provided set of weights, locate cards with the provided search,
    /// and update their memory state.
    /// Should be called inside a transaction.
    /// If Weights are None, it means the user disabled FSRS, and the existing
    /// memory state should be removed.
    pub(crate) fn update_memory_state(
        &mut self,
        entries: Vec<UpdateMemoryStateEntry>,
    ) -> Result<()> {
        let timing = self.timing_today()?;
        let usn = self.usn()?;
        for UpdateMemoryStateEntry {
            req,
            search,
            ignore_before,
        } in entries
        {
            let search =
                SearchBuilder::all([search.into(), SearchNode::State(StateKind::New).negated()]);
            let revlog = self.revlog_for_srs(search)?;
            let reschedule = req.as_ref().map(|e| e.reschedule).unwrap_or_default();
            let last_revlog_info = if reschedule {
                Some(get_last_revlog_info(&revlog))
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
                ignore_before,
            )?;
            let desired_retention = req.as_ref().map(|w| w.desired_retention);
            let mut progress = self.new_progress_handler::<ComputeMemoryProgress>();
            progress.update(false, |s| s.total_cards = items.len() as u32)?;
            for (idx, (card_id, item)) in items.into_iter().enumerate() {
                progress.update(true, |state| state.current_cards = idx as u32 + 1)?;
                let mut card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
                let original = card.clone();
                if let Some(req) = &req {
                    card.set_memory_state(&fsrs, item, sm2_retention.unwrap())?;
                    card.desired_retention = desired_retention;
                    // if rescheduling
                    if let Some(reviews) = &last_revlog_info {
                        // and we have a last review time for the card
                        if let Some(last_info) = reviews.get(&card.id) {
                            if let Some(last_review) = &last_info.last_reviewed_at {
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
                                        )
                                            as f32;
                                        card.interval = with_review_fuzz(
                                            card.get_fuzz_factor(true),
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
                                        // Add a manual revlog entry if the last entry wasn't manual
                                        if !last_info.last_revlog_is_manual {
                                            self.log_manually_scheduled_review(
                                                &card,
                                                original_interval,
                                                usn,
                                            )?;
                                        }
                                    }
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
            ignore_revlogs_before_ms_from_config(&config)?,
        )?;
        card.set_memory_state(&fsrs, item, sm2_retention)?;
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
    ) -> Result<()> {
        let memory_state = if let Some(i) = item {
            Some(fsrs.memory_state(i.item, i.starting_state)?)
        } else if self.ctype == CardType::New || self.interval == 0 || self.reps == 0 {
            None
        } else {
            // no valid revlog entries; infer state from current card state
            Some(fsrs.memory_state_from_sm2(
                self.ease_factor(),
                self.interval as f32,
                sm2_retention,
            )?)
        };
        self.memory_state = memory_state.map(Into::into);
        Ok(())
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
    ignore_revlogs_before: TimestampMillis,
) -> Result<Vec<(CardId, Option<FsrsItemWithStartingState>)>> {
    revlogs
        .into_iter()
        .group_by(|r| r.cid)
        .into_iter()
        .map(|(card_id, group)| {
            Ok((
                card_id,
                single_card_revlog_to_item(
                    fsrs,
                    group.collect(),
                    next_day_at,
                    sm2_retention,
                    ignore_revlogs_before,
                )?,
            ))
        })
        .collect()
}

struct LastRevlogInfo {
    /// Used to determine the actual elapsed time between the last time the user
    /// reviewed the card and now, so that we can determine an accurate period
    /// when the card has subsequently been rescheduled to a different day.
    last_reviewed_at: Option<TimestampSecs>,
    /// If true, the last action on this card was a reschedule, so we
    /// can avoid writing an extra revlog entry on another reschedule.
    last_revlog_is_manual: bool,
}

/// Return a map of cards to info about last review/reschedule.
fn get_last_revlog_info(revlogs: &[RevlogEntry]) -> HashMap<CardId, LastRevlogInfo> {
    let mut out = HashMap::new();
    revlogs
        .iter()
        .group_by(|r| r.cid)
        .into_iter()
        .for_each(|(card_id, group)| {
            let mut last_reviewed_at = None;
            let mut last_revlog_is_manual = false;
            for e in group.into_iter() {
                if e.button_chosen >= 1 {
                    last_reviewed_at = Some(e.id.as_secs());
                }
                last_revlog_is_manual = e.review_kind == RevlogReviewKind::Manual;
            }
            out.insert(
                card_id,
                LastRevlogInfo {
                    last_reviewed_at,
                    last_revlog_is_manual,
                },
            );
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
    ignore_revlogs_before: TimestampMillis,
) -> Result<Option<FsrsItemWithStartingState>> {
    struct FirstReview {
        interval: f32,
        ease_factor: f32,
    }
    let first_review = entries
        .iter()
        .find(|e| e.button_chosen > 0)
        .map(|e| FirstReview {
            interval: e.interval.max(1) as f32,
            ease_factor: if e.ease_factor == 0 {
                2500
            } else {
                e.ease_factor
            } as f32
                / 1000.0,
        });
    if let Some((mut items, revlogs_complete)) =
        single_card_revlog_to_items(entries, next_day_at, false, ignore_revlogs_before)
    {
        let mut item = items.pop().unwrap();
        if revlogs_complete {
            Ok(Some(FsrsItemWithStartingState {
                item,
                starting_state: None,
            }))
        } else if let Some(first_review) = first_review {
            // the revlog has been truncated, but not fully
            let starting_state = fsrs.memory_state_from_sm2(
                first_review.ease_factor,
                first_review.interval,
                sm2_retention,
            )?;
            item.reviews.remove(0);
            if item.reviews.is_empty() {
                Ok(None)
            } else {
                Ok(Some(FsrsItemWithStartingState {
                    item,
                    starting_state: Some(starting_state),
                }))
            }
        } else {
            // only manual rescheduling; treat like empty
            Ok(None)
        }
    } else {
        Ok(None)
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

    /// Floating point precision can vary between platforms, and each FSRS
    /// update tends to result in small changes to these numbers, so we
    /// round them.
    fn assert_int_eq(actual: Option<FsrsMemoryState>, expected: Option<FsrsMemoryState>) {
        let actual = actual.unwrap();
        let expected = expected.unwrap();
        assert_eq!(actual.stability.round(), expected.stability.round());
        assert_eq!(actual.difficulty.round(), expected.difficulty.round());
    }

    #[test]
    fn bypassed_learning_is_handled() -> Result<()> {
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
            0.into(),
        )?
        .unwrap();
        assert_int_eq(
            item.starting_state.map(Into::into),
            Some(FsrsMemoryState {
                stability: 99.999954,
                difficulty: 5.8158145,
            }),
        );
        let mut card = Card {
            reps: 1,
            ..Default::default()
        };
        card.set_memory_state(&fsrs, Some(item), 0.9)?;
        assert_int_eq(
            card.memory_state,
            Some(FsrsMemoryState {
                stability: 248.65057,
                difficulty: 5.782729,
            }),
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
            0.into(),
        )?;
        assert!(item.is_none());
        card.interval = 123;
        card.ease_factor = 2000;
        card.ctype = CardType::Review;
        card.set_memory_state(&fsrs, item, 0.9)?;
        assert_int_eq(
            card.memory_state,
            Some(FsrsMemoryState {
                stability: 122.99994,
                difficulty: 7.4459267,
            }),
        );
        Ok(())
    }

    #[test]
    fn zero_history_is_handled() -> Result<()> {
        // when the history is empty, no items are produced
        assert_eq!(convert(&[], false), None);
        // but memory state should still be inferred, by using the card's current state
        let mut card = Card {
            ctype: CardType::Review,
            interval: 100,
            ease_factor: 1300,
            reps: 1,
            ..Default::default()
        };
        card.set_memory_state(&FSRS::new(Some(&[])).unwrap(), None, 0.9)?;
        assert_int_eq(
            card.memory_state,
            Some(
                MemoryState {
                    stability: 99.999954,
                    difficulty: 9.963163,
                }
                .into(),
            ),
        );
        Ok(())
    }
}
