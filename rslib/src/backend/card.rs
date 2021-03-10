// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::convert::TryFrom;

use crate::prelude::*;
use crate::{
    backend_proto as pb,
    card::{CardQueue, CardType},
};

impl TryFrom<pb::Card> for Card {
    type Error = AnkiError;

    fn try_from(c: pb::Card) -> Result<Self, Self::Error> {
        let ctype = CardType::try_from(c.ctype as u8)
            .map_err(|_| AnkiError::invalid_input("invalid card type"))?;
        let queue = CardQueue::try_from(c.queue as i8)
            .map_err(|_| AnkiError::invalid_input("invalid card queue"))?;
        Ok(Card {
            id: CardID(c.id),
            note_id: NoteID(c.note_id),
            deck_id: DeckID(c.deck_id),
            template_idx: c.template_idx as u16,
            mtime: TimestampSecs(c.mtime_secs),
            usn: Usn(c.usn),
            ctype,
            queue,
            due: c.due,
            interval: c.interval,
            ease_factor: c.ease_factor as u16,
            reps: c.reps,
            lapses: c.lapses,
            remaining_steps: c.remaining_steps,
            original_due: c.original_due,
            original_deck_id: DeckID(c.original_deck_id),
            flags: c.flags as u8,
            data: c.data,
        })
    }
}
