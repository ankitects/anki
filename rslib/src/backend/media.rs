// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::generic;
pub(super) use anki_proto::media::media_service::Service as MediaService;

use super::notes::to_i64s;
use super::Backend;
use crate::prelude::*;

impl MediaService for Backend {
    type Error = AnkiError;

    fn check_media(&self) -> Result<anki_proto::media::CheckMediaResponse> {
        self.with_col(|col| {
            col.transact_no_undo(|col| {
                let mut checker = col.media_checker()?;
                let mut output = checker.check()?;

                let mut report = checker.summarize_output(&mut output);
                col.report_media_field_referencing_templates(&mut report)?;

                Ok(anki_proto::media::CheckMediaResponse {
                    unused: output.unused,
                    missing: output.missing,
                    missing_media_notes: to_i64s(output.missing_media_notes),
                    report,
                    have_trash: output.trash_count > 0,
                })
            })
        })
    }

    fn trash_media_files(&self, input: anki_proto::media::TrashMediaFilesRequest) -> Result<()> {
        self.with_col(|col| col.media()?.remove_files(&input.fnames))
            .map(Into::into)
    }

    fn add_media_file(
        &self,
        input: anki_proto::media::AddMediaFileRequest,
    ) -> Result<generic::String> {
        self.with_col(|col| {
            Ok(col
                .media()?
                .add_file(&input.desired_name, &input.data)?
                .to_string()
                .into())
        })
    }

    fn empty_trash(&self) -> Result<()> {
        self.with_col(|col| col.media_checker()?.empty_trash())
            .map(Into::into)
    }

    fn restore_trash(&self) -> Result<()> {
        self.with_col(|col| col.media_checker()?.restore_trash())
            .map(Into::into)
    }
}
