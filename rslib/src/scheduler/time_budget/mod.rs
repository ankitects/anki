// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Time-budget study sessions driven by RWKV-Instant.
//!
//! A deck with a time budget (and its subdecks) is studied differently from
//! a normal deck: instead of Anki's due queue, every learning, review and
//! relearning card is scored by the RWKV-Instant model, and the card with
//! the lowest predicted recall is shown first. Cards whose due date has not
//! arrived yet are pulled forward. Answers are still scheduled by the
//! normal (FSRS) scheduler.
//!
//! A card is shown while:
//! - time is left in the day's budget and its recall is below the maximum
//!   desired retention, or
//! - its recall is below the minimum desired retention, even once the budget is
//!   used up.
//!
//! So a session ends early when no card is below the maximum, and runs over
//! the budget until every card is at or above the minimum. Cards answered
//! the configured number of times today are not shown again.

pub(crate) mod history;

use std::collections::HashMap;

use anki_proto::scheduler::time_budget_status_response;
use anki_proto::scheduler::TimeBudgetStatusResponse;
use serde::Deserialize;
use serde::Serialize;

use self::history::QueryCard;
use super::queue::QueueEntryKind;
use super::queue::QueuedCard;
use super::queue::QueuedCards;
use crate::card::CardQueue;
use crate::decks::Deck;
use crate::prelude::*;
use crate::storage::ids_to_string;

/// Collection config key holding [TimeBudgetConfig].
pub(crate) const TIME_BUDGET_CONFIG_KEY: &str = "rwkvTimeBudget";

/// Revlog entries that took study time today. Filtered previews (factor 0)
/// are not real answers.
const STUDIED_CONDITION: &str =
    "r.ease between 1 and 4 and r.type in (0, 1, 2, 3) and not (r.type = 3 and r.factor = 0)";

#[derive(Serialize, Deserialize, Debug, Default, Clone, PartialEq)]
#[serde(default, rename_all = "camelCase")]
pub(crate) struct TimeBudgetConfig {
    /// Keyed by deck id.
    pub decks: HashMap<String, DeckTimeBudget>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
#[serde(default, rename_all = "camelCase")]
pub(crate) struct DeckTimeBudget {
    pub enabled: bool,
    /// Desired study time per day.
    pub minutes: u32,
    /// A card answered this many times today is not shown again today.
    pub max_reviews_per_card: u32,
    /// Percent. Cards below this are shown even once the budget is used up.
    pub min_dr: u32,
    /// Percent. Only cards below this are shown.
    pub max_dr: u32,
}

impl Default for DeckTimeBudget {
    fn default() -> Self {
        Self {
            enabled: true,
            minutes: 30,
            max_reviews_per_card: 3,
            min_dr: 60,
            max_dr: 95,
        }
    }
}

impl DeckTimeBudget {
    fn is_active(&self) -> bool {
        self.enabled && self.minutes > 0
    }

    fn budget_secs(&self) -> i64 {
        self.minutes as i64 * 60
    }

    /// The recall below which cards are shown, given the time left.
    fn threshold(&self, remaining_secs: i64) -> f32 {
        let percent = if remaining_secs > 0 {
            self.max_dr
        } else {
            self.min_dr.min(self.max_dr)
        };
        percent.min(100) as f32 / 100.0
    }
}

/// The budget that applies to a deck: its own, or its nearest configured
/// ancestor's.
#[derive(Debug, Clone)]
pub(crate) struct ActiveTimeBudget {
    pub root: Deck,
    pub budget: DeckTimeBudget,
}

#[derive(Debug, Clone, Copy, PartialEq)]
struct Candidate {
    card_id: CardId,
    retrievability: f32,
    learning: bool,
}

/// Cards to show, lowest recall first. The card answered last is not
/// repeated straight away when another card can be shown instead.
fn select_candidates(
    mut candidates: Vec<Candidate>,
    threshold: f32,
    last_answered: Option<CardId>,
) -> Vec<Candidate> {
    candidates.retain(|candidate| candidate.retrievability < threshold);
    candidates.sort_by(|a, b| {
        a.retrievability
            .total_cmp(&b.retrievability)
            .then(a.card_id.cmp(&b.card_id))
    });
    if candidates.len() > 1 && Some(candidates[0].card_id) == last_answered {
        candidates.swap(0, 1);
    }
    candidates
}

impl Collection {
    pub(crate) fn time_budget_config(&self) -> TimeBudgetConfig {
        self.get_config_optional(TIME_BUDGET_CONFIG_KEY)
            .unwrap_or_default()
    }

    pub(crate) fn active_time_budget(
        &mut self,
        deck_id: DeckId,
    ) -> Result<Option<ActiveTimeBudget>> {
        let config = self.time_budget_config();
        if !config.decks.values().any(DeckTimeBudget::is_active) {
            return Ok(None);
        }
        let Some(deck) = self.storage.get_deck(deck_id)? else {
            return Ok(None);
        };
        if deck.is_filtered() {
            return Ok(None);
        }
        let parents = self.storage.parent_decks(&deck)?;
        for deck in std::iter::once(deck).chain(parents) {
            if let Some(budget) = config
                .decks
                .get(&deck.id.0.to_string())
                .filter(|budget| budget.is_active())
            {
                return Ok(Some(ActiveTimeBudget {
                    root: deck,
                    budget: budget.clone(),
                }));
            }
        }
        Ok(None)
    }

    fn day_start_millis(&mut self) -> Result<i64> {
        Ok((self.timing_today()?.next_day_at.0 - 86_400) * 1000)
    }

    /// Seconds spent today answering cards whose home deck is in `deck_ids`.
    fn secs_studied_today(&mut self, deck_ids: &[DeckId]) -> Result<i64> {
        let day_start = self.day_start_millis()?;
        let mut ids = String::new();
        ids_to_string(&mut ids, deck_ids);
        let millis: i64 = self.storage.db.query_row(
            &format!(
                "select coalesce(sum(r.time), 0) from revlog r join cards c on c.id = r.cid
                 where r.id >= ? and {STUDIED_CONDITION}
                 and (case when c.odid != 0 then c.odid else c.did end) in {ids}"
            ),
            [day_start],
            |row| row.get(0),
        )?;
        Ok(millis / 1000)
    }

    fn remaining_budget_secs(&mut self, active: &ActiveTimeBudget) -> Result<i64> {
        let deck_ids = self.storage.deck_id_with_children(&active.root)?;
        Ok(active.budget.budget_secs() - self.secs_studied_today(&deck_ids)?)
    }

    pub(crate) fn time_budget_status(&mut self) -> Result<TimeBudgetStatusResponse> {
        let config = self.time_budget_config();
        let mut decks = vec![];
        for (deck_id, budget) in config.decks {
            let Ok(deck_id) = deck_id.parse() else {
                continue;
            };
            if !budget.is_active() {
                continue;
            }
            let Some(deck) = self.storage.get_deck(DeckId(deck_id))? else {
                continue;
            };
            if deck.is_filtered() {
                continue;
            }
            let active = ActiveTimeBudget { root: deck, budget };
            let remaining = self.remaining_budget_secs(&active)?;
            decks.push(time_budget_status_response::Deck {
                deck_id,
                budget_secs: active.budget.budget_secs() as u32,
                remaining_secs: remaining.clamp(i32::MIN as i64, i32::MAX as i64) as i32,
            });
        }
        decks.sort_by_key(|deck| deck.deck_id);
        if !decks.is_empty() {
            // The deck list requests this in the background, so replaying the
            // review log here keeps the first card of a session from waiting
            // on it.
            self.instant_history()?;
        }
        Ok(TimeBudgetStatusResponse { decks })
    }

    /// The study queue of a deck covered by a time budget.
    pub(crate) fn get_time_budget_queued_cards(
        &mut self,
        active: &ActiveTimeBudget,
        study_deck: DeckId,
        fetch_limit: usize,
        intraday_learning_only: bool,
    ) -> Result<QueuedCards> {
        // Anki's own queues are not used while a budget applies; answering
        // without them is supported.
        self.clear_study_queues();
        let remaining_secs = self.remaining_budget_secs(active)?;
        let threshold = active.budget.threshold(remaining_secs);
        let candidates = self.time_budget_candidates(active, study_deck)?;
        let last_answered = self.card_answered_last_today()?;
        let selected = select_candidates(candidates, threshold, last_answered);

        let learning_count = selected.iter().filter(|c| c.learning).count();
        let review_count = selected.len() - learning_count;
        let cards = if intraday_learning_only {
            vec![]
        } else {
            selected
                .iter()
                .take(fetch_limit)
                .map(|candidate| self.time_budget_queued_card(candidate))
                .collect::<Result<_>>()?
        };
        Ok(QueuedCards {
            cards,
            new_count: 0,
            learning_count,
            review_count,
        })
    }

    fn time_budget_queued_card(&mut self, candidate: &Candidate) -> Result<QueuedCard> {
        let card = self
            .storage
            .get_card(candidate.card_id)?
            .or_not_found(candidate.card_id)?;
        let states = self.get_scheduling_states(card.id)?;
        Ok(QueuedCard {
            context: super::queue::new_scheduling_context(self, &card)?,
            kind: if candidate.learning {
                QueueEntryKind::Learning
            } else {
                QueueEntryKind::Review
            },
            states,
            card,
        })
    }

    /// Learning, review and relearning cards of the study deck and its
    /// subdecks, with their predicted recall. Cards at today's review cap are
    /// left out.
    fn time_budget_candidates(
        &mut self,
        active: &ActiveTimeBudget,
        study_deck: DeckId,
    ) -> Result<Vec<Candidate>> {
        let study_deck = self
            .storage
            .get_deck(study_deck)?
            .or_not_found(study_deck)?;
        let deck_ids = self.storage.deck_id_with_children(&study_deck)?;
        let mut ids = String::new();
        ids_to_string(&mut ids, &deck_ids);
        let card_ids: Vec<CardId> = self
            .storage
            .db
            .prepare(&format!(
                "select id from cards where did in {ids} and queue in ({}, {}, {})",
                CardQueue::Learn as i8,
                CardQueue::Review as i8,
                CardQueue::DayLearn as i8
            ))?
            .query_map([], |row| row.get(0))?
            .collect::<std::result::Result<_, _>>()?;
        let answers_today = self.answers_today_by_card()?;
        let max_reviews = active.budget.max_reviews_per_card;
        let card_ids: Vec<CardId> = card_ids
            .into_iter()
            .filter(|card_id| {
                max_reviews == 0 || answers_today.get(card_id).copied().unwrap_or(0) < max_reviews
            })
            .collect();
        if card_ids.is_empty() {
            return Ok(vec![]);
        }
        let cards = self.all_cards_for_ids(&card_ids, false)?;
        let presets: HashMap<DeckId, i64> = self
            .storage
            .get_all_decks()?
            .into_iter()
            .filter_map(|deck| deck.config_id().map(|config| (deck.id, config.0)))
            .collect();
        let timing = self.timing_today()?;
        let queries: Vec<_> = cards
            .iter()
            .map(|card| QueryCard {
                card,
                preset_id: presets.get(&card.original_or_current_deck_id()).copied(),
            })
            .collect();
        let retrievabilities = self.instant_history()?.predict(&queries, &timing)?;
        Ok(cards
            .iter()
            .zip(retrievabilities)
            .map(|(card, retrievability)| Candidate {
                card_id: card.id,
                retrievability,
                learning: matches!(card.queue, CardQueue::Learn | CardQueue::DayLearn),
            })
            .collect())
    }

    fn answers_today_by_card(&mut self) -> Result<HashMap<CardId, u32>> {
        let day_start = self.day_start_millis()?;
        self.storage
            .db
            .prepare(&format!(
                "select r.cid, count() from revlog r where r.id >= ? and {STUDIED_CONDITION} group by r.cid"
            ))?
            .query_map([day_start], |row| Ok((row.get(0)?, row.get(1)?)))?
            .collect::<std::result::Result<_, _>>()
            .map_err(Into::into)
    }

    fn card_answered_last_today(&mut self) -> Result<Option<CardId>> {
        let day_start = self.day_start_millis()?;
        self.storage
            .db
            .query_row(
                &format!(
                    "select r.cid from revlog r where r.id >= ? and {STUDIED_CONDITION}
                     order by r.id desc limit 1"
                ),
                [day_start],
                |row| row.get(0),
            )
            .map(Some)
            .or_else(|err| match err {
                rusqlite::Error::QueryReturnedNoRows => Ok(None),
                err => Err(err.into()),
            })
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;
    use crate::tests::CardAdder;
    use crate::tests::DeckAdder;

    const DAY_MILLIS: i64 = 86_400_000;

    /// A review card in `deck` with answers (days ago, button, kind).
    fn review_card(
        col: &mut Collection,
        deck: DeckId,
        answers: &[(i64, u8, RevlogReviewKind)],
    ) -> CardId {
        let card = CardAdder::new()
            .deck(deck)
            .due_dates(["5"])
            .add(col)
            .remove(0);
        let now = TimestampMillis::now().0;
        for (index, (days_ago, button, kind)) in answers.iter().enumerate() {
            col.storage
                .add_revlog_entry(
                    &RevlogEntry {
                        id: RevlogId(now - days_ago * DAY_MILLIS - 60_000 + index as i64),
                        cid: card.id,
                        button_chosen: *button,
                        interval: 1,
                        ease_factor: 2500,
                        taken_millis: 5_000,
                        review_kind: *kind,
                        ..Default::default()
                    },
                    true,
                )
                .unwrap();
        }
        card.id
    }

    fn set_budget(col: &mut Collection, deck: DeckId, budget: DeckTimeBudget) {
        let config = TimeBudgetConfig {
            decks: [(deck.0.to_string(), budget)].into(),
        };
        col.set_config(TIME_BUDGET_CONFIG_KEY, &config).unwrap();
    }

    fn queued_ids(col: &mut Collection) -> Vec<CardId> {
        col.get_queued_cards(100, false)
            .unwrap()
            .cards
            .into_iter()
            .map(|queued| queued.card.id)
            .collect()
    }

    #[test]
    fn budget_queue_orders_by_rwkv_recall() -> Result<()> {
        use RevlogReviewKind::*;
        let mut col = Collection::new();
        let parent = DeckAdder::new("Parent").add(&mut col);
        let child = DeckAdder::new("Parent::Child").add(&mut col);
        let weak = review_card(
            &mut col,
            child.id,
            &[
                (40, 1, Learning),
                (40, 1, Learning),
                (40, 3, Learning),
                (30, 1, Review),
                (20, 1, Review),
                (6, 1, Review),
            ],
        );
        let strong = review_card(
            &mut col,
            child.id,
            &[
                (90, 3, Learning),
                (80, 3, Review),
                (60, 4, Review),
                (30, 3, Review),
                (1, 4, Review),
            ],
        );
        let history = col.instant_history()?;
        assert!(format!("{history:?}").contains("cards: 2"));

        // without a budget, the normal queue applies (nothing due)
        col.set_current_deck(child.id)?;
        assert!(queued_ids(&mut col).is_empty());

        set_budget(
            &mut col,
            parent.id,
            DeckTimeBudget {
                max_dr: 100,
                ..Default::default()
            },
        );
        let queue = col.get_queued_cards(100, false)?;
        let ids: Vec<_> = queue.cards.iter().map(|queued| queued.card.id).collect();
        assert_eq!(ids, vec![weak, strong]);
        assert_eq!(queue.review_count, 2);
        // the remaining time is shown for the configured deck
        let status = col.time_budget_status()?;
        assert_eq!(status.decks.len(), 1);
        assert_eq!(status.decks[0].deck_id, parent.id.0);
        assert_eq!(status.decks[0].remaining_secs, 30 * 60);

        // answering is scheduled normally, and the cap removes the card
        set_budget(
            &mut col,
            parent.id,
            DeckTimeBudget {
                max_dr: 100,
                max_reviews_per_card: 1,
                ..Default::default()
            },
        );
        col.answer_good();
        let card = col.storage.get_card(weak)?.unwrap();
        assert!(card.due > col.timing_today()?.days_elapsed as i32);
        assert_eq!(queued_ids(&mut col), vec![strong]);
        Ok(())
    }

    #[test]
    fn used_budget_falls_back_to_minimum_recall() -> Result<()> {
        use RevlogReviewKind::*;
        let mut col = Collection::new();
        let deck = DeckAdder::new("Budget").add(&mut col);
        let card = review_card(&mut col, deck.id, &[(20, 3, Learning), (10, 3, Review)]);
        col.set_current_deck(deck.id)?;
        let budget = DeckTimeBudget {
            minutes: 1,
            max_dr: 100,
            min_dr: 0,
            ..Default::default()
        };
        set_budget(&mut col, deck.id, budget.clone());
        assert_eq!(queued_ids(&mut col), vec![card]);

        // a minute and a half studied today: only cards below the minimum
        col.storage.add_revlog_entry(
            &RevlogEntry {
                id: RevlogId(TimestampMillis::now().0 - 1000),
                cid: card,
                button_chosen: 3,
                ease_factor: 2500,
                taken_millis: 90_000,
                review_kind: Review,
                ..Default::default()
            },
            true,
        )?;
        assert!(queued_ids(&mut col).is_empty());
        set_budget(
            &mut col,
            deck.id,
            DeckTimeBudget {
                min_dr: 100,
                ..budget
            },
        );
        assert_eq!(queued_ids(&mut col), vec![card]);
        assert_eq!(col.time_budget_status()?.decks[0].remaining_secs, -30);
        Ok(())
    }

    fn candidate(id: i64, retrievability: f32) -> Candidate {
        Candidate {
            card_id: CardId(id),
            retrievability,
            learning: false,
        }
    }

    #[test]
    fn selection_orders_by_recall_and_applies_threshold() {
        let selected = select_candidates(
            vec![candidate(1, 0.9), candidate(2, 0.5), candidate(3, 0.97)],
            0.95,
            None,
        );
        assert_eq!(selected, vec![candidate(2, 0.5), candidate(1, 0.9)]);
    }

    #[test]
    fn last_answered_card_is_not_repeated_immediately() {
        let selected = select_candidates(
            vec![candidate(1, 0.4), candidate(2, 0.5)],
            0.95,
            Some(CardId(1)),
        );
        assert_eq!(selected[0].card_id, CardId(2));
        // ...unless it is the only card left
        let selected = select_candidates(vec![candidate(1, 0.4)], 0.95, Some(CardId(1)));
        assert_eq!(selected[0].card_id, CardId(1));
    }

    #[test]
    fn threshold_drops_to_minimum_once_budget_is_used() {
        let budget = DeckTimeBudget::default();
        assert_eq!(budget.threshold(1), 0.95);
        assert_eq!(budget.threshold(0), 0.60);
        assert_eq!(budget.threshold(-30), 0.60);
    }
}
