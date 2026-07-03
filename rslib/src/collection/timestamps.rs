// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

pub(crate) struct CollectionTimestamps {
    pub collection_change: TimestampMillis,
    pub schema_change: TimestampMillis,
    pub last_sync: TimestampMillis,
}

impl CollectionTimestamps {
    pub fn collection_changed_since_sync(&self) -> bool {
        self.collection_change > self.last_sync
    }

    pub fn schema_changed_since_sync(&self) -> bool {
        self.schema_change > self.last_sync
    }
}

impl Collection {
    /// This is done automatically when you call collection methods, so callers
    /// outside this crate should only need this if you are manually
    /// modifying the database.
    pub fn set_modified(&mut self) -> Result<()> {
        let stamps = self.storage.get_collection_timestamps()?;
        self.set_modified_time_undoable(TimestampMillis::now(), stamps.collection_change)
    }

    /// Forces the next sync in one direction.
    pub fn set_schema_modified(&mut self) -> Result<()> {
        let stamps = self.storage.get_collection_timestamps()?;
        self.set_schema_modified_time_undoable(TimestampMillis::now(), stamps.schema_change)
    }

    pub fn changed_since_last_backup(&self) -> Result<bool> {
        let stamps = self.storage.get_collection_timestamps()?;
        Ok(self
            .state
            .last_backup_modified
            .map(|last_backup| last_backup != stamps.collection_change)
            .unwrap_or(true))
    }

    pub(crate) fn update_last_backup_timestamp(&mut self) -> Result<()> {
        self.state.last_backup_modified =
            Some(self.storage.get_collection_timestamps()?.collection_change);
        Ok(())
    }
}
