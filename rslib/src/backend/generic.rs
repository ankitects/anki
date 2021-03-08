// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{backend_proto as pb, prelude::*};

impl From<Vec<u8>> for pb::Json {
    fn from(json: Vec<u8>) -> Self {
        pb::Json { json }
    }
}

impl From<String> for pb::String {
    fn from(val: String) -> Self {
        pb::String { val }
    }
}

impl From<bool> for pb::Bool {
    fn from(val: bool) -> Self {
        pb::Bool { val }
    }
}

impl From<i64> for pb::Int64 {
    fn from(val: i64) -> Self {
        pb::Int64 { val }
    }
}

impl From<u32> for pb::UInt32 {
    fn from(val: u32) -> Self {
        pb::UInt32 { val }
    }
}

impl From<()> for pb::Empty {
    fn from(_val: ()) -> Self {
        pb::Empty {}
    }
}

impl From<pb::CardId> for CardID {
    fn from(cid: pb::CardId) -> Self {
        CardID(cid.cid)
    }
}

impl Into<Vec<CardID>> for pb::CardIDs {
    fn into(self) -> Vec<CardID> {
        self.cids.into_iter().map(CardID).collect()
    }
}

impl From<pb::NoteId> for NoteID {
    fn from(nid: pb::NoteId) -> Self {
        NoteID(nid.nid)
    }
}

impl From<pb::NoteTypeId> for NoteTypeID {
    fn from(ntid: pb::NoteTypeId) -> Self {
        NoteTypeID(ntid.ntid)
    }
}

impl From<pb::DeckId> for DeckID {
    fn from(did: pb::DeckId) -> Self {
        DeckID(did.did)
    }
}

impl From<DeckID> for pb::DeckId {
    fn from(did: DeckID) -> Self {
        pb::DeckId { did: did.0 }
    }
}

impl From<pb::DeckConfigId> for DeckConfID {
    fn from(dcid: pb::DeckConfigId) -> Self {
        DeckConfID(dcid.dcid)
    }
}

impl From<Vec<String>> for pb::StringList {
    fn from(vals: Vec<String>) -> Self {
        pb::StringList { vals }
    }
}
