// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{parser::Node, sqlwriter::node_to_sql};
use crate::card::CardType;
use crate::collection::RequestContext;
use crate::config::SortKind;
use crate::err::Result;
use crate::search::parser::parse;
use crate::types::ObjID;
use rusqlite::params;

pub(crate) enum SortMode {
    NoOrder,
    FromConfig,
    Builtin { kind: SortKind, reverse: bool },
    Custom(String),
}

pub(crate) fn search_cards<'a, 'b>(
    req: &'a mut RequestContext<'b>,
    search: &'a str,
    order: SortMode,
) -> Result<Vec<ObjID>> {
    let top_node = Node::Group(parse(search)?);
    let (sql, args) = node_to_sql(req, &top_node)?;

    let mut sql = format!(
        "select c.id from cards c, notes n where c.nid=n.id and {}",
        sql
    );

    match order {
        SortMode::NoOrder => (),
        SortMode::FromConfig => {
            let conf = req.storage.all_config()?;
            prepare_sort(req, &conf.browser_sort_kind)?;
            sql.push_str(" order by ");
            write_order(&mut sql, &conf.browser_sort_kind, conf.browser_sort_reverse)?;
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
    let ids: Vec<i64> = stmt
        .query_map(&args, |row| row.get(0))?
        .collect::<std::result::Result<_, _>>()?;

    Ok(ids)
}

/// Add the order clause to the sql.
fn write_order(sql: &mut String, kind: &SortKind, reverse: bool) -> Result<()> {
    let tmp_str;
    let order = match kind {
        SortKind::NoteCreation => "n.id, c.ord",
        SortKind::NoteMod => "n.mod, c.ord",
        SortKind::NoteField => "n.sfld collate nocase, c.ord",
        SortKind::CardMod => "c.mod",
        SortKind::CardReps => "c.reps",
        SortKind::CardDue => "c.type, c.due",
        SortKind::CardEase => {
            tmp_str = format!("c.type = {}, c.factor", CardType::New as i8);
            &tmp_str
        }
        SortKind::CardLapses => "c.lapses",
        SortKind::CardInterval => "c.ivl",
        SortKind::NoteTags => "n.tags",
        SortKind::CardDeck => "(select v from sort_order where k = c.did)",
        SortKind::NoteType => "(select v from sort_order where k = n.mid)",
        SortKind::CardTemplate => "(select v from sort_order where k1 = n.mid and k2 = c.ord)",
    };
    if order.is_empty() {
        return Ok(());
    }
    sql.push_str(order);
    if reverse {
        sql.push_str(" desc");
    }
    Ok(())
}

// In the future these items should be moved from JSON into separate SQL tables,
// - for now we use a temporary deck to sort them.
fn prepare_sort(req: &mut RequestContext, kind: &SortKind) -> Result<()> {
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
                    for (k, v) in req.storage.all_decks()? {
                        stmt.execute(params![k, v.name])?;
                    }
                }
                NoteType => {
                    for (k, v) in req.storage.all_note_types()? {
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

            for (ntid, nt) in req.storage.all_note_types()? {
                for tmpl in nt.templates {
                    stmt.execute(params![ntid, tmpl.ord, tmpl.name])?;
                }
            }
        }
        _ => (),
    }

    Ok(())
}

fn prepare_sort_order_table(req: &mut RequestContext) -> Result<()> {
    req.storage
        .db
        .execute_batch(include_str!("sort_order.sql"))?;
    Ok(())
}

fn prepare_sort_order_table2(req: &mut RequestContext) -> Result<()> {
    req.storage
        .db
        .execute_batch(include_str!("sort_order2.sql"))?;
    Ok(())
}
