// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug)]

pub(crate) enum UndoableDeckConfigChange {
    Added(Box<DeckConf>),
    Updated(Box<DeckConf>),
    Removed(Box<DeckConf>),
}

impl Collection {
    pub(crate) fn undo_deck_config_change(
        &mut self,
        change: UndoableDeckConfigChange,
    ) -> Result<()> {
        match change {
            UndoableDeckConfigChange::Added(config) => self.remove_deck_config_undoable(*config),
            UndoableDeckConfigChange::Updated(config) => {
                let current = self
                    .storage
                    .get_deck_config(config.id)?
                    .ok_or_else(|| AnkiError::invalid_input("deck config disappeared"))?;
                self.update_deck_config_undoable(&config, current)
            }
            UndoableDeckConfigChange::Removed(config) => self.restore_deleted_deck_config(*config),
        }
    }

    pub(crate) fn remove_deck_config_undoable(&mut self, config: DeckConf) -> Result<()> {
        self.storage.remove_deck_conf(config.id)?;
        self.save_undo(UndoableDeckConfigChange::Removed(Box::new(config)));
        Ok(())
    }

    pub(super) fn add_deck_config_undoable(
        &mut self,
        config: &mut DeckConf,
    ) -> Result<(), AnkiError> {
        self.storage.add_deck_conf(config)?;
        self.save_undo(UndoableDeckConfigChange::Added(Box::new(config.clone())));
        Ok(())
    }

    pub(super) fn update_deck_config_undoable(
        &mut self,
        config: &DeckConf,
        original: DeckConf,
    ) -> Result<()> {
        self.save_undo(UndoableDeckConfigChange::Updated(Box::new(original)));
        self.storage.update_deck_conf(config)
    }

    fn restore_deleted_deck_config(&mut self, config: DeckConf) -> Result<()> {
        self.storage
            .add_or_update_deck_config_with_existing_id(&config)?;
        self.save_undo(UndoableDeckConfigChange::Added(Box::new(config)));
        Ok(())
    }
}
