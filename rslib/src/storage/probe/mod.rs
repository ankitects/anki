// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use rusqlite::params;
use rusqlite::Row;

use super::SqliteStorage;
use crate::prelude::*;
use crate::probe::Probe;
use crate::probe::ProbeId;

fn row_to_probe(row: &Row) -> Result<Probe> {
    Ok(Probe {
        id: row.get(0)?,
        card_id: row.get(1)?,
        question: row.get(2)?,
        answer: row.get(3)?,
        citation: row.get(4)?,
        provenance: row.get(5)?,
    })
}

impl SqliteStorage {
    /// Adds the probe, if its id is unique. If it is not, and `uniquify` is
    /// true, adds it with a new id. Returns the added id.
    /// (I.e., the option is safe to unwrap, if `uniquify` is true.)
    /// A zero id is always replaced with a fresh timestamp-based one.
    pub(crate) fn add_probe(&self, probe: &Probe, uniquify: bool) -> Result<Option<ProbeId>> {
        let id = if probe.id.0 == 0 {
            ProbeId::new()
        } else {
            probe.id
        };
        let added = self
            .db
            .prepare_cached(include_str!("add.sql"))?
            .execute(params![
                uniquify,
                id,
                probe.card_id,
                probe.question,
                probe.answer,
                probe.citation,
                probe.provenance,
            ])?;
        Ok((added > 0).then(|| ProbeId(self.db.last_insert_rowid())))
    }

    pub(crate) fn remove_probe(&self, id: ProbeId) -> Result<()> {
        self.db
            .prepare_cached("delete from probes where id = ?")?
            .execute([id])?;
        Ok(())
    }

    pub(crate) fn get_probes_for_card(&self, card_id: CardId) -> Result<Vec<Probe>> {
        self.db
            .prepare_cached(concat!(include_str!("get.sql"), " where cid = ?"))?
            .query_and_then([card_id], row_to_probe)?
            .collect()
    }

    pub(crate) fn get_probes_for_searched_cards(&self) -> Result<Vec<Probe>> {
        self.db
            .prepare_cached(concat!(
                include_str!("get.sql"),
                " where cid in (select cid from search_cids)"
            ))?
            .query_and_then([], row_to_probe)?
            .collect()
    }
}
