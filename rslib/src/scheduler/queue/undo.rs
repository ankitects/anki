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

    pub(super) fn pop_undo_entry(&mut self, id: CardId) -> Option<QueueEntry> {
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

#[cfg(test)]
mod test {
    use crate::{
        card::{CardQueue, CardType},
        collection::open_test_collection,
        deckconfig::LeechAction,
        prelude::*,
    };

    fn add_note(col: &mut Collection, with_reverse: bool) -> Result<NoteId> {
        let nt = col
            .get_notetype_by_name("Basic (and reversed card)")?
            .unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "one")?;
        if with_reverse {
            note.set_field(1, "two")?;
        }
        col.add_note(&mut note, DeckId(1))?;
        Ok(note.id)
    }

    #[test]
    fn undo() -> Result<()> {
        // add a note
        let mut col = open_test_collection();
        let nid = add_note(&mut col, true)?;

        // turn burying and leech suspension on
        let mut conf = col.storage.get_deck_config(DeckConfigId(1))?.unwrap();
        conf.inner.bury_new = true;
        conf.inner.leech_action = LeechAction::Suspend as i32;
        col.storage.update_deck_conf(&conf)?;

        // get the first card
        let queued = col.next_card()?.unwrap();
        let cid = queued.card.id;
        let sibling_cid = col.storage.all_card_ids_of_note_in_order(nid)?[1];

        let assert_initial_state = |col: &mut Collection| -> Result<()> {
            let first = col.storage.get_card(cid)?.unwrap();
            assert_eq!(first.queue, CardQueue::New);
            let sibling = col.storage.get_card(sibling_cid)?.unwrap();
            assert_eq!(sibling.queue, CardQueue::New);
            Ok(())
        };

        assert_initial_state(&mut col)?;

        // immediately graduate the first card
        col.answer_easy();

        // the sibling will be buried
        let sibling = col.storage.get_card(sibling_cid)?.unwrap();
        assert_eq!(sibling.queue, CardQueue::SchedBuried);

        // make it due now, with 7 lapses. we use the storage layer directly,
        // bypassing undo
        let mut card = col.storage.get_card(cid)?.unwrap();
        assert_eq!(card.ctype, CardType::Review);
        card.lapses = 7;
        card.due = 0;
        col.storage.update_card(&card)?;

        // fail it, which should cause it to be marked as a leech
        col.clear_study_queues();
        col.answer_again();

        let assert_post_review_state = |col: &mut Collection| -> Result<()> {
            let card = col.storage.get_card(cid)?.unwrap();
            assert_eq!(card.interval, 1);
            assert_eq!(card.lapses, 8);

            assert_eq!(
                col.storage.get_all_revlog_entries(TimestampSecs(0))?.len(),
                2
            );

            let note = col.storage.get_note(nid)?.unwrap();
            assert_eq!(note.tags, vec!["leech".to_string()]);
            assert_eq!(col.storage.all_tags()?.is_empty(), false);

            let deck = col.get_deck(DeckId(1))?.unwrap();
            assert_eq!(deck.common.review_studied, 1);

            assert_eq!(col.next_card()?.is_some(), false);

            Ok(())
        };

        let assert_pre_review_state = |col: &mut Collection| -> Result<()> {
            // the card should have its old state, but a new mtime (which we can't
            // easily test without waiting)
            let card = col.storage.get_card(cid)?.unwrap();
            assert_eq!(card.interval, 4);
            assert_eq!(card.lapses, 7);

            // the revlog entry should have been removed
            assert_eq!(
                col.storage.get_all_revlog_entries(TimestampSecs(0))?.len(),
                1
            );

            // the note should no longer be tagged as a leech
            let note = col.storage.get_note(nid)?.unwrap();
            assert_eq!(note.tags.is_empty(), true);
            assert_eq!(col.storage.all_tags()?.is_empty(), true);

            let deck = col.get_deck(DeckId(1))?.unwrap();
            assert_eq!(deck.common.review_studied, 0);

            assert_eq!(col.next_card()?.is_some(), true);

            let q = col.get_queue_single()?;
            assert_eq!(&[q.new_count, q.learning_count, q.review_count], &[0, 0, 1]);

            Ok(())
        };

        // ensure everything is restored on undo/redo
        assert_post_review_state(&mut col)?;
        col.undo()?;
        assert_pre_review_state(&mut col)?;
        col.redo()?;
        assert_post_review_state(&mut col)?;
        col.undo()?;
        assert_pre_review_state(&mut col)?;
        col.undo()?;
        assert_initial_state(&mut col)?;

        Ok(())
    }

    #[test]
    #[ignore = "undo code is currently broken"]
    fn undo_counts1() -> Result<()> {
        let mut col = open_test_collection();

        assert_eq!(col.counts(), [0, 0, 0]);
        add_note(&mut col, true)?;
        assert_eq!(col.counts(), [2, 0, 0]);
        col.answer_good();
        assert_eq!(col.counts(), [1, 1, 0]);
        col.answer_good();
        assert_eq!(col.counts(), [0, 2, 0]);
        col.answer_good();
        assert_eq!(col.counts(), [0, 1, 0]);
        col.answer_good();
        assert_eq!(col.counts(), [0, 0, 0]);

        // now work backwards
        col.undo()?;
        assert_eq!(col.counts(), [0, 1, 0]);
        col.undo()?;
        assert_eq!(col.counts(), [0, 2, 0]);
        col.undo()?;
        assert_eq!(col.counts(), [1, 1, 0]);
        col.undo()?;
        assert_eq!(col.counts(), [2, 0, 0]);
        col.undo()?;
        assert_eq!(col.counts(), [0, 0, 0]);

        Ok(())
    }

    #[test]
    fn undo_counts2() -> Result<()> {
        let mut col = open_test_collection();

        assert_eq!(col.counts(), [0, 0, 0]);
        add_note(&mut col, true)?;
        assert_eq!(col.counts(), [2, 0, 0]);
        col.answer_again();
        assert_eq!(col.counts(), [1, 1, 0]);
        col.answer_again();
        assert_eq!(col.counts(), [0, 2, 0]);
        col.answer_again();
        assert_eq!(col.counts(), [0, 2, 0]);
        col.answer_again();
        assert_eq!(col.counts(), [0, 2, 0]);
        col.answer_good();
        assert_eq!(col.counts(), [0, 2, 0]);
        col.answer_easy();
        assert_eq!(col.counts(), [0, 1, 0]);
        col.answer_good();
        // last card, due in a minute
        assert_eq!(col.counts(), [0, 0, 0]);

        Ok(())
    }

    #[test]
    fn undo_counts_relearn() -> Result<()> {
        let mut col = open_test_collection();

        add_note(&mut col, true)?;
        col.storage
            .db
            .execute_batch("update cards set due=0,queue=2,type=2")?;
        assert_eq!(col.counts(), [0, 0, 2]);
        col.answer_again();
        assert_eq!(col.counts(), [0, 1, 1]);
        col.answer_again();
        assert_eq!(col.counts(), [0, 2, 0]);
        col.answer_easy();
        assert_eq!(col.counts(), [0, 1, 0]);
        col.answer_easy();
        assert_eq!(col.counts(), [0, 0, 0]);

        Ok(())
    }
}
