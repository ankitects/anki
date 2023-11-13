// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashSet;
use std::path::Path;
use std::path::PathBuf;

use anki_io::atomic_rename;
use anki_io::new_tempfile;
use anki_io::new_tempfile_in_parent_of;

use super::super::meta::MetaExt;
use crate::collection::CollectionBuilder;
use crate::import_export::gather::ExchangeData;
use crate::import_export::package::colpkg::export::export_collection;
use crate::import_export::package::media::MediaIter;
use crate::import_export::package::ExportAnkiPackageOptions;
use crate::import_export::package::Meta;
use crate::import_export::ExportProgress;
use crate::prelude::*;
use crate::progress::ThrottlingProgressHandler;

impl Collection {
    /// Returns number of exported notes.
    pub fn export_apkg(
        &mut self,
        out_path: impl AsRef<Path>,
        options: ExportAnkiPackageOptions,
        search: impl TryIntoSearch,
        media_fn: Option<Box<dyn FnOnce(HashSet<String>) -> MediaIter>>,
    ) -> Result<usize> {
        let mut progress = self.new_progress_handler();
        let temp_apkg = new_tempfile_in_parent_of(out_path.as_ref())?;
        let mut temp_col = new_tempfile()?;
        let temp_col_path = temp_col
            .path()
            .to_str()
            .or_invalid("non-unicode filename")?;
        let meta = if options.legacy {
            Meta::new_legacy()
        } else {
            Meta::new()
        };
        let data =
            self.export_into_collection_file(&meta, temp_col_path, options, search, &mut progress)?;

        progress.set(ExportProgress::File)?;
        let media = if let Some(media_fn) = media_fn {
            media_fn(data.media_filenames)
        } else {
            MediaIter::from_file_list(data.media_filenames, self.media_folder.clone())
        };
        let col_size = temp_col.as_file().metadata()?.len() as usize;

        export_collection(
            meta,
            temp_apkg.path(),
            &mut temp_col,
            col_size,
            media,
            &self.tr,
            &mut progress,
        )?;
        atomic_rename(temp_apkg, out_path.as_ref(), true)?;
        Ok(data.notes.len())
    }

    fn export_into_collection_file(
        &mut self,
        meta: &Meta,
        path: &str,
        options: ExportAnkiPackageOptions,
        search: impl TryIntoSearch,
        progress: &mut ThrottlingProgressHandler<ExportProgress>,
    ) -> Result<ExchangeData> {
        let mut data = ExchangeData::default();
        progress.set(ExportProgress::Gathering)?;
        data.gather_data(
            self,
            search,
            options.with_scheduling,
            options.with_deck_configs,
        )?;
        if options.with_media {
            data.gather_media_names(progress)?;
        }

        let mut temp_col = Collection::new_minimal(path)?;
        progress.set(ExportProgress::File)?;
        temp_col.insert_data(&data)?;
        temp_col.set_creation_stamp(self.storage.creation_stamp()?)?;
        temp_col.set_creation_utc_offset(data.creation_utc_offset)?;
        temp_col.close(Some(meta.schema_version()))?;

        Ok(data)
    }

    fn new_minimal(path: impl Into<PathBuf>) -> Result<Self> {
        let col = CollectionBuilder::new(path).build()?;
        col.storage.db.execute_batch("DELETE FROM notetypes")?;
        Ok(col)
    }
}
