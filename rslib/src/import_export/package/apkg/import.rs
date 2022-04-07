// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    borrow::Cow,
    collections::{HashMap, HashSet},
    fs::File,
    io::{self},
    mem,
};

use sha1::Sha1;
use tempfile::NamedTempFile;
use zip::ZipArchive;

use crate::{
    collection::CollectionBuilder,
    import_export::{
        gather::ExchangeData,
        package::{
            media::{extract_media_entries, safe_normalized_file_name, SafeMediaEntry},
            Meta,
        },
    },
    media::{
        files::{add_hash_suffix_to_file_stem, sha1_of_reader},
        MediaManager,
    },
    prelude::*,
    text::replace_media_refs,
};

struct Context<'a> {
    target_col: &'a mut Collection,
    archive: ZipArchive<File>,
    guid_map: HashMap<String, NoteMeta>,
    remapped_notetypes: HashMap<NotetypeId, NotetypeId>,
    existing_notes: HashSet<NoteId>,
    data: ExchangeData,
    usn: Usn,
    /// Map of source media files, that do not already exist in the target.
    ///
    /// original, normalized file name → (refererenced on import material,
    /// entry with possibly remapped file name)
    used_media_entries: HashMap<String, (bool, SafeMediaEntry)>,
    conflicting_notes: HashSet<String>,
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

    fn from_note(note: &Note) -> Self {
        Self::new(note.id, note.mtime, note.notetype_id)
    }
}

impl SafeMediaEntry {
    fn with_hash_from_archive(&mut self, archive: &mut ZipArchive<File>) -> Result<()> {
        if self.sha1 == [0; 20] {
            let mut reader = self.fetch_file(archive)?;
            self.sha1 = sha1_of_reader(&mut reader)?;
        }
        Ok(())
    }

    /// Requires sha1 to be set. Returns old file name.
    fn uniquify_name(&mut self) -> String {
        let new_name = add_hash_suffix_to_file_stem(&self.name, &self.sha1);
        mem::replace(&mut self.name, new_name)
    }
}

impl Collection {
    pub fn import_apkg(
        &mut self,
        path: &str,
        search: impl TryIntoSearch,
        with_scheduling: bool,
    ) -> Result<()> {
        let file = File::open(path)?;
        let archive = ZipArchive::new(file)?;

        let mut ctx = Context::new(archive, self, search, with_scheduling)?;
        ctx.prepare_media()?;
        ctx.prepare_notetypes()?;
        ctx.prepare_notes()?;

        ctx.target_col.insert_data(&ctx.data)?;
        ctx.copy_media()?;
        Ok(())
    }

    fn all_existing_sha1s(&mut self) -> Result<HashMap<String, [u8; 20]>> {
        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        mgr.all_checksums(|_| true, &self.log)
    }
}

impl ExchangeData {
    fn gather_from_archive(
        archive: &mut ZipArchive<File>,
        search: impl TryIntoSearch,
        with_scheduling: bool,
    ) -> Result<Self> {
        let mut zip_file = archive.by_name(Meta::new_legacy().collection_filename())?;
        let mut tempfile = NamedTempFile::new()?;
        io::copy(&mut zip_file, &mut tempfile)?;
        let mut col = CollectionBuilder::new(tempfile.path()).build()?;

        let mut data = ExchangeData::default();
        data.gather_data(&mut col, search, with_scheduling)?;

        Ok(data)
    }
}

impl<'a> Context<'a> {
    fn new(
        mut archive: ZipArchive<File>,
        target_col: &'a mut Collection,
        search: impl TryIntoSearch,
        with_scheduling: bool,
    ) -> Result<Self> {
        let data = ExchangeData::gather_from_archive(&mut archive, search, with_scheduling)?;
        let guid_map = target_col.storage.note_guid_map()?;
        let existing_notes = target_col.storage.get_all_note_ids()?;
        let usn = target_col.usn()?;
        Ok(Self {
            target_col,
            archive,
            data,
            guid_map,
            existing_notes,
            usn,
            conflicting_notes: HashSet::new(),
            remapped_notetypes: HashMap::new(),
            used_media_entries: HashMap::new(),
        })
    }

    fn prepare_media(&mut self) -> Result<()> {
        let existing_sha1s = self.target_col.all_existing_sha1s()?;
        for mut entry in extract_media_entries(&Meta::new_legacy(), &mut self.archive)? {
            if let Some(other_sha1) = existing_sha1s.get(&entry.name) {
                entry.with_hash_from_archive(&mut self.archive)?;
                if entry.sha1 != *other_sha1 {
                    let original_name = entry.uniquify_name();
                    self.used_media_entries
                        .insert(original_name, (false, entry));
                }
            } else {
                self.used_media_entries
                    .insert(entry.name.clone(), (false, entry));
            }
        }
        Ok(())
    }

    fn prepare_notetypes(&mut self) -> Result<()> {
        for mut notetype in std::mem::take(&mut self.data.notetypes) {
            if let Some(existing) = self.target_col.storage.get_notetype(notetype.id)? {
                self.merge_or_remap_notetype(&mut notetype, existing)?;
            } else {
                self.add_notetype(&mut notetype)?;
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
        notetype.usn = self.usn;
        // TODO: make undoable
        self.target_col
            .add_or_update_notetype_with_existing_id_inner(notetype, None, self.usn, true)
    }

    fn update_notetype(&mut self, notetype: &mut Notetype, original: Notetype) -> Result<()> {
        notetype.usn = self.usn;
        // TODO: make undoable
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

    fn prepare_notes(&mut self) -> Result<()> {
        for mut note in mem::take(&mut self.data.notes) {
            if let Some(notetype_id) = self.remapped_notetypes.get(&note.notetype_id) {
                if self.guid_map.contains_key(&note.guid) {
                    todo!("ignore");
                } else {
                    note.notetype_id = *notetype_id;
                    self.prepare_new_note(note)?;
                }
            } else if let Some(&meta) = self.guid_map.get(&note.guid) {
                self.prepare_existing_note(note, meta)?;
            } else {
                self.prepare_new_note(note)?;
            }
        }
        Ok(())
    }

    fn add_prepared_note(&mut self, mut note: Note) -> Result<()> {
        self.munge_media(&mut note)?;
        note.usn = self.usn;
        self.data.notes.push(note);
        Ok(())
    }

    fn prepare_new_note(&mut self, mut note: Note) -> Result<()> {
        self.to_next_available_note_id(&mut note.id);
        self.existing_notes.insert(note.id);
        self.guid_map
            .insert(note.guid.clone(), NoteMeta::from_note(&note));
        self.add_prepared_note(note)
        // TODO: Log add
    }

    fn prepare_existing_note(&mut self, mut note: Note, meta: NoteMeta) -> Result<()> {
        if meta.mtime < note.mtime {
            if meta.notetype_id == note.notetype_id {
                note.id = meta.id;
                self.add_prepared_note(note)?;
                // TODO: Log update
            } else {
                self.conflicting_notes.insert(note.guid);
                // TODO: Log ignore
            }
        } else {
            // TODO: Log duplicate
        }
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

    fn replace_media_refs(&mut self, field: &mut String) -> Option<String> {
        replace_media_refs(field, |name| {
            if let Ok(normalized) = safe_normalized_file_name(name) {
                if let Some((used, entry)) = self.used_media_entries.get_mut(normalized.as_ref()) {
                    *used = true;
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

    fn to_next_available_note_id(&self, note_id: &mut NoteId) {
        while self.existing_notes.contains(note_id) {
            note_id.0 += 999;
        }
    }

    fn copy_media(&mut self) -> Result<()> {
        for (used, entry) in self.used_media_entries.values() {
            if *used {
                entry.copy_from_archive(&mut self.archive, &self.target_col.media_folder)?;
            }
        }
        Ok(())
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
