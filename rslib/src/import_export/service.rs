use std::path::Path;

// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::generic;
use anki_proto::import_export::import_response::Log as NoteLog;
use anki_proto::import_export::ExportLimit;
use anki_proto::import_export::ImportResponse;

use super::get_last_import_response;
use super::set_last_import_response;
use crate::collection::Collection;
use crate::error;
use crate::ops::OpOutput;
use crate::search::SearchNode;

impl crate::services::ImportExportService for Collection {
    fn import_anki_package(
        &mut self,
        input: anki_proto::import_export::ImportAnkiPackageRequest,
    ) -> error::Result<ImportResponse> {
        let response = self
            .import_apkg(&input.package_path)
            .map(|out| output_to_import_response(out, Some(input.package_path)));
        if let Ok(ref response) = response {
            set_last_import_response(&response);
        }

        response
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
    ) -> error::Result<ImportResponse> {
        let response = self
            .import_csv(&input.path, input.metadata.unwrap_or_default())
            .map(|out| output_to_import_response(out, Some(input.path)));
        if let Ok(ref response) = response {
            set_last_import_response(&response);
        }

        response
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

    fn import_json_file(&mut self, input: generic::String) -> error::Result<ImportResponse> {
        let response = self
            .import_json_file(&input.val)
            .map(|out| output_to_import_response(out, Some(input.val)));
        if let Ok(ref response) = response {
            set_last_import_response(&response);
        }

        response
    }

    fn import_json_string(&mut self, input: generic::String) -> error::Result<ImportResponse> {
        let response = self
            .import_json_string(&input.val)
            .map(|out| output_to_import_response(out, None));
        if let Ok(ref response) = response {
            set_last_import_response(&response);
        }

        response
    }

    fn get_last_import_response(&mut self) -> error::Result<ImportResponse> {
        Ok(get_last_import_response())
    }
}

fn output_to_import_response(output: OpOutput<NoteLog>, path: Option<String>) -> ImportResponse {
    let filename = if let Some(path) = path {
        Path::new(&path)
            .file_name()
            .map_or("", |s| s.to_str().unwrap_or_default())
            .to_string()
    } else {
        "".into()
    };
    ImportResponse {
        changes: Some(output.changes.into()),
        log: Some(output.output),
        filename,
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
