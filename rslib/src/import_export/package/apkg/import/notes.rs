// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    borrow::Cow,
    collections::{HashMap, HashSet},
    mem,
    sync::Arc,
};

use sha1::Sha1;

use super::{media::MediaUseMap, Context};
use crate::{
    import_export::package::media::safe_normalized_file_name, prelude::*, text::replace_media_refs,
};

struct NoteContext<'a> {
    target_col: &'a mut Collection,
    usn: Usn,
    normalize_notes: bool,
    remapped_notetypes: HashMap<NotetypeId, NotetypeId>,
    target_guids: HashMap<String, NoteMeta>,
    target_ids: HashSet<NoteId>,
    media_map: &'a mut MediaUseMap,
    imported_notes: HashMap<NoteId, NoteId>,
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct NoteMeta {
    id: NoteId,
    mtime: TimestampSecs,
    notetype_id: NotetypeId,
}

impl NoteMeta {
    pub(crate) fn new(id: NoteId, mtime: TimestampSecs, notetype_id: NotetypeId) -> Self {
        Self {
            id,
            mtime,
            notetype_id,
        }
    }
}

impl<F> Context<'_, F> {
    pub(super) fn import_notes_and_notetypes(
        &mut self,
        media_map: &mut MediaUseMap,
    ) -> Result<HashMap<NoteId, NoteId>> {
        let mut ctx = NoteContext::new(self.usn, self.target_col, media_map)?;
        ctx.import_notetypes(mem::take(&mut self.data.notetypes))?;
        ctx.import_notes(mem::take(&mut self.data.notes))?;
        Ok(ctx.imported_notes)
    }
}

impl<'n> NoteContext<'n> {
    fn new<'a: 'n>(
        usn: Usn,
        target_col: &'a mut Collection,
        media_map: &'a mut MediaUseMap,
    ) -> Result<Self> {
        let target_guids = target_col.storage.note_guid_map()?;
        let normalize_notes = target_col.get_config_bool(BoolKey::NormalizeNoteText);
        let target_ids = target_col.storage.get_all_note_ids()?;
        Ok(Self {
            target_col,
            usn,
            normalize_notes,
            remapped_notetypes: HashMap::new(),
            target_guids,
            target_ids,
            imported_notes: HashMap::new(),
            media_map,
        })
    }

    fn import_notetypes(&mut self, mut notetypes: Vec<Notetype>) -> Result<()> {
        for notetype in &mut notetypes {
            if let Some(existing) = self.target_col.storage.get_notetype(notetype.id)? {
                self.merge_or_remap_notetype(notetype, existing)?;
            } else {
                self.add_notetype(notetype)?;
            }
        }
        Ok(())
    }

    fn merge_or_remap_notetype(
        &mut self,
        incoming: &mut Notetype,
        existing: Notetype,
    ) -> Result<()> {
        if incoming.schema_hash() == existing.schema_hash() {
            if incoming.mtime_secs > existing.mtime_secs {
                self.update_notetype(incoming, existing)?;
            }
        } else {
            self.add_notetype_with_remapped_id(incoming)?;
        }
        Ok(())
    }

    fn add_notetype(&mut self, notetype: &mut Notetype) -> Result<()> {
        notetype.prepare_for_update(None, true)?;
        self.target_col
            .ensure_notetype_name_unique(notetype, self.usn)?;
        notetype.usn = self.usn;
        self.target_col
            .add_notetype_with_unique_id_undoable(notetype)
    }

    fn update_notetype(&mut self, notetype: &mut Notetype, original: Notetype) -> Result<()> {
        notetype.usn = self.usn;
        self.target_col
            .add_or_update_notetype_with_existing_id_inner(notetype, Some(original), self.usn, true)
    }

    fn add_notetype_with_remapped_id(&mut self, notetype: &mut Notetype) -> Result<()> {
        let old_id = std::mem::take(&mut notetype.id);
        notetype.usn = self.usn;
        self.target_col
            .add_notetype_inner(notetype, self.usn, true)?;
        self.remapped_notetypes.insert(old_id, notetype.id);
        Ok(())
    }

    fn import_notes(&mut self, mut notes: Vec<Note>) -> Result<()> {
        for note in &mut notes {
            if let Some(notetype_id) = self.remapped_notetypes.get(&note.notetype_id) {
                if self.target_guids.contains_key(&note.guid) {
                    // TODO: Log ignore
                } else {
                    note.notetype_id = *notetype_id;
                    self.add_note(note)?;
                }
            } else if let Some(&meta) = self.target_guids.get(&note.guid) {
                self.maybe_update_note(note, meta)?;
            } else {
                self.add_note(note)?;
            }
        }
        Ok(())
    }

    fn add_note(&mut self, mut note: &mut Note) -> Result<()> {
        // TODO: Log add
        self.munge_media(note)?;
        self.target_col.canonify_note_tags(note, self.usn)?;
        let notetype = self.get_expected_notetype(note.notetype_id)?;
        note.prepare_for_update(&notetype, self.normalize_notes)?;
        note.usn = self.usn;
        let old_id = self.uniquify_note_id(note);

        self.target_col.add_note_only_with_id_undoable(note)?;
        self.target_ids.insert(note.id);
        self.imported_notes.insert(old_id, note.id);

        Ok(())
    }

    fn uniquify_note_id(&mut self, note: &mut Note) -> NoteId {
        let original = note.id;
        while self.target_ids.contains(&note.id) {
            note.id.0 += 999;
        }
        original
    }

    fn get_expected_notetype(&mut self, ntid: NotetypeId) -> Result<Arc<Notetype>> {
        self.target_col
            .get_notetype(ntid)?
            .ok_or(AnkiError::NotFound)
    }

    fn get_expected_note(&mut self, nid: NoteId) -> Result<Note> {
        self.target_col
            .storage
            .get_note(nid)?
            .ok_or(AnkiError::NotFound)
    }

    fn maybe_update_note(&mut self, note: &mut Note, meta: NoteMeta) -> Result<()> {
        if meta.mtime < note.mtime {
            if meta.notetype_id == note.notetype_id {
                self.imported_notes.insert(note.id, meta.id);
                note.id = meta.id;
                self.update_note(note)?;
            } else {
                // TODO: Log ignore
            }
        } else {
            // TODO: Log duplicate
            self.imported_notes.insert(note.id, meta.id);
        }
        Ok(())
    }

    fn update_note(&mut self, note: &mut Note) -> Result<()> {
        // TODO: Log update
        self.munge_media(note)?;
        let original = self.get_expected_note(note.id)?;
        let notetype = self.get_expected_notetype(note.notetype_id)?;
        self.target_col.update_note_inner_without_cards(
            note,
            &original,
            &notetype,
            self.usn,
            true,
            self.normalize_notes,
            true,
        )
    }

    fn munge_media(&mut self, note: &mut Note) -> Result<()> {
        for field in note.fields_mut() {
            if let Some(new_field) = self.replace_media_refs(field) {
                *field = new_field;
            };
        }
        Ok(())
    }

    fn replace_media_refs(&mut self, field: &mut String) -> Option<String> {
        replace_media_refs(field, |name| {
            if let Ok(normalized) = safe_normalized_file_name(name) {
                if let Some(entry) = self.media_map.use_entry(&normalized) {
                    if entry.name != name {
                        // name is not normalized, and/or remapped
                        return Some(entry.name.clone());
                    }
                } else if let Cow::Owned(s) = normalized {
                    // no entry; might be a reference to an existing file, so ensure normalization
                    return Some(s);
                }
            }
            None
        })
    }
}

impl Notetype {
    fn schema_hash(&self) -> [u8; 20] {
        let mut hasher = Sha1::new();
        for field in &self.fields {
            hasher.update(field.name.as_bytes());
        }
        for template in &self.templates {
            hasher.update(template.name.as_bytes());
        }
        hasher.digest().bytes()
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::{collection::open_test_collection, import_export::package::media::SafeMediaEntry};

    #[test]
    fn notes() {
        let mut col = open_test_collection();
        let mut media_map = MediaUseMap::default();
        let basic_ntid = col.get_notetype_by_name("basic").unwrap().unwrap().id;

        // a note with a remapped media reference
        let mut note_with_media = col.new_note("basic");
        note_with_media.fields_mut()[0] = "<img src='foo.jpg'>".to_string();
        let entry = SafeMediaEntry::from_legacy(("0", "bar.jpg".to_string())).unwrap();
        media_map.add_checked("foo.jpg", entry);
        // a note with an id existing in the target, but a unique guid
        let mut note_with_existing_id = col.add_new_note("basic");
        note_with_existing_id.guid = "other".to_string();
        // a note of which a later version exists in the target
        let mut outdated_note = col.add_new_note("basic");
        outdated_note.mtime.0 -= 1;
        outdated_note.fields_mut()[0] = "outdated".to_string();
        // an updated version of a target note, but with a different id
        let mut updated_note = col.add_new_note("basic");
        let updated_note_id = updated_note.id;
        updated_note.id.0 = 42;
        updated_note.mtime.0 += 1;
        updated_note.fields_mut()[0] = "updated".to_string();
        // an updated version of a target note, but with a different notetype
        let mut updated_note_with_new_nt = col.add_new_note("basic");
        updated_note_with_new_nt.notetype_id.0 = 42;
        updated_note_with_new_nt.mtime.0 += 1;
        updated_note_with_new_nt.fields_mut()[0] = "updated".to_string();
        // a new note with a remapped notetype
        let mut note_with_remapped_nt = col.new_note("basic");
        note_with_remapped_nt.notetype_id.0 = 123;
        // a note existing in the target with a remapped notetype
        let mut updated_note_with_remapped_nt = col.add_new_note("basic");
        updated_note_with_remapped_nt.notetype_id.0 = 123;
        updated_note_with_remapped_nt.mtime.0 += 1;
        updated_note_with_remapped_nt.fields_mut()[0] = "updated".to_string();

        let notes = vec![
            note_with_media.clone(),
            note_with_existing_id.clone(),
            outdated_note.clone(),
            updated_note.clone(),
            updated_note_with_new_nt.clone(),
            note_with_remapped_nt.clone(),
            updated_note_with_remapped_nt.clone(),
        ];
        let mut ctx = NoteContext::new(Usn(1), &mut col, &mut media_map).unwrap();
        ctx.remapped_notetypes.insert(NotetypeId(123), basic_ntid);
        ctx.import_notes(notes).unwrap();

        // media is remapped
        assert_eq!(
            col.get_note_field(note_with_media.id, 0),
            "<img src='bar.jpg'>"
        );
        // conflicting note id is remapped
        assert_ne!(col.note_id_for_guid("other"), note_with_existing_id.id);
        // note doesn't overwrite more recent version in target
        assert_eq!(col.get_note_field(outdated_note.id, 0), "");
        // note with same guid is updated, regardless of id
        assert_eq!(col.get_note_field(updated_note_id, 0), "updated");
        // note is not updated if notetype is different
        assert_eq!(col.get_note_field(updated_note_with_new_nt.id, 0), "");
        // notetype id is remapped
        assert_eq!(
            col.get_note_unwrapped(note_with_remapped_nt.id).notetype_id,
            basic_ntid
        );
        // note with remapped notetype is not updated
        assert_eq!(col.get_note_field(updated_note_with_remapped_nt.id, 0), "");
    }

    impl Collection {
        fn note_id_for_guid(&self, guid: &str) -> NoteId {
            self.storage
                .db
                .query_row("SELECT id FROM notes WHERE guid = ?", [guid], |r| r.get(0))
                .unwrap()
        }
    }
}
