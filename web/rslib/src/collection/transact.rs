// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::ops::StateChanges;
use crate::prelude::*;

impl Collection {
    fn transact_inner<F, R>(&mut self, op: Option<Op>, func: F) -> Result<OpOutput<R>>
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
        self.transact_inner(Some(op), func)
    }

    /// Execute the provided closure in a transaction, rolling back if
    /// an error is returned.
    pub(crate) fn transact_no_undo<F, R>(&mut self, func: F) -> Result<R>
    where
        F: FnOnce(&mut Collection) -> Result<R>,
    {
        self.transact_inner(None, func).map(|out| out.output)
    }
}
