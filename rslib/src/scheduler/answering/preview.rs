// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    card::CardQueue,
    prelude::*,
    scheduler::states::{CardState, IntervalKind, PreviewState},
};

use super::{CardStateUpdater, RevlogEntryPartial};

impl CardStateUpdater {
    // fixme: check learning card moved into preview
    // restores correctly in both learn and day-learn case
    pub(super) fn apply_preview_state(
        &mut self,
        current: CardState,
        next: PreviewState,
    ) -> Result<Option<RevlogEntryPartial>> {
        self.ensure_filtered()?;
        self.card.queue = CardQueue::PreviewRepeat;

        let interval = next.interval_kind();
        match interval {
            IntervalKind::InSecs(secs) => {
                self.card.due = self.now.0 as i32 + secs as i32;
            }
            IntervalKind::InDays(_days) => {
                // unsupported
            }
        }

        Ok(RevlogEntryPartial::maybe_new(
            current,
            next.into(),
            0.0,
            self.secs_until_rollover(),
        ))
    }
}

#[cfg(test)]
mod test {
    use crate::collection::open_test_collection;

    use super::*;
    use crate::{
        card::CardType,
        scheduler::{
            answering::{CardAnswer, Rating},
            states::{CardState, FilteredState, LearnState, NormalState},
        },
        timestamp::TimestampMillis,
    };

    #[test]
    fn preview() -> Result<()> {
        let mut col = open_test_collection();
        dbg!(col.scheduler_version());
        let mut c = Card {
            deck_id: DeckID(1),
            ctype: CardType::Learn,
            queue: CardQueue::Learn,
            remaining_steps: 2,
            ..Default::default()
        };
        col.add_card(&mut c)?;

        // set the first (current) step to a day
        let deck = col.storage.get_deck(DeckID(1))?.unwrap();
        let mut conf = col
            .get_deck_config(DeckConfID(deck.normal()?.config_id), false)?
            .unwrap();
        *conf.inner.learn_steps.get_mut(0).unwrap() = 24.0 * 60.0;
        col.add_or_update_deck_config(&mut conf, false)?;

        // pull the card into a preview deck
        let mut filtered_deck = Deck::new_filtered();
        filtered_deck.filtered_mut()?.reschedule = false;
        col.add_or_update_deck(&mut filtered_deck)?;
        assert_eq!(col.rebuild_filtered_deck(filtered_deck.id)?, 1);

        // the original state reflects the learning steps, not the card properties
        let next = col.get_next_card_states(c.id)?;
        assert_eq!(
            next.current,
            CardState::Filtered(FilteredState::Preview(PreviewState {
                scheduled_secs: 600,
                original_state: NormalState::Learning(LearnState {
                    remaining_steps: 2,
                    scheduled_secs: 86_400,
                }),
            }))
        );

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

        // and then it should return to its old state once passed
        // (based on learning steps)
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
        assert_eq!(c.queue, CardQueue::DayLearn);
        assert_eq!(c.due, 1);

        Ok(())
    }
}
