// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::notes::to_i64s;
use super::progress::Progress;
use super::Backend;
use crate::media::check::MediaChecker;
use crate::pb;
pub(super) use crate::pb::media::media_service::Service as MediaService;
use crate::prelude::*;

impl MediaService for Backend {
    // media
    //-----------------------------------------------

    fn check_media(&self, _input: pb::generic::Empty) -> Result<pb::media::CheckMediaResponse> {
        let mut handler = self.new_progress_handler();
        let progress_fn =
            move |progress| handler.update(Progress::MediaCheck(progress as u32), true);
        self.with_col(|col| {
            let mgr = col.media()?;
            col.transact_no_undo(|ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, progress_fn);
                let mut output = checker.check()?;

                let mut report = checker.summarize_output(&mut output);
                ctx.report_media_field_referencing_templates(&mut report)?;

                Ok(pb::media::CheckMediaResponse {
                    unused: output.unused,
                    missing: output.missing,
                    missing_media_notes: to_i64s(output.missing_media_notes),
                    report,
                    have_trash: output.trash_count > 0,
                })
            })
        })
    }

    fn trash_media_files(
        &self,
        input: pb::media::TrashMediaFilesRequest,
    ) -> Result<pb::generic::Empty> {
        self.with_col(|col| {
            let mgr = col.media()?;
            mgr.remove_files(&input.fnames)
        })
        .map(Into::into)
    }

    fn add_media_file(&self, input: pb::media::AddMediaFileRequest) -> Result<pb::generic::String> {
        self.with_col(|col| {
            let mgr = col.media()?;
            Ok(mgr
                .add_file(&input.desired_name, &input.data)?
                .to_string()
                .into())
        })
    }

    fn empty_trash(&self, _input: pb::generic::Empty) -> Result<pb::generic::Empty> {
        let mut handler = self.new_progress_handler();
        let progress_fn =
            move |progress| handler.update(Progress::MediaCheck(progress as u32), true);

        self.with_col(|col| {
            let mgr = col.media()?;
            let mut checker = MediaChecker::new(col, &mgr, progress_fn);
            checker.empty_trash()
        })
        .map(Into::into)
    }

    fn restore_trash(&self, _input: pb::generic::Empty) -> Result<pb::generic::Empty> {
        let mut handler = self.new_progress_handler();
        let progress_fn =
            move |progress| handler.update(Progress::MediaCheck(progress as u32), true);
        self.with_col(|col| {
            let mgr = col.media()?;
            let mut checker = MediaChecker::new(col, &mgr, progress_fn);
            checker.restore_trash()
        })
        .map(Into::into)
    }
}
