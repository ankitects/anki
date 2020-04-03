// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{parser::Node, sqlwriter::node_to_sql};
use crate::collection::Collection;
use crate::err::Result;
use crate::notes::NoteID;
use crate::search::parser::parse;

pub(crate) fn search_notes<'a>(req: &'a mut Collection, search: &'a str) -> Result<Vec<NoteID>> {
    let top_node = Node::Group(parse(search)?);
    let (sql, args) = node_to_sql(req, &top_node)?;

    let sql = format!(
        "select n.id from cards c, notes n where c.nid=n.id and {}",
        sql
    );

    let mut stmt = req.storage.db.prepare(&sql)?;
    let ids: Vec<_> = stmt
        .query_map(&args, |row| row.get(0))?
        .collect::<std::result::Result<_, _>>()?;

    Ok(ids)
}
