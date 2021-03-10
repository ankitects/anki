// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod gathering;
pub(crate) mod intersperser;
pub(crate) mod sized_chain;
mod sorting;

use std::{
    cmp::Reverse,
    collections::{BinaryHeap, HashSet, VecDeque},
};

use super::{
    limits::{remaining_limits_capped_to_parents, RemainingLimits},
    CardQueues, Counts, LearningQueueEntry, MainQueueEntry, MainQueueEntryKind,
};
use crate::deckconf::{NewCardOrder, ReviewCardOrder, ReviewMix};
use crate::prelude::*;
use {intersperser::Intersperser, sized_chain::SizedChain};

/// Temporary holder for review cards that will be built into a queue.
#[derive(Debug, Default, Clone)]
pub(crate) struct DueCard {
    pub id: CardID,
    pub note_id: NoteID,
    pub mtime: TimestampSecs,
    pub due: i32,
    pub interval: u32,
    pub hash: u64,
}

/// Temporary holder for new cards that will be built into a queue.
#[derive(Debug, Default, Clone)]
pub(crate) struct NewCard {
    pub id: CardID,
    pub note_id: NoteID,
    pub mtime: TimestampSecs,
    pub due: i32,
    /// Used to store template_idx, and for shuffling
    pub extra: u64,
}

impl From<DueCard> for MainQueueEntry {
    fn from(c: DueCard) -> Self {
        MainQueueEntry {
            id: c.id,
            mtime: c.mtime,
            kind: MainQueueEntryKind::Review,
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

#[derive(Default)]
pub(super) struct QueueBuilder {
    pub(super) new: Vec<NewCard>,
    pub(super) review: Vec<DueCard>,
    pub(super) learning: Vec<DueCard>,
    pub(super) day_learning: Vec<DueCard>,
    pub(super) seen_note_ids: HashSet<NoteID>,
    pub(super) new_order: NewCardOrder,
    pub(super) review_order: ReviewCardOrder,
    pub(super) day_learn_mix: ReviewMix,
    pub(super) new_review_mix: ReviewMix,
    pub(super) bury_new: bool,
    pub(super) bury_reviews: bool,
}

impl QueueBuilder {
    pub(super) fn build(
        mut self,
        top_deck_limits: RemainingLimits,
        learn_ahead_secs: u32,
        selected_deck: DeckID,
        current_day: u32,
    ) -> CardQueues {
        self.sort_new();
        self.sort_reviews();

        // split and sort learning
        let learn_ahead_secs = learn_ahead_secs as i64;
        let (due_learning, later_learning) = split_learning(self.learning, learn_ahead_secs);
        let learn_count = due_learning.len();

        // merge day learning in, and cap to parent review count
        let main_iter = merge_day_learning(self.review, self.day_learning, self.day_learn_mix);
        let main_iter = main_iter.take(top_deck_limits.review as usize);
        let review_count = main_iter.len();

        // cap to parent new count, note down the new count, then merge new in
        self.new.truncate(top_deck_limits.new as usize);
        let new_count = self.new.len();
        let main_iter = merge_new(main_iter, self.new, self.new_review_mix);

        CardQueues {
            counts: Counts {
                new: new_count,
                review: review_count,
                learning: learn_count,
            },
            undo: Vec::new(),
            main: main_iter.collect(),
            due_learning,
            later_learning,
            learn_ahead_secs,
            selected_deck,
            current_day,
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

/// Split the learning queue into cards due within limit, and cards due later
/// today. Learning does not need to be sorted in advance, as the sorting is
/// done as the heaps/dequeues are built.
fn split_learning(
    learning: Vec<DueCard>,
    learn_ahead_secs: i64,
) -> (
    VecDeque<LearningQueueEntry>,
    BinaryHeap<Reverse<LearningQueueEntry>>,
) {
    let cutoff = TimestampSecs(TimestampSecs::now().0 + learn_ahead_secs);

    // split learning into now and later
    let (mut now, later): (Vec<_>, Vec<_>) = learning
        .into_iter()
        .map(LearningQueueEntry::from)
        .partition(|c| c.due <= cutoff);

    // sort due items in ascending order, as we pop the deque from the front
    now.sort_unstable_by(|a, b| a.due.cmp(&b.due));
    // partition() requires both outputs to be the same, so we need to create the deque
    // separately
    let now = VecDeque::from(now);

    // build the binary min heap
    let later: BinaryHeap<_> = later.into_iter().map(Reverse).collect();

    (now, later)
}

impl Collection {
    pub(crate) fn build_queues(&mut self, deck_id: DeckID) -> Result<CardQueues> {
        let now = TimestampSecs::now();
        let timing = self.timing_for_timestamp(now)?;
        let (decks, parent_count) = self.storage.deck_with_parents_and_children(deck_id)?;
        let config = self.storage.get_deck_config_map()?;
        let limits = remaining_limits_capped_to_parents(&decks, &config, timing.days_elapsed);
        let selected_deck_limits = limits[parent_count];

        let mut queues = QueueBuilder::default();

        for (deck, mut limit) in decks.iter().zip(limits).skip(parent_count) {
            if limit.review > 0 {
                self.storage.for_each_due_card_in_deck(
                    timing.days_elapsed,
                    timing.next_day_at,
                    deck.id,
                    |queue, card| queues.add_due_card(&mut limit, queue, card),
                )?;
            }
            if limit.new > 0 {
                self.storage.for_each_new_card_in_deck(deck.id, |card| {
                    queues.add_new_card(&mut limit, card)
                })?;
            }
        }

        let queues = queues.build(
            selected_deck_limits,
            self.learn_ahead_secs(),
            deck_id,
            timing.days_elapsed,
        );

        Ok(queues)
    }
}
