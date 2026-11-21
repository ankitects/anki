// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Replays the review log into the RWKV-Instant model and keeps the warmed
//! model in memory, so recall predictions can be made while studying.
//!
//! Replay follows the JSchoreels/anki fork's canonical history rules:
//! - only real answers (ease 1-4) of learning, review, relearning, filtered,
//!   manual (Grade Now) and rescheduled kinds; filtered previews (factor 0) are
//!   skipped
//! - a card's history restarts after Forget (a manual entry with ease 0 and
//!   factor 0), and at its latest learning start (a learning answer not
//!   preceded by another learning answer)
//! - cards that are currently new are skipped.

use std::collections::HashMap;
use std::fmt;

use crate::card::CardQueue;
use crate::card::CardType;
use crate::prelude::*;
use crate::rwkv::instant::InstantModel;
use crate::rwkv::ReviewInput;
use crate::scheduler::timing::SchedTimingToday;

/// Answers fed to the model per call during a full replay.
const REPLAY_CHUNK: usize = 16_384;

/// Matches Forget's revlog entry.
const RESET_CONDITION: &str = "type = 4 and ease = 0 and factor = 0";

/// Revlog entries that count as answers for the RWKV history.
const ANSWER_CONDITION: &str = "r.ease between 1 and 4 and r.type in (0, 1, 2, 3, 4, 5) \
     and not (r.type = 3 and r.factor = 0)";

/// The dataset's review states (the fork's `RwkvReviewState`): 0 marks a
/// learning start; other answers are the revlog kind + 1.
const LEARN_START_STATE: i64 = 0;

#[derive(Debug, Clone)]
struct HistoryRow {
    review_id: i64,
    card_id: i64,
    note_id: i64,
    deck_id: i64,
    preset_id: Option<i64>,
    ease: u8,
    duration_millis: i64,
    review_kind: i64,
}

#[derive(Debug, Clone, Copy)]
struct CardHistory {
    last_review_id: i64,
    last_kind: i64,
}

/// The day a review belongs to, relative to the scheduler's current day.
fn historical_day_offset(review_id: i64, timing: &SchedTimingToday) -> i64 {
    let review_secs = review_id / 1000;
    let days_before_today = (timing.next_day_at.0 - 1 - review_secs).max(0) / 86_400;
    (timing.days_elapsed as i64 - days_before_today).max(0)
}

pub(crate) struct InstantHistory {
    model: InstantModel,
    /// Scheduler day the replay was built on. Rebuilt on rollover so that
    /// deck moves and preset changes are picked up at least daily.
    day: u32,
    next_day_at: i64,
    last_revlog_id: i64,
    revlog_count: i64,
    cards: HashMap<i64, CardHistory>,
}

impl fmt::Debug for InstantHistory {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("InstantHistory")
            .field("day", &self.day)
            .field("last_revlog_id", &self.last_revlog_id)
            .field("revlog_count", &self.revlog_count)
            .field("cards", &self.cards.len())
            .finish()
    }
}

/// What a card looks like to the model right now.
pub(crate) struct QueryCard<'a> {
    pub card: &'a Card,
    pub preset_id: Option<i64>,
}

impl InstantHistory {
    fn new(timing: &SchedTimingToday) -> Result<Self> {
        Ok(Self {
            model: InstantModel::load().or_invalid("RWKV model")?,
            day: timing.days_elapsed,
            next_day_at: timing.next_day_at.0,
            last_revlog_id: 0,
            revlog_count: 0,
            cards: HashMap::new(),
        })
    }

    /// Predicted probability of recall for each card if shown now.
    pub(crate) fn predict(
        &mut self,
        cards: &[QueryCard<'_>],
        timing: &SchedTimingToday,
    ) -> Result<Vec<f32>> {
        let queries = cards
            .iter()
            .map(|query| self.query_input(query, timing))
            .collect();
        self.model.predict(queries).or_invalid("RWKV prediction")
    }

    fn query_input(&self, query: &QueryCard<'_>, timing: &SchedTimingToday) -> ReviewInput {
        let card = query.card;
        // Prefer the replayed history, so the query lines up with the state.
        let last_review_secs = self
            .cards
            .get(&card.id.0)
            .map(|history| history.last_review_id / 1000)
            .or(card.last_review_time.map(|time| time.0));
        let elapsed_seconds = last_review_secs.map(|secs| timing.now.0.saturating_sub(secs).max(0));
        let is_learning = card.ctype == CardType::Learn
            && matches!(card.queue, CardQueue::Learn | CardQueue::DayLearn);
        let elapsed_days = if is_learning {
            None
        } else {
            last_review_secs
                .map(|secs| timing.next_day_at.elapsed_days_since(TimestampSecs(secs)) as i64)
        };
        let card_type = match (card.ctype, card.queue) {
            (CardType::Learn, _) => 1,
            (CardType::Relearn, _) => 3,
            _ => 2,
        };
        ReviewInput {
            card_id: card.id.0,
            note_id: Some(card.note_id.0),
            deck_id: Some(card.original_or_current_deck_id().0),
            preset_id: query.preset_id,
            is_query: true,
            ease: None,
            duration_millis: None,
            card_type: Some(card_type),
            day_offset: Some(timing.days_elapsed as i64),
            current_elapsed_days: elapsed_days,
            current_elapsed_seconds: elapsed_seconds,
            target_retentions: [None; 4],
            enforce_grade_order: true,
        }
    }

    fn answer_input(
        &mut self,
        row: &HistoryRow,
        learning_start: bool,
        timing: &SchedTimingToday,
    ) -> ReviewInput {
        let day_offset = historical_day_offset(row.review_id, timing);
        let previous = self.cards.insert(
            row.card_id,
            CardHistory {
                last_review_id: row.review_id,
                last_kind: row.review_kind,
            },
        );
        let (elapsed_days, elapsed_seconds) = match previous {
            Some(previous) => (
                (day_offset - historical_day_offset(previous.last_review_id, timing)).max(0),
                ((row.review_id - previous.last_review_id) / 1000).max(0),
            ),
            None => (-1, -1),
        };
        ReviewInput {
            card_id: row.card_id,
            note_id: Some(row.note_id),
            deck_id: Some(row.deck_id),
            preset_id: row.preset_id,
            is_query: false,
            ease: Some(row.ease),
            duration_millis: Some(row.duration_millis),
            card_type: Some(if learning_start {
                LEARN_START_STATE
            } else {
                row.review_kind + 1
            }),
            day_offset: Some(day_offset),
            current_elapsed_days: Some(elapsed_days),
            current_elapsed_seconds: Some(elapsed_seconds),
            target_retentions: [None; 4],
            enforce_grade_order: true,
        }
    }

    fn replay(&mut self, inputs: Vec<ReviewInput>) -> Result<()> {
        self.model.replay(inputs).or_invalid("RWKV replay")
    }
}

impl Collection {
    /// The RWKV-Instant model, with every answer in the review log replayed.
    pub(crate) fn instant_history(&mut self) -> Result<&mut InstantHistory> {
        let timing = self.timing_today()?;
        let (last_revlog_id, revlog_count): (i64, i64) = self.storage.db.query_row(
            "select coalesce(max(id), 0), count() from revlog",
            [],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )?;
        let mut history = self.state.instant_history.take();
        let up_to_date = history.as_mut().is_some_and(|history| {
            history.day == timing.days_elapsed
                && history.next_day_at == timing.next_day_at.0
                && history.last_revlog_id == last_revlog_id
                && history.revlog_count == revlog_count
        });
        if !up_to_date {
            let appended = match history.as_mut() {
                Some(history)
                    if history.day == timing.days_elapsed
                        && history.next_day_at == timing.next_day_at.0 =>
                {
                    self.append_instant_history(history, &timing, last_revlog_id, revlog_count)?
                }
                _ => false,
            };
            if !appended {
                history = Some(self.build_instant_history(&timing)?);
            }
        }
        Ok(self.state.instant_history.insert(history.unwrap()))
    }

    /// Replay entries added since the last sync. Returns false if a full
    /// rebuild is needed instead.
    fn append_instant_history(
        &mut self,
        history: &mut InstantHistory,
        timing: &SchedTimingToday,
        last_revlog_id: i64,
        revlog_count: i64,
    ) -> Result<bool> {
        if last_revlog_id < history.last_revlog_id || revlog_count < history.revlog_count {
            // entries were removed (undo, or a check database)
            return Ok(false);
        }
        let new_entries: i64 = self.storage.db.query_row(
            "select count() from revlog where id > ?",
            [history.last_revlog_id],
            |row| row.get(0),
        )?;
        if new_entries != revlog_count - history.revlog_count {
            // something was removed as well as added
            return Ok(false);
        }
        let resets: i64 = self.storage.db.query_row(
            &format!("select count() from revlog where id > ? and {RESET_CONDITION}"),
            [history.last_revlog_id],
            |row| row.get(0),
        )?;
        if resets > 0 {
            return Ok(false);
        }
        let rows = self.instant_history_rows(Some(history.last_revlog_id))?;
        let mut inputs = Vec::with_capacity(rows.len());
        for row in &rows {
            let previous_kind = history.cards.get(&row.card_id).map(|card| card.last_kind);
            if row.review_kind == 0 && previous_kind.is_some_and(|kind| kind != 0) {
                // a new learning start discards the card's earlier history
                return Ok(false);
            }
            inputs.push(history.answer_input(
                row,
                previous_kind.is_none() && row.review_kind == 0,
                timing,
            ));
        }
        history.replay(inputs)?;
        history.last_revlog_id = last_revlog_id;
        history.revlog_count = revlog_count;
        Ok(true)
    }

    fn build_instant_history(&mut self, timing: &SchedTimingToday) -> Result<InstantHistory> {
        let started = std::time::Instant::now();
        let mut history = InstantHistory::new(timing)?;
        let (last_revlog_id, revlog_count): (i64, i64) = self.storage.db.query_row(
            "select coalesce(max(id), 0), count() from revlog",
            [],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )?;
        let rows = self.instant_history_rows(None)?;
        let starts = replay_starts(&rows);
        let mut inputs = Vec::with_capacity(REPLAY_CHUNK);
        let mut replayed = 0;
        for row in &rows {
            let (start_id, learning_start_id) = starts[&row.card_id];
            if row.review_id < start_id {
                continue;
            }
            let input = history.answer_input(row, Some(row.review_id) == learning_start_id, timing);
            inputs.push(input);
            replayed += 1;
            if inputs.len() == REPLAY_CHUNK {
                history.replay(std::mem::take(&mut inputs))?;
            }
        }
        history.replay(inputs)?;
        history.last_revlog_id = last_revlog_id;
        history.revlog_count = revlog_count;
        tracing::debug!(
            reviews = replayed,
            elapsed_ms = started.elapsed().as_millis() as u64,
            "built RWKV-Instant history"
        );
        Ok(history)
    }

    /// Answers in review order, after each card's latest Forget. With
    /// `after`, only entries newer than that revlog id.
    fn instant_history_rows(&mut self, after: Option<i64>) -> Result<Vec<HistoryRow>> {
        let presets: HashMap<i64, i64> = self
            .storage
            .get_all_decks()?
            .into_iter()
            .filter_map(|deck| deck.config_id().map(|config| (deck.id.0, config.0)))
            .collect();
        let sql = format!(
            "with latest_resets as (
  select cid, max(id) as reset_id from revlog where {RESET_CONDITION} group by cid
)
select r.id, r.cid, c.nid, case when c.odid != 0 then c.odid else c.did end,
  r.ease, r.time, r.type
from revlog r
join cards c on c.id = r.cid
left join latest_resets resets on resets.cid = r.cid
where {ANSWER_CONDITION}
  and c.type != 0
  and r.id > coalesce(resets.reset_id, 0)
  and r.id > ?
order by r.id"
        );
        let mut stmt = self.storage.db.prepare(&sql)?;
        let rows = stmt
            .query_map([after.unwrap_or(0)], |row| {
                let deck_id: i64 = row.get(3)?;
                Ok(HistoryRow {
                    review_id: row.get(0)?,
                    card_id: row.get(1)?,
                    note_id: row.get(2)?,
                    deck_id,
                    preset_id: presets.get(&deck_id).copied(),
                    ease: row.get(4)?,
                    duration_millis: row.get(5)?,
                    review_kind: row.get(6)?,
                })
            })?
            .collect::<std::result::Result<Vec<_>, _>>()?;
        Ok(rows)
    }
}

/// For each card: the first review id to replay, and the id of its learning
/// start if it has one. A card's replay starts at its latest learning start
/// (a learning answer not preceded by another learning answer), or at its
/// first answer if it never had one.
fn replay_starts(rows: &[HistoryRow]) -> HashMap<i64, (i64, Option<i64>)> {
    struct Start {
        previous_kind: i64,
        first_review_id: i64,
        learning_start: Option<i64>,
    }
    let mut starts: HashMap<i64, Start> = HashMap::new();
    for row in rows {
        let is_learning = row.review_kind == 0;
        starts
            .entry(row.card_id)
            .and_modify(|start| {
                if is_learning && start.previous_kind != 0 {
                    start.learning_start = Some(row.review_id);
                }
                start.previous_kind = row.review_kind;
            })
            .or_insert(Start {
                previous_kind: row.review_kind,
                first_review_id: row.review_id,
                learning_start: is_learning.then_some(row.review_id),
            });
    }
    starts
        .into_iter()
        .map(|(card_id, start)| {
            (
                card_id,
                (
                    start.learning_start.unwrap_or(start.first_review_id),
                    start.learning_start,
                ),
            )
        })
        .collect()
}

#[cfg(test)]
mod test {
    use super::*;

    fn row(review_id: i64, card_id: i64, review_kind: i64) -> HistoryRow {
        HistoryRow {
            review_id,
            card_id,
            note_id: 1,
            deck_id: 1,
            preset_id: Some(1),
            ease: 3,
            duration_millis: 1000,
            review_kind,
        }
    }

    #[test]
    fn replay_starts_at_latest_learning_start() {
        let rows = vec![
            row(1, 10, 0),
            row(2, 10, 0),
            row(3, 10, 1),
            row(4, 20, 1),
            row(5, 10, 0),
            row(6, 10, 1),
        ];
        let starts = replay_starts(&rows);
        assert_eq!(starts[&10], (5, Some(5)));
        // no learning start: replay everything
        assert_eq!(starts[&20], (4, None));
    }
}

#[cfg(test)]
mod bench {
    use crate::prelude::*;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogReviewKind;

    /// cargo test --release -p anki --lib instant_history_speed -- --ignored
    /// --nocapture
    #[test]
    #[ignore]
    fn instant_history_speed() -> Result<()> {
        let mut col = Collection::new();
        let cards = 5_000;
        let reviews_per_card = 40;
        let now = TimestampMillis::now().0;
        col.transact_no_undo(|col| {
            for index in 0..cards {
                let mut card = Card::new(NoteId(index + 1), 0, DeckId(1), 0);
                card.ctype = crate::card::CardType::Review;
                card.queue = crate::card::CardQueue::Review;
                col.storage.add_card(&mut card)?;
                for review in 0..reviews_per_card {
                    col.storage.add_revlog_entry(
                        &RevlogEntry {
                            id: RevlogId(
                                now - (reviews_per_card - review) * 86_400_000 + index * 10,
                            ),
                            cid: card.id,
                            button_chosen: if review % 7 == 0 { 1 } else { 3 },
                            ease_factor: 2500,
                            taken_millis: 4000,
                            review_kind: if review == 0 {
                                RevlogReviewKind::Learning
                            } else {
                                RevlogReviewKind::Review
                            },
                            ..Default::default()
                        },
                        true,
                    )?;
                }
            }
            Ok(())
        })?;
        let started = std::time::Instant::now();
        col.instant_history()?;
        println!(
            "replayed {} reviews in {:?}",
            cards * reviews_per_card,
            started.elapsed()
        );
        let all: Vec<Card> = col.storage.get_all_cards();
        let timing = col.timing_today()?;
        let queries: Vec<_> = all
            .iter()
            .map(|card| super::QueryCard {
                card,
                preset_id: Some(1),
            })
            .collect();
        let started = std::time::Instant::now();
        let predictions = col.instant_history()?.predict(&queries, &timing)?;
        println!(
            "predicted {} cards in {:?}",
            predictions.len(),
            started.elapsed()
        );
        Ok(())
    }
}
