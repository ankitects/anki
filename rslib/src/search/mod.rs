// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod builder;
mod parser;
mod service;
mod sqlwriter;
pub(crate) mod writer;

use std::borrow::Cow;
use std::cmp::Ordering;

pub use builder::JoinSearches;
pub use builder::Negated;
pub use builder::SearchBuilder;
pub use parser::parse as parse_search;
pub use parser::FieldSearchMode;
pub use parser::Node;
pub use parser::PropertyKind;
pub use parser::RatingKind;
pub use parser::SearchNode;
pub use parser::StateKind;
pub use parser::TemplateKind;
use rusqlite::params_from_iter;
use rusqlite::types::FromSql;
use sqlwriter::RequiredTable;
use sqlwriter::SqlWriter;
pub use writer::replace_search_node;

use crate::browser_table::Column;
use crate::card::CardType;
use crate::prelude::*;
use crate::scheduler::fsrs::metrics::FsrsMetricContext;
use crate::scheduler::timing::SchedTimingToday;

const EXACT_RETRIEVABILITY_TABLE: &str = "search_exact_retrievability";

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub enum ReturnItemType {
    Cards,
    Notes,
}

#[derive(Debug, PartialEq, Eq, Clone)]
pub enum SortMode {
    NoOrder,
    Builtin { column: Column, reverse: bool },
    Custom(String),
}

pub trait AsReturnItemType {
    fn as_return_item_type() -> ReturnItemType;
}

impl AsReturnItemType for CardId {
    fn as_return_item_type() -> ReturnItemType {
        ReturnItemType::Cards
    }
}

impl AsReturnItemType for NoteId {
    fn as_return_item_type() -> ReturnItemType {
        ReturnItemType::Notes
    }
}

impl ReturnItemType {
    fn required_table(&self) -> RequiredTable {
        match self {
            ReturnItemType::Cards => RequiredTable::Cards,
            ReturnItemType::Notes => RequiredTable::Notes,
        }
    }
}

impl SortMode {
    fn required_table(&self) -> RequiredTable {
        match self {
            SortMode::NoOrder => RequiredTable::CardsOrNotes,
            SortMode::Builtin { column, .. } => column.required_table(),
            SortMode::Custom(ref text) => {
                if text.contains("n.") {
                    if text.contains("c.") {
                        RequiredTable::CardsAndNotes
                    } else {
                        RequiredTable::Notes
                    }
                } else {
                    RequiredTable::Cards
                }
            }
        }
    }
}

impl Column {
    fn required_table(self) -> RequiredTable {
        match self {
            Column::Cards
            | Column::NoteCreation
            | Column::NoteMod
            | Column::Notetype
            | Column::SortField
            | Column::Tags => RequiredTable::Notes,
            _ => RequiredTable::CardsOrNotes,
        }
    }
}

pub trait TryIntoSearch {
    fn try_into_search(self) -> Result<Node, AnkiError>;
}

impl TryIntoSearch for &str {
    fn try_into_search(self) -> Result<Node, AnkiError> {
        parser::parse(self).map(Node::Group)
    }
}

impl TryIntoSearch for &String {
    fn try_into_search(self) -> Result<Node, AnkiError> {
        parser::parse(self).map(Node::Group)
    }
}

impl<T> TryIntoSearch for T
where
    T: Into<Node>,
{
    fn try_into_search(self) -> Result<Node, AnkiError> {
        Ok(self.into())
    }
}

pub struct CardTableGuard<'a> {
    pub col: &'a mut Collection,
    pub cards: usize,
}

impl Drop for CardTableGuard<'_> {
    fn drop(&mut self) {
        if let Err(err) = self.col.storage.clear_searched_cards_table() {
            println!("{err:?}");
        }
    }
}

pub struct NoteTableGuard<'a> {
    pub col: &'a mut Collection,
    pub notes: usize,
}

impl Drop for NoteTableGuard<'_> {
    fn drop(&mut self) {
        if let Err(err) = self.col.storage.clear_searched_notes_table() {
            println!("{err:?}");
        }
    }
}

impl Collection {
    pub fn search_cards<N>(&mut self, search: N, mode: SortMode) -> Result<Vec<CardId>>
    where
        N: TryIntoSearch,
    {
        if let Some(reverse) = exact_retrievability_sort_mode(ReturnItemType::Cards, &mode) {
            let top_node = search.try_into_search()?;
            let mut ids = self.search_card_ids_for_node(&top_node, mode.required_table())?;
            self.sort_card_ids_by_exact_retrievability(&mut ids, reverse)?;
            return Ok(ids);
        }
        self.search(search, mode)
    }

    pub fn search_notes<N>(&mut self, search: N, mode: SortMode) -> Result<Vec<NoteId>>
    where
        N: TryIntoSearch,
    {
        self.search(search, mode)
    }

    pub fn search_notes_unordered<N>(&mut self, search: N) -> Result<Vec<NoteId>>
    where
        N: TryIntoSearch,
    {
        self.search(search, SortMode::NoOrder)
    }
}

impl Collection {
    fn with_exact_retrievability_table<R>(
        &mut self,
        needed: bool,
        op: impl FnOnce(&mut Self) -> Result<R>,
    ) -> Result<R> {
        if needed {
            if let Err(err) = self.setup_exact_retrievability_table() {
                let _ = self.clear_exact_retrievability_table();
                return Err(err);
            }
        }
        let result = op(self);
        let cleanup = if needed {
            self.clear_exact_retrievability_table()
        } else {
            Ok(())
        };
        match (result, cleanup) {
            (Err(err), _) => Err(err),
            (Ok(_), Err(err)) => Err(err),
            (Ok(value), Ok(())) => Ok(value),
        }
    }

    fn setup_exact_retrievability_table(&mut self) -> Result<()> {
        self.storage.db.execute_batch(&format!(
            "drop table if exists {EXACT_RETRIEVABILITY_TABLE};\
             create temporary table {EXACT_RETRIEVABILITY_TABLE}(\
                cid integer primary key, retrievability real)"
        ))?;
        let timing = self.timing_today()?;
        let decks = self.storage.get_decks_map()?;
        let configs = self.storage.get_deck_config_map()?;
        let mut metrics = FsrsMetricContext::new(&decks, &configs);
        let cards = self.storage.all_cards_for_fsrs_metrics()?;
        let mut rows = Vec::new();
        for card in cards {
            if let Some(state) = card.memory_state {
                let elapsed_days =
                    card.seconds_since_last_review(&timing).unwrap_or_default() as f32 / 86_400.0;
                rows.push((
                    card.id.0,
                    metrics.current_retrievability(&card, state, elapsed_days)?,
                ));
            }
        }
        let mut insert = self.storage.db.prepare_cached(&format!(
            "insert into {EXACT_RETRIEVABILITY_TABLE}(cid, retrievability) values (?, ?)"
        ))?;
        for (cid, retrievability) in rows {
            insert.execute(rusqlite::params![cid, retrievability])?;
        }
        Ok(())
    }

    fn clear_exact_retrievability_table(&self) -> Result<()> {
        self.storage.db.execute(
            &format!("drop table if exists {EXACT_RETRIEVABILITY_TABLE}"),
            [],
        )?;
        Ok(())
    }

    fn search_card_ids_for_node(
        &mut self,
        top_node: &Node,
        required_table: RequiredTable,
    ) -> Result<Vec<CardId>> {
        self.with_exact_retrievability_table(has_retrievability_property(top_node), |col| {
            let writer = SqlWriter::new(col, ReturnItemType::Cards);
            let (sql, args) = writer.build_query(top_node, required_table)?;
            let mut stmt = col.storage.db.prepare(&sql)?;
            let ids = stmt
                .query_map(params_from_iter(args.iter()), |row| row.get(0))?
                .collect::<std::result::Result<_, _>>()
                .map_err(Into::into);
            ids
        })
    }

    fn sort_card_ids_by_exact_retrievability(
        &mut self,
        ids: &mut [CardId],
        reverse: bool,
    ) -> Result<()> {
        let timing = self.timing_today()?;
        let cards = self.all_cards_for_ids(ids, false)?;
        let decks = self.storage.get_decks_map()?;
        let configs = self.storage.get_deck_config_map()?;
        let mut metrics = FsrsMetricContext::new(&decks, &configs);
        let mut with_retrievability = Vec::with_capacity(ids.len());
        for card in cards {
            let retrievability = if let Some(state) = card.memory_state {
                let elapsed_days =
                    card.seconds_since_last_review(&timing).unwrap_or_default() as f32 / 86_400.0;
                Some(metrics.current_retrievability(&card, state, elapsed_days)?)
            } else {
                None
            };
            with_retrievability.push((card.id, retrievability));
        }
        with_retrievability.sort_by(|(cid_a, r_a), (cid_b, r_b)| {
            let order = match (r_a, r_b) {
                (Some(a), Some(b)) => a.total_cmp(b),
                (None, Some(_)) => Ordering::Less,
                (Some(_), None) => Ordering::Greater,
                (None, None) => Ordering::Equal,
            };
            let order = if reverse { order.reverse() } else { order };
            order.then_with(|| cid_a.cmp(cid_b))
        });
        for (target, (cid, _)) in ids.iter_mut().zip(with_retrievability) {
            *target = cid;
        }
        Ok(())
    }

    fn search<T, N>(&mut self, search: N, mode: SortMode) -> Result<Vec<T>>
    where
        N: TryIntoSearch,
        T: FromSql + AsReturnItemType,
    {
        let item_type = T::as_return_item_type();
        let top_node = search.try_into_search()?;
        self.with_exact_retrievability_table(has_retrievability_property(&top_node), |col| {
            let writer = SqlWriter::new(col, item_type);
            let (mut sql, args) = writer.build_query(&top_node, mode.required_table())?;
            col.add_order(&mut sql, item_type, mode)?;

            let mut stmt = col.storage.db.prepare(&sql)?;
            let ids = stmt
                .query_map(params_from_iter(args.iter()), |row| row.get(0))?
                .collect::<std::result::Result<_, _>>()?;
            Ok(ids)
        })
    }

    fn add_order(
        &mut self,
        sql: &mut String,
        item_type: ReturnItemType,
        mode: SortMode,
    ) -> Result<()> {
        match mode {
            SortMode::NoOrder => (),
            SortMode::Builtin { column, reverse } => {
                prepare_sort(self, column, item_type)?;
                sql.push_str(" order by ");
                write_order(sql, item_type, column, reverse, self.timing_today()?)?;
            }
            SortMode::Custom(order_clause) => {
                sql.push_str(" order by ");
                sql.push_str(&order_clause);
            }
        }
        Ok(())
    }

    /// Place the matched card ids into a temporary 'search_cids' table
    /// instead of returning them. Returns a guard with a collection reference
    /// and the number of added cards. When the guard is dropped, the temporary
    /// table is cleaned up.
    pub(crate) fn search_cards_into_table(
        &mut self,
        search: impl TryIntoSearch,
        mode: SortMode,
    ) -> Result<CardTableGuard<'_>> {
        let top_node = search.try_into_search()?;
        if let Some(reverse) = exact_retrievability_sort_mode(ReturnItemType::Cards, &mode) {
            let mut ids = self.search_card_ids_for_node(&top_node, mode.required_table())?;
            self.sort_card_ids_by_exact_retrievability(&mut ids, reverse)?;
            self.storage
                .setup_searched_cards_table_to_preserve_order()?;
            self.storage.set_search_table_to_card_ids(&ids)?;
            return Ok(CardTableGuard {
                cards: ids.len(),
                col: self,
            });
        }
        let want_order = mode != SortMode::NoOrder;

        let cards =
            self.with_exact_retrievability_table(has_retrievability_property(&top_node), |col| {
                let writer = SqlWriter::new(col, ReturnItemType::Cards);
                let (mut sql, args) = writer.build_query(&top_node, mode.required_table())?;
                col.add_order(&mut sql, ReturnItemType::Cards, mode)?;

                if want_order {
                    col.storage.setup_searched_cards_table_to_preserve_order()?;
                } else {
                    col.storage.setup_searched_cards_table()?;
                }
                let sql = format!("insert into search_cids {sql}");

                col.storage
                    .db
                    .prepare(&sql)?
                    .execute(params_from_iter(args))
                    .map_err(Into::into)
            })?;

        Ok(CardTableGuard { cards, col: self })
    }

    pub(crate) fn all_cards_for_search(&mut self, search: impl TryIntoSearch) -> Result<Vec<Card>> {
        let guard = self.search_cards_into_table(search, SortMode::NoOrder)?;
        guard.col.storage.all_searched_cards()
    }

    pub(crate) fn all_cards_for_search_in_order(
        &mut self,
        search: impl TryIntoSearch,
        mode: SortMode,
    ) -> Result<Vec<Card>> {
        let guard = self.search_cards_into_table(search, mode)?;
        guard.col.storage.all_searched_cards_in_search_order()
    }

    pub(crate) fn all_cards_for_ids(
        &self,
        cards: &[CardId],
        preserve_order: bool,
    ) -> Result<Vec<Card>> {
        self.storage.with_searched_cards_table(preserve_order, || {
            self.storage.set_search_table_to_card_ids(cards)?;
            if preserve_order {
                self.storage.all_searched_cards_in_search_order()
            } else {
                self.storage.all_searched_cards()
            }
        })
    }

    pub(crate) fn for_each_card_in_search(
        &mut self,
        search: impl TryIntoSearch,
        mut func: impl FnMut(&Collection, Card) -> Result<()>,
    ) -> Result<()> {
        let guard = self.search_cards_into_table(search, SortMode::NoOrder)?;
        guard
            .col
            .storage
            .for_each_card_in_search(|card| func(guard.col, card))
    }

    /// Place the matched card ids into a temporary 'search_nids' table
    /// instead of returning them. Returns a guard with a collection reference
    /// and the number of added notes. When the guard is dropped, the temporary
    /// table is cleaned up.
    pub(crate) fn search_notes_into_table(
        &mut self,
        search: impl TryIntoSearch,
    ) -> Result<NoteTableGuard<'_>> {
        let top_node = search.try_into_search()?;
        let writer = SqlWriter::new(self, ReturnItemType::Notes);
        let mode = SortMode::NoOrder;

        let (sql, args) = writer.build_query(&top_node, mode.required_table())?;

        self.storage.setup_searched_notes_table()?;
        let sql = format!("insert into search_nids {sql}");

        let notes = self
            .storage
            .db
            .prepare(&sql)?
            .execute(params_from_iter(args))?;

        Ok(NoteTableGuard { notes, col: self })
    }

    /// Place the ids of cards with notes in 'search_nids' into 'search_cids'.
    /// Returns number of added cards.
    pub(crate) fn search_cards_of_notes_into_table(&mut self) -> Result<CardTableGuard<'_>> {
        self.storage.setup_searched_cards_table()?;
        let cards = self.storage.search_cards_of_notes_into_table()?;
        Ok(CardTableGuard { cards, col: self })
    }
}

fn exact_retrievability_sort_mode(item_type: ReturnItemType, mode: &SortMode) -> Option<bool> {
    match (item_type, mode) {
        (
            ReturnItemType::Cards,
            SortMode::Builtin {
                column: Column::Retrievability,
                reverse,
            },
        ) => Some(*reverse),
        _ => None,
    }
}

fn has_retrievability_property(node: &Node) -> bool {
    match node {
        Node::Not(inner) => has_retrievability_property(inner),
        Node::Group(nodes) => nodes.iter().any(has_retrievability_property),
        Node::Search(SearchNode::Property {
            kind: PropertyKind::Retrievability(_),
            ..
        }) => true,
        _ => false,
    }
}

/// Add the order clause to the sql.
fn write_order(
    sql: &mut String,
    item_type: ReturnItemType,
    column: Column,
    reverse: bool,
    timing: SchedTimingToday,
) -> Result<()> {
    let order = match item_type {
        ReturnItemType::Cards => card_order_from_sort_column(column, timing),
        ReturnItemType::Notes => note_order_from_sort_column(column),
    };
    require!(!order.is_empty(), "Can't sort {item_type:?} by {column:?}.");
    if reverse {
        sql.push_str(
            &order
                .to_ascii_lowercase()
                .replace(" desc", "")
                .replace(" asc", " desc"),
        )
    } else {
        sql.push_str(&order);
    }
    Ok(())
}

fn card_order_from_sort_column(column: Column, timing: SchedTimingToday) -> Cow<'static, str> {
    match column {
        Column::CardMod => "c.mod asc".into(),
        Column::Cards => concat!(
            "coalesce((select pos from sort_order where ntid = n.mid and ord = c.ord),",
            // need to fall back on ord 0 for cloze cards
            "(select pos from sort_order where ntid = n.mid and ord = 0)) asc, ord asc"
        )
        .into(),
        Column::Deck => "(select pos from sort_order where did = c.did) asc".into(),
        Column::Due => format!("(case when c.due > 1000000000 or c.type = {} then due else (due - {}) * 86400 + {} end) asc", CardType::New as i8, timing.days_elapsed, TimestampSecs::now().0).into(),
        Column::Ease => format!("c.type = {} asc, c.factor asc", CardType::New as i8).into(),
        Column::Interval => "c.ivl asc".into(),
        Column::Lapses => "c.lapses asc".into(),
        Column::NoteCreation => "n.id asc, c.ord asc".into(),
        Column::NoteMod => "n.mod asc, c.ord asc".into(),
        Column::Notetype => "(select pos from sort_order where ntid = n.mid) asc".into(),
        Column::OriginalPosition => "(select pos from sort_order where nid = c.nid) asc".into(),
        Column::Reps => "c.reps asc".into(),
        Column::SortField => "n.sfld collate nocase asc, c.ord asc".into(),
        Column::Tags => "n.tags asc".into(),
        Column::Answer | Column::Custom | Column::Question => "".into(),
        Column::Stability => "extract_fsrs_variable(c.data, 's') asc".into(),
        Column::Difficulty => "extract_fsrs_variable(c.data, 'd') asc".into(),
        Column::Retrievability => format!(
            "extract_fsrs_retrievability(c.data, case when c.odue !=0 then c.odue else c.due end, c.ivl, {}, {}, {}) asc",
            timing.days_elapsed,
            timing.next_day_at.0,
            timing.now.0,
        )
        .into(),
    }
}

fn note_order_from_sort_column(column: Column) -> Cow<'static, str> {
    match column {
        Column::CardMod
        | Column::Cards
        | Column::Deck
        | Column::Due
        | Column::Ease
        | Column::Interval
        | Column::Lapses
        | Column::OriginalPosition
        | Column::Reps => "(select pos from sort_order where nid = n.id) asc".into(),
        Column::NoteCreation => "n.id asc".into(),
        Column::NoteMod => "n.mod asc".into(),
        Column::Notetype => "(select pos from sort_order where ntid = n.mid) asc".into(),
        Column::SortField => "n.sfld collate nocase asc".into(),
        Column::Tags => "n.tags asc".into(),
        Column::Answer
        | Column::Custom
        | Column::Question
        | Column::Stability
        | Column::Difficulty
        | Column::Retrievability => "".into(),
    }
}

fn prepare_sort(col: &mut Collection, column: Column, item_type: ReturnItemType) -> Result<()> {
    let temp_string;
    let sql = match item_type {
        ReturnItemType::Cards => match column {
            Column::Cards => include_str!("template_order.sql"),
            Column::Deck => include_str!("deck_order.sql"),
            Column::Notetype => include_str!("notetype_order.sql"),
            Column::OriginalPosition => include_str!("note_original_position_order.sql"),
            _ => return Ok(()),
        },
        ReturnItemType::Notes => match column {
            Column::Cards => include_str!("note_cards_order.sql"),
            Column::CardMod => include_str!("card_mod_order.sql"),
            Column::Deck => include_str!("note_decks_order.sql"),
            Column::Due => {
                temp_string = format!("{} ORDER BY MIN({});", include_str!("note_due_order.sql"), format_args!("CASE WHEN due > 1000000000 OR type = {ctype} THEN due ELSE (due - {today}) * 86400 + {current_timestamp} END", ctype = CardType::New as i8, today = col.timing_today()?.days_elapsed, current_timestamp = TimestampSecs::now().0));
                &temp_string
            }
            Column::Ease => include_str!("note_ease_order.sql"),
            Column::Interval => include_str!("note_interval_order.sql"),
            Column::Lapses => include_str!("note_lapses_order.sql"),
            Column::OriginalPosition => include_str!("note_original_position_order.sql"),
            Column::Reps => include_str!("note_reps_order.sql"),
            Column::Notetype => include_str!("notetype_order.sql"),
            _ => return Ok(()),
        },
    };

    col.storage.db.execute_batch(sql)?;

    Ok(())
}

#[cfg(test)]
mod test {
    use anki_proto::search::browser_columns::Sorting;
    use fsrs::DEFAULT_PARAMETERS;
    use strum::IntoEnumIterator;

    use super::*;
    use crate::card::CardQueue;
    use crate::card::FsrsMemoryState;
    use crate::scheduler::fsrs::memory_state::fsrs_current_retrievability_for_state;

    impl SchedTimingToday {
        pub(crate) fn zero() -> Self {
            SchedTimingToday {
                now: TimestampSecs(0),
                days_elapsed: 0,
                next_day_at: TimestampSecs(0),
            }
        }
    }

    #[test]
    fn column_default_sort_order_should_match_order_by_clause() {
        let timing = SchedTimingToday::zero();
        for column in Column::iter() {
            assert_eq!(
                card_order_from_sort_column(column, timing).is_empty(),
                matches!(column.default_cards_order(), Sorting::None)
            );
            assert_eq!(
                note_order_from_sort_column(column).is_empty(),
                matches!(column.default_notes_order(), Sorting::None)
            );
        }
    }

    #[test]
    fn retrievability_search_and_sort_use_complete_fsrs7_state() -> Result<()> {
        let mut col = Collection::new();
        let config_id = col.get_deck(DeckId(1))?.unwrap().config_id().unwrap();
        let mut config = col.get_deck_config(config_id, false)?.unwrap();
        config.inner.fsrs_params_7 = DEFAULT_PARAMETERS.to_vec();
        col.add_or_update_deck_config(&mut config)?;

        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note1 = nt.new_note();
        let mut note2 = nt.new_note();
        col.add_note(&mut note1, DeckId(1))?;
        col.add_note(&mut note2, DeckId(1))?;
        let timing = col.timing_today()?;
        let mut cards = col.all_cards_for_search("")?;
        cards.sort_by_key(|card| card.id);
        let states = [
            FsrsMemoryState {
                stability: 10.0,
                stability_internal: 10.0,
                stability_fast: Some(20.0),
                difficulty: 5.0,
            },
            FsrsMemoryState {
                stability: 10.0,
                stability_internal: 10.0,
                stability_fast: Some(5.0),
                difficulty: 8.0,
            },
        ];
        let mut keyed = Vec::new();
        for (card, state) in cards.iter_mut().zip(states) {
            card.ctype = CardType::Review;
            card.queue = CardQueue::Review;
            card.interval = 20;
            card.memory_state = Some(state);
            card.last_review_time = Some(timing.now.adding_secs(-20 * 86_400));
            // These compatibility values must not influence built-in metrics.
            card.decay = Some(if keyed.is_empty() { 2.0 } else { 0.1 });
            col.storage.update_card(card)?;
            keyed.push((
                card.id,
                fsrs_current_retrievability_for_state(&DEFAULT_PARAMETERS, state, 20.0)?,
            ));
        }
        assert_ne!(keyed[0].1, keyed[1].1);
        keyed.sort_by(|a, b| a.1.total_cmp(&b.1));

        let sorted = col.search_cards(
            "",
            SortMode::Builtin {
                column: Column::Retrievability,
                reverse: false,
            },
        )?;
        assert_eq!(sorted, keyed.iter().map(|(id, _)| *id).collect::<Vec<_>>());

        let midpoint = (keyed[0].1 + keyed[1].1) / 2.0;
        let query = format!("prop:r<{midpoint:.6}");
        assert_eq!(
            col.search_cards(&query, SortMode::NoOrder)?,
            vec![keyed[0].0]
        );
        Ok(())
    }

    #[test]
    fn exact_retrievability_table_is_cleared_after_search_errors() -> Result<()> {
        let mut col = Collection::new();
        let result: Result<()> =
            col.with_exact_retrievability_table(true, |_| invalid_input!("forced search failure"));
        assert!(result.is_err());
        let count: u32 = col.storage.db.query_row(
            "select count(*) from sqlite_temp_master where name = ?",
            [EXACT_RETRIEVABILITY_TABLE],
            |row| row.get(0),
        )?;
        assert_eq!(count, 0);
        Ok(())
    }
}
