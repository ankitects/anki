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
    },
    io::{atomic_rename, tempfile_in_parent_of},
    prelude::*,
    storage::SchemaVersion,
};

impl Collection {
    pub fn export_apkg(
        &mut self,
        out_path: impl AsRef<Path>,
        search: impl TryIntoSearch,
        with_scheduling: bool,
        with_media: bool,
        media_fn: Option<Box<dyn FnOnce(HashSet<PathBuf>) -> MediaIter>>,
        progress_fn: impl FnMut(usize),
    ) -> Result<()> {
        let temp_apkg = tempfile_in_parent_of(out_path.as_ref())?;
        let mut temp_col = NamedTempFile::new()?;
        let temp_col_path = temp_col
            .path()
            .to_str()
            .ok_or_else(|| AnkiError::IoError("tempfile with non-unicode name".into()))?;
        let media = self.export_collection_extracting_media(
            temp_col_path,
            search,
            with_scheduling,
            with_media,
        )?;
        let media = if let Some(media_fn) = media_fn {
            media_fn(media)
        } else {
            MediaIter::from_file_list(media, self.media_folder.clone())
        };
        let col_size = temp_col.as_file().metadata()?.len() as usize;

        export_collection(
            Meta::new_legacy(),
            temp_apkg.path(),
            &mut temp_col,
            col_size,
            media,
            &self.tr,
            progress_fn,
        )?;
        atomic_rename(temp_apkg, out_path.as_ref(), true)
    }

    fn export_collection_extracting_media(
        &mut self,
        path: &str,
        search: impl TryIntoSearch,
        with_scheduling: bool,
        with_media: bool,
    ) -> Result<HashSet<PathBuf>> {
        let mut data = ExchangeData::default();
        data.gather_data(self, search, with_scheduling)?;
        if with_media {
            data.gather_media_paths();
        }

        let mut temp_col = Collection::new_minimal(path)?;
        temp_col.insert_data(&data)?;
        temp_col.set_creation_stamp(self.storage.creation_stamp()?)?;
        temp_col.close(Some(SchemaVersion::V11))?;

        Ok(data.media_paths)
    }

    fn new_minimal(path: impl Into<PathBuf>) -> Result<Self> {
        let col = CollectionBuilder::new(path).build()?;
        col.storage.db.execute_batch("DELETE FROM notetypes")?;
        Ok(col)
    }
}
