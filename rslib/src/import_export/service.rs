// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::generic;
use anki_proto::import_export::import_response::Log as NoteLog;
use anki_proto::import_export::ExportLimit;

use crate::prelude::*;
use crate::search::SearchNode;

impl crate::services::ImportExportService for Collection {
    fn import_anki_package(
        &mut self,
        input: anki_proto::import_export::ImportAnkiPackageRequest,
    ) -> Result<anki_proto::import_export::ImportResponse> {
        self.import_apkg(&input.package_path, input.options.unwrap_or_default())
            .map(Into::into)
    }

    fn get_import_anki_package_presets(
        &mut self,
    ) -> Result<anki_proto::import_export::ImportAnkiPackageOptions> {
        Ok(anki_proto::import_export::ImportAnkiPackageOptions {
            merge_notetypes: self.get_config_bool(BoolKey::MergeNotetypes),
            with_scheduling: self.get_config_bool(BoolKey::WithScheduling),
            with_deck_configs: self.get_config_bool(BoolKey::WithDeckConfigs),
            update_notes: self.get_update_notes() as i32,
            update_notetypes: self.get_update_notetypes() as i32,
        })
    }

    fn export_anki_package(
        &mut self,
        input: anki_proto::import_export::ExportAnkiPackageRequest,
    ) -> Result<generic::UInt32> {
        self.export_apkg(
            &input.out_path,
            input.options.unwrap_or_default(),
            input.limit.unwrap_or_default(),
            None,
        )
        .map(Into::into)
    }

    fn get_csv_metadata(
        &mut self,
        input: anki_proto::import_export::CsvMetadataRequest,
    ) -> Result<anki_proto::import_export::CsvMetadata> {
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
    ) -> Result<anki_proto::import_export::ImportResponse> {
        self.import_csv(&input.path, input.metadata.unwrap_or_default())
            .map(Into::into)
    }

    fn export_note_csv(
        &mut self,
        input: anki_proto::import_export::ExportNoteCsvRequest,
    ) -> Result<generic::UInt32> {
        self.export_note_csv(input).map(Into::into)
    }

    fn export_card_csv(
        &mut self,
        input: anki_proto::import_export::ExportCardCsvRequest,
    ) -> Result<generic::UInt32> {
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
    ) -> Result<anki_proto::import_export::ImportResponse> {
        self.import_json_file(&input.val).map(Into::into)
    }

    fn import_json_string(
        &mut self,
        input: generic::String,
    ) -> Result<anki_proto::import_export::ImportResponse> {
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

#[cfg(test)]
mod tests {
    use anki_proto::generic;
    use anki_proto::import_export::export_limit::Limit;
    use anki_proto::import_export::import_response::Log as NoteLog;
    use anki_proto::import_export::CsvMetadataRequest;
    use anki_proto::import_export::ExportAnkiPackageRequest;
    use anki_proto::import_export::ExportCardCsvRequest;
    use anki_proto::import_export::ExportLimit;
    use anki_proto::import_export::ExportNoteCsvRequest;
    use anki_proto::import_export::ImportAnkiPackageRequest;
    use anki_proto::import_export::ImportCsvRequest;

    use super::*;
    use crate::ops::Op;
    use crate::ops::OpChanges;
    use crate::ops::OpOutput;
    use crate::ops::StateChanges;
    use crate::services::ImportExportService;
    use crate::tests::open_fs_test_collection;
    use crate::tests::NoteAdder;

    // --- From<ExportLimit> for SearchNode ---

    #[test]
    fn export_limit_none_becomes_whole_collection() {
        let node = SearchNode::from(ExportLimit { limit: None });
        assert_eq!(node, SearchNode::WholeCollection);
    }

    #[test]
    fn export_limit_note_ids_becomes_note_id_search() {
        use anki_proto::notes::NoteIds;
        let node = SearchNode::from(ExportLimit {
            limit: Some(Limit::NoteIds(NoteIds {
                note_ids: vec![1, 2, 3],
            })),
        });
        assert!(
            matches!(node, SearchNode::NoteIds(_)),
            "expected NoteIds search node, got: {node:?}"
        );
    }

    #[test]
    fn export_limit_card_ids_becomes_card_id_search() {
        use anki_proto::cards::CardIds;
        let node = SearchNode::from(ExportLimit {
            limit: Some(Limit::CardIds(CardIds { cids: vec![10, 20] })),
        });
        assert!(
            matches!(node, SearchNode::CardIds(_)),
            "expected CardIds search node, got: {node:?}"
        );
    }

    #[test]
    fn export_limit_deck_id_becomes_deck_id_with_children() {
        let node = SearchNode::from(ExportLimit {
            limit: Some(Limit::DeckId(42)),
        });
        assert!(
            matches!(node, SearchNode::DeckIdWithChildren(_)),
            "expected DeckIdWithChildren, got: {node:?}"
        );
    }

    // --- Service function tests ---

    #[test]
    fn get_import_anki_package_presets_returns_collection_defaults() {
        let mut col = Collection::new();
        let presets = ImportExportService::get_import_anki_package_presets(&mut col).unwrap();
        // newly created collection uses the default config values
        assert!(!presets.merge_notetypes);
        assert!(!presets.with_scheduling);
        assert!(!presets.with_deck_configs);
    }

    #[test]
    fn export_card_csv_service_returns_uint32_count() {
        let (mut col, dir) = open_fs_test_collection("svc_card_csv");
        NoteAdder::basic(&mut col).add(&mut col);
        let path = dir.path().join("cards.csv");
        let result = ImportExportService::export_card_csv(
            &mut col,
            ExportCardCsvRequest {
                out_path: path.to_str().unwrap().into(),
                with_html: true,
                limit: None,
            },
        )
        .unwrap();
        assert_eq!(result.val, 1, "service should return 1 card as UInt32");
    }

    #[test]
    fn export_card_csv_service_propagates_error_for_invalid_path() {
        let mut col = Collection::new();
        let result = ImportExportService::export_card_csv(
            &mut col,
            ExportCardCsvRequest {
                out_path: "/nonexistent/dir/cards.csv".into(),
                with_html: true,
                limit: None,
            },
        );
        assert!(result.is_err(), "expected Err for invalid output path");
    }

    #[test]
    fn export_note_csv_service_returns_uint32_count() {
        let (mut col, dir) = open_fs_test_collection("svc_note_csv");
        NoteAdder::basic(&mut col).add(&mut col);
        let path = dir.path().join("notes.csv");
        let result = ImportExportService::export_note_csv(
            &mut col,
            ExportNoteCsvRequest {
                out_path: path.to_str().unwrap().into(),
                with_html: true,
                with_tags: true,
                with_deck: true,
                with_notetype: true,
                with_guid: true,
                limit: None,
            },
        )
        .unwrap();
        assert_eq!(result.val, 1, "service should return 1 note as UInt32");
    }

    #[test]
    fn export_note_csv_service_propagates_error_for_invalid_path() {
        let mut col = Collection::new();
        let result = ImportExportService::export_note_csv(
            &mut col,
            ExportNoteCsvRequest {
                out_path: "/nonexistent/dir/notes.csv".into(),
                with_html: true,
                with_tags: false,
                with_deck: false,
                with_notetype: false,
                with_guid: false,
                limit: None,
            },
        );
        assert!(result.is_err(), "expected Err for invalid output path");
    }

    #[test]
    fn import_anki_package_succeeds_with_valid_apkg_fixture() {
        // uses the pre-existing fixture from pylib/tests/support/
        let apkg_path = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../pylib/tests/support/update1.apkg");
        let (mut col, _dir) = open_fs_test_collection("import_apkg_success");
        let result = ImportExportService::import_anki_package(
            &mut col,
            ImportAnkiPackageRequest {
                package_path: apkg_path.to_str().unwrap().into(),
                options: None,
            },
        );
        assert!(result.is_ok(), "expected Ok for valid .apkg fixture");
        let response = result.unwrap();
        assert!(response.log.is_some(), "expected import log in response");
    }

    #[test]
    fn import_anki_package_propagates_error_for_missing_file() {
        let mut col = Collection::new();
        let result = ImportExportService::import_anki_package(
            &mut col,
            ImportAnkiPackageRequest {
                package_path: "/nonexistent/file.apkg".into(),
                options: None,
            },
        );
        assert!(result.is_err(), "expected Err for missing .apkg file");
    }

    #[test]
    fn export_anki_package_succeeds_with_empty_collection() {
        let (mut col, dir) = open_fs_test_collection("svc_apkg");
        let path = dir.path().join("out.apkg");
        let result = ImportExportService::export_anki_package(
            &mut col,
            ExportAnkiPackageRequest {
                out_path: path.to_str().unwrap().into(),
                options: None,
                limit: None,
            },
        );
        assert!(result.is_ok(), "expected Ok for empty collection export");
        assert!(path.exists(), "expected .apkg file to be created");
    }

    #[test]
    fn export_anki_package_propagates_error_for_invalid_path() {
        let mut col = Collection::new();
        let result = ImportExportService::export_anki_package(
            &mut col,
            ExportAnkiPackageRequest {
                out_path: "/nonexistent/dir/out.apkg".into(),
                options: None,
                limit: None,
            },
        );
        assert!(result.is_err(), "expected Err for invalid output path");
    }

    #[test]
    fn import_csv_succeeds_with_valid_csv_file() {
        let (mut col, dir) = open_fs_test_collection("import_csv_success");
        let path = dir.path().join("notes.csv");
        // tab-separated: two fields matching Basic notetype (Front, Back)
        // dir and the CSV inside are deleted automatically when dir drops at end of
        // test
        std::fs::write(&path, "front content\tback content\n").unwrap();

        let metadata = ImportExportService::get_csv_metadata(
            &mut col,
            CsvMetadataRequest {
                path: path.to_str().unwrap().into(),
                delimiter: None,
                notetype_id: None,
                deck_id: None,
                is_html: None,
            },
        )
        .unwrap();

        let result = ImportExportService::import_csv(
            &mut col,
            ImportCsvRequest {
                path: path.to_str().unwrap().into(),
                metadata: Some(metadata),
            },
        );
        assert!(result.is_ok(), "expected Ok for valid CSV import");
        assert!(
            result.unwrap().log.is_some(),
            "expected import log in response"
        );
    }

    #[test]
    fn import_csv_propagates_error_for_missing_file() {
        let mut col = Collection::new();
        let result = ImportExportService::import_csv(
            &mut col,
            ImportCsvRequest {
                path: "/nonexistent/file.csv".into(),
                metadata: None,
            },
        );
        assert!(result.is_err(), "expected Err for missing CSV file");
    }

    #[test]
    fn import_json_file_succeeds_with_minimal_valid_json() {
        let (mut col, dir) = open_fs_test_collection("import_json_file");
        let path = dir.path().join("notes.json");
        // ForeignData has #[serde(default)] — empty notes list is valid JSON
        // dir and the file are deleted automatically when dir drops at end of test
        std::fs::write(&path, r#"{"notes": []}"#).unwrap();
        let result = ImportExportService::import_json_file(
            &mut col,
            generic::String {
                val: path.to_str().unwrap().into(),
            },
        );
        assert!(result.is_ok(), "expected Ok for valid JSON file");
    }

    #[test]
    fn import_json_file_propagates_error_for_missing_file() {
        let mut col = Collection::new();
        let result = ImportExportService::import_json_file(
            &mut col,
            generic::String {
                val: "/nonexistent/file.json".into(),
            },
        );
        assert!(result.is_err(), "expected Err for missing JSON file");
    }

    #[test]
    fn import_json_string_succeeds_with_minimal_valid_json() {
        let mut col = Collection::new();
        let result = ImportExportService::import_json_string(
            &mut col,
            generic::String {
                val: r#"{"notes": []}"#.into(),
            },
        );
        assert!(result.is_ok(), "expected Ok for valid JSON string");
    }

    #[test]
    fn import_json_string_propagates_error_for_invalid_json() {
        let mut col = Collection::new();
        let result = ImportExportService::import_json_string(
            &mut col,
            generic::String {
                val: "not valid json at all".into(),
            },
        );
        assert!(result.is_err(), "expected Err for invalid JSON string");
    }

    #[test]
    fn op_output_converts_to_import_response_with_changes_and_log() {
        let output = OpOutput {
            output: NoteLog::default(),
            changes: OpChanges {
                op: Op::Import,
                changes: StateChanges::default(),
            },
        };
        let response: anki_proto::import_export::ImportResponse = output.into();
        assert!(response.changes.is_some(), "expected changes to be set");
        assert!(response.log.is_some(), "expected log to be set");
    }
}
