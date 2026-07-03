// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug)]
pub(crate) enum UndoableCollectionChange {
    Schema(TimestampMillis),
    Modified(TimestampMillis),
}

impl Collection {
    pub(crate) fn undo_collection_change(
        &mut self,
        change: UndoableCollectionChange,
    ) -> Result<()> {
        match change {
            UndoableCollectionChange::Schema(schema) => {
                let current = self.storage.get_collection_timestamps()?.schema_change;
                self.set_schema_modified_time_undoable(schema, current)
            }
            UndoableCollectionChange::Modified(modified) => {
                let current = self.storage.get_collection_timestamps()?.collection_change;
                self.set_modified_time_undoable(modified, current)
            }
        }
    }

    pub(super) fn set_modified_time_undoable(
        &mut self,
        modified: TimestampMillis,
        original: TimestampMillis,
    ) -> Result<()> {
        self.save_undo(UndoableCollectionChange::Modified(original));
        self.storage.set_modified_time(modified)
    }

    pub(super) fn set_schema_modified_time_undoable(
        &mut self,
        schema: TimestampMillis,
        original: TimestampMillis,
    ) -> Result<()> {
        self.save_undo(UndoableCollectionChange::Schema(original));
        self.storage.set_schema_modified_time(schema)
    }
}
