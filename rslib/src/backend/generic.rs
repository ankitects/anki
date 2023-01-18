// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::pb;
use crate::prelude::*;

impl From<Vec<u8>> for pb::generic::Json {
    fn from(json: Vec<u8>) -> Self {
        pb::generic::Json { json }
    }
}

impl From<String> for pb::generic::String {
    fn from(val: String) -> Self {
        pb::generic::String { val }
    }
}

impl From<bool> for pb::generic::Bool {
    fn from(val: bool) -> Self {
        pb::generic::Bool { val }
    }
}

impl From<i32> for pb::generic::Int32 {
    fn from(val: i32) -> Self {
        pb::generic::Int32 { val }
    }
}

impl From<i64> for pb::generic::Int64 {
    fn from(val: i64) -> Self {
        pb::generic::Int64 { val }
    }
}

impl From<u32> for pb::generic::UInt32 {
    fn from(val: u32) -> Self {
        pb::generic::UInt32 { val }
    }
}

impl From<usize> for pb::generic::UInt32 {
    fn from(val: usize) -> Self {
        pb::generic::UInt32 { val: val as u32 }
    }
}

impl From<()> for pb::generic::Empty {
    fn from(_val: ()) -> Self {
        pb::generic::Empty {}
    }
}

impl From<pb::cards::CardId> for CardId {
    fn from(cid: pb::cards::CardId) -> Self {
        CardId(cid.cid)
    }
}

impl From<pb::cards::CardIds> for Vec<CardId> {
    fn from(c: pb::cards::CardIds) -> Self {
        c.cids.into_iter().map(CardId).collect()
    }
}

impl From<pb::notes::NoteId> for NoteId {
    fn from(nid: pb::notes::NoteId) -> Self {
        NoteId(nid.nid)
    }
}

impl From<NoteId> for pb::notes::NoteId {
    fn from(nid: NoteId) -> Self {
        pb::notes::NoteId { nid: nid.0 }
    }
}

impl From<pb::notetypes::NotetypeId> for NotetypeId {
    fn from(ntid: pb::notetypes::NotetypeId) -> Self {
        NotetypeId(ntid.ntid)
    }
}

impl From<NotetypeId> for pb::notetypes::NotetypeId {
    fn from(ntid: NotetypeId) -> Self {
        pb::notetypes::NotetypeId { ntid: ntid.0 }
    }
}

impl From<pb::deckconfig::DeckConfigId> for DeckConfigId {
    fn from(dcid: pb::deckconfig::DeckConfigId) -> Self {
        DeckConfigId(dcid.dcid)
    }
}

impl From<Vec<String>> for pb::generic::StringList {
    fn from(vals: Vec<String>) -> Self {
        pb::generic::StringList { vals }
    }
}
