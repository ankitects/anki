// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::backend_proto as pb;
use crate::{
    collection::{Collection, CollectionOp},
    err::Result,
};
use std::{collections::VecDeque, fmt};

const UNDO_LIMIT: usize = 30;

pub(crate) trait Undo: fmt::Debug + Send {
    /// Undo the recorded action.
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()>;
}

#[derive(Debug)]
struct UndoStep {
    kind: CollectionOp,
    changes: Vec<Box<dyn Undo>>,
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
    undo_steps: VecDeque<UndoStep>,
    // redo steps are added to the end
    redo_steps: Vec<UndoStep>,
    mode: UndoMode,
    current_step: Option<UndoStep>,
}

impl UndoManager {
    pub(crate) fn save(&mut self, item: Box<dyn Undo>) {
        if let Some(step) = self.current_step.as_mut() {
            step.changes.push(item)
        }
    }

    pub(crate) fn begin_step(&mut self, op: Option<CollectionOp>) {
        if op.is_none() {
            self.clear();
        } else if self.mode == UndoMode::NormalOp {
            // a normal op clears the redo queue
            self.redo_steps.clear();
        }
        self.current_step = op.map(|op| UndoStep {
            kind: op,
            changes: vec![],
        });
    }

    pub(crate) fn clear(&mut self) {
        self.undo_steps.clear();
        self.redo_steps.clear();
    }

    pub(crate) fn clear_redo(&mut self) {
        self.redo_steps.clear();
    }

    pub(crate) fn end_step(&mut self) {
        if let Some(step) = self.current_step.take() {
            if self.mode == UndoMode::Undoing {
                self.redo_steps.push(step);
            } else {
                self.undo_steps.truncate(UNDO_LIMIT - 1);
                self.undo_steps.push_front(step);
            }
        }
    }

    pub(crate) fn discard_step(&mut self) {
        self.begin_step(None)
    }

    fn can_undo(&self) -> Option<CollectionOp> {
        self.undo_steps.front().map(|s| s.kind)
    }

    fn can_redo(&self) -> Option<CollectionOp> {
        self.redo_steps.last().map(|s| s.kind)
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

    #[inline]
    pub(crate) fn save_undo(&mut self, item: Box<dyn Undo>) {
        self.state.undo.save(item)
    }

    pub fn undo_status(&self) -> pb::UndoStatus {
        pb::UndoStatus {
            undo: self
                .can_undo()
                .map(|op| self.describe_collection_op(op))
                .unwrap_or_default(),
            redo: self
                .can_redo()
                .map(|op| self.describe_collection_op(op))
                .unwrap_or_default(),
        }
    }
}

#[cfg(test)]
mod test {
    use crate::card::Card;
    use crate::collection::{open_test_collection, CollectionOp};

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
            col.transact(Some(CollectionOp::UpdateCard), |col| {
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
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // undo a step
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), Some(CollectionOp::UpdateCard));

        // and again
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 2);
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), Some(CollectionOp::UpdateCard));

        // redo a step
        col.redo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), Some(CollectionOp::UpdateCard));

        // and another
        col.redo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 4);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // and undo the redo
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), Some(CollectionOp::UpdateCard));

        // if any action is performed, it should clear the redo queue
        col.transact(Some(CollectionOp::UpdateCard), |col| {
            col.get_and_update_card(cid, |card| {
                card.interval = 5;
                Ok(())
            })
            .unwrap();
            Ok(())
        })
        .unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 5);
        assert_eq!(col.can_undo(), Some(CollectionOp::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // and any action that doesn't support undoing will clear both queues
        col.transact(None, |_col| Ok(())).unwrap();
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), None);
    }
}
