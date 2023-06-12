// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::ops::OpChanges;
use crate::prelude::*;
use crate::undo::UndoOutput;
use crate::undo::UndoStatus;

impl From<OpChanges> for anki_proto::collection::OpChanges {
    fn from(c: OpChanges) -> Self {
        anki_proto::collection::OpChanges {
            card: c.changes.card,
            note: c.changes.note,
            deck: c.changes.deck,
            tag: c.changes.tag,
            notetype: c.changes.notetype,
            config: c.changes.config,
            deck_config: c.changes.deck_config,
            mtime: c.changes.mtime,
            browser_table: c.requires_browser_table_redraw(),
            browser_sidebar: c.requires_browser_sidebar_redraw(),
            note_text: c.requires_note_text_redraw(),
            study_queues: c.requires_study_queue_rebuild(),
        }
    }
}

impl UndoStatus {
    pub(crate) fn into_protobuf(self, tr: &I18n) -> anki_proto::collection::UndoStatus {
        anki_proto::collection::UndoStatus {
            undo: self.undo.map(|op| op.describe(tr)).unwrap_or_default(),
            redo: self.redo.map(|op| op.describe(tr)).unwrap_or_default(),
            last_step: self.last_step as u32,
        }
    }
}

impl From<OpOutput<()>> for anki_proto::collection::OpChanges {
    fn from(o: OpOutput<()>) -> Self {
        o.changes.into()
    }
}

impl From<OpOutput<usize>> for anki_proto::collection::OpChangesWithCount {
    fn from(out: OpOutput<usize>) -> Self {
        anki_proto::collection::OpChangesWithCount {
            count: out.output as u32,
            changes: Some(out.changes.into()),
        }
    }
}

impl From<OpOutput<i64>> for anki_proto::collection::OpChangesWithId {
    fn from(out: OpOutput<i64>) -> Self {
        anki_proto::collection::OpChangesWithId {
            id: out.output,
            changes: Some(out.changes.into()),
        }
    }
}

impl OpOutput<UndoOutput> {
    pub(crate) fn into_protobuf(self, tr: &I18n) -> anki_proto::collection::OpChangesAfterUndo {
        anki_proto::collection::OpChangesAfterUndo {
            changes: Some(self.changes.into()),
            operation: self.output.undone_op.describe(tr),
            reverted_to_timestamp: self.output.reverted_to.0,
            new_status: Some(self.output.new_undo_status.into_protobuf(tr)),
            counter: self.output.counter as u32,
        }
    }
}
