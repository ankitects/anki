// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
use crate::card::Card;
use crate::card::CardId;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::card::FsrsMemoryState;
use crate::collection::Collection;
use crate::decks::DeckId;
use crate::error;
use crate::error::AnkiError;
use crate::error::OrInvalid;
use crate::error::OrNotFound;
use crate::notes::NoteId;
use crate::prelude::TimestampSecs;
use crate::prelude::Usn;
use crate::undo::Op;

impl crate::services::CardsService for Collection {
    fn get_card(
        &mut self,
        input: anki_proto::cards::CardId,
    ) -> error::Result<anki_proto::cards::Card> {
        let cid = input.into();

        self.storage
            .get_card(cid)
            .and_then(|opt| opt.or_not_found(cid))
            .map(Into::into)
    }

    fn update_cards(
        &mut self,
        input: anki_proto::cards::UpdateCardsRequest,
    ) -> error::Result<anki_proto::collection::OpChanges> {
        let cards = input
            .cards
            .into_iter()
            .map(TryInto::try_into)
            .collect::<error::Result<Vec<Card>, AnkiError>>()?;
        for card in &cards {
            card.validate_custom_data()?;
        }
        self.update_cards_maybe_undoable(cards, !input.skip_undo_entry)
            .map(Into::into)
    }

    fn remove_cards(
        &mut self,
        input: anki_proto::cards::RemoveCardsRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        self.transact(Op::EmptyCards, |col| {
            col.remove_cards_and_orphaned_notes(
                &input
                    .card_ids
                    .into_iter()
                    .map(Into::into)
                    .collect::<Vec<_>>(),
            )
        })
        .map(Into::into)
    }

    fn set_deck(
        &mut self,
        input: anki_proto::cards::SetDeckRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        let cids: Vec<_> = input.card_ids.into_iter().map(CardId).collect();
        let deck_id = input.deck_id.into();
        self.set_deck(&cids, deck_id).map(Into::into)
    }

    fn set_flag(
        &mut self,
        input: anki_proto::cards::SetFlagRequest,
    ) -> error::Result<anki_proto::collection::OpChangesWithCount> {
        self.set_card_flag(&to_card_ids(input.card_ids), input.flag)
            .map(Into::into)
    }
}

impl TryFrom<anki_proto::cards::Card> for Card {
    type Error = AnkiError;

    fn try_from(c: anki_proto::cards::Card) -> error::Result<Self, Self::Error> {
        let ctype = CardType::try_from(c.ctype as u8).or_invalid("invalid card type")?;
        let queue = CardQueue::try_from(c.queue as i8).or_invalid("invalid card queue")?;
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
            original_position: c.original_position,
            memory_state: c.memory_state.map(Into::into),
            desired_retention: c.desired_retention,
            decay: c.decay,
            last_review_time: c.last_review_time_secs.map(TimestampSecs),
            custom_data: c.custom_data,
        })
    }
}

impl From<Card> for anki_proto::cards::Card {
    fn from(c: Card) -> Self {
        anki_proto::cards::Card {
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
            original_position: c.original_position,
            memory_state: c.memory_state.map(Into::into),
            desired_retention: c.desired_retention,
            decay: c.decay,
            last_review_time_secs: c.last_review_time.map(|t| t.0),
            custom_data: c.custom_data,
        }
    }
}

fn to_card_ids(v: Vec<i64>) -> Vec<CardId> {
    v.into_iter().map(CardId).collect()
}

impl From<anki_proto::cards::CardId> for CardId {
    fn from(cid: anki_proto::cards::CardId) -> Self {
        CardId(cid.cid)
    }
}

impl From<anki_proto::cards::FsrsMemoryState> for FsrsMemoryState {
    fn from(value: anki_proto::cards::FsrsMemoryState) -> Self {
        FsrsMemoryState {
            stability: value.stability,
            difficulty: value.difficulty,
        }
    }
}

impl From<FsrsMemoryState> for anki_proto::cards::FsrsMemoryState {
    fn from(value: FsrsMemoryState) -> Self {
        anki_proto::cards::FsrsMemoryState {
            stability: value.stability,
            difficulty: value.difficulty,
        }
    }
}
