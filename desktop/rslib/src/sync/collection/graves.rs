// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use serde::Deserialize;
use serde::Serialize;

use crate::prelude::*;
use crate::sync::collection::chunks::CHUNK_SIZE;
use crate::sync::collection::start::ServerSyncState;

#[derive(Serialize, Deserialize, Debug, Default, Clone)]
pub struct ApplyGravesRequest {
    pub chunk: Graves,
}

#[derive(Serialize, Deserialize, Debug, Default, Clone)]
pub struct Graves {
    pub(crate) cards: Vec<CardId>,
    pub(crate) decks: Vec<DeckId>,
    pub(crate) notes: Vec<NoteId>,
}

impl Graves {
    pub(in crate::sync) fn take_chunk(&mut self) -> Option<Graves> {
        let mut limit = CHUNK_SIZE;
        let mut out = Graves::default();
        while limit > 0 && !self.cards.is_empty() {
            out.cards.push(self.cards.pop().unwrap());
            limit -= 1;
        }
        while limit > 0 && !self.notes.is_empty() {
            out.notes.push(self.notes.pop().unwrap());
            limit -= 1;
        }
        while limit > 0 && !self.decks.is_empty() {
            out.decks.push(self.decks.pop().unwrap());
            limit -= 1;
        }
        if limit == CHUNK_SIZE {
            None
        } else {
            Some(out)
        }
    }
}

impl Collection {
    pub fn apply_graves(&self, graves: Graves, latest_usn: Usn) -> Result<()> {
        for nid in graves.notes {
            self.storage.remove_note(nid)?;
            self.storage.add_note_grave(nid, latest_usn)?;
        }
        for cid in graves.cards {
            self.storage.remove_card(cid)?;
            self.storage.add_card_grave(cid, latest_usn)?;
        }
        for did in graves.decks {
            self.storage.remove_deck(did)?;
            self.storage.add_deck_grave(did, latest_usn)?;
        }
        Ok(())
    }
}

pub fn server_apply_graves(
    req: ApplyGravesRequest,
    col: &mut Collection,
    state: &mut ServerSyncState,
) -> Result<()> {
    col.apply_graves(req.chunk, state.server_usn)
}
