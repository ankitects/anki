// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{parser::Node, sqlwriter::SqlWriter};
use crate::collection::Collection;
use crate::err::Result;
use crate::notes::NoteID;
use crate::search::parser::parse;

impl Collection {
    pub fn search_notes(&mut self, search: &str) -> Result<Vec<NoteID>> {
        let top_node = Node::Group(parse(search)?);
        let writer = SqlWriter::new(self);
        let (sql, args) = writer.build_notes_query(&top_node)?;

        let mut stmt = self.storage.db.prepare(&sql)?;
        let ids: Vec<_> = stmt
            .query_map(&args, |row| row.get(0))?
            .collect::<std::result::Result<_, _>>()?;

        Ok(ids)
    }
}
