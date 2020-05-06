// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{parser::Node, sqlwriter::node_to_sql};
use crate::collection::Collection;
use crate::err::Result;
use crate::notes::NoteID;
use crate::search::parser::parse;

impl Collection {
    /// This supports card queries as well, but is slower.
    pub fn search_notes(&mut self, search: &str) -> Result<Vec<NoteID>> {
        self.search_notes_inner(search, |sql| {
            format!(
                "select distinct n.id from cards c, notes n where c.nid=n.id and {}",
                sql
            )
        })
    }

    /// This only supports note-related search terms.
    pub fn search_notes_only(&mut self, search: &str) -> Result<Vec<NoteID>> {
        self.search_notes_inner(search, |sql| {
            format!("select n.id from notes n where {}", sql)
        })
    }

    fn search_notes_inner<F>(&mut self, search: &str, build_sql: F) -> Result<Vec<NoteID>>
    where
        F: FnOnce(String) -> String,
    {
        let top_node = Node::Group(parse(search)?);
        let (sql, args) = node_to_sql(self, &top_node, self.normalize_note_text())?;

        let sql = build_sql(sql);

        let mut stmt = self.storage.db.prepare(&sql)?;
        let ids: Vec<_> = stmt
            .query_map(&args, |row| row.get(0))?
            .collect::<std::result::Result<_, _>>()?;

        Ok(ids)
    }
}
