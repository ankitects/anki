// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;

use super::Backend;
use crate::import_export::package::import_colpkg;
use crate::prelude::*;
use crate::services::BackendImportExportService;

impl BackendImportExportService for Backend {
    fn export_collection_package(
        &self,
        input: anki_proto::import_export::ExportCollectionPackageRequest,
    ) -> Result<()> {
        self.abort_media_sync_and_wait();

        let mut guard = self.lock_open_collection()?;

        let col_inner = guard.take().unwrap();
        col_inner.export_colpkg(input.out_path, input.include_media, input.legacy)
    }

    fn import_collection_package(
        &self,
        input: anki_proto::import_export::ImportCollectionPackageRequest,
    ) -> Result<()> {
        let _guard = self.lock_closed_collection()?;

        import_colpkg(
            &input.backup_path,
            &input.col_path,
            Path::new(&input.media_folder),
            Path::new(&input.media_db),
            self.new_progress_handler(),
        )
    }
}
