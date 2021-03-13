// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, ops::StateChanges};

impl From<StateChanges> for pb::StateChanges {
    fn from(c: StateChanges) -> Self {
        pb::StateChanges {
            card_added: c.card_added,
            card_modified: c.card_modified,
            note_added: c.note_added,
            note_modified: c.note_modified,
            deck_added: c.deck_added,
            deck_modified: c.deck_modified,
            tag_modified: c.tag_modified,
            notetype_modified: c.notetype_modified,
            preference_modified: c.preference_modified,
        }
    }
}
