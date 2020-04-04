// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::err::Result;

impl SqliteStorage {
    pub(super) fn upgrade_to_latest_schema(&self, ver: u8) -> Result<()> {
        if ver < 12 {
            self.db
                .execute_batch(include_str!("schema12_upgrade.sql"))?;
            self.upgrade_deck_conf_to_schema12()?;
        }
        if ver < 13 {
            self.db
                .execute_batch(include_str!("schema13_upgrade.sql"))?;
            self.upgrade_tags_to_schema12()?;
        }
        Ok(())
    }

    pub(super) fn downgrade_to_schema_11(&self) -> Result<()> {
        self.begin_trx()?;

        self.downgrade_tags_from_schema13()?;
        self.db
            .execute_batch(include_str!("schema13_downgrade.sql"))?;

        self.downgrade_deck_conf_from_schema12()?;
        self.db
            .execute_batch(include_str!("schema12_downgrade.sql"))?;

        self.commit_trx()?;

        Ok(())
    }
}
