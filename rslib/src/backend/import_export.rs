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

#[cfg(test)]
mod tests {
    use anki_io::write_file;
    use anki_proto::backend::backend_error::Kind;
    use anki_proto::import_export::ImportCollectionPackageRequest;
    use tempfile::tempdir;

    use super::*;

    #[test]
    fn invalid_collection_package_returns_import_error() -> Result<()> {
        let dir = tempdir()?;
        let backup_path = dir.path().join("invalid.colpkg");
        write_file(&backup_path, b"not a zip archive")?;
        let backend = Backend::new(I18n::new(&["en"]), false);

        let error = backend
            .import_collection_package(ImportCollectionPackageRequest {
                col_path: dir
                    .path()
                    .join("collection.anki2")
                    .to_string_lossy()
                    .into_owned(),
                backup_path: backup_path.to_string_lossy().into_owned(),
                media_folder: dir
                    .path()
                    .join("collection.media")
                    .to_string_lossy()
                    .into_owned(),
                media_db: dir
                    .path()
                    .join("collection.media.db2")
                    .to_string_lossy()
                    .into_owned(),
            })
            .unwrap_err()
            .into_protobuf(backend.i18n());

        assert_eq!(error.kind(), Kind::ImportError);
        Ok(())
    }
}
