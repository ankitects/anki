// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;
use crate::sync::collection::normal::ClientSyncState;
use crate::sync::collection::normal::NormalSyncer;
use crate::sync::collection::protocol::EmptyInput;
use crate::sync::collection::protocol::SyncProtocol;

impl NormalSyncer<'_> {
    pub(in crate::sync) async fn finalize(&mut self, state: &ClientSyncState) -> Result<()> {
        let new_server_mtime = self.server.finish(EmptyInput::request()).await?.json()?;
        self.col.finalize_sync(state, new_server_mtime)
    }
}

impl Collection {
    fn finalize_sync(
        &self,
        state: &ClientSyncState,
        new_server_mtime: TimestampMillis,
    ) -> Result<()> {
        self.storage.set_last_sync(new_server_mtime)?;
        let mut usn = state.server_usn;
        usn.0 += 1;
        self.storage.set_usn(usn)?;
        self.storage.set_modified_time(new_server_mtime)
    }
}

pub fn server_finish(col: &mut Collection) -> Result<TimestampMillis> {
    let now = TimestampMillis::now();
    col.storage.set_last_sync(now)?;
    col.storage.increment_usn()?;
    col.storage.commit_rust_trx()?;
    col.storage.set_modified_time(now)?;
    Ok(now)
}
