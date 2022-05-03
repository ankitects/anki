// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::path::Path;

use super::{progress::Progress, Backend};
pub(super) use crate::backend_proto::importexport_service::Service as ImportExportService;
use crate::{
    backend_proto::{
        self as pb,
        export_anki_package_request::Selector,
        import_csv_request::{
            column::{Other as OtherColumn, Variant as ColumnVariant},
            Column as ProtoColumn,
        },
    },
    import_export::{
        package::{import_colpkg, NoteLog},
        text::csv::Column,
        ExportProgress, ImportProgress,
    },
    prelude::*,
    search::SearchNode,
};

impl ImportExportService for Backend {
    fn export_collection_package(
        &self,
        input: pb::ExportCollectionPackageRequest,
    ) -> Result<pb::Empty> {
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
        input: pb::ImportCollectionPackageRequest,
    ) -> Result<pb::Empty> {
        let _guard = self.lock_closed_collection()?;

        import_colpkg(
            &input.backup_path,
            &input.col_path,
            Path::new(&input.media_folder),
            Path::new(&input.media_db),
            self.import_progress_fn(),
            &self.log,
        )
        .map(Into::into)
    }

    fn import_anki_package(
        &self,
        input: pb::ImportAnkiPackageRequest,
    ) -> Result<pb::ImportAnkiPackageResponse> {
        self.with_col(|col| col.import_apkg(&input.package_path, self.import_progress_fn()))
            .map(Into::into)
    }

    fn export_anki_package(&self, input: pb::ExportAnkiPackageRequest) -> Result<pb::UInt32> {
        let selector = input
            .selector
            .ok_or_else(|| AnkiError::invalid_input("missing oneof"))?;
        self.with_col(|col| {
            col.export_apkg(
                &input.out_path,
                SearchNode::from_selector(selector),
                input.with_scheduling,
                input.with_media,
                input.legacy,
                None,
                self.export_progress_fn(),
            )
        })
        .map(Into::into)
    }

    fn import_csv(&self, input: pb::ImportCsvRequest) -> Result<pb::Empty> {
        let out = self.with_col(|col| {
            col.import_csv(
                &input.path,
                input.deck_id.into(),
                input.notetype_id.into(),
                input.columns.into_iter().map(Into::into).collect(),
                byte_from_string(&input.delimiter)?,
                input.allow_html,
            )
        })?;
        println!("{:?}", out);
        Ok(pb::Empty {})
    }
}

impl SearchNode {
    fn from_selector(selector: Selector) -> Self {
        match selector {
            Selector::WholeCollection(_) => Self::WholeCollection,
            Selector::DeckId(did) => Self::from_deck_id(did, true),
            Selector::NoteIds(nids) => Self::from_note_ids(nids.note_ids),
        }
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

impl From<OpOutput<NoteLog>> for pb::ImportAnkiPackageResponse {
    fn from(output: OpOutput<NoteLog>) -> Self {
        Self {
            changes: Some(output.changes.into()),
            log: Some(output.output),
        }
    }
}

impl From<ProtoColumn> for Column {
    fn from(column: ProtoColumn) -> Self {
        match column.variant.unwrap_or(ColumnVariant::Other(0)) {
            ColumnVariant::Field(idx) => Column::Field(idx as usize),
            ColumnVariant::Other(i) => match OtherColumn::from_i32(i).unwrap_or_default() {
                OtherColumn::Tags => Column::Tags,
                OtherColumn::Ignore => Column::Ignore,
            },
        }
    }
}

fn byte_from_string(s: &str) -> Result<u8> {
    s.bytes()
        .next()
        .ok_or_else(|| AnkiError::invalid_input("empty string"))
}
