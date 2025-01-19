// Copyright: Ankitects Pty Ltd and contributors
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
use crate::card::Card;
use crate::card::CardId;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::deckconfig::DeckConfigId;
use crate::deckconfig::ReviewCardOrder;
use crate::decks::Deck;
use crate::decks::DeckId;
use crate::decks::DeckKind;
use crate::error::Result;
use crate::notes::NoteId;
use crate::scheduler::congrats::CongratsInfo;
use crate::scheduler::queue::BuryMode;
use crate::scheduler::queue::DueCard;
use crate::scheduler::queue::DueCardKind;
use crate::scheduler::queue::NewCard;
use crate::scheduler::timing::SchedTimingToday;
use crate::timestamp::TimestampMillis;
use crate::timestamp::TimestampSecs;
use crate::types::Usn;

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
        custom_data: data.custom_data,
    })
}

fn row_to_card_entry(row: &Row) -> Result<CardEntry> {
    Ok(CardEntry {
        id: row.get(0)?,
        note_id: row.get(1)?,
        deck_id: row.get(2)?,
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
    pub fn get_card(&self, cid: CardId) -> Result<Option<Card>> {
        self.db
            .prepare_cached(concat!(include_str!("get_card.sql"), " where id = ?"))?
            .query_row(params![cid], row_to_card)
            .optional()
            .map_err(Into::into)
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
            if !func(DueCard {
                id: row.get(0)?,
                note_id: row.get(1)?,
                due: row.get(2).ok().unwrap_or_default(),
                mtime: row.get(4)?,
                current_deck_id: row.get(5)?,
                original_deck_id: row.get(6)?,
                kind,
            })? {
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
    ) -> Result<(usize, usize)> {
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
        Ok((new_cnt, other_cnt))
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

    /// Injects the provided card IDs into the search_cids table, for
    /// when ids have arrived outside of a search.
    pub(crate) fn set_search_table_to_card_ids(&self, cards: &[CardId]) -> Result<()> {
        let mut stmt = self
            .db
            .prepare_cached("insert into search_cids values (?)")?;
        for cid in cards {
            stmt.execute([cid])?;
        }
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

#[derive(Clone, Copy)]
enum ReviewOrderSubclause {
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
    RetrievabilitySm2 {
        today: u32,
        order: SqlSortOrder,
    },
    RetrievabilityFsrs {
        timing: SchedTimingToday,
        order: SqlSortOrder,
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
            ReviewOrderSubclause::RetrievabilitySm2 { today, order } => {
                // - (elapsed days+0.001)/(scheduled interval)
                temp_string = format!(
                    "(-1 - cast({today}-due+0.001 as real)/ivl) {order}",
                    today = today
                );
                &temp_string
            }
            ReviewOrderSubclause::RetrievabilityFsrs { timing, order } => {
                let today = timing.days_elapsed;
                let next_day_at = timing.next_day_at.0;
                temp_string =
                    format!("extract_fsrs_relative_retrievability(data, case when odue !=0 then odue else due end, {today}, ivl, {next_day_at}) {order}");
                &temp_string
            }
            ReviewOrderSubclause::Added => "nid asc, ord asc",
            ReviewOrderSubclause::ReverseAdded => "nid desc, ord asc",
        };
        write!(f, "{}", clause)
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
            build_retrievability_clauses(fsrs, timing, SqlSortOrder::Ascending)
        }
        ReviewCardOrder::RetrievabilityDescending => {
            build_retrievability_clauses(fsrs, timing, SqlSortOrder::Descending)
        }
        ReviewCardOrder::Random => vec![],
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

fn build_retrievability_clauses(
    fsrs: bool,
    timing: SchedTimingToday,
    order: SqlSortOrder,
) -> Vec<ReviewOrderSubclause> {
    vec![if fsrs {
        ReviewOrderSubclause::RetrievabilityFsrs { timing, order }
    } else {
        ReviewOrderSubclause::RetrievabilitySm2 {
            today: timing.days_elapsed,
            order,
        }
    }]
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
    use std::path::Path;

    use anki_i18n::I18n;

    use crate::card::Card;
    use crate::storage::SqliteStorage;

    #[test]
    fn add_card() {
        let tr = I18n::template_only();
        let storage =
            SqliteStorage::open_or_create(Path::new(":memory:"), &tr, false, false).unwrap();
        let mut card = Card::default();
        storage.add_card(&mut card).unwrap();
        let id1 = card.id;
        storage.add_card(&mut card).unwrap();
        assert_ne!(id1, card.id);
    }
}
