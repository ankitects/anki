// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod gathering;
pub(crate) mod intersperser;
pub(crate) mod sized_chain;
mod sorting;

use std::collections::{HashMap, VecDeque};

use intersperser::Intersperser;
use sized_chain::SizedChain;

use super::{
    limits::{remaining_limits_capped_to_parents, RemainingLimits},
    CardQueues, Counts, LearningQueueEntry, MainQueueEntry, MainQueueEntryKind,
};
use crate::{
    deckconfig::{NewCardSortOrder, ReviewCardOrder, ReviewMix},
    prelude::*,
};

/// Temporary holder for review cards that will be built into a queue.
#[derive(Debug, Default, Clone)]
pub(crate) struct DueCard {
    pub id: CardId,
    pub note_id: NoteId,
    pub mtime: TimestampSecs,
    pub due: i32,
    pub interval: u32,
    pub original_deck_id: DeckId,
    pub hash: u64,
}

/// Temporary holder for new cards that will be built into a queue.
#[derive(Debug, Default, Clone)]
pub(crate) struct NewCard {
    pub id: CardId,
    pub note_id: NoteId,
    pub mtime: TimestampSecs,
    pub due: i32,
    pub original_deck_id: DeckId,
    pub template_index: u32,
    pub hash: u64,
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
        selected_deck: DeckId,
        current_day: u32,
    ) -> CardQueues {
        self.sort_new();
        self.sort_reviews(current_day);

        // intraday learning
        let learning = sort_learning(self.learning);
        let cutoff = TimestampSecs::now().adding_secs(learn_ahead_secs);
        let learn_count = learning.iter().take_while(|e| e.due <= cutoff).count();

        // merge interday learning into main, and cap to parent review count
        let main_iter = merge_day_learning(
            self.review,
            self.day_learning,
            self.sort_options.day_learn_mix,
        );
        let main_iter = main_iter.take(top_deck_limits.review as usize);
        let review_count = main_iter.len();

        // cap to parent new count, note down the new count, then merge new in
        self.new.truncate(top_deck_limits.new as usize);
        let new_count = self.new.len();
        let main_iter = merge_new(main_iter, self.new, self.sort_options.new_review_mix);

        CardQueues {
            counts: Counts {
                new: new_count,
                review: review_count,
                learning: learn_count,
            },
            undo: Vec::new(),
            main: main_iter.collect(),
            learning,
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

fn sort_learning(mut learning: Vec<DueCard>) -> VecDeque<LearningQueueEntry> {
    learning.sort_unstable_by(|a, b| a.due.cmp(&b.due));
    learning.into_iter().map(LearningQueueEntry::from).collect()
}

impl Collection {
    pub(crate) fn build_queues(&mut self, deck_id: DeckId) -> Result<CardQueues> {
        let now = TimestampSecs::now();
        let timing = self.timing_for_timestamp(now)?;
        let (decks, parent_count) = self.storage.deck_with_parents_and_children(deck_id)?;
        let deck_map = self.storage.get_decks_map()?;
        let config = self.storage.get_deck_config_map()?;
        let sort_options = decks
            .get(parent_count)
            .unwrap()
            .config_id()
            .and_then(|config_id| config.get(&config_id))
            .map(|config| QueueSortOptions {
                new_order: config.inner.new_card_sort_order(),
                review_order: config.inner.review_order(),
                day_learn_mix: config.inner.interday_learning_mix(),
                new_review_mix: config.inner.new_mix(),
            })
            .unwrap_or_else(|| {
                // filtered decks do not space siblings
                QueueSortOptions {
                    new_order: NewCardSortOrder::Due,
                    ..Default::default()
                }
            });
        let limits = remaining_limits_capped_to_parents(&decks, &config, timing.days_elapsed);
        let selected_deck_limits = limits[parent_count];
        let mut queues = QueueBuilder::new(sort_options);

        for (deck, mut limit) in decks.iter().zip(limits).skip(parent_count) {
            if limit.review > 0 {
                self.storage.for_each_due_card_in_deck(
                    timing.days_elapsed,
                    timing.next_day_at,
                    deck.id,
                    |queue, card| {
                        let home_deck = if card.original_deck_id.0 == 0 {
                            deck.id
                        } else {
                            card.original_deck_id
                        };
                        let bury = deck_map
                            .get(&home_deck)
                            .and_then(|deck| deck.config_id())
                            .and_then(|config_id| config.get(&config_id))
                            .map(|config| BuryMode {
                                bury_new: config.inner.bury_new,
                                bury_reviews: config.inner.bury_reviews,
                            })
                            .unwrap_or_default();
                        queues.add_due_card(&mut limit, queue, card, bury)
                    },
                )?;
            }
            if limit.new > 0 {
                self.storage.for_each_new_card_in_deck(deck.id, |card| {
                    let home_deck = if card.original_deck_id.0 == 0 {
                        deck.id
                    } else {
                        card.original_deck_id
                    };
                    let bury = deck_map
                        .get(&home_deck)
                        .and_then(|deck| deck.config_id())
                        .and_then(|config_id| config.get(&config_id))
                        .map(|config| BuryMode {
                            bury_new: config.inner.bury_new,
                            bury_reviews: config.inner.bury_reviews,
                        })
                        .unwrap_or_default();
                    queues.add_new_card(&mut limit, card, bury)
                })?;
            }
        }

        let queues = queues.build(
            selected_deck_limits,
            self.learn_ahead_secs() as i64,
            deck_id,
            timing.days_elapsed,
        );

        Ok(queues)
    }
}
