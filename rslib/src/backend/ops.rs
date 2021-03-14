// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use pb::operation_info::{Changes, Kind};

use crate::{backend_proto as pb, ops::StateChanges, prelude::*, undo::UndoStatus};

impl From<StateChanges> for Changes {
    fn from(c: StateChanges) -> Self {
        Changes {
            card: c.card,
            note: c.note,
            deck: c.deck,
            tag: c.tag,
            notetype: c.notetype,
            preference: c.preference,
        }
    }
}

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

impl From<Op> for pb::OperationInfo {
    fn from(op: Op) -> Self {
        pb::OperationInfo {
            changes: Some(op.state_changes().into()),
            kind: Kind::from(op) as i32,
        }
    }
}

impl UndoStatus {
    pub(crate) fn into_protobuf(self, i18n: &I18n) -> pb::UndoStatus {
        pb::UndoStatus {
            undo: self.undo.map(|op| op.describe(i18n)).unwrap_or_default(),
            redo: self.redo.map(|op| op.describe(i18n)).unwrap_or_default(),
            last_op: self.undo.map(Into::into),
        }
    }
}
