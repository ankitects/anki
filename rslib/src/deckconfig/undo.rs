// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use crate::prelude::*;

#[derive(Debug)]

pub(crate) enum UndoableDeckConfigChange {
    Added(Box<DeckConfig>),
    Updated(Box<DeckConfig>),
    Removed(Box<DeckConfig>),
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
                    .or_invalid("deck config disappeared")?;
                self.update_deck_config_undoable(&config, current)
            }
            UndoableDeckConfigChange::Removed(config) => self.restore_deleted_deck_config(*config),
        }
    }

    pub(crate) fn remove_deck_config_undoable(&mut self, config: DeckConfig) -> Result<()> {
        self.storage.remove_deck_conf(config.id)?;
        self.save_undo(UndoableDeckConfigChange::Removed(Box::new(config)));
        Ok(())
    }

    pub(super) fn add_deck_config_undoable(
        &mut self,
        config: &mut DeckConfig,
    ) -> Result<(), AnkiError> {
        self.storage.add_deck_conf(config)?;
        self.save_undo(UndoableDeckConfigChange::Added(Box::new(config.clone())));
        Ok(())
    }

    pub(crate) fn add_deck_config_if_unique_undoable(&mut self, config: &DeckConfig) -> Result<()> {
        if self.storage.add_deck_conf_if_unique(config)? {
            self.save_undo(UndoableDeckConfigChange::Added(Box::new(config.clone())));
        }
        Ok(())
    }

    pub(super) fn update_deck_config_undoable(
        &mut self,
        config: &DeckConfig,
        original: DeckConfig,
    ) -> Result<()> {
        self.save_undo(UndoableDeckConfigChange::Updated(Box::new(original)));
        self.storage.update_deck_conf(config)
    }

    fn restore_deleted_deck_config(&mut self, config: DeckConfig) -> Result<()> {
        self.storage
            .add_or_update_deck_config_with_existing_id(&config)?;
        self.save_undo(UndoableDeckConfigChange::Added(Box::new(config)));
        Ok(())
    }
}
