// Copyright: Anki Software, LLC and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::ConfigEntry;
use super::RwkvHistoryResetMirror;
use super::RWKV_HISTORY_RESET_CONFIG_PREFIX;
use crate::prelude::*;

#[derive(Debug)]
pub(crate) enum UndoableConfigChange {
    Added(Box<ConfigEntry>),
    Updated(Box<ConfigEntry>),
    Removed(Box<ConfigEntry>),
}

impl Collection {
    fn refresh_cached_scheduler_config(&mut self, key: &str) {
        self.invalidate_fsrs_preset_overlay_for_config_key(key);
        if key == crate::scheduler::addon_desired_retention::CONFIG_KEY {
            self.invalidate_rwkv_offline_targets();
        }
        if self.state.card_queues.is_none() {
            return;
        }
        let config_key = match key {
            "fsrs" => BoolKey::Fsrs,
            "fsrsShortTermWithStepsEnabled" => BoolKey::FsrsShortTermWithStepsEnabled,
            _ => return,
        };
        let value = self.get_config_bool(config_key);
        if let Some(queues) = self.state.card_queues.as_mut() {
            match config_key {
                BoolKey::Fsrs => queues.fsrs_enabled = value,
                BoolKey::FsrsShortTermWithStepsEnabled => queues.fsrs_short_term_with_steps = value,
                _ => unreachable!(),
            }
        }
    }

    pub(crate) fn undo_config_change(&mut self, change: UndoableConfigChange) -> Result<()> {
        match change {
            UndoableConfigChange::Added(entry)
                if entry.key.starts_with(RWKV_HISTORY_RESET_CONFIG_PREFIX) =>
            {
                self.undo_rwkv_history_reset(&entry.key, None)
            }
            UndoableConfigChange::Updated(entry) | UndoableConfigChange::Removed(entry)
                if entry.key.starts_with(RWKV_HISTORY_RESET_CONFIG_PREFIX) =>
            {
                let previous: RwkvHistoryResetMirror = serde_json::from_slice(&entry.value)?;
                self.undo_rwkv_history_reset(&entry.key, Some(previous))
            }
            UndoableConfigChange::Added(entry) => self.remove_config_undoable(&entry.key),
            UndoableConfigChange::Updated(entry) => {
                let current = self
                    .storage
                    .get_config_entry(&entry.key)?
                    .or_invalid("config disappeared")?;
                self.update_config_entry_undoable(entry, current)
                    .map(|_| ())
            }
            UndoableConfigChange::Removed(entry) => self.add_config_entry_undoable(entry),
        }
    }

    /// Undo/redo is a new boundary decision, not an older sync update. Keep a
    /// timestamped fallback marker when undoing the original mirror creation.
    fn undo_rwkv_history_reset(
        &mut self,
        key: &str,
        previous: Option<RwkvHistoryResetMirror>,
    ) -> Result<()> {
        let current: Option<RwkvHistoryResetMirror> = self.storage.get_config_value(key)?;
        let latest = match (current, previous) {
            (Some(a), Some(b)) => Some(if a.updated >= b.updated { a } else { b }),
            (a, b) => a.or(b),
        };
        let restored = RwkvHistoryResetMirror::new(previous.and_then(|p| p.after), latest);
        self.set_config(key, &restored)?;
        Ok(())
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
            self.refresh_cached_scheduler_config(key);
        }

        Ok(())
    }

    fn add_config_entry_undoable(&mut self, entry: Box<ConfigEntry>) -> Result<()> {
        self.storage.set_config_entry(&entry)?;
        self.refresh_cached_scheduler_config(&entry.key);
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
            self.refresh_cached_scheduler_config(&entry.key);
            Ok(true)
        } else {
            Ok(false)
        }
    }
}

#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn undo() -> Result<()> {
        let mut col = Collection::new();
        // the op kind doesn't matter, we just need undo enabled
        let op = Op::Bury;
        // test key
        let key = BoolKey::NormalizeNoteText;

        // not set by default, but defaults to true
        assert!(col.get_config_bool(key));

        // first set adds the key
        col.transact(op.clone(), |col| col.set_config_bool_inner(key, false))?;
        assert!(!col.get_config_bool(key));

        // mutate it twice
        col.transact(op.clone(), |col| col.set_config_bool_inner(key, true))?;
        assert!(col.get_config_bool(key));
        col.transact(op.clone(), |col| col.set_config_bool_inner(key, false))?;
        assert!(!col.get_config_bool(key));

        // when we remove it, it goes back to its default
        col.transact(op, |col| col.remove_config_inner(key))?;
        assert!(col.get_config_bool(key));

        // undo the removal
        col.undo()?;
        assert!(!col.get_config_bool(key));

        // undo the mutations
        col.undo()?;
        assert!(col.get_config_bool(key));
        col.undo()?;
        assert!(!col.get_config_bool(key));

        // and undo the initial add
        col.undo()?;
        assert!(col.get_config_bool(key));

        Ok(())
    }
}
