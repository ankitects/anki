// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{
    backend_proto as pb,
    prelude::*,
    sched::answering::{CardAnswer, Rating},
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
