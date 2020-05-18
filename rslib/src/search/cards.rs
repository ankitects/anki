// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{parser::Node, sqlwriter::node_to_sql};
use crate::card::CardID;
use crate::card::CardType;
use crate::collection::Collection;
use crate::config::SortKind;
use crate::err::Result;
use crate::search::parser::parse;

pub enum SortMode {
    NoOrder,
    FromConfig,
    Builtin { kind: SortKind, reverse: bool },
    Custom(String),
}

impl Collection {
    pub fn search_cards(&mut self, search: &str, mode: SortMode) -> Result<Vec<CardID>> {
        let top_node = Node::Group(parse(search)?);
        let (sql, args) = node_to_sql(self, &top_node, self.normalize_note_text())?;

        let mut sql = format!(
            "select c.id from cards c, notes n where c.nid=n.id and {}",
            sql
        );

        match mode {
            SortMode::NoOrder => (),
            SortMode::FromConfig => {
                let kind = self.get_browser_sort_kind();
                prepare_sort(self, kind)?;
                sql.push_str(" order by ");
                write_order(&mut sql, kind, self.get_browser_sort_reverse())?;
            }
            SortMode::Builtin { kind, reverse } => {
                prepare_sort(self, kind)?;
                sql.push_str(" order by ");
                write_order(&mut sql, kind, reverse)?;
            }
            SortMode::Custom(order_clause) => {
                sql.push_str(" order by ");
                sql.push_str(&order_clause);
            }
        }

        let mut stmt = self.storage.db.prepare(&sql)?;
        let ids: Vec<_> = stmt
            .query_map(&args, |row| row.get(0))?
            .collect::<std::result::Result<_, _>>()?;

        Ok(ids)
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
    match kind {
        CardDeck | NoteType | CardTemplate => true,
        _ => false,
    }
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
