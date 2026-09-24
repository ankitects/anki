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

#[cfg(test)]
mod tests {

    use super::*;

    #[test]
    fn remove_deck_config_undoable_removes_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        let config_id = DeckConfigId(TimestampMillis::now().0);
        config.id.0 = config_id.0;
        col.storage
            .add_or_update_deck_config_with_existing_id(&config)?;
        col.remove_deck_config_undoable(config)?;
        let returned_config = col.storage.get_deck_config(config_id)?;
        assert_eq!(returned_config, None);

        Ok(())
    }

    #[test]
    fn add_deck_config_undoable_adds_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        let config_id = DeckConfigId(TimestampMillis::now().0);
        config.id.0 = config_id.0;
        col.add_deck_config_undoable(&mut config)?;
        let returned_config = col.storage.get_deck_config(config_id)?;
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn add_deck_config_undoable_gives_unique_id() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        let config_id = DeckConfigId(TimestampMillis::now().0);
        config.id.0 = config_id.0;
        // Add same config twice
        col.add_deck_config_undoable(&mut config)?;
        col.add_deck_config_undoable(&mut config)?;
        let returned_config = col.storage.get_deck_config(config_id)?.unwrap();
        assert_ne!(returned_config.id, config.id);

        Ok(())
    }

    #[test]
    fn add_deck_config_if_unique_undoable_adds_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        let config_id = DeckConfigId(TimestampMillis::now().0);
        config.id.0 = config_id.0;
        col.add_deck_config_if_unique_undoable(&config)?;
        let returned_config = col.storage.get_deck_config(config_id)?;
        assert_eq!(returned_config, Some(config));

        Ok(())
    }

    #[test]
    fn add_deck_config_if_unique_undoable_ignores_existing_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        let original_name = config.name.clone();
        let config_id = DeckConfigId(TimestampMillis::now().0);
        config.id.0 = config_id.0;
        // Try to add same config twice
        col.add_deck_config_if_unique_undoable(&config)?;
        config.name = "renamed".into();
        col.add_deck_config_if_unique_undoable(&config)?;
        let returned_config = col.storage.get_deck_config(config_id)?.unwrap();
        assert_eq!(returned_config.name, original_name);

        Ok(())
    }

    #[test]
    fn update_deck_config_undoable_updates_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        let config_id = DeckConfigId(TimestampMillis::now().0);
        config.id.0 = config_id.0;
        col.add_deck_config_undoable(&mut config)?;
        let mut new_config = config.clone();
        new_config.name = "new name".into();
        col.update_deck_config_undoable(&new_config, config)?;
        let returned_config = col.storage.get_deck_config(config_id)?.unwrap();
        assert_eq!(returned_config, new_config);

        Ok(())
    }

    #[test]
    fn restore_deleted_deck_config_restores_config() -> Result<()> {
        let mut col = Collection::new();
        let mut config = DeckConfig::default();
        let config_id = DeckConfigId(TimestampMillis::now().0);
        config.id.0 = config_id.0;
        col.restore_deleted_deck_config(config.clone())?;
        let returned_config = col.storage.get_deck_config(config_id)?;
        assert_eq!(returned_config, Some(config));

        Ok(())
    }
}
