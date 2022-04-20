// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{progress::Progress, Backend};
pub(super) use crate::backend_proto::importexport_service::Service as ImportExportService;
use crate::{
    backend_proto::{self as pb, export_anki_package_request::Selector},
    import_export::{package::import_colpkg, ImportProgress},
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
            &input.media_folder,
            self.import_progress_fn(),
        )
        .map(Into::into)
    }

    fn import_anki_package(&self, input: pb::ImportAnkiPackageRequest) -> Result<pb::OpChanges> {
        self.with_col(|col| col.import_apkg(&input.package_path, &mut self.import_progress_fn()))
            .map(Into::into)
    }

    fn export_anki_package(&self, input: pb::ExportAnkiPackageRequest) -> Result<pb::Empty> {
        let selector = input
            .selector
            .ok_or_else(|| AnkiError::invalid_input("missing oneof"))?;
        self.with_col(|col| {
            col.export_apkg(
                &input.out_path,
                SearchNode::from_selector(selector),
                input.with_scheduling,
                input.with_media,
                None,
                self.export_progress_fn(),
            )
        })
        .map(Into::into)
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
    fn import_progress_fn(&self) -> impl FnMut(ImportProgress) -> Result<()> {
        let mut handler = self.new_progress_handler();
        move |progress| {
            let throttle = matches!(progress, ImportProgress::Media(_));
            if handler.update(Progress::Import(progress), throttle) {
                Ok(())
            } else {
                Err(AnkiError::Interrupted)
            }
        }
    }

    fn export_progress_fn(&self) -> impl FnMut(usize) {
        let mut handler = self.new_progress_handler();
        move |media_files| {
            handler.update(Progress::Export(media_files), true);
        }
    }
}
