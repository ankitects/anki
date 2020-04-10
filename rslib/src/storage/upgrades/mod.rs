// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::SqliteStorage;
use crate::err::Result;

impl SqliteStorage {
    pub(super) fn upgrade_to_latest_schema(&self, ver: u8) -> Result<()> {
        if ver < 14 {
            self.db
                .execute_batch(include_str!("schema14_upgrade.sql"))?;
            self.upgrade_deck_conf_to_schema14()?;
            self.upgrade_tags_to_schema14()?;
            self.upgrade_config_to_schema14()?;
        }

        Ok(())
    }

    pub(super) fn downgrade_to_schema_11(&self) -> Result<()> {
        self.begin_trx()?;

        self.downgrade_config_from_schema14()?;
        self.downgrade_tags_from_schema14()?;
        self.downgrade_deck_conf_from_schema14()?;
        self.db
            .execute_batch(include_str!("schema11_downgrade.sql"))?;

        self.commit_trx()?;

        Ok(())
    }
}
