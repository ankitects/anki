// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::CardMemoryMetrics;

use crate::prelude::*;
use crate::scheduler::fsrs::memory_state::fsrs_current_retrievability_for_state;

impl Collection {
    /// Read current state without reconstructing historical states or updating
    /// cards. Missing cards are omitted; remaining input order and
    /// duplicates are preserved.
    pub fn card_memory_metrics(
        &mut self,
        card_ids: &[CardId],
        include_retrievability: bool,
    ) -> Result<Vec<CardMemoryMetrics>> {
        let cards = self.stored_cards_for_ids(card_ids)?;
        self.memory_metrics_for_cards(&cards, include_retrievability)
    }

    pub(crate) fn stored_cards_for_ids(&self, card_ids: &[CardId]) -> Result<Vec<Card>> {
        let mut cards = Vec::with_capacity(card_ids.len());
        for &cid in card_ids {
            if let Some(card) = self.storage.get_card(cid)? {
                cards.push(card);
            }
        }
        Ok(cards)
    }

    pub(crate) fn memory_metrics_for_cards(
        &mut self,
        cards: &[Card],
        include_retrievability: bool,
    ) -> Result<Vec<CardMemoryMetrics>> {
        let mut entries = cards
            .iter()
            .map(|card| CardMemoryMetrics {
                card_id: card.id.0,
                memory_state: card.memory_state.map(Into::into),
                desired_retention: card.desired_retention,
                fsrs_retrievability: None,
            })
            .collect::<Vec<_>>();
        if !include_retrievability || cards.is_empty() {
            return Ok(entries);
        }

        // Resolve only stateful cards, using the same home-deck/overlay resolver
        // as Card Info. Everything here is scoped to this request.
        let stateful = cards
            .iter()
            .filter(|card| card.memory_state.is_some())
            .cloned()
            .collect::<Vec<_>>();
        if stateful.is_empty() {
            return Ok(entries);
        }
        let presets = self.fsrs_presets_for_cards(&stateful)?;
        let now = self.timing_today()?.now;
        for (card, entry) in cards.iter().zip(entries.iter_mut()) {
            if let Some(state) = card.memory_state {
                let last_review_time = if let Some(time) = card.last_review_time {
                    time
                } else {
                    self.storage
                        .time_of_last_review(card.id)?
                        .unwrap_or_default()
                };
                let elapsed_days =
                    now.elapsed_secs_since_clamped(last_review_time) as f32 / 86_400.0;
                entry.fsrs_retrievability = Some(fsrs_current_retrievability_for_state(
                    &presets[&card.id].params,
                    state,
                    elapsed_days,
                )?);
            }
        }
        Ok(entries)
    }
}
