// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use anki_proto::stats::CardMemoryMetrics;

use crate::prelude::*;
use crate::scheduler::fsrs::metrics::FsrsMetricContext;

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

        // These APIs let add-ons request model-correct R in one backend call,
        // without deriving it from scalar S/decay or replaying Card Info history.
        // A request-local context reuses each preset's validated model.
        let decks = self.storage.get_decks_map()?;
        let configs = self.storage.get_deck_config_map()?;
        let mut metrics = FsrsMetricContext::new(&decks, &configs);
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
                    now.elapsed_secs_since(last_review_time).max(0) as f32 / 86_400.0;
                entry.fsrs_retrievability =
                    Some(metrics.current_retrievability(card, state, elapsed_days)?);
            }
        }
        Ok(entries)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::card::FsrsMemoryState;
    use crate::tests::NoteAdder;

    #[test]
    fn batch_metrics_are_exact_read_only_and_preserve_request_order() -> Result<()> {
        let mut col = Collection::new();
        let note = NoteAdder::basic(&mut col).add(&mut col);
        let cid = col.storage.card_ids_of_notes(&[note.id])?[0];
        assert!(col.card_memory_metrics(&[cid], true)?[0]
            .memory_state
            .is_none());
        let mut card = col.storage.get_card(cid)?.unwrap();
        card.memory_state = Some(FsrsMemoryState {
            stability: 20.0,
            stability_internal: 12.0,
            stability_fast: Some(0.7),
            difficulty: 8.0,
        });
        card.last_review_time = Some(TimestampSecs::now().adding_secs(-21_600));
        col.storage.update_card(&card)?;
        let before = col.storage.get_card(cid)?.unwrap();
        let entries = col.card_memory_metrics(&[cid, CardId(1), cid], true)?;
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0], entries[1]);
        let expected = fsrs::FSRS::new(&fsrs::DEFAULT_PARAMETERS)?
            .current_retrievability(card.memory_state.unwrap().into(), 0.25);
        assert!((entries[0].fsrs_retrievability.unwrap() - expected).abs() < 0.0001);
        assert!(col.card_memory_metrics(&[cid], false)?[0]
            .fsrs_retrievability
            .is_none());
        assert_eq!(col.storage.get_card(cid)?.unwrap(), before);
        assert!(col.storage.get_revlog_entries_for_card(cid)?.is_empty());
        Ok(())
    }
}
