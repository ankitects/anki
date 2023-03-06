// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;
use std::collections::HashSet;
use std::mem;
use std::sync::Arc;

use sha1::Digest;
use sha1::Sha1;

use super::media::MediaUseMap;
use super::Context;
use crate::import_export::package::media::safe_normalized_file_name;
use crate::import_export::ImportProgress;
use crate::import_export::IncrementableProgress;
use crate::import_export::NoteLog;
use crate::prelude::*;
use crate::text::replace_media_refs;

struct NoteContext<'a> {
    target_col: &'a mut Collection,
    usn: Usn,
    normalize_notes: bool,
    remapped_notetypes: HashMap<NotetypeId, NotetypeId>,
    target_guids: HashMap<String, NoteMeta>,
    target_ids: HashSet<NoteId>,
    media_map: &'a mut MediaUseMap,
    imports: NoteImports,
}

#[derive(Debug, Default)]
pub(super) struct NoteImports {
    pub(super) id_map: HashMap<NoteId, NoteId>,
    /// All notes from the source collection as [Vec]s of their fields, and
    /// grouped by import result kind.
    pub(super) log: NoteLog,
}

impl NoteImports {
    fn log_new(&mut self, note: Note, source_id: NoteId) {
        self.id_map.insert(source_id, note.id);
        self.log.new.push(note.into_log_note());
    }

    fn log_updated(&mut self, note: Note, source_id: NoteId) {
        self.id_map.insert(source_id, note.id);
        self.log.updated.push(note.into_log_note());
    }

    fn log_duplicate(&mut self, mut note: Note, target_id: NoteId) {
        self.id_map.insert(note.id, target_id);
        // id is for looking up note in *target* collection
        note.id = target_id;
        self.log.duplicate.push(note.into_log_note());
    }

    fn log_conflicting(&mut self, note: Note) {
        self.log.conflicting.push(note.into_log_note());
    }
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

impl Context<'_> {
    pub(super) fn import_notes_and_notetypes(
        &mut self,
        media_map: &mut MediaUseMap,
    ) -> Result<NoteImports> {
        let mut ctx = NoteContext::new(self.usn, self.target_col, media_map)?;
        ctx.import_notetypes(mem::take(&mut self.data.notetypes))?;
        ctx.import_notes(mem::take(&mut self.data.notes), &mut self.progress)?;
        Ok(ctx.imports)
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
            imports: NoteImports::default(),
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
        let old_id = mem::take(&mut notetype.id);
        notetype.usn = self.usn;
        self.target_col
            .add_notetype_inner(notetype, self.usn, true)?;
        self.remapped_notetypes.insert(old_id, notetype.id);
        Ok(())
    }

    fn import_notes(
        &mut self,
        notes: Vec<Note>,
        progress: &mut IncrementableProgress<ImportProgress>,
    ) -> Result<()> {
        let mut incrementor = progress.incrementor(ImportProgress::Notes);

        for mut note in notes {
            incrementor.increment()?;
            if let Some(notetype_id) = self.remapped_notetypes.get(&note.notetype_id) {
                if self.target_guids.contains_key(&note.guid) {
                    self.imports.log_conflicting(note);
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

    fn add_note(&mut self, mut note: Note) -> Result<()> {
        self.munge_media(&mut note)?;
        self.target_col.canonify_note_tags(&mut note, self.usn)?;
        let notetype = self.get_expected_notetype(note.notetype_id)?;
        note.prepare_for_update(&notetype, self.normalize_notes)?;
        note.usn = self.usn;
        let old_id = self.uniquify_note_id(&mut note);

        self.target_col.add_note_only_with_id_undoable(&mut note)?;
        self.target_ids.insert(note.id);
        self.imports.log_new(note, old_id);

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
        self.target_col.get_notetype(ntid)?.or_not_found(ntid)
    }

    fn get_expected_note(&mut self, nid: NoteId) -> Result<Note> {
        self.target_col.storage.get_note(nid)?.or_not_found(nid)
    }

    fn maybe_update_note(&mut self, note: Note, meta: NoteMeta) -> Result<()> {
        if meta.mtime < note.mtime {
            if meta.notetype_id == note.notetype_id {
                self.update_note(note, meta.id)?;
            } else {
                self.imports.log_conflicting(note);
            }
        } else {
            self.imports.log_duplicate(note, meta.id);
        }
        Ok(())
    }

    fn update_note(&mut self, mut note: Note, target_id: NoteId) -> Result<()> {
        let source_id = note.id;
        note.id = target_id;
        self.munge_media(&mut note)?;
        let original = self.get_expected_note(note.id)?;
        let notetype = self.get_expected_notetype(note.notetype_id)?;
        self.target_col.update_note_inner_without_cards(
            &mut note,
            &original,
            &notetype,
            self.usn,
            true,
            self.normalize_notes,
            true,
        )?;
        self.imports.log_updated(note, source_id);
        Ok(())
    }

    fn munge_media(&mut self, note: &mut Note) -> Result<()> {
        for field in note.fields_mut() {
            if let Some(new_field) = self.replace_media_refs(field) {
                *field = new_field;
            };
        }
        Ok(())
    }

    fn replace_media_refs(&mut self, field: &mut str) -> Option<String> {
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
    fn schema_hash(&self) -> Sha1Hash {
        let mut hasher = Sha1::new();
        for field in &self.fields {
            hasher.update(field.name.as_bytes());
        }
        for template in &self.templates {
            hasher.update(template.name.as_bytes());
        }
        hasher.finalize().into()
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::collection::open_test_collection;
    use crate::import_export::package::media::SafeMediaEntry;

    /// Import [Note] into [Collection], optionally taking a [MediaUseMap],
    /// or a [Notetype] remapping.
    macro_rules! import_note {
        ($col:expr, $note:expr, $old_notetype:expr => $new_notetype:expr) => {{
            let mut media_map = MediaUseMap::default();
            let mut ctx = NoteContext::new(Usn(1), &mut $col, &mut media_map).unwrap();
            ctx.remapped_notetypes.insert($old_notetype, $new_notetype);
            let mut progress = IncrementableProgress::new(|_, _| true);
            ctx.import_notes(vec![$note], &mut progress).unwrap();
            ctx.imports.log
        }};
        ($col:expr, $note:expr, $media_map:expr) => {{
            let mut ctx = NoteContext::new(Usn(1), &mut $col, &mut $media_map).unwrap();
            let mut progress = IncrementableProgress::new(|_, _| true);
            ctx.import_notes(vec![$note], &mut progress).unwrap();
            ctx.imports.log
        }};
        ($col:expr, $note:expr) => {{
            let mut media_map = MediaUseMap::default();
            import_note!($col, $note, media_map)
        }};
    }

    /// Assert that exactly one [Note] is logged, and that with the given state
    /// and fields.
    macro_rules! assert_note_logged {
        ($log:expr, $state:ident, $fields:expr) => {
            assert_eq!($log.$state.pop().unwrap().fields, $fields);
            assert!($log.new.is_empty());
            assert!($log.updated.is_empty());
            assert!($log.duplicate.is_empty());
            assert!($log.conflicting.is_empty());
        };
    }

    impl Collection {
        fn note_id_for_guid(&self, guid: &str) -> NoteId {
            self.storage
                .db
                .query_row("SELECT id FROM notes WHERE guid = ?", [guid], |r| r.get(0))
                .unwrap()
        }
    }

    #[test]
    fn should_add_note_with_new_id_if_guid_is_unique_and_id_is_not() {
        let mut col = open_test_collection();
        let mut note = NoteAdder::basic(&mut col).add(&mut col);
        note.guid = "other".to_string();
        let original_id = note.id;

        let mut log = import_note!(col, note);
        assert_ne!(col.note_id_for_guid("other"), original_id);
        assert_note_logged!(log, new, &["", ""]);
    }

    #[test]
    fn should_skip_note_if_guid_already_exists_with_newer_mtime() {
        let mut col = open_test_collection();
        let mut note = NoteAdder::basic(&mut col).add(&mut col);
        note.mtime.0 -= 1;
        note.fields_mut()[0] = "outdated".to_string();

        let mut log = import_note!(col, note);
        assert_eq!(col.get_all_notes()[0].fields()[0], "");
        assert_note_logged!(log, duplicate, &["outdated", ""]);
    }

    #[test]
    fn should_update_note_if_guid_already_exists_with_different_id() {
        let mut col = open_test_collection();
        let mut note = NoteAdder::basic(&mut col).add(&mut col);
        note.id.0 = 42;
        note.mtime.0 += 1;
        note.fields_mut()[0] = "updated".to_string();

        let mut log = import_note!(col, note);
        assert_eq!(col.get_all_notes()[0].fields()[0], "updated");
        assert_note_logged!(log, updated, &["updated", ""]);
    }

    #[test]
    fn should_ignore_note_if_guid_already_exists_with_different_notetype() {
        let mut col = open_test_collection();
        let mut note = NoteAdder::basic(&mut col).add(&mut col);
        note.notetype_id.0 = 42;
        note.mtime.0 += 1;
        note.fields_mut()[0] = "updated".to_string();

        let mut log = import_note!(col, note);
        assert_eq!(col.get_all_notes()[0].fields()[0], "");
        assert_note_logged!(log, conflicting, &["updated", ""]);
    }

    #[test]
    fn should_add_note_with_remapped_notetype_if_in_notetype_map() {
        let mut col = open_test_collection();
        let basic_ntid = col.get_notetype_by_name("basic").unwrap().unwrap().id;
        let mut note = NoteAdder::basic(&mut col).note();
        note.notetype_id.0 = 123;

        let mut log = import_note!(col, note, NotetypeId(123) => basic_ntid);
        assert_eq!(col.get_all_notes()[0].notetype_id, basic_ntid);
        assert_note_logged!(log, new, &["", ""]);
    }

    #[test]
    fn should_ignore_note_if_guid_already_exists_and_notetype_is_remapped() {
        let mut col = open_test_collection();
        let basic_ntid = col.get_notetype_by_name("basic").unwrap().unwrap().id;
        let mut note = NoteAdder::basic(&mut col).add(&mut col);
        note.notetype_id.0 = 123;
        note.mtime.0 += 1;
        note.fields_mut()[0] = "updated".to_string();

        let mut log = import_note!(col, note, NotetypeId(123) => basic_ntid);
        assert_eq!(col.get_all_notes()[0].fields()[0], "");
        assert_note_logged!(log, conflicting, &["updated", ""]);
    }

    #[test]
    fn should_add_note_with_remapped_media_reference_in_field_if_in_media_map() {
        let mut col = open_test_collection();
        let mut note = NoteAdder::basic(&mut col).note();
        note.fields_mut()[0] = "<img src='foo.jpg'>".to_string();

        let mut media_map = MediaUseMap::default();
        let entry = SafeMediaEntry::from_legacy(("0", "bar.jpg".to_string())).unwrap();
        media_map.add_checked("foo.jpg", entry);

        let mut log = import_note!(col, note, media_map);
        assert_eq!(col.get_all_notes()[0].fields()[0], "<img src='bar.jpg'>");
        assert_note_logged!(log, new, &[" bar.jpg ", ""]);
    }
}
