// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

#[cfg(test)]
mod test {
    use crate::{
        card::{CardQueue, CardType},
        collection::open_test_collection,
        deckconf::LeechAction,
        prelude::*,
        scheduler::answering::{CardAnswer, Rating},
    };

    #[test]
    fn undo() -> Result<()> {
        // add a note
        let mut col = open_test_collection();
        let nt = col
            .get_notetype_by_name("Basic (and reversed card)")?
            .unwrap();
        let mut note = nt.new_note();
        note.set_field(0, "one")?;
        note.set_field(1, "two")?;
        col.add_note(&mut note, DeckID(1))?;

        // turn burying and leech suspension on
        let mut conf = col.storage.get_deck_config(DeckConfID(1))?.unwrap();
        conf.inner.bury_new = true;
        conf.inner.leech_action = LeechAction::Suspend as i32;
        col.storage.update_deck_conf(&conf)?;

        // get the first card
        let queued = col.next_card()?.unwrap();
        let nid = note.id;
        let cid = queued.card.id;
        let sibling_cid = col.storage.all_card_ids_of_note(nid)?[1];

        let assert_initial_state = |col: &mut Collection| -> Result<()> {
            let first = col.storage.get_card(cid)?.unwrap();
            assert_eq!(first.queue, CardQueue::New);
            let sibling = col.storage.get_card(sibling_cid)?.unwrap();
            assert_eq!(sibling.queue, CardQueue::New);
            Ok(())
        };

        assert_initial_state(&mut col)?;

        // immediately graduate the first card
        col.answer_card(&CardAnswer {
            card_id: queued.card.id,
            current_state: queued.next_states.current,
            new_state: queued.next_states.easy,
            rating: Rating::Easy,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
        })?;

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
        let queued = col.next_card()?.unwrap();
        dbg!(&queued);
        col.answer_card(&CardAnswer {
            card_id: queued.card.id,
            current_state: queued.next_states.current,
            new_state: queued.next_states.again,
            rating: Rating::Again,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
        })?;

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

            let deck = col.get_deck(DeckID(1))?.unwrap();
            assert_eq!(deck.common.review_studied, 1);

            dbg!(&col.next_card()?);
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

            let deck = col.get_deck(DeckID(1))?.unwrap();
            assert_eq!(deck.common.review_studied, 0);

            assert_eq!(col.next_card()?.is_some(), true);

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
}
