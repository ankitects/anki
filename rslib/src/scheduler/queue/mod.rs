// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod builder;
mod entry;
mod learning;
mod main;
pub(crate) mod undo;

use std::collections::VecDeque;

use anki_proto::scheduler::SchedulingContext;
pub(crate) use builder::DueCard;
pub(crate) use builder::DueCardKind;
pub(crate) use builder::NewCard;
pub(crate) use entry::QueueEntry;
pub(crate) use entry::QueueEntryKind;
use fsrs::FSRS5_DEFAULT_DECAY;
pub(crate) use learning::LearningQueueEntry;
pub(crate) use main::MainQueueEntry;
pub(crate) use main::MainQueueEntryKind;
use rand::rngs::StdRng;
use rand::Rng;
use rand::SeedableRng;

use self::undo::QueueUpdate;
use super::states::SchedulingStates;
use super::timing::SchedTimingToday;
use crate::prelude::*;
use crate::probe::Probe;
use crate::scheduler::states::load_balancer::LoadBalancer;
use crate::timestamp::TimestampSecs;

#[derive(Debug)]
pub(crate) struct CardQueues {
    counts: Counts,
    main: VecDeque<MainQueueEntry>,
    intraday_learning: VecDeque<LearningQueueEntry>,
    current_day: u32,
    learn_ahead_secs: i64,
    build_time: TimestampMillis,
    /// Updated each time a card is answered, and by get_queued_cards() when the
    /// counts are zero. Ensures we don't show a newly-due learning card after a
    /// user returns from editing a review card.
    current_learning_cutoff: TimestampSecs,
    pub(crate) load_balancer: Option<LoadBalancer>,
    pub(crate) fsrs_enabled: bool,
    pub(crate) fsrs_short_term_with_steps: bool,
}

#[derive(Debug, Copy, Clone)]
pub struct Counts {
    pub new: usize,
    pub learning: usize,
    pub review: usize,
}

impl Counts {
    fn all_zero(self) -> bool {
        self.new == 0 && self.learning == 0 && self.review == 0
    }
}

#[derive(Debug, Clone)]
pub struct QueuedCard {
    pub card: Card,
    pub kind: QueueEntryKind,
    pub states: SchedulingStates,
    pub context: SchedulingContext,
    /// A probe variant chosen to be served in place of the original card.
    /// Clients that show it must echo its id in `CardAnswer::variant_id`.
    pub probe: Option<Probe>,
}

#[derive(Debug)]
pub struct QueuedCards {
    pub cards: Vec<QueuedCard>,
    pub new_count: usize,
    pub learning_count: usize,
    pub review_count: usize,
}

/// When we encounter a card with new or review burying enabled, all future
/// siblings need to be buried, regardless of their own settings.
#[derive(Default, Debug, Clone, Copy)]
pub(crate) struct BuryMode {
    pub(crate) bury_new: bool,
    pub(crate) bury_reviews: bool,
    pub(crate) bury_interday_learning: bool,
}

impl Collection {
    pub fn get_next_card(&mut self) -> Result<Option<QueuedCard>> {
        self.get_queued_cards(1, false)
            .map(|queued| queued.cards.first().cloned())
    }

    pub fn get_queued_cards(
        &mut self,
        fetch_limit: usize,
        intraday_learning_only: bool,
    ) -> Result<QueuedCards> {
        let queues = self.get_queues()?;
        let counts = queues.counts();
        let entries: Vec<_> = if intraday_learning_only {
            queues
                .intraday_now_iter()
                .chain(queues.intraday_ahead_iter())
                .map(Into::into)
                .collect()
        } else {
            queues.iter().take(fetch_limit).collect()
        };
        let cards: Vec<_> = entries
            .into_iter()
            .map(|entry| {
                let card = self
                    .storage
                    .get_card(entry.card_id())?
                    .or_not_found(entry.card_id())?;
                require!(
                    card.mtime == entry.mtime(),
                    "bug: card modified without updating queue: id:{} card:{} entry:{}",
                    card.id,
                    card.mtime,
                    entry.mtime()
                );

                // fixme: pass in card instead of id
                let next_states = self.get_scheduling_states(card.id)?;

                Ok(QueuedCard {
                    context: new_scheduling_context(self, &card)?,
                    probe: self.maybe_probe_substitute(&card, entry.kind())?,
                    card,
                    states: next_states,
                    kind: entry.kind(),
                })
            })
            .collect::<Result<_>>()?;
        Ok(QueuedCards {
            cards,
            new_count: counts.new,
            learning_count: counts.learning,
            review_count: counts.review,
        })
    }
}

impl Collection {
    /// Ascent fork: when the scheduler is already confident this review will
    /// pass, sometimes choose a probe variant to serve instead of the
    /// original. Purely a presentation decision - card state and scheduling
    /// are never touched.
    ///
    /// Sits in a latency-sensitive path. The free checks come first, so a
    /// collection with no probes pays a single indexed lookup on an empty
    /// table; only a card that actually has probes reaches the deck config
    /// and elapsed-time reads. Every ambiguous case declines to substitute,
    /// so a malformed config or missing history can never turn the feature
    /// on by accident.
    fn maybe_probe_substitute(
        &mut self,
        card: &Card,
        kind: QueueEntryKind,
    ) -> Result<Option<Probe>> {
        // Learning and relearning cards are excluded: a probe is only
        // meaningful where a pass was already expected.
        if kind != QueueEntryKind::Review {
            return Ok(None);
        }
        let Some(memory_state) = card.memory_state else {
            return Ok(None);
        };
        let probes = self.storage.get_probes_for_card(card.id)?;
        if probes.is_empty() {
            return Ok(None);
        }
        let config = self.home_deck_config(None, card.original_or_current_deck_id())?;
        // The config is raw JSON on the sync wire, so a NaN or infinite rate
        // is reachable; treat anything that isn't a sane probability as off.
        let rate = config.inner.probe_rate;
        if !rate.is_finite() || rate <= 0.0 {
            return Ok(None);
        }
        let now = TimestampSecs::now();
        let Some(last_review_time) = card
            .last_review_time
            .or(self.storage.time_of_last_review(card.id)?)
        else {
            // No review history to measure retrievability against; treating
            // that as "just reviewed" would make the card unconditionally
            // eligible, so decline instead.
            return Ok(None);
        };
        let seconds_elapsed = now.elapsed_secs_since(last_review_time).max(0) as f32;
        let retrievability = fsrs::current_retrievability(
            memory_state.into(),
            seconds_elapsed / 86_400.0,
            card.decay.unwrap_or(FSRS5_DEFAULT_DECAY),
        );
        let threshold = config.inner.probe_retrievability_threshold;
        if !threshold.is_finite() || !retrievability.is_finite() || retrievability < threshold {
            return Ok(None);
        }
        // review_seed() is the collection's existing per-review seed; its
        // rotate keeps neighbouring (id, reps) pairs from colliding. The
        // constant decorrelates the coin from the interval fuzz that shares
        // the seed.
        let mut rng = StdRng::seed_from_u64(card.review_seed() ^ 0x50524f4245);
        if rng.random_range(0.0..1.0) >= rate {
            return Ok(None);
        }
        let chosen = rng.random_range(0..probes.len());
        Ok(probes.into_iter().nth(chosen))
    }
}

fn new_scheduling_context(col: &mut Collection, card: &Card) -> Result<SchedulingContext> {
    Ok(SchedulingContext {
        deck_name: col
            .get_deck(card.original_or_current_deck_id())?
            .or_not_found(card.deck_id)?
            .human_name(),
        seed: card.review_seed(),
        decay: card.decay,
        desired_retention: card.desired_retention,
    })
}

impl CardQueues {
    /// An iterator over the card queues, in the order the cards will
    /// be presented.
    fn iter(&self) -> impl Iterator<Item = QueueEntry> + '_ {
        self.intraday_now_iter()
            .map(Into::into)
            .chain(self.main.iter().map(Into::into))
            .chain(self.intraday_ahead_iter().map(Into::into))
    }

    /// Remove the provided card from the top of the queues and
    /// adjust the counts. If it was not at the top, return an error.
    fn pop_entry(&mut self, id: CardId) -> Result<QueueEntry> {
        if let Some(pos) = self.intraday_learning.iter().position(|e| e.id == id) {
            let entry = self.intraday_learning.remove(pos).unwrap();
            // FIXME:
            // under normal circumstances this should not go below 0, but currently
            // the Python unit tests answer learning cards before they're due
            self.counts.learning = self.counts.learning.saturating_sub(1);
            Ok(entry.into())
        } else if self.main.front().filter(|e| e.id == id).is_some() {
            Ok(self.pop_main().unwrap().into())
        } else {
            invalid_input!("not at top of queue")
        }
    }

    fn push_undo_entry(&mut self, entry: QueueEntry) {
        match entry {
            QueueEntry::IntradayLearning(entry) => self.push_intraday_learning(entry),
            QueueEntry::Main(entry) => self.push_main(entry),
        }
    }

    /// Return the current due counts. If there are no due cards, the learning
    /// cutoff is updated to the current time first, and any newly-due learning
    /// cards are added to the counts.
    pub(crate) fn counts(&mut self) -> Counts {
        if self.counts.all_zero() {
            // we discard the returned undo information in this case
            self.update_learning_cutoff_and_count();
        }
        self.counts
    }

    fn is_stale(&self, current_day: u32) -> bool {
        self.current_day != current_day
    }
}

impl Collection {
    /// This is automatically done when transact() is called for everything
    /// except card answers, so unless you are modifying state outside of a
    /// transaction, you probably don't need this.
    pub(crate) fn clear_study_queues(&mut self) {
        self.state.card_queues = None;
    }

    pub(crate) fn maybe_clear_study_queues_after_op(&mut self, op: &OpChanges) {
        if op.op != Op::AnswerCard && op.requires_study_queue_rebuild() {
            self.state.card_queues = None;
        }
    }

    pub(crate) fn update_queues_after_answering_card(
        &mut self,
        card: &Card,
        timing: SchedTimingToday,
        is_finished_preview: bool,
    ) -> Result<()> {
        if let Some(queues) = &mut self.state.card_queues {
            let entry = queues.pop_entry(card.id)?;
            let requeued_learning = if is_finished_preview {
                None
            } else {
                queues.maybe_requeue_learning_card(card, timing)
            };
            let cutoff_snapshot = queues.update_learning_cutoff_and_count();
            let queue_build_time = queues.build_time;
            self.save_queue_update_undo(Box::new(QueueUpdate {
                entry,
                learning_requeue: requeued_learning,
                queue_build_time,
                cutoff_snapshot,
            }));
        } else {
            // we currently allow the queues to be empty for unit tests
        }

        Ok(())
    }

    /// Get the card queues, building if necessary.
    pub(crate) fn get_queues(&mut self) -> Result<&mut CardQueues> {
        let deck = self.get_current_deck()?;
        self.clear_queues_if_day_changed()?;
        if self.state.card_queues.is_none() {
            self.state.card_queues = Some(self.build_queues(deck.id)?);
        }

        Ok(self.state.card_queues.as_mut().unwrap())
    }

    // Returns queues if they are valid and have not been rebuilt. If build time has
    // changed, they are cleared.
    pub(crate) fn get_or_invalidate_queues(
        &mut self,
        build_time: TimestampMillis,
    ) -> Result<Option<&mut CardQueues>> {
        self.clear_queues_if_day_changed()?;
        let same_build = self
            .state
            .card_queues
            .as_ref()
            .map(|q| q.build_time == build_time)
            .unwrap_or_default();
        if same_build {
            Ok(self.state.card_queues.as_mut())
        } else {
            self.clear_study_queues();
            Ok(None)
        }
    }

    fn clear_queues_if_day_changed(&mut self) -> Result<()> {
        let timing = self.timing_today()?;
        let day_rolled_over = self
            .state
            .card_queues
            .as_ref()
            .map(|q| q.is_stale(timing.days_elapsed))
            .unwrap_or(false);
        if day_rolled_over {
            self.discard_undo_and_study_queues();
            self.unbury_on_day_rollover(timing.days_elapsed)?;
        }
        Ok(())
    }
}

// test helpers
#[cfg(test)]
impl Collection {
    pub(crate) fn counts(&mut self) -> [usize; 3] {
        self.get_queued_cards(1, false)
            .map(|q| [q.new_count, q.learning_count, q.review_count])
            .unwrap_or([0; 3])
    }
}

#[cfg(test)]
mod probe_test {
    use std::collections::HashSet;

    use super::*;
    use crate::card::CardType;
    use crate::card::FsrsMemoryState;
    use crate::deckconfig::DeckConfigInner;
    use crate::probe::test::add_test_probe;
    use crate::scheduler::answering::CardAnswer;
    use crate::scheduler::answering::Rating;
    use crate::tests::NoteAdder;

    /// A collection of `count` review cards, each due today with a high
    /// retrievability memory state and one probe attached.
    fn probe_collection(count: usize, modifier: impl FnOnce(&mut DeckConfigInner)) -> Collection {
        let mut col = Collection::new();
        // probes key off FSRS retrievability, so the fixture runs with FSRS
        // on; it also keeps memory state across a lapse
        col.set_config_bool(crate::config::BoolKey::Fsrs, true, false)
            .unwrap();
        col.update_default_deck_config(modifier);
        let days_elapsed = col.timing_today().unwrap().days_elapsed as i32;
        for _ in 0..count {
            let note = NoteAdder::basic(&mut col).add(&mut col);
            let mut card = col
                .storage
                .all_cards_of_note(note.id)
                .unwrap()
                .pop()
                .unwrap();
            card.ctype = CardType::Review;
            card.queue = crate::card::CardQueue::Review;
            card.due = days_elapsed;
            card.interval = 10;
            // stability far above the elapsed time, so retrievability is
            // very close to 1
            card.memory_state = Some(FsrsMemoryState {
                stability: 1000.0,
                difficulty: 5.0,
            });
            card.last_review_time = Some(TimestampSecs::now());
            col.storage.update_card(&card).unwrap();
            add_test_probe(&mut col, card.id, &card.id.0.to_string());
        }
        col.clear_study_queues();
        col
    }

    fn substituted_count(col: &mut Collection, fetch: usize) -> usize {
        col.get_queued_cards(fetch, false)
            .unwrap()
            .cards
            .iter()
            .filter(|c| c.probe.is_some())
            .count()
    }

    #[test]
    fn zero_rate_never_substitutes() {
        let mut col = probe_collection(50, |c| {
            c.probe_rate = 0.0;
            c.probe_retrievability_threshold = 0.85;
        });
        assert_eq!(substituted_count(&mut col, 50), 0);
    }

    #[test]
    fn served_at_approximately_the_configured_rate() {
        // 120 cards keeps the fixture clear of the 200/day review limit, so a
        // change to that default can't quietly turn this into a limit test.
        // Card ids are wall-clock millis, so the seeds differ every run: this
        // is a statistical assertion, not a deterministic one. A quarter rate
        // is used rather than a half because at p=0.5 an inverted coin is
        // indistinguishable from a correct one.
        const N: usize = 120;
        let mut col = probe_collection(N, |c| {
            c.probe_rate = 0.25;
            c.probe_retrievability_threshold = 0.85;
        });
        let substituted = substituted_count(&mut col, N);
        // ~30 expected; sigma is ~4.7, so this window is ±4 sigma - wide
        // enough not to flake, tight enough to catch an inverted, doubled or
        // halved rate
        assert!(
            (11..=49).contains(&substituted),
            "expected ~30 of {N} substituted, got {substituted}"
        );

        // the extremes are exact: every eligible card, or none
        let mut col = probe_collection(20, |c| {
            c.probe_rate = 1.0;
            c.probe_retrievability_threshold = 0.85;
        });
        assert_eq!(substituted_count(&mut col, 20), 20);
    }

    /// The chosen variant must be one of *this* card's probes, and the same
    /// one on every refetch of the same review.
    #[test]
    fn chooses_a_probe_of_the_card_and_sticks_with_it() {
        let mut col = probe_collection(1, |c| {
            c.probe_rate = 1.0;
            c.probe_retrievability_threshold = 0.85;
        });
        let card_id = col.storage.get_all_cards().pop().unwrap().id;
        // give the card a second probe, so the choice is a real one
        add_test_probe(&mut col, card_id, "extra");
        col.clear_study_queues();

        let mine: Vec<_> = col
            .get_probes_for_card(card_id)
            .unwrap()
            .into_iter()
            .map(|p| p.id)
            .collect();
        assert_eq!(mine.len(), 2);

        let first = col.get_next_card().unwrap().unwrap().probe.unwrap();
        assert!(
            mine.contains(&first.id),
            "served a probe belonging to another card"
        );
        // refetching the same review must not reroll
        let again = col.get_next_card().unwrap().unwrap().probe.unwrap();
        assert_eq!(again, first);
    }

    /// Learning and relearning cards are never eligible, however good their
    /// memory state looks - a probe implies a pass was already expected.
    #[test]
    fn relearning_cards_are_not_eligible() {
        let mut col = probe_collection(1, |c| {
            c.probe_rate = 1.0;
            c.probe_retrievability_threshold = 0.85;
        });
        assert_eq!(substituted_count(&mut col, 1), 1);

        // fail the card into relearning; it keeps its memory state
        col.answer_again();
        col.storage
            .db
            .execute_batch("update cards set due = 0")
            .unwrap();
        col.clear_study_queues();

        let queued = col.get_next_card().unwrap().unwrap();
        assert_eq!(queued.kind, QueueEntryKind::Learning);
        assert!(queued.card.memory_state.is_some());
        assert!(queued.probe.is_none());
    }

    #[test]
    fn retrievability_below_threshold_is_not_eligible() {
        let mut col = probe_collection(20, |c| {
            c.probe_rate = 1.0;
            c.probe_retrievability_threshold = 0.85;
        });
        // shrink stability and push the last review well into the past, so
        // retrievability falls below the threshold
        for mut card in col.storage.get_all_cards() {
            card.memory_state = Some(FsrsMemoryState {
                stability: 1.0,
                difficulty: 5.0,
            });
            card.last_review_time = Some(TimestampSecs::now().adding_secs(-30 * 86_400));
            col.storage.update_card(&card).unwrap();
        }
        col.clear_study_queues();
        assert_eq!(substituted_count(&mut col, 20), 0);
    }

    /// Deck config crosses the sync wire as raw JSON, so nonsense values are
    /// reachable. Every one of them must switch the feature off, never on.
    #[test]
    fn malformed_config_and_missing_history_fail_closed() {
        for rate in [f32::NAN, f32::INFINITY, -1.0] {
            let mut col = probe_collection(5, |c| {
                c.probe_rate = rate;
                c.probe_retrievability_threshold = 0.85;
            });
            assert_eq!(substituted_count(&mut col, 5), 0, "rate {rate} substituted");
        }

        let mut col = probe_collection(5, |c| {
            c.probe_rate = 1.0;
            c.probe_retrievability_threshold = f32::NAN;
        });
        assert_eq!(substituted_count(&mut col, 5), 0, "NaN threshold");

        // no review history at all: retrievability is unmeasurable, so
        // treating the card as freshly reviewed would make it always eligible
        let mut col = probe_collection(5, |c| {
            c.probe_rate = 1.0;
            c.probe_retrievability_threshold = 0.85;
        });
        assert_eq!(substituted_count(&mut col, 5), 5);
        for mut card in col.storage.get_all_cards() {
            card.last_review_time = None;
            col.storage.update_card(&card).unwrap();
        }
        col.storage.db.execute_batch("delete from revlog").unwrap();
        col.clear_study_queues();
        assert_eq!(substituted_count(&mut col, 5), 0, "no review history");
    }

    #[test]
    fn cards_without_probes_are_never_substituted() {
        let mut col = probe_collection(5, |c| {
            c.probe_rate = 1.0;
            c.probe_retrievability_threshold = 0.85;
        });
        col.storage.db.execute_batch("DELETE FROM probes").unwrap();
        col.clear_study_queues();
        assert_eq!(substituted_count(&mut col, 5), 0);
    }

    #[derive(Clone, Copy, PartialEq)]
    enum Arm {
        /// Stock behaviour: no probes in the collection at all.
        NoProbes,
        /// The experiment's control arm: probes stored, feature switched off.
        RateZero,
        /// The treatment arm: every eligible review serves its probe, and the
        /// answer carries the variant id.
        RateOne,
    }

    /// The experiment depends on probes being purely additive, so all three
    /// arms must schedule identically: storing probes must not perturb
    /// anything, and neither must actually *serving* one and recording the
    /// outcome. This is the guard on "probe outcomes are recorded but must
    /// not feed back into scheduling".
    #[test]
    fn probes_never_affect_fsrs_scheduling() {
        // (interval, ease factor, reps, lapses, memory state, due offset)
        type CardState = (u32, u16, u32, u32, Option<String>, i32);

        const RATINGS: [Rating; 5] = [
            Rating::Good,
            Rating::Easy,
            Rating::Good,
            Rating::Hard,
            Rating::Again,
        ];

        fn study(arm: Arm) -> Vec<CardState> {
            let mut col = Collection::new();
            col.set_config_bool(crate::config::BoolKey::Fsrs, true, false)
                .unwrap();
            col.update_default_deck_config(|c| {
                c.probe_rate = if arm == Arm::RateOne { 1.0 } else { 0.0 };
                c.probe_retrievability_threshold = 0.85;
            });
            let days_elapsed = col.timing_today().unwrap().days_elapsed as i32;
            for _ in 0..RATINGS.len() {
                let note = NoteAdder::basic(&mut col).add(&mut col);
                let mut card = col
                    .storage
                    .all_cards_of_note(note.id)
                    .unwrap()
                    .pop()
                    .unwrap();
                card.ctype = CardType::Review;
                card.queue = crate::card::CardQueue::Review;
                card.due = days_elapsed;
                card.interval = 10;
                card.memory_state = Some(FsrsMemoryState {
                    stability: 1000.0,
                    difficulty: 5.0,
                });
                card.last_review_time = Some(TimestampSecs::now());
                col.storage.update_card(&card).unwrap();
                if arm != Arm::NoProbes {
                    add_test_probe(&mut col, card.id, &card.id.0.to_string());
                }
            }
            col.clear_study_queues();

            for rating in RATINGS {
                let queued = col.get_next_card().unwrap().unwrap();
                let probe = queued.probe.clone();
                assert_eq!(
                    probe.is_some(),
                    arm == Arm::RateOne,
                    "wrong arm: probe served = {}",
                    probe.is_some()
                );
                let new_state = match rating {
                    Rating::Again => queued.states.again,
                    Rating::Hard => queued.states.hard,
                    Rating::Good => queued.states.good,
                    Rating::Easy => queued.states.easy,
                };
                col.answer_card(&mut CardAnswer {
                    card_id: queued.card.id,
                    current_state: queued.states.current,
                    new_state,
                    rating,
                    answered_at: TimestampMillis::now(),
                    milliseconds_taken: 0,
                    milliseconds_to_reveal: None,
                    variant_id: probe.map(|p| p.id),
                    custom_data: None,
                    from_queue: true,
                })
                .unwrap();
            }

            let mut states: Vec<_> = col
                .storage
                .get_all_cards()
                .into_iter()
                .map(|c| {
                    (
                        c.interval,
                        c.ease_factor,
                        c.reps,
                        c.lapses,
                        c.memory_state.map(|m| format!("{m:?}")),
                        c.due - days_elapsed,
                    )
                })
                .collect();
            states.sort();
            states
        }

        let stock = study(Arm::NoProbes);

        // Anchor the baseline, so this can't degenerate into comparing two
        // sets of cards that were never scheduled: FSRS ran, every card was
        // answered once, and the ratings actually moved the intervals apart.
        assert_eq!(stock.len(), RATINGS.len());
        assert!(stock.iter().all(|s| s.2 == 1), "every card answered once");
        assert!(
            stock.iter().all(|s| s.4.is_some()),
            "FSRS memory state should be set"
        );
        assert!(
            stock.iter().any(|s| s.0 > 10),
            "a passed review should have grown its interval past the initial 10"
        );
        assert!(
            stock.iter().map(|s| s.0).collect::<HashSet<_>>().len() > 1,
            "different ratings should produce different intervals"
        );

        assert_eq!(
            study(Arm::RateZero),
            stock,
            "storing probes with the feature off must not perturb scheduling"
        );
        assert_eq!(
            study(Arm::RateOne),
            stock,
            "serving a probe and recording its outcome must not perturb scheduling"
        );
    }

    /// The variant id comes from the client and rides the revlog into sync,
    /// so a wrong one would permanently mislabel the transfer data. Reject it
    /// rather than record it.
    #[test]
    fn variant_id_from_another_card_is_rejected() {
        let mut col = probe_collection(2, |c| {
            c.probe_rate = 1.0;
            c.probe_retrievability_threshold = 0.85;
        });
        let queued = col.get_next_card().unwrap().unwrap();
        let card_id = queued.card.id;
        // a probe that exists, but belongs to the other card
        let other = col
            .storage
            .get_all_cards()
            .into_iter()
            .find(|c| c.id != card_id)
            .unwrap();
        let foreign = col.get_probes_for_card(other.id).unwrap().pop().unwrap();

        let mut answer = CardAnswer {
            card_id,
            current_state: queued.states.current,
            new_state: queued.states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 3000,
            milliseconds_to_reveal: None,
            variant_id: Some(foreign.id),
            custom_data: None,
            from_queue: true,
        };
        assert!(col.answer_card(&mut answer).is_err());

        // ...and one that doesn't exist at all
        answer.variant_id = Some(crate::probe::ProbeId(999_999));
        assert!(col.answer_card(&mut answer).is_err());

        // the rejected answers left no revlog behind
        assert!(col
            .storage
            .get_revlog_entries_for_card(card_id)
            .unwrap()
            .is_empty());
    }

    #[test]
    fn shown_variant_is_recorded_in_the_revlog() {
        let mut col = probe_collection(1, |c| {
            c.probe_rate = 1.0;
            c.probe_retrievability_threshold = 0.85;
        });
        let queued = col.get_next_card().unwrap().unwrap();
        let probe = queued.probe.clone().expect("probe should be served");
        let card_id = queued.card.id;

        col.answer_card(&mut CardAnswer {
            card_id,
            current_state: queued.states.current,
            new_state: queued.states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 3000,
            milliseconds_to_reveal: None,
            variant_id: Some(probe.id),
            custom_data: None,
            from_queue: true,
        })
        .unwrap();

        let entries = col.storage.get_revlog_entries_for_card(card_id).unwrap();
        let entry = entries.iter().max_by_key(|e| e.id).unwrap();
        assert_eq!(entry.variant_id(), Some(probe.id));

        // answering the original leaves the column empty, so ordinary rows
        // are byte-identical to ones written by older clients
        col.storage
            .db
            .execute_batch("UPDATE cards SET due = 0")
            .unwrap();
        col.clear_study_queues();
        let queued = col.get_next_card().unwrap().unwrap();
        col.answer_card(&mut CardAnswer {
            card_id,
            current_state: queued.states.current,
            new_state: queued.states.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 3000,
            milliseconds_to_reveal: None,
            variant_id: None,
            custom_data: None,
            from_queue: true,
        })
        .unwrap();
        let entries = col.storage.get_revlog_entries_for_card(card_id).unwrap();
        let entry = entries.iter().max_by_key(|e| e.id).unwrap();
        assert_eq!(entry.data, "");
        assert_eq!(entry.variant_id(), None);
    }
}
