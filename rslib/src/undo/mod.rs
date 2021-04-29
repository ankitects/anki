// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod changes;

use std::collections::VecDeque;

pub(crate) use changes::UndoableChange;

pub use crate::ops::Op;
use crate::{
    collection::undo::UndoableCollectionChange,
    ops::{OpChanges, StateChanges},
    prelude::*,
};

const UNDO_LIMIT: usize = 30;

#[derive(Debug)]
pub(crate) struct UndoableOp {
    pub kind: Op,
    pub timestamp: TimestampSecs,
    pub changes: Vec<UndoableChange>,
}

impl UndoableOp {
    /// True if changes empty, or only the collection mtime has changed.
    fn has_changes(&self) -> bool {
        !matches!(
            &self.changes[..],
            &[] | &[UndoableChange::Collection(
                UndoableCollectionChange::Modified(_)
            )]
        )
    }
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

pub struct UndoStatus {
    pub undo: Option<Op>,
    pub redo: Option<Op>,
}

pub struct UndoOutput {
    pub undone_op: Op,
    pub reverted_to: TimestampSecs,
    pub new_undo_status: UndoStatus,
}

#[derive(Debug, Default)]
pub(crate) struct UndoManager {
    // undo steps are added to the front of a double-ended queue, so we can
    // efficiently cap the number of steps we retain in memory
    undo_steps: VecDeque<UndoableOp>,
    // redo steps are added to the end
    redo_steps: Vec<UndoableOp>,
    mode: UndoMode,
    current_step: Option<UndoableOp>,
}

impl UndoManager {
    fn save(&mut self, item: UndoableChange) {
        if let Some(step) = self.current_step.as_mut() {
            step.changes.push(item)
        }
    }

    fn begin_step(&mut self, op: Option<Op>) {
        println!("begin: {:?}", op);
        if op.is_none() {
            self.undo_steps.clear();
            self.redo_steps.clear();
        } else if self.mode == UndoMode::NormalOp {
            // a normal op clears the redo queue
            self.redo_steps.clear();
        }
        self.current_step = op.map(|op| UndoableOp {
            kind: op,
            timestamp: TimestampSecs::now(),
            changes: vec![],
        });
    }

    fn end_step(&mut self) {
        if let Some(step) = self.current_step.take() {
            if step.has_changes() {
                if self.mode == UndoMode::Undoing {
                    self.redo_steps.push(step);
                } else {
                    self.undo_steps.truncate(UNDO_LIMIT - 1);
                    self.undo_steps.push_front(step);
                }
            } else {
                println!("no undo changes, discarding step");
            }
        }
        println!("ended, undo steps count now {}", self.undo_steps.len());
    }

    fn can_undo(&self) -> Option<Op> {
        self.undo_steps.front().map(|s| s.kind)
    }

    fn can_redo(&self) -> Option<Op> {
        self.redo_steps.last().map(|s| s.kind)
    }

    fn previous_op(&self) -> Option<&UndoableOp> {
        self.undo_steps.front()
    }

    fn current_op(&self) -> Option<&UndoableOp> {
        self.current_step.as_ref()
    }

    fn op_changes(&self) -> OpChanges {
        let current_op = self
            .current_step
            .as_ref()
            .expect("current_changes() called when no op set");

        let mut changes = StateChanges::default();
        for change in &current_op.changes {
            match change {
                UndoableChange::Card(_) => changes.card = true,
                UndoableChange::Note(_) => changes.note = true,
                UndoableChange::Deck(_) => changes.deck = true,
                UndoableChange::Tag(_) => changes.tag = true,
                UndoableChange::Revlog(_) => {}
                UndoableChange::Queue(_) => {}
                UndoableChange::Config(_) => changes.config = true,
                UndoableChange::DeckConfig(_) => changes.deck_config = true,
                UndoableChange::Collection(_) => {}
                UndoableChange::Notetype(_) => changes.notetype = true,
            }
        }

        OpChanges {
            op: current_op.kind,
            changes,
        }
    }
}

impl Collection {
    pub fn can_undo(&self) -> Option<Op> {
        self.state.undo.can_undo()
    }

    pub fn can_redo(&self) -> Option<Op> {
        self.state.undo.can_redo()
    }

    pub fn undo(&mut self) -> Result<OpOutput<UndoOutput>> {
        if let Some(step) = self.state.undo.undo_steps.pop_front() {
            self.undo_inner(step, UndoMode::Undoing)
        } else {
            Err(AnkiError::UndoEmpty)
        }
    }
    pub fn redo(&mut self) -> Result<OpOutput<UndoOutput>> {
        if let Some(step) = self.state.undo.redo_steps.pop() {
            self.undo_inner(step, UndoMode::Redoing)
        } else {
            Err(AnkiError::UndoEmpty)
        }
    }

    pub fn undo_status(&self) -> UndoStatus {
        UndoStatus {
            undo: self.can_undo(),
            redo: self.can_redo(),
        }
    }

    /// If op is None, clears the undo/redo queues.
    pub(crate) fn begin_undoable_operation(&mut self, op: Option<Op>) {
        self.state.undo.begin_step(op);
    }

    /// Called at the end of a successful transaction.
    /// In most instances, this will also clear the study queues.
    pub(crate) fn end_undoable_operation(&mut self) {
        self.state.undo.end_step();
    }

    pub(crate) fn discard_undo_and_study_queues(&mut self) {
        self.state.undo.begin_step(None);
        self.clear_study_queues();
    }

    pub(crate) fn update_state_after_dbproxy_modification(&mut self) {
        self.discard_undo_and_study_queues();
        self.state.modified_by_dbproxy = true;
    }

    #[inline]
    pub(crate) fn save_undo(&mut self, item: impl Into<UndoableChange>) {
        self.state.undo.save(item.into());
    }

    pub(crate) fn current_undo_op(&self) -> Option<&UndoableOp> {
        self.state.undo.current_op()
    }

    pub(crate) fn previous_undo_op(&self) -> Option<&UndoableOp> {
        self.state.undo.previous_op()
    }

    /// Used for coalescing successive note updates.
    pub(crate) fn pop_last_change(&mut self) -> Option<UndoableChange> {
        self.state
            .undo
            .current_step
            .as_mut()
            .expect("no operation active")
            .changes
            .pop()
    }

    /// Return changes made by the current op. Must only be called in a transaction,
    /// when an operation was passed to transact().
    pub(crate) fn op_changes(&self) -> OpChanges {
        self.state.undo.op_changes()
    }
}

impl Collection {
    fn undo_inner(&mut self, step: UndoableOp, mode: UndoMode) -> Result<OpOutput<UndoOutput>> {
        let undone_op = step.kind;
        let reverted_to = step.timestamp;
        let changes = step.changes;
        self.state.undo.mode = mode;
        let res = self.transact(step.kind, |col| {
            for change in changes.into_iter().rev() {
                change.undo(col)?;
            }
            Ok(UndoOutput {
                undone_op,
                reverted_to,
                new_undo_status: col.undo_status(),
            })
        });
        self.state.undo.mode = UndoMode::NormalOp;
        res
    }
}

#[cfg(test)]
mod test {
    use crate::{card::Card, collection::open_test_collection, prelude::*};

    #[test]
    fn undo() -> Result<()> {
        let mut col = open_test_collection();

        let mut card = Card {
            interval: 1,
            ..Default::default()
        };
        col.add_card(&mut card).unwrap();
        let cid = card.id;

        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), None);

        // outside of a transaction, no undo info recorded
        let card = col
            .get_and_update_card(cid, |card| {
                card.interval = 2;
                Ok(())
            })
            .unwrap();
        assert_eq!(card.interval, 2);
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), None);

        // record a few undo steps
        for i in 3..=4 {
            col.transact(Op::UpdateCard, |col| {
                col.get_and_update_card(cid, |card| {
                    card.interval = i;
                    Ok(())
                })
                .unwrap();
                Ok(())
            })
            .unwrap();
        }

        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 4);
        assert_eq!(col.can_undo(), Some(Op::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // undo a step
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(Op::UpdateCard));
        assert_eq!(col.can_redo(), Some(Op::UpdateCard));

        // and again
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 2);
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), Some(Op::UpdateCard));

        // redo a step
        col.redo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(Op::UpdateCard));
        assert_eq!(col.can_redo(), Some(Op::UpdateCard));

        // and another
        col.redo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 4);
        assert_eq!(col.can_undo(), Some(Op::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // and undo the redo
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(Op::UpdateCard));
        assert_eq!(col.can_redo(), Some(Op::UpdateCard));

        // if any action is performed, it should clear the redo queue
        col.transact(Op::UpdateCard, |col| {
            col.get_and_update_card(cid, |card| {
                card.interval = 5;
                Ok(())
            })
        })?;
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 5);
        assert_eq!(col.can_undo(), Some(Op::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // and any action that doesn't support undoing will clear both queues
        col.transact_no_undo(|_col| Ok(())).unwrap();
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), None);

        // if an object is mutated multiple times in one operation,
        // the changes should be undone in the correct order
        col.transact(Op::UpdateCard, |col| {
            col.get_and_update_card(cid, |card| {
                card.interval = 10;
                Ok(())
            })?;
            col.get_and_update_card(cid, |card| {
                card.interval = 15;
                Ok(())
            })
        })?;

        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 15);
        col.undo()?;
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 5);

        Ok(())
    }
}
