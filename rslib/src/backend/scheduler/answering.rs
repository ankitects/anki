// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto as pb,
    prelude::*,
    scheduler::{
        answering::{CardAnswer, Rating},
        queue::{QueuedCard, QueuedCards},
    },
};

impl From<pb::AnswerCardIn> for CardAnswer {
    fn from(answer: pb::AnswerCardIn) -> Self {
        CardAnswer {
            card_id: CardID(answer.card_id),
            rating: answer.rating().into(),
            current_state: answer.current_state.unwrap_or_default().into(),
            new_state: answer.new_state.unwrap_or_default().into(),
            answered_at: TimestampMillis(answer.answered_at_millis),
            milliseconds_taken: answer.milliseconds_taken,
        }
    }
}

impl From<pb::answer_card_in::Rating> for Rating {
    fn from(rating: pb::answer_card_in::Rating) -> Self {
        match rating {
            pb::answer_card_in::Rating::Again => Rating::Again,
            pb::answer_card_in::Rating::Hard => Rating::Hard,
            pb::answer_card_in::Rating::Good => Rating::Good,
            pb::answer_card_in::Rating::Easy => Rating::Easy,
        }
    }
}

impl From<QueuedCard> for pb::get_queued_cards_out::QueuedCard {
    fn from(queued_card: QueuedCard) -> Self {
        Self {
            card: Some(queued_card.card.into()),
            next_states: Some(queued_card.next_states.into()),
            queue: queued_card.kind as i32,
        }
    }
}

impl From<QueuedCards> for pb::get_queued_cards_out::QueuedCards {
    fn from(queued_cards: QueuedCards) -> Self {
        Self {
            cards: queued_cards.cards.into_iter().map(Into::into).collect(),
            new_count: queued_cards.new_count as u32,
            learning_count: queued_cards.learning_count as u32,
            review_count: queued_cards.review_count as u32,
        }
    }
}
