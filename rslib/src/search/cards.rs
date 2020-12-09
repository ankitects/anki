// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{
    parser::Node,
    sqlwriter::{RequiredTable, SqlWriter},
};
use crate::{
    card::CardID, card::CardType, collection::Collection, config::SortKind, err::Result,
    search::parser::parse,
};

#[derive(Debug, PartialEq, Clone)]
pub enum SortMode {
    NoOrder,
    FromConfig,
    Builtin { kind: SortKind, reverse: bool },
    Custom(String),
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
            | SortKind::NoteTags
            | SortKind::CardTemplate => RequiredTable::CardsAndNotes,
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
    pub fn search_cards(&mut self, search: &str, mut mode: SortMode) -> Result<Vec<CardID>> {
        let top_node = Node::Group(parse(search)?);
        self.resolve_config_sort(&mut mode);
        let writer = SqlWriter::new(self);

        let (mut sql, args) = writer.build_cards_query(&top_node, mode.required_table())?;
        self.add_order(&mut sql, mode)?;

        let mut stmt = self.storage.db.prepare(&sql)?;
        let ids: Vec<_> = stmt
            .query_map(&args, |row| row.get(0))?
            .collect::<std::result::Result<_, _>>()?;

        Ok(ids)
    }

    fn add_order(&mut self, sql: &mut String, mode: SortMode) -> Result<()> {
        match mode {
            SortMode::NoOrder => (),
            SortMode::FromConfig => unreachable!(),
            SortMode::Builtin { kind, reverse } => {
                prepare_sort(self, kind)?;
                sql.push_str(" order by ");
                write_order(sql, kind, reverse)?;
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
        let writer = SqlWriter::new(self);
        let want_order = mode != SortMode::NoOrder;

        let (mut sql, args) = writer.build_cards_query(&top_node, mode.required_table())?;
        self.add_order(&mut sql, mode)?;

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
    fn resolve_config_sort(&self, mode: &mut SortMode) {
        if mode == &SortMode::FromConfig {
            *mode = SortMode::Builtin {
                kind: self.get_browser_sort_kind(),
                reverse: self.get_browser_sort_reverse(),
            }
        }
    }
}

/// Add the order clause to the sql.
fn write_order(sql: &mut String, kind: SortKind, reverse: bool) -> Result<()> {
    let tmp_str;
    let order = match kind {
        SortKind::NoteCreation => "n.id asc, c.ord asc",
        SortKind::NoteMod => "n.mod asc, c.ord asc",
        SortKind::NoteField => "n.sfld collate nocase asc, c.ord asc",
        SortKind::CardMod => "c.mod asc",
        SortKind::CardReps => "c.reps asc",
        SortKind::CardDue => "c.type asc, c.due asc",
        SortKind::CardEase => {
            tmp_str = format!("c.type = {} asc, c.factor asc", CardType::New as i8);
            &tmp_str
        }
        SortKind::CardLapses => "c.lapses asc",
        SortKind::CardInterval => "c.ivl asc",
        SortKind::NoteTags => "n.tags asc",
        SortKind::CardDeck => "(select pos from sort_order where did = c.did) asc",
        SortKind::NoteType => "(select pos from sort_order where ntid = n.mid) asc",
        SortKind::CardTemplate => concat!(
            "coalesce((select pos from sort_order where ntid = n.mid and ord = c.ord),",
            // need to fall back on ord 0 for cloze cards
            "(select pos from sort_order where ntid = n.mid and ord = 0)) asc"
        ),
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
        sql.push_str(order);
    }
    Ok(())
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
