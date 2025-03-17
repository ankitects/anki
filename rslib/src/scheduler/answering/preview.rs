// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::CardStateUpdater;
use super::RevlogEntryPartial;
use crate::card::CardQueue;
use crate::scheduler::states::CardState;
use crate::scheduler::states::IntervalKind;
use crate::scheduler::states::PreviewState;

impl CardStateUpdater {
    pub(super) fn apply_preview_state(
        &mut self,
        current: CardState,
        next: PreviewState,
    ) -> RevlogEntryPartial {
        let revlog = RevlogEntryPartial::new(current, next.into(), 0.0, self.secs_until_rollover());
        if next.finished {
            self.card.remove_from_filtered_deck_restoring_queue();
            return revlog;
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

        revlog
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::card::CardType;
    use crate::prelude::*;
    use crate::scheduler::answering::CardAnswer;
    use crate::scheduler::answering::Rating;
    use crate::scheduler::states::CardState;
    use crate::scheduler::states::FilteredState;
    use crate::timestamp::TimestampMillis;

    #[test]
    fn preview() -> Result<()> {
        let mut col = Collection::new();
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

        let next = col.get_scheduling_states(c.id)?;
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
        assert!(matches!(
            next.good,
            CardState::Filtered(FilteredState::Preview(PreviewState {
                scheduled_secs: 0,
                finished: true
            }))
        ));

        // use Again on the preview
        col.answer_card(&mut CardAnswer {
            card_id: c.id,
            current_state: next.current,
            new_state: next.again,
            rating: Rating::Again,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            from_queue: true,
        })?;

        c = col.storage.get_card(c.id)?.unwrap();
        assert_eq!(c.queue, CardQueue::PreviewRepeat);

        // hard
        let next = col.get_scheduling_states(c.id)?;
        col.answer_card(&mut CardAnswer {
            card_id: c.id,
            current_state: next.current,
            new_state: next.hard,
            rating: Rating::Hard,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            from_queue: true,
        })?;
        c = col.storage.get_card(c.id)?.unwrap();
        assert_eq!(c.queue, CardQueue::PreviewRepeat);

        // and then it should return to its old state once good or easy selected,
        // with the default filtered config
        let next = col.get_scheduling_states(c.id)?;
        col.answer_card(&mut CardAnswer {
            card_id: c.id,
            current_state: next.current,
            new_state: next.good,
            rating: Rating::Good,
            answered_at: TimestampMillis::now(),
            milliseconds_taken: 0,
            custom_data: None,
            from_queue: true,
        })?;
        c = col.storage.get_card(c.id)?.unwrap();
        assert_eq!(c.queue, CardQueue::DayLearn);
        assert_eq!(c.due, 123);

        Ok(())
    }
}
