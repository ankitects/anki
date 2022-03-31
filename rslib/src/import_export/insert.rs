// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::gather::ExchangeData;
use crate::{prelude::*, revlog::RevlogEntry};

impl Collection {
    pub(super) fn insert_data(&mut self, data: &ExchangeData) -> Result<()> {
        self.insert_decks(&data.decks)?;
        self.insert_notes(&data.notes)?;
        self.insert_cards(&data.cards)?;
        self.insert_notetypes(&data.notetypes)?;
        self.insert_revlog(&data.revlog)?;
        self.insert_deck_configs(&data.decks_configs)?;
        Ok(())
    }

    fn insert_decks(&mut self, decks: &[Deck]) -> Result<()> {
        for deck in decks {
            self.storage.add_or_update_deck_with_existing_id(deck)?;
        }
        Ok(())
    }

    fn insert_notes(&mut self, notes: &[Note]) -> Result<()> {
        for note in notes {
            self.storage.add_or_update_note(note)?;
        }
        Ok(())
    }

    fn insert_cards(&mut self, cards: &[Card]) -> Result<()> {
        for card in cards {
            self.storage.add_or_update_card(card)?;
        }
        Ok(())
    }

    fn insert_notetypes(&mut self, notetypes: &[Notetype]) -> Result<()> {
        for notetype in notetypes {
            self.storage
                .add_or_update_notetype_with_existing_id(notetype)?;
        }
        Ok(())
    }

    fn insert_revlog(&mut self, revlog: &[RevlogEntry]) -> Result<()> {
        for entry in revlog {
            self.storage.add_revlog_entry(entry, false)?;
        }
        Ok(())
    }

    fn insert_deck_configs(&mut self, configs: &[DeckConfig]) -> Result<()> {
        for config in configs {
            self.storage
                .add_or_update_deck_config_with_existing_id(config)?;
        }
        Ok(())
    }
}
