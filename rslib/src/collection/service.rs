// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use anki_proto::generic;

use crate::collection::Collection;
use crate::config::BoolKey;
use crate::error;
use crate::progress::progress_to_proto;

impl crate::services::CollectionService for Collection {
    fn check_database(&mut self) -> error::Result<anki_proto::collection::CheckDatabaseResponse> {
        {
            self.check_database()
                .map(|problems| anki_proto::collection::CheckDatabaseResponse {
                    problems: problems.to_i18n_strings(&self.tr),
                })
        }
    }

    fn get_undo_status(&mut self) -> error::Result<anki_proto::collection::UndoStatus> {
        Ok(self.undo_status().into_protobuf(&self.tr))
    }

    fn undo(&mut self) -> error::Result<anki_proto::collection::OpChangesAfterUndo> {
        self.undo().map(|out| out.into_protobuf(&self.tr))
    }

    fn redo(&mut self) -> error::Result<anki_proto::collection::OpChangesAfterUndo> {
        self.redo().map(|out| out.into_protobuf(&self.tr))
    }

    fn add_custom_undo_entry(&mut self, input: generic::String) -> error::Result<generic::UInt32> {
        Ok(self.add_custom_undo_step(input.val).into())
    }

    fn merge_undo_entries(
        &mut self,
        input: generic::UInt32,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        let starting_from = input.val as usize;
        self.merge_undoable_ops(starting_from).map(Into::into)
    }

    fn latest_progress(&mut self) -> error::Result<anki_proto::collection::Progress> {
        let progress = self.state.progress.lock().unwrap().last_progress;
        Ok(progress_to_proto(progress, &self.tr))
    }

    fn set_wants_abort(&mut self) -> error::Result<()> {
        self.state.progress.lock().unwrap().want_abort = true;
        Ok(())
    }

    fn set_load_balancer_enabled(&mut self, input: generic::Bool) -> error::Result<()> {
        self.clear_study_queues();
        self.set_config_bool(BoolKey::LoadBalancerEnabled, input.val, false)?;
        Ok(())
    }
}
