// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::generic;
use anki_proto::media::AddMediaFileRequest;
use anki_proto::media::CheckMediaResponse;
use anki_proto::media::TrashMediaFilesRequest;

use crate::collection::Collection;
use crate::error;
use crate::notes::service::to_i64s;

impl crate::services::MediaService for Collection {
    fn check_media(&mut self) -> error::Result<CheckMediaResponse> {
        self.transact_no_undo(|col| {
            let mut checker = col.media_checker()?;
            let mut output = checker.check()?;

            let mut report = checker.summarize_output(&mut output);
            col.report_media_field_referencing_templates(&mut report)?;

            Ok(CheckMediaResponse {
                unused: output.unused,
                missing: output.missing,
                missing_media_notes: to_i64s(output.missing_media_notes),
                report,
                have_trash: output.trash_count > 0,
            })
        })
    }

    fn add_media_file(&mut self, input: AddMediaFileRequest) -> error::Result<generic::String> {
        Ok(self
            .media()?
            .add_file(&input.desired_name, &input.data)?
            .to_string()
            .into())
    }

    fn trash_media_files(&mut self, input: TrashMediaFilesRequest) -> error::Result<()> {
        self.media()?.remove_files(&input.fnames)
    }

    fn empty_trash(&mut self) -> error::Result<()> {
        self.media_checker()?.empty_trash()
    }

    fn restore_trash(&mut self) -> error::Result<()> {
        self.media_checker()?.restore_trash()
    }
}
