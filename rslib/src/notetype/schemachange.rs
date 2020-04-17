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

        let nids = self.search_notes_only(&format!("mid:{}", nt.id))?;
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
            note.prepare_for_update(nt, usn)?;
            self.storage.update_note(&note)?;
        }
        Ok(())
    }
}

#[cfg(test)]
mod test {
    use crate::collection::open_test_collection;
    use crate::err::Result;

    #[test]
    fn fields() -> Result<()> {
        let mut col = open_test_collection();
        let mut nt = col
            .storage
            .get_notetype(col.get_current_notetype_id().unwrap())?
            .unwrap();
        let mut note = nt.new_note();
        assert_eq!(note.fields.len(), 2);
        note.fields = vec!["one".into(), "two".into()];
        col.add_note(&mut note)?;

        nt.add_field("three");
        col.update_notetype(&mut nt)?;

        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(
            note.fields,
            vec!["one".to_string(), "two".into(), "".into()]
        );

        nt.fields.remove(1);
        col.update_notetype(&mut nt)?;

        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.fields, vec!["one".to_string(), "".into()]);

        Ok(())
    }
}
