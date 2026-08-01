// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::Probe;
use crate::prelude::*;

#[derive(Debug)]
pub(crate) enum UndoableProbeChange {
    Added(Box<Probe>),
    Removed(Box<Probe>),
}

impl Collection {
    pub(crate) fn undo_probe_change(&mut self, change: UndoableProbeChange) -> Result<()> {
        match change {
            UndoableProbeChange::Added(probe) => {
                self.storage.remove_probe(probe.id)?;
                self.save_undo(UndoableProbeChange::Removed(probe));
                Ok(())
            }
            UndoableProbeChange::Removed(probe) => {
                self.storage.add_probe(&probe, false)?;
                self.save_undo(UndoableProbeChange::Added(probe));
                Ok(())
            }
        }
    }

    /// Add the provided probe, modifying the id if it is not unique.
    pub(crate) fn add_probe_undoable(&mut self, probe: &mut Probe) -> Result<()> {
        probe.id = self.storage.add_probe(probe, true)?.unwrap();
        self.save_undo(UndoableProbeChange::Added(Box::new(probe.clone())));
        Ok(())
    }

    /// Add the provided probe, if its id is unique.
    pub(crate) fn add_probe_if_unique_undoable(&mut self, probe: Probe) -> Result<()> {
        if self.storage.add_probe(&probe, false)?.is_some() {
            self.save_undo(UndoableProbeChange::Added(Box::new(probe)));
        }
        Ok(())
    }

    /// Remove any probes attached to the given card, undoably. Called when
    /// the parent card is removed.
    pub(crate) fn remove_probes_for_card_undoable(&mut self, card_id: CardId) -> Result<()> {
        for probe in self.storage.get_probes_for_card(card_id)? {
            self.storage.remove_probe(probe.id)?;
            self.save_undo(UndoableProbeChange::Removed(Box::new(probe)));
        }
        Ok(())
    }
}
