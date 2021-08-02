// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::convert::{TryFrom, TryInto};

use super::Backend;
pub(super) use crate::backend_proto::cards_service::Service as CardsService;
use crate::{
    backend_proto as pb,
    card::{CardQueue, CardType},
    prelude::*,
};

impl CardsService for Backend {
    fn get_card(&self, input: pb::CardId) -> Result<pb::Card> {
        self.with_col(|col| {
            col.storage
                .get_card(input.into())
                .and_then(|opt| opt.ok_or(AnkiError::NotFound))
                .map(Into::into)
        })
    }

    fn update_cards(&self, input: pb::UpdateCardsRequest) -> Result<pb::OpChanges> {
        self.with_col(|col| {
            let cards = input
                .cards
                .into_iter()
                .map(TryInto::try_into)
                .collect::<Result<Vec<Card>, AnkiError>>()?;
            col.update_cards_maybe_undoable(cards, !input.skip_undo_entry)
        })
        .map(Into::into)
    }

    fn remove_cards(&self, input: pb::RemoveCardsRequest) -> Result<pb::Empty> {
        self.with_col(|col| {
            col.transact_no_undo(|col| {
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

    fn set_deck(&self, input: pb::SetDeckRequest) -> Result<pb::OpChangesWithCount> {
        let cids: Vec<_> = input.card_ids.into_iter().map(CardId).collect();
        let deck_id = input.deck_id.into();
        self.with_col(|col| col.set_deck(&cids, deck_id).map(Into::into))
    }

    fn set_flag(&self, input: pb::SetFlagRequest) -> Result<pb::OpChangesWithCount> {
        self.with_col(|col| {
            col.set_card_flag(&to_card_ids(input.card_ids), input.flag)
                .map(Into::into)
        })
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
            id: CardId(c.id),
            note_id: NoteId(c.note_id),
            deck_id: DeckId(c.deck_id),
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
            original_deck_id: DeckId(c.original_deck_id),
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

fn to_card_ids(v: Vec<i64>) -> Vec<CardId> {
    v.into_iter().map(CardId).collect()
}
