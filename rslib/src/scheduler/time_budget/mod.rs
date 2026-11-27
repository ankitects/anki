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
//!
//! New cards are shown while time is left, up to the daily new card limits
//! of the deck and its subdecks if `limit_new_cards` is set. Once the goal is
//! reached, the user may continue for the rest of the day
//! ([Collection::continue_time_budget]): reviews are then shown whatever
//! their recall, lowest first, so each card studied has a higher recall than
//! the last. New cards stay within their limits: with `limit_new_cards`, the
//! rest of the day's limit; without it, none (time was their only limit).

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
use crate::decks::limits::RemainingLimits;
use crate::decks::Deck;
use crate::prelude::*;
use crate::storage::ids_to_string;

/// Collection config key holding [TimeBudgetConfig].
pub(crate) const TIME_BUDGET_CONFIG_KEY: &str = "rwkvTimeBudget";
/// Collection config key holding the day on which each budget deck was
/// continued past its goal, keyed by deck id. Kept apart from
/// [TIME_BUDGET_CONFIG_KEY] so that saving the settings does not drop it.
pub(crate) const TIME_BUDGET_CONTINUED_KEY: &str = "rwkvTimeBudgetContinued";

/// Revlog entries that took study time today. Filtered previews (factor 0)
/// are not real answers.
const STUDIED_CONDITION: &str =
    "r.ease between 1 and 4 and r.type in (0, 1, 2, 3) and not (r.type = 3 and r.factor = 0)";

/// Stored settings: the time per deck, and the review and retention limits
/// shared by all decks with a budget.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
#[serde(default, rename_all = "camelCase")]
pub(crate) struct TimeBudgetConfig {
    /// Keyed by deck id.
    pub decks: HashMap<String, DeckBudgetEntry>,
    pub max_reviews_per_card: u32,
    pub min_dr: u32,
    pub max_dr: u32,
    /// Which group of cards is shown first.
    pub order: Vec<CardGroup>,
    /// Stop introducing new cards at the daily new card limits of the deck
    /// and its subdecks. Otherwise new cards are only limited by time.
    pub limit_new_cards: bool,
}

/// The groups a session draws from, in a configurable order.
#[derive(Serialize, Deserialize, Debug, Clone, Copy, PartialEq, Eq, Hash)]
#[serde(rename_all = "camelCase")]
pub(crate) enum CardGroup {
    /// Cards not answered yet today, lowest predicted recall first.
    FirstReview,
    /// New cards, within the deck's new card limit, in the deck's order.
    New,
    /// Cards answered earlier today, lowest predicted recall first.
    ReReview,
}

pub(crate) const DEFAULT_ORDER: [CardGroup; 3] =
    [CardGroup::FirstReview, CardGroup::New, CardGroup::ReReview];

/// Every group exactly once: `order` first, then any it leaves out in the
/// default order.
fn complete_order(order: &[CardGroup]) -> Vec<CardGroup> {
    let mut out: Vec<CardGroup> = vec![];
    for group in order.iter().chain(DEFAULT_ORDER.iter()) {
        if !out.contains(group) {
            out.push(*group);
        }
    }
    out
}

impl Default for TimeBudgetConfig {
    fn default() -> Self {
        let defaults = DeckTimeBudget::default();
        Self {
            decks: HashMap::new(),
            max_reviews_per_card: defaults.max_reviews_per_card,
            min_dr: defaults.min_dr,
            max_dr: defaults.max_dr,
            order: DEFAULT_ORDER.to_vec(),
            limit_new_cards: defaults.limit_new_cards,
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
#[serde(default, rename_all = "camelCase")]
pub(crate) struct DeckBudgetEntry {
    pub enabled: bool,
    /// Desired study time per day.
    pub minutes: u32,
    /// Settings this deck does not take from the shared ones. Kept in its
    /// own object, so the per-deck keys of an older format are still
    /// ignored.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub overrides: Option<DeckOverrides>,
}

impl Default for DeckBudgetEntry {
    fn default() -> Self {
        let defaults = DeckTimeBudget::default();
        Self {
            enabled: defaults.enabled,
            minutes: defaults.minutes,
            overrides: None,
        }
    }
}

/// What one deck uses in place of the shared settings. An unset field
/// (`None`) uses the shared one.
#[derive(Serialize, Deserialize, Debug, Clone, Default, PartialEq)]
#[serde(default, rename_all = "camelCase")]
pub(crate) struct DeckOverrides {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_reviews_per_card: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub min_dr: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_dr: Option<u32>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub limit_new_cards: Option<bool>,
    /// Study time for each day of the week, Monday first, in place of the
    /// deck's single [DeckBudgetEntry::minutes].
    #[serde(skip_serializing_if = "Option::is_none")]
    pub weekday_minutes: Option<Vec<u32>>,
}

impl DeckOverrides {
    /// The time set aside for `weekday` (0 is Monday), if any.
    fn minutes_for(&self, weekday: usize) -> Option<u32> {
        self.weekday_minutes.as_ref()?.get(weekday).copied()
    }
}

impl TimeBudgetConfig {
    /// The active budget of a deck on `weekday` (0 is Monday), with the
    /// shared limits applied where the deck does not override them.
    fn budget_for(&self, deck_id: DeckId, weekday: usize) -> Option<DeckTimeBudget> {
        let entry = self.decks.get(&deck_id.0.to_string())?;
        let overrides = entry.overrides.clone().unwrap_or_default();
        Some(DeckTimeBudget {
            enabled: entry.enabled,
            minutes: overrides.minutes_for(weekday).unwrap_or(entry.minutes),
            max_reviews_per_card: overrides
                .max_reviews_per_card
                .unwrap_or(self.max_reviews_per_card),
            min_dr: overrides.min_dr.unwrap_or(self.min_dr),
            max_dr: overrides.max_dr.unwrap_or(self.max_dr),
            order: complete_order(&self.order),
            limit_new_cards: overrides.limit_new_cards.unwrap_or(self.limit_new_cards),
        })
        .filter(DeckTimeBudget::is_active)
    }

    fn active_deck_ids(&self, weekday: usize) -> impl Iterator<Item = DeckId> + '_ {
        self.decks
            .keys()
            .filter_map(|id| id.parse().ok().map(DeckId))
            .filter(move |id| self.budget_for(*id, weekday).is_some())
    }
}

/// The settings that apply to one deck with a budget.
#[derive(Debug, Clone, PartialEq)]
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
    pub order: Vec<CardGroup>,
    /// New cards stop at the daily new card limits.
    pub limit_new_cards: bool,
}

impl Default for DeckTimeBudget {
    fn default() -> Self {
        Self {
            enabled: true,
            minutes: 30,
            max_reviews_per_card: 3,
            min_dr: 60,
            max_dr: 95,
            order: DEFAULT_ORDER.to_vec(),
            limit_new_cards: true,
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

/// Cards scored together and shown in turn, without scoring again.
const BATCH_SIZE: usize = 10;
/// The next batch is scored once this many cards of the current one are left
/// (after five of ten have been answered).
const REFILL_AT: usize = 5;

/// The next cards of a time-budget session, lowest predicted recall first.
/// Answered cards are removed; the batch is dropped whenever Anki's study
/// queues would be rebuilt (edits, undo, setting changes).
#[derive(Debug, Clone)]
pub(crate) struct TimeBudgetBatch {
    study_deck: DeckId,
    budget: DeckTimeBudget,
    day: u32,
    cards: Vec<Candidate>,
    /// Cards eligible when scored, minus those answered since.
    new_count: usize,
    learning_count: usize,
    review_count: usize,
}

impl TimeBudgetBatch {
    fn serves(&self, study_deck: DeckId, budget: &DeckTimeBudget, day: u32) -> bool {
        self.study_deck == study_deck && &self.budget == budget && self.day == day
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
struct Candidate {
    card_id: CardId,
    /// Unused for new cards.
    retrievability: f32,
    learning: bool,
    group: CardGroup,
}

impl Candidate {
    /// Whether the card may be shown, given the recall threshold and whether
    /// new cards may be introduced.
    fn eligible(&self, threshold: f32, new_allowed: bool) -> bool {
        match self.group {
            CardGroup::New => new_allowed,
            CardGroup::FirstReview | CardGroup::ReReview => self.retrievability < threshold,
        }
    }
}

/// Cards to show: the groups in `order`; reviews lowest recall first, new
/// cards in the order given. The card answered last is not repeated
/// straight away when another card can be shown instead.
fn select_candidates(
    candidates: Vec<Candidate>,
    threshold: f32,
    new_allowed: bool,
    order: &[CardGroup],
    last_answered: Option<CardId>,
) -> Vec<Candidate> {
    let (new_cards, mut reviews): (Vec<Candidate>, Vec<Candidate>) = candidates
        .into_iter()
        .filter(|candidate| candidate.eligible(threshold, new_allowed))
        .partition(|candidate| candidate.group == CardGroup::New);
    reviews.sort_by(|a, b| {
        a.retrievability
            .total_cmp(&b.retrievability)
            .then(a.card_id.cmp(&b.card_id))
    });
    let mut selected: Vec<Candidate> = vec![];
    for group in complete_order(order) {
        let source = if group == CardGroup::New {
            &new_cards
        } else {
            &reviews
        };
        selected.extend(source.iter().filter(|card| card.group == group));
    }
    if selected.len() > 1 && Some(selected[0].card_id) == last_answered {
        selected.swap(0, 1);
    }
    selected
}

/// How a session stands today.
struct SessionState {
    /// Negative once the budget is used up.
    remaining_secs: i64,
    /// Continued past the goal today.
    continued: bool,
}

impl SessionState {
    /// New cards are introduced while time is left, and once continued only
    /// within the daily new card limits.
    fn new_allowed(&self, budget: &DeckTimeBudget) -> bool {
        self.remaining_secs > 0 || (self.continued && budget.limit_new_cards)
    }

    /// The recall below which reviews are shown: any recall once continued.
    fn threshold(&self, budget: &DeckTimeBudget) -> f32 {
        if self.continued {
            f32::INFINITY
        } else {
            budget.threshold(self.remaining_secs)
        }
    }
}

/// Remaining new card limits of a deck tree; see
/// [Collection::time_budget_new_limits].
struct NewLimits {
    remaining: HashMap<DeckId, u32>,
    /// The decks whose limits a card in the key deck counts against.
    chains: HashMap<DeckId, Vec<DeckId>>,
}

/// The cards, in order, that fit within the limits of their deck and its
/// ancestors.
fn within_new_limits(cards: Vec<(CardId, DeckId)>, mut limits: NewLimits) -> Vec<CardId> {
    let mut out = vec![];
    for (card_id, deck_id) in cards {
        let Some(chain) = limits.chains.get(&deck_id) else {
            continue;
        };
        if chain
            .iter()
            .all(|deck| limits.remaining.get(deck).copied().unwrap_or(0) > 0)
        {
            for deck in chain {
                if let Some(left) = limits.remaining.get_mut(deck) {
                    *left -= 1;
                }
            }
            out.push(card_id);
        }
    }
    out
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
        let weekday = self.time_budget_weekday()?;
        if config.active_deck_ids(weekday).next().is_none() {
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
            if let Some(budget) = config.budget_for(deck.id, weekday) {
                return Ok(Some(ActiveTimeBudget { root: deck, budget }));
            }
        }
        Ok(None)
    }

    fn day_start_millis(&mut self) -> Result<i64> {
        Ok((self.timing_today()?.next_day_at.0 - 86_400) * 1000)
    }

    /// The day of the week the current scheduler day falls on, 0 for
    /// Monday. Taken from the day's start, so a session that runs past
    /// midnight keeps the weekday it began on, as the rollover hour says.
    pub(crate) fn time_budget_weekday(&mut self) -> Result<usize> {
        use chrono::Datelike;
        let day_start = TimestampSecs(self.timing_today()?.next_day_at.0 - 86_400);
        let offset = day_start.local_utc_offset()?;
        Ok(day_start.datetime(offset)?.weekday().num_days_from_monday() as usize)
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

    fn time_budget_continued_days(&self) -> HashMap<String, u32> {
        self.get_config_optional(TIME_BUDGET_CONTINUED_KEY)
            .unwrap_or_default()
    }

    /// Whether the budget of `root` was continued past its goal today.
    fn time_budget_continued(&mut self, root: DeckId) -> Result<bool> {
        let today = self.timing_today()?.days_elapsed;
        Ok(self.time_budget_continued_days().get(&root.0.to_string()) == Some(&today))
    }

    fn time_budget_session(&mut self, active: &ActiveTimeBudget) -> Result<SessionState> {
        Ok(SessionState {
            remaining_secs: self.remaining_budget_secs(active)?,
            continued: self.time_budget_continued(active.root.id)?,
        })
    }

    /// Keeps studying the current deck's budget for the rest of the day:
    /// reviews are shown whatever their recall, lowest first; new cards stay
    /// within their limits.
    pub(crate) fn continue_time_budget(&mut self) -> Result<()> {
        let deck_id = self.get_current_deck_id();
        let Some(active) = self.active_time_budget(deck_id)? else {
            return Ok(());
        };
        let today = self.timing_today()?.days_elapsed;
        let mut days = self.time_budget_continued_days();
        // only today's entries matter
        days.retain(|_, day| *day == today);
        days.insert(active.root.id.0.to_string(), today);
        self.transact_no_undo(|col| col.set_config(TIME_BUDGET_CONTINUED_KEY, &days).map(|_| ()))?;
        self.clear_study_queues();
        Ok(())
    }

    /// What the congrats screen shows for a deck studied with a budget.
    pub(crate) fn time_budget_congrats(
        &mut self,
        deck_id: DeckId,
    ) -> Result<Option<anki_proto::scheduler::TimeBudgetCongrats>> {
        let Some(active) = self.active_time_budget(deck_id)? else {
            return Ok(None);
        };
        let session = self.time_budget_session(&active)?;
        Ok(Some(anki_proto::scheduler::TimeBudgetCongrats {
            time_used_up: session.remaining_secs <= 0,
            continued: session.continued,
        }))
    }

    /// Time left for each deck with a budget. With `check_done`, also
    /// whether anything is left to study, which needs the RWKV model: a stale
    /// model is rebuilt first, so only call that in the background.
    pub(crate) fn time_budget_status(
        &mut self,
        check_done: bool,
    ) -> Result<TimeBudgetStatusResponse> {
        let config = self.time_budget_config();
        let weekday = self.time_budget_weekday()?;
        // Loading or replaying the model here, in the background, keeps the
        // first card of a session from waiting on it; with RWKV on all
        // decks there may be no budget deck to warm it up for.
        if check_done
            && (config.active_deck_ids(weekday).next().is_some() || self.rwkv_options().all_decks)
        {
            self.rebuild_stale_instant_history()?;
        }
        let mut decks = vec![];
        for deck_id in config.active_deck_ids(weekday).collect::<Vec<_>>() {
            let budget = config.budget_for(deck_id, weekday).expect("active deck");
            let Some(deck) = self.storage.get_deck(deck_id)? else {
                continue;
            };
            if deck.is_filtered() {
                continue;
            }
            let active = ActiveTimeBudget { root: deck, budget };
            let session = self.time_budget_session(&active)?;
            // The deck list requests this in the background after showing
            // the times, so loading or replaying the model here also keeps
            // the first card of a session from waiting on it.
            let threshold = session.threshold(&active.budget);
            let new_allowed = session.new_allowed(&active.budget);
            let done = check_done
                && !self
                    .time_budget_candidates(&active, deck_id)?
                    .iter()
                    .any(|candidate| candidate.eligible(threshold, new_allowed));
            decks.push(time_budget_status_response::Deck {
                deck_id: deck_id.0,
                budget_secs: active.budget.budget_secs() as u32,
                remaining_secs: session
                    .remaining_secs
                    .clamp(i32::MIN as i64, i32::MAX as i64) as i32,
                done,
                continued: session.continued,
            });
        }
        decks.sort_by_key(|deck| deck.deck_id);
        Ok(TimeBudgetStatusResponse { decks })
    }

    /// The study queue of a deck covered by a time budget. Cards are scored
    /// in batches: see [TimeBudgetBatch].
    pub(crate) fn get_time_budget_queued_cards(
        &mut self,
        active: &ActiveTimeBudget,
        study_deck: DeckId,
        fetch_limit: usize,
        intraday_learning_only: bool,
    ) -> Result<QueuedCards> {
        // Anki's own queues are not used while a budget applies; answering
        // without them is supported.
        self.state.card_queues = None;
        let session = self.time_budget_session(active)?;
        let threshold = session.threshold(&active.budget);
        let new_allowed = session.new_allowed(&active.budget);
        let day = self.timing_today()?.days_elapsed;

        let mut refilled = false;
        if !self
            .state
            .time_budget_batch
            .as_ref()
            .is_some_and(|batch| batch.serves(study_deck, &active.budget, day))
        {
            self.refill_time_budget_batch(active, study_deck, &session, day)?;
            refilled = true;
        }
        // A batch scored before the time ran out may hold cards that are no
        // longer eligible.
        let mut usable = self.usable_batch_cards(threshold, new_allowed);
        if usable.is_empty() && !refilled {
            self.refill_time_budget_batch(active, study_deck, &session, day)?;
            usable = self.usable_batch_cards(threshold, new_allowed);
        }
        let batch = self.state.time_budget_batch.as_ref().expect("filled above");
        let (mut new_count, mut learning_count, mut review_count) = if usable.is_empty() {
            (0, 0, 0)
        } else {
            (batch.new_count, batch.learning_count, batch.review_count)
        };
        // cards whose answers were undone are shown again first, as in Anki's
        // own queues. Inserting in reverse keeps the most recently undone
        // card at the front.
        for undone in self
            .time_budget_undone_candidates(study_deck)?
            .into_iter()
            .rev()
        {
            if let Some(index) = usable
                .iter()
                .position(|card| card.card_id == undone.card_id)
            {
                usable.remove(index);
            } else if undone.group == CardGroup::New {
                new_count += 1;
            } else if undone.learning {
                learning_count += 1;
            } else {
                review_count += 1;
            }
            usable.insert(0, undone);
        }

        let cards = if intraday_learning_only {
            vec![]
        } else {
            usable
                .iter()
                .take(fetch_limit)
                .map(|candidate| self.time_budget_queued_card(candidate))
                .collect::<Result<_>>()?
        };
        Ok(QueuedCards {
            cards,
            new_count,
            learning_count,
            review_count,
        })
    }

    fn usable_batch_cards(&self, threshold: f32, new_allowed: bool) -> Vec<Candidate> {
        self.state
            .time_budget_batch
            .as_ref()
            .map(|batch| {
                batch
                    .cards
                    .iter()
                    .filter(|card| card.eligible(threshold, new_allowed))
                    .copied()
                    .collect()
            })
            .unwrap_or_default()
    }

    /// Scores every card and keeps the best [BATCH_SIZE].
    fn refill_time_budget_batch(
        &mut self,
        active: &ActiveTimeBudget,
        study_deck: DeckId,
        session: &SessionState,
        day: u32,
    ) -> Result<()> {
        let candidates = self.time_budget_candidates(active, study_deck)?;
        let last_answered = self.card_answered_last_today()?;
        let selected = select_candidates(
            candidates,
            session.threshold(&active.budget),
            session.new_allowed(&active.budget),
            &active.budget.order,
            last_answered,
        );
        let new_count = selected
            .iter()
            .filter(|c| c.group == CardGroup::New)
            .count();
        let learning_count = selected.iter().filter(|c| c.learning).count();
        self.state.time_budget_batch = Some(TimeBudgetBatch {
            study_deck,
            budget: active.budget.clone(),
            day,
            review_count: selected.len() - learning_count - new_count,
            new_count,
            learning_count,
            cards: selected.into_iter().take(BATCH_SIZE).collect(),
        });
        Ok(())
    }

    /// After an undo: the undone card is shown again first.
    ///
    /// The batch is kept, so going back does not pause the reviewer while
    /// every card is scored again; the undone answer changes the other
    /// cards' predictions very little, and the background prefetch replaces
    /// the batch once the model has been replayed
    /// ([Collection::prefetch_time_budget_cards]). An undo of anything but
    /// an answer may have changed the cards themselves, so the batch is
    /// dropped then (as it already is for the equivalent forward
    /// operations).
    pub(crate) fn time_budget_after_undo(&mut self, undone_answer: Option<CardId>) {
        let Some(card_id) = undone_answer else {
            self.state.time_budget_batch = None;
            self.state.time_budget_undone_cards.clear();
            return;
        };
        let undone = &mut self.state.time_budget_undone_cards;
        undone.retain(|id| *id != card_id);
        undone.insert(0, card_id);
        undone.truncate(BATCH_SIZE);
    }

    /// The cards whose answers were undone, in the order they are shown
    /// again: only those still in the study deck's tree and able to be
    /// studied.
    fn time_budget_undone_candidates(&mut self, study_deck: DeckId) -> Result<Vec<Candidate>> {
        if self.state.time_budget_undone_cards.is_empty() {
            return Ok(vec![]);
        }
        let study_deck = self
            .storage
            .get_deck(study_deck)?
            .or_not_found(study_deck)?;
        let deck_ids = self.storage.deck_id_with_children(&study_deck)?;
        let answers_today = self.answers_today_by_card()?;
        let mut out = vec![];
        for card_id in self.state.time_budget_undone_cards.clone() {
            let Some(card) = self.storage.get_card(card_id)? else {
                continue;
            };
            if !deck_ids.contains(&card.deck_id) {
                continue;
            }
            let group = match card.queue {
                CardQueue::New => CardGroup::New,
                CardQueue::Learn | CardQueue::Review | CardQueue::DayLearn => {
                    if answers_today.contains_key(&card_id) {
                        CardGroup::ReReview
                    } else {
                        CardGroup::FirstReview
                    }
                }
                _ => continue,
            };
            out.push(Candidate {
                card_id,
                retrievability: 0.0,
                learning: matches!(card.queue, CardQueue::Learn | CardQueue::DayLearn),
                group,
            });
        }
        Ok(out)
    }

    pub(crate) fn remove_from_time_budget_batch(&mut self, card_id: CardId) {
        self.state
            .time_budget_undone_cards
            .retain(|id| *id != card_id);
        if let Some(batch) = self.state.time_budget_batch.as_mut() {
            if let Some(index) = batch.cards.iter().position(|card| card.card_id == card_id) {
                let card = batch.cards.remove(index);
                if card.group == CardGroup::New {
                    batch.new_count = batch.new_count.saturating_sub(1);
                } else if card.learning {
                    batch.learning_count = batch.learning_count.saturating_sub(1);
                } else {
                    batch.review_count = batch.review_count.saturating_sub(1);
                }
            }
        }
    }

    /// Scores the next batch ahead of time once [REFILL_AT] cards of the
    /// current one are left, or if there is none yet. Called in the
    /// background after each answer, so the reviewer rarely waits on
    /// scoring. Returns true if a batch was scored.
    pub(crate) fn prefetch_time_budget_cards(&mut self) -> Result<bool> {
        let study_deck = self.get_current_deck_id();
        let Some(active) = self.active_time_budget(study_deck)? else {
            return Ok(false);
        };
        // A full replay (after an undo, say) happens here, off the
        // reviewer's thread; the batch scored with the stale model is
        // replaced.
        let rebuilt = self.rebuild_stale_instant_history()?;
        let day = self.timing_today()?.days_elapsed;
        let needed = rebuilt
            || match self.state.time_budget_batch.as_ref() {
                Some(batch) if batch.serves(study_deck, &active.budget, day) => {
                    batch.cards.len() <= REFILL_AT
                }
                _ => true,
            };
        if needed {
            let session = self.time_budget_session(&active)?;
            self.refill_time_budget_batch(&active, study_deck, &session, day)?;
        }
        Ok(needed)
    }

    fn time_budget_queued_card(&mut self, candidate: &Candidate) -> Result<QueuedCard> {
        let card = self
            .storage
            .get_card(candidate.card_id)?
            .or_not_found(candidate.card_id)?;
        let states = self.get_scheduling_states(card.id)?;
        Ok(QueuedCard {
            context: super::queue::new_scheduling_context(self, &card)?,
            kind: if candidate.group == CardGroup::New {
                QueueEntryKind::New
            } else if candidate.learning {
                QueueEntryKind::Learning
            } else {
                QueueEntryKind::Review
            },
            states,
            card,
        })
    }

    /// Learning, review and relearning cards of the study deck and its
    /// subdecks, with their predicted recall, and the new cards the deck may
    /// introduce today. Cards at today's review cap are left out.
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
        let new_cards =
            self.time_budget_new_cards(&study_deck, &ids, active.budget.limit_new_cards)?;
        if card_ids.is_empty() {
            return Ok(new_cards);
        }
        let cards = self.all_cards_for_ids(&card_ids, false)?;
        let retrievabilities = self.predict_recall(&cards)?;
        Ok(cards
            .iter()
            .zip(retrievabilities)
            .map(|(card, retrievability)| Candidate {
                card_id: card.id,
                retrievability,
                learning: matches!(card.queue, CardQueue::Learn | CardQueue::DayLearn),
                group: if answers_today.contains_key(&card.id) {
                    CardGroup::ReReview
                } else {
                    CardGroup::FirstReview
                },
            })
            .chain(new_cards)
            .collect())
    }

    /// The RWKV-Instant probability of recall of each card if shown now, in
    /// the order the cards were given in.
    pub(crate) fn predict_recall(&mut self, cards: &[Card]) -> Result<Vec<f32>> {
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
        self.instant_history()?.predict(&queries, &timing)
    }

    /// New cards of the study deck's tree in due (position) order. With
    /// `limited`, only as many as the daily new card limits of the study
    /// deck and of each subdeck allow (the review limit does not apply).
    fn time_budget_new_cards(
        &mut self,
        study_deck: &Deck,
        deck_ids_sql: &str,
        limited: bool,
    ) -> Result<Vec<Candidate>> {
        let cards: Vec<(CardId, DeckId)> = self
            .storage
            .db
            .prepare(&format!(
                "select id, did from cards where did in {deck_ids_sql} and queue = {}
                 order by due, ord, id",
                CardQueue::New as i8
            ))?
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))?
            .collect::<std::result::Result<_, _>>()?;
        let card_ids = if limited {
            let limits = self.time_budget_new_limits(study_deck)?;
            within_new_limits(cards, limits)
        } else {
            cards.into_iter().map(|(card_id, _)| card_id).collect()
        };
        Ok(card_ids
            .into_iter()
            .map(|card_id| Candidate {
                card_id,
                retrievability: 0.0,
                learning: false,
                group: CardGroup::New,
            })
            .collect())
    }

    /// The remaining new card limit of the study deck and each of its
    /// subdecks today, with the decks each limit applies to: the deck itself
    /// and its ancestors up to the study deck.
    fn time_budget_new_limits(&mut self, study_deck: &Deck) -> Result<NewLimits> {
        let today = self.timing_today()?.days_elapsed;
        let mut remaining = HashMap::new();
        let mut chains = HashMap::new();
        let tree = self.storage.child_decks(study_deck)?;
        for deck in std::iter::once(study_deck.clone()).chain(tree) {
            let config = match deck.config_id() {
                Some(id) => self.get_deck_config(id, true)?,
                None => None,
            };
            remaining.insert(
                deck.id,
                RemainingLimits::new(&deck, config.as_ref(), today, true).new,
            );
            let mut chain = vec![deck.id];
            if deck.id != study_deck.id {
                for parent in self.storage.parent_decks(&deck)? {
                    chain.push(parent.id);
                    if parent.id == study_deck.id {
                        break;
                    }
                }
            }
            chains.insert(deck.id, chain);
        }
        Ok(NewLimits { remaining, chains })
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
        // Revlog ids must not collide: on a collision the id is moved to the
        // current time, which would count as study time today.
        static NEXT_OFFSET: std::sync::atomic::AtomicI64 = std::sync::atomic::AtomicI64::new(0);
        let now = TimestampMillis::now().0;
        for (days_ago, button, kind) in answers {
            let offset = NEXT_OFFSET.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
            col.storage
                .add_revlog_entry(
                    &RevlogEntry {
                        id: RevlogId(now - days_ago * DAY_MILLIS - 60_000 - offset),
                        cid: card.id,
                        button_chosen: *button,
                        interval: 1,
                        ease_factor: 2500,
                        taken_millis: 5_000,
                        review_kind: *kind,
                        ..Default::default()
                    },
                    false,
                )
                .unwrap();
        }
        card.id
    }

    fn set_budget(col: &mut Collection, deck: DeckId, budget: DeckTimeBudget) {
        let config = TimeBudgetConfig {
            decks: [(
                deck.0.to_string(),
                DeckBudgetEntry {
                    enabled: budget.enabled,
                    minutes: budget.minutes,
                    overrides: None,
                },
            )]
            .into(),
            max_reviews_per_card: budget.max_reviews_per_card,
            min_dr: budget.min_dr,
            max_dr: budget.max_dr,
            order: budget.order,
            limit_new_cards: budget.limit_new_cards,
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
        let status = col.time_budget_status(true)?;
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
    fn limits_are_shared_by_all_decks() {
        let config: TimeBudgetConfig = serde_json::from_str(
            r#"{"decks": {"1": {"minutes": 65, "minDr": 10}, "2": {"enabled": false}},
                "maxReviewsPerCard": 2, "maxDr": 90}"#,
        )
        .unwrap();
        let budget = config.budget_for(DeckId(1), 0).unwrap();
        assert_eq!(
            budget,
            DeckTimeBudget {
                enabled: true,
                minutes: 65,
                max_reviews_per_card: 2,
                // unset shared values use the defaults; older per-deck values
                // are ignored
                min_dr: 60,
                max_dr: 90,
                order: DEFAULT_ORDER.to_vec(),
                limit_new_cards: true,
            }
        );
        assert_eq!(config.budget_for(DeckId(2), 0), None);
        assert_eq!(
            config.active_deck_ids(0).collect::<Vec<_>>(),
            vec![DeckId(1)]
        );
    }

    #[test]
    fn deck_overrides_replace_the_shared_limits() {
        let config: TimeBudgetConfig = serde_json::from_str(
            r#"{"decks": {
                  "1": {"minutes": 30, "overrides": {
                          "maxReviewsPerCard": 1, "minDr": 50, "limitNewCards": false,
                          "weekdayMinutes": [13, 20, 20, 20, 20, 30, 30]}},
                  "2": {"minutes": 30}},
                "maxReviewsPerCard": 2, "minDr": 60, "maxDr": 90}"#,
        )
        .unwrap();
        // Monday: the deck's own limits, and Monday's time
        let budget = config.budget_for(DeckId(1), 0).unwrap();
        assert_eq!(
            budget,
            DeckTimeBudget {
                enabled: true,
                minutes: 13,
                max_reviews_per_card: 1,
                min_dr: 50,
                // unset: the shared maximum still applies
                max_dr: 90,
                order: DEFAULT_ORDER.to_vec(),
                limit_new_cards: false,
            }
        );
        // Saturday has its own time
        assert_eq!(config.budget_for(DeckId(1), 5).unwrap().minutes, 30);
        // a deck without overrides is unaffected
        let plain = config.budget_for(DeckId(2), 0).unwrap();
        assert_eq!(
            (plain.minutes, plain.max_reviews_per_card, plain.min_dr),
            (30, 2, 60)
        );
        // a week with fewer days than expected falls back to the deck's time
        let short: TimeBudgetConfig = serde_json::from_str(
            r#"{"decks": {"1": {"minutes": 45, "overrides": {"weekdayMinutes": [10]}}}}"#,
        )
        .unwrap();
        assert_eq!(short.budget_for(DeckId(1), 0).unwrap().minutes, 10);
        assert_eq!(short.budget_for(DeckId(1), 3).unwrap().minutes, 45);
    }

    #[test]
    fn overrides_are_kept_when_the_settings_are_written_back() {
        let json = r#"{"decks": {"1": {"enabled": true, "minutes": 30,
                       "overrides": {"minDr": 50, "weekdayMinutes": [13, 30]}}},
                       "maxReviewsPerCard": 3, "minDr": 60, "maxDr": 95,
                       "order": ["firstReview", "new", "reReview"],
                       "limitNewCards": true}"#;
        let config: TimeBudgetConfig = serde_json::from_str(json).unwrap();
        let written: TimeBudgetConfig =
            serde_json::from_str(&serde_json::to_string(&config).unwrap()).unwrap();
        assert_eq!(written, config);
        // unset fields are left out rather than written as null
        let text = serde_json::to_string(&config).unwrap();
        assert!(text.contains(r#""minDr":50"#) && text.contains(r#""weekdayMinutes":[13,30]"#));
        assert!(!text.contains("null"));
        // a deck with no overrides does not grow an empty object
        let plain: TimeBudgetConfig =
            serde_json::from_str(r#"{"decks": {"1": {"minutes": 30}}}"#).unwrap();
        assert!(!serde_json::to_string(&plain).unwrap().contains("overrides"));
    }

    #[test]
    fn the_weekday_comes_from_the_start_of_the_scheduler_day() -> Result<()> {
        use chrono::Datelike;
        let mut col = Collection::new();
        let timing = col.timing_today()?;
        let day_start = TimestampSecs(timing.next_day_at.0 - 86_400);
        let expected = day_start
            .datetime(day_start.local_utc_offset()?)?
            .weekday()
            .num_days_from_monday() as usize;
        assert_eq!(col.time_budget_weekday()?, expected);
        assert!(col.time_budget_weekday()? < 7);
        Ok(())
    }

    #[test]
    fn cards_are_scored_in_batches_and_refilled_ahead() -> Result<()> {
        use RevlogReviewKind::*;
        let mut col = Collection::new();
        let deck = DeckAdder::new("Batch").add(&mut col);
        for index in 0..16 {
            let button = if index % 2 == 0 { 1 } else { 3 };
            review_card(
                &mut col,
                deck.id,
                &[(30 + index, 3, Learning), (10, button, Review)],
            );
        }
        col.set_current_deck(deck.id)?;
        set_budget(
            &mut col,
            deck.id,
            DeckTimeBudget {
                max_dr: 100,
                max_reviews_per_card: 1,
                ..Default::default()
            },
        );
        let batch_len =
            |col: &Collection| col.state.time_budget_batch.as_ref().unwrap().cards.len();

        // one batch of ten is scored; the counts cover every eligible card
        let queue = col.get_queued_cards(100, false)?;
        let first: Vec<_> = queue.cards.iter().map(|queued| queued.card.id).collect();
        assert_eq!(first.len(), 10);
        assert_eq!(queue.review_count, 16);

        // answers are served from the batch without scoring again
        col.answer_good();
        assert_eq!(batch_len(&col), 9);
        assert_eq!(queued_ids(&mut col), first[1..]);
        for _ in 0..3 {
            col.answer_good();
        }
        assert!(!col.prefetch_time_budget_cards()?);
        assert_eq!(batch_len(&col), 6);

        // after the fifth answer, the next batch is scored ahead of time
        col.answer_good();
        assert_eq!(batch_len(&col), 5);
        assert!(col.prefetch_time_budget_cards()?);
        assert_eq!(batch_len(&col), 10);
        assert_eq!(col.get_queued_cards(1, false)?.review_count, 11);

        // without a prefetch, a used-up batch is refilled when needed
        for _ in 0..10 {
            col.answer_good();
        }
        assert_eq!(batch_len(&col), 0);
        assert_eq!(queued_ids(&mut col).len(), 1);
        col.answer_good();
        assert!(queued_ids(&mut col).is_empty());
        Ok(())
    }

    #[test]
    fn new_cards_follow_the_deck_limit_and_order() -> Result<()> {
        use RevlogReviewKind::*;
        let mut col = Collection::new();
        let deck = DeckAdder::new("Mixed")
            .with_config(|config| config.inner.new_per_day = 2)
            .add(&mut col);
        let review = review_card(&mut col, deck.id, &[(20, 3, Learning), (10, 1, Review)]);
        let new_cards: Vec<CardId> = (0..3)
            .map(|_| CardAdder::new().deck(deck.id).add(&mut col)[0].id)
            .collect();
        col.set_current_deck(deck.id)?;
        let mut budget = DeckTimeBudget {
            max_dr: 100,
            limit_new_cards: false,
            ..Default::default()
        };
        // without the new card limit, every new card while time is left
        set_budget(&mut col, deck.id, budget.clone());
        assert_eq!(
            queued_ids(&mut col),
            vec![review, new_cards[0], new_cards[1], new_cards[2]]
        );

        budget.limit_new_cards = true;
        set_budget(&mut col, deck.id, budget.clone());
        // first reviews, then at most two new cards in deck order
        let queue = col.get_queued_cards(100, false)?;
        let ids: Vec<_> = queue.cards.iter().map(|queued| queued.card.id).collect();
        assert_eq!(ids, vec![review, new_cards[0], new_cards[1]]);
        assert_eq!((queue.new_count, queue.review_count), (2, 1));
        assert_eq!(queue.cards[1].kind, QueueEntryKind::New);

        budget.order = vec![CardGroup::New, CardGroup::FirstReview, CardGroup::ReReview];
        set_budget(&mut col, deck.id, budget);
        assert_eq!(
            queued_ids(&mut col),
            vec![new_cards[0], new_cards[1], review]
        );
        Ok(())
    }

    #[test]
    fn new_card_limit_applies_to_subdecks() -> Result<()> {
        let mut col = Collection::new();
        let parent = DeckAdder::new("Parent")
            .with_config(|config| config.inner.new_per_day = 3)
            .add(&mut col);
        let child = DeckAdder::new("Parent::Child")
            .with_config(|config| config.inner.new_per_day = 1)
            .add(&mut col);
        let in_child: Vec<CardId> = (0..2)
            .map(|_| CardAdder::new().deck(child.id).add(&mut col)[0].id)
            .collect();
        let in_parent: Vec<CardId> = (0..3)
            .map(|_| CardAdder::new().deck(parent.id).add(&mut col)[0].id)
            .collect();
        col.set_current_deck(parent.id)?;
        set_budget(
            &mut col,
            parent.id,
            DeckTimeBudget {
                limit_new_cards: true,
                ..Default::default()
            },
        );
        // one from the child (its limit), then the parent's up to its limit
        assert_eq!(
            queued_ids(&mut col),
            vec![in_child[0], in_parent[0], in_parent[1]]
        );
        Ok(())
    }

    #[test]
    fn answers_can_be_undone() -> Result<()> {
        use RevlogReviewKind::*;
        let mut col = Collection::new();
        let deck = DeckAdder::new("Budget").add(&mut col);
        let first = review_card(&mut col, deck.id, &[(20, 3, Learning), (10, 1, Review)]);
        let second = review_card(&mut col, deck.id, &[(30, 3, Learning), (10, 1, Review)]);
        col.set_current_deck(deck.id)?;
        set_budget(
            &mut col,
            deck.id,
            DeckTimeBudget {
                max_dr: 100,
                ..Default::default()
            },
        );
        let shown = queued_ids(&mut col)[0];
        col.answer_good();
        col.prefetch_time_budget_cards()?;
        let scored = col.state.time_budget_batch.clone().unwrap().cards;
        col.undo()?;
        // the batch is kept, so going back does not pause while every card
        // is scored again
        assert_eq!(col.state.time_budget_batch.as_ref().unwrap().cards, scored);
        // the undone card is shown again, first
        assert_eq!(queued_ids(&mut col)[0], shown);
        col.answer_good();
        col.undo()?;
        col.redo()?;
        col.undo()?;
        assert_eq!(queued_ids(&mut col)[0], shown);
        assert!(queued_ids(&mut col).contains(&first) && queued_ids(&mut col).contains(&second));
        Ok(())
    }

    #[test]
    fn going_back_twice_shows_both_cards_without_scoring_again() -> Result<()> {
        use RevlogReviewKind::*;
        let mut col = Collection::new();
        let deck = DeckAdder::new("Budget").add(&mut col);
        for index in 0..4 {
            review_card(
                &mut col,
                deck.id,
                &[(30 + index, 3, Learning), (10, 1, Review)],
            );
        }
        col.set_current_deck(deck.id)?;
        set_budget(
            &mut col,
            deck.id,
            DeckTimeBudget {
                max_dr: 100,
                ..Default::default()
            },
        );
        let order = queued_ids(&mut col);
        assert_eq!(order.len(), 4);
        col.answer_good();
        col.answer_good();
        let scored = col.state.time_budget_batch.clone().unwrap().cards;

        col.undo()?;
        col.undo()?;
        assert_eq!(col.state.time_budget_batch.as_ref().unwrap().cards, scored);
        // both cards are shown again, the one answered first at the front
        let queue = col.get_queued_cards(100, false)?;
        let ids: Vec<_> = queue.cards.iter().map(|queued| queued.card.id).collect();
        assert_eq!(ids[..2], order[..2]);
        assert_eq!(ids.len(), 4);
        assert_eq!(queue.review_count, 4);
        // answering them again picks up where it left off
        col.answer_good();
        assert_eq!(queued_ids(&mut col)[0], order[1]);
        Ok(())
    }

    #[test]
    fn new_card_answers_can_be_undone() -> Result<()> {
        let mut col = Collection::new();
        let deck = DeckAdder::new("Budget").add(&mut col);
        let new_cards: Vec<CardId> = (0..3)
            .map(|_| CardAdder::new().deck(deck.id).add(&mut col)[0].id)
            .collect();
        col.set_current_deck(deck.id)?;
        set_budget(&mut col, deck.id, DeckTimeBudget::default());
        for _ in 0..3 {
            assert_eq!(queued_ids(&mut col)[0], new_cards[0]);
            col.answer_again();
            col.prefetch_time_budget_cards()?;
            col.get_queued_cards(1, false)?;
            col.undo()?;
            let queue = col.get_queued_cards(100, false)?;
            assert_eq!(queue.cards[0].card.id, new_cards[0]);
            assert_eq!(queue.cards[0].kind, QueueEntryKind::New);
        }
        // answering after the undo works, with the model rebuilt or not
        col.answer_good();
        col.answer_good();
        col.prefetch_time_budget_cards()?;
        col.answer_good();
        Ok(())
    }

    #[test]
    fn continuing_shows_reviews_at_any_recall() -> Result<()> {
        use RevlogReviewKind::*;
        let mut col = Collection::new();
        let deck = DeckAdder::new("Budget").add(&mut col);
        let review = review_card(
            &mut col,
            deck.id,
            &[
                (90, 3, Learning),
                (80, 3, Review),
                (60, 4, Review),
                (2, 4, Review),
            ],
        );
        let new_card = CardAdder::new().deck(deck.id).add(&mut col)[0].id;
        col.set_current_deck(deck.id)?;
        // the review is well above the maximum: only the new card is shown
        let budget = DeckTimeBudget {
            minutes: 1,
            min_dr: 10,
            max_dr: 20,
            limit_new_cards: false,
            ..Default::default()
        };
        set_budget(&mut col, deck.id, budget.clone());
        assert_eq!(queued_ids(&mut col), vec![new_card]);
        // a minute and a half studied today: the goal is reached
        col.storage.add_revlog_entry(
            &RevlogEntry {
                id: RevlogId(TimestampMillis::now().0 - 1000),
                cid: new_card,
                button_chosen: 3,
                taken_millis: 90_000,
                review_kind: Learning,
                ..Default::default()
            },
            true,
        )?;
        assert!(queued_ids(&mut col).is_empty());
        let congrats = col.congrats_info()?.time_budget.unwrap();
        assert!(congrats.time_used_up && !congrats.continued);

        // continuing shows the review whatever its recall; time was the new
        // card's only limit, so it is not shown
        col.continue_time_budget()?;
        assert_eq!(queued_ids(&mut col), vec![review]);
        assert!(col.congrats_info()?.time_budget.unwrap().continued);
        let status = col.time_budget_status(true)?;
        assert!(status.decks[0].continued && !status.decks[0].done);

        // with the new card limit, new cards continue within it
        set_budget(
            &mut col,
            deck.id,
            DeckTimeBudget {
                limit_new_cards: true,
                ..budget
            },
        );
        assert_eq!(queued_ids(&mut col), vec![review, new_card]);
        Ok(())
    }

    #[test]
    fn stats_show_target_band_of_current_deck_budget() -> Result<()> {
        let mut col = Collection::new();
        let parent = DeckAdder::new("Parent").add(&mut col);
        let child = DeckAdder::new("Parent::Child").add(&mut col);
        col.set_current_deck(child.id)?;
        let band = |col: &mut Collection, search| -> Result<_> {
            let retention = col
                .graph_data_for_search(search, 30)?
                .retention_over_time
                .unwrap();
            Ok((retention.target_min, retention.target_max))
        };
        assert_eq!(band(&mut col, "deck:current")?, (None, None));

        set_budget(
            &mut col,
            parent.id,
            DeckTimeBudget {
                min_dr: 70,
                max_dr: 90,
                ..Default::default()
            },
        );
        assert_eq!(band(&mut col, "deck:current")?, (Some(0.7), Some(0.9)));
        // the whole collection is not covered by one budget
        assert_eq!(band(&mut col, "")?, (None, None));
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
        assert!(!col.time_budget_status(true)?.decks[0].done);

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
        // time is up and nothing is below the minimum: done for today
        assert!(col.time_budget_status(true)?.decks[0].done);
        set_budget(
            &mut col,
            deck.id,
            DeckTimeBudget {
                min_dr: 100,
                ..budget
            },
        );
        assert_eq!(queued_ids(&mut col), vec![card]);
        let status = col.time_budget_status(true)?;
        assert_eq!(status.decks[0].remaining_secs, -30);
        assert!(!status.decks[0].done);
        Ok(())
    }

    fn candidate(id: i64, retrievability: f32) -> Candidate {
        grouped(id, retrievability, CardGroup::FirstReview)
    }

    fn grouped(id: i64, retrievability: f32, group: CardGroup) -> Candidate {
        Candidate {
            card_id: CardId(id),
            retrievability,
            learning: false,
            group,
        }
    }

    fn select(candidates: Vec<Candidate>, last_answered: Option<CardId>) -> Vec<Candidate> {
        select_candidates(candidates, 0.95, true, &DEFAULT_ORDER, last_answered)
    }

    #[test]
    fn selection_orders_by_recall_and_applies_threshold() {
        let selected = select(
            vec![candidate(1, 0.9), candidate(2, 0.5), candidate(3, 0.97)],
            None,
        );
        assert_eq!(selected, vec![candidate(2, 0.5), candidate(1, 0.9)]);
    }

    #[test]
    fn last_answered_card_is_not_repeated_immediately() {
        let selected = select(vec![candidate(1, 0.4), candidate(2, 0.5)], Some(CardId(1)));
        assert_eq!(selected[0].card_id, CardId(2));
        // ...unless it is the only card left
        let selected = select(vec![candidate(1, 0.4)], Some(CardId(1)));
        assert_eq!(selected[0].card_id, CardId(1));
    }

    #[test]
    fn groups_follow_the_configured_order() {
        use CardGroup::*;
        let cards = vec![
            grouped(1, 0.3, ReReview),
            grouped(2, 0.8, FirstReview),
            grouped(3, 0.0, New),
            grouped(4, 0.5, FirstReview),
            grouped(5, 0.0, New),
            grouped(6, 0.99, ReReview),
        ];
        let ids = |selected: Vec<Candidate>| -> Vec<i64> {
            selected.iter().map(|card| card.card_id.0).collect()
        };
        // default: first reviews by recall, new cards in deck order, then re-reviews
        assert_eq!(ids(select(cards.clone(), None)), vec![4, 2, 3, 5, 1]);
        assert_eq!(
            ids(select_candidates(
                cards.clone(),
                0.95,
                true,
                &[ReReview, New, FirstReview],
                None
            )),
            vec![1, 3, 5, 4, 2]
        );
        // an incomplete order falls back to the default for the rest
        assert_eq!(
            ids(select_candidates(cards.clone(), 0.95, true, &[New], None)),
            vec![3, 5, 4, 2, 1]
        );
        // no new cards once the time is used up
        assert_eq!(
            ids(select_candidates(cards, 0.95, false, &DEFAULT_ORDER, None)),
            vec![4, 2, 1]
        );
    }

    #[test]
    fn threshold_drops_to_minimum_once_budget_is_used() {
        let budget = DeckTimeBudget::default();
        assert_eq!(budget.threshold(1), 0.95);
        assert_eq!(budget.threshold(0), 0.60);
        assert_eq!(budget.threshold(-30), 0.60);
    }
}
