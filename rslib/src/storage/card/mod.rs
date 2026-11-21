// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

pub(crate) mod data;
pub(crate) mod filtered;

use std::collections::HashSet;
use std::convert::TryFrom;
use std::fmt;
use std::result;

use anki_proto::stats::CardEntry;
use rusqlite::named_params;
use rusqlite::params;
use rusqlite::types::FromSql;
use rusqlite::types::FromSqlError;
use rusqlite::types::ValueRef;
use rusqlite::OptionalExtension;
use rusqlite::Row;

use self::data::CardData;
use super::ids_to_string;
use super::sqlite::SqlSortOrder;
use super::sqlite::RETRIEVABILITY_CACHE_DB_SCHEMA;
use crate::card::Card;
use crate::card::CardId;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::config::rwkv_history_reset_config_key;
use crate::config::RwkvHistoryResetMirror;
use crate::deckconfig::DeckConfigId;
use crate::deckconfig::ReviewCardOrder;
use crate::decks::Deck;
use crate::decks::DeckId;
use crate::decks::DeckKind;
use crate::error::Result;
use crate::notes::NoteId;
use crate::scheduler::congrats::CongratsInfo;
use crate::scheduler::fsrs::memory_state::get_last_revlog_info;
use crate::scheduler::queue::BuryMode;
use crate::scheduler::queue::DueCard;
use crate::scheduler::queue::DueCardKind;
use crate::scheduler::queue::DueCardWithState;
use crate::scheduler::queue::NewCard;
use crate::scheduler::timing::SchedTimingToday;
use crate::timestamp::TimestampMillis;
use crate::timestamp::TimestampSecs;
use crate::types::Usn;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct CardFixStats {
    pub new_cards_fixed: usize,
    pub other_cards_fixed: usize,
    pub fsrs_stability_fixed: usize,
    pub last_review_time_fixed: usize,
}

impl FromSql for CardType {
    fn column_result(value: ValueRef<'_>) -> result::Result<Self, FromSqlError> {
        if let ValueRef::Integer(i) = value {
            Ok(Self::try_from(i as u8).map_err(|_| FromSqlError::InvalidType)?)
        } else {
            Err(FromSqlError::InvalidType)
        }
    }
}

impl FromSql for CardQueue {
    fn column_result(value: ValueRef<'_>) -> result::Result<Self, FromSqlError> {
        if let ValueRef::Integer(i) = value {
            Ok(Self::try_from(i as i8).map_err(|_| FromSqlError::InvalidType)?)
        } else {
            Err(FromSqlError::InvalidType)
        }
    }
}

fn row_to_card(row: &Row) -> result::Result<Card, rusqlite::Error> {
    let data: CardData = row.get(17)?;
    Ok(Card {
        id: row.get(0)?,
        note_id: row.get(1)?,
        deck_id: row.get(2)?,
        template_idx: row.get(3)?,
        mtime: row.get(4)?,
        usn: row.get(5)?,
        ctype: row.get(6)?,
        queue: row.get(7)?,
        due: row.get(8).ok().unwrap_or_default(),
        interval: row.get(9)?,
        ease_factor: row.get(10)?,
        reps: row.get(11)?,
        lapses: row.get(12)?,
        remaining_steps: row.get(13)?,
        original_due: row.get(14).ok().unwrap_or_default(),
        original_deck_id: row.get(15)?,
        flags: row.get(16)?,
        original_position: data.original_position,
        memory_state: data.memory_state(),
        desired_retention: data.fsrs_desired_retention,
        decay: data.decay,
        last_review_time: data.last_review_time,
        rwkv_history_reset_after: data.rwkv_history_reset_after,
        custom_data: data.custom_data,
    })
}

fn row_to_due_card_with_state(row: &Row) -> result::Result<DueCardWithState, rusqlite::Error> {
    let queue = row.get(4)?;
    // Keep the shared decoder, including its malformed-data defaults.
    let data: CardData = row.get(10)?;
    Ok(DueCardWithState {
        card: DueCard {
            id: row.get(0)?,
            note_id: row.get(1)?,
            current_deck_id: row.get(2)?,
            mtime: row.get(3)?,
            due: row.get(5).ok().unwrap_or_default(),
            reps: row.get(7)?,
            original_deck_id: row.get(9)?,
            kind: if queue == CardQueue::Review {
                DueCardKind::Review
            } else {
                DueCardKind::Learning
            },
        },
        queue,
        interval: row.get(6)?,
        original_due: row.get(8).ok().unwrap_or_default(),
        memory_state: data.memory_state(),
        desired_retention: data.fsrs_desired_retention,
        last_review_time: data.last_review_time,
    })
}

fn row_to_card_entry(row: &Row) -> Result<CardEntry> {
    Ok(CardEntry {
        id: row.get(0)?,
        note_id: row.get(1)?,
        deck_id: row.get(2)?,
        template_idx: row.get(3)?,
        notetype_id: row.get(4)?,
    })
}

fn row_to_new_card(row: &Row) -> result::Result<NewCard, rusqlite::Error> {
    Ok(NewCard {
        id: row.get(0)?,
        note_id: row.get(1)?,
        template_index: row.get(2)?,
        mtime: row.get(3)?,
        current_deck_id: row.get(4)?,
        original_deck_id: row.get(5)?,
        hash: 0,
    })
}

impl super::SqliteStorage {
    /// Return cards with an FSRS state written by a client that did not
    /// preserve the model's internal stability fields.
    pub(crate) fn card_ids_with_foreign_fsrs_state(&self) -> Result<Vec<CardId>> {
        self.db
            .prepare_cached(
                r#"select id, data from cards
where data like '%"s":%'
and (data not like '%"s_int":%' or data not like '%"s_fast":%')"#,
            )?
            .query_and_then([], |row| -> Result<Option<CardId>> {
                let data: CardData = row.get(1)?;
                let is_foreign = data.fsrs_stability.is_some()
                    && data.fsrs_difficulty.is_some()
                    && (data.fsrs_stability_internal.is_none()
                        || data.fsrs_stability_fast.is_none());
                Ok(is_foreign.then(|| row.get(0)).transpose()?)
            })?
            .filter_map(Result::transpose)
            .collect()
    }

    /// The collection's change time when the open-time scan for foreign FSRS
    /// states last ran. Kept in the local retrievability-cache sidecar, which
    /// does not sync. None before the first scan or without the sidecar table.
    pub(crate) fn foreign_card_scan_stamp(&self) -> Result<Option<TimestampMillis>> {
        let exists: bool = self.db.query_row(
            &format!(
                "select exists(select 1 from {RETRIEVABILITY_CACHE_DB_SCHEMA}.sqlite_master \
                 where type = 'table' and name = 'foreign_card_scan')"
            ),
            [],
            |row| row.get(0),
        )?;
        if !exists {
            return Ok(None);
        }
        self.db
            .query_row(
                &format!(
                    "select collection_change from \
                     {RETRIEVABILITY_CACHE_DB_SCHEMA}.foreign_card_scan where id = 0"
                ),
                [],
                |row| row.get(0),
            )
            .optional()
            .map_err(Into::into)
    }

    pub(crate) fn set_foreign_card_scan_stamp(
        &self,
        collection_change: TimestampMillis,
    ) -> Result<()> {
        self.db.execute_batch(&format!(
            "create table if not exists {RETRIEVABILITY_CACHE_DB_SCHEMA}.foreign_card_scan (
                id integer primary key check (id = 0),
                collection_change integer not null
            )"
        ))?;
        self.db.execute(
            &format!(
                "insert or replace into {RETRIEVABILITY_CACHE_DB_SCHEMA}.foreign_card_scan \
                 (id, collection_change) values (0, ?)"
            ),
            [collection_change],
        )?;
        Ok(())
    }

    pub fn get_card(&self, cid: CardId) -> Result<Option<Card>> {
        let mut card = self
            .db
            .prepare_cached(concat!(include_str!("get_card.sql"), " where id = ?"))?
            .query_row(params![cid], row_to_card)
            .optional()?;
        if let Some(card) = &mut card {
            self.populate_rwkv_history_reset(card)?;
        }
        Ok(card)
    }

    pub(crate) fn populate_rwkv_history_reset(&self, card: &mut Card) -> Result<()> {
        if let Some(boundary) = self
            .get_config_value::<RwkvHistoryResetMirror>(&rwkv_history_reset_config_key(card.id))?
        {
            if let Some(after) = boundary.after {
                card.rwkv_history_reset_after = (after > 0).then_some(after);
            }
        }
        Ok(())
    }

    /// Fields written by an earlier build need a synchronized mirror before an
    /// older client has a chance to rewrite their cards.
    pub(crate) fn unmirrored_rwkv_history_resets(
        &self,
    ) -> Result<Vec<(CardId, i64, TimestampSecs)>> {
        self.db
            .prepare(
                r#"select c.id, json_extract(c.data, '$.rwkv_reset_after'), c.mod from cards c
                 where c.data like '%"rwkv_reset_after"%'
                 and case when json_valid(c.data) then
                   json_type(c.data, '$.rwkv_reset_after') = 'integer'
                   and json_extract(c.data, '$.rwkv_reset_after') > 0
                 else false end
                 and not exists (select 1 from config
                   where key = 'rwkvHistoryReset_' || c.id)"#,
            )?
            .query_and_then([], |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)))?
            .collect()
    }

    pub(crate) fn get_all_card_entries(&self) -> Result<Vec<CardEntry>> {
        self.db
            .prepare_cached(include_str!("get_card_entry.sql"))?
            .query_and_then([], row_to_card_entry)?
            .collect()
    }

    pub(crate) fn update_card(&self, card: &Card) -> Result<()> {
        let mut stmt = self.db.prepare_cached(include_str!("update_card.sql"))?;
        stmt.execute(params![
            card.note_id,
            card.deck_id,
            card.template_idx,
            card.mtime,
            card.usn,
            card.ctype as u8,
            card.queue as i8,
            card.due,
            card.interval,
            card.ease_factor,
            card.reps,
            card.lapses,
            card.remaining_steps,
            card.original_due,
            card.original_deck_id,
            card.flags,
            CardData::from_card(card).convert_to_json()?,
            card.id,
        ])?;
        Ok(())
    }

    /// Persist derived memory metadata without changing scheduling or sync
    /// fields.
    pub(crate) fn update_card_data(&self, card: &Card) -> Result<()> {
        self.db
            .prepare_cached("update cards set data = ? where id = ?")?
            .execute(params![
                CardData::from_card(card).convert_to_json()?,
                card.id
            ])?;
        Ok(())
    }

    pub(crate) fn add_card(&self, card: &mut Card) -> Result<()> {
        let now = TimestampMillis::now().0;
        let mut stmt = self.db.prepare_cached(include_str!("add_card.sql"))?;
        stmt.execute(params![
            now,
            card.note_id,
            card.deck_id,
            card.template_idx,
            card.mtime,
            card.usn,
            card.ctype as u8,
            card.queue as i8,
            card.due,
            card.interval,
            card.ease_factor,
            card.reps,
            card.lapses,
            card.remaining_steps,
            card.original_due,
            card.original_deck_id,
            card.flags,
            CardData::from_card(card).convert_to_json()?,
        ])?;
        card.id = CardId(self.db.last_insert_rowid());
        Ok(())
    }

    /// Add card if id is unique. True if card was added.
    pub(crate) fn add_card_if_unique(&self, card: &Card) -> Result<bool> {
        self.db
            .prepare_cached(include_str!("add_card_if_unique.sql"))?
            .execute(params![
                card.id,
                card.note_id,
                card.deck_id,
                card.template_idx,
                card.mtime,
                card.usn,
                card.ctype as u8,
                card.queue as i8,
                card.due,
                card.interval,
                card.ease_factor,
                card.reps,
                card.lapses,
                card.remaining_steps,
                card.original_due,
                card.original_deck_id,
                card.flags,
                CardData::from_card(card).convert_to_json()?,
            ])
            .map(|n_rows| n_rows == 1)
            .map_err(Into::into)
    }

    /// Add or update card, using the provided ID. Used for syncing & undoing.
    pub(crate) fn add_or_update_card(&self, card: &Card) -> Result<()> {
        let mut stmt = self.db.prepare_cached(include_str!("add_or_update.sql"))?;
        stmt.execute(params![
            card.id,
            card.note_id,
            card.deck_id,
            card.template_idx,
            card.mtime,
            card.usn,
            card.ctype as u8,
            card.queue as i8,
            card.due,
            card.interval,
            card.ease_factor,
            card.reps,
            card.lapses,
            card.remaining_steps,
            card.original_due,
            card.original_deck_id,
            card.flags,
            CardData::from_card(card).convert_to_json()?,
        ])?;

        Ok(())
    }

    pub(crate) fn remove_card(&self, cid: CardId) -> Result<()> {
        self.db
            .prepare_cached("delete from cards where id = ?")?
            .execute([cid])?;
        Ok(())
    }

    pub(crate) fn for_each_intraday_card_in_active_decks<F>(
        &self,
        learn_cutoff: TimestampSecs,
        mut func: F,
    ) -> Result<()>
    where
        F: FnMut(DueCard),
    {
        let mut stmt = self.db.prepare_cached(include_str!("intraday_due.sql"))?;
        let mut rows = stmt.query(params![learn_cutoff])?;
        while let Some(row) = rows.next()? {
            func(DueCard {
                id: row.get(0)?,
                note_id: row.get(1)?,
                due: row.get(2).ok().unwrap_or_default(),
                mtime: row.get(3)?,
                current_deck_id: row.get(4)?,
                original_deck_id: row.get(5)?,
                reps: row.get(6)?,
                kind: DueCardKind::Learning,
            })
        }

        Ok(())
    }

    /// Call func() for each review card or interday learning card, stopping
    /// when it returns false or no more cards found.
    pub(crate) fn for_each_due_card_in_active_decks<F>(
        &self,
        timing: SchedTimingToday,
        order: ReviewCardOrder,
        kind: DueCardKind,
        fsrs: bool,
        mut func: F,
    ) -> Result<()>
    where
        F: FnMut(DueCard) -> Result<bool>,
    {
        let order_clause = review_order_sql(order, timing, fsrs);
        let mut stmt = self.db.prepare_cached(&format!(
            "{} order by {}",
            include_str!("due_cards.sql"),
            order_clause
        ))?;
        let queue = match kind {
            DueCardKind::Review => CardQueue::Review,
            DueCardKind::Learning => CardQueue::DayLearn,
        };
        let mut rows = stmt.query(params![queue as i8, timing.days_elapsed])?;
        while let Some(row) = rows.next()? {
            if !func(due_card_from_review_row(row, kind)?)? {
                break;
            }
        }

        Ok(())
    }

    /// Scheduling state for exact queue scoring, without an intermediate SQL
    /// sort or one additional card lookup per candidate. The two ranges let
    /// SQLite use the scheduling index with the appropriate day/second
    /// cutoff.
    pub(crate) fn due_cards_with_state_in_active_decks(
        &self,
        timing: SchedTimingToday,
    ) -> Result<Vec<DueCardWithState>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get_due_card_state.sql"),
                " where did in (select id from active_decks) and queue in (2, 3) and due <= ?1 union all ",
                include_str!("get_due_card_state.sql"),
                " where did in (select id from active_decks) and queue in (1, 4) and due <= ?2"
            ))?
            .query_and_then(params![timing.days_elapsed, timing.now.min(timing.next_day_at)], |row| {
                row_to_due_card_with_state(row).map_err(Into::into)
            })?
            .collect()
    }

    #[cfg(test)]
    pub(crate) fn due_cards_full_state_for_benchmark(
        &self,
        timing: SchedTimingToday,
    ) -> Result<Vec<Card>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get_card.sql"),
                " where did in (select id from active_decks) and queue in (2, 3) and due <= ?1 union all ",
                include_str!("get_card.sql"),
                " where did in (select id from active_decks) and queue in (1, 4) and due <= ?2"
            ))?
            .query_and_then(params![timing.days_elapsed, timing.now.min(timing.next_day_at)], |row| {
                row_to_card(row).map_err(Into::into)
            })?
            .collect()
    }

    /// Call func() for each requested review card in the active decks,
    /// including cards whose due day is in the future.
    pub(crate) fn for_each_review_card_in_active_decks_with_ids<F>(
        &self,
        card_ids: &[CardId],
        mut func: F,
    ) -> Result<()>
    where
        F: FnMut(DueCard) -> Result<bool>,
    {
        if card_ids.is_empty() {
            return Ok(());
        }

        let mut ids = String::new();
        ids_to_string(&mut ids, card_ids);
        let sql =
            include_str!("review_cards_in_active_decks_with_ids.sql").replace("CARD_IDS", &ids);
        let mut stmt = self.db.prepare(&sql)?;
        let mut rows = stmt.query(params![CardQueue::Review as i8])?;
        while let Some(row) = rows.next()? {
            if !func(due_card_from_review_row(row, DueCardKind::Review)?)? {
                break;
            }
        }

        Ok(())
    }

    /// Call func() for each review card in the active decks, including cards
    /// whose due day is in the future, in the configured review order.
    pub(crate) fn for_each_review_card_in_active_decks<F>(
        &self,
        timing: SchedTimingToday,
        order: ReviewCardOrder,
        fsrs: bool,
        mut func: F,
    ) -> Result<()>
    where
        F: FnMut(DueCard) -> Result<bool>,
    {
        let order_clause = review_order_sql(order, timing, fsrs);
        let mut stmt = self.db.prepare_cached(&format!(
            "{} order by {}",
            include_str!("review_cards_in_active_decks.sql"),
            order_clause
        ))?;
        let mut rows = stmt.query(params![CardQueue::Review as i8])?;
        while let Some(row) = rows.next()? {
            if !func(due_card_from_review_row(row, DueCardKind::Review)?)? {
                break;
            }
        }

        Ok(())
    }

    /// Call func() for each new card in the provided deck, stopping when it
    /// returns or no more cards found.
    pub(crate) fn for_each_new_card_in_deck<F>(
        &self,
        deck: DeckId,
        sort: NewCardSorting,
        mut func: F,
    ) -> Result<()>
    where
        F: FnMut(NewCard) -> Result<bool>,
    {
        let mut stmt = self.db.prepare_cached(&format!(
            "{} ORDER BY {}",
            include_str!("new_cards.sql"),
            sort.write()
        ))?;
        let mut rows = stmt.query(params![deck])?;
        while let Some(row) = rows.next()? {
            if !func(row_to_new_card(row)?)? {
                break;
            }
        }

        Ok(())
    }

    /// Call func() for each new card in the active decks, stopping when it
    /// returns false or no more cards found.
    pub(crate) fn for_each_new_card_in_active_decks<F>(
        &self,
        order: NewCardSorting,
        mut func: F,
    ) -> Result<()>
    where
        F: FnMut(NewCard) -> Result<bool>,
    {
        let mut stmt = self.db.prepare_cached(&format!(
            "{} ORDER BY {}",
            include_str!("active_new_cards.sql"),
            order.write(),
        ))?;
        let mut rows = stmt.query(params![])?;
        while let Some(row) = rows.next()? {
            if !func(row_to_new_card(row)?)? {
                break;
            }
        }

        Ok(())
    }

    /// Fix some invalid card properties, and return number of changed cards.
    pub(crate) fn fix_card_properties(
        &self,
        today: u32,
        mtime: TimestampSecs,
        usn: Usn,
        v1_sched: bool,
    ) -> Result<CardFixStats> {
        let new_cnt = self
            .db
            .prepare(include_str!("fix_due_new.sql"))?
            .execute(params![mtime, usn])?;
        let mut other_cnt = self
            .db
            .prepare(include_str!("fix_due_other.sql"))?
            .execute(params![today, mtime, usn])?;
        other_cnt += self
            .db
            .prepare(include_str!("fix_odue.sql"))?
            .execute(params![mtime, usn, v1_sched])?;
        other_cnt += self
            .db
            .prepare(include_str!("fix_ivl.sql"))?
            .execute(params![mtime, usn])?;
        other_cnt += self
            .db
            .prepare(include_str!("fix_mod.sql"))?
            .execute(params![])?;
        other_cnt += self
            .db
            .prepare(include_str!("fix_ordinal.sql"))?
            .execute(params![mtime, usn])?;
        let fsrs_stability_cnt = self.fix_zero_fsrs_stability(mtime, usn)?;
        let mut last_review_time_cnt = 0;
        let revlog = self.get_all_revlog_entries_in_card_order()?;
        let last_revlog_info = get_last_revlog_info(&revlog);
        for (card_id, last_revlog_info) in last_revlog_info {
            let card = self.get_card(card_id)?;
            let lrt = last_revlog_info.last_reviewed_at;
            if let Some(mut card) = card {
                if card.ctype != CardType::New && card.last_review_time != lrt {
                    card.last_review_time = lrt;
                    self.update_card(&card)?;
                    last_review_time_cnt += 1;
                }
            }
        }
        Ok(CardFixStats {
            new_cards_fixed: new_cnt,
            other_cards_fixed: other_cnt,
            fsrs_stability_fixed: fsrs_stability_cnt,
            last_review_time_fixed: last_review_time_cnt,
        })
    }

    fn fix_zero_fsrs_stability(&self, mtime: TimestampSecs, usn: Usn) -> Result<usize> {
        let card_ids: Vec<CardId> = self
            .db
            .prepare(
                "select id from cards where
                    data like '%\"s\":0,%' or data like '%\"s\":0.0,%' or
                    data like '%\"s\":0}' or data like '%\"s\":0.0}'",
            )?
            .query_and_then([], |row| Ok(CardId(row.get(0)?)))?
            .collect::<Result<_>>()?;
        let mut fixed = 0;
        for card_id in card_ids {
            let Some(mut card) = self.get_card(card_id)? else {
                continue;
            };
            let Some(memory_state) = card.memory_state else {
                continue;
            };
            if memory_state.stability == 0.0 {
                card.mtime = mtime;
                card.usn = usn;
                self.update_card(&card)?;
                fixed += 1;
            }
        }
        Ok(fixed)
    }

    pub(crate) fn delete_orphaned_cards(&self) -> Result<usize> {
        self.db
            .prepare("delete from cards where nid not in (select id from notes)")?
            .execute([])
            .map_err(Into::into)
    }

    pub(crate) fn all_filtered_cards_by_deck(&self) -> Result<Vec<(CardId, DeckId)>> {
        self.db
            .prepare("select id, did from cards where odid > 0")?
            .query_and_then([], |r| -> Result<_> { Ok((r.get(0)?, r.get(1)?)) })?
            .collect()
    }

    pub(crate) fn max_new_card_position(&self) -> Result<u32> {
        self.db
            .prepare("select max(due)+1 from cards where type=0")?
            .query_row([], |r| r.get(0))
            .map_err(Into::into)
    }

    pub(crate) fn get_card_by_ordinal(&self, nid: NoteId, ord: u16) -> Result<Option<Card>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get_card.sql"),
                " where nid = ? and ord = ?"
            ))?
            .query_row(params![nid, ord], row_to_card)
            .optional()
            .map_err(Into::into)
    }

    pub(crate) fn clear_pending_card_usns(&self) -> Result<()> {
        self.db
            .prepare("update cards set usn = 0 where usn = -1")?
            .execute([])?;
        Ok(())
    }

    pub(crate) fn have_at_least_one_card(&self) -> Result<bool> {
        self.db
            .prepare_cached("select null from cards")?
            .query([])?
            .next()
            .map(|o| o.is_some())
            .map_err(Into::into)
    }

    pub fn all_cards_of_note(&self, nid: NoteId) -> Result<Vec<Card>> {
        self.db
            .prepare_cached(concat!(include_str!("get_card.sql"), " where nid = ?"))?
            .query_and_then([nid], |r| row_to_card(r).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn all_cards_of_notes_above_ordinal(
        &mut self,
        note_ids: &[NoteId],
        ordinal: usize,
    ) -> Result<Vec<Card>> {
        self.with_ids_in_searched_notes_table(note_ids, || {
            self.db
                .prepare_cached(concat!(
                    include_str!("get_card.sql"),
                    " where nid in (select nid from search_nids) and ord > ?"
                ))?
                .query_and_then([ordinal as i64], |r| row_to_card(r).map_err(Into::into))?
                .collect()
        })
    }

    pub fn all_card_ids_of_note_in_template_order(&self, nid: NoteId) -> Result<Vec<CardId>> {
        self.db
            .prepare_cached("select id from cards where nid = ? order by ord")?
            .query_and_then([nid], |r| Ok(CardId(r.get(0)?)))?
            .collect()
    }

    pub(crate) fn get_all_card_ids(&self) -> Result<HashSet<CardId>> {
        self.db
            .prepare("SELECT id FROM cards")?
            .query_and_then([], |row| Ok(row.get(0)?))?
            .collect()
    }

    pub(crate) fn all_cards_as_nid_and_ord(&self) -> Result<HashSet<(NoteId, u16)>> {
        self.db
            .prepare("SELECT nid, ord FROM cards")?
            .query_and_then([], |r| Ok((NoteId(r.get(0)?), r.get(1)?)))?
            .collect()
    }

    pub(crate) fn card_ids_of_notes(&self, nids: &[NoteId]) -> Result<Vec<CardId>> {
        let mut stmt = self
            .db
            .prepare_cached("select id from cards where nid = ?")?;
        let mut cids = vec![];
        for nid in nids {
            for cid in stmt.query_map([nid], |row| row.get(0))? {
                cids.push(cid?);
            }
        }
        Ok(cids)
    }

    pub(crate) fn all_siblings_for_bury(
        &self,
        cid: CardId,
        nid: NoteId,
        bury_mode: BuryMode,
    ) -> Result<Vec<Card>> {
        let params = named_params! {
            ":card_id": cid,
            ":note_id": nid,
            ":include_new": bury_mode.bury_new,
            ":include_reviews": bury_mode.bury_reviews,
            ":include_day_learn": bury_mode.bury_interday_learning      ,
            ":new_queue": CardQueue::New as i8,
            ":review_queue": CardQueue::Review as i8,
            ":daylearn_queue": CardQueue::DayLearn as i8,
        };
        self.with_searched_cards_table(false, || {
            self.db
                .prepare_cached(include_str!("siblings_for_bury.sql"))?
                .execute(params)?;
            self.all_searched_cards()
        })
    }

    pub(crate) fn with_searched_cards_table<T>(
        &self,
        preserve_order: bool,
        func: impl FnOnce() -> Result<T>,
    ) -> Result<T> {
        if preserve_order {
            self.setup_searched_cards_table_to_preserve_order()?;
        } else {
            self.setup_searched_cards_table()?;
        }
        let result = func();
        self.clear_searched_cards_table()?;
        result
    }

    pub(crate) fn note_ids_of_cards(&self, cids: &[CardId]) -> Result<HashSet<NoteId>> {
        let mut stmt = self
            .db
            .prepare_cached("select nid from cards where id = ?")?;
        let mut nids = HashSet::new();
        for cid in cids {
            if let Some(nid) = stmt
                .query_row([cid], |r| r.get::<_, NoteId>(0))
                .optional()?
            {
                nids.insert(nid);
            }
        }
        Ok(nids)
    }

    /// Place the ids of cards with notes in 'search_nids' into 'search_cids'.
    /// Returns number of added cards.
    pub(crate) fn search_cards_of_notes_into_table(&self) -> Result<usize> {
        self.db
            .prepare(include_str!("search_cards_of_notes_into_table.sql"))?
            .execute([])
            .map_err(Into::into)
    }

    pub(crate) fn all_searched_cards(&self) -> Result<Vec<Card>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get_card.sql"),
                " where id in (select cid from search_cids)"
            ))?
            .query_and_then([], |r| row_to_card(r).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn rwkv_review_input_candidate_cards_for_ids(
        &self,
        card_ids: &[CardId],
        include_suspended_review: bool,
        include_new_cards: bool,
        enabled_deck_ids: Option<&HashSet<DeckId>>,
    ) -> Result<Vec<Card>> {
        if card_ids.is_empty() || enabled_deck_ids.is_some_and(HashSet::is_empty) {
            return Ok(Vec::new());
        }

        self.with_searched_cards_table(false, || {
            self.set_search_table_to_card_ids(card_ids)?;
            self.rwkv_review_input_candidate_cards_in_search(
                include_suspended_review,
                include_new_cards,
                enabled_deck_ids,
            )
        })
    }

    pub(crate) fn rwkv_review_input_candidate_cards_for_deck_review_queue(
        &self,
        deck_ids: &[DeckId],
        enabled_deck_ids: Option<&HashSet<DeckId>>,
        include_new_cards: bool,
    ) -> Result<(u32, Vec<Card>)> {
        if deck_ids.is_empty() {
            return Ok((0, Vec::new()));
        }

        let mut deck_ids_sql = String::new();
        let mut sorted_deck_ids: Vec<_> = deck_ids.iter().map(|deck_id| deck_id.0).collect();
        sorted_deck_ids.sort_unstable();
        ids_to_string(&mut deck_ids_sql, sorted_deck_ids);

        let searched_cards = self
            .db
            .prepare(&format!(
                "select count() from cards where did in {deck_ids_sql} and queue in ({})",
                if include_new_cards { "0, 2" } else { "2" }
            ))?
            .query_row([], |row| row.get(0))?;

        if enabled_deck_ids.is_some_and(HashSet::is_empty) {
            return Ok((searched_cards, Vec::new()));
        }

        let queue_filter = if include_new_cards {
            format!(
                "((type = {} and queue = {}) or (type = {} and queue = {}))",
                CardType::Review as i8,
                CardQueue::Review as i8,
                CardType::New as i8,
                CardQueue::New as i8,
            )
        } else {
            format!(
                "type = {} and queue = {}",
                CardType::Review as i8,
                CardQueue::Review as i8,
            )
        };

        let mut sql = format!(
            "{} where did in {deck_ids_sql} and {queue_filter}",
            include_str!("get_card.sql"),
        );

        if let Some(enabled_deck_ids) = enabled_deck_ids {
            let mut enabled_ids: Vec<_> =
                enabled_deck_ids.iter().map(|deck_id| deck_id.0).collect();
            enabled_ids.sort_unstable();
            let mut enabled_ids_sql = String::new();
            ids_to_string(&mut enabled_ids_sql, enabled_ids);
            sql.push_str(" and (case when odid != 0 then odid else did end) in ");
            sql.push_str(&enabled_ids_sql);
        }

        let cards = self
            .db
            .prepare(&sql)?
            .query_and_then([], |r| row_to_card(r).map_err(Into::into))?
            .collect::<Result<Vec<_>>>()?;

        Ok((searched_cards, cards))
    }

    pub(crate) fn rwkv_review_input_candidate_cards_in_search(
        &self,
        include_suspended_review: bool,
        include_new_cards: bool,
        enabled_deck_ids: Option<&HashSet<DeckId>>,
    ) -> Result<Vec<Card>> {
        if enabled_deck_ids.is_some_and(HashSet::is_empty) {
            return Ok(Vec::new());
        }

        let new_card_filter = if include_new_cards {
            format!(
                "   or (type = {} and queue = {})\n",
                CardType::New as i8,
                CardQueue::New as i8,
            )
        } else {
            String::new()
        };
        let mut sql = concat!(
            include_str!("get_card.sql"),
            " where id in (select cid from search_cids)\n",
            " and (\n",
            "   (type = 2 and queue in REVIEW_QUEUES)\n",
            "   or (type = 1 and queue in (1, 3))\n",
            "   or (type = 3 and queue in (1, 3))\n",
            "NEW_CARD_FILTER",
            " )\n",
        )
        .replace(
            "REVIEW_QUEUES",
            if include_suspended_review {
                "(2, -1)"
            } else {
                "(2)"
            },
        )
        .replace("NEW_CARD_FILTER", &new_card_filter);

        if let Some(enabled_deck_ids) = enabled_deck_ids {
            let mut deck_ids: Vec<_> = enabled_deck_ids.iter().map(|deck_id| deck_id.0).collect();
            deck_ids.sort_unstable();
            let mut deck_ids_sql = String::new();
            ids_to_string(&mut deck_ids_sql, deck_ids);
            sql.push_str(" and (case when odid != 0 then odid else did end) in ");
            sql.push_str(&deck_ids_sql);
        }

        self.db
            .prepare(&sql)?
            .query_and_then([], |r| row_to_card(r).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn all_searched_cards_in_search_order(&self) -> Result<Vec<Card>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get_card.sql"),
                ", search_cids where cards.id = search_cids.cid order by search_cids.rowid"
            ))?
            .query_and_then([], |r| row_to_card(r).map_err(Into::into))?
            .collect()
    }

    /// Cards will arrive in card id order, not search order.
    pub(crate) fn for_each_card_in_search<F>(&self, mut func: F) -> Result<()>
    where
        F: FnMut(Card) -> Result<()>,
    {
        let mut stmt = self.db.prepare_cached(concat!(
            include_str!("get_card.sql"),
            " where id in (select cid from search_cids)"
        ))?;
        let mut rows = stmt.query([])?;
        while let Some(row) = rows.next()? {
            let card = row_to_card(row)?;
            func(card)?
        }

        Ok(())
    }

    pub(crate) fn get_all_cards_due_in_range(
        &self,
        min_day: u32,
        max_day: u32,
    ) -> Result<Vec<Vec<(CardId, NoteId, DeckId)>>> {
        Ok(self
            .db
            .prepare_cached("select id, nid, did, due from cards where due >= ?1 and due < ?2 ")?
            .query_and_then([min_day, max_day], |row: &Row| {
                Ok::<_, rusqlite::Error>((
                    row.get::<_, CardId>(0)?,
                    row.get::<_, NoteId>(1)?,
                    row.get::<_, DeckId>(2)?,
                    row.get::<_, i32>(3)?,
                ))
            })?
            .flatten()
            .fold(
                vec![Vec::new(); (max_day - min_day) as usize],
                |mut acc, (card_id, note_id, deck_id, due)| {
                    acc[due as usize - min_day as usize].push((card_id, note_id, deck_id));
                    acc
                },
            ))
    }

    pub(crate) fn get_deck_due_counts(&self) -> Result<Vec<(DeckId, i32, usize)>> {
        self.db
            .prepare(include_str!("deck_due_counts.sql"))?
            .query_and_then([], |row| -> Result<_> {
                Ok((DeckId(row.get(0)?), row.get(1)?, row.get(2)?))
            })?
            .collect()
    }

    pub(crate) fn filtered_review_counts_by_original_deck(&self) -> Result<Vec<(DeckId, u32)>> {
        self.db
            .prepare_cached(
                "select odid, count()
                 from cards
                 where odid > 0
                   and queue in (?1, ?2)
                 group by odid",
            )?
            .query_and_then(
                params![CardQueue::Review as i8, CardQueue::DayLearn as i8],
                |row| -> Result<_> { Ok((DeckId(row.get(0)?), row.get(1)?)) },
            )?
            .collect()
    }

    pub(crate) fn congrats_info(&self, current: &Deck, today: u32) -> Result<CongratsInfo> {
        // NOTE: this line is obsolete in v3 as it's run on queue build, but kept to
        // prevent errors for v1/v2 users before they upgrade
        self.update_active_decks(current)?;
        self.db
            .prepare(include_str!("congrats.sql"))?
            .query_and_then(
                named_params! {
                    ":review_queue": CardQueue::Review as i8,
                    ":day_learn_queue": CardQueue::DayLearn as i8,
                    ":new_queue": CardQueue::New as i8,
                    ":user_buried_queue": CardQueue::UserBuried as i8,
                    ":sched_buried_queue": CardQueue::SchedBuried as i8,
                    ":learn_queue": CardQueue::Learn as i8,
                    ":today": today,
                },
                |row| {
                    Ok(CongratsInfo {
                        review_remaining: row.get::<_, u32>(0)? > 0,
                        new_remaining: row.get::<_, u32>(1)? > 0,
                        have_sched_buried: row.get::<_, u32>(2)? > 0,
                        have_user_buried: row.get::<_, u32>(3)? > 0,
                        learn_count: row.get(4)?,
                        next_learn_due: row.get(5)?,
                    })
                },
            )?
            .next()
            .unwrap()
    }

    pub(crate) fn all_cards_at_or_above_position(&self, start: u32) -> Result<Vec<Card>> {
        self.with_searched_cards_table(false, || {
            self.db
                .prepare(include_str!("at_or_above_position.sql"))?
                .execute([start, CardType::New as u32])?;
            self.all_searched_cards()
        })
    }

    pub(crate) fn setup_searched_cards_table(&self) -> Result<()> {
        self.db
            .execute_batch(include_str!("search_cids_setup.sql"))?;
        Ok(())
    }

    pub(crate) fn setup_searched_cards_table_to_preserve_order(&self) -> Result<()> {
        self.db
            .execute_batch(include_str!("search_cids_setup_ordered.sql"))?;
        Ok(())
    }

    pub(crate) fn clear_searched_cards_table(&self) -> Result<()> {
        self.db.execute("drop table if exists search_cids", [])?;
        Ok(())
    }

    pub(crate) fn setup_fsrs_preset_search_cards_table(&self) -> Result<()> {
        self.db.execute_batch(
            "DROP TABLE IF EXISTS fsrs_preset_search_cids;
CREATE TEMPORARY TABLE fsrs_preset_search_cids (cid integer PRIMARY KEY NOT NULL);",
        )?;
        Ok(())
    }

    pub(crate) fn clear_fsrs_preset_search_cards_table(&self) -> Result<()> {
        self.db
            .execute("drop table if exists fsrs_preset_search_cids", [])?;
        Ok(())
    }

    /// Injects the provided card IDs into the search_cids table, for
    /// when ids have arrived outside of a search.
    /// One statement instead of one autocommit insert per id; the ordered
    /// table keeps the given order.
    pub(crate) fn set_search_table_to_card_ids(&self, cards: &[CardId]) -> Result<()> {
        let mut json = String::with_capacity(cards.len() * 14 + 2);
        json.push('[');
        super::write_comma_separated_ids(&mut json, cards);
        json.push(']');
        self.db.execute(
            "insert into search_cids select value from json_each(?) order by key",
            [json],
        )?;
        Ok(())
    }

    /// Inserts the ids in one statement and in key order, which is several
    /// times faster than one insert per id for startup-sized batches.
    pub(crate) fn set_fsrs_preset_search_table_to_card_ids(&self, cards: &[CardId]) -> Result<()> {
        let mut sorted = cards.to_vec();
        sorted.sort_unstable();
        let mut json = String::with_capacity(sorted.len() * 14 + 2);
        json.push('[');
        super::write_comma_separated_ids(&mut json, sorted);
        json.push(']');
        self.db.execute(
            "insert into fsrs_preset_search_cids select value from json_each(?)",
            [json],
        )?;
        Ok(())
    }

    pub(crate) fn remove_fsrs_preset_search_table_card_ids(&self, cards: &[CardId]) -> Result<()> {
        let mut stmt = self
            .db
            .prepare_cached("delete from fsrs_preset_search_cids where cid = ?")?;
        for cid in cards {
            stmt.execute([cid])?;
        }
        Ok(())
    }

    pub(crate) fn setup_fsrs_preset_first_grades_table(&self) -> Result<()> {
        self.db.execute_batch(
            "DROP TABLE IF EXISTS fsrs_preset_first_grades;
CREATE TEMPORARY TABLE fsrs_preset_first_grades (
  cid integer PRIMARY KEY NOT NULL,
  ease integer NOT NULL
);
INSERT INTO fsrs_preset_first_grades
SELECT sc.cid,
  (
    SELECT r.ease
    FROM revlog r
    WHERE r.cid = sc.cid
      AND r.ease BETWEEN 1 AND 4
    ORDER BY r.id
    LIMIT 1
  ) AS ease
FROM fsrs_preset_search_cids sc
WHERE ease IS NOT NULL;",
        )?;
        Ok(())
    }

    pub(crate) fn clear_fsrs_preset_first_grades_table(&self) -> Result<()> {
        self.db
            .execute("drop table if exists fsrs_preset_first_grades", [])?;
        Ok(())
    }

    /// Fix cards with low eases due to schema 15 bug.
    /// Deck configs were defaulting to 2.5% ease, which was capped to
    /// 130% when the deck options were edited for the first time.
    pub(crate) fn fix_low_card_eases_for_configs(
        &self,
        configs: &[DeckConfigId],
        server: bool,
    ) -> Result<()> {
        let mut affected_decks = vec![];
        for conf in configs {
            for (deck_id, _name) in self.get_all_deck_names()? {
                if let Some(deck) = self.get_deck(deck_id)? {
                    if let DeckKind::Normal(normal) = &deck.kind {
                        if normal.config_id == conf.0 {
                            affected_decks.push(deck.id);
                        }
                    }
                }
            }
        }

        let mut ids = String::new();
        ids_to_string(&mut ids, &affected_decks);
        let sql = include_str!("fix_low_ease.sql").replace("DECK_IDS", &ids);

        self.db.prepare(&sql)?.execute(params![self.usn(server)?])?;

        Ok(())
    }

    pub(crate) fn get_card_count_with_ignore_before(
        &self,
        ignore_before: TimestampMillis,
    ) -> Result<u64> {
        Ok(self
            .db
            .prepare(include_str!("get_ignored_before_count.sql"))?
            .query(params![ignore_before.0])?
            .next()
            .unwrap()
            .unwrap()
            .get(0)?)
    }

    #[cfg(test)]
    pub(crate) fn get_all_cards(&self) -> Vec<Card> {
        self.db
            .prepare("SELECT * FROM cards")
            .unwrap()
            .query_and_then([], row_to_card)
            .unwrap()
            .collect::<rusqlite::Result<_>>()
            .unwrap()
    }
}

fn due_card_from_review_row(row: &Row<'_>, kind: DueCardKind) -> Result<DueCard> {
    Ok(DueCard {
        id: row.get(0)?,
        note_id: row.get(1)?,
        due: row.get(2).ok().unwrap_or_default(),
        mtime: row.get(4)?,
        current_deck_id: row.get(5)?,
        original_deck_id: row.get(6)?,
        reps: row.get(7)?,
        kind,
    })
}

#[derive(Clone, Copy)]
pub(crate) enum ReviewOrderSubclause {
    Day,
    Deck,
    Random,
    IntervalsAscending,
    IntervalsDescending,
    EaseAscending,
    EaseDescending,
    /// FSRS
    DifficultyAscending,
    /// FSRS
    DifficultyDescending,
    RetrievabilityFsrs {
        timing: SchedTimingToday,
        order: SqlSortOrder,
    },
    RelativeOverdueness {
        fsrs: bool,
        timing: SchedTimingToday,
    },
    Added,
    ReverseAdded,
}

impl fmt::Display for ReviewOrderSubclause {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let temp_string;
        let clause = match self {
            ReviewOrderSubclause::Day => "due",
            ReviewOrderSubclause::Deck => "(select rowid from active_decks ad where ad.id = did)",
            ReviewOrderSubclause::Random => "fnvhash(id, mod)",
            ReviewOrderSubclause::IntervalsAscending => "ivl asc",
            ReviewOrderSubclause::IntervalsDescending => "ivl desc",
            ReviewOrderSubclause::EaseAscending => "factor asc",
            ReviewOrderSubclause::EaseDescending => "factor desc",
            ReviewOrderSubclause::DifficultyAscending => "extract_fsrs_variable(data, 'd') asc",
            ReviewOrderSubclause::DifficultyDescending => "extract_fsrs_variable(data, 'd') desc",
            ReviewOrderSubclause::RetrievabilityFsrs { timing, order } => {
                let today = timing.days_elapsed;
                let next_day_at = timing.next_day_at.0;
                let now = timing.now.0;
                temp_string =
                    format!("extract_fsrs_retrievability(data, case when odue !=0 then odue else due end, ivl, {today}, {next_day_at}, {now}) {order}");
                &temp_string
            }
            ReviewOrderSubclause::RelativeOverdueness { fsrs, timing } => {
                let today = timing.days_elapsed;
                let next_day_at = timing.next_day_at.0;
                let now = timing.now.0;
                temp_string = if *fsrs {
                    format!("extract_fsrs_relative_retrievability(data, case when odue !=0 then odue else due end, ivl, {today}, {next_day_at}, {now}) asc")
                } else {
                    format!(
                        // - (elapsed days+0.001)/(scheduled interval)
                        "-(1 + cast({today}-due+0.001 as real)/ivl) asc"
                    )
                };
                &temp_string
            }
            ReviewOrderSubclause::Added => "nid asc, ord asc",
            ReviewOrderSubclause::ReverseAdded => "nid desc, ord asc",
        };
        write!(f, "{clause}")
    }
}

fn review_order_sql(order: ReviewCardOrder, timing: SchedTimingToday, fsrs: bool) -> String {
    let mut subclauses = match order {
        ReviewCardOrder::Day => vec![ReviewOrderSubclause::Day],
        ReviewCardOrder::DayThenDeck => vec![ReviewOrderSubclause::Day, ReviewOrderSubclause::Deck],
        ReviewCardOrder::DeckThenDay => vec![ReviewOrderSubclause::Deck, ReviewOrderSubclause::Day],
        ReviewCardOrder::IntervalsAscending => vec![ReviewOrderSubclause::IntervalsAscending],
        ReviewCardOrder::IntervalsDescending => vec![ReviewOrderSubclause::IntervalsDescending],
        ReviewCardOrder::EaseAscending => {
            vec![if fsrs {
                ReviewOrderSubclause::DifficultyDescending
            } else {
                ReviewOrderSubclause::EaseAscending
            }]
        }
        ReviewCardOrder::EaseDescending => vec![if fsrs {
            ReviewOrderSubclause::DifficultyAscending
        } else {
            ReviewOrderSubclause::EaseDescending
        }],
        ReviewCardOrder::RetrievabilityAscending => {
            vec![ReviewOrderSubclause::RetrievabilityFsrs {
                timing,
                order: SqlSortOrder::Ascending,
            }]
        }
        ReviewCardOrder::RetrievabilityDescending => {
            vec![ReviewOrderSubclause::RetrievabilityFsrs {
                timing,
                order: SqlSortOrder::Descending,
            }]
        }
        ReviewCardOrder::RelativeOverdueness => {
            vec![ReviewOrderSubclause::RelativeOverdueness { fsrs, timing }]
        }
        // Draw from the entire pool before queue gathering applies eligibility
        // and deck limits. A stable id/mtime hash lets recently answered cards
        // jump ahead of a backlog whose low hashes have already been consumed.
        ReviewCardOrder::Random => return "random()".into(),
        ReviewCardOrder::Added => vec![ReviewOrderSubclause::Added],
        ReviewCardOrder::ReverseAdded => vec![ReviewOrderSubclause::ReverseAdded],
    };
    subclauses.push(ReviewOrderSubclause::Random);

    let v: Vec<_> = subclauses
        .iter()
        .map(ReviewOrderSubclause::to_string)
        .collect();
    v.join(", ")
}

#[derive(Debug, Clone, Copy)]
pub(crate) enum NewCardSorting {
    /// Ascending position, consecutive siblings,
    /// provided they have the same position.
    LowestPosition,
    /// Descending position, consecutive siblings,
    /// provided they have the same position.
    HighestPosition,
    /// Random, but with consecutive siblings.
    /// For some given salt the order is stable.
    RandomNotes(u32),
    /// Fully random.
    /// For some given salt the order is stable.
    RandomCards(u32),
}

impl NewCardSorting {
    fn write(self) -> String {
        match self {
            NewCardSorting::LowestPosition => "due ASC, ord ASC".to_string(),
            NewCardSorting::HighestPosition => "due DESC, ord ASC".to_string(),
            NewCardSorting::RandomNotes(salt) => format!("fnvhash(nid, {salt}), ord ASC"),
            NewCardSorting::RandomCards(salt) => format!("fnvhash(id, {salt})"),
        }
    }
}

#[cfg(test)]
mod test {
    use std::collections::HashSet;
    use std::path::Path;

    use anki_i18n::I18n;
    use rusqlite::named_params;
    use rusqlite::params;

    use crate::card::Card;
    use crate::card::CardQueue;
    use crate::card::CardType;
    use crate::collection::Collection;
    use crate::decks::Deck;
    use crate::decks::DeckId;
    use crate::decks::NativeDeckName;
    use crate::notes::NoteId;
    use crate::revlog::RevlogEntry;
    use crate::revlog::RevlogId;
    use crate::revlog::RevlogReviewKind;
    use crate::storage::deck::test::add_cards_around_cutoffs;
    use crate::storage::SqliteStorage;
    use crate::timestamp::TimestampSecs;
    use crate::types::Usn;

    fn create_test_storage() -> SqliteStorage {
        let tr: I18n = I18n::template_only();
        SqliteStorage::open_or_create(Path::new(":memory:"), &tr, false, false).unwrap()
    }

    #[test]
    fn card_entries_include_template_and_notetype_ids() {
        let storage = create_test_storage();
        storage
            .db
            .execute(
                "INSERT INTO notes (id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data) VALUES (100, '', 42, 0, 0, '', '', '', 0, 0, '')",
                [],
            )
            .unwrap();
        let mut card = Card {
            note_id: NoteId(100),
            template_idx: 3,
            ..Default::default()
        };
        storage.add_card(&mut card).unwrap();
        let entries = storage.get_all_card_entries().unwrap();
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].template_idx, 3);
        assert_eq!(entries[0].notetype_id, 42);
    }

    #[test]
    fn add_card() {
        let storage = create_test_storage();

        let mut card = Card::default();
        storage.add_card(&mut card).unwrap();
        let id1 = card.id;
        storage.add_card(&mut card).unwrap();
        assert_ne!(id1, card.id);
    }

    #[test]
    fn foreign_fsrs_state_detection_requires_state_and_missing_traces() -> crate::error::Result<()>
    {
        let storage = create_test_storage();
        let mut card = Card::default();
        storage.add_card(&mut card)?;
        for (data, expected) in [
            (r#"{"s":20,"d":6}"#, true),
            (r#"{"s":20,"s_int":20,"d":6}"#, true),
            (r#"{"s":20,"s_fast":5,"d":6}"#, true),
            (r#"{"s":20,"s_int":15,"s_fast":5,"d":6}"#, false),
            (r#"{"s":20}"#, false),
            (r#"{"d":6}"#, false),
            (r#"{}"#, false),
        ] {
            storage.db.execute(
                "update cards set data = ? where id = ?",
                params![data, card.id],
            )?;

            let ids = storage.card_ids_with_foreign_fsrs_state()?;

            assert_eq!(ids, if expected { vec![card.id] } else { vec![] }, "{data}");
        }
        Ok(())
    }

    #[test]
    fn due_card_state_matches_full_card_decoding() -> crate::error::Result<()> {
        use super::row_to_card;
        use super::row_to_due_card_with_state;
        use crate::scheduler::queue::DueCardWithState;

        let storage = create_test_storage();
        // Query synthetic rows, including values older clients may have stored.
        // No collection file or persisted fixture is involved.
        let row = r#"with cards as (select
            42 as id, 43 as nid, 44 as did, 2 as ord, 1234.5 as mod,
            0 as usn, 2 as type, ?1 as queue, ?2 as due, 12.5 as ivl,
            2500 as factor, 17 as reps, 3 as lapses, 2 as left,
            ?2 as odue, ?3 as odid, 0 as flags, ?4 as data) "#;
        let mut full = storage
            .db
            .prepare(&format!("{row}{}", include_str!("get_card.sql")))?;
        let mut narrow = storage
            .db
            .prepare(&format!("{row}{}", include_str!("get_due_card_state.sql")))?;
        for queue in [1, 2, 3, 4] {
            for due in [
                rusqlite::types::Value::Integer(100),
                rusqlite::types::Value::Real(100.5),
            ] {
                for original_deck in [0, 45] {
                    for data in [
                        r#"{"s":30,"s_int":40,"s_fast":0.25,"d":6,"dr":0.85,"lrt":123456,"cd":"{\"x\":1}"}"#,
                        r#"{"s":30,"d":6}"#,
                        r#"{"s":30,"s_int":"bad","s_fast":[],"d":6,"dr":"bad","lrt":{}}"#,
                        r#"{"s":"bad","d":6}"#,
                        r#"{"s":30,"d":6,"cd":42}"#,
                        "not json",
                        "",
                    ] {
                        let params = params![queue, due, original_deck, data];
                        let card = full.query_row(params, row_to_card)?;
                        let candidate = narrow.query_row(params, row_to_due_card_with_state)?;
                        assert_eq!(candidate, DueCardWithState::from(&card), "{data}");
                        assert_eq!(candidate.interval, 12);
                        assert_eq!(candidate.card.mtime, TimestampSecs(1234));
                    }
                }
            }
        }
        Ok(())
    }

    #[test]
    fn due_cards_with_state_respects_queue_cutoffs_and_active_decks() -> crate::error::Result<()> {
        use crate::scheduler::timing::SchedTimingToday;

        let mut col = Collection::new();
        let active = col.get_or_create_normal_deck("Active")?;
        let child = col.get_or_create_normal_deck("Active::Child")?;
        let inactive = col.get_or_create_normal_deck("Inactive")?;
        let timing = SchedTimingToday {
            now: TimestampSecs(1_000_000),
            next_day_at: TimestampSecs(1_010_000),
            days_elapsed: 100,
        };
        let mut expected = Vec::new();
        for deck in [&active, &child, &inactive] {
            for (queue, cutoff) in [
                (CardQueue::Review, 100),
                (CardQueue::DayLearn, 100),
                (CardQueue::Learn, 1_000_000),
                (CardQueue::PreviewRepeat, 1_000_000),
                (CardQueue::New, 0),
                (CardQueue::Suspended, 0),
                (CardQueue::SchedBuried, 0),
                (CardQueue::UserBuried, 0),
            ] {
                for offset in [-1, 0, 1] {
                    let mut card = Card {
                        deck_id: deck.id,
                        queue,
                        due: cutoff + offset,
                        ..Default::default()
                    };
                    col.add_card(&mut card)?;
                    if deck.id != inactive.id && cutoff != 0 && offset <= 0 {
                        expected.push((deck.id, queue as i8, card.due));
                    }
                }
            }
        }
        col.storage.update_active_decks(&active)?;
        let mut actual = col
            .storage
            .due_cards_with_state_in_active_decks(timing)?
            .into_iter()
            .map(|candidate| {
                (
                    candidate.card.current_deck_id,
                    candidate.queue as i8,
                    candidate.card.due,
                )
            })
            .collect::<Vec<_>>();
        actual.sort_unstable();
        expected.sort_unstable();
        assert_eq!(actual, expected);
        Ok(())
    }

    #[test]
    fn fix_card_properties_last_review_time() {
        let mut col = Collection::new();

        let mut new_card = Card::default();
        col.add_card(&mut new_card).unwrap();
        col.storage
            .db
            .execute(
                "UPDATE cards SET due = 2000001, ord = 40000 WHERE id = ?1",
                params![new_card.id],
            )
            .unwrap();

        let mut review_card = Card {
            ctype: CardType::Review,
            queue: CardQueue::Review,
            ..Default::default()
        };
        col.add_card(&mut review_card).unwrap();
        col.storage
            .db
            .execute(
                "UPDATE cards SET due = 2000001, queue = 2, type = 2, odue = 10, ivl = 1.5 WHERE id = ?1",
                params![review_card.id],
            )
            .unwrap();

        let mut mod_card = Card::default();
        col.add_card(&mut mod_card).unwrap();
        col.storage
            .db
            .execute(
                "UPDATE cards SET mod = 1.5 WHERE id = ?1",
                params![mod_card.id],
            )
            .unwrap();

        let mut revlog_card = Card {
            ctype: CardType::Review,
            queue: CardQueue::Review,
            last_review_time: Some(TimestampSecs(999)),
            ..Default::default()
        };
        col.add_card(&mut revlog_card).unwrap();

        let revlog_entry = RevlogEntry {
            id: RevlogId(2000),
            cid: revlog_card.id,
            usn: Usn(1),
            button_chosen: 2,
            interval: 1,
            last_interval: 0,
            ease_factor: 2500,
            taken_millis: 0,
            review_kind: RevlogReviewKind::Review,
        };
        col.storage.add_revlog_entry(&revlog_entry, false).unwrap();

        let stats = col
            .storage
            .fix_card_properties(999, TimestampSecs(123), Usn(42), false)
            .unwrap();

        assert_eq!(stats.new_cards_fixed, 1);
        assert_eq!(stats.other_cards_fixed, 5);
        assert_eq!(stats.last_review_time_fixed, 1);

        let new_card = col.storage.get_card(new_card.id).unwrap().unwrap();
        assert_eq!(new_card.due, 1000001);
        assert_eq!(new_card.template_idx, 30000);
        assert_eq!(new_card.mtime, TimestampSecs(123));
        assert_eq!(new_card.usn, Usn(42));

        let review_card = col.storage.get_card(review_card.id).unwrap().unwrap();
        let review_odue: i32 = col
            .storage
            .db
            .query_row(
                "SELECT odue FROM cards WHERE id = ?1",
                params![review_card.id],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(review_card.due, 999);
        assert_eq!(review_odue, 0);
        assert_eq!(review_card.interval, 2);
        assert_eq!(review_card.mtime, TimestampSecs(123));
        assert_eq!(review_card.usn, Usn(42));

        let mod_card = col.storage.get_card(mod_card.id).unwrap().unwrap();
        assert_eq!(mod_card.mtime, TimestampSecs(1));

        let revlog_card = col.storage.get_card(revlog_card.id).unwrap().unwrap();
        assert_eq!(revlog_card.last_review_time, Some(TimestampSecs(2)));
    }

    type Congrats = (bool, bool, bool, bool, u32, u32);

    /// The congrats info exactly as the original one-pass query computed it,
    /// for the decks in active_decks.
    fn reference_congrats(storage: &SqliteStorage, today: u32) -> Congrats {
        storage
            .db
            .query_row(
                "SELECT coalesce(sum(queue IN (:review_queue, :day_learn_queue)
                     AND due <= :today), 0),
                   coalesce(sum(queue = :new_queue), 0),
                   coalesce(sum(queue = :sched_buried_queue), 0),
                   coalesce(sum(queue = :user_buried_queue), 0),
                   coalesce(sum(queue = :learn_queue), 0),
                   max(0, coalesce(min(CASE WHEN queue = :learn_queue THEN due
                     ELSE NULL END), 0))
                 FROM cards WHERE did IN (SELECT id FROM active_decks)",
                named_params! {
                    ":review_queue": CardQueue::Review as i8,
                    ":day_learn_queue": CardQueue::DayLearn as i8,
                    ":new_queue": CardQueue::New as i8,
                    ":user_buried_queue": CardQueue::UserBuried as i8,
                    ":sched_buried_queue": CardQueue::SchedBuried as i8,
                    ":learn_queue": CardQueue::Learn as i8,
                    ":today": today,
                },
                |row| {
                    Ok((
                        row.get::<_, u32>(0)? > 0,
                        row.get::<_, u32>(1)? > 0,
                        row.get::<_, u32>(2)? > 0,
                        row.get::<_, u32>(3)? > 0,
                        row.get(4)?,
                        row.get(5)?,
                    ))
                },
            )
            .unwrap()
    }

    #[test]
    fn congrats_info_matches_the_one_pass_query() {
        const TODAY: u32 = 100;
        const LEARN_CUTOFF: u32 = 1_700_000_000;
        let mut col = Collection::new();
        let mut decks = vec![col.storage.get_deck(DeckId(1)).unwrap().unwrap()];
        for name in ["A", "A::B", "A::B::C", "D", "E"] {
            decks.push(col.get_or_create_normal_deck(name).unwrap());
        }
        let mut filtered = Deck::new_filtered();
        filtered.name = NativeDeckName::from_native_str("F");
        col.add_or_update_deck(&mut filtered).unwrap();
        decks.push(filtered);
        // "E" keeps no cards; "D" gets a learning card due before 1970
        let with_cards: Vec<_> = decks
            .iter()
            .filter(|deck| deck.name.as_native_str() != "E")
            .map(|deck| deck.id)
            .collect();
        let mut card = Card {
            deck_id: decks[4].id,
            queue: CardQueue::Learn,
            due: -5,
            ..Default::default()
        };
        col.storage.add_card(&mut card).unwrap();

        let mut seen = HashSet::new();
        for (seed, count) in [(0, 0), (1, 6), (2, 30), (3, 3000)] {
            add_cards_around_cutoffs(&mut col, &with_cards, TODAY, LEARN_CUTOFF, seed, count);
            for deck in &decks {
                let info = col.storage.congrats_info(deck, TODAY).unwrap();
                let got = (
                    info.review_remaining,
                    info.new_remaining,
                    info.have_sched_buried,
                    info.have_user_buried,
                    info.learn_count,
                    info.next_learn_due,
                );
                // congrats_info() filled active_decks for this deck
                assert_eq!(
                    got,
                    reference_congrats(&col.storage, TODAY),
                    "{}",
                    deck.name
                );
                seen.insert(got);
            }
        }
        // both answers of every yes/no question were checked
        for flag in 0..4 {
            let flags: HashSet<bool> = seen.iter().map(|c| [c.0, c.1, c.2, c.3][flag]).collect();
            assert_eq!(flags.len(), 2, "flag {flag}");
        }
    }
}
