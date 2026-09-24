// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#[cfg(test)]
mod benchmark;
mod burying;
mod gathering;
pub(crate) mod intersperser;
pub(crate) mod sized_chain;
mod sorting;

use std::collections::HashMap;
use std::collections::VecDeque;

use intersperser::Intersperser;
use sized_chain::SizedChain;

use super::BuryMode;
use super::CardQueues;
use super::Counts;
use super::LearningQueueEntry;
use super::MainQueueEntry;
use super::MainQueueEntryKind;
use crate::card::CardQueue;
use crate::deckconfig::NewCardGatherPriority;
use crate::deckconfig::NewCardSortOrder;
use crate::deckconfig::ReviewCardOrder;
use crate::deckconfig::ReviewMix;
use crate::decks::limits::LimitTreeMap;
use crate::prelude::*;
use crate::scheduler::states::load_balancer::LoadBalancer;
use crate::scheduler::timing::SchedTimingToday;

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
    pub reps: u32,
}

#[derive(Debug, Clone, Copy)]
pub(crate) enum DueCardKind {
    Review,
    Learning,
}

/// Minimal scheduling projection for exact scoring. Avoids a second card
/// lookup per candidate without constructing partially populated Card values.
#[derive(Debug, Clone, Copy)]
pub(crate) struct DueCardWithState {
    pub card: DueCard,
    pub queue: CardQueue,
    pub interval: u32,
    pub original_due: i32,
    pub memory_state: Option<crate::card::FsrsMemoryState>,
    pub last_review_time: Option<TimestampSecs>,
}

impl DueCardWithState {
    fn original_or_current_due(&self) -> i32 {
        if self.card.original_deck_id.0 != 0 {
            self.original_due
        } else {
            self.card.due
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
    pub(super) new_review_mix: ReviewMix,
}

#[derive(Debug)]
pub(super) struct QueueBuilder {
    pub(super) new: Vec<NewCard>,
    pub(super) review: Vec<DueCard>,
    pub(super) learning: Vec<DueCard>,
    pub(super) day_learning: Vec<DueCard>,
    pub(super) retrievability_sorted_non_new: Vec<DueCard>,
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
        let limits = LimitTreeMap::build(
            &decks,
            &config_map,
            timing.days_elapsed,
            new_cards_ignore_review_limit,
        );
        let sort_options = sort_options(&root_deck, &config_map);
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
            retrievability_sorted_non_new: Vec::new(),
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
            },
        })
    }

    pub(super) fn build(mut self, learn_ahead_secs: i64) -> CardQueues {
        self.sort_new();

        // intraday learning and total learn count
        let intraday_learning = sort_learning(self.learning);
        let now = TimestampSecs::now();
        let cutoff = now.adding_secs(learn_ahead_secs);
        let exact_retrievability_order = self.context.uses_exact_retrievability_order();
        let learn_count = if exact_retrievability_order {
            self.retrievability_sorted_non_new
                .iter()
                .filter(|card| matches!(card.kind, DueCardKind::Learning))
                .count()
        } else {
            intraday_learning.iter().filter(|e| e.due <= cutoff).count() + self.day_learning.len()
        };
        let review_count = if exact_retrievability_order {
            self.retrievability_sorted_non_new
                .iter()
                .filter(|card| matches!(card.kind, DueCardKind::Review))
                .count()
        } else {
            self.review.len()
        };
        let new_count = self.new.len();

        // merge interday and new cards into main
        let with_interday_learn = if exact_retrievability_order {
            Box::new(
                self.retrievability_sorted_non_new
                    .into_iter()
                    .map(Into::into),
            ) as Box<dyn ExactSizeIterator<Item = MainQueueEntry>>
        } else {
            merge_day_learning(
                self.review,
                self.day_learning,
                self.context.sort_options.day_learn_mix,
            )
        };
        let main_iter = merge_new(
            with_interday_learn,
            self.new,
            self.context.sort_options.new_review_mix,
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
            current_day: self.context.timing.days_elapsed,
            build_time: TimestampMillis::now(),
            load_balancer: self.load_balancer,
            current_learning_cutoff: now,
            exact_retrievability_order,
            shown_top_card: None,
            fsrs_enabled: self.context.fsrs,
            fsrs_short_term_with_steps: self.context.fsrs_short_term_with_steps,
        }
    }
}

impl Context {
    fn uses_exact_retrievability_order(&self) -> bool {
        self.fsrs
            && matches!(
                self.sort_options.review_order,
                ReviewCardOrder::RetrievabilityAscending
                    | ReviewCardOrder::RetrievabilityDescending
                    | ReviewCardOrder::RelativeOverdueness
            )
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
            new_review_mix: config.inner.new_mix(),
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
) -> Box<dyn ExactSizeIterator<Item = MainQueueEntry>> {
    let new_iter = new.into_iter().map(Into::into);

    match mode {
        ReviewMix::BeforeReviews => Box::new(SizedChain::new(new_iter, review_iter)),
        ReviewMix::AfterReviews => Box::new(SizedChain::new(review_iter, new_iter)),
        ReviewMix::MixWithReviews => Box::new(Intersperser::new(review_iter, new_iter)),
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
        let mut queues = QueueBuilder::new(self, deck_id)?;
        self.storage
            .update_active_decks(&queues.context.root_deck)?;

        queues.gather_cards(self)?;

        let queues = queues.build(self.learn_ahead_secs() as i64);

        Ok(queues)
    }
}

#[cfg(test)]
mod test {
    use anki_proto::deck_config::deck_config::config::NewCardGatherPriority;
    use anki_proto::deck_config::deck_config::config::NewCardSortOrder;
    use fsrs::DEFAULT_PARAMETERS;

    use super::*;
    use crate::card::CardQueue;
    use crate::card::CardType;
    use crate::card::FsrsMemoryState;

    impl Collection {
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

        fn set_fsrs7_params(&mut self, deck_id: DeckId) {
            let config_id = self
                .get_deck(deck_id)
                .unwrap()
                .unwrap()
                .config_id()
                .unwrap();
            let mut config = self.get_deck_config(config_id, false).unwrap().unwrap();
            config.inner.fsrs_params_7 = DEFAULT_PARAMETERS.to_vec();
            self.add_or_update_deck_config(&mut config).unwrap();
        }
    }

    #[test]
    fn should_build_empty_queue_if_limit_is_reached() {
        let mut col = Collection::new();
        CardAdder::new().due_dates(["0"]).add(&mut col);
        col.set_deck_review_limit(DeckId(1), 0);
        assert_eq!(col.queue_as_deck_and_template(DeckId(1)), vec![]);
    }

    #[test]
    fn exhausted_review_limit_keeps_intraday_but_not_interday_learning() -> Result<()> {
        for order in [
            ReviewCardOrder::Day,
            ReviewCardOrder::RetrievabilityAscending,
            ReviewCardOrder::RetrievabilityDescending,
        ] {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::Fsrs, true, true)?;
            let mut deck = col.get_or_create_normal_deck("Default")?;
            col.set_deck_review_order(&mut deck, order);
            col.set_fsrs7_params(deck.id);
            col.set_deck_review_limit(deck.id, 0);
            let timing = col.timing_today()?;
            let intraday = add_fsrs_review_card(
                &mut col,
                deck.id,
                CardQueue::Learn,
                CardType::Relearn,
                (timing.now.0 - 1) as i32,
                2,
            )?;
            for (queue, ctype) in [
                (CardQueue::Review, CardType::Review),
                (CardQueue::DayLearn, CardType::Relearn),
            ] {
                add_fsrs_review_card(
                    &mut col,
                    deck.id,
                    queue,
                    ctype,
                    timing.days_elapsed as i32,
                    3,
                )?;
            }
            assert_eq!(col.queue_as_ids(deck.id), vec![intraday], "{order:?}");
            // Answering an intraday learning card does not consume a daily review.
            col.answer_easy();
            assert_eq!(col.get_deck(deck.id)?.unwrap().common.review_studied, 0);
        }
        Ok(())
    }

    #[test]
    fn newly_due_intraday_cards_wait_for_current_answer_then_enter_exact_r_order() -> Result<()> {
        for order in [
            ReviewCardOrder::RetrievabilityAscending,
            ReviewCardOrder::RetrievabilityDescending,
        ] {
            let mut col = Collection::new();
            col.set_config_bool(BoolKey::Fsrs, true, true)?;
            let mut deck = col.get_or_create_normal_deck("Default")?;
            col.set_deck_review_order(&mut deck, order);
            let timing = col.timing_today()?;
            if timing.near_cutoff() {
                continue;
            }
            let first = add_fsrs_review_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32,
                2,
            )?;
            let second = add_fsrs_review_card(
                &mut col,
                deck.id,
                CardQueue::Review,
                CardType::Review,
                timing.days_elapsed as i32,
                4,
            )?;
            let ascending = order == ReviewCardOrder::RetrievabilityAscending;
            let future = add_fsrs_review_card(
                &mut col,
                deck.id,
                CardQueue::Learn,
                CardType::Relearn,
                (timing.now.0 + 60) as i32,
                if ascending { 1 } else { 5 },
            )?;
            let (current, other) = if ascending {
                (second, first)
            } else {
                (first, second)
            };
            assert_eq!(col.queue_as_ids(deck.id), vec![current, other]);
            assert_eq!(col.get_next_card()?.unwrap().card.id, current);
            assert_eq!(col.counts(), [0, 0, 2]);
            // Force the undo snapshot's generation ahead of wall time, so the
            // subsequent rebuild must advance it rather than accidentally reuse it.
            col.state.card_queues.as_mut().unwrap().build_time.0 += 60_000;
            // Advance the fixture's due boundary without sleeping or changing mtime.
            let mut card = col.storage.get_card(future)?.unwrap();
            card.due = (timing.now.0 - 1) as i32;
            col.storage.update_card(&card)?;
            col.state
                .card_queues
                .as_mut()
                .unwrap()
                .intraday_learning
                .front_mut()
                .unwrap()
                .due = TimestampSecs(card.due as i64);
            assert_eq!(col.get_next_card()?.unwrap().card.id, current);
            col.answer_easy();
            let queued = col.get_queued_cards(10, false)?;
            assert_eq!(
                queued.cards.iter().map(|c| c.card.id).collect::<Vec<_>>(),
                vec![other, future]
            );
            assert_eq!((queued.learning_count, queued.review_count), (1, 1));
            assert_eq!(col.get_queued_cards(10, true)?.cards[0].card.id, future);
            col.undo()?;
            assert_eq!(col.counts(), [0, 1, 2]);
        }
        Ok(())
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

    fn add_fsrs_review_card(
        col: &mut Collection,
        deck_id: DeckId,
        queue: CardQueue,
        ctype: CardType,
        due: i32,
        elapsed_days: i64,
    ) -> Result<CardId> {
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, deck_id)?;
        let mut card = col.storage.get_card_by_ordinal(note.id, 0)?.unwrap();
        card.ctype = ctype;
        card.queue = queue;
        card.due = due;
        card.interval = 1;
        card.memory_state = Some(FsrsMemoryState {
            stability: 30.0,
            stability_internal: 30.0,
            stability_fast: Some(30.0),
            difficulty: 5.0,
        });
        card.last_review_time = Some(TimestampSecs::now().adding_secs(-elapsed_days * 86_400));
        col.storage.update_card(&card)?;
        Ok(card.id)
    }

    #[test]
    fn fsrs_retrievability_order_is_global_across_due_non_new_queues() -> Result<()> {
        let mut col = Collection::new();
        col.set_config_bool(BoolKey::Fsrs, true, true)?;
        let mut deck = col.get_or_create_normal_deck("Default")?;
        col.set_deck_review_order(&mut deck, ReviewCardOrder::RetrievabilityAscending);
        col.set_fsrs7_params(deck.id);
        let timing = col.timing_today()?;

        let review = add_fsrs_review_card(
            &mut col,
            deck.id,
            CardQueue::Review,
            CardType::Review,
            timing.days_elapsed as i32,
            2,
        )?;
        let day_learning = add_fsrs_review_card(
            &mut col,
            deck.id,
            CardQueue::DayLearn,
            CardType::Relearn,
            timing.days_elapsed as i32,
            4,
        )?;
        let intraday_learning = add_fsrs_review_card(
            &mut col,
            deck.id,
            CardQueue::Learn,
            CardType::Relearn,
            (timing.now.0 - 1) as i32,
            6,
        )?;

        assert_eq!(
            col.queue_as_ids(deck.id),
            vec![intraday_learning, day_learning, review]
        );
        col.set_deck_review_order(&mut deck, ReviewCardOrder::RetrievabilityDescending);
        col.set_fsrs7_params(deck.id);
        assert_eq!(
            col.queue_as_ids(deck.id),
            vec![review, day_learning, intraday_learning]
        );
        Ok(())
    }

    impl Collection {
        fn card_queue_len(&mut self) -> usize {
            self.get_queued_cards(5, false).unwrap().cards.len()
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
