// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashMap, fs::File, mem};

use zip::ZipArchive;

use super::Context;
use crate::{
    import_export::{
        package::media::{extract_media_entries, SafeMediaEntry},
        ImportProgress, IncrementableProgress,
    },
    media::{
        files::{add_hash_suffix_to_file_stem, sha1_of_reader},
        MediaManager,
    },
    prelude::*,
};

/// Map of source media files, that do not already exist in the target.
#[derive(Default)]
pub(super) struct MediaUseMap {
    /// original, normalized filename → (refererenced on import material,
    /// entry with possibly remapped filename)
    checked: HashMap<String, (bool, SafeMediaEntry)>,
    /// Static files (latex, underscored). Usage is not tracked, and if the name
    /// already exists in the target, it is skipped regardless of content equality.
    unchecked: Vec<SafeMediaEntry>,
}

impl Context<'_> {
    pub(super) fn prepare_media(&mut self) -> Result<MediaUseMap> {
        let media_entries = extract_media_entries(&self.meta, &mut self.archive)?;
        if media_entries.is_empty() {
            return Ok(MediaUseMap::default());
        }

        let db_progress_fn = self.progress.media_db_fn(ImportProgress::MediaCheck)?;
        let existing_sha1s = self.target_col.all_existing_sha1s(db_progress_fn)?;

        prepare_media(
            media_entries,
            &mut self.archive,
            &existing_sha1s,
            &mut self.progress,
        )
    }

    pub(super) fn copy_media(&mut self, media_map: &mut MediaUseMap) -> Result<()> {
        let mut incrementor = self.progress.incrementor(ImportProgress::Media);
        for entry in media_map.used_entries() {
            incrementor.increment()?;
            entry.copy_from_archive(&mut self.archive, &self.target_col.media_folder)?;
        }
        Ok(())
    }
}

impl Collection {
    fn all_existing_sha1s(
        &mut self,
        progress_fn: impl FnMut(usize) -> bool,
    ) -> Result<HashMap<String, Sha1Hash>> {
        let mgr = MediaManager::new(&self.media_folder, &self.media_db)?;
        mgr.all_checksums(progress_fn, &self.log)
    }
}

fn prepare_media(
    media_entries: Vec<SafeMediaEntry>,
    archive: &mut ZipArchive<File>,
    existing_sha1s: &HashMap<String, Sha1Hash>,
    progress: &mut IncrementableProgress<ImportProgress>,
) -> Result<MediaUseMap> {
    let mut media_map = MediaUseMap::default();
    let mut incrementor = progress.incrementor(ImportProgress::MediaCheck);

    for mut entry in media_entries {
        incrementor.increment()?;

        if entry.is_static() {
            if !existing_sha1s.contains_key(&entry.name) {
                media_map.unchecked.push(entry);
            }
        } else if let Some(other_sha1) = existing_sha1s.get(&entry.name) {
            entry.with_hash_from_archive(archive)?;
            if entry.sha1 != *other_sha1 {
                let original_name = entry.uniquify_name();
                media_map.add_checked(original_name, entry);
            }
        } else {
            media_map.add_checked(entry.name.clone(), entry);
        }
    }
    Ok(media_map)
}

impl MediaUseMap {
    pub(super) fn add_checked(&mut self, filename: impl Into<String>, entry: SafeMediaEntry) {
        self.checked.insert(filename.into(), (false, entry));
    }

    pub(super) fn use_entry(&mut self, filename: &str) -> Option<&SafeMediaEntry> {
        self.checked.get_mut(filename).map(|(used, entry)| {
            *used = true;
            &*entry
        })
    }

    pub(super) fn used_entries(&self) -> impl Iterator<Item = &SafeMediaEntry> {
        self.checked
            .values()
            .filter_map(|(used, entry)| used.then(|| entry))
            .chain(self.unchecked.iter())
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

    fn is_static(&self) -> bool {
        self.name.starts_with('_') || self.name.starts_with("latex-")
    }
}
