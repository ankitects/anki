// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{parser::Node, sqlwriter::node_to_sql};
use crate::collection::RequestContext;
use crate::err::Result;
use crate::search::parser::parse;
use crate::types::ObjID;

pub(crate) fn search_notes<'a, 'b>(
    req: &'a mut RequestContext<'b>,
    search: &'a str,
) -> Result<Vec<ObjID>> {
    let top_node = Node::Group(parse(search)?);
    let (sql, args) = node_to_sql(req, &top_node)?;

    let sql = format!(
        "select n.id from cards c, notes n where c.nid=n.id and {}",
        sql
    );

    let mut stmt = req.storage.db.prepare(&sql)?;
    let ids: Vec<i64> = stmt
        .query_map(&args, |row| row.get(0))?
        .collect::<std::result::Result<_, _>>()?;

    Ok(ids)
}
