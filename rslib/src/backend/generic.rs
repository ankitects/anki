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

impl From<usize> for pb::UInt32 {
    fn from(val: usize) -> Self {
        pb::UInt32 { val: val as u32 }
    }
}

impl From<()> for pb::Empty {
    fn from(_val: ()) -> Self {
        pb::Empty {}
    }
}

impl From<pb::CardId> for CardId {
    fn from(cid: pb::CardId) -> Self {
        CardId(cid.cid)
    }
}

impl From<pb::CardIds> for Vec<CardId> {
    fn from(c: pb::CardIds) -> Self {
        c.cids.into_iter().map(CardId).collect()
    }
}

impl From<pb::NoteId> for NoteId {
    fn from(nid: pb::NoteId) -> Self {
        NoteId(nid.nid)
    }
}

impl From<pb::NotetypeId> for NotetypeId {
    fn from(ntid: pb::NotetypeId) -> Self {
        NotetypeId(ntid.ntid)
    }
}

impl From<NotetypeId> for pb::NotetypeId {
    fn from(ntid: NotetypeId) -> Self {
        pb::NotetypeId { ntid: ntid.0 }
    }
}

impl From<pb::DeckConfigId> for DeckConfigId {
    fn from(dcid: pb::DeckConfigId) -> Self {
        DeckConfigId(dcid.dcid)
    }
}

impl From<Vec<String>> for pb::StringList {
    fn from(vals: Vec<String>) -> Self {
        pb::StringList { vals }
    }
}
