// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::{collection::Collection, err::Result};

impl Collection {
    pub(crate) fn check_database(&mut self) -> Result<()> {
        let names = self.storage.get_all_deck_names()?;
        self.add_missing_decks(&names)?;
        Ok(())
    }
}
