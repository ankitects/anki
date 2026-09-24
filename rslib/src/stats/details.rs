// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::hash_map::Entry;
use std::collections::HashMap;
use std::collections::HashSet;

use anki_proto::stats::selected_note_fields::Field;
use anki_proto::stats::CardDetails;
use anki_proto::stats::SelectedNoteFields;

use crate::prelude::*;

impl Collection {
    /// Reuse one set of card reads for current metrics and scheduling fields.
    pub fn card_details(
        &mut self,
        card_ids: &[CardId],
        include_memory_state: bool,
        include_retrievability: bool,
        note_fields: Option<&[String]>,
    ) -> Result<Vec<CardDetails>> {
        let cards = self.stored_cards_for_ids(card_ids)?;
        let mut metrics = if include_memory_state || include_retrievability {
            self.memory_metrics_for_cards(&cards, include_retrievability)?
        } else {
            vec![]
        }
        .into_iter();
        let wanted =
            note_fields.map(|names| names.iter().map(String::as_str).collect::<HashSet<_>>());
        let mut notes = HashMap::new();
        let mut fields_by_type = HashMap::new();
        cards
            .iter()
            .map(|card| {
                let fields = if let Some(wanted) = &wanted {
                    if let Entry::Vacant(entry) = notes.entry(card.note_id) {
                        let fields =
                            self.selected_note_fields(card.note_id, wanted, &mut fields_by_type)?;
                        entry.insert(fields);
                    }
                    notes[&card.note_id].clone()
                } else {
                    None
                };
                Ok(CardDetails {
                    card_id: card.id.0,
                    ctype: card.ctype as u32,
                    queue: card.queue as i32,
                    due: card.due,
                    interval: card.interval,
                    reps: card.reps,
                    metrics: metrics.next(),
                    note_fields: fields,
                })
            })
            .collect()
    }

    fn selected_note_fields(
        &mut self,
        nid: NoteId,
        wanted: &HashSet<&str>,
        fields_by_type: &mut HashMap<NotetypeId, Vec<(usize, String)>>,
    ) -> Result<Option<SelectedNoteFields>> {
        let Some(note) = self.storage.get_note_without_fields(nid)? else {
            return Ok(None);
        };
        if let Entry::Vacant(entry) = fields_by_type.entry(note.notetype_id) {
            let Some(notetype) = self.get_notetype(note.notetype_id)? else {
                return Ok(None);
            };
            entry.insert(
                notetype
                    .fields
                    .iter()
                    .enumerate()
                    .filter(|(_, field)| wanted.contains(field.name.as_str()))
                    .map(|(index, field)| (index, field.name.clone()))
                    .collect(),
            );
        }
        let selected = &fields_by_type[&note.notetype_id];
        let indices = selected.iter().map(|(index, _)| *index).collect::<Vec<_>>();
        let Some(values) = self.storage.get_note_fields_at_indices(nid, &indices)? else {
            return Ok(None);
        };
        Ok(Some(SelectedNoteFields {
            fields: selected
                .iter()
                .zip(values)
                .map(|((index, name), value)| Field {
                    name: name.clone(),
                    value,
                    order: *index as u32,
                })
                .collect(),
        }))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::search::SortMode;

    #[test]
    fn details_preserve_raw_values_field_order_and_card_order() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "<b>猫</b> & \"quotes\"")?;
        note.set_field(1, "line one\nline two")?;
        col.add_note(&mut note, DeckId(1))?;
        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        let before = col.storage.get_card(cid)?.unwrap();
        let names = vec![
            "Back".into(),
            "Front".into(),
            "Missing".into(),
            "Front".into(),
        ];
        let values = col.card_details(&[cid, CardId(1), cid], true, true, Some(&names))?;
        assert_eq!(values.len(), 2);
        assert_eq!(values[0], values[1]);
        assert_eq!(values[0].card_id, cid.0);
        assert_eq!(values[0].ctype, before.ctype as u32);
        assert_eq!(values[0].queue, before.queue as i32);
        assert_eq!(values[0].due, before.due);
        assert_eq!(values[0].interval, before.interval);
        assert_eq!(values[0].reps, before.reps);
        assert_eq!(
            values[0].metrics.as_ref().unwrap(),
            &col.card_memory_metrics(&[cid], true)?[0]
        );
        assert_eq!(
            values[0].note_fields.as_ref().unwrap().fields,
            vec![
                Field {
                    name: "Front".into(),
                    value: "<b>猫</b> & \"quotes\"".into(),
                    order: 0
                },
                Field {
                    name: "Back".into(),
                    value: "line one\nline two".into(),
                    order: 1
                },
            ]
        );
        assert_eq!(col.storage.get_card(cid)?.unwrap(), before);
        assert_eq!(col.storage.get_note(note.id)?.unwrap(), note);
        Ok(())
    }

    #[test]
    fn details_distinguish_skipped_empty_and_unknown_note_field_selection() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        let cid = col.search_cards("", SortMode::NoOrder)?[0];
        assert!(col.card_details(&[], true, true, Some(&[]))?.is_empty());
        let plain = col.card_details(&[cid], false, false, None)?.remove(0);
        assert!(plain.metrics.is_none());
        assert!(plain.note_fields.is_none());
        for names in [vec![], vec!["Missing".into()], vec!["front".into()]] {
            let entry = col
                .card_details(&[cid], false, false, Some(&names))?
                .remove(0);
            assert!(entry.note_fields.unwrap().fields.is_empty());
            assert!(entry.metrics.is_none());
        }
        assert!(col
            .storage
            .get_note_fields_at_indices(note.id, &[2])?
            .is_none());
        assert!(col
            .storage
            .get_note_fields_at_indices(NoteId(1), &[0])?
            .is_none());
        Ok(())
    }
}
