// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{CardQueues, LearningQueueEntry, QueueEntry, QueueEntryKind};
use crate::prelude::*;

#[derive(Debug)]
pub(crate) enum UndoableQueueChange {
    CardAnswered(Box<QueueUpdate>),
    CardAnswerUndone(Box<QueueUpdate>),
}

#[derive(Debug)]
pub(crate) struct QueueUpdate {
    pub entry: QueueEntry,
    pub learning_requeue: Option<LearningQueueEntry>,
}

impl Collection {
    pub(crate) fn undo_queue_change(&mut self, change: UndoableQueueChange) -> Result<()> {
        match change {
            UndoableQueueChange::CardAnswered(update) => {
                let queues = self.get_queues()?;
                if let Some(learning) = &update.learning_requeue {
                    queues.remove_requeued_learning_card_after_undo(learning.id);
                }
                queues.push_undo_entry(update.entry);
                self.save_undo(UndoableQueueChange::CardAnswerUndone(update));

                Ok(())
            }
            UndoableQueueChange::CardAnswerUndone(update) => {
                // don't try to update existing queue when redoing; just
                // rebuild it instead
                self.clear_study_queues();
                // but preserve undo state for a subsequent undo
                self.save_undo(UndoableQueueChange::CardAnswered(update));

                Ok(())
            }
        }
    }

    pub(super) fn save_queue_update_undo(&mut self, change: Box<QueueUpdate>) {
        self.save_undo(UndoableQueueChange::CardAnswered(change))
    }
}

impl CardQueues {
    pub(super) fn next_undo_entry(&self) -> Option<QueueEntry> {
        self.undo.last().copied()
    }

    pub(super) fn pop_undo_entry(&mut self, id: CardID) -> Option<QueueEntry> {
        if let Some(last) = self.undo.last() {
            if last.card_id() == id {
                match last.kind() {
                    QueueEntryKind::New => self.counts.new -= 1,
                    QueueEntryKind::Review => self.counts.review -= 1,
                    QueueEntryKind::Learning => self.counts.learning -= 1,
                }
                return self.undo.pop();
            }
        }

        None
    }

    /// Add an undone card back to the 'front' of the list, and update
    /// the counts.
    pub(super) fn push_undo_entry(&mut self, entry: QueueEntry) {
        match entry.kind() {
            QueueEntryKind::New => self.counts.new += 1,
            QueueEntryKind::Review => self.counts.review += 1,
            QueueEntryKind::Learning => self.counts.learning += 1,
        }
        self.undo.push(entry);
    }
}
