// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod parser;
mod sqlwriter;
mod writer;

use crate::{
    card::CardID, card::CardType, collection::Collection, config::SortKind, err::Result,
    notes::NoteID, search::parser::parse,
};
pub use parser::{Node, PropertyKind, RatingKind, SearchNode, StateKind, TemplateKind};
use rusqlite::types::FromSql;
use sqlwriter::{RequiredTable, SqlWriter};
use std::borrow::Cow;
pub use writer::{
    concatenate_searches, negate_search, normalize_search, replace_search_term, write_nodes,
    BoolSeparator,
};

#[derive(Debug, PartialEq, Clone, Copy)]
pub enum SearchItems {
    Cards,
    Notes,
}

#[derive(Debug, PartialEq, Clone)]
pub enum SortMode {
    NoOrder,
    FromConfig,
    Builtin { kind: SortKind, reverse: bool },
    Custom(String),
}

pub trait AsSearchItems {
    fn as_search_items() -> SearchItems;
}

impl AsSearchItems for CardID {
    fn as_search_items() -> SearchItems {
        SearchItems::Cards
    }
}

impl AsSearchItems for NoteID {
    fn as_search_items() -> SearchItems {
        SearchItems::Notes
    }
}

impl SortMode {
    fn required_table(&self) -> RequiredTable {
        match self {
            SortMode::NoOrder => RequiredTable::Cards,
            SortMode::FromConfig => unreachable!(),
            SortMode::Builtin { kind, .. } => kind.required_table(),
            SortMode::Custom(ref text) => {
                if text.contains("n.") {
                    RequiredTable::CardsAndNotes
                } else {
                    RequiredTable::Cards
                }
            }
        }
    }
}

impl SortKind {
    fn required_table(self) -> RequiredTable {
        match self {
            SortKind::NoteCreation
            | SortKind::NoteMod
            | SortKind::NoteField
            | SortKind::NoteType
            | SortKind::NoteTags => RequiredTable::Notes,
            SortKind::CardTemplate => RequiredTable::CardsAndNotes,
            SortKind::CardMod
            | SortKind::CardReps
            | SortKind::CardDue
            | SortKind::CardEase
            | SortKind::CardLapses
            | SortKind::CardInterval
            | SortKind::CardDeck => RequiredTable::Cards,
        }
    }
}

impl Collection {
    pub fn search<T>(&mut self, search: &str, mut mode: SortMode) -> Result<Vec<T>>
    where
        T: FromSql + AsSearchItems,
    {
        let items = T::as_search_items();
        let top_node = Node::Group(parse(search)?);
        self.resolve_config_sort(items, &mut mode);
        let writer = SqlWriter::new(self, items);

        let (mut sql, args) = writer.build_query(&top_node, mode.required_table())?;
        self.add_order(&mut sql, items, mode)?;

        let mut stmt = self.storage.db.prepare(&sql)?;
        let ids: Vec<_> = stmt
            .query_map(&args, |row| row.get(0))?
            .collect::<std::result::Result<_, _>>()?;

        Ok(ids)
    }

    pub fn search_cards(&mut self, search: &str, mode: SortMode) -> Result<Vec<CardID>> {
        self.search(search, mode)
    }

    pub fn search_notes(&mut self, search: &str) -> Result<Vec<NoteID>> {
        self.search(search, SortMode::NoOrder)
    }

    fn add_order(&mut self, sql: &mut String, items: SearchItems, mode: SortMode) -> Result<()> {
        match mode {
            SortMode::NoOrder => (),
            SortMode::FromConfig => unreachable!(),
            SortMode::Builtin { kind, reverse } => {
                prepare_sort(self, kind)?;
                sql.push_str(" order by ");
                write_order(sql, items, kind, reverse)?;
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
    pub(crate) fn search_cards_into_table(&mut self, search: &str, mode: SortMode) -> Result<()> {
        let top_node = Node::Group(parse(search)?);
        let writer = SqlWriter::new(self, SearchItems::Cards);
        let want_order = mode != SortMode::NoOrder;

        let (mut sql, args) = writer.build_query(&top_node, mode.required_table())?;
        self.add_order(&mut sql, SearchItems::Cards, mode)?;

        if want_order {
            self.storage
                .setup_searched_cards_table_to_preserve_order()?;
        } else {
            self.storage.setup_searched_cards_table()?;
        }
        let sql = format!("insert into search_cids {}", sql);

        self.storage.db.prepare(&sql)?.execute(&args)?;

        Ok(())
    }

    /// If the sort mode is based on a config setting, look it up.
    fn resolve_config_sort(&self, items: SearchItems, mode: &mut SortMode) {
        if mode == &SortMode::FromConfig {
            *mode = match items {
                SearchItems::Cards => SortMode::Builtin {
                    kind: self.get_browser_sort_kind(),
                    reverse: self.get_browser_sort_reverse(),
                },
                SearchItems::Notes => SortMode::Builtin {
                    kind: self.get_browser_note_sort_kind(),
                    reverse: self.get_browser_note_sort_reverse(),
                },
            }
        }
    }
}

/// Add the order clause to the sql.
fn write_order(sql: &mut String, items: SearchItems, kind: SortKind, reverse: bool) -> Result<()> {
    let order = match items {
        SearchItems::Cards => card_order_from_sortkind(kind),
        SearchItems::Notes => note_order_from_sortkind(kind),
    };
    if order.is_empty() {
        return Ok(());
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

fn card_order_from_sortkind(kind: SortKind) -> Cow<'static, str> {
    match kind {
        SortKind::NoteCreation => "n.id asc, c.ord asc".into(),
        SortKind::NoteMod => "n.mod asc, c.ord asc".into(),
        SortKind::NoteField => "n.sfld collate nocase asc, c.ord asc".into(),
        SortKind::CardMod => "c.mod asc".into(),
        SortKind::CardReps => "c.reps asc".into(),
        SortKind::CardDue => "c.type asc, c.due asc".into(),
        SortKind::CardEase => format!("c.type = {} asc, c.factor asc", CardType::New as i8).into(),
        SortKind::CardLapses => "c.lapses asc".into(),
        SortKind::CardInterval => "c.ivl asc".into(),
        SortKind::NoteTags => "n.tags asc".into(),
        SortKind::CardDeck => "(select pos from sort_order where did = c.did) asc".into(),
        SortKind::NoteType => "(select pos from sort_order where ntid = n.mid) asc".into(),
        SortKind::CardTemplate => concat!(
            "coalesce((select pos from sort_order where ntid = n.mid and ord = c.ord),",
            // need to fall back on ord 0 for cloze cards
            "(select pos from sort_order where ntid = n.mid and ord = 0)) asc"
        )
        .into(),
    }
}

fn note_order_from_sortkind(kind: SortKind) -> Cow<'static, str> {
    match kind {
        SortKind::NoteCreation => "n.id asc".into(),
        SortKind::NoteMod => "n.mod asc".into(),
        SortKind::NoteField => "n.sfld collate nocase asc".into(),
        // fixme: pick fixed template
        SortKind::CardMod => "c.mod asc".into(),
        SortKind::CardReps => "c.reps asc".into(),
        SortKind::CardDue => "c.type asc, c.due asc".into(),
        SortKind::CardEase => format!("c.type = {} asc, c.factor asc", CardType::New as i8).into(),
        SortKind::CardLapses => "c.lapses asc".into(),
        SortKind::CardInterval => "c.ivl asc".into(),
        SortKind::NoteTags => "n.tags asc".into(),
        SortKind::CardDeck => "(select pos from sort_order where did = c.did) asc".into(),
        SortKind::NoteType | SortKind::CardTemplate => {
            "(select pos from sort_order where ntid = n.mid) asc".into()
        }
    }
}

fn needs_aux_sort_table(kind: SortKind) -> bool {
    use SortKind::*;
    matches!(kind, CardDeck | NoteType | CardTemplate)
}

fn prepare_sort(col: &mut Collection, kind: SortKind) -> Result<()> {
    if !needs_aux_sort_table(kind) {
        return Ok(());
    }

    use SortKind::*;
    let sql = match kind {
        CardDeck => include_str!("deck_order.sql"),
        NoteType => include_str!("notetype_order.sql"),
        CardTemplate => include_str!("template_order.sql"),
        _ => unreachable!(),
    };

    col.storage.db.execute_batch(sql)?;

    Ok(())
}
