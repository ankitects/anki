// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::adding::DeckAndNotetype;
use crate::backend_proto::DeckAndNotetype as DeckAndNotetypeProto;

impl From<DeckAndNotetype> for DeckAndNotetypeProto {
    fn from(s: DeckAndNotetype) -> Self {
        DeckAndNotetypeProto {
            deck_id: s.deck_id.0,
            notetype_id: s.notetype_id.0,
        }
    }
}
