// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::fs::File;
use std::mem;

use anki_io::FileIoSnafu;
use anki_io::FileOp;
use zip::ZipArchive;

use super::super::super::meta::MetaExt;
use super::Context;
use crate::import_export::package::media::extract_media_entries;
use crate::import_export::package::media::MediaCopier;
use crate::import_export::package::media::SafeMediaEntry;
use crate::import_export::ImportProgress;
use crate::media::files::add_hash_suffix_to_file_stem;
use crate::media::files::sha1_of_reader;
use crate::media::Checksums;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;

/// Map of source media files, that do not already exist in the target.
#[derive(Debug, Default)]
pub(super) struct MediaUseMap {
    /// original, normalized filename → (refererenced on import material,
    /// entry with possibly remapped filename)
    checked: HashMap<String, (bool, SafeMediaEntry)>,
    /// Static files (latex, underscored). Usage is not tracked, and if the name
    /// already exists in the target, it is skipped regardless of content
    /// equality.
    unchecked: Vec<SafeMediaEntry>,
}

impl Context<'_> {
    pub(super) fn prepare_media(&mut self) -> Result<MediaUseMap> {
        let media_entries = extract_media_entries(&self.meta, &mut self.archive)?;
        if media_entries.is_empty() {
            return Ok(MediaUseMap::default());
        }

        let db_progress_fn = self.progress.media_db_fn(ImportProgress::MediaCheck)?;
        let existing_sha1s = self
            .media_manager
            .all_checksums_after_checking(db_progress_fn)?;

        prepare_media(
            media_entries,
            &mut self.archive,
            &existing_sha1s,
            &mut self.progress,
        )
    }

    pub(super) fn copy_media(&mut self, media_map: &mut MediaUseMap) -> Result<()> {
        let mut incrementor = self.progress.incrementor(ImportProgress::Media);
        let mut copier = MediaCopier::new(false);
        self.media_manager.transact(|_db| {
            for entry in media_map.used_entries() {
                incrementor.increment()?;
                entry.copy_and_ensure_sha1_set(
                    &mut self.archive,
                    &self.target_col.media_folder,
                    &mut copier,
                    self.meta.zstd_compressed(),
                )?;
                self.media_manager
                    .add_entry(&entry.name, entry.sha1.unwrap())?;
            }
            Ok(())
        })
    }
}

fn prepare_media(
    media_entries: Vec<SafeMediaEntry>,
    archive: &mut ZipArchive<File>,
    existing_sha1s: &Checksums,
    progress: &mut ThrottlingProgressHandler<ImportProgress>,
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
            entry.ensure_sha1_set(archive)?;
            if entry.sha1.unwrap() != *other_sha1 {
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

    pub(super) fn used_entries(&mut self) -> impl Iterator<Item = &mut SafeMediaEntry> {
        self.checked
            .values_mut()
            .filter_map(|(used, entry)| used.then(|| entry))
            .chain(self.unchecked.iter_mut())
    }
}

impl SafeMediaEntry {
    fn ensure_sha1_set(&mut self, archive: &mut ZipArchive<File>) -> Result<()> {
        if self.sha1.is_none() {
            let mut reader = self.fetch_file(archive)?;
            self.sha1 = Some(sha1_of_reader(&mut reader).context(FileIoSnafu {
                path: &self.name,
                op: FileOp::Read,
            })?);
        }
        Ok(())
    }

    /// Requires sha1 to be set. Returns old file name.
    fn uniquify_name(&mut self) -> String {
        let new_name = add_hash_suffix_to_file_stem(&self.name, &self.sha1.expect("sha1 not set"));
        mem::replace(&mut self.name, new_name)
    }

    fn is_static(&self) -> bool {
        self.name.starts_with('_') || self.name.starts_with("latex-")
    }
}
