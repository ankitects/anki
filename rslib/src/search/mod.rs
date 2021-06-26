// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod parser;
mod sqlwriter;
pub(crate) mod writer;

use std::borrow::Cow;

pub use parser::{
    parse as parse_search, Node, PropertyKind, RatingKind, SearchNode, StateKind, TemplateKind,
};
use rusqlite::{params_from_iter, types::FromSql};
use sqlwriter::{RequiredTable, SqlWriter};
pub use writer::{concatenate_searches, replace_search_node, write_nodes, BoolSeparator};

use crate::{
    browser_table::Column,
    card::{CardId, CardType},
    collection::Collection,
    error::Result,
    notes::NoteId,
    prelude::AnkiError,
};

#[derive(Debug, PartialEq, Clone, Copy)]
pub enum ReturnItemType {
    Cards,
    Notes,
}

#[derive(Debug, PartialEq, Clone)]
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

impl Collection {
    pub fn search_cards<N>(&mut self, search: N, mode: SortMode) -> Result<Vec<CardId>>
    where
        N: TryIntoSearch,
    {
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
    fn search<T, N>(&mut self, search: N, mode: SortMode) -> Result<Vec<T>>
    where
        N: TryIntoSearch,
        T: FromSql + AsReturnItemType,
    {
        let item_type = T::as_return_item_type();
        let top_node = search.try_into_search()?;
        let writer = SqlWriter::new(self, item_type);

        let (mut sql, args) = writer.build_query(&top_node, mode.required_table())?;
        self.add_order(&mut sql, item_type, mode)?;

        let mut stmt = self.storage.db.prepare(&sql)?;
        let ids: Vec<_> = stmt
            .query_map(params_from_iter(args.iter()), |row| row.get(0))?
            .collect::<std::result::Result<_, _>>()?;

        Ok(ids)
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
                write_order(sql, item_type, column, reverse)?;
            }
            SortMode::Custom(order_clause) => {
                sql.push_str(" order by ");
                sql.push_str(&order_clause);
            }
        }
        Ok(())
    }

    /// Place the matched card ids into a temporary 'search_cids' table
    /// instead of returning them. Use clear_searched_cards() to remove it.
    /// Returns number of added cards.
    pub(crate) fn search_cards_into_table<N>(&mut self, search: N, mode: SortMode) -> Result<usize>
    where
        N: TryIntoSearch,
    {
        let top_node = search.try_into_search()?;
        let writer = SqlWriter::new(self, ReturnItemType::Cards);
        let want_order = mode != SortMode::NoOrder;

        let (mut sql, args) = writer.build_query(&top_node, mode.required_table())?;
        self.add_order(&mut sql, ReturnItemType::Cards, mode)?;

        if want_order {
            self.storage
                .setup_searched_cards_table_to_preserve_order()?;
        } else {
            self.storage.setup_searched_cards_table()?;
        }
        let sql = format!("insert into search_cids {}", sql);

        self.storage
            .db
            .prepare(&sql)?
            .execute(params_from_iter(args))
            .map_err(Into::into)
    }
}

/// Add the order clause to the sql.
fn write_order(
    sql: &mut String,
    item_type: ReturnItemType,
    column: Column,
    reverse: bool,
) -> Result<()> {
    let order = match item_type {
        ReturnItemType::Cards => card_order_from_sort_column(column),
        ReturnItemType::Notes => note_order_from_sort_column(column),
    };
    if order.is_empty() {
        return Err(AnkiError::invalid_input(format!(
            "Can't sort {:?} by {:?}.",
            item_type, column
        )));
    }
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

fn card_order_from_sort_column(column: Column) -> Cow<'static, str> {
    match column {
        Column::CardMod => "c.mod asc".into(),
        Column::Cards => concat!(
            "coalesce((select pos from sort_order where ntid = n.mid and ord = c.ord),",
            // need to fall back on ord 0 for cloze cards
            "(select pos from sort_order where ntid = n.mid and ord = 0)) asc"
        )
        .into(),
        Column::Deck => "(select pos from sort_order where did = c.did) asc".into(),
        Column::Due => "c.type asc, c.due asc".into(),
        Column::Ease => format!("c.type = {} asc, c.factor asc", CardType::New as i8).into(),
        Column::Interval => "c.ivl asc".into(),
        Column::Lapses => "c.lapses asc".into(),
        Column::NoteCreation => "n.id asc, c.ord asc".into(),
        Column::NoteMod => "n.mod asc, c.ord asc".into(),
        Column::Notetype => "(select pos from sort_order where ntid = n.mid) asc".into(),
        Column::Reps => "c.reps asc".into(),
        Column::SortField => "n.sfld collate nocase asc, c.ord asc".into(),
        Column::Tags => "n.tags asc".into(),
        Column::Answer | Column::Custom | Column::Question => "".into(),
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
        | Column::Reps => "(select pos from sort_order where nid = n.id) asc".into(),
        Column::NoteCreation => "n.id asc".into(),
        Column::NoteMod => "n.mod asc".into(),
        Column::Notetype => "(select pos from sort_order where ntid = n.mid) asc".into(),
        Column::SortField => "n.sfld collate nocase asc".into(),
        Column::Tags => "n.tags asc".into(),
        Column::Answer | Column::Custom | Column::Question => "".into(),
    }
}

fn prepare_sort(col: &mut Collection, column: Column, item_type: ReturnItemType) -> Result<()> {
    let sql = match item_type {
        ReturnItemType::Cards => match column {
            Column::Cards => include_str!("template_order.sql"),
            Column::Deck => include_str!("deck_order.sql"),
            Column::Notetype => include_str!("notetype_order.sql"),
            _ => return Ok(()),
        },
        ReturnItemType::Notes => match column {
            Column::Cards => include_str!("note_cards_order.sql"),
            Column::CardMod => include_str!("card_mod_order.sql"),
            Column::Deck => include_str!("note_decks_order.sql"),
            Column::Due => include_str!("note_due_order.sql"),
            Column::Ease => include_str!("note_ease_order.sql"),
            Column::Interval => include_str!("note_interval_order.sql"),
            Column::Lapses => include_str!("note_lapses_order.sql"),
            Column::Reps => include_str!("note_reps_order.sql"),
            Column::Notetype => include_str!("notetype_order.sql"),
            _ => return Ok(()),
        },
    };

    col.storage.db.execute_batch(sql)?;

    Ok(())
}
