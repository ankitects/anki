// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::convert::{TryFrom, TryInto};

use super::Backend;
use crate::prelude::*;
use crate::{
    backend_proto as pb,
    card::{CardQueue, CardType},
};
pub(super) use pb::cards_service::Service as CardsService;

impl CardsService for Backend {
    fn get_card(&self, input: pb::CardId) -> Result<pb::Card> {
        self.with_col(|col| {
            col.storage
                .get_card(input.into())
                .and_then(|opt| opt.ok_or(AnkiError::NotFound))
                .map(Into::into)
        })
    }

    fn update_card(&self, input: pb::UpdateCardIn) -> Result<pb::Empty> {
        self.with_col(|col| {
            let op = if input.skip_undo_entry {
                None
            } else {
                Some(Op::UpdateCard)
            };
            let mut card: Card = input.card.ok_or(AnkiError::NotFound)?.try_into()?;
            col.update_card_with_op(&mut card, op)
        })
        .map(Into::into)
    }

    fn remove_cards(&self, input: pb::RemoveCardsIn) -> Result<pb::Empty> {
        self.with_col(|col| {
            col.transact(None, |col| {
                col.remove_cards_and_orphaned_notes(
                    &input
                        .card_ids
                        .into_iter()
                        .map(Into::into)
                        .collect::<Vec<_>>(),
                )?;
                Ok(().into())
            })
        })
    }

    fn set_deck(&self, input: pb::SetDeckIn) -> Result<pb::Empty> {
        let cids: Vec<_> = input.card_ids.into_iter().map(CardID).collect();
        let deck_id = input.deck_id.into();
        self.with_col(|col| col.set_deck(&cids, deck_id).map(Into::into))
    }
}

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

impl From<Card> for pb::Card {
    fn from(c: Card) -> Self {
        pb::Card {
            id: c.id.0,
            note_id: c.note_id.0,
            deck_id: c.deck_id.0,
            template_idx: c.template_idx as u32,
            mtime_secs: c.mtime.0,
            usn: c.usn.0,
            ctype: c.ctype as u32,
            queue: c.queue as i32,
            due: c.due,
            interval: c.interval,
            ease_factor: c.ease_factor as u32,
            reps: c.reps,
            lapses: c.lapses,
            remaining_steps: c.remaining_steps,
            original_due: c.original_due,
            original_deck_id: c.original_deck_id.0,
            flags: c.flags as u32,
            data: c.data,
        }
    }
}
