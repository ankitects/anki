// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod context;
mod gathering;
pub(crate) mod intersperser;
pub(crate) mod sized_chain;
mod sorting;

use std::collections::{HashMap, VecDeque};

use intersperser::Intersperser;
use sized_chain::SizedChain;

use self::context::Context;
use super::{CardQueues, Counts, LearningQueueEntry, MainQueueEntry, MainQueueEntryKind};
use crate::{
    deckconfig::{NewCardGatherPriority, NewCardSortOrder, ReviewCardOrder, ReviewMix},
    decks::limits::RemainingLimits,
    prelude::*,
};

/// Temporary holder for review cards that will be built into a queue.
#[derive(Debug, Clone, Copy)]
pub(crate) struct DueCard {
    pub id: CardId,
    pub note_id: NoteId,
    pub mtime: TimestampSecs,
    pub due: i32,
    pub current_deck_id: DeckId,
    pub original_deck_id: DeckId,
    pub kind: DueCardKind,
}

#[derive(Debug, Clone, Copy)]
pub(crate) enum DueCardKind {
    Review,
    Learning,
}

/// Temporary holder for new cards that will be built into a queue.
#[derive(Debug, Default, Clone)]
pub(crate) struct NewCard {
    pub id: CardId,
    pub note_id: NoteId,
    pub mtime: TimestampSecs,
    pub due: i32,
    pub current_deck_id: DeckId,
    pub original_deck_id: DeckId,
    pub template_index: u32,
    pub hash: u64,
}

impl From<DueCard> for MainQueueEntry {
    fn from(c: DueCard) -> Self {
        MainQueueEntry {
            id: c.id,
            mtime: c.mtime,
            kind: match c.kind {
                DueCardKind::Review => MainQueueEntryKind::Review,
                DueCardKind::Learning => MainQueueEntryKind::InterdayLearning,
            },
        }
    }
}

impl From<NewCard> for MainQueueEntry {
    fn from(c: NewCard) -> Self {
        MainQueueEntry {
            id: c.id,
            mtime: c.mtime,
            kind: MainQueueEntryKind::New,
        }
    }
}

impl From<DueCard> for LearningQueueEntry {
    fn from(c: DueCard) -> Self {
        LearningQueueEntry {
            due: TimestampSecs(c.due as i64),
            id: c.id,
            mtime: c.mtime,
        }
    }
}

/// When we encounter a card with new or review burying enabled, all future
/// siblings need to be buried, regardless of their own settings.
#[derive(Default, Debug, Clone, Copy)]
pub(super) struct BuryMode {
    bury_new: bool,
    bury_reviews: bool,
}

#[derive(Default, Clone, Debug)]
pub(super) struct QueueSortOptions {
    pub(super) new_order: NewCardSortOrder,
    pub(super) new_gather_priority: NewCardGatherPriority,
    pub(super) review_order: ReviewCardOrder,
    pub(super) day_learn_mix: ReviewMix,
    pub(super) new_review_mix: ReviewMix,
}

#[derive(Default)]
pub(super) struct QueueBuilder {
    pub(super) new: Vec<NewCard>,
    pub(super) review: Vec<DueCard>,
    pub(super) learning: Vec<DueCard>,
    pub(super) day_learning: Vec<DueCard>,
    pub(super) seen_note_ids: HashMap<NoteId, BuryMode>,
    pub(super) sort_options: QueueSortOptions,
}

impl QueueBuilder {
    pub(super) fn new(sort_options: QueueSortOptions) -> Self {
        QueueBuilder {
            sort_options,
            ..Default::default()
        }
    }

    pub(super) fn build(
        mut self,
        top_deck_limits: RemainingLimits,
        learn_ahead_secs: i64,
        current_day: u32,
    ) -> CardQueues {
        self.sort_new();

        // intraday learning and total learn count
        let intraday_learning = sort_learning(self.learning);
        let now = TimestampSecs::now();
        let cutoff = now.adding_secs(learn_ahead_secs);
        let learn_count = intraday_learning
            .iter()
            .take_while(|e| e.due <= cutoff)
            .count()
            + self.day_learning.len();

        // cap and note down review + new counts
        self.review.truncate(top_deck_limits.review as usize);
        let review_count = self.review.len();
        self.new.truncate(top_deck_limits.new as usize);
        let new_count = self.new.len();

        // merge interday and new cards into main
        let with_interday_learn = merge_day_learning(
            self.review,
            self.day_learning,
            self.sort_options.day_learn_mix,
        );
        let main_iter = merge_new(
            with_interday_learn,
            self.new,
            self.sort_options.new_review_mix,
        );

        CardQueues {
            counts: Counts {
                new: new_count,
                review: review_count,
                learning: learn_count,
            },
            main: main_iter.collect(),
            intraday_learning,
            learn_ahead_secs,
            current_day,
            build_time: TimestampMillis::now(),
            current_learning_cutoff: now,
        }
    }
}

fn merge_day_learning(
    reviews: Vec<DueCard>,
    day_learning: Vec<DueCard>,
    mode: ReviewMix,
) -> Box<dyn ExactSizeIterator<Item = MainQueueEntry>> {
    let day_learning_iter = day_learning.into_iter().map(Into::into);
    let reviews_iter = reviews.into_iter().map(Into::into);

    match mode {
        ReviewMix::AfterReviews => Box::new(SizedChain::new(reviews_iter, day_learning_iter)),
        ReviewMix::BeforeReviews => Box::new(SizedChain::new(day_learning_iter, reviews_iter)),
        ReviewMix::MixWithReviews => Box::new(Intersperser::new(reviews_iter, day_learning_iter)),
    }
}

fn merge_new(
    review_iter: impl ExactSizeIterator<Item = MainQueueEntry> + 'static,
    new: Vec<NewCard>,
    mode: ReviewMix,
) -> Box<dyn ExactSizeIterator<Item = MainQueueEntry>> {
    let new_iter = new.into_iter().map(Into::into);

    match mode {
        ReviewMix::BeforeReviews => Box::new(SizedChain::new(new_iter, review_iter)),
        ReviewMix::AfterReviews => Box::new(SizedChain::new(review_iter, new_iter)),
        ReviewMix::MixWithReviews => Box::new(Intersperser::new(review_iter, new_iter)),
    }
}

fn sort_learning(mut learning: Vec<DueCard>) -> VecDeque<LearningQueueEntry> {
    learning.sort_unstable_by(|a, b| a.due.cmp(&b.due));
    learning.into_iter().map(LearningQueueEntry::from).collect()
}

impl Collection {
    pub(crate) fn build_queues(&mut self, deck_id: DeckId) -> Result<CardQueues> {
        let mut ctx = Context::new(self, deck_id)?;
        self.storage.update_active_decks(&ctx.root_deck)?;
        let mut queues = QueueBuilder::new(ctx.sort_options.clone());

        self.add_intraday_learning_cards(&mut queues, &mut ctx)?;

        self.add_due_cards(&mut queues, &mut ctx, DueCardKind::Learning)?;
        self.add_due_cards(&mut queues, &mut ctx, DueCardKind::Review)?;

        ctx.limits.cap_new_to_review();
        match ctx.sort_options.new_gather_priority {
            NewCardGatherPriority::Deck => self.add_new_cards_by_deck(&mut queues, &mut ctx)?,
            NewCardGatherPriority::LowestPosition => {
                self.add_new_cards_by_position(&mut queues, &mut ctx, false)?
            }
            NewCardGatherPriority::HighestPosition => {
                self.add_new_cards_by_position(&mut queues, &mut ctx, true)?
            }
        };

        let queues = queues.build(
            ctx.limits.final_limits(),
            self.learn_ahead_secs() as i64,
            ctx.timing.days_elapsed,
        );

        Ok(queues)
    }

    fn add_intraday_learning_cards(
        &self,
        queues: &mut QueueBuilder,
        ctx: &mut Context,
    ) -> Result<()> {
        self.storage
            .for_each_intraday_card_in_active_decks(ctx.timing.next_day_at, |card| {
                let bury = ctx.bury_mode(card.current_deck_id);
                queues.add_intraday_learning_card(card, bury)
            })?;

        Ok(())
    }

    fn add_due_cards(
        &self,
        queues: &mut QueueBuilder,
        ctx: &mut Context,
        kind: DueCardKind,
    ) -> Result<()> {
        if !ctx.limits.is_exhausted_root() {
            self.storage.for_each_due_card_in_active_decks(
                ctx.timing.days_elapsed,
                ctx.sort_options.review_order,
                kind,
                |card| {
                    if ctx.limits.is_exhausted_root() {
                        return false;
                    }
                    let bury = ctx.bury_mode(card.original_deck_id.or(card.current_deck_id));
                    if let Some(node_id) = ctx.limits.remaining_node_id(card.current_deck_id) {
                        if queues.add_due_card(card, bury) {
                            ctx.limits.decrement_node_and_parent_limits(&node_id, false);
                        }
                    }

                    true
                },
            )?;
        }
        Ok(())
    }

    fn add_new_cards_by_deck(&self, queues: &mut QueueBuilder, ctx: &mut Context) -> Result<()> {
        // TODO: must own Vec as closure below requires unique access to ctx
        // maybe decks should not be field of Context?
        for deck_id in ctx.limits.remaining_decks() {
            if ctx.limits.is_exhausted_root() {
                break;
            }
            if !ctx.limits.is_exhausted(deck_id) {
                self.storage.for_each_new_card_in_deck(deck_id, |card| {
                    let bury = ctx.bury_mode(card.original_deck_id.or(deck_id));
                    // TODO: This could be done more efficiently if we held on to the node_id
                    // and only adjusted the parent nodes after this node's limit is reached
                    if let Some(node_id) = ctx.limits.remaining_node_id(deck_id) {
                        if queues.add_new_card(card, bury) {
                            ctx.limits.decrement_node_and_parent_limits(&node_id, true);
                        }

                        true
                    } else {
                        false
                    }
                })?;
            }
        }

        Ok(())
    }

    fn add_new_cards_by_position(
        &self,
        queues: &mut QueueBuilder,
        ctx: &mut Context,
        reverse: bool,
    ) -> Result<()> {
        self.storage
            .for_each_new_card_in_active_decks(reverse, |card| {
                let bury = ctx.bury_mode(card.original_deck_id.or(card.current_deck_id));
                if let Some(node_id) = ctx.limits.remaining_node_id(card.current_deck_id) {
                    if queues.add_new_card(card, bury) {
                        ctx.limits.decrement_node_and_parent_limits(&node_id, true);
                    }

                    true
                } else {
                    false
                }
            })?;

        Ok(())
    }
}
