// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::notes::DeckAndNotetype as DeckAndNotetypeProto;

use crate::adding::DeckAndNotetype;

impl From<DeckAndNotetype> for DeckAndNotetypeProto {
    fn from(s: DeckAndNotetype) -> Self {
        DeckAndNotetypeProto {
            deck_id: s.deck_id.0,
            notetype_id: s.notetype_id.0,
        }
    }
}
