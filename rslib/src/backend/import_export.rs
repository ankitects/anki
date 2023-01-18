// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;

use super::progress::Progress;
use super::Backend;
use crate::import_export::package::import_colpkg;
use crate::import_export::ExportProgress;
use crate::import_export::ImportProgress;
use crate::import_export::NoteLog;
use crate::pb;
use crate::pb::import_export::export_limit;
pub(super) use crate::pb::import_export::importexport_service::Service as ImportExportService;
use crate::pb::import_export::ExportLimit;
use crate::prelude::*;
use crate::search::SearchNode;

impl ImportExportService for Backend {
    fn export_collection_package(
        &self,
        input: pb::import_export::ExportCollectionPackageRequest,
    ) -> Result<pb::generic::Empty> {
        self.abort_media_sync_and_wait();

        let mut guard = self.lock_open_collection()?;

        let col_inner = guard.take().unwrap();
        col_inner
            .export_colpkg(
                input.out_path,
                input.include_media,
                input.legacy,
                self.export_progress_fn(),
            )
            .map(Into::into)
    }

    fn import_collection_package(
        &self,
        input: pb::import_export::ImportCollectionPackageRequest,
    ) -> Result<pb::generic::Empty> {
        let _guard = self.lock_closed_collection()?;

        import_colpkg(
            &input.backup_path,
            &input.col_path,
            Path::new(&input.media_folder),
            Path::new(&input.media_db),
            self.import_progress_fn(),
        )
        .map(Into::into)
    }

    fn import_anki_package(
        &self,
        input: pb::import_export::ImportAnkiPackageRequest,
    ) -> Result<pb::import_export::ImportResponse> {
        self.with_col(|col| col.import_apkg(&input.package_path, self.import_progress_fn()))
            .map(Into::into)
    }

    fn export_anki_package(
        &self,
        input: pb::import_export::ExportAnkiPackageRequest,
    ) -> Result<pb::generic::UInt32> {
        self.with_col(|col| {
            col.export_apkg(
                &input.out_path,
                SearchNode::from(input.limit.unwrap_or_default()),
                input.with_scheduling,
                input.with_media,
                input.legacy,
                None,
                self.export_progress_fn(),
            )
        })
        .map(Into::into)
    }

    fn get_csv_metadata(
        &self,
        input: pb::import_export::CsvMetadataRequest,
    ) -> Result<pb::import_export::CsvMetadata> {
        let delimiter = input.delimiter.is_some().then(|| input.delimiter());
        self.with_col(|col| {
            col.get_csv_metadata(
                &input.path,
                delimiter,
                input.notetype_id.map(Into::into),
                input.is_html,
            )
        })
    }

    fn import_csv(
        &self,
        input: pb::import_export::ImportCsvRequest,
    ) -> Result<pb::import_export::ImportResponse> {
        self.with_col(|col| {
            col.import_csv(
                &input.path,
                input.metadata.unwrap_or_default(),
                self.import_progress_fn(),
            )
        })
        .map(Into::into)
    }

    fn export_note_csv(
        &self,
        input: pb::import_export::ExportNoteCsvRequest,
    ) -> Result<pb::generic::UInt32> {
        self.with_col(|col| col.export_note_csv(input, self.export_progress_fn()))
            .map(Into::into)
    }

    fn export_card_csv(
        &self,
        input: pb::import_export::ExportCardCsvRequest,
    ) -> Result<pb::generic::UInt32> {
        self.with_col(|col| {
            col.export_card_csv(
                &input.out_path,
                SearchNode::from(input.limit.unwrap_or_default()),
                input.with_html,
                self.export_progress_fn(),
            )
        })
        .map(Into::into)
    }

    fn import_json_file(
        &self,
        input: pb::generic::String,
    ) -> Result<pb::import_export::ImportResponse> {
        self.with_col(|col| col.import_json_file(&input.val, self.import_progress_fn()))
            .map(Into::into)
    }

    fn import_json_string(
        &self,
        input: pb::generic::String,
    ) -> Result<pb::import_export::ImportResponse> {
        self.with_col(|col| col.import_json_string(&input.val, self.import_progress_fn()))
            .map(Into::into)
    }
}

impl Backend {
    fn import_progress_fn(&self) -> impl FnMut(ImportProgress, bool) -> bool {
        let mut handler = self.new_progress_handler();
        move |progress, throttle| handler.update(Progress::Import(progress), throttle)
    }

    fn export_progress_fn(&self) -> impl FnMut(ExportProgress, bool) -> bool {
        let mut handler = self.new_progress_handler();
        move |progress, throttle| handler.update(Progress::Export(progress), throttle)
    }
}

impl From<OpOutput<NoteLog>> for pb::import_export::ImportResponse {
    fn from(output: OpOutput<NoteLog>) -> Self {
        Self {
            changes: Some(output.changes.into()),
            log: Some(output.output),
        }
    }
}

impl From<ExportLimit> for SearchNode {
    fn from(export_limit: ExportLimit) -> Self {
        use export_limit::Limit;
        let limit = export_limit
            .limit
            .unwrap_or(Limit::WholeCollection(pb::generic::Empty {}));
        match limit {
            Limit::WholeCollection(_) => Self::WholeCollection,
            Limit::DeckId(did) => Self::from_deck_id(did, true),
            Limit::NoteIds(nids) => Self::from_note_ids(nids.note_ids),
            Limit::CardIds(cids) => Self::from_card_ids(cids.cids),
        }
    }
}
