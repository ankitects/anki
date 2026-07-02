// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Updates to notes/cards when the structure of a notetype is changed.

use std::collections::HashMap;
use std::mem;

use super::CardGenContext;
use super::CardTemplate;
use super::Notetype;
use crate::notes::UpdateNoteInnerWithoutCardsArgs;
use crate::prelude::*;
use crate::search::JoinSearches;
use crate::search::TemplateKind;

/// True if any ordinals added, removed or reordered.
fn ords_changed(ords: &[Option<u32>], previous_len: usize) -> bool {
    ords.len() != previous_len
        || ords
            .iter()
            .enumerate()
            .any(|(idx, &ord)| ord != Some(idx as u32))
}

#[derive(Default, PartialEq, Eq, Debug)]
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

    fn is_empty(&self) -> bool {
        *self == Self::default()
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
                self.set_schema_modified()?;
                let nids = self.search_notes_unordered(nt.id)?;
                for nid in nids {
                    let mut note = self.storage.get_note(nid)?.unwrap();
                    let original = note.clone();
                    self.update_note_inner_without_cards(UpdateNoteInnerWithoutCardsArgs {
                        note: &mut note,
                        original: &original,
                        notetype: nt,
                        usn,
                        mark_note_modified: true,
                        normalize_text,
                        update_tags: false,
                    })?;
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
            note.reorder_fields(&ords);
            self.update_note_inner_without_cards(UpdateNoteInnerWithoutCardsArgs {
                note: &mut note,
                original: &original,
                notetype: nt,
                usn,
                mark_note_modified: true,
                normalize_text,
                update_tags: false,
            })?;
        }
        Ok(())
    }

    /// Update cards after card templates added, removed or reordered.
    /// Does not remove cards where the template still exists but creates an
    /// empty card. Caller must create transaction.
    pub(crate) fn update_cards_for_changed_templates(
        &mut self,
        nt: &Notetype,
        old_templates: &[CardTemplate],
    ) -> Result<()> {
        let usn = self.usn()?;
        let ords: Vec<_> = nt.templates.iter().map(|f| f.ord).collect();
        let changes = TemplateOrdChanges::new(ords, old_templates.len() as u32);

        if !changes.is_empty() {
            self.set_schema_modified()?;
        }

        // remove any cards where the template was deleted
        if !changes.removed.is_empty() {
            let ords =
                SearchBuilder::any(changes.removed.iter().cloned().map(TemplateKind::Ordinal));
            for card in self.all_cards_for_search(nt.id.and(ords))? {
                self.remove_card_and_add_grave_undoable(card, usn)?;
            }
        }
        // update ordinals for cards with a repositioned template
        if !changes.moved.is_empty() {
            let ords = SearchBuilder::any(changes.moved.keys().cloned().map(TemplateKind::Ordinal));
            for mut card in self.all_cards_for_search(nt.id.and(ords))? {
                let original = card.clone();
                card.template_idx = *changes.moved.get(&card.template_idx).unwrap();
                self.update_card_inner(&mut card, original, usn)?;
            }
        }

        if should_generate_cards(&changes, nt, old_templates) {
            let last_deck = self.get_last_deck_added_to_for_notetype(nt.id);
            let ctx = CardGenContext::new(nt, last_deck, usn);
            self.generate_cards_for_notetype(&ctx)?;
        }

        Ok(())
    }
}

fn should_generate_cards(
    changes: &TemplateOrdChanges,
    nt: &Notetype,
    old_templates: &[CardTemplate],
) -> bool {
    // must regenerate if any front side has changed, but also in the (unlikely)
    // case that a template has been replaced by one with an identical front
    !(changes.added.is_empty() && nt.template_fronts_are_identical(old_templates))
}

impl Notetype {
    fn template_fronts_are_identical(&self, other_templates: &[CardTemplate]) -> bool {
        self.templates
            .iter()
            .map(|t| &t.config.q_format)
            .eq(other_templates.iter().map(|t| &t.config.q_format))
    }
}

impl Note {
    pub(crate) fn reorder_fields(&mut self, new_ords: &[Option<u32>]) {
        *self.fields_mut() = new_ords
            .iter()
            .map(|ord| {
                ord.and_then(|idx| self.fields_mut().get_mut(idx as usize))
                    .map(mem::take)
                    .unwrap_or_default()
            })
            .collect();
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::search::SortMode;

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
        let mut col = Collection::new();
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
        let mut col = Collection::new();
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
        let mut col = Collection::new();
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
