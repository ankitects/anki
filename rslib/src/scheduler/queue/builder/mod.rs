// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod burying;
mod gathering;
pub(crate) mod intersperser;
pub(crate) mod sized_chain;
mod sorting;

#[cfg(test)]
mod benchmark;

use std::collections::HashMap;
use std::collections::HashSet;
use std::collections::VecDeque;
use std::sync::Arc;

use intersperser::Intersperser;
use sized_chain::SizedChain;

use super::BuryMode;
use super::CardQueues;
use super::Counts;
use super::DeferredRwkvReview;
use super::LearningQueueEntry;
use super::MainQueueEntry;
use super::MainQueueEntryKind;
use super::NewReviewMixProgress;
use crate::card::CardQueue;
use crate::card::FsrsMemoryState;
use crate::collection::RwkvReviewQueueScoreEntry;
use crate::deckconfig::NewCardGatherPriority;
use crate::deckconfig::NewCardSortOrder;
use crate::deckconfig::ReviewCardOrder;
use crate::deckconfig::ReviewMix;
use crate::decks::limits::LimitTreeMap;
use crate::prelude::*;
use crate::scheduler::states::load_balancer::LoadBalancer;
use crate::scheduler::timing::SchedTimingToday;

/// Temporary holder for review cards that will be built into a queue.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct DueCard {
    pub id: CardId,
    pub note_id: NoteId,
    pub mtime: TimestampSecs,
    pub due: i32,
    pub current_deck_id: DeckId,
    pub original_deck_id: DeckId,
    pub kind: DueCardKind,
    pub reps: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum DueCardKind {
    Review,
    Learning,
}

/// Only the scheduling state needed to score and enqueue a due candidate.
#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) struct DueCardWithState {
    pub card: DueCard,
    pub queue: CardQueue,
    pub interval: u32,
    pub original_due: i32,
    pub memory_state: Option<FsrsMemoryState>,
    pub desired_retention: Option<f32>,
    pub last_review_time: Option<TimestampSecs>,
}

impl DueCardWithState {
    fn original_or_current_due(&self) -> i32 {
        if self.card.original_deck_id.0 > 0 {
            self.original_due
        } else {
            self.card.due
        }
    }
}

#[cfg(test)]
impl From<&Card> for DueCardWithState {
    fn from(card: &Card) -> Self {
        Self {
            card: DueCard {
                id: card.id,
                note_id: card.note_id,
                mtime: card.mtime,
                due: card.due,
                current_deck_id: card.deck_id,
                original_deck_id: card.original_deck_id,
                kind: if card.queue == CardQueue::Review {
                    DueCardKind::Review
                } else {
                    DueCardKind::Learning
                },
                reps: card.reps,
            },
            queue: card.queue,
            interval: card.interval,
            original_due: card.original_due,
            memory_state: card.memory_state,
            desired_retention: card.desired_retention,
            last_review_time: card.last_review_time,
        }
    }
}

/// Temporary holder for new cards that will be built into a queue.
#[derive(Debug, Default, Clone, Copy)]
pub(crate) struct NewCard {
    pub id: CardId,
    pub note_id: NoteId,
    pub mtime: TimestampSecs,
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
            reps: c.reps,
        }
    }
}

#[derive(Default, Clone, Debug)]
pub(super) struct QueueSortOptions {
    pub(super) new_order: NewCardSortOrder,
    pub(super) new_gather_priority: NewCardGatherPriority,
    pub(super) review_order: ReviewCardOrder,
    pub(super) day_learn_mix: ReviewMix,
    pub(super) intraday_learn_mix: ReviewMix,
    pub(super) new_review_mix: ReviewMix,
    pub(super) reviews_per_new_card: Option<u32>,
    pub(super) rwkv_review_enabled: bool,
    pub(super) rwkv_review_instant_order_enabled: bool,
    pub(super) rwkv_review_min_intervening_reviews: u32,
    pub(super) rwkv_review_min_elapsed_secs: u32,
}

#[derive(Debug)]
pub(super) struct QueueBuilder {
    pub(super) new: Vec<NewCard>,
    pub(super) review: Vec<DueCard>,
    pub(super) learning: Vec<DueCard>,
    pub(super) day_learning: Vec<DueCard>,
    pub(super) r_sorted_non_new: Vec<DueCard>,
    r_sorted_intraday_learning: Vec<DueCard>,
    pinned_card_id: Option<CardId>,
    deferred_rwkv_reviews: HashMap<CardId, DeferredRwkvReview>,
    new_review_mix_progress: NewReviewMixProgress,
    limits: LimitTreeMap,
    load_balancer: Option<LoadBalancer>,
    context: Context,
}

/// Data container and helper for building queues.
#[derive(Debug, Clone)]
struct Context {
    timing: SchedTimingToday,
    config_map: HashMap<DeckConfigId, DeckConfig>,
    root_deck: Deck,
    sort_options: QueueSortOptions,
    seen_note_ids: HashMap<NoteId, BuryMode>,
    deck_map: HashMap<DeckId, Deck>,
    fsrs: bool,
    fsrs_short_term_with_steps: bool,
    rwkv_review_queue_scores: Option<Arc<HashMap<CardId, RwkvReviewQueueScoreEntry>>>,
    reviewed_today_card_ids: HashSet<CardId>,
    same_day_reviews_may_ignore_limit: bool,
}

impl QueueBuilder {
    pub(super) fn new(col: &mut Collection, deck_id: DeckId) -> Result<Self> {
        let timing = col.timing_for_timestamp(TimestampSecs::now())?;
        let new_cards_ignore_review_limit = col.get_config_bool(BoolKey::NewCardsIgnoreReviewLimit);
        let apply_all_parent_limits = col.get_config_bool(BoolKey::ApplyAllParentLimits);
        let config_map = col.storage.get_deck_config_map()?;
        let root_deck = col.storage.get_deck(deck_id)?.or_not_found(deck_id)?;
        let mut decks = col.storage.child_decks(&root_deck)?;
        decks.insert(0, root_deck.clone());
        if apply_all_parent_limits {
            for parent in col.storage.parent_decks(&root_deck)? {
                decks.insert(0, parent);
            }
        }
        let mut limits = LimitTreeMap::build(
            &decks,
            &config_map,
            timing.days_elapsed,
            new_cards_ignore_review_limit,
            deck_id,
        );
        // the reservation only lowers RWKV review minimums: without any, skip
        // its scan of every card
        if limits.any_rwkv_review_minimum_remaining() {
            for (original_deck_id, count) in
                col.storage.filtered_review_counts_by_original_deck()?
            {
                limits.reserve_rwkv_reviews_if_present(original_deck_id, count);
            }
        }
        let sort_options = sort_options(&root_deck, &config_map);
        let new_review_mix_progress = col
            .state
            .card_queues
            .as_ref()
            .map(|queues| queues.new_review_mix_progress)
            .or(col.state.pending_new_review_mix)
            .filter(|progress| {
                sort_options.uses_rwkv_review_order()
                    && progress.deck_id == deck_id
                    && progress.day == timing.days_elapsed
            })
            .unwrap_or(NewReviewMixProgress {
                deck_id,
                day: timing.days_elapsed,
                new_answered: 0,
                review_answered: 0,
            });
        let same_day_reviews_may_ignore_limit = config_map
            .values()
            .any(|config| config.inner.same_day_reviews_ignore_review_limit);
        let reviewed_today_card_ids = if same_day_reviews_may_ignore_limit {
            col.storage.card_ids_reviewed_between(
                timing.next_day_at.adding_secs(-86_400),
                timing.next_day_at,
            )?
        } else {
            HashSet::new()
        };
        let rwkv_review_queue_scores = if sort_options.uses_rwkv_retrievability_scores() {
            if let Some(scores) = col.rwkv_review_queue_scores(root_deck.id, timing.days_elapsed) {
                Some(scores)
            } else if let Some((score_deck_id, scores)) =
                col.rwkv_review_queue_scores_for_day(timing.days_elapsed)
            {
                col.storage
                    .get_deck(score_deck_id)?
                    .filter(|score_root| deck_is_within_scope(&root_deck, score_root))
                    .map(|_| scores)
            } else {
                None
            }
        } else {
            None
        };
        let deck_map = col.storage.get_decks_map()?;

        let load_balancer = col
            .get_config_bool(BoolKey::LoadBalancerEnabled)
            .then(|| {
                let did_to_dcid = deck_map
                    .values()
                    .filter_map(|deck| Some((deck.id, deck.config_id()?)))
                    .collect::<HashMap<_, _>>();
                LoadBalancer::new(
                    timing.days_elapsed,
                    did_to_dcid,
                    col.review_fuzz_config(),
                    col.timing_today()?.next_day_at,
                    &col.storage,
                )
            })
            .transpose()?;

        Ok(QueueBuilder {
            new: Vec::new(),
            review: Vec::new(),
            learning: Vec::new(),
            day_learning: Vec::new(),
            r_sorted_non_new: Vec::new(),
            r_sorted_intraday_learning: Vec::new(),
            pinned_card_id: None,
            deferred_rwkv_reviews: HashMap::new(),
            new_review_mix_progress,
            limits,
            load_balancer,
            context: Context {
                timing,
                config_map,
                root_deck,
                sort_options,
                seen_note_ids: HashMap::new(),
                deck_map,
                fsrs: col.get_config_bool(BoolKey::Fsrs),
                fsrs_short_term_with_steps: col
                    .get_config_bool(BoolKey::FsrsShortTermWithStepsEnabled),
                rwkv_review_queue_scores,
                reviewed_today_card_ids,
                same_day_reviews_may_ignore_limit,
            },
        })
    }

    fn pin_current_card(&mut self, card: &Card) -> Result<()> {
        require!(
            self.pinned_card_id.is_none(),
            "a queue can only preserve one current card"
        );

        let current_deck_id = card.deck_id;
        let original_deck_id = card.original_deck_id;
        match card.queue {
            CardQueue::New => {
                let card = NewCard {
                    id: card.id,
                    note_id: card.note_id,
                    mtime: card.mtime,
                    current_deck_id,
                    original_deck_id,
                    template_index: card.template_idx as u32,
                    hash: 0,
                };
                require!(self.add_new_card(card), "current new card was buried");
                self.limits.reserve_new_card(current_deck_id)?;
            }
            CardQueue::Learn | CardQueue::PreviewRepeat => {
                let card = DueCard {
                    id: card.id,
                    note_id: card.note_id,
                    mtime: card.mtime,
                    due: card.due,
                    current_deck_id,
                    original_deck_id,
                    kind: DueCardKind::Learning,
                    reps: card.reps,
                };
                self.get_and_update_bury_mode_for_note(card.into());
                if self.context.non_news_sorted_by_retrievability()
                    && card.due <= self.context.timing.now.0 as i32
                {
                    self.push_retrievability_sorted_card(card, false);
                } else {
                    self.learning.push(card);
                }
            }
            CardQueue::Review | CardQueue::DayLearn => {
                let card = DueCard {
                    id: card.id,
                    note_id: card.note_id,
                    mtime: card.mtime,
                    due: card.due,
                    current_deck_id,
                    original_deck_id,
                    kind: if card.queue == CardQueue::Review {
                        DueCardKind::Review
                    } else {
                        DueCardKind::Learning
                    },
                    reps: card.reps,
                };
                if self.context.non_news_sorted_by_retrievability() {
                    require!(
                        self.add_due_card_for_retrievability_sort(card, true),
                        "current review card was buried"
                    );
                    self.push_retrievability_sorted_card(card, true);
                } else {
                    require!(self.add_due_card(card), "current review card was buried");
                }
                self.limits
                    .reserve_review(current_deck_id, original_deck_id)?;
            }
            CardQueue::Suspended | CardQueue::SchedBuried | CardQueue::UserBuried => {
                invalid_input!("current card is not eligible for the review queue")
            }
        }

        self.pinned_card_id = Some(card.id);
        Ok(())
    }

    fn push_retrievability_sorted_card(&mut self, card: DueCard, interday_or_review: bool) {
        if !interday_or_review
            && self.context.sort_options.intraday_learn_mix != ReviewMix::MixWithReviews
        {
            self.r_sorted_intraday_learning.push(card);
        } else if interday_or_review
            && card.kind == DueCardKind::Learning
            && self.context.sort_options.day_learn_mix != ReviewMix::MixWithReviews
        {
            self.day_learning.push(card);
        } else {
            self.r_sorted_non_new.push(card);
        }
    }

    fn card_is_pinned(&self, card_id: CardId) -> bool {
        self.pinned_card_id == Some(card_id)
    }

    pub(super) fn build(mut self, learn_ahead_secs: i64) -> CardQueues {
        self.sort_new();

        let rwkv_learning_repeat_guards = self
            .context
            .sort_options
            .uses_rwkv_review_order()
            .then_some(super::rwkv::RwkvLearningRepeatGuards {
                min_intervening_reviews: self
                    .context
                    .sort_options
                    .rwkv_review_min_intervening_reviews,
                min_elapsed_secs: self.context.sort_options.rwkv_review_min_elapsed_secs,
            });

        // intraday learning and total learn count
        let intraday_learning = sort_learning(self.learning);
        let now = TimestampSecs::now();
        let cutoff = now.adding_secs(learn_ahead_secs);
        let intraday_learning_count = intraday_learning.iter().filter(|e| e.due <= cutoff).count();
        let shared_r_sort = self.context.non_news_sorted_by_retrievability();
        let r_sorted_learning_count = self
            .r_sorted_non_new
            .iter()
            .filter(|card| matches!(card.kind, DueCardKind::Learning))
            .count();
        let r_sorted_review_count = self
            .r_sorted_non_new
            .iter()
            .filter(|card| matches!(card.kind, DueCardKind::Review))
            .count();
        let learn_count = if shared_r_sort {
            intraday_learning_count
                + r_sorted_learning_count
                + self.day_learning.len()
                + self.r_sorted_intraday_learning.len()
        } else {
            intraday_learning_count + self.day_learning.len()
        };

        let review_count = if shared_r_sort {
            r_sorted_review_count
        } else {
            self.review.len()
        };
        let new_count = self.new.len();

        // merge due non-new and new cards into main
        let with_interday_learn = if shared_r_sort {
            merge_day_learning(
                self.r_sorted_non_new,
                self.day_learning,
                self.context.sort_options.day_learn_mix,
            )
        } else {
            merge_day_learning(
                self.review,
                self.day_learning,
                self.context.sort_options.day_learn_mix,
            )
        };
        let intraday_iter = self
            .r_sorted_intraday_learning
            .into_iter()
            .map(MainQueueEntry::from);
        let (new_done, reviews_done) = self
            .context
            .root_deck
            .new_rev_counts(self.context.timing.days_elapsed);
        let main_iter = merge_new(
            with_interday_learn,
            self.new,
            self.context.sort_options.new_review_mix,
            self.context.sort_options.reviews_per_new_card,
            reviews_done.max(0) as usize,
            new_done.max(0) as usize,
            self.new_review_mix_progress,
        );
        let main_iter: Box<dyn ExactSizeIterator<Item = MainQueueEntry>> =
            match self.context.sort_options.intraday_learn_mix {
                ReviewMix::BeforeReviews => Box::new(SizedChain::new(intraday_iter, main_iter)),
                ReviewMix::AfterReviews => Box::new(SizedChain::new(main_iter, intraday_iter)),
                ReviewMix::MixWithReviews => main_iter,
            };

        let main: VecDeque<MainQueueEntry> = main_iter.collect();
        // kuma3: one line per build, to see in logcat how the new cards are spread
        tracing::info!(
            new = new_count,
            review = review_count,
            learning = learn_count,
            new_done,
            reviews_done,
            reviews_per_new_card = ?self.context.sort_options.reviews_per_new_card,
            next_new_at = ?main.iter().position(|entry| entry.kind == MainQueueEntryKind::New),
            "queue built"
        );

        CardQueues {
            counts: Counts {
                new: new_count,
                review: review_count,
                learning: learn_count,
            },
            main,
            intraday_learning,
            learn_ahead_secs,
            current_day: self.context.timing.days_elapsed,
            build_time: TimestampMillis::now(),
            load_balancer: self.load_balancer,
            fsrs_enabled: self.context.fsrs,
            fsrs_short_term_with_steps: self.context.fsrs_short_term_with_steps,
            current_learning_cutoff: now,
            shown_top_card: None,
            non_news_sorted_by_retrievability: shared_r_sort,
            deferred_rwkv_reviews: self.deferred_rwkv_reviews,
            rwkv_learning_repeat_guards,
            blocked_rwkv_learning_cards: HashSet::new(),
            new_review_mix_progress: self.new_review_mix_progress,
        }
    }
}

fn deck_is_within_scope(deck: &Deck, scope_root: &Deck) -> bool {
    let mut deck_components = deck.name.components();
    scope_root
        .name
        .components()
        .all(|component| deck_components.next() == Some(component))
}

impl Context {
    fn non_news_sorted_by_retrievability(&self) -> bool {
        self.fsrs
            && !self.sort_options.uses_rwkv_review_order()
            && !self.sort_options.rwkv_review_enabled
            && matches!(
                self.sort_options.review_order,
                ReviewCardOrder::RetrievabilityAscending
                    | ReviewCardOrder::RetrievabilityDescending
                    | ReviewCardOrder::RelativeOverdueness
            )
    }

    fn uses_rwkv_review_order(&self) -> bool {
        self.rwkv_review_queue_scores.is_some() && self.sort_options.uses_rwkv_review_order()
    }
}

impl QueueSortOptions {
    fn uses_rwkv_review_order(&self) -> bool {
        self.rwkv_review_instant_order_enabled
    }

    fn uses_rwkv_retrievability_scores(&self) -> bool {
        self.uses_rwkv_review_order()
            || matches!(
                self.new_gather_priority,
                NewCardGatherPriority::AscendingRetrievability
                    | NewCardGatherPriority::DescendingRetrievability
            )
    }

    fn gather_review_order(&self) -> ReviewCardOrder {
        if (self.rwkv_review_enabled || self.rwkv_review_instant_order_enabled)
            && matches!(
                self.review_order,
                ReviewCardOrder::RetrievabilityAscending
                    | ReviewCardOrder::RetrievabilityDescending
            )
        {
            ReviewCardOrder::Day
        } else {
            self.review_order
        }
    }
}

fn sort_options(deck: &Deck, config_map: &HashMap<DeckConfigId, DeckConfig>) -> QueueSortOptions {
    deck.config_id()
        .and_then(|config_id| config_map.get(&config_id))
        .map(|config| QueueSortOptions {
            new_order: config.inner.new_card_sort_order(),
            new_gather_priority: config.inner.new_card_gather_priority(),
            review_order: config.inner.review_order(),
            day_learn_mix: config.inner.interday_learning_mix(),
            intraday_learn_mix: config.intraday_learning_mix(),
            new_review_mix: config.inner.new_mix(),
            reviews_per_new_card: config.reviews_per_new_card(),
            rwkv_review_enabled: config.inner.rwkv_review_enabled,
            rwkv_review_instant_order_enabled: config.inner.rwkv_review_instant_order_enabled,
            rwkv_review_min_intervening_reviews: config.inner.rwkv_review_min_intervening_reviews,
            rwkv_review_min_elapsed_secs: config.inner.rwkv_review_min_elapsed_secs,
        })
        .unwrap_or_else(|| {
            // filtered decks do not space siblings
            QueueSortOptions {
                new_order: NewCardSortOrder::NoSort,
                ..Default::default()
            }
        })
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
    reviews_per_new_card: Option<u32>,
    reviews_done: usize,
    new_done: usize,
    progress: NewReviewMixProgress,
) -> Box<dyn ExactSizeIterator<Item = MainQueueEntry>> {
    let new_iter = new.into_iter().map(Into::into);

    match mode {
        ReviewMix::BeforeReviews => Box::new(SizedChain::new(new_iter, review_iter)),
        ReviewMix::AfterReviews => Box::new(SizedChain::new(review_iter, new_iter)),
        // kuma3: a fixed "Reviews per new card" spacing resumes from today's
        // counts; otherwise the fork's tracked progress keeps the even mix
        // (kept across RWKV queue rebuilds by invalidate_rwkv_review_queue)
        ReviewMix::MixWithReviews => Box::new(match reviews_per_new_card {
            Some(_) => Intersperser::resumed(
                review_iter,
                new_iter,
                reviews_per_new_card,
                reviews_done,
                new_done,
            ),
            None => Intersperser::with_progress(
                review_iter,
                new_iter,
                progress.review_answered,
                progress.new_answered,
            ),
        }),
    }
}

fn sort_learning(learning: Vec<DueCard>) -> VecDeque<LearningQueueEntry> {
    let mut entries: Vec<LearningQueueEntry> =
        learning.into_iter().map(LearningQueueEntry::from).collect();
    entries.sort_by(|a, b| a.cmp_by_reps_then_due(b));
    entries.into_iter().collect()
}

impl Collection {
    pub(crate) fn build_queues(&mut self, deck_id: DeckId) -> Result<CardQueues> {
        self.build_queues_with_current_card(deck_id, None)
    }

    pub(crate) fn build_queues_with_current_card(
        &mut self,
        deck_id: DeckId,
        current_card: Option<&Card>,
    ) -> Result<CardQueues> {
        let started = std::time::Instant::now();
        let mut queues = QueueBuilder::new(self, deck_id)?;
        self.storage
            .update_active_decks(&queues.context.root_deck)?;
        let current_card = current_card.filter(|card| {
            queues
                .context
                .deck_map
                .get(&card.deck_id)
                .is_some_and(|deck| deck_is_within_scope(deck, &queues.context.root_deck))
        });

        if let Some(card) = current_card {
            queues.pin_current_card(card)?;
        }
        let prepared = std::time::Instant::now();
        queues.gather_cards(self)?;
        let gathered = std::time::Instant::now();

        let mut queues = queues.build(self.learn_ahead_secs() as i64);
        if let Some(card) = current_card {
            queues.preserve_current_card(card.id)?;
        }

        tracing::debug!(
            prepared_ms = (prepared - started).as_secs_f64() * 1_000.0,
            gathered_ms = (gathered - prepared).as_secs_f64() * 1_000.0,
            finalized_ms = gathered.elapsed().as_secs_f64() * 1_000.0,
            "queue build profile"
        );

        Ok(queues)
    }
}

#[cfg(test)]
mod test {
    use std::collections::HashMap;
    use std::collections::HashSet;
    use std::hash::Hasher;

    use anki_proto::deck_config::deck_config::config::NewCardGatherPriority;
    use anki_proto::deck_config::deck_config::config::NewCardSortOrder;
    use fnv::FnvHasher;
    use fsrs::DEFAULT_PARAMETERS;

    use super::*;
    use crate::card::CardQueue;
    use crate::card::CardType;
    use crate::card::FsrsMemoryState;
    use crate::deckconfig::FsrsVersion;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;
    use crate::search::SortMode;

    impl Collection {
        fn add_review_log_today(
            &mut self,
            card_id: CardId,
            timing: SchedTimingToday,
        ) -> Result<()> {
            self.storage.add_revlog_entry(
                &RevlogEntry {
                    id: timing.now.as_millis().into(),
                    cid: card_id,
                    button_chosen: 3,
                    interval: 1,
                    review_kind: RevlogReviewKind::Review,
                    ..Default::default()
                },
                true,
            )?;
            Ok(())
        }

        fn set_deck_gather_order(&mut self, deck: &mut Deck, order: NewCardGatherPriority) {
            let mut conf = DeckConfig::default();
            conf.inner.new_card_gather_priority = order as i32;
            conf.inner.new_card_sort_order = NewCardSortOrder::NoSort as i32;
            self.add_or_update_deck_config(&mut conf).unwrap();
            deck.normal_mut().unwrap().config_id = conf.id.0;
            self.add_or_update_deck(deck).unwrap();
        }

        fn set_deck_new_limit(&mut self, deck: &mut Deck, new_limit: u32) {
            let mut conf = DeckConfig::default();
            conf.inner.new_per_day = new_limit;
            self.add_or_update_deck_config(&mut conf).unwrap();
            deck.normal_mut().unwrap().config_id = conf.id.0;
            self.add_or_update_deck(deck).unwrap();
        }

        fn set_deck_review_limit(&mut self, deck: DeckId, limit: u32) {
            let dcid = self.get_deck(deck).unwrap().unwrap().config_id().unwrap();
            let mut conf = self.get_deck_config(dcid, false).unwrap().unwrap();
            conf.inner.reviews_per_day = limit;
            self.add_or_update_deck_config(&mut conf).unwrap();
        }

        fn set_deck_fsrs7_defaults(&mut self, deck: DeckId) {
            let config_id = self.get_deck(deck).unwrap().unwrap().config_id().unwrap();
            let mut config = self.get_deck_config(config_id, false).unwrap().unwrap();
            config.inner.fsrs_version = FsrsVersion::Seven as i32;
            config.inner.fsrs_params_7 = DEFAULT_PARAMETERS.to_vec();
            self.add_or_update_deck_config(&mut config).unwrap();
        }

        fn queue_as_deck_and_template(&mut self, deck_id: DeckId) -> Vec<(DeckId, u16)> {
            self.build_queues(deck_id)
                .unwrap()
                .iter()
                .map(|entry| {
                    let card = self.storage.get_card(entry.card_id()).unwrap().unwrap();
                    (card.deck_id, card.template_idx)
                })
                .collect()
        }

        fn set_deck_review_order(&mut self, deck: &mut Deck, order: ReviewCardOrder) {
            let mut conf = DeckConfig::default();
            conf.inner.review_order = order as i32;
            self.add_or_update_deck_config(&mut conf).unwrap();
            deck.normal_mut().unwrap().config_id = conf.id.0;
            self.add_or_update_deck(deck).unwrap();
        }

        fn set_deck_rwkv_instant_order(&mut self, deck: &mut Deck, order: ReviewCardOrder) {
            let mut conf = DeckConfig::default();
            conf.inner.review_order = order as i32;
            conf.inner.rwkv_review_enabled = false;
            conf.inner.rwkv_review_instant_order_enabled = true;
            self.add_or_update_deck_config(&mut conf).unwrap();
            deck.normal_mut().unwrap().config_id = conf.id.0;
            self.add_or_update_deck(deck).unwrap();
        }

        fn set_deck_rwkv_review_order_with_desired_retention(
            &mut self,
            deck: &mut Deck,
            order: ReviewCardOrder,
            desired_retention: f32,
        ) {
            self.set_deck_rwkv_review_order_with_options(deck, order, desired_retention, false);
        }

        fn set_deck_rwkv_review_order_with_options(
            &mut self,
            deck: &mut Deck,
            order: ReviewCardOrder,
            desired_retention: f32,
            allow_same_day_review: bool,
        ) {
            let mut conf = DeckConfig::default();
            conf.inner.review_order = order as i32;
            conf.inner.rwkv_review_enabled = true;
            conf.inner.rwkv_review_instant_order_enabled = true;
            conf.inner.desired_retention = desired_retention;
            self.set_config_bool(
                BoolKey::FsrsShortTermWithStepsEnabled,
                allow_same_day_review,
                false,
            )
            .unwrap();
            conf.inner.rwkv_review_min_intervening_reviews = 0;
            conf.inner.rwkv_review_min_elapsed_secs = 0;
            self.add_or_update_deck_config(&mut conf).unwrap();
            deck.normal_mut().unwrap().config_id = conf.id.0;
            self.add_or_update_deck(deck).unwrap();
        }

        fn set_deck_rwkv_review_order_with_repeat_guards(
            &mut self,
            deck: &mut Deck,
            order: ReviewCardOrder,
            desired_retention: f32,
            min_intervening_reviews: u32,
            min_elapsed_secs: u32,
        ) {
            let mut conf = DeckConfig::default();
            conf.inner.review_order = order as i32;
            conf.inner.rwkv_review_enabled = true;
            conf.inner.rwkv_review_instant_order_enabled = true;
            conf.inner.desired_retention = desired_retention;
            self.set_config_bool(BoolKey::FsrsShortTermWithStepsEnabled, true, false)
                .unwrap();
            conf.inner.rwkv_review_min_intervening_reviews = min_intervening_reviews;
            conf.inner.rwkv_review_min_elapsed_secs = min_elapsed_secs;
            self.add_or_update_deck_config(&mut conf).unwrap();
            deck.normal_mut().unwrap().config_id = conf.id.0;
            self.add_or_update_deck(deck).unwrap();
        }

        fn set_deck_rwkv_minimum_reviews(&mut self, deck: DeckId, minimum: u32) {
            let config_id = self.get_deck(deck).unwrap().unwrap().config_id().unwrap();
            let mut config = self.get_deck_config(config_id, false).unwrap().unwrap();
            config.inner.rwkv_review_minimum_reviews_per_day = minimum;
            self.add_or_update_deck_config(&mut config).unwrap();
        }

        fn set_deck_same_day_reviews_ignore_review_limit(&mut self, deck: DeckId, enabled: bool) {
            let config_id = self.get_deck(deck).unwrap().unwrap().config_id().unwrap();
            let mut config = self.get_deck_config(config_id, false).unwrap().unwrap();
            config.inner.same_day_reviews_ignore_review_limit = enabled;
            self.add_or_update_deck_config(&mut config).unwrap();
        }

        fn queue_as_due_and_ivl(&mut self, deck_id: DeckId) -> Vec<(i32, u32)> {
            self.build_queues(deck_id)
                .unwrap()
                .iter()
                .map(|entry| {
                    let card = self.storage.get_card(entry.card_id()).unwrap().unwrap();
                    (card.due, card.interval)
                })
                .collect()
        }

        fn queue_as_ids(&mut self, deck_id: DeckId) -> Vec<CardId> {
            self.build_queues(deck_id)
                .unwrap()
                .iter()
                .map(|entry| entry.card_id())
                .collect()
        }

        fn queued_card_ids(&mut self, fetch_limit: usize) -> Result<Vec<CardId>> {
            Ok(self
                .get_queued_cards(fetch_limit, false, true)?
                .cards
                .into_iter()
                .map(|queued| queued.card.id)
                .collect())
        }
    }

    #[test]
    fn queued_cards_can_skip_scheduling_states() {
        let mut col = Collection::new();
        CardAdder::new().add(&mut col);

        let queued = col.get_queued_cards(1, false, true).unwrap();
        assert_eq!(queued.cards.len(), 1);
        assert!(queued.cards[0].states.is_none());

        let queued = col.get_queued_cards(1, false, false).unwrap();
        assert_eq!(queued.cards.len(), 1);
        assert!(queued.cards[0].states.is_some());
    }

    #[test]
    fn preserved_current_card_reuses_rebuilt_rwkv_queue() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_current_deck(deck.id)?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let current = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let next = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let last = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(current, 0.10), (next, 0.20), (last, 0.30)]),
        )?;
        assert_eq!(col.queued_card_ids(1)?, vec![current]);

        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(current, 0.80), (next, 0.10), (last, 0.20)]),
        )?;
        let counts = col.rebuild_queued_cards_preserving_current_card(current)?;
        assert!(counts.cards.is_empty());
        assert_eq!(
            (counts.new_count, counts.learning_count, counts.review_count),
            (0, 0, 3)
        );

        let cached = col.state.card_queues.as_ref().unwrap();
        let build_time = cached.build_time;
        assert_eq!(cached.shown_top_card, Some(current));
        assert_eq!(cached.main.front().unwrap().id, current);

        let restored = col.get_queued_cards(1, false, true)?;
        assert_eq!(restored.cards[0].card.id, current);
        assert_eq!(restored.review_count, 3);

        col.answer_good();
        let next_card = col.get_queued_cards(1, false, true)?;
        assert_eq!(next_card.cards[0].card.id, next);
        assert_eq!(
            col.state.card_queues.as_ref().unwrap().build_time,
            build_time
        );
        Ok(())
    }

    /// kuma3: RWKV drops the queues when its scores change (after every
    /// answer in an RWKV deck); the card on screen stays first, and Undo gets
    /// the undone card back instead of the new top card.
    #[test]
    fn shown_card_stays_first_through_rwkv_rebuilds_and_undo() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_current_deck(deck.id)?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);
        let timing = col.timing_today()?;
        let add = |col: &mut Collection| {
            add_memory_state_card(
                col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32,
                2 * 86_400,
                30.0,
            )
        };
        let (current, next, last) = (add(&mut col)?, add(&mut col)?, add(&mut col)?);
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(current, 0.10), (next, 0.20), (last, 0.30)]),
        )?;
        assert_eq!(col.queued_card_ids(1)?, vec![current]);

        // new scores would put `next` first: the card on screen stays
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(current, 0.80), (next, 0.10), (last, 0.20)]),
        )?;
        assert!(col.state.card_queues.is_none());
        assert_eq!(col.queued_card_ids(1)?, vec![current]);

        // answered: the scores decide again
        col.answer_good();
        assert_eq!(col.queued_card_ids(1)?, vec![next]);

        // Undo after the queues were dropped again shows the undone card
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(current, 0.80), (next, 0.10), (last, 0.20)]),
        )?;
        col.undo()?;
        assert_eq!(col.queued_card_ids(1)?, vec![current]);

        // RWKV dropped the queues between showing the card and its answer:
        // the answer leaves no queue entry, Undo still shows the card
        col.clear_study_queues();
        col.answer_good();
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(current, 0.80), (next, 0.10), (last, 0.20)]),
        )?;
        assert_eq!(col.queued_card_ids(1)?, vec![next]);
        col.undo()?;
        assert_eq!(col.queued_card_ids(1)?, vec![current]);
        Ok(())
    }

    #[test]
    fn preserved_current_card_outside_selected_deck_is_ignored() -> Result<()> {
        let mut col = Collection::new();
        let selected_deck = col.get_or_create_normal_deck("Selected")?;
        let other_deck = col.get_or_create_normal_deck("Other")?;
        col.set_current_deck(selected_deck.id)?;
        let selected_card = CardAdder::new().deck(selected_deck.id).add(&mut col)[0].id;
        let other_card = CardAdder::new().deck(other_deck.id).add(&mut col)[0].id;

        let counts = col.rebuild_queued_cards_preserving_current_card(other_card)?;

        assert_eq!(counts.new_count, 1);
        assert_eq!(col.queued_card_ids(10)?, vec![selected_card]);
        Ok(())
    }

    #[test]
    fn queue_tolerates_missing_intermediate_parent_deck() -> Result<()> {
        let mut col = Collection::new();
        let root = col.get_or_create_normal_deck("Root")?;
        let missing_parent = col.get_or_create_normal_deck("Root::Missing")?;
        let leaf = col.get_or_create_normal_deck("Root::Missing::Leaf")?;
        col.storage.remove_deck(missing_parent.id)?;
        col.set_current_deck(root.id)?;
        let card_id = CardAdder::new().deck(leaf.id).add(&mut col)[0].id;

        assert_eq!(col.queued_card_ids(10)?, vec![card_id]);
        Ok(())
    }

    #[test]
    fn preserved_current_card_applies_sibling_burying_first() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_current_deck(deck.id)?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);
        let config_id = deck.config_id().unwrap();
        let mut config = col.get_deck_config(config_id, false)?.unwrap();
        config.inner.bury_reviews = true;
        col.add_or_update_deck_config(&mut config)?;

        let siblings = CardAdder::new()
            .siblings(2)
            .deck(deck.id)
            .due_dates(["0", "0"])
            .add(&mut col);
        let current = siblings[0].id;
        let sibling = siblings[1].id;
        let unrelated = CardAdder::new()
            .deck(deck.id)
            .due_dates(["0"])
            .add(&mut col)[0]
            .id;
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(current, 0.10), (sibling, 0.20), (unrelated, 0.30)]),
        )?;
        assert_eq!(col.queued_card_ids(1)?, vec![current]);

        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(current, 0.80), (sibling, 0.10), (unrelated, 0.20)]),
        )?;
        let counts = col.rebuild_queued_cards_preserving_current_card(current)?;
        assert_eq!(counts.review_count, 2);
        assert_eq!(col.queued_card_ids(10)?, vec![current, unrelated]);
        Ok(())
    }

    #[test]
    fn preserved_current_new_card_reserves_review_capacity() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_current_deck(deck.id)?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);
        let config_id = deck.config_id().unwrap();
        let mut config = col.get_deck_config(config_id, false)?.unwrap();
        config.inner.new_mix = ReviewMix::BeforeReviews as i32;
        config.inner.new_per_day = 1;
        config.inner.reviews_per_day = 2;
        col.add_or_update_deck_config(&mut config)?;

        let current = CardAdder::new().deck(deck.id).add(&mut col)[0].id;
        let timing = col.timing_today()?;
        let first_review = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        let second_review = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(first_review, 0.95), (second_review, 0.95)]),
        )?;
        assert_eq!(col.queued_card_ids(1)?, vec![current]);

        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(first_review, 0.10), (second_review, 0.20)]),
        )?;
        let counts = col.rebuild_queued_cards_preserving_current_card(current)?;
        assert_eq!(
            (counts.new_count, counts.learning_count, counts.review_count),
            (1, 0, 1)
        );

        col.answer_good();
        let next_card = col.get_queued_cards(1, false, true)?;
        assert_eq!(next_card.cards[0].card.id, first_review);
        Ok(())
    }

    #[test]
    fn new_cards_stay_mixed_when_the_queue_is_rebuilt_after_every_answer() -> Result<()> {
        for (reviews_per_new_card, expected) in [(None, "RRRNRRRNRR"), (Some(3), "RRRNRRRNRR")] {
            let mut col = Collection::new();
            let mut deck = col.get_or_create_normal_deck("Default")?;
            col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::Day);
            for _ in 0..8 {
                CardAdder::new().due_dates(["0"]).add(&mut col);
            }
            CardAdder::new().add(&mut col);
            CardAdder::new().add(&mut col);
            if let Some(n) = reviews_per_new_card {
                let mut config = col.get_deck_config(DeckConfigId(1), false)?.unwrap();
                config.inner.other = serde_json::to_vec(
                    &serde_json::json!({"kuma3.deckoptions": {"reviews_per_new_card": n}}),
                )?;
                col.add_or_update_deck_config(&mut config)?;
            }
            let mut order = String::new();
            while let Some(card) = col.get_queued_cards(1, false, true)?.cards.first() {
                order.push(if card.card.queue == CardQueue::New {
                    'N'
                } else {
                    'R'
                });
                col.answer_easy();
                // RWKV-Instant rebuilds the queue after most answers
                col.invalidate_rwkv_review_queue();
            }
            assert_eq!(order, expected, "{reviews_per_new_card:?}");
        }
        Ok(())
    }

    #[test]
    fn should_build_empty_queue_if_limit_is_reached() {
        let mut col = Collection::new();
        CardAdder::new().due_dates(["0"]).add(&mut col);
        col.set_deck_review_limit(DeckId(1), 0);
        assert_eq!(col.queue_as_deck_and_template(DeckId(1)), vec![]);
    }

    #[test]
    fn new_queue_building() -> Result<()> {
        let mut col = Collection::new();

        // parent
        // ┣━━child━━grandchild
        // ┗━━child_2
        let mut parent = DeckAdder::new("parent").add(&mut col);
        let mut child = DeckAdder::new("parent::child").add(&mut col);
        let child_2 = DeckAdder::new("parent::child_2").add(&mut col);
        let grandchild = DeckAdder::new("parent::child::grandchild").add(&mut col);

        // add 2 new cards to each deck
        for deck in [&parent, &child, &child_2, &grandchild] {
            CardAdder::new().siblings(2).deck(deck.id).add(&mut col);
        }

        // set child's new limit to 3, which should affect grandchild
        col.set_deck_new_limit(&mut child, 3);

        // depth-first tree order
        col.set_deck_gather_order(&mut parent, NewCardGatherPriority::Deck);
        let cards = vec![
            (parent.id, 0),
            (parent.id, 1),
            (child.id, 0),
            (child.id, 1),
            (grandchild.id, 0),
            (child_2.id, 0),
            (child_2.id, 1),
        ];
        assert_eq!(col.queue_as_deck_and_template(parent.id), cards);

        // insertion order
        col.set_deck_gather_order(&mut parent, NewCardGatherPriority::LowestPosition);
        let cards = vec![
            (parent.id, 0),
            (parent.id, 1),
            (child.id, 0),
            (child.id, 1),
            (child_2.id, 0),
            (child_2.id, 1),
            (grandchild.id, 0),
        ];
        assert_eq!(col.queue_as_deck_and_template(parent.id), cards);

        // inverted insertion order, but sibling order is preserved
        col.set_deck_gather_order(&mut parent, NewCardGatherPriority::HighestPosition);
        let cards = vec![
            (grandchild.id, 0),
            (grandchild.id, 1),
            (child_2.id, 0),
            (child_2.id, 1),
            (child.id, 0),
            (parent.id, 0),
            (parent.id, 1),
        ];
        assert_eq!(col.queue_as_deck_and_template(parent.id), cards);

        Ok(())
    }

    #[test]
    fn review_queue_building() -> Result<()> {
        let mut col = Collection::new();

        let mut deck = col.get_or_create_normal_deck("Default").unwrap();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut cards = vec![];

        // relative overdueness
        let expected_queue = vec![
            (-150, 1),
            (-100, 1),
            (-50, 1),
            (-150, 5),
            (-100, 5),
            (-50, 5),
            (-150, 20),
            (-150, 20),
            (-100, 20),
            (-50, 20),
            (-150, 100),
            (-100, 100),
            (-50, 100),
            (0, 1),
            (0, 5),
            (0, 20),
            (0, 100),
        ];
        for t in expected_queue.iter() {
            let mut note = nt.new_note();
            note.set_field(0, "foo")?;
            note.id.0 = 0;
            col.add_note(&mut note, deck.id)?;
            let mut card = col.storage.get_card_by_ordinal(note.id, 0)?.unwrap();
            card.interval = t.1;
            card.due = t.0;
            card.ctype = CardType::Review;
            card.queue = CardQueue::Review;
            cards.push(card);
        }
        col.update_cards_maybe_undoable(cards, false)?;
        col.set_deck_review_order(&mut deck, ReviewCardOrder::RelativeOverdueness);
        assert_eq!(col.queue_as_due_and_ivl(deck.id), expected_queue);

        Ok(())
    }

    #[test]
    fn fsrs_retrievability_order_ignores_stale_decay() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_review_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);
        col.set_deck_fsrs7_defaults(deck.id);

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note1 = nt.new_note();
        let mut note2 = nt.new_note();
        col.add_note(&mut note1, deck.id)?;
        col.add_note(&mut note2, deck.id)?;

        let mut ids = col.search_cards("", SortMode::NoOrder)?;
        ids.sort();
        let timing = col.timing_today()?;
        let mut card1 = col.storage.get_card(ids[0])?.unwrap();
        let mut card2 = col.storage.get_card(ids[1])?.unwrap();
        for card in [&mut card1, &mut card2] {
            card.ctype = CardType::Review;
            card.queue = CardQueue::Review;
            card.due = 0;
            card.interval = 20;
            card.memory_state = Some(FsrsMemoryState {
                stability: 30.0,
                stability_internal: 30.0,
                stability_fast: None,
                difficulty: 5.0,
            });
            card.desired_retention = Some(0.8);
            card.last_review_time = Some(timing.now.adding_secs(-20 * 86_400));
        }
        card1.decay = Some(0.1);
        card2.decay = Some(2.0);
        col.storage.update_card(&card1)?;
        col.storage.update_card(&card2)?;

        // Exact FSRS ordering should tie on identical state/elapsed/DR and use
        // the normal id/mtime hash tiebreaker, not stale per-card decay.
        let first_queue = col.queue_as_ids(deck.id);
        assert_eq!(first_queue.len(), 2);
        assert!(first_queue.contains(&card1.id));
        assert!(first_queue.contains(&card2.id));
        assert_eq!(col.queue_as_ids(deck.id), first_queue);
        Ok(())
    }

    fn add_memory_state_card(
        col: &mut Collection,
        deck_id: DeckId,
        queue: CardQueue,
        ctype: CardType,
        due: i32,
        elapsed_secs: i64,
        stability: f32,
    ) -> Result<CardId> {
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "foo")?;
        col.add_note(&mut note, deck_id)?;
        let mut card = col.storage.get_card_by_ordinal(note.id, 0)?.unwrap();
        card.ctype = ctype;
        card.queue = queue;
        card.due = due;
        card.interval = 1;
        card.memory_state = Some(FsrsMemoryState {
            stability,
            stability_internal: stability,
            stability_fast: None,
            difficulty: 5.0,
        });
        card.desired_retention = Some(0.9);
        card.last_review_time = Some(TimestampSecs::now().adding_secs(-elapsed_secs));
        col.storage.update_card(&card)?;
        Ok(card.id)
    }

    fn fnvhash_card_and_mod(card: &Card) -> i64 {
        let mut hasher = FnvHasher::default();
        hasher.write_i64(card.id.0);
        hasher.write_i64(card.mtime.0);
        hasher.finish() as i64
    }

    #[test]
    fn fsrs_retrievability_order_interleaves_due_non_new_queues() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_review_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let review = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let day_learning = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::DayLearn,
            CardType::Relearn,
            timing.days_elapsed as i32,
            4 * 86_400,
            30.0,
        )?;
        let intraday_learning = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Learn,
            CardType::Relearn,
            (timing.now.0 - 1) as i32,
            6 * 86_400,
            30.0,
        )?;

        assert_eq!(
            col.queue_as_ids(deck.id),
            vec![intraday_learning, day_learning, review]
        );
        assert_eq!(col.counts(), [0, 2, 1]);
        Ok(())
    }

    #[test]
    fn fsrs_descending_retrievability_order_interleaves_due_non_new_queues() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_review_order(&mut deck, ReviewCardOrder::RetrievabilityDescending);

        let timing = col.timing_today()?;
        let review = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let day_learning = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::DayLearn,
            CardType::Relearn,
            timing.days_elapsed as i32,
            4 * 86_400,
            30.0,
        )?;
        let intraday_learning = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Learn,
            CardType::Relearn,
            (timing.now.0 - 1) as i32,
            6 * 86_400,
            30.0,
        )?;

        assert_eq!(
            col.queue_as_ids(deck.id),
            vec![review, day_learning, intraday_learning]
        );
        assert_eq!(col.counts(), [0, 2, 1]);
        Ok(())
    }

    #[test]
    fn fsrs_review_order_respects_learning_priorities() -> Result<()> {
        for version in [FsrsVersion::Six, FsrsVersion::Seven] {
            for order in [
                ReviewCardOrder::RetrievabilityAscending,
                ReviewCardOrder::RetrievabilityDescending,
                ReviewCardOrder::RelativeOverdueness,
            ] {
                for (mix, intraday_mix) in [
                    (ReviewMix::BeforeReviews, ReviewMix::MixWithReviews),
                    (ReviewMix::MixWithReviews, ReviewMix::MixWithReviews),
                    (ReviewMix::AfterReviews, ReviewMix::MixWithReviews),
                    (ReviewMix::BeforeReviews, ReviewMix::BeforeReviews),
                    (ReviewMix::MixWithReviews, ReviewMix::BeforeReviews),
                    (ReviewMix::AfterReviews, ReviewMix::BeforeReviews),
                    (ReviewMix::BeforeReviews, ReviewMix::AfterReviews),
                    (ReviewMix::MixWithReviews, ReviewMix::AfterReviews),
                    (ReviewMix::AfterReviews, ReviewMix::AfterReviews),
                ] {
                    let mut col = Collection::new();
                    col.set_config_bool(BoolKey::Fsrs, true, true)?;
                    let mut deck = col.get_or_create_normal_deck("Default")?;
                    col.set_deck_review_order(&mut deck, order);
                    let mut config = col
                        .get_deck_config(deck.config_id().unwrap(), false)?
                        .unwrap();
                    config.inner.fsrs_version = version as i32;
                    config.inner.interday_learning_mix = mix as i32;
                    config.inner.other = serde_json::to_vec(&serde_json::json!({
                        "jschoreels.deckoptions": {
                            "intraday_learning_mix": intraday_mix as i32,
                        },
                    }))?;
                    col.add_or_update_deck_config(&mut config)?;
                    let timing = col.timing_today()?;
                    let mut ranked_cards = Vec::new();
                    for (queue, ctype, elapsed_days) in [
                        (CardQueue::DayLearn, CardType::Learn, 6),
                        (CardQueue::Review, CardType::Review, 5),
                        (CardQueue::Learn, CardType::Relearn, 4),
                        (CardQueue::Learn, CardType::Learn, 3),
                        (CardQueue::Review, CardType::Review, 2),
                        (CardQueue::DayLearn, CardType::Relearn, 1),
                    ] {
                        let due = if queue == CardQueue::Learn {
                            (timing.now.0 - 1) as i32
                        } else {
                            timing.days_elapsed as i32
                        };
                        let id = add_memory_state_card(
                            &mut col,
                            deck.id,
                            queue,
                            ctype,
                            due,
                            elapsed_days * 86_400,
                            30.0,
                        )?;
                        ranked_cards.push((id, queue));
                    }
                    if order == ReviewCardOrder::RetrievabilityDescending {
                        ranked_cards.reverse();
                    }
                    let interday = ranked_cards
                        .iter()
                        .filter(|(_, queue)| *queue == CardQueue::DayLearn)
                        .map(|(id, _)| *id);
                    let other = ranked_cards
                        .iter()
                        .filter(|(_, queue)| {
                            *queue != CardQueue::DayLearn
                                && (intraday_mix == ReviewMix::MixWithReviews
                                    || *queue != CardQueue::Learn)
                        })
                        .map(|(id, _)| *id);
                    let mut expected = match mix {
                        ReviewMix::BeforeReviews => interday.chain(other).collect::<Vec<_>>(),
                        ReviewMix::AfterReviews => other.chain(interday).collect(),
                        ReviewMix::MixWithReviews => ranked_cards
                            .iter()
                            .filter(|(_, queue)| {
                                intraday_mix == ReviewMix::MixWithReviews
                                    || *queue != CardQueue::Learn
                            })
                            .map(|(id, _)| *id)
                            .collect(),
                    };
                    let intraday = ranked_cards
                        .iter()
                        .filter(|(_, queue)| *queue == CardQueue::Learn)
                        .map(|(id, _)| *id);
                    expected = match intraday_mix {
                        ReviewMix::BeforeReviews => intraday.chain(expected).collect(),
                        ReviewMix::AfterReviews => expected.into_iter().chain(intraday).collect(),
                        ReviewMix::MixWithReviews => expected,
                    };
                    assert_eq!(
                        col.queue_as_ids(deck.id),
                        expected,
                        "{version:?}, {order:?}, interday={mix:?}, intraday={intraday_mix:?}"
                    );
                    assert_eq!(col.counts(), [0, 4, 2]);

                    for current_queue in [CardQueue::DayLearn, CardQueue::Learn] {
                        let current_id = ranked_cards
                            .iter()
                            .find(|(_, queue)| *queue == current_queue)
                            .unwrap()
                            .0;
                        let current = col.storage.get_card(current_id)?.unwrap();
                        let mut queues =
                            col.build_queues_with_current_card(deck.id, Some(&current))?;
                        let preserved_order = std::iter::once(current_id)
                            .chain(expected.iter().copied().filter(|id| *id != current_id))
                            .collect::<Vec<_>>();
                        assert_eq!(
                            queues
                                .iter()
                                .map(|entry| entry.card_id())
                                .collect::<Vec<_>>(),
                            preserved_order,
                            "preserved current card: {version:?}, {order:?}, interday={mix:?}, intraday={intraday_mix:?}"
                        );
                        let counts = queues.counts();
                        assert_eq!([counts.new, counts.learning, counts.review], [0, 4, 2]);
                    }
                }
            }
        }
        Ok(())
    }

    #[test]
    fn fsrs_learning_priorities_preserve_review_limit_selection() -> Result<()> {
        for (mix, intraday_mix) in [
            (ReviewMix::BeforeReviews, ReviewMix::MixWithReviews),
            (ReviewMix::MixWithReviews, ReviewMix::MixWithReviews),
            (ReviewMix::AfterReviews, ReviewMix::MixWithReviews),
            (ReviewMix::BeforeReviews, ReviewMix::BeforeReviews),
            (ReviewMix::MixWithReviews, ReviewMix::BeforeReviews),
            (ReviewMix::AfterReviews, ReviewMix::BeforeReviews),
            (ReviewMix::BeforeReviews, ReviewMix::AfterReviews),
            (ReviewMix::MixWithReviews, ReviewMix::AfterReviews),
            (ReviewMix::AfterReviews, ReviewMix::AfterReviews),
        ] {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::Fsrs, true, true)?;
            let mut deck = col.get_or_create_normal_deck("Default")?;
            col.set_deck_review_order(&mut deck, ReviewCardOrder::RetrievabilityDescending);
            col.set_deck_review_limit(deck.id, 1);
            let mut config = col
                .get_deck_config(deck.config_id().unwrap(), false)?
                .unwrap();
            config.inner.interday_learning_mix = mix as i32;
            config.inner.other = serde_json::to_vec(&serde_json::json!({
                "jschoreels.deckoptions": {
                    "intraday_learning_mix": intraday_mix as i32,
                },
            }))?;
            col.add_or_update_deck_config(&mut config)?;
            let timing = col.timing_today()?;
            let review = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32,
                86_400,
                30.0,
            )?;
            add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::DayLearn,
                CardType::Learn,
                timing.days_elapsed as i32,
                2 * 86_400,
                30.0,
            )?;
            let intraday = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Learn,
                CardType::Relearn,
                (timing.now.0 - 1) as i32,
                3 * 86_400,
                30.0,
            )?;
            let expected = if intraday_mix == ReviewMix::BeforeReviews {
                vec![intraday, review]
            } else {
                vec![review, intraday]
            };
            assert_eq!(
                col.queue_as_ids(deck.id),
                expected,
                "interday={mix:?}, intraday={intraday_mix:?}"
            );
            assert_eq!(col.counts(), [0, 1, 1]);
        }
        Ok(())
    }

    #[test]
    fn fsrs_retrievability_order_uses_both_stabilities_and_difficulty() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_review_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);
        col.set_deck_fsrs7_defaults(deck.id);

        let timing = col.timing_today()?;
        let first = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            20 * 86_400,
            10.0,
        )?;
        let second = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            20 * 86_400,
            10.0,
        )?;

        let first_card = col.storage.get_card(first)?.unwrap();
        let second_card = col.storage.get_card(second)?.unwrap();
        let state_a = FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: Some(20.0),
            difficulty: 5.0,
        };
        let state_b = FsrsMemoryState {
            stability: 10.0,
            stability_internal: 10.0,
            stability_fast: Some(5.0),
            difficulty: 8.0,
        };
        let key_a = col.fsrs_current_retrievability_for_card_state(first, state_a, 20.0)?;
        let key_b = col.fsrs_current_retrievability_for_card_state(first, state_b, 20.0)?;
        assert_ne!(key_a, key_b);
        let (low_state, high_state) = if key_a < key_b {
            (state_a, state_b)
        } else {
            (state_b, state_a)
        };

        // Put the lower-R state second in the scalar helper's hash tie order,
        // so this test would fail if only slow stability were considered.
        let (high_r_id, low_r_id) =
            if fnvhash_card_and_mod(&first_card) < fnvhash_card_and_mod(&second_card) {
                (first, second)
            } else {
                (second, first)
            };

        let mut high_r = col.storage.get_card(high_r_id)?.unwrap();
        high_r.memory_state = Some(high_state);
        col.storage.update_card(&high_r)?;

        let mut low_r = col.storage.get_card(low_r_id)?.unwrap();
        low_r.memory_state = Some(low_state);
        col.storage.update_card(&low_r)?;

        let high_key = col.fsrs_current_retrievability_for_card_state(
            high_r.id,
            high_r.memory_state.unwrap(),
            20.0,
        )?;
        let low_key = col.fsrs_current_retrievability_for_card_state(
            low_r.id,
            low_r.memory_state.unwrap(),
            20.0,
        )?;
        assert!(low_key < high_key);
        assert_eq!(col.queue_as_ids(deck.id), vec![low_r_id, high_r_id]);
        Ok(())
    }

    #[test]
    fn fsrs_batch_queue_order_matches_exact_scores_across_presets() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_review_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);
        col.set_deck_fsrs7_defaults(deck.id);
        let mut child = col.get_or_create_normal_deck("Default::Child")?;
        child.normal_mut()?.desired_retention = Some(0.95);
        col.add_or_update_deck(&mut child)?;
        let mut home = col.get_or_create_normal_deck("Home")?;
        home.normal_mut()?.desired_retention = Some(0.8);
        col.add_or_update_deck(&mut home)?;
        let timing = col.timing_today()?;
        assert_eq!(timing.days_elapsed, 0);
        // Exceed the preset resolver's small-batch threshold, and mix model
        // families so accidentally sharing a model across presets changes order.
        for index in 0..140 {
            let id = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32,
                (index % 7 + 1) * 86_400,
                30.0,
            )?;
            let mut card = col.storage.get_card(id)?.unwrap();
            if index % 4 == 1 {
                card.deck_id = child.id;
            } else if index % 4 == 3 {
                card.original_deck_id = home.id;
                card.original_due = card.due;
            }
            if index == 0 {
                card.memory_state = None;
            } else {
                let state = card.memory_state.as_mut().unwrap();
                state.stability_fast = Some((index % 5 + 1) as f32);
                state.difficulty = (index % 9 + 1) as f32;
            }
            card.desired_retention = (index % 3 == 0).then_some(0.8);
            col.update_cards_maybe_undoable(vec![card.clone()], false)?;
            if index % 2 == 0 {
                col.add_tags_to_notes(&[card.note_id], "legacy-model")?;
            }
        }
        col.set_config(
            "fsrsPresetOverlay",
            &serde_json::json!({
                "presets": [{
                    "id": "addon:test:legacy", "name": "Legacy", "fsrs_version": "six",
                    "params": fsrs::FSRS6_DEFAULT_PARAMETERS, "desired_retention": 0.85,
                    "historical_retention": 0.9, "ignore_revlogs_before_date": ""
                }],
                "rules": [{"search": "tag:legacy-model", "preset_id": "addon:test:legacy"}]
            }),
        )?;
        let cards = col.storage.get_all_cards();
        for order in [
            ReviewCardOrder::RetrievabilityAscending,
            ReviewCardOrder::RetrievabilityDescending,
            ReviewCardOrder::RelativeOverdueness,
        ] {
            let mut config = col
                .get_deck_config(deck.config_id().unwrap(), false)?
                .unwrap();
            config.inner.review_order = order as i32;
            col.add_or_update_deck_config(&mut config)?;
            let mut builder = QueueBuilder::new(&mut col, deck.id)?;
            builder.context.timing = timing;
            col.storage
                .update_active_decks(&builder.context.root_deck)?;
            let mut expected = Vec::new();
            for card in &cards {
                let key = if let Some(state) = card.memory_state {
                    let elapsed =
                        (timing.now.0 - card.last_review_time.unwrap().0) as f32 / 86_400.0;
                    if order == ReviewCardOrder::RelativeOverdueness {
                        col.fsrs_relative_overdueness_for_card_state(card, state, elapsed)?
                    } else {
                        col.fsrs_current_retrievability_for_card_state(card.id, state, elapsed)?
                    }
                } else {
                    // Due on collection day zero: the legacy elapsed-day
                    // calculation clamps a review before creation to zero.
                    -0.001
                };
                expected.push((card.id, key, fnvhash_card_and_mod(card)));
            }
            expected.sort_by(|a, b| {
                let order_by_key = a.1.total_cmp(&b.1);
                let order_by_key = if order == ReviewCardOrder::RetrievabilityDescending {
                    order_by_key.reverse()
                } else {
                    order_by_key
                };
                order_by_key
                    .then_with(|| a.2.cmp(&b.2))
                    .then_with(|| a.0.cmp(&b.0))
            });
            // Exercise uncached overlay matching in the batched path.
            col.state.fsrs_preset_overlay_cache = None;
            builder.gather_cards(&mut col)?;
            assert_eq!(
                builder
                    .r_sorted_non_new
                    .iter()
                    .map(|card| card.id)
                    .collect::<Vec<_>>(),
                expected
                    .into_iter()
                    .map(|entry| entry.0)
                    .collect::<Vec<_>>(),
                "{order:?}"
            );
        }
        Ok(())
    }

    #[test]
    fn fsrs_relative_overdueness_uses_the_card_target_interval() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_review_order(&mut deck, ReviewCardOrder::RelativeOverdueness);
        col.set_deck_fsrs7_defaults(deck.id);

        let timing = col.timing_today()?;
        let first = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            20 * 86_400,
            10.0,
        )?;
        let second = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            20 * 86_400,
            10.0,
        )?;
        let first_card = col.storage.get_card(first)?.unwrap();
        let second_card = col.storage.get_card(second)?.unwrap();
        let (low_hash_id, high_hash_id) =
            if fnvhash_card_and_mod(&first_card) < fnvhash_card_and_mod(&second_card) {
                (first, second)
            } else {
                (second, first)
            };

        let state = first_card.memory_state.unwrap();
        let mut target_a_card = first_card.clone();
        target_a_card.desired_retention = Some(0.8);
        let target_a_key =
            col.fsrs_relative_overdueness_for_card_state(&target_a_card, state, 20.0)?;
        let mut target_b_card = first_card;
        target_b_card.desired_retention = Some(0.95);
        let target_b_key =
            col.fsrs_relative_overdueness_for_card_state(&target_b_card, state, 20.0)?;
        assert_ne!(target_a_key, target_b_key);
        let (lower_key_target, higher_key_target) = if target_a_key < target_b_key {
            (0.8, 0.95)
        } else {
            (0.95, 0.8)
        };

        // Exact R is identical for both cards, so assign the lower relative-
        // overdueness key against the normal hash order to catch an R-only sort.
        let mut low_hash_card = col.storage.get_card(low_hash_id)?.unwrap();
        low_hash_card.desired_retention = Some(higher_key_target);
        col.storage.update_card(&low_hash_card)?;
        let mut high_hash_card = col.storage.get_card(high_hash_id)?.unwrap();
        high_hash_card.desired_retention = Some(lower_key_target);
        col.storage.update_card(&high_hash_card)?;

        assert_eq!(col.queue_as_ids(deck.id), vec![high_hash_id, low_hash_id]);
        Ok(())
    }

    #[test]
    fn fsrs_retrievability_order_applies_limits_after_sorting_filtered_child_cards() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;

        let mut parent = DeckAdder::new("Parent").add(&mut col);
        let study = DeckAdder::new("Parent::Study").add(&mut col);
        let filtered = DeckAdder::new("Parent::Filtered")
            .filtered(true)
            .add(&mut col);
        col.set_deck_review_order(&mut parent, ReviewCardOrder::RetrievabilityAscending);
        col.set_deck_review_limit(parent.id, 1);

        let timing = col.timing_today()?;
        let lower_retrievability_card = add_memory_state_card(
            &mut col,
            study.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            20 * 86_400,
            30.0,
        )?;
        let filtered_card = add_memory_state_card(
            &mut col,
            study.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            86_400,
            30.0,
        )?;

        let mut card = col.storage.get_card(filtered_card)?.unwrap();
        card.original_deck_id = card.deck_id;
        card.deck_id = filtered.id;
        card.original_due = card.due;
        card.due = -100_000;
        col.storage.update_card(&card)?;

        assert_eq!(col.queue_as_ids(parent.id), vec![lower_retrievability_card]);
        Ok(())
    }

    #[test]
    fn fsrs_retrievability_order_uses_actual_r_across_child_decks() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;

        let mut parent = DeckAdder::new("Parent").add(&mut col);
        let low_r_deck = DeckAdder::new("Parent::LowR").add(&mut col);
        let high_r_deck = DeckAdder::new("Parent::HighR").add(&mut col);
        col.set_deck_review_order(&mut parent, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let low_r_card = add_memory_state_card(
            &mut col,
            low_r_deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            20 * 86_400,
            30.0,
        )?;
        let high_r_card = add_memory_state_card(
            &mut col,
            high_r_deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            86_400,
            30.0,
        )?;

        let mut low_r = col.storage.get_card(low_r_card)?.unwrap();
        low_r.desired_retention = Some(0.1);
        col.storage.update_card(&low_r)?;
        let mut high_r = col.storage.get_card(high_r_card)?.unwrap();
        high_r.desired_retention = Some(0.9999);
        col.storage.update_card(&high_r)?;

        let low_retrievability =
            col.fsrs_current_retrievability_for_card(low_r_card, 30.0, 20.0)?;
        let high_retrievability =
            col.fsrs_current_retrievability_for_card(high_r_card, 30.0, 1.0)?;
        assert!(low_retrievability < high_retrievability);
        assert_eq!(col.queue_as_ids(parent.id), vec![low_r_card, high_r_card]);
        Ok(())
    }

    #[test]
    fn fsrs_review_orders_respect_learn_ahead_after_due_and_new_cards() -> Result<()> {
        for version in [FsrsVersion::Six, FsrsVersion::Seven] {
            for order in [
                ReviewCardOrder::RetrievabilityAscending,
                ReviewCardOrder::RetrievabilityDescending,
                ReviewCardOrder::RelativeOverdueness,
            ] {
                for intraday_mix in [
                    ReviewMix::BeforeReviews,
                    ReviewMix::MixWithReviews,
                    ReviewMix::AfterReviews,
                ] {
                    let mut col = Collection::new();
                    col.set_config_bool(BoolKey::Fsrs, true, true)?;
                    let mut deck = col.get_or_create_normal_deck("Default")?;
                    col.set_deck_review_order(&mut deck, order);
                    let mut config = col
                        .get_deck_config(deck.config_id().unwrap(), false)?
                        .unwrap();
                    config.inner.fsrs_version = version as i32;
                    config.inner.new_mix = ReviewMix::AfterReviews as i32;
                    config.inner.other = serde_json::to_vec(&serde_json::json!({
                        "jschoreels.deckoptions": {
                            "intraday_learning_mix": intraday_mix as i32,
                        },
                    }))?;
                    col.add_or_update_deck_config(&mut config)?;
                    let timing = col.timing_today()?;
                    let review = add_memory_state_card(
                        &mut col,
                        deck.id,
                        CardQueue::Review,
                        CardType::Review,
                        timing.days_elapsed as i32,
                        2 * 86_400,
                        30.0,
                    )?;
                    let new_card = CardAdder::new().deck(deck.id).add(&mut col)[0].id;
                    let relearning = add_memory_state_card(
                        &mut col,
                        deck.id,
                        CardQueue::Learn,
                        CardType::Relearn,
                        (timing.now.0 + 60) as i32,
                        6 * 86_400,
                        30.0,
                    )?;
                    let learning = add_memory_state_card(
                        &mut col,
                        deck.id,
                        CardQueue::Learn,
                        CardType::Learn,
                        (timing.now.0 + 90) as i32,
                        86_400,
                        30.0,
                    )?;

                    for (ahead_secs, early) in [(0, false), (30, false), (240 * 60, true)] {
                        col.set_learn_ahead_secs(ahead_secs)?;
                        let mut builder = QueueBuilder::new(&mut col, deck.id)?;
                        col.storage
                            .update_active_decks(&builder.context.root_deck)?;
                        // Keep both future cards intraday, even just before rollover.
                        builder.context.timing.next_day_at = timing.now.adding_secs(86_400);
                        builder.gather_cards(&mut col)?;
                        let mut queues = builder.build(ahead_secs.into());
                        let expected = if early {
                            vec![review, new_card, relearning, learning]
                        } else {
                            vec![review, new_card]
                        };
                        assert_eq!(
                            queues
                                .iter()
                                .map(|entry| entry.card_id())
                                .collect::<Vec<_>>(),
                            expected,
                            "{version:?}, {order:?}, {intraday_mix:?}, ahead={ahead_secs}"
                        );
                        let counts = queues.counts();
                        assert_eq!(
                            [counts.new, counts.learning, counts.review],
                            [1, if early { 2 } else { 0 }, 1],
                            "{version:?}, {order:?}, {intraday_mix:?}, ahead={ahead_secs}"
                        );
                    }
                }
            }
        }
        Ok(())
    }

    #[test]
    fn fsrs_learn_ahead_refreshes_empty_queue_counts() -> Result<()> {
        for order in [
            ReviewCardOrder::RetrievabilityAscending,
            ReviewCardOrder::RetrievabilityDescending,
            ReviewCardOrder::RelativeOverdueness,
        ] {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::Fsrs, true, true)?;
            let mut deck = col.get_or_create_normal_deck("Default")?;
            col.set_deck_review_order(&mut deck, order);
            let now = TimestampSecs::now();
            let learning = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Learn,
                CardType::Learn,
                now.adding_secs(60).0 as i32,
                60,
                30.0,
            )?;
            let mut builder = QueueBuilder::new(&mut col, deck.id)?;
            col.storage
                .update_active_decks(&builder.context.root_deck)?;
            builder.context.timing.next_day_at = now.adding_secs(86_400);
            builder.gather_cards(&mut col)?;
            let mut queues = builder.build(0);
            assert_eq!(queues.counts.learning, 0);

            let due = queues.intraday_learning[0].due;
            queues.learn_ahead_secs = 30;
            queues.current_learning_cutoff = due.adding_secs(-31);
            assert!(queues.iter().next().is_none());
            queues.current_learning_cutoff = due.adding_secs(-30);
            assert_eq!(queues.iter().next().unwrap().card_id(), learning);

            // Simulate an empty queue whose cutoff predates the card entering
            // the learn-ahead window. Refreshing counts must also make it selectable.
            queues.current_learning_cutoff = now.adding_secs(-120);
            queues.learn_ahead_secs = 120;
            assert_eq!(queues.counts().learning, 1);
            assert_eq!(queues.iter().next().unwrap().card_id(), learning);
        }
        Ok(())
    }

    #[test]
    fn fsrs_learn_ahead_survives_answers_and_undo_redo() -> Result<()> {
        for order in [
            ReviewCardOrder::RetrievabilityAscending,
            ReviewCardOrder::RetrievabilityDescending,
            ReviewCardOrder::RelativeOverdueness,
        ] {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::Fsrs, true, true)?;
            col.set_learn_ahead_secs(240 * 60)?;
            let mut deck = col.get_or_create_normal_deck("Default")?;
            col.set_deck_review_order(&mut deck, order);
            col.set_deck_fsrs7_defaults(deck.id);
            let card = CardAdder::new().deck(deck.id).add(&mut col)[0].id;

            assert_eq!(col.answer_again().card_id, card);
            assert_eq!(col.counts(), [0, 1, 0]);
            assert_eq!(col.get_next_card()?.unwrap().card.id, card);
            assert_eq!(col.answer_again().card_id, card);
            assert_eq!(col.counts(), [0, 1, 0]);
            assert_eq!(col.get_next_card()?.unwrap().card.reps, 2);

            col.undo()?;
            assert_eq!(col.counts(), [0, 1, 0]);
            let restored = col.get_next_card()?.unwrap().card;
            assert_eq!((restored.id, restored.reps), (card, 1));
            col.redo()?;
            assert_eq!(col.counts(), [0, 1, 0]);
            let restored = col.get_next_card()?.unwrap().card;
            assert_eq!((restored.id, restored.reps), (card, 2));
        }
        Ok(())
    }

    #[test]
    fn fsrs_retrievability_order_preserves_new_card_mix() -> Result<()> {
        for (new_mix, intraday_mix) in [
            (ReviewMix::BeforeReviews, ReviewMix::BeforeReviews),
            (ReviewMix::AfterReviews, ReviewMix::BeforeReviews),
            (ReviewMix::BeforeReviews, ReviewMix::AfterReviews),
            (ReviewMix::AfterReviews, ReviewMix::AfterReviews),
            (ReviewMix::BeforeReviews, ReviewMix::MixWithReviews),
            (ReviewMix::AfterReviews, ReviewMix::MixWithReviews),
        ] {
            for interday_mix in [
                ReviewMix::BeforeReviews,
                ReviewMix::MixWithReviews,
                ReviewMix::AfterReviews,
            ] {
                let mut col = Collection::new();
                col.set_config_bool(BoolKey::Fsrs, true, true)?;
                let mut deck = col.get_or_create_normal_deck("Default")?;
                col.set_deck_review_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);
                let deck_config_id = deck.config_id().unwrap();
                let mut config = col.get_deck_config(deck_config_id, false)?.unwrap();
                config.inner.new_mix = new_mix as i32;
                config.inner.interday_learning_mix = interday_mix as i32;
                config.inner.other = serde_json::to_vec(&serde_json::json!({
                    "jschoreels.deckoptions": {
                        "intraday_learning_mix": intraday_mix as i32,
                    },
                }))?;
                col.add_or_update_deck_config(&mut config)?;

                let new_card = CardAdder::new().add(&mut col)[0].id;
                let timing = col.timing_today()?;
                let review = add_memory_state_card(
                    &mut col,
                    deck.id,
                    CardQueue::Review,
                    CardType::Review,
                    timing.days_elapsed as i32,
                    2 * 86_400,
                    30.0,
                )?;
                let interday = add_memory_state_card(
                    &mut col,
                    deck.id,
                    CardQueue::DayLearn,
                    CardType::Learn,
                    timing.days_elapsed as i32,
                    4 * 86_400,
                    30.0,
                )?;

                let intraday = add_memory_state_card(
                    &mut col,
                    deck.id,
                    CardQueue::Learn,
                    CardType::Relearn,
                    (timing.now.0 - 1) as i32,
                    86_400,
                    30.0,
                )?;

                let mut expected = if interday_mix == ReviewMix::AfterReviews {
                    vec![review, interday]
                } else {
                    vec![interday, review]
                };
                if intraday_mix == ReviewMix::MixWithReviews {
                    let position = if interday_mix == ReviewMix::AfterReviews {
                        1
                    } else {
                        2
                    };
                    expected.insert(position, intraday);
                }
                if new_mix == ReviewMix::BeforeReviews {
                    expected.insert(0, new_card);
                } else {
                    expected.push(new_card);
                }
                match intraday_mix {
                    ReviewMix::BeforeReviews => expected.insert(0, intraday),
                    ReviewMix::AfterReviews => expected.push(intraday),
                    ReviewMix::MixWithReviews => {}
                }
                assert_eq!(
                    col.queue_as_ids(deck.id),
                    expected,
                    "new: {new_mix:?}, interday: {interday_mix:?}, intraday: {intraday_mix:?}"
                );
                assert_eq!(col.counts(), [1, 2, 1]);
            }
        }
        Ok(())
    }

    #[test]
    fn rwkv_descending_retrievability_gathers_new_cards() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_gather_order(&mut deck, NewCardGatherPriority::DescendingRetrievability);

        let first = CardAdder::new().add(&mut col)[0].id;
        let second = CardAdder::new().add(&mut col)[0].id;
        let unscored = CardAdder::new().add(&mut col)[0].id;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(first, 0.10), (second, 0.80)]))?;

        assert_eq!(col.queue_as_ids(deck.id), vec![second, first, unscored]);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_gather_reuses_parent_scope_scores() -> Result<()> {
        let mut col = Collection::new();
        let parent = DeckAdder::new("Parent").add(&mut col);
        let mut child = DeckAdder::new("Parent::Child").add(&mut col);
        col.set_deck_gather_order(&mut child, NewCardGatherPriority::DescendingRetrievability);

        let first = CardAdder::new().deck(child.id).add(&mut col)[0].id;
        let second = CardAdder::new().deck(child.id).add(&mut col)[0].id;
        col.set_rwkv_review_queue_scores(
            parent.id,
            HashMap::from([(first, 0.10), (second, 0.80)]),
        )?;

        assert_eq!(col.queue_as_ids(child.id), vec![second, first]);
        Ok(())
    }

    #[test]
    fn rwkv_queue_rejects_scores_from_an_unrelated_scope() -> Result<()> {
        let mut col = Collection::new();
        let mut study_deck = DeckAdder::new("Study").add(&mut col);
        let prepared_deck = DeckAdder::new("Prepared").add(&mut col);
        col.set_deck_rwkv_instant_order(&mut study_deck, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let future_review = add_memory_state_card(
            &mut col,
            study_deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_scores(prepared_deck.id, HashMap::from([(future_review, 0.10)]))?;

        assert!(col.queue_as_ids(study_deck.id).is_empty());
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_gather_is_not_overridden_by_new_card_sort_order() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        let mut conf = DeckConfig::default();
        conf.inner.new_card_gather_priority =
            NewCardGatherPriority::DescendingRetrievability as i32;
        conf.inner.new_card_sort_order = NewCardSortOrder::Template as i32;
        col.add_or_update_deck_config(&mut conf)?;
        deck.normal_mut().unwrap().config_id = conf.id.0;
        col.add_or_update_deck(&mut deck)?;

        let siblings = CardAdder::new().siblings(2).add(&mut col);
        let lower_template = siblings[0].id;
        let higher_template = siblings[1].id;
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(lower_template, 0.61), (higher_template, 0.83)]),
        )?;

        assert_eq!(
            col.queue_as_ids(deck.id),
            vec![higher_template, lower_template]
        );
        Ok(())
    }

    #[test]
    fn rwkv_descending_retrievability_gathers_new_cards_across_child_decks() -> Result<()> {
        let mut col = Collection::new();
        let mut parent = DeckAdder::new("Parent").add(&mut col);
        let child1 = DeckAdder::new("Parent::Child 1").add(&mut col);
        let child2 = DeckAdder::new("Parent::Child 2").add(&mut col);
        let child3 = DeckAdder::new("Parent::Child 3").add(&mut col);
        col.set_deck_gather_order(&mut parent, NewCardGatherPriority::DescendingRetrievability);

        let card32 = CardAdder::new().deck(child1.id).add(&mut col)[0].id;
        let card33 = CardAdder::new().deck(child2.id).add(&mut col)[0].id;
        let card35 = CardAdder::new().deck(child3.id).add(&mut col)[0].id;
        col.set_rwkv_review_queue_scores(
            parent.id,
            HashMap::from([(card32, 0.32), (card33, 0.33), (card35, 0.35)]),
        )?;

        assert_eq!(col.queue_as_ids(parent.id), vec![card35, card33, card32]);
        Ok(())
    }

    #[test]
    fn rwkv_ascending_retrievability_gathers_new_cards() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_gather_order(&mut deck, NewCardGatherPriority::AscendingRetrievability);

        let first = CardAdder::new().add(&mut col)[0].id;
        let second = CardAdder::new().add(&mut col)[0].id;
        let unscored = CardAdder::new().add(&mut col)[0].id;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(first, 0.10), (second, 0.80)]))?;

        assert_eq!(col.queue_as_ids(deck.id), vec![first, second, unscored]);
        Ok(())
    }

    #[test]
    fn random_reviews_resample_full_pool_before_parent_limit() -> Result<()> {
        for rwkv in [false, true] {
            let mut col = Collection::new();
            let mut parent = DeckAdder::new("Parent").add(&mut col);
            let children = [
                DeckAdder::new("Parent::A").add(&mut col),
                DeckAdder::new("Parent::B").add(&mut col),
            ];
            if rwkv {
                col.set_deck_rwkv_instant_order(&mut parent, ReviewCardOrder::Random);
            } else {
                col.set_deck_review_order(&mut parent, ReviewCardOrder::Random);
            }
            col.set_deck_review_limit(parent.id, 1);
            let today = col.timing_today()?.days_elapsed as i32;
            let mut eligible = HashSet::new();
            let mut scores = HashMap::new();
            for child in &children {
                for index in 0..4 {
                    let id = add_memory_state_card(
                        &mut col,
                        child.id,
                        CardQueue::Review,
                        CardType::Review,
                        today,
                        2 * 86_400,
                        30.0,
                    )?;
                    if !rwkv || index != 0 {
                        eligible.insert(id);
                    }
                    scores.insert(id, if index == 0 { 0.99 } else { 0.20 });
                }
            }
            if rwkv {
                col.set_rwkv_review_queue_scores(parent.id, scores)?;
            }
            let mut seen = HashSet::new();
            // No card data changes between builds: the old hash order always
            // returned the same card. The generous sample avoids flaky tests
            // without attempting to certify a statistical distribution.
            for _ in 0..256 {
                let ids = col.queue_as_ids(parent.id);
                assert_eq!(ids.len(), 1);
                assert!(eligible.contains(&ids[0]));
                seen.insert(ids[0]);
            }
            assert_eq!(seen, eligible, "RWKV enabled: {rwkv}");
        }
        Ok(())
    }

    #[test]
    fn rwkv_instant_eligibility_applies_to_every_review_order() -> Result<()> {
        for order in [
            ReviewCardOrder::Day,
            ReviewCardOrder::DayThenDeck,
            ReviewCardOrder::DeckThenDay,
            ReviewCardOrder::IntervalsAscending,
            ReviewCardOrder::IntervalsDescending,
            ReviewCardOrder::EaseAscending,
            ReviewCardOrder::EaseDescending,
            ReviewCardOrder::RetrievabilityAscending,
            ReviewCardOrder::RetrievabilityDescending,
            ReviewCardOrder::RelativeOverdueness,
            ReviewCardOrder::Random,
            ReviewCardOrder::Added,
            ReviewCardOrder::ReverseAdded,
        ] {
            let mut col = Collection::new();
            let mut deck = col.get_or_create_normal_deck("Default")?;
            col.set_deck_rwkv_instant_order(&mut deck, order);

            let timing = col.timing_today()?;
            let unscored_due = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32 - 1,
                2 * 86_400,
                30.0,
            )?;
            let scored_due_above_target = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32,
                2 * 86_400,
                30.0,
            )?;
            let scored_future_below_target = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32 + 7,
                2 * 86_400,
                30.0,
            )?;
            col.set_rwkv_review_queue_score_entries(
                deck.id,
                HashMap::from([
                    (
                        scored_due_above_target,
                        RwkvReviewQueueScoreEntry {
                            retrievability: 0.80,
                            intervening_reviews: None,
                            target_retention: Some(0.75),
                        },
                    ),
                    (
                        scored_future_below_target,
                        RwkvReviewQueueScoreEntry {
                            retrievability: 0.20,
                            intervening_reviews: None,
                            target_retention: Some(0.75),
                        },
                    ),
                ]),
            )?;

            let mut actual = col.queue_as_ids(deck.id);
            actual.sort();
            let mut expected = vec![unscored_due, scored_future_below_target];
            expected.sort();
            assert_eq!(actual, expected, "review order {order:?}");
            assert_eq!(col.counts(), [0, 0, 2], "review order {order:?}");
        }
        Ok(())
    }

    #[test]
    fn rwkv_relative_overdueness_uses_score_target_before_review_limit() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut deck,
            ReviewCardOrder::RelativeOverdueness,
            0.90,
        );
        col.set_deck_review_limit(deck.id, 1);

        let timing = col.timing_today()?;
        let higher_target = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let lower_target = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            20 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_score_entries(
            deck.id,
            HashMap::from([
                (
                    higher_target,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.90,
                        intervening_reviews: None,
                        target_retention: Some(0.95),
                    },
                ),
                (
                    lower_target,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.79,
                        intervening_reviews: None,
                        target_retention: Some(0.80),
                    },
                ),
            ]),
        )?;

        assert_eq!(col.queue_as_ids(deck.id), vec![higher_target]);
        Ok(())
    }

    #[test]
    fn rwkv_same_day_reviews_may_ignore_review_limit() -> Result<()> {
        for order in [
            ReviewCardOrder::Day,
            ReviewCardOrder::RetrievabilityAscending,
        ] {
            let mut col = Collection::new();
            let mut deck = col.get_or_create_normal_deck("Default")?;
            col.set_deck_rwkv_review_order_with_options(&mut deck, order, 0.75, true);
            col.set_deck_same_day_reviews_ignore_review_limit(deck.id, true);
            col.set_deck_review_limit(deck.id, 1);

            let timing = col.timing_today()?;
            let first_due = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32,
                2 * 86_400,
                30.0,
            )?;
            let second_due = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32,
                2 * 86_400,
                30.0,
            )?;
            let same_day = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32 + 1,
                2 * 86_400,
                30.0,
            )?;
            let mut card = col.storage.get_card(same_day)?.unwrap();
            card.last_review_time = Some(timing.now);
            col.storage.update_card(&card)?;
            col.add_review_log_today(same_day, timing)?;
            col.set_rwkv_review_queue_scores(
                deck.id,
                HashMap::from([(first_due, 0.20), (second_due, 0.30), (same_day, 0.10)]),
            )?;

            let queue = col.queue_as_ids(deck.id);
            assert_eq!(queue.len(), 2, "review order {order:?}");
            assert!(queue.contains(&same_day), "review order {order:?}");
            assert_eq!(
                queue.iter().filter(|card| **card != same_day).count(),
                1,
                "review order {order:?}"
            );

            col.set_deck_review_limit(deck.id, 0);
            assert_eq!(col.queue_as_ids(deck.id), vec![same_day]);

            col.set_deck_same_day_reviews_ignore_review_limit(deck.id, false);
            assert!(col.queue_as_ids(deck.id).is_empty());
        }
        Ok(())
    }

    #[test]
    fn fsrs_same_day_reviews_may_ignore_review_limit() -> Result<()> {
        for order in [
            ReviewCardOrder::Day,
            ReviewCardOrder::RetrievabilityAscending,
        ] {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::FsrsLearningQueuesDisabled, true, false)?;
            let mut deck = col.get_or_create_normal_deck("Default")?;
            col.set_deck_review_order(&mut deck, order);
            col.set_deck_same_day_reviews_ignore_review_limit(deck.id, true);
            col.set_deck_review_limit(deck.id, 0);

            let timing = col.timing_today()?;
            let same_day = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32,
                2 * 86_400,
                30.0,
            )?;
            col.add_review_log_today(same_day, timing)?;

            assert_eq!(col.queue_as_ids(deck.id), vec![same_day], "{order:?}");

            col.set_deck_same_day_reviews_ignore_review_limit(deck.id, false);
            assert!(col.queue_as_ids(deck.id).is_empty(), "{order:?}");
        }
        Ok(())
    }

    #[test]
    fn rwkv_instant_preserves_due_day_review_order() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::Day);

        let timing = col.timing_today()?;
        let unscored_due = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 - 1,
            2 * 86_400,
            30.0,
        )?;
        let scored_future = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_score_entries(
            deck.id,
            HashMap::from([(
                scored_future,
                RwkvReviewQueueScoreEntry {
                    retrievability: 0.20,
                    intervening_reviews: None,
                    target_retention: Some(0.75),
                },
            )]),
        )?;

        assert_eq!(col.queue_as_ids(deck.id), vec![unscored_due, scored_future]);
        Ok(())
    }

    #[test]
    fn answered_card_score_patch_preserves_retained_queue() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let queued_card = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let answered_card = CardId(9_999_999_999);
        col.set_rwkv_review_queue_score_entries(
            deck.id,
            HashMap::from([(
                answered_card,
                RwkvReviewQueueScoreEntry {
                    retrievability: 0.20,
                    intervening_reviews: None,
                    target_retention: Some(0.75),
                },
            )]),
        )?;
        col.get_queued_cards(1, false, true)?;
        let build_time = col.state.card_queues.as_ref().unwrap().build_time;

        col.patch_answered_card_rwkv_review_queue_score_entry(
            deck.id,
            answered_card,
            Some(RwkvReviewQueueScoreEntry {
                retrievability: 0.80,
                intervening_reviews: Some(1),
                target_retention: Some(0.75),
            }),
        )?;

        assert_eq!(
            col.state.card_queues.as_ref().unwrap().build_time,
            build_time
        );
        let scores = col
            .rwkv_review_queue_scores(deck.id, timing.days_elapsed)
            .unwrap();
        assert_eq!(scores[&answered_card].retrievability, 0.80);
        assert!(col
            .patch_answered_card_rwkv_review_queue_score_entry(
                deck.id,
                queued_card,
                Some(RwkvReviewQueueScoreEntry::new(0.50)),
            )
            .is_err());
        Ok(())
    }

    #[test]
    fn eligible_answered_card_score_patch_invalidates_retained_queue() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_options(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
            true,
        );
        let timing = col.timing_today()?;
        let answered_card = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let remaining_card = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(answered_card, 0.10), (remaining_card, 0.20)]),
        )?;
        assert_eq!(col.queued_card_ids(1)?, vec![answered_card]);
        col.state
            .card_queues
            .as_mut()
            .unwrap()
            .pop_entry(answered_card)?;
        let mut card = col.storage.get_card(answered_card)?.unwrap();
        card.last_review_time = Some(timing.now);
        col.storage.update_card(&card)?;

        col.patch_answered_card_rwkv_review_queue_score_entry(
            deck.id,
            answered_card,
            Some(RwkvReviewQueueScoreEntry {
                retrievability: 0.10,
                intervening_reviews: Some(0),
                target_retention: Some(0.75),
            }),
        )?;

        assert!(col.state.card_queues.is_none());
        Ok(())
    }

    #[test]
    fn deferred_answered_card_patch_rebuilds_when_repeat_guard_is_ready() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_repeat_guards(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
            2,
            0,
        );
        let timing = col.timing_today()?;
        let answered_card = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let remaining_card = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(answered_card, 0.10), (remaining_card, 0.20)]),
        )?;
        assert_eq!(col.queued_card_ids(1)?, vec![answered_card]);
        col.state
            .card_queues
            .as_mut()
            .unwrap()
            .pop_entry(answered_card)?;
        let mut card = col.storage.get_card(answered_card)?.unwrap();
        card.last_review_time = Some(timing.now);
        col.storage.update_card(&card)?;

        col.patch_answered_card_rwkv_review_queue_score_entry(
            deck.id,
            answered_card,
            Some(RwkvReviewQueueScoreEntry {
                retrievability: 0.10,
                intervening_reviews: Some(0),
                target_retention: Some(0.75),
            }),
        )?;
        assert!(col.state.card_queues.is_some());

        col.update_rwkv_review_queue_intervening_reviews(
            deck.id,
            HashMap::from([(answered_card, 2)]),
        )?;

        assert!(col.state.card_queues.is_none());
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_order_can_include_future_review_cards() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        let other_deck = col.get_or_create_normal_deck("Other")?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let due_review = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let future_review = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        let inactive_deck_review = add_memory_state_card(
            &mut col,
            other_deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([
                (due_review, 0.90),
                (future_review, 0.10),
                (inactive_deck_review, 0.01),
            ]),
        )?;

        assert_eq!(col.queue_as_ids(deck.id), vec![future_review, due_review]);
        assert_eq!(col.counts(), [0, 0, 2]);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_order_keeps_due_reviews_without_scores() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let due_review_without_score = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let future_review = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(future_review, 0.10)]))?;

        assert_eq!(
            col.queue_as_ids(deck.id),
            vec![future_review, due_review_without_score]
        );
        assert_eq!(col.counts(), [0, 0, 2]);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_order_continues_after_an_ineligible_ranked_chunk() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.90,
        );
        col.set_deck_review_limit(deck.id, 1);

        let timing = col.timing_today()?;
        let mut scores = HashMap::new();
        for index in 0..=gathering::RWKV_REVIEW_GATHER_MIN_CHUNK_SIZE {
            let card_id = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32,
                2 * 86_400,
                30.0,
            )?;
            let in_first_chunk = index < gathering::RWKV_REVIEW_GATHER_MIN_CHUNK_SIZE;
            scores.insert(
                card_id,
                RwkvReviewQueueScoreEntry {
                    retrievability: if in_first_chunk { 0.10 } else { 0.20 },
                    intervening_reviews: None,
                    target_retention: Some(if in_first_chunk { 0.05 } else { 0.50 }),
                },
            );
        }
        let expected = *scores.keys().max().unwrap();
        col.set_rwkv_review_queue_score_entries(deck.id, scores)?;

        assert_eq!(col.queue_as_ids(deck.id), vec![expected]);
        assert_eq!(col.counts(), [0, 0, 1]);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_order_excludes_scores_above_desired_retention() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
        );

        let timing = col.timing_today()?;
        let due_high_rwkv_r = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let future_low_rwkv_r = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        for card_id in [due_high_rwkv_r, future_low_rwkv_r] {
            let mut card = col.storage.get_card(card_id)?.unwrap();
            card.desired_retention = None;
            col.storage.update_card(&card)?;
        }
        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(due_high_rwkv_r, 0.80), (future_low_rwkv_r, 0.20)]),
        )?;

        assert_eq!(col.queue_as_ids(deck.id), vec![future_low_rwkv_r]);
        assert_eq!(col.counts(), [0, 0, 1]);
        Ok(())
    }

    #[test]
    fn rwkv_minimum_pulls_lowest_retrievability_reviews() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
        );
        col.set_deck_rwkv_minimum_reviews(deck.id, 2);

        let timing = col.timing_today()?;
        let mut scored_cards = Vec::new();
        for score in [0.90, 0.80, 0.95] {
            let card_id = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32 + 7,
                2 * 86_400,
                30.0,
            )?;
            scored_cards.push((card_id, score));
        }
        col.set_rwkv_review_queue_score_entries(
            deck.id,
            scored_cards
                .iter()
                .map(|(card_id, score)| {
                    (
                        *card_id,
                        RwkvReviewQueueScoreEntry {
                            retrievability: *score,
                            intervening_reviews: None,
                            target_retention: Some(0.75),
                        },
                    )
                })
                .collect(),
        )?;

        scored_cards.sort_by(|(_, score_a), (_, score_b)| score_a.total_cmp(score_b));
        assert_eq!(
            col.queue_as_ids(deck.id),
            scored_cards[..2]
                .iter()
                .map(|(card_id, _)| *card_id)
                .collect::<Vec<_>>()
        );
        Ok(())
    }

    #[test]
    fn rwkv_minimum_counts_normally_eligible_reviews() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
        );
        col.set_deck_rwkv_minimum_reviews(deck.id, 2);

        let timing = col.timing_today()?;
        let mut cards = Vec::new();
        for score in [0.20, 0.80, 0.90] {
            let card_id = add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32 + 7,
                2 * 86_400,
                30.0,
            )?;
            cards.push((card_id, score));
        }
        col.set_rwkv_review_queue_score_entries(
            deck.id,
            cards
                .iter()
                .map(|(card_id, score)| {
                    (
                        *card_id,
                        RwkvReviewQueueScoreEntry {
                            retrievability: *score,
                            intervening_reviews: None,
                            target_retention: Some(0.75),
                        },
                    )
                })
                .collect(),
        )?;

        assert_eq!(col.queue_as_ids(deck.id), vec![cards[0].0, cards[1].0]);
        Ok(())
    }

    #[test]
    fn rwkv_minimum_uses_selected_deck_across_subdecks() -> Result<()> {
        let mut col = Collection::new();
        let mut parent = DeckAdder::new("parent").add(&mut col);
        let mut lower = DeckAdder::new("parent::lower").add(&mut col);
        let mut higher = DeckAdder::new("parent::higher").add(&mut col);
        for deck in [&mut parent, &mut lower, &mut higher] {
            col.set_deck_rwkv_review_order_with_desired_retention(
                deck,
                ReviewCardOrder::RetrievabilityAscending,
                0.75,
            );
            col.set_deck_rwkv_minimum_reviews(deck.id, 2);
        }
        col.set_deck_rwkv_minimum_reviews(lower.id, 1);
        col.set_deck_rwkv_minimum_reviews(higher.id, 1);

        let timing = col.timing_today()?;
        let mut add_card = |deck_id| {
            add_memory_state_card(
                &mut col,
                deck_id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32 + 7,
                2 * 86_400,
                30.0,
            )
        };
        let first = add_card(lower.id)?;
        let second = add_card(lower.id)?;
        let high = add_card(higher.id)?;
        col.set_rwkv_review_queue_score_entries(
            parent.id,
            [(first, 0.80), (second, 0.85), (high, 0.95)]
                .into_iter()
                .map(|(card_id, retrievability)| {
                    (
                        card_id,
                        RwkvReviewQueueScoreEntry {
                            retrievability,
                            target_retention: Some(0.75),
                            intervening_reviews: None,
                        },
                    )
                })
                .collect(),
        )?;

        assert_eq!(col.queue_as_ids(parent.id), vec![first, second]);

        parent.set_rwkv_respect_subdeck_minimums(true)?;
        col.add_or_update_deck(&mut parent)?;
        assert_eq!(col.queue_as_ids(parent.id), vec![first, high]);
        parent.set_rwkv_respect_subdeck_minimums(false)?;
        col.add_or_update_deck(&mut parent)?;

        let usn = col.usn()?;
        col.update_deck_stats(
            timing.days_elapsed,
            usn,
            anki_proto::scheduler::UpdateStatsRequest {
                deck_id: parent.id.0,
                review_delta: 2,
                ..Default::default()
            },
        )?;
        assert!(col.queue_as_ids(parent.id).is_empty());
        assert_eq!(col.queue_as_ids(higher.id), vec![high]);

        col.set_deck_rwkv_minimum_reviews(higher.id, 0);
        col.update_deck_stats(
            timing.days_elapsed,
            usn,
            anki_proto::scheduler::UpdateStatsRequest {
                deck_id: parent.id.0,
                review_delta: -2,
                ..Default::default()
            },
        )?;
        col.set_config_bool(BoolKey::ApplyAllParentLimits, true, false)?;
        assert!(col.queue_as_ids(higher.id).is_empty());
        Ok(())
    }

    #[test]
    fn rwkv_subdeck_minimums_follow_each_level_option() -> Result<()> {
        let mut col = Collection::new();
        let mut language = DeckAdder::new("Language").add(&mut col);
        let mut japanese = DeckAdder::new("Language::Japanese").add(&mut col);
        let mut english = DeckAdder::new("Language::English").add(&mut col);
        let mut japanese_vocab = DeckAdder::new("Language::Japanese::Vocab").add(&mut col);
        let mut japanese_sentences = DeckAdder::new("Language::Japanese::Sentences").add(&mut col);
        let mut english_vocab = DeckAdder::new("Language::English::Vocab").add(&mut col);
        let mut english_sentences = DeckAdder::new("Language::English::Sentences").add(&mut col);
        for deck in [
            &mut language,
            &mut japanese,
            &mut english,
            &mut japanese_vocab,
            &mut japanese_sentences,
            &mut english_vocab,
            &mut english_sentences,
        ] {
            col.set_deck_rwkv_review_order_with_desired_retention(
                deck,
                ReviewCardOrder::RetrievabilityAscending,
                0.75,
            );
            col.set_deck_rwkv_minimum_reviews(deck.id, 1);
        }
        col.set_deck_rwkv_minimum_reviews(language.id, 2);
        language.set_rwkv_respect_subdeck_minimums(true)?;
        col.add_or_update_deck(&mut language)?;
        let timing = col.timing_today()?;
        let mut add_card = |deck_id| {
            add_memory_state_card(
                &mut col,
                deck_id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32 + 7,
                2 * 86_400,
                30.0,
            )
        };
        let japanese_low = add_card(japanese_vocab.id)?;
        let japanese_high = add_card(japanese_sentences.id)?;
        let english_low = add_card(english_vocab.id)?;
        let english_high = add_card(english_sentences.id)?;
        col.set_rwkv_review_queue_score_entries(
            language.id,
            [
                (japanese_low, 0.80),
                (japanese_high, 0.96),
                (english_low, 0.85),
                (english_high, 0.97),
            ]
            .into_iter()
            .map(|(card_id, retrievability)| {
                (
                    card_id,
                    RwkvReviewQueueScoreEntry {
                        retrievability,
                        target_retention: Some(0.75),
                        intervening_reviews: None,
                    },
                )
            })
            .collect(),
        )?;

        assert_eq!(
            col.queue_as_ids(language.id),
            vec![japanese_low, english_low]
        );

        japanese.set_rwkv_respect_subdeck_minimums(true)?;
        col.add_or_update_deck(&mut japanese)?;
        assert_eq!(
            col.queue_as_ids(language.id),
            vec![japanese_low, english_low, japanese_high]
        );
        Ok(())
    }

    #[test]
    fn rwkv_minimum_uses_lowest_recall_with_relative_overdueness() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = DeckAdder::new("parent").add(&mut col);
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RelativeOverdueness);
        col.set_deck_rwkv_minimum_reviews(deck.id, 1);
        let timing = col.timing_today()?;
        let lower_recall = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        let higher_recall = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_score_entries(
            deck.id,
            HashMap::from([
                (
                    lower_recall,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.80,
                        target_retention: Some(0.75),
                        intervening_reviews: None,
                    },
                ),
                (
                    higher_recall,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.90,
                        target_retention: Some(0.89),
                        intervening_reviews: None,
                    },
                ),
            ]),
        )?;

        assert_eq!(col.queue_as_ids(deck.id), vec![lower_recall]);
        Ok(())
    }

    #[test]
    fn rwkv_minimum_counts_reviews_moved_to_filtered_decks() -> Result<()> {
        let mut col = Collection::new();
        let mut source = DeckAdder::new("Source").add(&mut col);
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut source,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
        );
        col.set_deck_rwkv_minimum_reviews(source.id, 1);

        let mut filtered = Deck::new_filtered();
        filtered.name = NativeDeckName::from_native_str("Filtered");
        col.add_or_update_deck(&mut filtered)?;

        let timing = col.timing_today()?;
        let moved = add_memory_state_card(
            &mut col,
            source.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        let mut moved_card = col.storage.get_card(moved)?.unwrap();
        moved_card.original_deck_id = moved_card.deck_id;
        moved_card.original_due = moved_card.due;
        moved_card.deck_id = filtered.id;
        moved_card.due = -100_000;
        col.storage.update_card(&moved_card)?;

        let pull_candidate = add_memory_state_card(
            &mut col,
            source.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_score_entries(
            source.id,
            HashMap::from([(
                pull_candidate,
                RwkvReviewQueueScoreEntry {
                    retrievability: 0.90,
                    intervening_reviews: None,
                    target_retention: Some(0.75),
                },
            )]),
        )?;

        assert_eq!(
            col.storage.filtered_review_counts_by_original_deck()?,
            vec![(source.id, 1)]
        );
        assert!(col.queue_as_ids(source.id).is_empty());
        Ok(())
    }

    #[test]
    fn rwkv_minimum_uses_lowest_scores_with_configured_review_order() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::Day);
        col.set_deck_rwkv_minimum_reviews(deck.id, 2);

        let timing = col.timing_today()?;
        let mut add_review_card = |due_offset: i32| -> Result<CardId> {
            add_memory_state_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32 + due_offset,
                2 * 86_400,
                30.0,
            )
        };
        let later_lowest = add_review_card(3)?;
        let earliest_highest = add_review_card(1)?;
        let earlier_second = add_review_card(2)?;

        col.set_rwkv_review_queue_score_entries(
            deck.id,
            HashMap::from([
                (
                    later_lowest,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.80,
                        intervening_reviews: None,
                        target_retention: Some(0.75),
                    },
                ),
                (
                    earliest_highest,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.90,
                        intervening_reviews: None,
                        target_retention: Some(0.75),
                    },
                ),
                (
                    earlier_second,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.85,
                        intervening_reviews: None,
                        target_retention: Some(0.75),
                    },
                ),
            ]),
        )?;

        assert_eq!(
            col.queue_as_ids(deck.id),
            vec![earlier_second, later_lowest]
        );
        Ok(())
    }

    #[test]
    fn rwkv_minimum_does_not_bypass_same_day_guard() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
        );
        col.set_deck_rwkv_minimum_reviews(deck.id, 1);

        let timing = col.timing_today()?;
        let card_id = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        let mut card = col.storage.get_card(card_id)?.unwrap();
        card.last_review_time = Some(timing.now);
        col.storage.update_card(&card)?;
        col.set_rwkv_review_queue_score_entries(
            deck.id,
            HashMap::from([(
                card_id,
                RwkvReviewQueueScoreEntry {
                    retrievability: 0.80,
                    intervening_reviews: Some(0),
                    target_retention: Some(0.75),
                },
            )]),
        )?;

        assert!(col.queue_as_ids(deck.id).is_empty());
        Ok(())
    }

    #[test]
    fn rwkv_minimum_child_reviews_count_toward_parent_target() -> Result<()> {
        let mut col = Collection::new();
        let mut parent = DeckAdder::new("parent").add(&mut col);
        let mut child = DeckAdder::new("parent::child").add(&mut col);
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut parent,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
        );
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut child,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
        );
        col.set_deck_rwkv_minimum_reviews(parent.id, 3);
        col.set_deck_rwkv_minimum_reviews(child.id, 2);

        let timing = col.timing_today()?;
        let usn = col.usn()?;
        col.update_deck_stats(
            timing.days_elapsed,
            usn,
            anki_proto::scheduler::UpdateStatsRequest {
                deck_id: child.id.0,
                review_delta: 1,
                ..Default::default()
            },
        )?;

        let child_first = add_memory_state_card(
            &mut col,
            child.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        let parent_second = add_memory_state_card(
            &mut col,
            parent.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        let child_last = add_memory_state_card(
            &mut col,
            child.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_score_entries(
            parent.id,
            HashMap::from([
                (
                    child_first,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.80,
                        intervening_reviews: None,
                        target_retention: Some(0.75),
                    },
                ),
                (
                    parent_second,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.90,
                        intervening_reviews: None,
                        target_retention: Some(0.75),
                    },
                ),
                (
                    child_last,
                    RwkvReviewQueueScoreEntry {
                        retrievability: 0.95,
                        intervening_reviews: None,
                        target_retention: Some(0.75),
                    },
                ),
            ]),
        )?;

        assert_eq!(
            col.queue_as_ids(parent.id),
            vec![child_first, parent_second]
        );
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_order_uses_card_desired_retention_override() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
        );

        let timing = col.timing_today()?;
        let card_id = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let mut card = col.storage.get_card(card_id)?.unwrap();
        card.desired_retention = Some(0.85);
        col.storage.update_card(&card)?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(card_id, 0.80)]))?;

        assert_eq!(col.queue_as_ids(deck.id), vec![card_id]);
        assert_eq!(col.counts(), [0, 0, 1]);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_order_excludes_scores_above_card_desired_retention() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.90,
        );

        let timing = col.timing_today()?;
        let card_id = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let mut card = col.storage.get_card(card_id)?.unwrap();
        card.desired_retention = Some(0.90);
        col.storage.update_card(&card)?;
        col.set_rwkv_review_queue_score_entries(
            deck.id,
            HashMap::from([(
                card_id,
                RwkvReviewQueueScoreEntry {
                    retrievability: 0.60,
                    intervening_reviews: None,
                    target_retention: Some(0.50),
                },
            )]),
        )?;

        assert!(col.queue_as_ids(deck.id).is_empty());
        assert_eq!(col.counts(), [0, 0, 0]);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_order_excludes_same_day_reviews_when_disabled() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_desired_retention(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
        );

        let timing = col.timing_today()?;
        let card_id = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let mut card = col.storage.get_card(card_id)?.unwrap();
        card.last_review_time = Some(timing.now);
        col.storage.update_card(&card)?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(card_id, 0.20)]))?;

        assert!(col.queue_as_ids(deck.id).is_empty());
        assert_eq!(col.counts(), [0, 0, 0]);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_order_can_allow_same_day_reviews() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_options(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
            true,
        );

        let timing = col.timing_today()?;
        let card_id = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let mut card = col.storage.get_card(card_id)?.unwrap();
        card.last_review_time = Some(timing.now);
        col.storage.update_card(&card)?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(card_id, 0.20)]))?;

        assert_eq!(col.queue_as_ids(deck.id), vec![card_id]);
        assert_eq!(col.counts(), [0, 0, 1]);
        Ok(())
    }

    #[test]
    fn rwkv_instant_uses_only_global_same_day_setting() -> Result<()> {
        for order in [
            ReviewCardOrder::Day,
            ReviewCardOrder::RetrievabilityAscending,
        ] {
            for (fsrs_enabled, legacy_allow_same_day) in
                [(false, false), (false, true), (true, false), (true, true)]
            {
                for skip_learning_queues in [false, true] {
                    for allow_same_day in [false, true] {
                        let mut col = Collection::new();
                        col.set_config_bool(BoolKey::Fsrs, fsrs_enabled, false)?;
                        col.set_config_bool(
                            BoolKey::FsrsLearningQueuesDisabled,
                            skip_learning_queues,
                            false,
                        )?;
                        let mut deck = col.get_or_create_normal_deck("Default")?;
                        col.set_deck_rwkv_review_order_with_options(
                            &mut deck,
                            order,
                            0.75,
                            allow_same_day,
                        );
                        let mut config = col
                            .get_deck_config(deck.config_id().unwrap(), false)?
                            .unwrap();
                        config.inner.rwkv_review_allow_same_day_review = legacy_allow_same_day;
                        col.add_or_update_deck_config(&mut config)?;
                        // Daily minimums must not pull a blocked repeat back into the queue.
                        col.set_deck_rwkv_minimum_reviews(deck.id, 10);

                        let timing = col.timing_today()?;
                        let card_id = add_memory_state_card(
                            &mut col,
                            deck.id,
                            CardQueue::Review,
                            CardType::Review,
                            timing.days_elapsed as i32 + 1,
                            0,
                            1.0,
                        )?;
                        let mut card = col.storage.get_card(card_id)?.unwrap();
                        card.last_review_time = Some(timing.now);
                        col.storage.update_card(&card)?;
                        col.set_rwkv_review_queue_scores(
                            deck.id,
                            HashMap::from([(card_id, 0.20)]),
                        )?;
                        col.set_rwkv_stats_graph_score_entries(
                            "".into(),
                            HashMap::from([(
                                card_id,
                                crate::collection::RwkvStatsGraphScoreEntry {
                                    retrievability: 0.20,
                                    curve_retrievability: None,
                                    intervening_reviews: None,
                                    target_retention: None,
                                    curve_due: false,
                                },
                            )]),
                        )?;

                        let expected = if allow_same_day {
                            vec![card_id]
                        } else {
                            vec![]
                        };
                        assert_eq!(col.queue_as_ids(deck.id), expected);
                        assert_eq!(col.counts(), [0, 0, usize::from(allow_same_day)]);
                        let tree = col.deck_tree(Some(timing.now))?;
                        assert_eq!(tree.children[0].review_count, u32::from(allow_same_day));
                        assert_eq!(
                            col.search_cards("is:rwkv:due", SortMode::NoOrder)?,
                            expected
                        );

                        col.set_rwkv_review_queue_scores(
                            deck.id,
                            HashMap::from([(card_id, 0.95)]),
                        )?;
                        assert_eq!(col.queue_as_ids(deck.id), expected);

                        // The day begins at the configured rollover, not midnight.
                        card.last_review_time = Some(timing.next_day_at.adding_secs(-86_401));
                        col.storage.update_card(&card)?;
                        col.clear_study_queues();
                        assert_eq!(col.queue_as_ids(deck.id), vec![card_id]);
                    }
                }
            }
        }
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_order_requires_min_intervening_reviews() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_repeat_guards(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
            2,
            0,
        );

        let timing = col.timing_today()?;
        let card_id = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let mut card = col.storage.get_card(card_id)?.unwrap();
        card.last_review_time = Some(timing.now);
        col.storage.update_card(&card)?;
        col.set_rwkv_review_queue_score_entries(
            deck.id,
            HashMap::from([(
                card_id,
                RwkvReviewQueueScoreEntry {
                    retrievability: 0.20,
                    intervening_reviews: Some(1),
                    target_retention: None,
                },
            )]),
        )?;

        assert!(col.queue_as_ids(deck.id).is_empty());
        assert_eq!(col.counts(), [0, 0, 0]);

        col.set_rwkv_review_queue_score_entries(
            deck.id,
            HashMap::from([(
                card_id,
                RwkvReviewQueueScoreEntry {
                    retrievability: 0.20,
                    intervening_reviews: Some(2),
                    target_retention: None,
                },
            )]),
        )?;

        assert_eq!(col.queue_as_ids(deck.id), vec![card_id]);
        assert_eq!(col.counts(), [0, 0, 1]);
        Ok(())
    }

    #[test]
    fn rwkv_intervening_review_update_patches_existing_queue_score() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_repeat_guards(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
            2,
            0,
        );

        let timing = col.timing_today()?;
        let card_id = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let mut card = col.storage.get_card(card_id)?.unwrap();
        card.last_review_time = Some(timing.now);
        col.storage.update_card(&card)?;
        col.set_rwkv_review_queue_score_entries(
            deck.id,
            HashMap::from([(
                card_id,
                RwkvReviewQueueScoreEntry {
                    retrievability: 0.20,
                    intervening_reviews: Some(1),
                    target_retention: None,
                },
            )]),
        )?;

        assert!(col.queued_card_ids(1)?.is_empty());
        assert!(col.state.card_queues.is_some());

        col.update_rwkv_review_queue_intervening_reviews(deck.id, HashMap::from([(card_id, 2)]))?;

        assert!(col.state.card_queues.is_none());
        assert_eq!(col.queued_card_ids(1)?, vec![card_id]);
        Ok(())
    }

    #[test]
    fn rwkv_retrievability_order_requires_min_elapsed_secs() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_review_order_with_repeat_guards(
            &mut deck,
            ReviewCardOrder::RetrievabilityAscending,
            0.75,
            0,
            300,
        );

        let timing = col.timing_today()?;
        let card_id = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let mut card = col.storage.get_card(card_id)?.unwrap();
        // Leave enough margin that a slow CI runner cannot cross the eligibility
        // boundary between storing the card and rebuilding the queue.
        card.last_review_time = Some(timing.now.adding_secs(-250));
        col.storage.update_card(&card)?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(card_id, 0.20)]))?;

        assert!(col.queue_as_ids(deck.id).is_empty());
        assert_eq!(col.counts(), [0, 0, 0]);

        let mut card = col.storage.get_card(card_id)?.unwrap();
        card.last_review_time = Some(timing.now.adding_secs(-350));
        col.storage.update_card(&card)?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(card_id, 0.20)]))?;

        assert_eq!(col.queue_as_ids(deck.id), vec![card_id]);
        assert_eq!(col.counts(), [0, 0, 1]);
        Ok(())
    }

    #[test]
    fn rwkv_unscored_due_reviews_do_not_use_fsrs_retrievability_order() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let older_due_high_r = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 - 10,
            10 * 86_400,
            1000.0,
        )?;
        let later_due_low_r = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            86_400,
            0.1,
        )?;
        let future_scored = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(future_scored, 0.10)]))?;

        let older_retrievability =
            col.fsrs_current_retrievability_for_card(older_due_high_r, 1000.0, 10.0)?;
        let later_retrievability =
            col.fsrs_current_retrievability_for_card(later_due_low_r, 0.1, 1.0)?;
        assert!(older_retrievability > later_retrievability);
        assert_eq!(
            col.queue_as_ids(deck.id),
            vec![future_scored, older_due_high_r, later_due_low_r]
        );
        Ok(())
    }

    #[test]
    fn rwkv_review_order_with_empty_scores_does_not_use_fsrs_retrievability_order() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let older_due_high_r = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 - 10,
            10 * 86_400,
            1000.0,
        )?;
        let later_due_low_r = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            86_400,
            0.1,
        )?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::new())?;

        let older_retrievability =
            col.fsrs_current_retrievability_for_card(older_due_high_r, 1000.0, 10.0)?;
        let later_retrievability =
            col.fsrs_current_retrievability_for_card(later_due_low_r, 0.1, 1.0)?;
        assert!(older_retrievability > later_retrievability);
        assert_eq!(
            col.queue_as_ids(deck.id),
            vec![older_due_high_r, later_due_low_r]
        );
        Ok(())
    }

    #[test]
    fn rwkv_review_without_instant_order_does_not_use_fsrs_retrievability_order() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut deck = col.get_or_create_normal_deck("Default")?;
        let mut conf = DeckConfig::default();
        conf.inner.review_order = ReviewCardOrder::RetrievabilityAscending as i32;
        conf.inner.rwkv_review_enabled = true;
        conf.inner.rwkv_review_instant_order_enabled = false;
        col.add_or_update_deck_config(&mut conf)?;
        deck.normal_mut().unwrap().config_id = conf.id.0;
        col.add_or_update_deck(&mut deck)?;

        let timing = col.timing_today()?;
        let older_due_high_r = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 - 10,
            10 * 86_400,
            1000.0,
        )?;
        let later_due_low_r = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            86_400,
            0.1,
        )?;

        let older_retrievability =
            col.fsrs_current_retrievability_for_card(older_due_high_r, 1000.0, 10.0)?;
        let later_retrievability =
            col.fsrs_current_retrievability_for_card(later_due_low_r, 0.1, 1.0)?;
        assert!(older_retrievability > later_retrievability);
        assert_eq!(
            col.queue_as_ids(deck.id),
            vec![older_due_high_r, later_due_low_r]
        );
        Ok(())
    }

    #[test]
    fn rwkv_empty_score_updates_preserve_new_review_mix() -> Result<()> {
        for with_card_info_score in [false, true] {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::Fsrs, true, true)?;
            let deck = col.get_or_create_normal_deck("Default")?;
            col.set_current_deck(deck.id)?;
            col.update_default_deck_config(|config| {
                config.fsrs_version = FsrsVersion::Seven as i32;
                config.new_mix = ReviewMix::MixWithReviews as i32;
                config.rwkv_review_enabled = false;
                config.rwkv_review_instant_order_enabled = false;
            });
            for _ in 0..6 {
                CardAdder::new().add(&mut col);
            }
            for _ in 0..2 {
                CardAdder::new().due_dates(["0"]).add(&mut col);
            }
            if with_card_info_score {
                let card_id = col.get_next_card()?.unwrap().card.id;
                col.set_rwkv_card_info_score(card_id, Some(0.5))?;
            }

            let mut shown_queues = vec![];
            for _ in 0..8 {
                col.set_rwkv_review_queue_scores(deck.id, HashMap::new())?;
                shown_queues.push(col.get_next_card()?.unwrap().card.queue);
                col.answer_easy();
            }

            assert_eq!(
                shown_queues,
                vec![
                    CardQueue::New,
                    CardQueue::New,
                    CardQueue::Review,
                    CardQueue::New,
                    CardQueue::New,
                    CardQueue::Review,
                    CardQueue::New,
                    CardQueue::New,
                ],
                "Card Info score installed: {with_card_info_score}"
            );
            assert!(col.get_next_card()?.is_none());
        }
        Ok(())
    }

    #[test]
    fn rwkv_score_refresh_preserves_minimum_new_review_mix() -> Result<()> {
        for (mode, new_count, expected) in [
            (ReviewMix::MixWithReviews, 1, "RRRRRRRRRRNRRRRRRRRRR"),
            (ReviewMix::MixWithReviews, 4, "RRRRNRRRRNRRRRNRRRRNRRRR"),
            (ReviewMix::BeforeReviews, 4, "NNNNRRRRRRRRRRRRRRRRRRRR"),
            (ReviewMix::AfterReviews, 4, "RRRRRRRRRRRRRRRRRRRRNNNN"),
        ] {
            for preserve_current_card in [false, true] {
                let mut col = Collection::new();
                let mut deck = col.get_or_create_normal_deck("Default")?;
                col.set_current_deck(deck.id)?;
                col.set_deck_rwkv_review_order_with_desired_retention(
                    &mut deck,
                    ReviewCardOrder::RetrievabilityAscending,
                    0.9,
                );
                let mut config = col
                    .get_deck_config(deck.config_id().unwrap(), false)?
                    .unwrap();
                config.inner.new_mix = mode as i32;
                config.inner.rwkv_review_minimum_reviews_per_day = 20;
                col.add_or_update_deck_config(&mut config)?;
                for _ in 0..new_count {
                    CardAdder::new().add(&mut col);
                }
                let timing = col.timing_today()?;
                let mut scores = HashMap::new();
                for _ in 0..20 {
                    let card_id = add_memory_state_card(
                        &mut col,
                        deck.id,
                        CardQueue::Review,
                        CardType::Review,
                        timing.days_elapsed as i32 + 7,
                        2 * 86_400,
                        30.0,
                    )?;
                    scores.insert(card_id, 0.95);
                }
                col.set_rwkv_review_queue_scores(deck.id, scores.clone())?;
                assert_eq!(col.counts(), [new_count, 0, 20]);

                let mut shown = String::new();
                for _ in 0..20 + new_count {
                    let card = col.get_next_card()?.unwrap().card;
                    shown.push(if card.queue == CardQueue::New {
                        'N'
                    } else {
                        'R'
                    });
                    col.answer_easy();
                    scores.remove(&card.id);
                    let current_card = if preserve_current_card {
                        col.get_next_card()?.map(|queued| queued.card.id)
                    } else {
                        None
                    };
                    col.set_rwkv_review_queue_scores(deck.id, scores.clone())?;
                    if let Some(card_id) = current_card {
                        col.rebuild_queued_cards_preserving_current_card(card_id)?;
                        assert_eq!(col.get_next_card()?.unwrap().card.id, card_id);
                    }
                }

                assert_eq!(
                    shown, expected,
                    "{mode:?}, preserve current: {preserve_current_card}"
                );
                assert!(col.get_next_card()?.is_none());
            }
        }
        Ok(())
    }

    #[test]
    fn rwkv_new_review_mix_preserves_progress_after_undo_and_redo() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_current_deck(deck.id)?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::Day);
        for _ in 0..2 {
            CardAdder::new().add(&mut col);
        }
        for _ in 0..6 {
            CardAdder::new().due_dates(["0"]).add(&mut col);
        }
        let expected = col.queued_card_ids(20)?;

        for answered in 1..=expected.len() {
            col.answer_easy();
            col.undo()?;
            col.redo()?;
            col.invalidate_rwkv_review_queue();

            assert_eq!(col.queued_card_ids(20)?, expected[answered..]);
        }
        Ok(())
    }

    #[test]
    fn rwkv_new_review_mix_resets_after_normal_queue_invalidation() -> Result<()> {
        for switch_deck in [false, true] {
            let mut col = Collection::new();
            let mut deck = col.get_or_create_normal_deck("Default")?;
            let other = col.get_or_create_normal_deck("Other")?;
            col.set_current_deck(deck.id)?;
            col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::Day);
            for _ in 0..2 {
                CardAdder::new().add(&mut col);
            }
            for _ in 0..6 {
                CardAdder::new().due_dates(["0"]).add(&mut col);
            }
            col.answer_easy();
            col.invalidate_rwkv_review_queue();
            if switch_deck {
                col.set_current_deck(other.id)?;
                col.set_current_deck(deck.id)?;
            } else {
                col.clear_study_queues();
            }

            let shown: String = col
                .get_queued_cards(20, false, true)?
                .cards
                .iter()
                .map(|queued| {
                    if queued.card.queue == CardQueue::New {
                        'N'
                    } else {
                        'R'
                    }
                })
                .collect();
            assert_eq!(shown, "RRNRRNR", "switch deck: {switch_deck}");
        }
        Ok(())
    }

    #[test]
    fn rwkv_score_clear_rebuilds_review_queue_from_normal_due_dates() -> Result<()> {
        for clear_from_other_deck in [false, true] {
            let mut col = Collection::new();
            let mut deck = col.get_or_create_normal_deck("Default")?;
            col.set_current_deck(deck.id)?;
            col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);
            let other_deck = col.get_or_create_normal_deck("Other")?;
            let due = CardAdder::new().due_dates(["0"]).add(&mut col)[0].id;
            let future = CardAdder::new().due_dates(["7"]).add(&mut col)[0].id;
            col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(due, 0.2), (future, 0.1)]))?;
            assert_eq!(col.queued_card_ids(10)?, vec![future, due]);

            col.set_rwkv_review_queue_scores(
                if clear_from_other_deck {
                    other_deck.id
                } else {
                    deck.id
                },
                HashMap::new(),
            )?;

            assert_eq!(col.queued_card_ids(10)?, vec![due]);
            assert_eq!(col.counts(), [0, 0, 1]);
        }
        Ok(())
    }

    #[test]
    fn rwkv_score_update_rebuilds_review_queue_with_new_scores() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_current_deck(deck.id)?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let first = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let second = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let future = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32 + 7,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(first, 0.10), (second, 0.20)]))?;

        assert_eq!(col.queued_card_ids(10)?, vec![first, second]);

        col.set_rwkv_review_queue_scores(
            deck.id,
            HashMap::from([(first, 0.90), (second, 0.05), (future, 0.01)]),
        )?;

        assert_eq!(col.queued_card_ids(10)?, vec![future, second, first]);
        assert_eq!(col.counts(), [0, 0, 3]);
        Ok(())
    }

    #[test]
    fn rwkv_score_update_keeps_the_shown_card_first() -> Result<()> {
        let mut col = Collection::new();
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_current_deck(deck.id)?;
        col.set_deck_rwkv_instant_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);

        let timing = col.timing_today()?;
        let first = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        let second = add_memory_state_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2 * 86_400,
            30.0,
        )?;
        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(first, 0.10), (second, 0.20)]))?;

        assert_eq!(col.queued_card_ids(1)?, vec![first]);

        col.set_rwkv_review_queue_scores(deck.id, HashMap::from([(first, 0.90), (second, 0.05)]))?;

        // kuma3: the card on screen stays first (the desktop reviewer used to
        // keep it with rebuild_queued_cards_preserving_current_card); the rest
        // follow the new scores
        assert_eq!(col.queued_card_ids(10)?, vec![first, second]);
        Ok(())
    }

    impl Collection {
        fn card_queue_len(&mut self) -> usize {
            self.get_queued_cards(5, false, false).unwrap().cards.len()
        }
    }

    #[test]
    fn new_card_potentially_burying_review_card() {
        let mut col = Collection::new();
        // add one new and one review card
        CardAdder::new().siblings(2).due_dates(["0"]).add(&mut col);
        // Potentially problematic config: New cards are shown first and would bury
        // review siblings. This poses a problem because we gather review cards first.
        col.update_default_deck_config(|config| {
            config.new_mix = ReviewMix::BeforeReviews as i32;
            config.bury_new = false;
            config.bury_reviews = true;
        });

        let old_queue_len = col.card_queue_len();
        col.answer_easy();
        col.clear_study_queues();

        // The number of cards in the queue must decrease by exactly 1, either because
        // no burying was performed, or the first built queue anticipated it and didn't
        // include the buried card.
        assert_eq!(col.card_queue_len(), old_queue_len - 1);
    }

    #[test]
    fn new_cards_may_ignore_review_limit() {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::NewCardsIgnoreReviewLimit, true, false)
            .unwrap();
        col.update_default_deck_config(|config| {
            config.reviews_per_day = 0;
        });
        CardAdder::new().add(&mut col);

        // review limit doesn't apply to new card
        assert_eq!(col.card_queue_len(), 1);
    }

    #[test]
    fn reviews_dont_affect_new_limit_before_review_limit_is_reached() {
        let mut col = Collection::new();
        col.update_default_deck_config(|config| {
            config.new_per_day = 1;
        });
        CardAdder::new().siblings(2).due_dates(["0"]).add(&mut col);
        assert_eq!(col.card_queue_len(), 2);
    }

    #[test]
    fn may_apply_parent_limits() {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::ApplyAllParentLimits, true, false)
            .unwrap();
        col.update_default_deck_config(|config| {
            config.new_per_day = 0;
        });
        let child = DeckAdder::new("Default::child")
            .with_config(|_| ())
            .add(&mut col);
        CardAdder::new().deck(child.id).add(&mut col);
        col.set_current_deck(child.id).unwrap();
        assert_eq!(col.card_queue_len(), 0);
    }
}
