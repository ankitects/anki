// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::{HashMap, HashSet},
    fs::{self, File},
    io::{self, Write},
    mem,
    path::{Path, PathBuf},
    sync::Arc,
};

use sha1::Sha1;
use tempfile::NamedTempFile;
use zip::ZipArchive;

use crate::{
    collection::CollectionBuilder,
    import_export::{
        gather::ExchangeData,
        package::{media::extract_media_entries, Meta},
    },
    io::{atomic_rename, tempfile_in_parent_of},
    prelude::*,
    text::replace_media_refs,
};

#[derive(Debug)]
struct Context {
    archive: ZipArchive<File>,
    guid_map: HashMap<String, NoteMeta>,
    remapped_notetypes: HashMap<NotetypeId, NotetypeId>,
    existing_notes: HashSet<NoteId>,
    existing_notetypes: HashSet<NotetypeId>,
    data: ExchangeData,
    usn: Usn,
    media_map: HashMap<String, String>,
    target_media_folder: PathBuf,
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
        ctx.prepare_notetypes(self)?;
        ctx.prepare_notes()?;

        self.insert_data(&ctx.data)
    }
}

fn build_media_map(archive: &mut ZipArchive<File>) -> Result<HashMap<String, String>> {
    Ok(extract_media_entries(&Meta::new_legacy(), archive)?
        .into_iter()
        .map(|entry| (entry.name, entry.index.to_string()))
        .collect())
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

impl Context {
    fn new(
        mut archive: ZipArchive<File>,
        target_col: &mut Collection,
        search: impl TryIntoSearch,
        with_scheduling: bool,
    ) -> Result<Self> {
        let data = ExchangeData::gather_from_archive(&mut archive, search, with_scheduling)?;
        let media_map = build_media_map(&mut archive)?;
        Ok(Self {
            archive,
            data,
            guid_map: target_col.storage.note_guid_map()?,
            existing_notes: target_col.storage.get_all_note_ids()?,
            existing_notetypes: target_col.storage.get_all_notetype_ids()?,
            media_map,
            target_media_folder: target_col.media_folder.clone(),
            usn: target_col.usn()?,
            conflicting_notes: HashSet::new(),
            remapped_notetypes: HashMap::new(),
        })
    }

    fn prepare_notetypes(&mut self, target_col: &mut Collection) -> Result<()> {
        for notetype in mem::take(&mut self.data.notetypes) {
            if let Some(existing) = target_col.get_notetype(notetype.id)? {
                self.merge_or_remap_notetype(notetype, existing)?;
            } else {
                self.add_notetype(notetype);
            }
        }
        Ok(())
    }

    fn add_notetype(&mut self, mut notetype: Notetype) {
        self.existing_notetypes.insert(notetype.id);
        notetype.usn = self.usn;
        self.data.notetypes.push(notetype);
    }

    fn merge_or_remap_notetype(
        &mut self,
        incoming: Notetype,
        existing: Arc<Notetype>,
    ) -> Result<()> {
        if incoming.schema_hash() == existing.schema_hash() {
            self.add_notetype_if_newer(incoming, existing);
        } else {
            self.add_notetype_with_new_id(incoming)?;
        }
        Ok(())
    }

    fn add_notetype_with_new_id(&mut self, mut notetype: Notetype) -> Result<()> {
        let new_id = self.next_available_notetype_id();
        self.remapped_notetypes.insert(notetype.id, new_id);
        notetype.id = new_id;
        self.add_notetype(notetype);
        Ok(())
    }

    fn next_available_notetype_id(&self) -> NotetypeId {
        let mut next_id = NotetypeId(TimestampMillis::now().0);
        while self.existing_notetypes.contains(&next_id) {
            next_id.0 += 1;
        }
        next_id
    }

    fn add_notetype_if_newer(&mut self, incoming: Notetype, existing: Arc<Notetype>) {
        if incoming.mtime_secs > existing.mtime_secs {
            self.add_notetype(incoming);
        }
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
        let notetype_id = note.notetype_id;
        for field in note.fields_mut() {
            if let Some(new_field) = self.replace_media_refs_fallible(field, notetype_id)? {
                *field = new_field;
            };
        }
        Ok(())
    }

    fn replace_media_refs_fallible(
        &mut self,
        field: &mut String,
        notetype_id: NotetypeId,
    ) -> Result<Option<String>> {
        let mut res = Ok(());
        let out = replace_media_refs(field, |name| {
            if res.is_err() {
                None
            } else {
                self.merge_media_maybe_renaming(name, notetype_id)
                    .unwrap_or_else(|err| {
                        res = Err(err);
                        None
                    })
            }
        });
        res.map(|_| out)
    }

    fn merge_media_maybe_renaming(
        &mut self,
        name: &str,
        notetype: NotetypeId,
    ) -> Result<Option<String>> {
        Ok(if let Some(zip_name) = self.media_map.get(name) {
            let alternate_name = alternate_media_name(name, notetype);
            let alternate_path = self.target_media_folder.join(&alternate_name);
            if alternate_path.exists() {
                Some(alternate_name)
            } else {
                let mut data = Vec::new();
                io::copy(&mut self.archive.by_name(zip_name)?, &mut data)?;
                let target_path = self.target_media_folder.join(name);
                if !target_path.exists() {
                    write_data_atomically(&data, &target_path)?;
                    None
                } else if data == fs::read(target_path)? {
                    None
                } else {
                    write_data_atomically(&data, &alternate_path)?;
                    Some(alternate_name)
                }
            }
        } else {
            None
        })
    }

    fn to_next_available_note_id(&self, note_id: &mut NoteId) {
        while self.existing_notes.contains(note_id) {
            note_id.0 += 999;
        }
    }
}

fn write_data_atomically(data: &[u8], path: &Path) -> Result<()> {
    let mut tempfile = tempfile_in_parent_of(path)?;
    tempfile.write_all(data)?;
    atomic_rename(tempfile, path, false)
}

fn alternate_media_name(name: &str, notetype_id: NotetypeId) -> String {
    let (stem, dot, extension) = name
        .rsplit_once('.')
        .map(|(stem, ext)| (stem, ".", ext))
        .unwrap_or((name, "", ""));
    format!("{stem}_{notetype_id}{dot}{extension}")
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
