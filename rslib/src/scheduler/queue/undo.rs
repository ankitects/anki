// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{CardQueues, LearningQueueEntry, QueueEntry, QueueEntryKind};
use crate::{prelude::*, undo::Undo};

#[derive(Debug)]
pub(super) struct QueueUpdateAfterAnsweringCard {
    pub entry: QueueEntry,
    pub learning_requeue: Option<LearningQueueEntry>,
}

impl Undo for QueueUpdateAfterAnsweringCard {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        let queues = col.get_queues()?;
        if let Some(learning) = self.learning_requeue {
            queues.remove_requeued_learning_card_after_undo(learning.id);
        }
        queues.push_undo_entry(self.entry);
        col.save_undo(Box::new(QueueUpdateAfterUndoingAnswer {
            entry: self.entry,
            learning_requeue: self.learning_requeue,
        }));

        Ok(())
    }
}

#[derive(Debug)]
pub(super) struct QueueUpdateAfterUndoingAnswer {
    pub entry: QueueEntry,
    pub learning_requeue: Option<LearningQueueEntry>,
}

impl Undo for QueueUpdateAfterUndoingAnswer {
    fn undo(self: Box<Self>, col: &mut Collection) -> Result<()> {
        // don't try to update existing queue when redoing; just
        // rebuild it instead
        col.clear_study_queues();
        // but preserve undo state for a subsequent undo
        col.save_undo(Box::new(QueueUpdateAfterAnsweringCard {
            entry: self.entry,
            learning_requeue: self.learning_requeue,
        }));

        Ok(())
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
