// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod cards;
mod decks;
mod notes;

use std::{collections::HashMap, fs::File, io, mem, path::Path};

pub(crate) use notes::NoteMeta;
use tempfile::NamedTempFile;
use zip::ZipArchive;

use crate::{
    collection::CollectionBuilder,
    import_export::{
        gather::ExchangeData,
        package::{
            media::{extract_media_entries, SafeMediaEntry},
            Meta,
        },
    },
    media::{
        files::{add_hash_suffix_to_file_stem, sha1_of_reader},
        MediaManager,
    },
    prelude::*,
    search::SearchNode,
};

/// Map of source media files, that do not already exist in the target.
///
/// original, normalized filename → (refererenced on import material,
/// entry with possibly remapped filename)
#[derive(Default)]
struct MediaUseMap(HashMap<String, (bool, SafeMediaEntry)>);

struct Context<'a> {
    target_col: &'a mut Collection,
    archive: ZipArchive<File>,
    data: ExchangeData,
    usn: Usn,
}

impl MediaUseMap {
    fn add(&mut self, filename: impl Into<String>, entry: SafeMediaEntry) {
        self.0.insert(filename.into(), (false, entry));
    }

    fn use_entry(&mut self, filename: &str) -> Option<&SafeMediaEntry> {
        self.0.get_mut(filename).map(|(used, entry)| {
            *used = true;
            &*entry
        })
    }

    fn used_entries(&self) -> impl Iterator<Item = &SafeMediaEntry> {
        self.0.values().filter_map(|t| t.0.then(|| &t.1))
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
    pub fn import_apkg(&mut self, path: impl AsRef<Path>) -> Result<OpOutput<()>> {
        let file = File::open(path)?;
        let archive = ZipArchive::new(file)?;

        self.transact(Op::Import, |col| {
            let mut ctx = Context::new(archive, col)?;
            ctx.import()
        })
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
    fn new(mut archive: ZipArchive<File>, target_col: &'a mut Collection) -> Result<Self> {
        let data =
            ExchangeData::gather_from_archive(&mut archive, SearchNode::WholeCollection, true)?;
        let usn = target_col.usn()?;
        Ok(Self {
            target_col,
            archive,
            data,
            usn,
        })
    }

    fn import(&mut self) -> Result<()> {
        let mut media_map = self.prepare_media()?;
        let imported_notes = self.import_notes_and_notetypes(&mut media_map)?;
        let imported_decks = self.import_decks_and_configs()?;
        self.import_cards_and_revlog(&imported_notes, &imported_decks)?;
        self.copy_media(&mut media_map)
    }

    fn prepare_media(&mut self) -> Result<MediaUseMap> {
        let mut media_map = MediaUseMap::default();
        let existing_sha1s = self.target_col.all_existing_sha1s()?;
        for mut entry in extract_media_entries(&Meta::new_legacy(), &mut self.archive)? {
            if let Some(other_sha1) = existing_sha1s.get(&entry.name) {
                entry.with_hash_from_archive(&mut self.archive)?;
                if entry.sha1 != *other_sha1 {
                    let original_name = entry.uniquify_name();
                    media_map.add(original_name, entry);
                }
            } else {
                media_map.add(entry.name.clone(), entry);
            }
        }
        Ok(media_map)
    }

    fn copy_media(&mut self, media_map: &mut MediaUseMap) -> Result<()> {
        for entry in media_map.used_entries() {
            entry.copy_from_archive(&mut self.archive, &self.target_col.media_folder)?;
        }
        Ok(())
    }
}
