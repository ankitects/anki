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
    pub(crate) fn set_modified(&mut self) -> Result<()> {
        let stamps = self.storage.get_collection_timestamps()?;
        self.set_modified_time_undoable(TimestampMillis::now(), stamps.collection_change)
    }

    pub(crate) fn set_schema_modified(&mut self) -> Result<()> {
        let stamps = self.storage.get_collection_timestamps()?;
        self.set_schema_modified_time_undoable(TimestampMillis::now(), stamps.schema_change)
    }
}
