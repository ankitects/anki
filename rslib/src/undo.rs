// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    collection::{Collection, CollectionOp},
    err::Result,
    types::Usn,
};
use std::fmt;

pub(crate) trait Undoable: fmt::Debug + Send {
    /// Undo the recorded action.
    fn apply(&self, ctx: &mut Collection, usn: Usn) -> Result<()>;
}

#[derive(Debug)]
struct UndoStep {
    kind: CollectionOp,
    changes: Vec<Box<dyn Undoable>>,
}

#[derive(Debug, PartialEq)]
enum UndoMode {
    NormalOp,
    Undoing,
    Redoing,
}

impl Default for UndoMode {
    fn default() -> Self {
        Self::NormalOp
    }
}

#[derive(Debug, Default)]
pub(crate) struct UndoManager {
    undo_steps: Vec<UndoStep>,
    redo_steps: Vec<UndoStep>,
    mode: UndoMode,
    current_step: Option<UndoStep>,
}

impl UndoManager {
    pub(crate) fn save_undoable(&mut self, item: Box<dyn Undoable>) {
        if let Some(step) = self.current_step.as_mut() {
            step.changes.push(item)
        }
    }

    pub(crate) fn begin_step(&mut self, op: Option<CollectionOp>) {
        if op.is_none() {
            // action doesn't support undoing; clear the queue
            self.undo_steps.clear();
            self.redo_steps.clear();
        } else if self.mode == UndoMode::NormalOp {
            // a normal op clears the redo queue
            self.redo_steps.clear();
        }
        self.current_step = op.map(|op| UndoStep {
            kind: op,
            changes: vec![],
        });
    }

    pub(crate) fn end_step(&mut self) {
        if let Some(step) = self.current_step.take() {
            if self.mode == UndoMode::Undoing {
                self.redo_steps.push(step);
            } else {
                self.undo_steps.push(step);
            }
        }
    }

    pub(crate) fn discard_step(&mut self) {
        self.begin_step(None)
    }

    fn can_undo(&self) -> Option<CollectionOp> {
        self.undo_steps.last().map(|s| s.kind.clone())
    }

    fn can_redo(&self) -> Option<CollectionOp> {
        self.redo_steps.last().map(|s| s.kind.clone())
    }
}

impl Collection {
    pub fn can_undo(&self) -> Option<CollectionOp> {
        self.state.undo.can_undo()
    }

    pub fn can_redo(&self) -> Option<CollectionOp> {
        self.state.undo.can_redo()
    }

    pub fn undo(&mut self) -> Result<()> {
        if let Some(step) = self.state.undo.undo_steps.pop() {
            let changes = step.changes;
            self.state.undo.mode = UndoMode::Undoing;
            let res = self.transact(Some(step.kind), |col| {
                let usn = col.usn()?;
                for change in changes.iter().rev() {
                    change.apply(col, usn)?;
                }
                Ok(())
            });
            self.state.undo.mode = UndoMode::NormalOp;
            res?;
        }
        Ok(())
    }

    pub fn redo(&mut self) -> Result<()> {
        if let Some(step) = self.state.undo.redo_steps.pop() {
            let changes = step.changes;
            self.state.undo.mode = UndoMode::Redoing;
            let res = self.transact(Some(step.kind), |col| {
                let usn = col.usn()?;
                for change in changes.iter().rev() {
                    change.apply(col, usn)?;
                }
                Ok(())
            });
            self.state.undo.mode = UndoMode::NormalOp;
            res?;
        }
        Ok(())
    }
}
