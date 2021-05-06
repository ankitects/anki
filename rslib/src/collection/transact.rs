// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{ops::StateChanges, prelude::*};

impl Collection {
    fn transact_inner<F, R>(&mut self, op: Option<Op>, func: F) -> Result<OpOutput<R>>
    where
        F: FnOnce(&mut Collection) -> Result<R>,
    {
        let have_op = op.is_some();

        self.storage.begin_rust_trx()?;
        self.begin_undoable_operation(op);

        let mut res = func(self);

        if res.is_ok() {
            if let Err(e) = self.set_modified() {
                res = Err(e);
            } else if let Err(e) = self.storage.commit_rust_trx() {
                res = Err(e);
            }
        }

        match res {
            Ok(output) => {
                let changes = if have_op {
                    let changes = self.op_changes();
                    self.maybe_clear_study_queues_after_op(&changes);
                    self.maybe_coalesce_note_undo_entry(&changes);
                    changes
                } else {
                    self.clear_study_queues();
                    // dummy value, not used by transact_no_undo(). only required
                    // until we can migrate all the code to undoable ops
                    OpChanges {
                        op: Op::SetFlag,
                        changes: StateChanges::default(),
                    }
                };
                self.end_undoable_operation();
                Ok(OpOutput { output, changes })
            }
            Err(err) => {
                self.discard_undo_and_study_queues();
                self.storage.rollback_rust_trx()?;
                Err(err)
            }
        }
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
