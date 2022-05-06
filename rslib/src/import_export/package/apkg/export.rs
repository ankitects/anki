// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::HashSet,
    path::{Path, PathBuf},
};

use tempfile::NamedTempFile;

use crate::{
    collection::CollectionBuilder,
    import_export::{
        gather::ExchangeData,
        package::{
            colpkg::export::{export_collection, MediaIter},
            Meta,
        },
        ExportProgress, IncrementableProgress,
    },
    io::{atomic_rename, tempfile_in_parent_of},
    prelude::*,
};

impl Collection {
    /// Returns number of exported notes.
    #[allow(clippy::too_many_arguments)]
    pub fn export_apkg(
        &mut self,
        out_path: impl AsRef<Path>,
        search: impl TryIntoSearch,
        with_scheduling: bool,
        with_media: bool,
        legacy: bool,
        media_fn: Option<Box<dyn FnOnce(HashSet<String>) -> MediaIter>>,
        progress_fn: impl 'static + FnMut(ExportProgress, bool) -> bool,
    ) -> Result<usize> {
        let mut progress = IncrementableProgress::new(progress_fn);
        progress.call(ExportProgress::File)?;
        let temp_apkg = tempfile_in_parent_of(out_path.as_ref())?;
        let mut temp_col = NamedTempFile::new()?;
        let temp_col_path = temp_col
            .path()
            .to_str()
            .ok_or_else(|| AnkiError::IoError("tempfile with non-unicode name".into()))?;
        let meta = if legacy {
            Meta::new_legacy()
        } else {
            Meta::new()
        };
        let data = self.export_into_collection_file(
            &meta,
            temp_col_path,
            search,
            &mut progress,
            with_scheduling,
            with_media,
        )?;

        progress.call(ExportProgress::File)?;
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
        search: impl TryIntoSearch,
        progress: &mut IncrementableProgress<ExportProgress>,
        with_scheduling: bool,
        with_media: bool,
    ) -> Result<ExchangeData> {
        let mut data = ExchangeData::default();
        progress.call(ExportProgress::Gathering)?;
        data.gather_data(self, search, with_scheduling)?;
        if with_media {
            data.gather_media_names(progress)?;
        }

        let mut temp_col = Collection::new_minimal(path)?;
        progress.call(ExportProgress::File)?;
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
