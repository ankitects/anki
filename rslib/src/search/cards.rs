// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{parser::Node, sqlwriter::node_to_sql};
use crate::card::CardID;
use crate::card::CardType;
use crate::collection::Collection;
use crate::config::SortKind;
use crate::err::Result;
use crate::search::parser::parse;
use rusqlite::params;

pub(crate) enum SortMode {
    NoOrder,
    FromConfig,
    Builtin { kind: SortKind, reverse: bool },
    Custom(String),
}

pub(crate) fn search_cards<'a, 'b>(
    req: &'b mut Collection,
    search: &'a str,
    order: SortMode,
) -> Result<Vec<CardID>> {
    let top_node = Node::Group(parse(search)?);
    let (sql, args) = node_to_sql(req, &top_node)?;

    let mut sql = format!(
        "select c.id from cards c, notes n where c.nid=n.id and {}",
        sql
    );

    match order {
        SortMode::NoOrder => (),
        SortMode::FromConfig => {
            let kind = req.get_browser_sort_kind();
            prepare_sort(req, &kind)?;
            sql.push_str(" order by ");
            write_order(&mut sql, &kind, req.get_browser_sort_reverse())?;
        }
        SortMode::Builtin { kind, reverse } => {
            prepare_sort(req, &kind)?;
            sql.push_str(" order by ");
            write_order(&mut sql, &kind, reverse)?;
        }
        SortMode::Custom(order_clause) => {
            sql.push_str(" order by ");
            sql.push_str(&order_clause);
        }
    }

    let mut stmt = req.storage.db.prepare(&sql)?;
    let ids: Vec<_> = stmt
        .query_map(&args, |row| row.get(0))?
        .collect::<std::result::Result<_, _>>()?;

    Ok(ids)
}

/// Add the order clause to the sql.
fn write_order(sql: &mut String, kind: &SortKind, reverse: bool) -> Result<()> {
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
        SortKind::CardDeck => "(select v from sort_order where k = c.did) asc",
        SortKind::NoteType => "(select v from sort_order where k = n.mid) asc",
        SortKind::CardTemplate => "(select v from sort_order where k1 = n.mid and k2 = c.ord) asc",
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

// In the future these items should be moved from JSON into separate SQL tables,
// - for now we use a temporary deck to sort them.
fn prepare_sort(req: &mut Collection, kind: &SortKind) -> Result<()> {
    use SortKind::*;
    match kind {
        CardDeck | NoteType => {
            prepare_sort_order_table(req)?;
            let mut stmt = req
                .storage
                .db
                .prepare("insert into sort_order (k,v) values (?,?)")?;

            match kind {
                CardDeck => {
                    for (k, v) in req.storage.get_all_decks()? {
                        stmt.execute(params![k, v.name()])?;
                    }
                }
                NoteType => {
                    for (k, v) in req.storage.get_all_notetypes()? {
                        stmt.execute(params![k, v.name])?;
                    }
                }
                _ => unreachable!(),
            }
        }
        CardTemplate => {
            prepare_sort_order_table2(req)?;
            let mut stmt = req
                .storage
                .db
                .prepare("insert into sort_order (k1,k2,v) values (?,?,?)")?;

            for (ntid, nt) in req.storage.get_all_notetypes()? {
                for tmpl in nt.templates {
                    stmt.execute(params![ntid, tmpl.ord, tmpl.name])?;
                }
            }
        }
        _ => (),
    }

    Ok(())
}

fn prepare_sort_order_table(req: &mut Collection) -> Result<()> {
    req.storage
        .db
        .execute_batch(include_str!("sort_order.sql"))?;
    Ok(())
}

fn prepare_sort_order_table2(req: &mut Collection) -> Result<()> {
    req.storage
        .db
        .execute_batch(include_str!("sort_order2.sql"))?;
    Ok(())
}
