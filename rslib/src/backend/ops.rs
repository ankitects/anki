// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use pb::op_changes::Kind;

use crate::{
    backend_proto as pb,
    ops::OpChanges,
    prelude::*,
    undo::{UndoOutput, UndoStatus},
};

impl From<Op> for Kind {
    fn from(o: Op) -> Self {
        match o {
            Op::SetFlag => Kind::SetCardFlag,
            Op::UpdateTag => Kind::UpdateNoteTags,
            Op::UpdateNote => Kind::UpdateNote,
            _ => Kind::Other,
        }
    }
}

impl From<OpChanges> for pb::OpChanges {
    fn from(c: OpChanges) -> Self {
        pb::OpChanges {
            kind: Kind::from(c.op) as i32,
            card: c.changes.card,
            note: c.changes.note,
            deck: c.changes.deck,
            tag: c.changes.tag,
            notetype: c.changes.notetype,
            preference: c.changes.preference,
        }
    }
}

impl UndoStatus {
    pub(crate) fn into_protobuf(self, tr: &I18n) -> pb::UndoStatus {
        pb::UndoStatus {
            undo: self.undo.map(|op| op.describe(tr)).unwrap_or_default(),
            redo: self.redo.map(|op| op.describe(tr)).unwrap_or_default(),
        }
    }
}

impl From<OpOutput<()>> for pb::OpChanges {
    fn from(o: OpOutput<()>) -> Self {
        o.changes.into()
    }
}

impl From<OpOutput<usize>> for pb::OpChangesWithCount {
    fn from(out: OpOutput<usize>) -> Self {
        pb::OpChangesWithCount {
            count: out.output as u32,
            changes: Some(out.changes.into()),
        }
    }
}

impl From<OpOutput<i64>> for pb::OpChangesWithId {
    fn from(out: OpOutput<i64>) -> Self {
        pb::OpChangesWithId {
            id: out.output,
            changes: Some(out.changes.into()),
        }
    }
}

impl OpOutput<UndoOutput> {
    pub(crate) fn into_protobuf(self, tr: &I18n) -> pb::OpChangesAfterUndo {
        pb::OpChangesAfterUndo {
            changes: Some(self.changes.into()),
            operation: self.output.undone_op.describe(tr),
            reverted_to_timestamp: self.output.reverted_to.0,
            new_status: Some(self.output.new_undo_status.into_protobuf(tr)),
        }
    }
}
