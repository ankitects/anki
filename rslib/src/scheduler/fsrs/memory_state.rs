// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::scheduler::ComputeMemoryStateResponse;
use fsrs::FSRS;

use crate::card::FsrsMemoryState;
use crate::prelude::*;
use crate::scheduler::fsrs::weights::fsrs_items_for_memory_state;
use crate::scheduler::fsrs::weights::single_card_revlog_to_items;
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

pub(crate) type WeightsAndDesiredRetention = (Weights, f32);

impl Collection {
    /// For each provided set of weights, locate cards with the provided search,
    /// and update their memory state.
    /// Should be called inside a transaction.
    /// If Weights are None, it means the user disabled FSRS, and the existing
    /// memory state should be removed.
    pub(crate) fn update_memory_state(
        &mut self,
        entries: Vec<(Option<WeightsAndDesiredRetention>, Vec<SearchNode>)>,
    ) -> Result<()> {
        let timing = self.timing_today()?;
        let usn = self.usn()?;
        for (weights_and_desired_retention, search) in entries {
            let search = SearchBuilder::any(search.into_iter())
                .and(SearchNode::State(StateKind::New).negated());
            let revlog = self.revlog_for_srs(search)?;
            let items = fsrs_items_for_memory_state(revlog, timing.next_day_at);
            let desired_retention = weights_and_desired_retention.as_ref().map(|w| w.1);
            let fsrs = FSRS::new(weights_and_desired_retention.as_ref().map(|w| &w.0[..]))?;
            let mut progress = self.new_progress_handler::<ComputeMemoryProgress>();
            progress.update(false, |s| s.total_cards = items.len() as u32)?;
            for (idx, (card_id, item)) in items.into_iter().enumerate() {
                progress.update(true, |state| state.current_cards = idx as u32 + 1)?;
                let mut card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
                let original = card.clone();
                if weights_and_desired_retention.is_some() {
                    let state = fsrs.memory_state(item);
                    card.memory_state = Some(state.into());
                    card.desired_retention = desired_retention;
                } else {
                    card.memory_state = None;
                    card.desired_retention = None;
                }
                self.update_card_inner(&mut card, original, usn)?;
            }
        }
        Ok(())
    }

    pub fn compute_memory_state(&mut self, card_id: CardId) -> Result<ComputeMemoryStateResponse> {
        let card = self.storage.get_card(card_id)?.or_not_found(card_id)?;
        let deck_id = card.original_deck_id.or(card.deck_id);
        let deck = self.get_deck(deck_id)?.or_not_found(card.deck_id)?;
        let conf_id = DeckConfigId(deck.normal()?.config_id);
        let config = self
            .storage
            .get_deck_config(conf_id)?
            .or_not_found(conf_id)?;
        let desired_retention = config.inner.desired_retention;
        let fsrs = FSRS::new(Some(&config.inner.fsrs_weights))?;
        let revlog = self.revlog_for_srs(SearchNode::CardIds(card.id.to_string()))?;
        let items = single_card_revlog_to_items(revlog, self.timing_today()?.next_day_at, false);
        if let Some(mut items) = items {
            if let Some(last) = items.pop() {
                let state = fsrs.memory_state(last);
                let state = FsrsMemoryState::from(state);
                return Ok(ComputeMemoryStateResponse {
                    state: Some(state.into()),
                    desired_retention,
                });
            }
        }
        Ok(ComputeMemoryStateResponse {
            state: None,
            desired_retention,
        })
    }
}
