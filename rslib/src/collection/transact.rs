// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::ops::StateChanges;
use crate::prelude::*;

impl Collection {
    fn transact_inner<F, R>(
        &mut self,
        op: Option<Op>,
        modified_after: Option<TimestampMillis>,
        func: F,
    ) -> Result<OpOutput<R>>
    where
        F: FnOnce(&mut Collection) -> Result<R>,
    {
        let have_op = op.is_some();
        let skip_undo_queue = op == Some(Op::SkipUndo);
        let autocommit = self.storage.db.is_autocommit();

        self.storage.begin_rust_trx()?;
        self.begin_undoable_operation(op);

        func(self)
            .and_then(|output| {
                // any changes mean an mtime bump
                if !have_op || (self.current_undo_step_has_changes() && !self.undoing_or_redoing())
                {
                    self.set_modified()?;
                    if let Some(after) = modified_after {
                        let modified = self.storage.get_collection_timestamps()?.collection_change;
                        self.storage
                            .set_modified_time(TimestampMillis(modified.0.max(after.0 + 1)))?;
                    }
                }
                // then commit
                self.storage.commit_rust_trx()?;
                // finalize undo
                let changes = if have_op {
                    let changes = self.op_changes();
                    self.maybe_clear_study_queues_after_op(&changes);
                    self.maybe_coalesce_note_undo_entry(&changes);
                    changes
                } else {
                    self.clear_study_queues();
                    // dummy value that will be discarded
                    OpChanges {
                        op: Op::SkipUndo,
                        changes: StateChanges::default(),
                    }
                };
                self.end_undoable_operation(skip_undo_queue);
                Ok(OpOutput { output, changes })
            })
            // roll back on error
            .or_else(|err| {
                self.discard_undo_and_study_queues();
                if autocommit {
                    self.storage.rollback_trx()?;
                } else {
                    self.storage.rollback_rust_trx()?;
                }
                Err(err)
            })
    }

    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned. Records undo state, and returns changes.
    pub(crate) fn transact<F, R>(&mut self, op: Op, func: F) -> Result<OpOutput<R>>
    where
        F: FnOnce(&mut Collection) -> Result<R>,
    {
        self.transact_inner(Some(op), None, func)
    }

    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned.
    pub(crate) fn transact_no_undo<F, R>(&mut self, func: F) -> Result<R>
    where
        F: FnOnce(&mut Collection) -> Result<R>,
    {
        self.transact_inner(None, None, func).map(|out| out.output)
    }

    /// Apply a client migration atomically, leaving its writes pending even
    /// when the sync server's clock is ahead of the local clock.
    pub(crate) fn transact_no_undo_after_sync<F, R>(&mut self, func: F) -> Result<R>
    where
        F: FnOnce(&mut Self) -> Result<R>,
    {
        let last_sync = self.storage.get_collection_timestamps()?.last_sync;
        self.transact_inner(None, Some(last_sync), func)
            .map(|out| out.output)
    }
}
