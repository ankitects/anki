// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashMap, fs::File, mem};

use zip::ZipArchive;

use super::Context;
use crate::{
    import_export::package::{
        media::{extract_media_entries, SafeMediaEntry},
        Meta,
    },
    media::{
        files::{add_hash_suffix_to_file_stem, sha1_of_reader},
        MediaManager,
    },
    prelude::*,
};

/// Map of source media files, that do not already exist in the target.
///
/// original, normalized filename → (refererenced on import material,
/// entry with possibly remapped filename)
#[derive(Default)]
pub(super) struct MediaUseMap(HashMap<String, (bool, SafeMediaEntry)>);

impl<'a> Context<'a> {
    pub(super) fn prepare_media(&mut self) -> Result<MediaUseMap> {
        let existing_sha1s = self.target_col.all_existing_sha1s()?;
        prepare_media(&mut self.archive, &existing_sha1s)
    }

    pub(super) fn copy_media(&mut self, media_map: &mut MediaUseMap) -> Result<()> {
        for entry in media_map.used_entries() {
            entry.copy_from_archive(&mut self.archive, &self.target_col.media_folder)?;
        }
        Ok(())
    }
}

impl Collection {
    fn all_existing_sha1s(&mut self) -> Result<HashMap<String, [u8; 20]>> {
        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        mgr.all_checksums(|_| true, &self.log)
    }
}

fn prepare_media(
    archive: &mut ZipArchive<File>,
    existing_sha1s: &HashMap<String, [u8; 20]>,
) -> Result<MediaUseMap> {
    let mut media_map = MediaUseMap::default();
    for mut entry in extract_media_entries(&Meta::new_legacy(), archive)? {
        if let Some(other_sha1) = existing_sha1s.get(&entry.name) {
            entry.with_hash_from_archive(archive)?;
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

impl MediaUseMap {
    pub(super) fn add(&mut self, filename: impl Into<String>, entry: SafeMediaEntry) {
        self.0.insert(filename.into(), (false, entry));
    }

    pub(super) fn use_entry(&mut self, filename: &str) -> Option<&SafeMediaEntry> {
        self.0.get_mut(filename).map(|(used, entry)| {
            *used = true;
            &*entry
        })
    }

    pub(super) fn used_entries(&self) -> impl Iterator<Item = &SafeMediaEntry> {
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
