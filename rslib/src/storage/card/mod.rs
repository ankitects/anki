// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::{Card, CardID, CardQueue, CardType},
    deckconf::DeckConfID,
    decks::{Deck, DeckID, DeckKind},
    err::Result,
    notes::NoteID,
    sched::congrats::CongratsInfo,
    timestamp::{TimestampMillis, TimestampSecs},
    types::Usn,
};
use rusqlite::params;
use rusqlite::{
    named_params,
    types::{FromSql, FromSqlError, ValueRef},
    OptionalExtension, Row, NO_PARAMS,
};
use std::{collections::HashSet, convert::TryFrom, result};

use super::ids_to_string;

impl FromSql for CardType {
    fn column_result(value: ValueRef<'_>) -> std::result::Result<Self, FromSqlError> {
        if let ValueRef::Integer(i) = value {
            Ok(Self::try_from(i as u8).map_err(|_| FromSqlError::InvalidType)?)
        } else {
            Err(FromSqlError::InvalidType)
        }
    }
}

impl FromSql for CardQueue {
    fn column_result(value: ValueRef<'_>) -> std::result::Result<Self, FromSqlError> {
        if let ValueRef::Integer(i) = value {
            Ok(Self::try_from(i as i8).map_err(|_| FromSqlError::InvalidType)?)
        } else {
            Err(FromSqlError::InvalidType)
        }
    }
}

fn row_to_card(row: &Row) -> result::Result<Card, rusqlite::Error> {
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
        data: row.get(17)?,
    })
}

impl super::SqliteStorage {
    pub fn get_card(&self, cid: CardID) -> Result<Option<Card>> {
        self.db
            .prepare_cached(concat!(include_str!("get_card.sql"), " where id = ?"))?
            .query_row(params![cid], row_to_card)
            .optional()
            .map_err(Into::into)
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
            card.data,
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
            card.data,
        ])?;
        card.id = CardID(self.db.last_insert_rowid());
        Ok(())
    }

    /// Add or update card, using the provided ID. Used when syncing.
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
            card.data,
        ])?;

        Ok(())
    }

    pub(crate) fn remove_card(&self, cid: CardID) -> Result<()> {
        self.db
            .prepare_cached("delete from cards where id = ?")?
            .execute(&[cid])?;
        Ok(())
    }

    /// Fix some invalid card properties, and return number of changed cards.
    pub(crate) fn fix_card_properties(
        &self,
        today: u32,
        mtime: TimestampSecs,
        usn: Usn,
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
            .execute(params![mtime, usn])?;
        other_cnt += self
            .db
            .prepare(include_str!("fix_ivl.sql"))?
            .execute(params![mtime, usn])?;
        Ok((new_cnt, other_cnt))
    }

    pub(crate) fn delete_orphaned_cards(&self) -> Result<usize> {
        self.db
            .prepare("delete from cards where nid not in (select id from notes)")?
            .execute(NO_PARAMS)
            .map_err(Into::into)
    }

    pub(crate) fn all_filtered_cards_by_deck(&self) -> Result<Vec<(CardID, DeckID)>> {
        self.db
            .prepare("select id, did from cards where odid > 0")?
            .query_and_then(NO_PARAMS, |r| -> Result<_> { Ok((r.get(0)?, r.get(1)?)) })?
            .collect()
    }

    pub(crate) fn max_new_card_position(&self) -> Result<u32> {
        self.db
            .prepare("select max(due)+1 from cards where type=0")?
            .query_row(NO_PARAMS, |r| r.get(0))
            .map_err(Into::into)
    }

    pub(crate) fn get_card_by_ordinal(&self, nid: NoteID, ord: u16) -> Result<Option<Card>> {
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
            .execute(NO_PARAMS)?;
        Ok(())
    }

    pub(crate) fn have_at_least_one_card(&self) -> Result<bool> {
        self.db
            .prepare_cached("select null from cards")?
            .query(NO_PARAMS)?
            .next()
            .map(|o| o.is_some())
            .map_err(Into::into)
    }

    pub(crate) fn all_cards_of_note(&self, nid: NoteID) -> Result<Vec<Card>> {
        self.db
            .prepare_cached(concat!(include_str!("get_card.sql"), " where nid = ?"))?
            .query_and_then(&[nid], |r| row_to_card(r).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn all_card_ids_of_note(&self, nid: NoteID) -> Result<Vec<CardID>> {
        self.db
            .prepare_cached("select id from cards where nid = ? order by ord")?
            .query_and_then(&[nid], |r| Ok(CardID(r.get(0)?)))?
            .collect()
    }

    pub(crate) fn note_ids_of_cards(&self, cids: &[CardID]) -> Result<HashSet<NoteID>> {
        let mut stmt = self
            .db
            .prepare_cached("select nid from cards where id = ?")?;
        let mut nids = HashSet::new();
        for cid in cids {
            if let Some(nid) = stmt
                .query_row(&[cid], |r| r.get::<_, NoteID>(0))
                .optional()?
            {
                nids.insert(nid);
            }
        }
        Ok(nids)
    }

    pub(crate) fn all_searched_cards(&self) -> Result<Vec<Card>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get_card.sql"),
                " where id in (select cid from search_cids)"
            ))?
            .query_and_then(NO_PARAMS, |r| row_to_card(r).map_err(Into::into))?
            .collect()
    }

    pub(crate) fn all_searched_cards_in_search_order(&self) -> Result<Vec<Card>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get_card.sql"),
                ", search_cids where cards.id = search_cids.cid order by search_cids.rowid"
            ))?
            .query_and_then(NO_PARAMS, |r| row_to_card(r).map_err(Into::into))?
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
        let mut rows = stmt.query(NO_PARAMS)?;
        while let Some(row) = rows.next()? {
            let card = row_to_card(row)?;
            func(card)?
        }

        Ok(())
    }

    pub(crate) fn congrats_info(&self, current: &Deck, today: u32) -> Result<CongratsInfo> {
        self.update_active_decks(current)?;
        self.db
            .prepare(include_str!("congrats.sql"))?
            .query_and_then_named(
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

    pub(crate) fn search_cards_at_or_above_position(&self, start: u32) -> Result<()> {
        self.setup_searched_cards_table()?;
        self.db
            .prepare(include_str!("at_or_above_position.sql"))?
            .execute(&[start, CardType::New as u32])?;
        Ok(())
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
        self.db
            .execute("drop table if exists search_cids", NO_PARAMS)?;
        Ok(())
    }

    /// Injects the provided card IDs into the search_cids table, for
    /// when ids have arrived outside of a search.
    /// Clear with clear_searched_cards().
    pub(crate) fn set_search_table_to_card_ids(
        &mut self,
        cards: &[CardID],
        preserve_order: bool,
    ) -> Result<()> {
        if preserve_order {
            self.setup_searched_cards_table_to_preserve_order()?;
        } else {
            self.setup_searched_cards_table()?;
        }
        let mut stmt = self
            .db
            .prepare_cached("insert into search_cids values (?)")?;
        for cid in cards {
            stmt.execute(&[cid])?;
        }

        Ok(())
    }

    /// Fix cards with low eases due to schema 15 bug.
    /// Deck configs were defaulting to 2.5% ease, which was capped to
    /// 130% when the deck options were edited for the first time.
    pub(crate) fn fix_low_card_eases_for_configs(
        &self,
        configs: &[DeckConfID],
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
}

#[cfg(test)]
mod test {
    use crate::{card::Card, i18n::I18n, log, storage::SqliteStorage};
    use std::path::Path;

    #[test]
    fn add_card() {
        let i18n = I18n::new(&[""], "", log::terminal());
        let storage = SqliteStorage::open_or_create(Path::new(":memory:"), &i18n, false).unwrap();
        let mut card = Card::default();
        storage.add_card(&mut card).unwrap();
        let id1 = card.id;
        storage.add_card(&mut card).unwrap();
        assert_ne!(id1, card.id);
    }
}
