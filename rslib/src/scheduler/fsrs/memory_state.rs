// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use anki_proto::scheduler::ComputeMemoryStateResponse;
use fsrs::FSRSItem;
use fsrs::MemoryState;
use fsrs::FSRS;
use fsrs::FSRS5_DEFAULT_DECAY;
use fsrs::FSRS6_DEFAULT_DECAY;
use itertools::Itertools;

use super::params::ignore_revlogs_before_ms_from_config;
use super::rescheduler::Rescheduler;
use crate::card::CardType;
use crate::prelude::*;
use crate::revlog::RevlogEntry;
use crate::scheduler::answering::get_fuzz_seed;
use crate::scheduler::fsrs::params::reviews_for_fsrs;
use crate::scheduler::fsrs::params::Params;
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
    pub params: Params,
    pub desired_retention: f32,
    pub historical_retention: f32,
    pub max_interval: u32,
    pub reschedule: bool,
}

pub(crate) struct UpdateMemoryStateEntry {
    pub req: Option<UpdateMemoryStateRequest>,
    pub search: SearchNode,
    pub ignore_before: TimestampMillis,
}

impl Collection {
    /// For each provided set of params, locate cards with the provided search,
    /// and update their memory state.
    /// Should be called inside a transaction.
    /// If Params are None, it means the user disabled FSRS, and the existing
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
            let mut rescheduler = self
                .get_config_bool(BoolKey::LoadBalancerEnabled)
                .then(|| Rescheduler::new(self))
                .transpose()?;
            let fsrs = FSRS::new(req.as_ref().map(|w| &w.params[..]).or(Some([].as_slice())))?;
            let decay = req.as_ref().map(|w| {
                if w.params.is_empty() {
                    FSRS6_DEFAULT_DECAY // default decay for FSRS-6
                } else if w.params.len() < 21 {
                    FSRS5_DEFAULT_DECAY // default decay for FSRS-4.5 and FSRS-5
                } else {
                    w.params[20]
                }
            });
            let historical_retention = req.as_ref().map(|w| w.historical_retention);
            let items = fsrs_items_for_memory_states(
                &fsrs,
                revlog,
                timing.next_day_at,
                historical_retention.unwrap_or(0.9),
                ignore_before,
            )?;
            let desired_retention = req.as_ref().map(|w| w.desired_retention);
            let mut progress = self.new_progress_handler::<ComputeMemoryProgress>();
            progress.update(false, |s| s.total_cards = items.len() as u32)?;
            for (idx, (card_id, item)) in items.into_iter().enumerate() {
                progress.update(true, |state| state.current_cards = idx as u32 + 1)?;
                let mut card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
                let original = card.clone();
                if let (Some(req), Some(item)) = (&req, item) {
                    card.set_memory_state(&fsrs, Some(item), historical_retention.unwrap())?;
                    card.desired_retention = desired_retention;
                    card.decay = decay;
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
                                        let deck = self
                                            .get_deck(card.original_or_current_deck_id())?
                                            .or_not_found(card.original_or_current_deck_id())?;
                                        let deckconfig_id = deck.config_id().unwrap();
                                        // reschedule it
                                        let original_interval = card.interval;
                                        let interval = fsrs.next_interval(
                                            Some(state.stability),
                                            card.desired_retention.unwrap(),
                                            0,
                                        );
                                        card.interval = rescheduler
                                            .as_mut()
                                            .and_then(|r| {
                                                r.find_interval(
                                                    interval,
                                                    1,
                                                    req.max_interval,
                                                    days_elapsed as u32,
                                                    deckconfig_id,
                                                    get_fuzz_seed(&card, true),
                                                )
                                            })
                                            .unwrap_or_else(|| {
                                                with_review_fuzz(
                                                    card.get_fuzz_factor(true),
                                                    interval,
                                                    1,
                                                    req.max_interval,
                                                )
                                            });
                                        let due = if card.original_due != 0 {
                                            &mut card.original_due
                                        } else {
                                            &mut card.due
                                        };
                                        let new_due = (timing.days_elapsed as i32) - days_elapsed
                                            + card.interval as i32;
                                        if let Some(rescheduler) = &mut rescheduler {
                                            rescheduler.update_due_cnt_per_day(
                                                *due,
                                                new_due,
                                                deckconfig_id,
                                            );
                                        }
                                        *due = new_due;
                                        // Add a rescheduled revlog entry
                                        self.log_rescheduled_review(&card, original_interval, usn)?;
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
        let historical_retention = config.inner.historical_retention;
        let fsrs = FSRS::new(Some(config.fsrs_params()))?;
        let revlog = self.revlog_for_srs(SearchNode::CardIds(card.id.to_string()))?;
        let item = fsrs_item_for_memory_state(
            &fsrs,
            revlog,
            self.timing_today()?.next_day_at,
            historical_retention,
            ignore_revlogs_before_ms_from_config(&config)?,
        )?;
        if item.is_some() {
            card.set_memory_state(&fsrs, item, historical_retention)?;
            Ok(ComputeMemoryStateResponse {
                state: card.memory_state.map(Into::into),
                desired_retention,
            })
        } else {
            card.memory_state = None;
            card.desired_retention = None;
            Ok(ComputeMemoryStateResponse {
                state: None,
                desired_retention,
            })
        }
    }
}

impl Card {
    pub(crate) fn set_memory_state(
        &mut self,
        fsrs: &FSRS,
        item: Option<FsrsItemForMemoryState>,
        historical_retention: f32,
    ) -> Result<()> {
        let memory_state = if let Some(i) = item {
            Some(fsrs.memory_state(i.item, i.starting_state)?)
        } else if self.ctype == CardType::New || self.interval == 0 {
            None
        } else {
            // no valid revlog entries; infer state from current card state
            Some(fsrs.memory_state_from_sm2(
                self.ease_factor(),
                self.interval as f32,
                historical_retention,
            )?)
        };
        self.memory_state = memory_state.map(Into::into);
        Ok(())
    }
}

#[derive(Debug)]
pub(crate) struct FsrsItemForMemoryState {
    pub item: FSRSItem,
    /// When revlogs have been truncated, this stores the initial state at first
    /// review
    pub starting_state: Option<MemoryState>,
    pub filtered_revlogs: Vec<RevlogEntry>,
}

/// Like [fsrs_item_for_memory_state], but for updating multiple cards at once.
pub(crate) fn fsrs_items_for_memory_states(
    fsrs: &FSRS,
    revlogs: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    historical_retention: f32,
    ignore_revlogs_before: TimestampMillis,
) -> Result<Vec<(CardId, Option<FsrsItemForMemoryState>)>> {
    revlogs
        .into_iter()
        .chunk_by(|r| r.cid)
        .into_iter()
        .map(|(card_id, group)| {
            Ok((
                card_id,
                fsrs_item_for_memory_state(
                    fsrs,
                    group.collect(),
                    next_day_at,
                    historical_retention,
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
}

/// Return a map of cards to info about last review/reschedule.
fn get_last_revlog_info(revlogs: &[RevlogEntry]) -> HashMap<CardId, LastRevlogInfo> {
    let mut out = HashMap::new();
    revlogs
        .iter()
        .chunk_by(|r| r.cid)
        .into_iter()
        .for_each(|(card_id, group)| {
            let mut last_reviewed_at = None;
            for e in group.into_iter() {
                if e.button_chosen >= 1 {
                    last_reviewed_at = Some(e.id.as_secs());
                }
            }
            out.insert(card_id, LastRevlogInfo { last_reviewed_at });
        });
    out
}

/// When calculating memory state, only the last FSRSItem is required. If the
/// revlog is non-empty and no learning steps have been detected (indicative of
/// a truncated revlog), we return the starting state inferred from the first
/// revlog entry, so that the first review is not treated as if started from
/// scratch.
pub(crate) fn fsrs_item_for_memory_state(
    fsrs: &FSRS,
    entries: Vec<RevlogEntry>,
    next_day_at: TimestampSecs,
    historical_retention: f32,
    ignore_revlogs_before: TimestampMillis,
) -> Result<Option<FsrsItemForMemoryState>> {
    struct FirstReview {
        interval: f32,
        ease_factor: f32,
    }
    if let Some(mut output) = reviews_for_fsrs(entries, next_day_at, false, ignore_revlogs_before) {
        let mut item = output.fsrs_items.pop().unwrap().1;
        if output.revlogs_complete {
            Ok(Some(FsrsItemForMemoryState {
                item,
                starting_state: None,
                filtered_revlogs: output.filtered_revlogs,
            }))
        } else if let Some(first_user_grade) = output.filtered_revlogs.first() {
            // the revlog has been truncated, but not fully
            let first_review = FirstReview {
                interval: first_user_grade.interval.max(1) as f32,
                ease_factor: if first_user_grade.ease_factor == 0 {
                    2500
                } else {
                    first_user_grade.ease_factor
                } as f32
                    / 1000.0,
            };
            let mut starting_state = fsrs.memory_state_from_sm2(
                first_review.ease_factor,
                first_review.interval,
                historical_retention,
            )?;
            // if the ease factor is less than 1.1, the revlog entry is generated by FSRS
            if first_review.ease_factor <= 1.1 {
                starting_state.difficulty = (first_review.ease_factor - 0.1) * 9.0 + 1.0;
            }
            // remove the first review because it has been converted to the starting state
            item.reviews.remove(0);
            Ok(Some(FsrsItemForMemoryState {
                item,
                starting_state: Some(starting_state),
                filtered_revlogs: output.filtered_revlogs,
            }))
        } else {
            // only manual and rescheduled revlogs; treat like empty
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
    use crate::scheduler::fsrs::params::tests::convert;
    use crate::scheduler::fsrs::params::tests::revlog;

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
        let item = fsrs_item_for_memory_state(
            &fsrs,
            vec![
                RevlogEntry {
                    ease_factor: 2500,
                    interval: 100,
                    ..revlog(RevlogReviewKind::Review, 99)
                },
                revlog(RevlogReviewKind::Review, 0),
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
                difficulty: 5.6932373,
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
                stability: 248.64305,
                difficulty: 5.7909784,
            }),
        );
        // cards with a single review-type entry also get memory states from revlog
        // rather than card states
        let item = fsrs_item_for_memory_state(
            &fsrs,
            vec![RevlogEntry {
                ease_factor: 2500,
                interval: 100,
                ..revlog(RevlogReviewKind::Review, 100)
            }],
            TimestampSecs::now(),
            0.9,
            0.into(),
        )?
        .unwrap();
        assert!(item.item.reviews.is_empty());
        card.set_memory_state(&fsrs, Some(item), 0.9)?;
        assert_int_eq(
            card.memory_state,
            Some(FsrsMemoryState {
                stability: 99.999954,
                difficulty: 5.840841,
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
                    difficulty: 9.979899,
                }
                .into(),
            ),
        );
        Ok(())
    }
}
