// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Updates to notes/cards when the structure of a notetype is changed.

use std::collections::HashMap;

use super::{CardGenContext, Notetype};
use crate::{
    prelude::*,
    search::{Node, SortMode, TemplateKind},
};

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
    removed: Vec<u16>,
    // map of old->new
    moved: HashMap<u16, u16>,
}

impl TemplateOrdChanges {
    fn new(ords: Vec<Option<u32>>, previous_len: u32) -> Self {
        let mut changes = TemplateOrdChanges::default();
        let mut removed: Vec<_> = (0..previous_len).map(|v| Some(v as u16)).collect();
        for (idx, old_ord) in ords.into_iter().enumerate() {
            if let Some(old_ord) = old_ord {
                if let Some(entry) = removed.get_mut(old_ord as usize) {
                    // guard required to ensure we don't panic if invalid high ordinal received
                    *entry = None;
                }
                if old_ord == idx as u32 {
                    // no action
                } else {
                    changes.moved.insert(old_ord as u16, idx as u16);
                }
            } else {
                changes.added.push(idx as u32);
            }
        }

        changes.removed = removed.into_iter().flatten().collect();

        changes
    }
}

impl Collection {
    /// Rewrite notes to match the updated field schema.
    /// Caller must create transaction.
    pub(crate) fn update_notes_for_changed_fields(
        &mut self,
        nt: &Notetype,
        previous_field_count: usize,
        previous_sort_idx: u32,
        normalize_text: bool,
    ) -> Result<()> {
        let usn = self.usn()?;
        let ords: Vec<_> = nt.fields.iter().map(|f| f.ord).collect();
        if !ords_changed(&ords, previous_field_count) {
            if nt.config.sort_field_idx != previous_sort_idx {
                // only need to update sort field
                let nids = self.search_notes_unordered(nt.id)?;
                for nid in nids {
                    let mut note = self.storage.get_note(nid)?.unwrap();
                    let original = note.clone();
                    self.update_note_inner_without_cards(
                        &mut note,
                        &original,
                        nt,
                        usn,
                        true,
                        normalize_text,
                        false,
                    )?;
                }
            } else {
                // nothing to do
            }
            return Ok(());
        }

        // fields have changed
        self.set_schema_modified()?;
        let nids = self.search_notes_unordered(nt.id)?;
        let usn = self.usn()?;
        for nid in nids {
            let mut note = self.storage.get_note(nid)?.unwrap();
            let original = note.clone();
            *note.fields_mut() = ords
                .iter()
                .map(|f| {
                    if let Some(idx) = f {
                        note.fields()
                            .get(*idx as usize)
                            .map(AsRef::as_ref)
                            .unwrap_or("")
                    } else {
                        ""
                    }
                })
                .map(Into::into)
                .collect();
            self.update_note_inner_without_cards(
                &mut note,
                &original,
                nt,
                usn,
                true,
                normalize_text,
                false,
            )?;
        }
        Ok(())
    }

    /// Update cards after card templates added, removed or reordered.
    /// Does not remove cards where the template still exists but creates an empty card.
    /// Caller must create transaction.
    pub(crate) fn update_cards_for_changed_templates(
        &mut self,
        nt: &Notetype,
        previous_template_count: usize,
    ) -> Result<()> {
        let ords: Vec<_> = nt.templates.iter().map(|f| f.ord).collect();
        if !ords_changed(&ords, previous_template_count) {
            // nothing to do
            return Ok(());
        }

        self.set_schema_modified()?;
        let usn = self.usn()?;
        let changes = TemplateOrdChanges::new(ords, previous_template_count as u32);

        // remove any cards where the template was deleted
        if !changes.removed.is_empty() {
            let ords = Node::any(
                changes
                    .removed
                    .into_iter()
                    .map(TemplateKind::Ordinal)
                    .map(Into::into),
            );
            self.search_cards_into_table(match_all![nt.id, ords], SortMode::NoOrder)?;
            for card in self.storage.all_searched_cards()? {
                self.remove_card_and_add_grave_undoable(card, usn)?;
            }
            self.storage.clear_searched_cards_table()?;
        }

        // update ordinals for cards with a repositioned template
        if !changes.moved.is_empty() {
            let ords = Node::any(
                changes
                    .moved
                    .keys()
                    .cloned()
                    .map(TemplateKind::Ordinal)
                    .map(Into::into),
            );
            self.search_cards_into_table(match_all![nt.id, ords], SortMode::NoOrder)?;
            for mut card in self.storage.all_searched_cards()? {
                let original = card.clone();
                card.template_idx = *changes.moved.get(&card.template_idx).unwrap();
                self.update_card_inner(&mut card, original, usn)?;
            }
            self.storage.clear_searched_cards_table()?;
        }

        let last_deck = self.get_last_deck_added_to_for_notetype(nt.id);
        let ctx = CardGenContext::new(nt, last_deck, self.usn()?);
        self.generate_cards_for_notetype(&ctx)?;

        Ok(())
    }
}

#[cfg(test)]
mod test {
    use super::{ords_changed, TemplateOrdChanges};
    use crate::{collection::open_test_collection, decks::DeckId, error::Result, search::SortMode};

    #[test]
    fn ord_changes() {
        assert!(!ords_changed(&[Some(0), Some(1)], 2));
        assert!(ords_changed(&[Some(0), Some(1)], 1));
        assert!(ords_changed(&[Some(1), Some(0)], 2));
        assert!(ords_changed(&[None, Some(1)], 2));
        assert!(ords_changed(&[Some(0), Some(1), None], 2));
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
                moved: vec![(1, 0)].into_iter().collect(),
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
                moved: vec![(2, 0), (0, 2)].into_iter().collect(),
                removed: vec![1],
            }
        );
        assert_eq!(
            TemplateOrdChanges::new(vec![None, Some(2), None, Some(4)], 5),
            TemplateOrdChanges {
                added: vec![0, 2],
                moved: vec![(2, 1), (4, 3)].into_iter().collect(),
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
        assert_eq!(note.fields().len(), 2);
        note.set_field(0, "one")?;
        note.set_field(1, "two")?;
        col.add_note(&mut note, DeckId(1))?;

        nt.add_field("three");
        col.update_notetype(&mut nt, false)?;

        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.fields(), &["one".to_string(), "two".into(), "".into()]);

        nt.fields.remove(1);
        col.update_notetype(&mut nt, false)?;

        let note = col.storage.get_note(note.id)?.unwrap();
        assert_eq!(note.fields(), &["one".to_string(), "".into()]);

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
        assert_eq!(note.fields().len(), 2);
        note.set_field(0, "one")?;
        note.set_field(1, "two")?;
        col.add_note(&mut note, DeckId(1))?;

        assert_eq!(
            col.search_cards(note.id, SortMode::NoOrder).unwrap().len(),
            1
        );

        // add an extra card template
        nt.add_template("card 2", "{{Front}}2", "");
        col.update_notetype(&mut nt, false)?;

        assert_eq!(
            col.search_cards(note.id, SortMode::NoOrder).unwrap().len(),
            2
        );

        Ok(())
    }
}
