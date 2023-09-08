// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::borrow::Cow;
use std::collections::HashMap;
use std::collections::HashSet;
use std::mem;
use std::sync::Arc;

use super::media::MediaUseMap;
use super::Context;
use super::TemplateMap;
use crate::import_export::package::media::safe_normalized_file_name;
use crate::import_export::package::UpdateCondition;
use crate::import_export::ImportProgress;
use crate::import_export::NoteLog;
use crate::notetype::ChangeNotetypeInput;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;
use crate::text::replace_media_refs;

struct NoteContext<'a> {
    target_col: &'a mut Collection,
    usn: Usn,
    normalize_notes: bool,
    remapped_notetypes: HashMap<NotetypeId, NotetypeId>,
    remapped_fields: HashMap<NotetypeId, Vec<Option<u32>>>,
    target_guids: HashMap<String, NoteMeta>,
    target_ids: HashSet<NoteId>,
    target_notetypes: Vec<Arc<Notetype>>,
    media_map: &'a mut MediaUseMap,
    merge_notetypes: bool,
    update_notes: UpdateCondition,
    update_notetypes: UpdateCondition,
    imports: NoteImports,
}

#[derive(Debug, Default)]
pub(super) struct NoteImports {
    pub(super) id_map: HashMap<NoteId, NoteId>,
    pub(super) remapped_templates: HashMap<NotetypeId, TemplateMap>,
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
        let mut ctx = NoteContext::new(
            self.usn,
            self.target_col,
            media_map,
            self.merge_notetypes,
            self.update_notes,
            self.update_notetypes,
        )?;
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
        merge_notetypes: bool,
        update_notes: UpdateCondition,
        update_notetypes: UpdateCondition,
    ) -> Result<Self> {
        let target_guids = target_col.storage.note_guid_map()?;
        let normalize_notes = target_col.get_config_bool(BoolKey::NormalizeNoteText);
        let target_ids = target_col.storage.get_all_note_ids()?;
        let target_notetypes = target_col.get_all_notetypes()?;
        Ok(Self {
            target_col,
            usn,
            normalize_notes,
            remapped_notetypes: HashMap::new(),
            remapped_fields: HashMap::new(),
            target_guids,
            target_ids,
            target_notetypes,
            imports: NoteImports::default(),
            merge_notetypes,
            update_notes,
            update_notetypes,
            media_map,
        })
    }

    fn import_notetypes(&mut self, mut notetypes: Vec<Notetype>) -> Result<()> {
        for notetype in &mut notetypes {
            notetype.config.original_id.replace(notetype.id.0);
            if let Some(nt) = self.get_target_notetype(notetype.id) {
                let existing = nt.as_ref().clone();
                if self.merge_notetypes {
                    self.update_or_merge_notetype(notetype, existing)?;
                } else {
                    self.update_or_duplicate_notetype(notetype, existing)?;
                }
            } else {
                self.add_notetype(notetype)?;
            }
        }
        Ok(())
    }

    fn get_target_notetype(&self, ntid: NotetypeId) -> Option<&Arc<Notetype>> {
        self.target_notetypes.iter().find(|nt| nt.id == ntid)
    }

    fn update_or_duplicate_notetype(
        &mut self,
        incoming: &mut Notetype,
        mut existing: Notetype,
    ) -> Result<()> {
        if !existing.equal_schema(incoming) {
            if let Some(nt) = self.get_previously_duplicated_notetype(incoming) {
                existing = nt;
                self.remapped_notetypes.insert(incoming.id, existing.id);
                incoming.id = existing.id;
            } else {
                return self.add_notetype_with_remapped_id(incoming);
            }
        }
        if should_update(
            self.update_notetypes,
            existing.mtime_secs,
            incoming.mtime_secs,
        ) {
            self.update_notetype(incoming, existing, false)?;
        }
        Ok(())
    }

    /// Try to find a notetype with matching original id and schema.
    fn get_previously_duplicated_notetype(&self, original: &Notetype) -> Option<Notetype> {
        self.target_notetypes
            .iter()
            .find(|nt| {
                nt.id != original.id
                    && nt.config.original_id == Some(original.id.0)
                    && nt.equal_schema(original)
            })
            .map(|nt| nt.as_ref().clone())
    }

    fn should_update_notetype(&self, existing: &Notetype, incoming: &Notetype) -> bool {
        match self.update_notetypes {
            UpdateCondition::IfNewer => existing.mtime_secs < incoming.mtime_secs,
            UpdateCondition::Always => true,
            UpdateCondition::Never => false,
        }
    }

    fn add_notetype(&mut self, notetype: &mut Notetype) -> Result<()> {
        notetype.prepare_for_update(None, true)?;
        self.target_col
            .ensure_notetype_name_unique(notetype, self.usn)?;
        notetype.usn = self.usn;
        self.target_col
            .add_notetype_with_unique_id_undoable(notetype)
    }

    fn update_notetype(
        &mut self,
        notetype: &mut Notetype,
        original: Notetype,
        modified: bool,
    ) -> Result<()> {
        if modified {
            notetype.set_modified(self.usn);
            notetype.prepare_for_update(Some(&original), true)?;
        } else {
            notetype.usn = self.usn;
        }
        self.target_col
            .add_or_update_notetype_with_existing_id_inner(notetype, Some(original), self.usn, true)
    }

    fn update_or_merge_notetype(
        &mut self,
        incoming: &mut Notetype,
        mut existing: Notetype,
    ) -> Result<()> {
        let original_existing = existing.clone();
        // get and merge duplicated notetypes from previous no-merge imports
        let mut siblings = self.get_sibling_notetypes(existing.id);
        existing.merge_all(&siblings);
        incoming.merge(&existing);
        existing.merge(incoming);
        self.record_remapped_ords(incoming);
        let new_incoming = if self.should_update_notetype(&existing, incoming) {
            // ords must be existing's as they are used to remap note fields and card
            // template indices
            incoming.copy_ords(&existing);
            incoming
        } else {
            &mut existing
        };
        self.update_notetype(new_incoming, original_existing, true)?;
        self.drop_sibling_notetypes(new_incoming, &mut siblings)
    }

    /// Get notetypes with different id, but matching original id.
    fn get_sibling_notetypes(&mut self, original_id: NotetypeId) -> Vec<Notetype> {
        self.target_notetypes
            .iter()
            .filter(|nt| nt.id != original_id && nt.config.original_id == Some(original_id.0))
            .map(|nt| nt.as_ref().clone())
            .collect()
    }

    /// Removes the sibling notetypes, changing their notes' notetype to
    /// `original`. This assumes `siblings` have already been merged into
    /// `original`.
    fn drop_sibling_notetypes(
        &mut self,
        original: &Notetype,
        siblings: &mut [Notetype],
    ) -> Result<()> {
        for nt in siblings {
            nt.merge(original);
            let note_ids = self.target_col.search_notes_unordered(nt.id)?;
            self.target_col
                .change_notetype_of_notes_inner(ChangeNotetypeInput {
                    current_schema: self.target_col_schema_change()?,
                    note_ids,
                    old_notetype_name: nt.name.clone(),
                    old_notetype_id: nt.id,
                    new_notetype_id: original.id,
                    new_fields: nt.field_ords_vec(),
                    new_templates: Some(nt.template_ords_vec()),
                })?;
            self.target_col.remove_notetype_inner(nt.id)?;
        }
        Ok(())
    }

    fn target_col_schema_change(&self) -> Result<TimestampMillis> {
        self.target_col
            .storage
            .get_collection_timestamps()
            .map(|ts| ts.schema_change)
    }

    fn record_remapped_ords(&mut self, incoming: &Notetype) {
        self.remapped_fields
            .insert(incoming.id, incoming.field_ords().collect());
        self.imports.remapped_templates.insert(
            incoming.id,
            incoming
                .template_ords()
                .enumerate()
                .filter_map(|(new, old)| old.map(|ord| (ord as u16, new as u16)))
                .collect(),
        );
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
        progress: &mut ThrottlingProgressHandler<ImportProgress>,
    ) -> Result<()> {
        let mut incrementor = progress.incrementor(ImportProgress::Notes);
        self.imports.log.found_notes = notes.len() as u32;
        for mut note in notes {
            incrementor.increment()?;
            self.remap_notetype_and_fields(&mut note);
            if let Some(existing_note) = self.target_guids.get(&note.guid) {
                self.maybe_update_existing_note(*existing_note, note)?;
            } else {
                self.add_note(note)?;
            }
        }

        Ok(())
    }

    fn remap_notetype_and_fields(&mut self, note: &mut Note) {
        if let Some(new_ords) = self.remapped_fields.get(&note.notetype_id) {
            note.reorder_fields(new_ords);
        }
        if let Some(remapped_ntid) = self.remapped_notetypes.get(&note.notetype_id) {
            note.notetype_id = *remapped_ntid;
        }
    }

    fn maybe_update_existing_note(&mut self, existing: NoteMeta, incoming: Note) -> Result<()> {
        if incoming.notetype_id != existing.notetype_id {
            // notetype of existing note has changed, or notetype of incoming note has been
            // remapped due to a schema conflict
            self.imports.log_conflicting(incoming);
        } else if should_update(self.update_notes, existing.mtime, incoming.mtime) {
            self.update_note(incoming, existing.id)?;
        } else {
            // TODO: might still want to update merged in fields
            self.imports.log_duplicate(incoming, existing.id);
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

fn should_update(
    cond: UpdateCondition,
    existing_mtime: TimestampSecs,
    incoming_mtime: TimestampSecs,
) -> bool {
    match cond {
        UpdateCondition::IfNewer => existing_mtime < incoming_mtime,
        UpdateCondition::Always => existing_mtime != incoming_mtime,
        UpdateCondition::Never => false,
    }
}

impl Notetype {
    pub(crate) fn field_ords(&self) -> impl Iterator<Item = Option<u32>> + '_ {
        self.fields.iter().map(|f| f.ord)
    }

    pub(crate) fn template_ords(&self) -> impl Iterator<Item = Option<u32>> + '_ {
        self.templates.iter().map(|t| t.ord)
    }

    fn field_ords_vec(&self) -> Vec<Option<usize>> {
        self.field_ords()
            .map(|opt| opt.map(|u| u as usize))
            .collect()
    }

    fn template_ords_vec(&self) -> Vec<Option<usize>> {
        self.template_ords()
            .map(|opt| opt.map(|u| u as usize))
            .collect()
    }

    fn equal_schema(&self, other: &Self) -> bool {
        self.fields.len() == other.fields.len()
            && self.templates.len() == other.templates.len()
            && self
                .fields
                .iter()
                .zip(other.fields.iter())
                .all(|(f1, f2)| f1.is_match(f2))
            && self
                .templates
                .iter()
                .zip(other.templates.iter())
                .all(|(t1, t2)| t1.is_match(t2))
    }

    fn copy_ords(&mut self, other: &Self) {
        for (field, other_ord) in self.fields.iter_mut().zip(other.field_ords()) {
            field.ord = other_ord;
        }
        for (template, other_ord) in self.templates.iter_mut().zip(other.template_ords()) {
            template.ord = other_ord;
        }
    }
}

#[cfg(test)]
mod test {
    use anki_proto::import_export::ImportAnkiPackageOptions;
    use tempfile::TempDir;

    use super::*;
    use crate::collection::CollectionBuilder;
    use crate::import_export::package::media::SafeMediaEntry;
    use crate::notetype::CardTemplate;
    use crate::notetype::NoteField;

    /// Import [Note] into [Collection], optionally taking a [MediaUseMap],
    /// or a [Notetype] remapping.
    macro_rules! import_note {
        ($col:expr, $note:expr, $old_notetype:expr => $new_notetype:expr) => {{
            let mut media_map = MediaUseMap::default();
            let mut progress = $col.new_progress_handler();
            let mut ctx = NoteContext::new(
                Usn(1),
                &mut $col,
                &mut media_map,
                false,
                UpdateCondition::IfNewer,
                UpdateCondition::IfNewer,
            )
            .unwrap();
            ctx.remapped_notetypes.insert($old_notetype, $new_notetype);
            ctx.import_notes(vec![$note], &mut progress).unwrap();
            ctx.imports.log
        }};
        ($col:expr, $note:expr, $media_map:expr) => {{
            let mut progress = $col.new_progress_handler();
            let mut ctx = NoteContext::new(
                Usn(1),
                &mut $col,
                &mut $media_map,
                false,
                UpdateCondition::IfNewer,
                UpdateCondition::IfNewer,
            )
            .unwrap();
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

    struct Remappings {
        remapped_notetypes: HashMap<NotetypeId, NotetypeId>,
        remapped_fields: HashMap<NotetypeId, Vec<Option<u32>>>,
        remapped_templates: HashMap<NotetypeId, TemplateMap>,
    }

    /// Imports the notetype into the collection, and returns its remapped id if
    /// any.
    macro_rules! import_notetype {
        ($col:expr, $notetype:expr) => {{
            import_notetype!($col, $notetype, merge = false)
        }};
        ($col:expr, $notetype:expr, merge = $merge:expr) => {{
            let mut media_map = MediaUseMap::default();
            let mut ctx = NoteContext::new(
                Usn(1),
                $col,
                &mut media_map,
                $merge,
                UpdateCondition::IfNewer,
                UpdateCondition::IfNewer,
            )
            .unwrap();
            ctx.import_notetypes(vec![$notetype]).unwrap();
            Remappings {
                remapped_notetypes: ctx.remapped_notetypes,
                remapped_fields: ctx.remapped_fields,
                remapped_templates: ctx.imports.remapped_templates,
            }
        }};
    }

    impl Collection {
        fn note_id_for_guid(&self, guid: &str) -> NoteId {
            self.storage
                .db
                .query_row("SELECT id FROM notes WHERE guid = ?", [guid], |r| r.get(0))
                .unwrap()
        }
    }

    impl Notetype {
        pub(crate) fn field_names(&self) -> impl Iterator<Item = &String> {
            self.fields.iter().map(|f| &f.name)
        }

        pub(crate) fn template_names(&self) -> impl Iterator<Item = &String> {
            self.templates.iter().map(|t| &t.name)
        }
    }

    #[test]
    fn should_add_note_with_new_id_if_guid_is_unique_and_id_is_not() {
        let mut col = Collection::new();
        let mut note = NoteAdder::basic(&mut col).add(&mut col);
        note.guid = "other".to_string();
        let original_id = note.id;

        let mut log = import_note!(col, note);
        assert_ne!(col.note_id_for_guid("other"), original_id);
        assert_note_logged!(log, new, &["", ""]);
    }

    #[test]
    fn should_skip_note_if_guid_already_exists_with_newer_mtime() {
        let mut col = Collection::new();
        let mut note = NoteAdder::basic(&mut col).add(&mut col);
        note.mtime.0 -= 1;
        note.fields_mut()[0] = "outdated".to_string();

        let mut log = import_note!(col, note);
        assert_eq!(col.get_all_notes()[0].fields()[0], "");
        assert_note_logged!(log, duplicate, &["outdated", ""]);
    }

    #[test]
    fn should_update_note_if_guid_already_exists_with_different_id() {
        let mut col = Collection::new();
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
        let mut col = Collection::new();
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
        let mut col = Collection::new();
        let basic_ntid = col.get_notetype_by_name("basic").unwrap().unwrap().id;
        let mut note = NoteAdder::basic(&mut col).note();
        note.notetype_id.0 = 123;

        let mut log = import_note!(col, note, NotetypeId(123) => basic_ntid);
        assert_eq!(col.get_all_notes()[0].notetype_id, basic_ntid);
        assert_note_logged!(log, new, &["", ""]);
    }

    #[test]
    fn should_ignore_note_if_guid_already_exists_and_notetype_is_remapped() {
        let mut col = Collection::new();
        let basic_ntid = col.get_notetype_by_name("basic").unwrap().unwrap().id;
        let mut note = NoteAdder::basic(&mut col).add(&mut col);
        note.mtime.0 += 1;
        note.fields_mut()[0] = "updated".to_string();

        let mut log = import_note!(col, note, basic_ntid => NotetypeId(123));
        assert_eq!(col.get_all_notes()[0].fields()[0], "");
        assert_note_logged!(log, conflicting, &["updated", ""]);
    }

    #[test]
    fn should_add_note_with_remapped_media_reference_in_field_if_in_media_map() {
        let mut col = Collection::new();
        let mut note = NoteAdder::basic(&mut col).note();
        note.fields_mut()[0] = "<img src='foo.jpg'>".to_string();

        let mut media_map = MediaUseMap::default();
        let entry = SafeMediaEntry::from_legacy(("0", "bar.jpg".to_string())).unwrap();
        media_map.add_checked("foo.jpg", entry);

        let mut log = import_note!(col, note, media_map);
        assert_eq!(col.get_all_notes()[0].fields()[0], "<img src='bar.jpg'>");
        assert_note_logged!(log, new, &[" bar.jpg ", ""]);
    }

    #[test]
    fn should_import_new_notetype() {
        let mut col = Collection::new();
        let mut new_basic = crate::notetype::stock::basic(&col.tr);
        new_basic.id.0 = 123;
        import_notetype!(&mut col, new_basic);
        assert!(col.storage.get_notetype(NotetypeId(123)).unwrap().is_some());
    }

    #[test]
    fn should_update_existing_notetype_with_older_mtime_and_matching_schema() {
        let mut col = Collection::new();
        let mut basic = col.basic_notetype();
        basic.mtime_secs.0 += 1;
        basic.name = String::from("new");
        import_notetype!(&mut col, basic);
        assert!(col.get_notetype_by_name("new").unwrap().is_some());
    }

    #[test]
    fn should_not_update_existing_notetype_with_newer_mtime_and_matching_schema() {
        let mut col = Collection::new();
        let mut basic = col.basic_notetype();
        basic.mtime_secs.0 -= 1;
        basic.name = String::from("new");
        import_notetype!(&mut col, basic);
        assert!(col.get_notetype_by_name("new").unwrap().is_none());
    }

    #[test]
    fn should_rename_field_with_matching_id_without_schema_change() {
        let mut col = Collection::new();
        let mut to_import = col.basic_notetype();
        to_import.fields[0].name = String::from("renamed");
        to_import.mtime_secs.0 += 1;
        import_notetype!(&mut col, to_import);
        assert_eq!(col.basic_notetype().fields[0].name, "renamed");
    }

    #[test]
    fn should_add_remapped_notetype_if_schema_has_changed_and_reuse_it_subsequently() {
        let mut col = Collection::new();
        let mut to_import = col.basic_notetype();
        to_import.fields[0].name = String::from("new field");
        // clear id or schemas would still match
        to_import.fields[0].config.id.take();

        // schema mismatch => notetype should be imported with new id
        let out = import_notetype!(&mut col, to_import.clone());
        let remapped_id = *out.remapped_notetypes.values().next().unwrap();
        assert_eq!(col.basic_notetype().fields[0].name, "Front");
        let remapped = col.storage.get_notetype(remapped_id).unwrap().unwrap();
        assert_eq!(remapped.fields[0].name, "new field");

        // notetype with matching schema and original id exists => should be reused
        to_import.name = String::from("new name");
        to_import.mtime_secs.0 = remapped.mtime_secs.0 + 1;
        let out_2 = import_notetype!(&mut col, to_import);
        let remapped_id_2 = *out_2.remapped_notetypes.values().next().unwrap();
        assert_eq!(remapped_id, remapped_id_2);
        let updated = col.storage.get_notetype(remapped_id).unwrap().unwrap();
        assert_eq!(updated.name, "new name");
    }

    #[test]
    fn should_merge_notetype_fields() {
        let mut col = Collection::new();
        let mut to_import = col.basic_notetype();
        to_import.mtime_secs.0 += 1;
        to_import.fields.remove(0);
        to_import.fields[0].name = String::from("renamed");
        to_import.fields[0].ord.replace(0);
        to_import.fields.push(NoteField::new("new"));
        to_import.fields[1].ord.replace(1);

        let fields = import_notetype!(&mut col, to_import.clone(), merge = true).remapped_fields;
        // Front field is preserved and new field added
        assert!(col
            .basic_notetype()
            .field_names()
            .eq(["Front", "renamed", "new"]));
        // extra field must be inserted into incoming notes
        assert_eq!(
            fields.get(&to_import.id).unwrap(),
            &[None, Some(0), Some(1)]
        );
    }

    #[test]
    fn should_merge_notetype_templates() {
        let mut col = Collection::new();
        let mut to_import = col.basic_rev_notetype();
        to_import.mtime_secs.0 += 1;
        to_import.templates.remove(0);
        to_import.templates[0].name = String::from("renamed");
        to_import.templates[0].ord.replace(0);
        to_import.templates.push(CardTemplate::new("new", "", ""));
        to_import.templates[1].ord.replace(1);

        let templates =
            import_notetype!(&mut col, to_import.clone(), merge = true).remapped_templates;
        // Card 1 is preserved and new template added
        assert!(col
            .basic_rev_notetype()
            .template_names()
            .eq(["Card 1", "renamed", "new"]));
        // templates must be shifted accordingly
        let map = templates.get(&to_import.id).unwrap();
        assert_eq!(map.get(&0), Some(&1));
        assert_eq!(map.get(&1), Some(&2));
    }

    #[test]
    fn should_merge_notetype_duplicates_from_previous_imports() {
        let mut col = Collection::new();
        let mut incoming = col.basic_notetype();
        incoming.fields.push(NoteField::new("new incoming"));
        // simulate a notetype duplicated during previous import
        let mut remapped = col.basic_notetype();
        remapped.config.original_id.replace(incoming.id.0);
        // ... which was modified and has notes
        remapped.fields.push(NoteField::new("new remapped"));
        remapped.id.0 = 0;
        col.add_notetype_inner(&mut remapped, Usn(0), true).unwrap();
        let mut note = Note::new(&remapped);
        *note.fields_mut() = vec![
            String::from("front"),
            String::from("back"),
            String::from("new"),
        ];
        col.add_note(&mut note, DeckId(1)).unwrap();

        let ntid = incoming.id;
        import_notetype!(&mut col, incoming, merge = true);

        // both notetypes should have been merged into it
        assert!(col.get_notetype(ntid).unwrap().unwrap().field_names().eq([
            "Front",
            "Back",
            "new remapped",
            "new incoming",
        ]));
        assert!(col.get_all_notes()[0]
            .fields()
            .iter()
            .eq(["front", "back", "new", ""]))
    }

    #[test]
    fn reimport_with_merge_enabled_should_handle_duplicates() -> Result<()> {
        // import from src to dst
        let mut src = Collection::new();
        NoteAdder::basic(&mut src)
            .fields(&["foo", "bar"])
            .add(&mut src);
        let temp_dir = TempDir::new()?;
        let path = temp_dir.path().join("foo.apkg");
        src.export_apkg(&path, "", false, false, false, None)?;

        let mut dst = CollectionBuilder::new(temp_dir.path().join("dst.anki2"))
            .with_desktop_media_paths()
            .build()?;
        dst.import_apkg(&path, ImportAnkiPackageOptions::default())?;

        // add a field to src
        let mut nt = src.basic_notetype();
        nt.fields.push(NoteField::new("new incoming"));
        src.update_notetype(&mut nt, false)?;

        // importing again with merge enabled will fail, and add an empty notetype
        assert_eq!(dst.storage.get_all_notetype_names().unwrap().len(), 6);
        src.export_apkg(&path, "", false, false, false, None)?;
        assert_eq!(
            dst.import_apkg(&path, ImportAnkiPackageOptions::default())?
                .output
                .conflicting
                .len(),
            1
        );
        assert_eq!(dst.storage.get_all_notetype_names().unwrap().len(), 7);

        // if enabling merge, it should succeed and remove the empty notetype
        src.export_apkg(&path, "", false, false, false, None)?;
        assert_eq!(
            dst.import_apkg(
                &path,
                ImportAnkiPackageOptions {
                    merge_notetypes: true,
                    ..Default::default()
                }
            )?
            .output
            .conflicting
            .len(),
            0
        );
        assert_eq!(dst.storage.get_all_notetype_names().unwrap().len(), 6);

        Ok(())
    }
}
