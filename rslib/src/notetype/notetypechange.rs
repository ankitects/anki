// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Updates to notes/cards when a note is moved to a different notetype.

use std::collections::HashMap;
use std::collections::HashSet;

use super::CardGenContext;
use super::Notetype;
use super::NotetypeKind;
use crate::prelude::*;
use crate::search::JoinSearches;
use crate::search::Node;
use crate::search::SearchNode;
use crate::search::TemplateKind;
use crate::storage::comma_separated_ids;

#[derive(Debug)]
pub struct ChangeNotetypeInput {
    pub current_schema: TimestampMillis,
    pub note_ids: Vec<NoteId>,
    pub old_notetype_name: String,
    pub old_notetype_id: NotetypeId,
    pub new_notetype_id: NotetypeId,
    pub new_fields: Vec<Option<usize>>,
    pub new_templates: Option<Vec<Option<usize>>>,
}

#[derive(Debug)]
pub struct NotetypeChangeInfo {
    pub input: ChangeNotetypeInput,
    pub old_notetype_name: String,
    pub old_field_names: Vec<String>,
    pub old_template_names: Vec<String>,
    pub new_field_names: Vec<String>,
    pub new_template_names: Vec<String>,
}

#[derive(Debug, PartialEq, Eq)]
pub struct TemplateMap {
    pub removed: Vec<usize>,
    pub remapped: HashMap<usize, usize>,
}

impl TemplateMap {
    fn new(new_templates: Vec<Option<usize>>, old_template_count: usize) -> Self {
        let mut seen: HashSet<usize> = HashSet::new();
        let remapped: HashMap<_, _> = new_templates
            .iter()
            .enumerate()
            .filter_map(|(new_idx, old_idx)| {
                if let Some(old_idx) = *old_idx {
                    seen.insert(old_idx);
                    if old_idx != new_idx {
                        return Some((old_idx, new_idx));
                    }
                }

                None
            })
            .collect();

        let removed: Vec<_> = (0..old_template_count)
            .filter(|idx| !seen.contains(idx))
            .collect();

        TemplateMap { removed, remapped }
    }
}

impl Collection {
    pub fn notetype_change_info(
        &mut self,
        old_notetype_id: NotetypeId,
        new_notetype_id: NotetypeId,
    ) -> Result<NotetypeChangeInfo> {
        let old_notetype = self
            .get_notetype(old_notetype_id)?
            .or_not_found(old_notetype_id)?;
        let new_notetype = self
            .get_notetype(new_notetype_id)?
            .or_not_found(new_notetype_id)?;

        let current_schema = self.storage.get_collection_timestamps()?.schema_change;
        let old_notetype_name = &old_notetype.name;
        let new_fields = default_field_map(&old_notetype, &new_notetype);
        let new_templates = default_template_map(&old_notetype, &new_notetype);
        Ok(NotetypeChangeInfo {
            input: ChangeNotetypeInput {
                current_schema,
                note_ids: vec![],
                old_notetype_name: old_notetype_name.clone(),
                old_notetype_id,
                new_notetype_id,
                new_fields,
                new_templates,
            },
            old_notetype_name: old_notetype_name.clone(),
            old_field_names: old_notetype.fields.iter().map(|f| f.name.clone()).collect(),
            old_template_names: old_notetype
                .templates
                .iter()
                .map(|f| f.name.clone())
                .collect(),
            new_field_names: new_notetype.fields.iter().map(|f| f.name.clone()).collect(),
            new_template_names: new_notetype
                .templates
                .iter()
                .map(|f| f.name.clone())
                .collect(),
        })
    }

    pub fn change_notetype_of_notes(&mut self, input: ChangeNotetypeInput) -> Result<OpOutput<()>> {
        self.transact(Op::ChangeNotetype, |col| {
            col.change_notetype_of_notes_inner(input)
        })
    }
}

fn default_template_map(
    current_notetype: &Notetype,
    new_notetype: &Notetype,
) -> Option<Vec<Option<usize>>> {
    if current_notetype.config.kind() == NotetypeKind::Cloze
        || new_notetype.config.kind() == NotetypeKind::Cloze
    {
        // clozes can't be remapped
        None
    } else {
        // name -> (ordinal, is_used)
        let mut existing_templates: HashMap<&str, (usize, bool)> = current_notetype
            .templates
            .iter()
            .map(|template| {
                (
                    template.name.as_str(),
                    (template.ord.unwrap() as usize, false),
                )
            })
            .collect();

        // match by name
        let mut new_templates: Vec<_> = new_notetype
            .templates
            .iter()
            .map(|template| {
                existing_templates
                    .get_mut(template.name.as_str())
                    .map(|(idx, used)| {
                        *used = true;
                        *idx
                    })
            })
            .collect();

        // fill in gaps with any unused templates
        let mut remaining_templates: Vec<_> = existing_templates
            .values()
            .filter_map(|(idx, used)| if !used { Some(idx) } else { None })
            .collect();
        remaining_templates.sort_unstable();
        new_templates
            .iter_mut()
            .filter(|o| o.is_none())
            .zip(remaining_templates)
            .for_each(|(template, old_idx)| *template = Some(*old_idx));

        Some(new_templates)
    }
}

fn default_field_map(current_notetype: &Notetype, new_notetype: &Notetype) -> Vec<Option<usize>> {
    // name -> (ordinal, is_used)
    let mut existing_fields: HashMap<&str, (usize, bool)> = current_notetype
        .fields
        .iter()
        .map(|field| (field.name.as_str(), (field.ord.unwrap() as usize, false)))
        .collect();

    // match by name
    let mut new_fields: Vec<_> = new_notetype
        .fields
        .iter()
        .map(|field| {
            existing_fields
                .get_mut(field.name.as_str())
                .map(|(idx, used)| {
                    *used = true;
                    *idx
                })
        })
        .collect();

    // fill in gaps with any unused fields
    let mut remaining_fields: Vec<_> = existing_fields
        .values()
        .filter_map(|(idx, used)| if !used { Some(idx) } else { None })
        .collect();
    remaining_fields.sort_unstable();
    new_fields
        .iter_mut()
        .filter(|o| o.is_none())
        .zip(remaining_fields)
        .for_each(|(field, old_idx)| *field = Some(*old_idx));

    new_fields
}

impl Collection {
    pub(crate) fn change_notetype_of_notes_inner(
        &mut self,
        input: ChangeNotetypeInput,
    ) -> Result<()> {
        require!(
            input.current_schema == self.storage.get_collection_timestamps()?.schema_change,
            "schema changed"
        );

        let usn = self.usn()?;
        self.set_schema_modified()?;
        if let Some(new_templates) = input.new_templates {
            let old_notetype = self
                .get_notetype(input.old_notetype_id)?
                .or_not_found(input.old_notetype_id)?;
            self.update_cards_for_new_notetype(
                &input.note_ids,
                old_notetype.templates.len(),
                new_templates,
                usn,
            )?;
        } else {
            self.maybe_remove_cards_with_missing_template(
                &input.note_ids,
                input.new_notetype_id,
                usn,
            )?;
        }
        self.update_notes_for_new_notetype_and_generate_cards(
            &input.note_ids,
            &input.new_fields,
            input.new_notetype_id,
            usn,
        )?;

        Ok(())
    }

    /// Rewrite notes to match new notetype, and assigns new notetype id.
    ///
    /// `new_fields` should be the length of the new notetype's fields, and is a
    /// list of the previous field index each field should be mapped to. If
    /// None, the field is left empty.
    fn update_notes_for_new_notetype_and_generate_cards(
        &mut self,
        note_ids: &[NoteId],
        new_fields: &[Option<usize>],
        new_notetype_id: NotetypeId,
        usn: Usn,
    ) -> Result<()> {
        let notetype = self
            .get_notetype(new_notetype_id)?
            .or_not_found(new_notetype_id)?;
        let last_deck = self.get_last_deck_added_to_for_notetype(notetype.id);
        let ctx = CardGenContext::new(notetype.as_ref(), last_deck, usn);

        for nid in note_ids {
            let mut note = self.storage.get_note(*nid)?.or_not_found(nid)?;
            let original = note.clone();
            remap_fields(note.fields_mut(), new_fields);
            note.notetype_id = new_notetype_id;
            self.update_note_inner_generating_cards(
                &ctx, &mut note, &original, true, false, false,
            )?;
        }

        Ok(())
    }

    fn update_cards_for_new_notetype(
        &mut self,
        note_ids: &[NoteId],
        old_template_count: usize,
        new_templates: Vec<Option<usize>>,
        usn: Usn,
    ) -> Result<()> {
        let nids: Node = SearchNode::NoteIds(comma_separated_ids(note_ids)).into();
        let map = TemplateMap::new(new_templates, old_template_count);
        self.remove_unmapped_cards(&map, nids.clone(), usn)?;
        self.rewrite_remapped_cards(&map, nids, usn)?;

        Ok(())
    }

    fn remove_unmapped_cards(
        &mut self,
        map: &TemplateMap,
        nids: Node,
        usn: Usn,
    ) -> Result<(), AnkiError> {
        if !map.removed.is_empty() {
            let ords =
                SearchBuilder::any(map.removed.iter().map(|o| TemplateKind::Ordinal(*o as u16)));
            for card in self.all_cards_for_search(nids.and(ords))? {
                self.remove_card_and_add_grave_undoable(card, usn)?;
            }
        }

        Ok(())
    }

    fn rewrite_remapped_cards(
        &mut self,
        map: &TemplateMap,
        nids: Node,
        usn: Usn,
    ) -> Result<(), AnkiError> {
        if !map.remapped.is_empty() {
            let ords = SearchBuilder::any(
                map.remapped
                    .keys()
                    .map(|o| TemplateKind::Ordinal(*o as u16)),
            );
            for mut card in self.all_cards_for_search(nids.and(ords))? {
                let original = card.clone();
                card.template_idx =
                    *map.remapped.get(&(card.template_idx as usize)).unwrap() as u16;
                self.update_card_inner(&mut card, original, usn)?;
            }
        }

        Ok(())
    }

    /// If provided notetype is a normal notetype, remove any card ordinals that
    /// don't have a template associated with them. While recent Anki versions
    /// should be able to handle this case, it can cause crashes on older
    /// clients.
    fn maybe_remove_cards_with_missing_template(
        &mut self,
        note_ids: &[NoteId],
        notetype_id: NotetypeId,
        usn: Usn,
    ) -> Result<()> {
        let notetype = self.get_notetype(notetype_id)?.or_not_found(notetype_id)?;

        if notetype.config.kind() == NotetypeKind::Normal {
            // cloze -> normal change requires clean up
            for card in self
                .storage
                .all_cards_of_notes_above_ordinal(note_ids, notetype.templates.len() - 1)?
            {
                self.remove_card_and_add_grave_undoable(card, usn)?;
            }
        }

        Ok(())
    }
}

/// Rewrite the field list from a note to match a new notetype's fields.
fn remap_fields(fields: &mut Vec<String>, new_fields: &[Option<usize>]) {
    *fields = new_fields
        .iter()
        .map(|field| {
            if let Some(idx) = *field {
                // clone required as same field can be mapped multiple times
                fields.get(idx).map(ToString::to_string).unwrap_or_default()
            } else {
                String::new()
            }
        })
        .collect();
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::error::Result;

    #[test]
    fn field_map() -> Result<()> {
        let mut col = Collection::new();
        let mut basic = col
            .storage
            .get_notetype(col.get_current_notetype_id().unwrap())?
            .unwrap();

        // no matching field names; fields are assigned in order
        let cloze = col.get_notetype_by_name("Cloze")?.unwrap().as_ref().clone();
        assert_eq!(&default_field_map(&basic, &cloze), &[Some(0), Some(1)]);

        basic.add_field("idx2");
        basic.add_field("idx3");
        basic.add_field("Text"); // 4
        basic.add_field("idx5");
        // re-fetch to get ordinals
        col.update_notetype(&mut basic, false)?;
        let basic = col.get_notetype(basic.id)?.unwrap();

        // if names match, assignments are out of order; unmatched entries
        // are filled sequentially
        assert_eq!(&default_field_map(&basic, &cloze), &[Some(4), Some(0)]);

        // unmatched entries are filled sequentially until exhausted
        assert_eq!(
            &default_field_map(&cloze, &basic),
            &[
                // front
                Some(1),
                // back
                None,
                // idx2
                None,
                // idx3
                None,
                // text
                Some(0),
                // idx5
                None,
            ]
        );

        Ok(())
    }

    #[test]
    fn template_map() {
        let new_templates = vec![None, Some(0)];

        assert_eq!(
            TemplateMap::new(new_templates.clone(), 1),
            TemplateMap {
                removed: vec![],
                remapped: vec![(0, 1)].into_iter().collect()
            }
        );

        assert_eq!(
            TemplateMap::new(new_templates, 2),
            TemplateMap {
                removed: vec![1],
                remapped: vec![(0, 1)].into_iter().collect()
            }
        );
    }

    #[test]
    fn basic() -> Result<()> {
        let mut col = Collection::new();
        let basic = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = basic.new_note();
        note.set_field(0, "1")?;
        note.set_field(1, "2")?;
        col.add_note(&mut note, DeckId(1))?;

        let basic2 = col
            .get_notetype_by_name("Basic (and reversed card)")?
            .unwrap();

        let first_card = col.storage.all_cards_of_note(note.id)?[0].clone();
        assert_eq!(first_card.template_idx, 0);

        // switch the existing card to ordinal 2
        let input = ChangeNotetypeInput {
            note_ids: vec![note.id],
            new_templates: Some(vec![None, Some(0)]),
            ..col.notetype_change_info(basic.id, basic2.id)?.input
        };
        col.change_notetype_of_notes(input)?;

        // cards arrive in creation order, so the existing card will come first
        let cards = col.storage.all_cards_of_note(note.id)?;
        assert_eq!(cards[0].id, first_card.id);
        assert_eq!(cards[0].template_idx, 1);

        // a new forward card should also have been generated
        assert_eq!(cards[1].template_idx, 0);
        assert_ne!(cards[1].id, first_card.id);

        Ok(())
    }

    #[test]
    fn field_count_change() -> Result<()> {
        let mut col = Collection::new();
        let basic = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = basic.new_note();
        note.set_field(0, "1")?;
        note.set_field(1, "2")?;
        col.add_note(&mut note, DeckId(1))?;

        let basic2 = col
            .get_notetype_by_name("Basic (optional reversed card)")?
            .unwrap();
        let input = ChangeNotetypeInput {
            note_ids: vec![note.id],
            ..col.notetype_change_info(basic.id, basic2.id)?.input
        };
        col.change_notetype_of_notes(input)?;

        Ok(())
    }

    #[test]
    fn cloze() -> Result<()> {
        let mut col = Collection::new();
        let basic = col
            .get_notetype_by_name("Basic (and reversed card)")?
            .unwrap();
        let mut note = basic.new_note();
        note.set_field(0, "1")?;
        note.set_field(1, "2")?;
        col.add_note(&mut note, DeckId(1))?;

        let cloze = col.get_notetype_by_name("Cloze")?.unwrap();

        // changing to cloze should leave all the existing cards alone
        let input = ChangeNotetypeInput {
            note_ids: vec![note.id],
            ..col.notetype_change_info(basic.id, cloze.id)?.input
        };
        col.change_notetype_of_notes(input)?;
        let cards = col.storage.all_cards_of_note(note.id)?;
        assert_eq!(cards.len(), 2);

        // and back again should also work
        let input = ChangeNotetypeInput {
            note_ids: vec![note.id],
            ..col.notetype_change_info(cloze.id, basic.id)?.input
        };
        col.change_notetype_of_notes(input)?;
        let cards = col.storage.all_cards_of_note(note.id)?;
        assert_eq!(cards.len(), 2);

        // but any cards above the available templates should be removed when converting
        // from cloze->normal
        let input = ChangeNotetypeInput {
            note_ids: vec![note.id],
            ..col.notetype_change_info(basic.id, cloze.id)?.input
        };
        col.change_notetype_of_notes(input)?;

        let basic1 = col.get_notetype_by_name("Basic")?.unwrap();
        let input = ChangeNotetypeInput {
            note_ids: vec![note.id],
            ..col.notetype_change_info(cloze.id, basic1.id)?.input
        };
        col.change_notetype_of_notes(input)?;
        let cards = col.storage.all_cards_of_note(note.id)?;
        assert_eq!(cards.len(), 1);

        Ok(())
    }
}
