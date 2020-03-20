// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{parser::Node, sqlwriter::node_to_sql};
use crate::card::CardType;
use crate::collection::RequestContext;
use crate::config::SortKind;
use crate::err::Result;
use crate::search::parser::parse;
use crate::types::ObjID;

pub(crate) fn search_cards<'a, 'b>(
    req: &'a mut RequestContext<'b>,
    search: &'a str,
) -> Result<Vec<ObjID>> {
    let top_node = Node::Group(parse(search)?);
    let (sql, args) = node_to_sql(req, &top_node)?;
    let mut sql = format!(
        "select c.id from cards c, notes n where c.nid=n.id and {} order by ",
        sql
    );
    write_order(req, &mut sql)?;

    let mut stmt = req.storage.db.prepare(&sql)?;
    let ids: Vec<i64> = stmt
        .query_map(&args, |row| row.get(0))?
        .collect::<std::result::Result<_, _>>()?;

    println!("sql {}\nargs {:?} count {}", sql, args, ids.len());
    Ok(ids)
}

fn write_order(req: &mut RequestContext, sql: &mut String) -> Result<()> {
    let conf = req.storage.all_config()?;
    let tmp_str;
    sql.push_str(match conf.browser_sort_kind {
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
    });
    if conf.browser_sort_reverse {
        sql.push_str(" desc");
    }
    Ok(())
}
