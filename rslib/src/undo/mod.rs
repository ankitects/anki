// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod changes;

use std::collections::VecDeque;

pub(crate) use changes::UndoableChange;

pub use crate::ops::Op;
use crate::ops::OpChanges;
use crate::ops::StateChanges;
use crate::prelude::*;

const UNDO_LIMIT: usize = 30;

#[derive(Debug)]
pub(crate) struct UndoableOp {
    pub kind: Op,
    pub timestamp: TimestampSecs,
    pub changes: Vec<UndoableChange>,
    pub counter: usize,
}

impl UndoableOp {
    /// True if changes non-empty, or a custom undo step.
    fn has_changes(&self) -> bool {
        !self.changes.is_empty() || matches!(self.kind, Op::Custom(_))
    }
}

#[derive(Debug, PartialEq, Eq)]
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
    pub last_step: usize,
}

pub struct UndoOutput {
    pub undone_op: Op,
    pub reverted_to: TimestampSecs,
    pub new_undo_status: UndoStatus,
    pub counter: usize,
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
    counter: usize,
}

impl UndoManager {
    fn save(&mut self, item: UndoableChange) {
        if let Some(step) = self.current_step.as_mut() {
            step.changes.push(item)
        }
    }

    fn begin_step(&mut self, op: Option<Op>) {
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
            counter: {
                self.counter += 1;
                self.counter
            },
        });
    }

    fn end_step(&mut self, skip_undo: bool) {
        if let Some(step) = self.current_step.take() {
            if step.has_changes() && !skip_undo {
                if self.mode == UndoMode::Undoing {
                    self.redo_steps.push(step);
                } else {
                    self.undo_steps.truncate(UNDO_LIMIT - 1);
                    self.undo_steps.push_front(step);
                }
            }
        }
    }

    fn can_undo(&self) -> Option<&Op> {
        self.undo_steps.front().map(|s| &s.kind)
    }

    fn can_redo(&self) -> Option<&Op> {
        self.redo_steps.last().map(|s| &s.kind)
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

        let changes = StateChanges::from(&current_op.changes[..]);
        OpChanges {
            op: current_op.kind.clone(),
            changes,
        }
    }

    fn merge_undoable_ops(&mut self, starting_from: usize) -> Result<OpChanges> {
        let target_idx = self
            .undo_steps
            .iter()
            .enumerate()
            .filter_map(|(idx, op)| {
                if op.counter == starting_from {
                    Some(idx)
                } else {
                    None
                }
            })
            .next()
            .or_invalid("target undo op not found")?;
        let mut removed = vec![];
        for _ in 0..target_idx {
            removed.push(self.undo_steps.pop_front().unwrap());
        }
        let target = self.undo_steps.front_mut().unwrap();
        for step in removed.into_iter().rev() {
            target.changes.extend(step.changes.into_iter());
        }
        self.counter = starting_from;
        Ok(OpChanges {
            op: target.kind.clone(),
            changes: StateChanges::from(&target.changes[..]),
        })
    }

    /// Start a new step with a custom name, and return its associated
    /// counter value, which can be used with `merge_undoable_ops`.
    fn add_custom_step(&mut self, name: String) -> usize {
        self.begin_step(Some(Op::Custom(name)));
        self.end_step(false);
        self.counter
    }
}

impl Collection {
    pub fn can_undo(&self) -> Option<&Op> {
        self.state.undo.can_undo()
    }

    pub fn can_redo(&self) -> Option<&Op> {
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
            undo: self.can_undo().cloned(),
            redo: self.can_redo().cloned(),
            last_step: self.state.undo.counter,
        }
    }

    /// Merge multiple undoable operations into one, and return the union of
    /// their changes.
    pub fn merge_undoable_ops(&mut self, starting_from: usize) -> Result<OpChanges> {
        self.state.undo.merge_undoable_ops(starting_from)
    }

    /// Add an empty custom undo step, which subsequent changes can be merged
    /// into.
    pub fn add_custom_undo_step(&mut self, name: String) -> usize {
        self.state.undo.add_custom_step(name)
    }
}

impl Collection {
    /// If op is None, clears the undo/redo queues.
    pub(crate) fn begin_undoable_operation(&mut self, op: Option<Op>) {
        self.state.undo.begin_step(op);
    }

    /// Called at the end of a successful transaction.
    /// In most instances, this will also clear the study queues.
    pub(crate) fn end_undoable_operation(&mut self, skip_undo: bool) {
        self.state.undo.end_step(skip_undo);
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

    pub(crate) fn undoing_or_redoing(&self) -> bool {
        self.state.undo.mode != UndoMode::NormalOp
    }

    pub(crate) fn current_undo_step_has_changes(&self) -> bool {
        self.state
            .undo
            .current_op()
            .map(|op| op.has_changes())
            .unwrap_or_default()
    }

    /// Used for coalescing successive note updates.
    pub(crate) fn clear_last_op(&mut self) {
        self.state
            .undo
            .current_step
            .as_mut()
            .expect("no operation active")
            .changes
            .clear()
    }

    /// Return changes made by the current op. Must only be called in a
    /// transaction, when an operation was passed to transact().
    pub(crate) fn op_changes(&self) -> OpChanges {
        self.state.undo.op_changes()
    }

    fn undo_inner(&mut self, step: UndoableOp, mode: UndoMode) -> Result<OpOutput<UndoOutput>> {
        let undone_op = step.kind;
        let reverted_to = step.timestamp;
        let changes = step.changes;
        let counter = step.counter;
        self.state.undo.mode = mode;
        let res = self.transact(undone_op.clone(), |col| {
            for change in changes.into_iter().rev() {
                change.undo(col)?;
            }
            Ok(UndoOutput {
                undone_op,
                reverted_to,
                new_undo_status: col.undo_status(),
                counter,
            })
        });
        self.state.undo.mode = UndoMode::NormalOp;
        res
    }
}

impl From<&[UndoableChange]> for StateChanges {
    fn from(changes: &[UndoableChange]) -> Self {
        let mut out = StateChanges::default();
        if !changes.is_empty() {
            out.mtime = true;
        }
        for change in changes {
            match change {
                UndoableChange::Card(_) => out.card = true,
                UndoableChange::Note(_) => out.note = true,
                UndoableChange::Deck(_) => out.deck = true,
                UndoableChange::Tag(_) => out.tag = true,
                UndoableChange::Revlog(_) => {}
                UndoableChange::Queue(_) => {}
                UndoableChange::Config(_) => out.config = true,
                UndoableChange::DeckConfig(_) => out.deck_config = true,
                UndoableChange::Collection(_) => {}
                UndoableChange::Notetype(_) => out.notetype = true,
            }
        }
        out
    }
}

#[cfg(test)]
mod test {
    use super::UndoableChange;
    use crate::card::Card;
    use crate::prelude::*;

    #[test]
    fn undo() -> Result<()> {
        let mut col = Collection::new();

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
        assert_eq!(col.can_undo(), Some(&Op::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // undo a step
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(&Op::UpdateCard));
        assert_eq!(col.can_redo(), Some(&Op::UpdateCard));

        // and again
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 2);
        assert_eq!(col.can_undo(), None);
        assert_eq!(col.can_redo(), Some(&Op::UpdateCard));

        // redo a step
        col.redo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(&Op::UpdateCard));
        assert_eq!(col.can_redo(), Some(&Op::UpdateCard));

        // and another
        col.redo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 4);
        assert_eq!(col.can_undo(), Some(&Op::UpdateCard));
        assert_eq!(col.can_redo(), None);

        // and undo the redo
        col.undo().unwrap();
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 3);
        assert_eq!(col.can_undo(), Some(&Op::UpdateCard));
        assert_eq!(col.can_redo(), Some(&Op::UpdateCard));

        // if any action is performed, it should clear the redo queue
        col.transact(Op::UpdateCard, |col| {
            col.get_and_update_card(cid, |card| {
                card.interval = 5;
                Ok(())
            })
        })?;
        assert_eq!(col.storage.get_card(cid).unwrap().unwrap().interval, 5);
        assert_eq!(col.can_undo(), Some(&Op::UpdateCard));
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

    #[test]
    fn custom() -> Result<()> {
        let mut col = Collection::new();

        // perform some actions in separate steps
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        assert_eq!(col.undo_status().last_step, 1);

        let card = col.storage.all_cards_of_note(note.id)?.remove(0);

        col.transact(Op::UpdateCard, |col| {
            col.get_and_update_card(card.id, |card| {
                card.due = 10;
                Ok(())
            })
        })?;

        let restore_point = col.add_custom_undo_step("hello".to_string());

        col.transact(Op::UpdateCard, |col| {
            col.get_and_update_card(card.id, |card| {
                card.due = 20;
                Ok(())
            })
        })?;
        col.transact(Op::UpdateCard, |col| {
            col.get_and_update_card(card.id, |card| {
                card.due = 30;
                Ok(())
            })
        })?;
        // dummy op name
        col.transact(Op::Bury, |col| col.set_current_notetype_id(NotetypeId(123)))?;

        // merge subsequent changes into our restore point
        let op = col.merge_undoable_ops(restore_point)?;
        assert!(op.changes.card);
        assert!(op.changes.config);

        // the last undo action should be at the end of the step list,
        // before the modtime bump
        assert!(matches!(
            col.state
                .undo
                .previous_op()
                .unwrap()
                .changes
                .iter()
                .rev()
                .nth(1)
                .unwrap(),
            UndoableChange::Config(_)
        ));

        // if we then undo, we'll be back to before step 3
        assert_eq!(col.storage.get_card(card.id)?.unwrap().due, 30);
        col.undo()?;
        assert_eq!(col.storage.get_card(card.id)?.unwrap().due, 10);

        Ok(())
    }

    #[test]
    fn undo_mtime_bump() -> Result<()> {
        let mut col = Collection::new();
        col.storage.db.execute_batch("update col set mod = 0")?;

        // a no-op change should not bump mtime
        let out = col.set_config_bool(BoolKey::AddingDefaultsToCurrentDeck, true, true)?;
        assert_eq!(
            col.storage.get_collection_timestamps()?.collection_change.0,
            0
        );
        assert!(!out.changes.had_change());

        // if there is an undoable step, mtime should change
        let out = col.set_config_bool(BoolKey::AddingDefaultsToCurrentDeck, false, true)?;
        assert_ne!(
            col.storage.get_collection_timestamps()?.collection_change.0,
            0
        );
        assert!(out.changes.had_change());

        // when skipping undo, mtime should still only be bumped on a change
        col.storage.db.execute_batch("update col set mod = 0")?;
        let out = col.set_config_bool(BoolKey::AddingDefaultsToCurrentDeck, false, false)?;
        assert_eq!(
            col.storage.get_collection_timestamps()?.collection_change.0,
            0
        );
        assert!(!out.changes.had_change());

        // op output will reflect changes were made
        let out = col.set_config_bool(BoolKey::AddingDefaultsToCurrentDeck, true, false)?;
        assert_ne!(
            col.storage.get_collection_timestamps()?.collection_change.0,
            0
        );
        assert!(out.changes.had_change());

        Ok(())
    }

    #[test]
    fn coalesce_note_undo_entries() -> Result<()> {
        let mut col = Collection::new();
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, DeckId(1))?;
        note.set_field(0, "foo")?;
        col.update_note(&mut note)?;
        note.set_field(0, "bar")?;
        col.update_note(&mut note)?;
        assert_eq!(col.state.undo.undo_steps.len(), 2);

        Ok(())
    }
}
