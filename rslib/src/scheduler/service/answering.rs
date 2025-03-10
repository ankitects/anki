// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::mem;

use crate::prelude::*;
use crate::scheduler::answering::CardAnswer;
use crate::scheduler::answering::Rating;
use crate::scheduler::queue::QueuedCard;
use crate::scheduler::queue::QueuedCards;

impl From<anki_proto::scheduler::CardAnswer> for CardAnswer {
    fn from(mut answer: anki_proto::scheduler::CardAnswer) -> Self {
        let mut new_state = mem::take(&mut answer.new_state).unwrap_or_default();
        let custom_data = mem::take(&mut new_state.custom_data);
        CardAnswer {
            card_id: CardId(answer.card_id),
            rating: answer.rating().into(),
            current_state: answer.current_state.unwrap_or_default().into(),
            new_state: new_state.into(),
            answered_at: TimestampMillis(answer.answered_at_millis),
            milliseconds_taken: answer.milliseconds_taken,
            custom_data,
            from_queue: answer.from_queue,
        }
    }
}

impl From<anki_proto::scheduler::card_answer::Rating> for Rating {
    fn from(rating: anki_proto::scheduler::card_answer::Rating) -> Self {
        match rating {
            anki_proto::scheduler::card_answer::Rating::Again => Rating::Again,
            anki_proto::scheduler::card_answer::Rating::Hard => Rating::Hard,
            anki_proto::scheduler::card_answer::Rating::Good => Rating::Good,
            anki_proto::scheduler::card_answer::Rating::Easy => Rating::Easy,
        }
    }
}

impl From<QueuedCard> for anki_proto::scheduler::queued_cards::QueuedCard {
    fn from(queued_card: QueuedCard) -> Self {
        Self {
            card: Some(queued_card.card.into()),
            states: Some(queued_card.states.into()),
            context: Some(queued_card.context),
            queue: match queued_card.kind {
                crate::scheduler::queue::QueueEntryKind::New => {
                    anki_proto::scheduler::queued_cards::Queue::New
                }
                crate::scheduler::queue::QueueEntryKind::Review => {
                    anki_proto::scheduler::queued_cards::Queue::Review
                }
                crate::scheduler::queue::QueueEntryKind::Learning => {
                    anki_proto::scheduler::queued_cards::Queue::Learning
                }
            } as i32,
        }
    }
}

impl From<QueuedCards> for anki_proto::scheduler::QueuedCards {
    fn from(queued_cards: QueuedCards) -> Self {
        Self {
            cards: queued_cards.cards.into_iter().map(Into::into).collect(),
            new_count: queued_cards.new_count as u32,
            learning_count: queued_cards.learning_count as u32,
            review_count: queued_cards.review_count as u32,
        }
    }
}
