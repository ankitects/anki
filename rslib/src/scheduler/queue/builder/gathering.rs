// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::DueCard;
use super::DueCardWithState;
use super::NewCard;
use super::QueueBuilder;
use crate::card::CardQueue;
use crate::deckconfig::NewCardGatherPriority;
use crate::decks::limits::LimitKind;
use crate::prelude::*;
use crate::scheduler::fsrs::metrics::ExactFsrsOrder;
use crate::scheduler::fsrs::metrics::ExactOrderKey;
use crate::scheduler::fsrs::metrics::FsrsMetricContext;
use crate::scheduler::queue::DueCardKind;
use crate::scheduler::timing::SchedTimingToday;
use crate::storage::card::NewCardSorting;

#[derive(Debug, Clone, Copy)]
struct DueCardForRetrievabilitySort {
    card: DueCard,
    counts_towards_review_limit: bool,
    interday_or_review: bool,
}

impl QueueBuilder {
    pub(super) fn gather_cards(&mut self, col: &mut Collection) -> Result<()> {
        if let Some(order) = self.context.exact_fsrs_order() {
            self.gather_due_non_new_cards_with_exact_retrievability(col, order)?;
            self.gather_new_cards(col)?;
            return Ok(());
        }

        self.gather_intraday_learning_cards(col)?;
        self.gather_due_cards(col, DueCardKind::Learning)?;
        self.gather_due_cards(col, DueCardKind::Review)?;
        self.gather_new_cards(col)?;

        Ok(())
    }

    fn gather_due_non_new_cards_with_exact_retrievability(
        &mut self,
        col: &mut Collection,
        order: ExactFsrsOrder,
    ) -> Result<()> {
        self.gather_future_learning_cards_for_retrievability_sort(col)?;
        let cards = col
            .storage
            .due_cards_with_state_in_active_decks(self.context.timing)?;

        let mut with_keys = {
            let mut metrics =
                FsrsMetricContext::new(&self.context.deck_map, &self.context.config_map);
            let mut with_keys = Vec::with_capacity(cards.len());
            for card in cards {
                let interday_or_review =
                    matches!(card.queue, CardQueue::Review | CardQueue::DayLearn);
                let candidate = DueCardForRetrievabilitySort {
                    card: card.card,
                    counts_towards_review_limit: interday_or_review,
                    interday_or_review,
                };
                let memory = card.memory_state.map(|state| {
                    let elapsed = elapsed_seconds_since_last_review(&card, self.context.timing);
                    (state, elapsed as f32 / 86_400.0)
                });
                let key = metrics.exact_order_key(
                    order,
                    card.card.original_deck_id.or(card.card.current_deck_id),
                    memory,
                    card.original_or_current_due(),
                    card.interval,
                    self.context.timing,
                )?;
                with_keys.push((
                    candidate,
                    ExactOrderKey::new(key, candidate.card.id, candidate.card.mtime),
                ));
            }
            with_keys
        };
        with_keys.sort_unstable_by(|(_, key_a), (_, key_b)| order.compare(key_a, key_b));

        for (candidate, _) in with_keys {
            if candidate.counts_towards_review_limit
                && (self.limits.root_limit_reached(LimitKind::Review)
                    || self
                        .limits
                        .limit_reached(candidate.card.current_deck_id, LimitKind::Review)?)
            {
                continue;
            }
            if self
                .add_due_card_for_retrievability_sort(candidate.card, candidate.interday_or_review)
            {
                self.retrievability_sorted_non_new.push(candidate.card);
                if candidate.counts_towards_review_limit {
                    self.limits.decrement_deck_and_parent_limits(
                        candidate.card.current_deck_id,
                        LimitKind::Review,
                    )?;
                }
            }
        }
        Ok(())
    }

    fn gather_intraday_learning_cards(&mut self, col: &mut Collection) -> Result<()> {
        col.storage.for_each_intraday_card_in_active_decks(
            self.context.timing.next_day_at,
            |card| {
                self.get_and_update_bury_mode_for_note(card.into());
                self.learning.push(card);
            },
        )?;

        Ok(())
    }

    fn gather_future_learning_cards_for_retrievability_sort(
        &mut self,
        col: &mut Collection,
    ) -> Result<()> {
        col.storage.for_each_intraday_card_in_active_decks(
            self.context.timing.next_day_at,
            |card| {
                if card.due > self.context.timing.now.0 as i32 {
                    self.get_and_update_bury_mode_for_note(card.into());
                    self.learning.push(card);
                }
            },
        )?;
        Ok(())
    }

    fn gather_due_cards(&mut self, col: &mut Collection, kind: DueCardKind) -> Result<()> {
        if self.limits.root_limit_reached(LimitKind::Review) {
            return Ok(());
        }
        col.storage.for_each_due_card_in_active_decks(
            self.context.timing,
            self.context.sort_options.review_order,
            kind,
            self.context.fsrs,
            |card| {
                if self.limits.root_limit_reached(LimitKind::Review) {
                    return Ok(false);
                }
                if !self
                    .limits
                    .limit_reached(card.current_deck_id, LimitKind::Review)?
                    && self.add_due_card(card)
                {
                    self.limits.decrement_deck_and_parent_limits(
                        card.current_deck_id,
                        LimitKind::Review,
                    )?;
                }
                Ok(true)
            },
        )
    }

    fn gather_new_cards(&mut self, col: &mut Collection) -> Result<()> {
        let salt = Self::knuth_salt(self.context.timing.days_elapsed);
        match self.context.sort_options.new_gather_priority {
            NewCardGatherPriority::Deck => {
                self.gather_new_cards_by_deck(col, NewCardSorting::LowestPosition)
            }
            NewCardGatherPriority::DeckThenRandomNotes => {
                self.gather_new_cards_by_deck(col, NewCardSorting::RandomNotes(salt))
            }
            NewCardGatherPriority::LowestPosition => {
                self.gather_new_cards_sorted(col, NewCardSorting::LowestPosition)
            }
            NewCardGatherPriority::HighestPosition => {
                self.gather_new_cards_sorted(col, NewCardSorting::HighestPosition)
            }
            NewCardGatherPriority::RandomNotes => {
                self.gather_new_cards_sorted(col, NewCardSorting::RandomNotes(salt))
            }
            NewCardGatherPriority::RandomCards => {
                self.gather_new_cards_sorted(col, NewCardSorting::RandomCards(salt))
            }
        }
    }

    fn gather_new_cards_by_deck(
        &mut self,
        col: &mut Collection,
        sort: NewCardSorting,
    ) -> Result<()> {
        for deck_id in col.storage.get_active_deck_ids_sorted()? {
            if self.limits.root_limit_reached(LimitKind::New) {
                break;
            }
            if self.limits.limit_reached(deck_id, LimitKind::New)? {
                continue;
            }
            col.storage
                .for_each_new_card_in_deck(deck_id, sort, |card| {
                    let limit_reached = self.limits.limit_reached(deck_id, LimitKind::New)?;
                    if !limit_reached && self.add_new_card(card) {
                        self.limits
                            .decrement_deck_and_parent_limits(deck_id, LimitKind::New)?;
                    }
                    Ok(!limit_reached)
                })?;
        }

        Ok(())
    }

    fn gather_new_cards_sorted(
        &mut self,
        col: &mut Collection,
        order: NewCardSorting,
    ) -> Result<()> {
        col.storage
            .for_each_new_card_in_active_decks(order, |card| {
                if self.limits.root_limit_reached(LimitKind::New) {
                    return Ok(false);
                }
                if !self
                    .limits
                    .limit_reached(card.current_deck_id, LimitKind::New)?
                    && self.add_new_card(card)
                {
                    self.limits
                        .decrement_deck_and_parent_limits(card.current_deck_id, LimitKind::New)?;
                }
                Ok(true)
            })
    }

    /// True if limit should be decremented.
    fn add_due_card(&mut self, card: DueCard) -> bool {
        let added = self.add_due_card_for_retrievability_sort(card, true);
        if added {
            match card.kind {
                DueCardKind::Review => self.review.push(card),
                DueCardKind::Learning => self.day_learning.push(card),
            }
        }
        added
    }

    fn add_due_card_for_retrievability_sort(
        &mut self,
        card: DueCard,
        interday_or_review: bool,
    ) -> bool {
        let bury_this_card = self
            .get_and_update_bury_mode_for_note(card.into())
            .map(|mode| match card.kind {
                DueCardKind::Review => mode.bury_reviews,
                DueCardKind::Learning if interday_or_review => mode.bury_interday_learning,
                DueCardKind::Learning => false,
            })
            .unwrap_or_default();
        !bury_this_card
    }

    // True if limit should be decremented.
    fn add_new_card(&mut self, card: NewCard) -> bool {
        let bury_this_card = self
            .get_and_update_bury_mode_for_note(card.into())
            .map(|mode| mode.bury_new)
            .unwrap_or_default();
        // no previous siblings seen?
        if bury_this_card {
            false
        } else {
            self.new.push(card);
            true
        }
    }

    // Generates a salt for use with fnvhash. Useful to increase randomness
    // when the base salt is a small integer.
    fn knuth_salt(base_salt: u32) -> u32 {
        base_salt.wrapping_mul(2654435761)
    }
}

/// Unlike [Card::seconds_since_last_review], a card without a stored review
/// time is measured like upstream's SQL queue ordering
/// (extract_fsrs_relative_retrievability): whole days since its review day,
/// or seconds since its due time for intraday learning, never negative.
fn elapsed_seconds_since_last_review(card: &DueCardWithState, timing: SchedTimingToday) -> u32 {
    if let Some(last_review) = card.last_review_time {
        timing.now.elapsed_secs_since(last_review).max(0) as u32
    } else {
        // Preserve the legacy due/interval estimate when no timestamp was stored.
        let due = card.original_or_current_due() as i64;
        if due > 365_000 {
            timing.now.elapsed_secs_since(TimestampSecs(due)).max(0) as u32
        } else {
            ((timing.days_elapsed as i64 - due + card.interval as i64).max(0) * 86_400) as u32
        }
    }
}
