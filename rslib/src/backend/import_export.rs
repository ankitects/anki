// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;

use anki_proto::generic;
use anki_proto::import_export::export_limit;
pub(super) use anki_proto::import_export::importexport_service::Service as ImportExportService;
use anki_proto::import_export::ExportLimit;

use super::Backend;
use crate::import_export::package::import_colpkg;
use crate::import_export::NoteLog;
use crate::prelude::*;
use crate::search::SearchNode;

impl ImportExportService for Backend {
    type Error = AnkiError;

    fn export_collection_package(
        &self,
        input: anki_proto::import_export::ExportCollectionPackageRequest,
    ) -> Result<()> {
        self.abort_media_sync_and_wait();

        let mut guard = self.lock_open_collection()?;

        let col_inner = guard.take().unwrap();
        col_inner
            .export_colpkg(input.out_path, input.include_media, input.legacy)
            .map(Into::into)
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
        .map(Into::into)
    }

    fn import_anki_package(
        &self,
        input: anki_proto::import_export::ImportAnkiPackageRequest,
    ) -> Result<anki_proto::import_export::ImportResponse> {
        self.with_col(|col| col.import_apkg(&input.package_path))
            .map(Into::into)
    }

    fn export_anki_package(
        &self,
        input: anki_proto::import_export::ExportAnkiPackageRequest,
    ) -> Result<generic::UInt32> {
        self.with_col(|col| {
            col.export_apkg(
                &input.out_path,
                SearchNode::from(input.limit.unwrap_or_default()),
                input.with_scheduling,
                input.with_media,
                input.legacy,
                None,
            )
        })
        .map(Into::into)
    }

    fn get_csv_metadata(
        &self,
        input: anki_proto::import_export::CsvMetadataRequest,
    ) -> Result<anki_proto::import_export::CsvMetadata> {
        let delimiter = input.delimiter.is_some().then(|| input.delimiter());
        self.with_col(|col| {
            col.get_csv_metadata(
                &input.path,
                delimiter,
                input.notetype_id.map(Into::into),
                input.deck_id.map(Into::into),
                input.is_html,
            )
        })
    }

    fn import_csv(
        &self,
        input: anki_proto::import_export::ImportCsvRequest,
    ) -> Result<anki_proto::import_export::ImportResponse> {
        self.with_col(|col| col.import_csv(&input.path, input.metadata.unwrap_or_default()))
            .map(Into::into)
    }

    fn export_note_csv(
        &self,
        input: anki_proto::import_export::ExportNoteCsvRequest,
    ) -> Result<generic::UInt32> {
        self.with_col(|col| col.export_note_csv(input))
            .map(Into::into)
    }

    fn export_card_csv(
        &self,
        input: anki_proto::import_export::ExportCardCsvRequest,
    ) -> Result<generic::UInt32> {
        self.with_col(|col| {
            col.export_card_csv(
                &input.out_path,
                SearchNode::from(input.limit.unwrap_or_default()),
                input.with_html,
            )
        })
        .map(Into::into)
    }

    fn import_json_file(
        &self,
        input: generic::String,
    ) -> Result<anki_proto::import_export::ImportResponse> {
        self.with_col(|col| col.import_json_file(&input.val))
            .map(Into::into)
    }

    fn import_json_string(
        &self,
        input: generic::String,
    ) -> Result<anki_proto::import_export::ImportResponse> {
        self.with_col(|col| col.import_json_string(&input.val))
            .map(Into::into)
    }
}

impl From<OpOutput<NoteLog>> for anki_proto::import_export::ImportResponse {
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
            .unwrap_or(Limit::WholeCollection(generic::Empty {}));
        match limit {
            Limit::WholeCollection(_) => Self::WholeCollection,
            Limit::DeckId(did) => Self::from_deck_id(did, true),
            Limit::NoteIds(nids) => Self::from_note_ids(nids.note_ids),
            Limit::CardIds(cids) => Self::from_card_ids(cids.cids),
        }
    }
}
