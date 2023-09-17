// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use fsrs::FSRS;

use crate::prelude::*;
use crate::scheduler::fsrs::weights::fsrs_items_for_memory_state;
use crate::scheduler::fsrs::weights::Weights;
use crate::search::JoinSearches;
use crate::search::Negated;
use crate::search::SearchNode;
use crate::search::StateKind;

#[derive(Debug, Clone, Copy, Default)]
pub struct ComputeMemoryProgress {
    pub current_cards: u32,
    pub total_cards: u32,
}

impl Collection {
    /// For each provided set of weights, locate cards with the provided search,
    /// and update their memory state.
    /// Should be called inside a transaction.
    /// If Weights are None, it means the user disabled FSRS, and the existing
    /// memory state should be removed.
    pub(crate) fn update_memory_state(
        &mut self,
        entries: Vec<(Option<Weights>, Vec<SearchNode>)>,
    ) -> Result<()> {
        let timing = self.timing_today()?;
        let usn = self.usn()?;
        for (weights, search) in entries {
            let search = SearchBuilder::any(search.into_iter())
                .and(SearchNode::State(StateKind::New).negated());
            let revlog = self.revlog_for_srs(search)?;
            let items = fsrs_items_for_memory_state(revlog, timing.next_day_at);
            let fsrs = FSRS::new(weights.as_deref())?;
            let mut progress = self.new_progress_handler::<ComputeMemoryProgress>();
            progress.update(false, |s| s.total_cards = items.len() as u32)?;
            for (idx, (card_id, item)) in items.into_iter().enumerate() {
                progress.update(true, |state| state.current_cards = idx as u32 + 1)?;
                let mut card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
                let original = card.clone();
                if weights.is_some() {
                    let state = fsrs.memory_state(item);
                    card.memory_state = Some(state.into());
                } else {
                    card.memory_state = None;
                }
                self.update_card_inner(&mut card, original, usn)?;
            }
        }
        Ok(())
    }
}
