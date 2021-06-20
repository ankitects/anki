// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{progress::Progress, Backend};
pub(super) use crate::backend_proto::media_service::Service as MediaService;
use crate::{
    backend_proto as pb,
    media::{check::MediaChecker, MediaManager},
    prelude::*,
};

impl MediaService for Backend {
    // media
    //-----------------------------------------------

    fn check_media(&self, _input: pb::Empty) -> Result<pb::CheckMediaResponse> {
        let mut handler = self.new_progress_handler();
        let progress_fn =
            move |progress| handler.update(Progress::MediaCheck(progress as u32), true);
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            col.transact_no_undo(|ctx| {
                let mut checker = MediaChecker::new(ctx, &mgr, progress_fn);
                let mut output = checker.check()?;

                let report = checker.summarize_output(&mut output);

                Ok(pb::CheckMediaResponse {
                    unused: output.unused,
                    missing: output.missing,
                    report,
                    have_trash: output.trash_count > 0,
                })
            })
        })
    }

    fn trash_media_files(&self, input: pb::TrashMediaFilesRequest) -> Result<pb::Empty> {
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut ctx = mgr.dbctx();
            mgr.remove_files(&mut ctx, &input.fnames)
        })
        .map(Into::into)
    }

    fn add_media_file(&self, input: pb::AddMediaFileRequest) -> Result<pb::String> {
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut ctx = mgr.dbctx();
            Ok(mgr
                .add_file(&mut ctx, &input.desired_name, &input.data)?
                .to_string()
                .into())
        })
    }

    fn empty_trash(&self, _input: pb::Empty) -> Result<pb::Empty> {
        let mut handler = self.new_progress_handler();
        let progress_fn =
            move |progress| handler.update(Progress::MediaCheck(progress as u32), true);

        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut checker = MediaChecker::new(col, &mgr, progress_fn);
            checker.empty_trash()
        })
        .map(Into::into)
    }

    fn restore_trash(&self, _input: pb::Empty) -> Result<pb::Empty> {
        let mut handler = self.new_progress_handler();
        let progress_fn =
            move |progress| handler.update(Progress::MediaCheck(progress as u32), true);
        self.with_col(|col| {
            let mgr = MediaManager::new(&col.media_folder, &col.media_db)?;
            let mut checker = MediaChecker::new(col, &mgr, progress_fn);
            checker.restore_trash()
        })
        .map(Into::into)
    }
}
