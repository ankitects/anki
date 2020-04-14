// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{NoteField, NoteType};
use crate::{collection::Collection, err::Result};

/// If any fields added, removed or reordered, returns a list of the new
/// field length, comprised of the original ordinals.
fn field_change_map(fields: &[NoteField], previous_field_count: usize) -> Option<Vec<Option<u32>>> {
    let map: Vec<_> = fields.iter().map(|f| f.ord).collect();
    let changed = map.len() != previous_field_count
        || map
            .iter()
            .enumerate()
            .any(|(idx, f)| f != &Some(idx as u32));
    if changed {
        Some(map)
    } else {
        None
    }
}

impl Collection {
    /// Caller must create transaction
    pub(crate) fn update_notes_for_changed_fields(
        &mut self,
        nt: &NoteType,
        previous_field_count: usize,
    ) -> Result<()> {
        let change_map = match field_change_map(&nt.fields, previous_field_count) {
            None => {
                // nothing to do
                return Ok(());
            }
            Some(map) => map,
        };

        let nids = self.search_notes(&format!("mid:{}", nt.id))?;
        let usn = self.usn()?;
        for nid in nids {
            let mut note = self.storage.get_note(nid)?.unwrap();
            note.fields = change_map
                .iter()
                .map(|f| {
                    if let Some(idx) = f {
                        note.fields
                            .get(*idx as usize)
                            .map(AsRef::as_ref)
                            .unwrap_or("")
                    } else {
                        ""
                    }
                })
                .map(Into::into)
                .collect();
            note.prepare_for_update(nt.config.sort_field_idx as usize, usn);
            self.storage.update_note(&note)?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use crate::collection::open_test_collection;

    #[test]
    fn fields() {
        let mut _col = open_test_collection();

        // fixme: need note adding before we can check this
    }
}
