// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::ConfigEntry;
use crate::prelude::*;

#[derive(Debug)]
pub(crate) enum UndoableConfigChange {
    Added(Box<ConfigEntry>),
    Updated(Box<ConfigEntry>),
    Removed(Box<ConfigEntry>),
}

impl Collection {
    pub(crate) fn undo_config_change(&mut self, change: UndoableConfigChange) -> Result<()> {
        match change {
            UndoableConfigChange::Added(entry) => self.remove_config_undoable(&entry.key),
            UndoableConfigChange::Updated(entry) => {
                let current = self
                    .storage
                    .get_config_entry(&entry.key)?
                    .ok_or_else(|| AnkiError::invalid_input("config disappeared"))?;
                self.update_config_entry_undoable(entry, current)
                    .map(|_| ())
            }
            UndoableConfigChange::Removed(entry) => self.add_config_entry_undoable(entry),
        }
    }

    /// True if added, or value changed.
    pub(super) fn set_config_undoable(&mut self, entry: Box<ConfigEntry>) -> Result<bool> {
        if let Some(original) = self.storage.get_config_entry(&entry.key)? {
            self.update_config_entry_undoable(entry, original)
        } else {
            self.add_config_entry_undoable(entry)?;
            Ok(true)
        }
    }

    pub(super) fn remove_config_undoable(&mut self, key: &str) -> Result<()> {
        if let Some(current) = self.storage.get_config_entry(key)? {
            self.save_undo(UndoableConfigChange::Removed(current));
            self.storage.remove_config(key)?;
        }

        Ok(())
    }

    fn add_config_entry_undoable(&mut self, entry: Box<ConfigEntry>) -> Result<()> {
        self.storage.set_config_entry(&entry)?;
        self.save_undo(UndoableConfigChange::Added(entry));
        Ok(())
    }

    /// True if new value differed.
    fn update_config_entry_undoable(
        &mut self,
        entry: Box<ConfigEntry>,
        original: Box<ConfigEntry>,
    ) -> Result<bool> {
        if entry.value != original.value {
            self.save_undo(UndoableConfigChange::Updated(original));
            self.storage.set_config_entry(&entry)?;
            Ok(true)
        } else {
            Ok(false)
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;
    use crate::collection::open_test_collection;

    #[test]
    fn undo() -> Result<()> {
        let mut col = open_test_collection();
        // the op kind doesn't matter, we just need undo enabled
        let op = Op::Bury;
        // test key
        let key = BoolKey::NormalizeNoteText;

        // not set by default, but defaults to true
        assert_eq!(col.get_config_bool(key), true);

        // first set adds the key
        col.transact(op.clone(), |col| col.set_config_bool_inner(key, false))?;
        assert_eq!(col.get_config_bool(key), false);

        // mutate it twice
        col.transact(op.clone(), |col| col.set_config_bool_inner(key, true))?;
        assert_eq!(col.get_config_bool(key), true);
        col.transact(op.clone(), |col| col.set_config_bool_inner(key, false))?;
        assert_eq!(col.get_config_bool(key), false);

        // when we remove it, it goes back to its default
        col.transact(op, |col| col.remove_config_inner(key))?;
        assert_eq!(col.get_config_bool(key), true);

        // undo the removal
        col.undo()?;
        assert_eq!(col.get_config_bool(key), false);

        // undo the mutations
        col.undo()?;
        assert_eq!(col.get_config_bool(key), true);
        col.undo()?;
        assert_eq!(col.get_config_bool(key), false);

        // and undo the initial add
        col.undo()?;
        assert_eq!(col.get_config_bool(key), true);

        Ok(())
    }
}
