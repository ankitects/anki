// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::generic;
use anki_proto::import_export::import_response::Log as NoteLog;
use anki_proto::import_export::ExportLimit;

use crate::collection::Collection;
use crate::error;
use crate::ops::OpOutput;
use crate::search::SearchNode;

impl crate::services::ImportExportService for Collection {
    fn import_anki_package(
        &mut self,
        input: anki_proto::import_export::ImportAnkiPackageRequest,
    ) -> error::Result<anki_proto::import_export::ImportResponse> {
        self.import_apkg(&input.package_path).map(Into::into)
    }

    fn export_anki_package(
        &mut self,
        input: anki_proto::import_export::ExportAnkiPackageRequest,
    ) -> error::Result<generic::UInt32> {
        self.export_apkg(
            &input.out_path,
            SearchNode::from(input.limit.unwrap_or_default()),
            input.with_scheduling,
            input.with_media,
            input.legacy,
            None,
        )
        .map(Into::into)
    }

    fn get_csv_metadata(
        &mut self,
        input: anki_proto::import_export::CsvMetadataRequest,
    ) -> error::Result<anki_proto::import_export::CsvMetadata> {
        let delimiter = input.delimiter.is_some().then(|| input.delimiter());

        self.get_csv_metadata(
            &input.path,
            delimiter,
            input.notetype_id.map(Into::into),
            input.deck_id.map(Into::into),
            input.is_html,
        )
    }

    fn import_csv(
        &mut self,
        input: anki_proto::import_export::ImportCsvRequest,
    ) -> error::Result<anki_proto::import_export::ImportResponse> {
        self.import_csv(&input.path, input.metadata.unwrap_or_default())
            .map(Into::into)
    }

    fn export_note_csv(
        &mut self,
        input: anki_proto::import_export::ExportNoteCsvRequest,
    ) -> error::Result<generic::UInt32> {
        self.export_note_csv(input).map(Into::into)
    }

    fn export_card_csv(
        &mut self,
        input: anki_proto::import_export::ExportCardCsvRequest,
    ) -> error::Result<generic::UInt32> {
        self.export_card_csv(
            &input.out_path,
            SearchNode::from(input.limit.unwrap_or_default()),
            input.with_html,
        )
        .map(Into::into)
    }

    fn import_json_file(
        &mut self,
        input: generic::String,
    ) -> error::Result<anki_proto::import_export::ImportResponse> {
        self.import_json_file(&input.val).map(Into::into)
    }

    fn import_json_string(
        &mut self,
        input: generic::String,
    ) -> error::Result<anki_proto::import_export::ImportResponse> {
        self.import_json_string(&input.val).map(Into::into)
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
        use anki_proto::import_export::export_limit::Limit;
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
