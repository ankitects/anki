// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod changes;
mod ops;

pub(crate) use changes::UndoableChange;
pub use ops::UndoableOpKind;

use crate::backend_proto as pb;
use crate::prelude::*;
use std::collections::VecDeque;

const UNDO_LIMIT: usize = 30;

#[derive(Debug)]
pub(crate) struct UndoableOp {
    pub kind: UndoableOpKind,
    pub timestamp: TimestampSecs,
    pub changes: Vec<UndoableChange>,
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

    fn begin_step(&mut self, op: Option<UndoableOpKind>) {
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
            if !step.changes.is_empty() {
                if self.mode == UndoMode::Undoing {
                    self.redo_steps.push(step);
                } else {
                    self.undo_steps.truncate(UNDO_LIMIT - 1);
                    self.undo_steps.push_front(step);
                }
            }
        }
        println!("ended, undo steps count now {}", self.undo_steps.len());
    }

    fn current_step_requires_study_queue_reset(&self) -> bool {
        self.current_step
            .as_ref()
            .map(|s| s.kind.needs_study_queue_reset())
            .unwrap_or(true)
    }

    fn can_undo(&self) -> Option<UndoableOpKind> {
        self.undo_steps.front().map(|s| s.kind)
    }

    fn can_redo(&self) -> Option<UndoableOpKind> {
        self.redo_steps.last().map(|s| s.kind)
    }

    pub(crate) fn previous_op(&self) -> Option<&UndoableOp> {
        self.undo_steps.front()
    }
}

impl Collection {
    pub fn can_undo(&self) -> Option<UndoableOpKind> {
        self.state.undo.can_undo()
    }

    pub fn can_redo(&self) -> Option<UndoableOpKind> {
        self.state.undo.can_redo()
    }

    pub fn undo(&mut self) -> Result<()> {
        if let Some(step) = self.state.undo.undo_steps.pop_front() {
            let changes = step.changes;
            self.state.undo.mode = UndoMode::Undoing;
            let res = self.transact(Some(step.kind), |col| {
                for change in changes.into_iter().rev() {
                    change.undo(col)?;
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
                for change in changes.into_iter().rev() {
                    change.undo(col)?;
                }
                Ok(())
            });
            self.state.undo.mode = UndoMode::NormalOp;
            res?;
        }
        Ok(())
    }

    pub fn undo_status(&self) -> pb::UndoStatus {
        pb::UndoStatus {
            undo: self
                .can_undo()
                .map(|op| self.describe_op_kind(op))
                .unwrap_or_default(),
            redo: self
                .can_redo()
                .map(|op| self.describe_op_kind(op))
                .unwrap_or_default(),
        }
    }

    /// If op is None, clears the undo/redo queues.
    pub(crate) fn begin_undoable_operation(&mut self, op: Option<UndoableOpKind>) {
        self.state.undo.begin_step(op);
    }

    /// Called at the end of a successful transaction.
    /// In most instances, this will also clear the study queues.
    pub(crate) fn end_undoable_operation(&mut self) {
        if self.state.undo.current_step_requires_study_queue_reset() {
            self.clear_study_queues();
        }
        self.state.undo.end_step();
    }

    pub(crate) fn discard_undo_and_study_queues(&mut self) {
        self.state.undo.begin_step(None);
        self.clear_study_queues();
    }

    #[inline]
    pub(crate) fn save_undo(&mut self, item: impl Into<UndoableChange>) {
        self.state.undo.save(item.into());
    }

    pub(crate) fn previous_undo_op(&self) -> Option<&UndoableOp> {
        self.state.undo.previous_op()
    }
}

#[cfg(test)]
mod test {
    use crate::card::Card;
    use crate::{collection::open_test_collection, prelude::*};

    #[test]
    fn undo() {
        let mut col = open_test_collection();

        let mut card = Card::default();
        card.interval = 1;
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
            col.transact(Some(UndoableOpKind::UpdateCard), |col| {
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
        assert_eq!(col.can_undo(), Some(UndoableOpKind::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // undo a step
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(UndoableOpKind::UpdateCard));
        assert_eq!(col.can_redo(), Some(UndoableOpKind::UpdateCard));

        // and again
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 2);
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), Some(UndoableOpKind::UpdateCard));

        // redo a step
        col.redo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(UndoableOpKind::UpdateCard));
        assert_eq!(col.can_redo(), Some(UndoableOpKind::UpdateCard));

        // and another
        col.redo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 4);
        assert_eq!(col.can_undo(), Some(UndoableOpKind::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // and undo the redo
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(UndoableOpKind::UpdateCard));
        assert_eq!(col.can_redo(), Some(UndoableOpKind::UpdateCard));

        // if any action is performed, it should clear the redo queue
        col.transact(Some(UndoableOpKind::UpdateCard), |col| {
            col.get_and_update_card(cid, |card| {
                card.interval = 5;
                Ok(())
            })
            .unwrap();
            Ok(())
        })
        .unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 5);
        assert_eq!(col.can_undo(), Some(UndoableOpKind::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // and any action that doesn't support undoing will clear both queues
        col.transact(None, |_col| Ok(())).unwrap();
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), None);
    }
}
