// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::{CardStateUpdater, RevlogEntryPartial};
use crate::{
    card::CardQueue,
    config::SchedulerVersion,
    scheduler::states::{CardState, IntervalKind, PreviewState},
};

impl CardStateUpdater {
    // fixme: check learning card moved into preview
    // restores correctly in both learn and day-learn case
    pub(super) fn apply_preview_state(
        &mut self,
        current: CardState,
        next: PreviewState,
    ) -> Option<RevlogEntryPartial> {
        if next.finished {
            self.card
                .remove_from_filtered_deck_restoring_queue(SchedulerVersion::V2);
            return None;
        }

        self.card.queue = CardQueue::PreviewRepeat;

        let interval = next.interval_kind();
        match interval {
            IntervalKind::InSecs(secs) => {
                self.card.due = self.fuzzed_next_learning_timestamp(secs);
            }
            IntervalKind::InDays(_days) => {
                // unsupported
            }
        }

        RevlogEntryPartial::maybe_new(current, next.into(), 0.0, self.secs_until_rollover())
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::{
        card::CardType,
        collection::open_test_collection,
        prelude::*,
        scheduler::{
            answering::{CardAnswer, Rating},
            states::{CardState, FilteredState},
        },
        timestamp::TimestampMillis,
    };

    #[test]
    fn preview() -> Result<()> {
        let mut col = open_test_collection();
        let mut c = Card {
            deck_id: DeckId(1),
            ctype: CardType::Learn,
            queue: CardQueue::DayLearn,
            remaining_steps: 2,
            due: 123,
            ..Default::default()
        };
        col.add_card(&mut c)?;

        // pull the card into a preview deck
        let mut filtered_deck = Deck::new_filtered();
        filtered_deck.filtered_mut()?.reschedule = false;
        col.add_or_update_deck(&mut filtered_deck)?;
        assert_eq!(col.rebuild_filtered_deck(filtered_deck.id)?.output, 1);

        let next = col.get_next_card_states(c.id)?;
        assert!(matches!(
            next.current,
            CardState::Filtered(FilteredState::Preview(_))
        ));
        // the exit state should have a 0 second interval, which will show up as (end)
        assert!(matches!(
            next.easy,
            CardState::Filtered(FilteredState::Preview(PreviewState {
                scheduled_secs: 0,
                finished: true
            }))
        ));

        // use Again on the preview
        col.answer_card(&CardAnswer {
            card_id: c.id,
            current_state: next.current,
            new_state: next.again,
            rating: Rating::Again,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
        })?;

        c = col.storage.get_card(c.id)?.unwrap();
        assert_eq!(c.queue, CardQueue::PreviewRepeat);

        // hard
        let next = col.get_next_card_states(c.id)?;
        col.answer_card(&CardAnswer {
            card_id: c.id,
            current_state: next.current,
            new_state: next.hard,
            rating: Rating::Hard,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
        })?;
        c = col.storage.get_card(c.id)?.unwrap();
        assert_eq!(c.queue, CardQueue::PreviewRepeat);

        // good
        let next = col.get_next_card_states(c.id)?;
        col.answer_card(&CardAnswer {
            card_id: c.id,
            current_state: next.current,
            new_state: next.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
        })?;
        c = col.storage.get_card(c.id)?.unwrap();
        assert_eq!(c.queue, CardQueue::PreviewRepeat);

        // and then it should return to its old state once easy selected
        let next = col.get_next_card_states(c.id)?;
        col.answer_card(&CardAnswer {
            card_id: c.id,
            current_state: next.current,
            new_state: next.easy,
            rating: Rating::Easy,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
        })?;
        c = col.storage.get_card(c.id)?.unwrap();
        assert_eq!(c.queue, CardQueue::DayLearn);
        assert_eq!(c.due, 123);

        Ok(())
    }
}
