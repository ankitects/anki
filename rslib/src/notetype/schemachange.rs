// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{CardGenContext, NoteType};
use crate::{collection::Collection, err::Result};

/// True if any ordinals added, removed or reordered.
fn ords_changed(ords: &[Option<u32>], previous_len: usize) -> bool {
    ords.len() != previous_len
        || ords
            .iter()
            .enumerate()
            .any(|(idx, &ord)| ord != Some(idx as u32))
}

#[derive(Default, PartialEq, Debug)]
struct TemplateOrdChanges {
    added: Vec<u32>,
    removed: Vec<u32>,
    // map of old->new
    moved: Vec<(u32, u32)>,
}

impl TemplateOrdChanges {
    fn new(ords: Vec<Option<u32>>, previous_len: u32) -> Self {
        let mut changes = TemplateOrdChanges::default();
        let mut removed: Vec<_> = (0..previous_len).map(|v| Some(v as u32)).collect();
        for (idx, old_ord) in ords.into_iter().enumerate() {
            if let Some(old_ord) = old_ord {
                if let Some(entry) = removed.get_mut(old_ord as usize) {
                    // guard required to ensure we don't panic if invalid high ordinal received
                    *entry = None;
                }
                if old_ord == idx as u32 {
                    // no action
                } else {
                    changes.moved.push((old_ord as u32, idx as u32));
                }
            } else {
                changes.added.push(idx as u32);
            }
        }

        changes.removed = removed.into_iter().filter_map(|v| v).collect();

        changes
    }
}

impl Collection {
    /// Rewrite notes to match the updated field schema.
    /// Caller must create transaction.
    pub(crate) fn update_notes_for_changed_fields(
        &mut self,
        nt: &NoteType,
        previous_field_count: usize,
        previous_sort_idx: u32,
        normalize_text: bool,
    ) -> Result<()> {
        let ords: Vec<_> = nt.fields.iter().map(|f| f.ord).collect();
        if !ords_changed(&ords, previous_field_count) {
            if nt.config.sort_field_idx != previous_sort_idx {
                // only need to update sort field
                let nids = self.search_notes(&format!("mid:{}", nt.id))?;
                for nid in nids {
                    let mut note = self.storage.get_note(nid)?.unwrap();
                    note.prepare_for_update(nt, normalize_text)?;
                    self.storage.update_note(&note)?;
                }
            } else {
                // nothing to do
                return Ok(());
            }
        }

        self.storage.set_schema_modified()?;

        let nids = self.search_notes(&format!("mid:{}", nt.id))?;
        let usn = self.usn()?;
        for nid in nids {
            let mut note = self.storage.get_note(nid)?.unwrap();
            note.fields = ords
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
            note.prepare_for_update(nt, normalize_text)?;
            note.set_modified(usn);
            self.storage.update_note(&note)?;
        }
        Ok(())
    }

    /// Update cards after card templates added, removed or reordered.
    /// Does not remove cards where the template still exists but creates an empty card.
    /// Caller must create transaction.
    pub(crate) fn update_cards_for_changed_templates(
        &mut self,
        nt: &NoteType,
        previous_template_count: usize,
    ) -> Result<()> {
        let ords: Vec<_> = nt.templates.iter().map(|f| f.ord).collect();
        if !ords_changed(&ords, previous_template_count) {
            // nothing to do
            return Ok(());
        }

        self.storage.set_schema_modified()?;

        let changes = TemplateOrdChanges::new(ords, previous_template_count as u32);
        if !changes.removed.is_empty() {
            self.storage
                .remove_cards_for_deleted_templates(nt.id, &changes.removed)?;
        }
        if !changes.moved.is_empty() {
            self.storage
                .move_cards_for_repositioned_templates(nt.id, &changes.moved)?;
        }

        let ctx = CardGenContext::new(nt, self.usn()?);
        self.generate_cards_for_notetype(&ctx)?;

        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::{ords_changed, TemplateOrdChanges};
    use crate::{collection::open_test_collection, decks::DeckID, err::Result, search::SortMode};

    #[test]
    fn ord_changes() {
        assert_eq!(ords_changed(&[Some(0), Some(1)], 2), false);
        assert_eq!(ords_changed(&[Some(0), Some(1)], 1), true);
        assert_eq!(ords_changed(&[Some(1), Some(0)], 2), true);
        assert_eq!(ords_changed(&[None, Some(1)], 2), true);
        assert_eq!(ords_changed(&[Some(0), Some(1), None], 2), true);
    }

    #[test]
    fn template_changes() {
        assert_eq!(
            TemplateOrdChanges::new(vec![Some(0), Some(1)], 2),
            TemplateOrdChanges::default(),
        );
        assert_eq!(
            TemplateOrdChanges::new(vec![Some(0), Some(1)], 3),
            TemplateOrdChanges {
                removed: vec![2],
                ..Default::default()
            }
        );
        assert_eq!(
            TemplateOrdChanges::new(vec![Some(1)], 2),
            TemplateOrdChanges {
                removed: vec![0],
                moved: vec![(1, 0)],
                ..Default::default()
            }
        );
        assert_eq!(
            TemplateOrdChanges::new(vec![Some(0), None], 1),
            TemplateOrdChanges {
                added: vec![1],
                ..Default::default()
            }
        );
        assert_eq!(
            TemplateOrdChanges::new(vec![Some(2), None, Some(0)], 2),
            TemplateOrdChanges {
                added: vec![1],
                moved: vec![(2, 0), (0, 2)],
                removed: vec![1],
            }
        );
        assert_eq!(
            TemplateOrdChanges::new(vec![None, Some(2), None, Some(4)], 5),
            TemplateOrdChanges {
                added: vec![0, 2],
                moved: vec![(2, 1), (4, 3)],
                removed: vec![0, 1, 3],
            }
        );
    }

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
        col.add_note(&mut note, DeckID(1))?;

        nt.add_field("three");
        col.update_notetype(&mut nt, false)?;

        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(
            note.fields,
            vec!["one".to_string(), "two".into(), "".into()]
        );

        nt.fields.remove(1);
        col.update_notetype(&mut nt, false)?;

        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.fields, vec!["one".to_string(), "".into()]);

        Ok(())
    }

    #[test]
    fn field_renaming_and_deleting() -> Result<()> {
        let mut col = open_test_collection();
        let mut nt = col
            .storage
            .get_notetype(col.get_current_notetype_id().unwrap())?
            .unwrap();
        nt.templates[0].config.q_format += "\n{{#Front}}{{some:Front}}{{Back}}{{/Front}}";
        nt.fields[0].name = "Test".into();
        col.update_notetype(&mut nt, false)?;
        assert_eq!(
            &nt.templates[0].config.q_format,
            "{{Test}}\n{{#Test}}{{some:Test}}{{Back}}{{/Test}}"
        );
        nt.fields.remove(0);
        col.update_notetype(&mut nt, false)?;
        assert_eq!(&nt.templates[0].config.q_format, "\n{{Back}}");

        Ok(())
    }

    #[test]
    fn cards() -> Result<()> {
        let mut col = open_test_collection();
        let mut nt = col
            .storage
            .get_notetype(col.get_current_notetype_id().unwrap())?
            .unwrap();
        let mut note = nt.new_note();
        assert_eq!(note.fields.len(), 2);
        note.fields = vec!["one".into(), "two".into()];
        col.add_note(&mut note, DeckID(1))?;

        assert_eq!(
            col.search_cards(&format!("nid:{}", note.id), SortMode::NoOrder)
                .unwrap()
                .len(),
            1
        );

        // add an extra card template
        nt.add_template("card 2", "{{Front}}", "");
        col.update_notetype(&mut nt, false)?;

        assert_eq!(
            col.search_cards(&format!("nid:{}", note.id), SortMode::NoOrder)
                .unwrap()
                .len(),
            2
        );

        Ok(())
    }
}
