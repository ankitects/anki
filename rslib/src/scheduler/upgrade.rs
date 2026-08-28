// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use super::timing::local_minutes_west_for_stamp;
use crate::card::CardQueue;
use crate::card::CardType;
use crate::config::SchedulerVersion;
use crate::prelude::*;
use crate::search::SortMode;

struct V1FilteredDeckInfo {
    /// True if the filtered deck had rescheduling enabled.
    reschedule: bool,
    /// If the filtered deck had custom steps enabled, `original_step_count`
    /// contains the step count of the home deck, which will be used to ensure
    /// the remaining steps of the card are not out of bounds.
    original_step_count: Option<u32>,
}

impl Card {
    /// Update relearning cards and cards in filtered decks.
    /// `filtered_info` should be provided if card is in a filtered deck.
    fn upgrade_to_v2(&mut self, filtered_info: Option<V1FilteredDeckInfo>) {
        // relearning cards have their own type
        if self.ctype == CardType::Review
            && matches!(self.queue, CardQueue::Learn | CardQueue::DayLearn)
        {
            self.ctype = CardType::Relearn;
        }

        // filtered deck handling
        if let Some(info) = filtered_info {
            // cap remaining count to home deck
            if let Some(step_count) = info.original_step_count {
                self.remaining_steps = self.remaining_steps.min(step_count);
            }

            if info.reschedule {
                // only new cards should be in the new queue
                if self.queue == CardQueue::New && self.ctype != CardType::New {
                    self.restore_queue_from_type();
                }
            } else {
                // preview cards start in the review queue in v2
                if self.queue == CardQueue::New {
                    self.queue = CardQueue::Review;
                }

                // to ensure learning cards are reset to new on exit, we must
                // make them new now
                if self.ctype == CardType::Learn {
                    self.queue = CardQueue::PreviewRepeat;
                    self.ctype = CardType::New;
                }
            }
        }
    }
}

fn get_filter_info_for_card(
    card: &Card,
    decks: &HashMap<DeckId, Deck>,
    configs: &HashMap<DeckConfigId, DeckConfig>,
) -> Option<V1FilteredDeckInfo> {
    if card.original_deck_id.0 == 0 {
        None
    } else {
        let (had_custom_steps, reschedule) = if let Some(deck) = decks.get(&card.deck_id) {
            if let DeckKind::Filtered(filtered) = &deck.kind {
                (!filtered.delays.is_empty(), filtered.reschedule)
            } else {
                // not a filtered deck, give up
                return None;
            }
        } else {
            // missing filtered deck, give up
            return None;
        };

        let original_step_count = if had_custom_steps {
            let home_conf_id = decks
                .get(&card.original_deck_id)
                .and_then(|deck| deck.config_id())
                .unwrap_or(DeckConfigId(1));
            Some(
                configs
                    .get(&home_conf_id)
                    .map(|config| {
                        if card.ctype == CardType::Review {
                            config.inner.relearn_steps.len()
                        } else {
                            config.inner.learn_steps.len()
                        }
                    })
                    .unwrap_or(0) as u32,
            )
        } else {
            None
        };

        Some(V1FilteredDeckInfo {
            reschedule,
            original_step_count,
        })
    }
}

impl Collection {
    /// Expects an existing transaction. No-op if already on v2.
    pub(crate) fn upgrade_to_v2_scheduler(&mut self) -> Result<()> {
        if self.scheduler_version() == SchedulerVersion::V2 {
            // nothing to do
            return Ok(());
        }
        self.storage.upgrade_revlog_to_v2()?;
        self.upgrade_cards_to_v2()?;
        self.set_scheduler_version_config_key(SchedulerVersion::V2)?;

        // enable new timezone code by default
        let created = self.storage.creation_stamp()?;
        if self.get_creation_utc_offset().is_none() {
            self.set_creation_utc_offset(Some(local_minutes_west_for_stamp(created)?))?;
        }

        // force full sync
        self.set_schema_modified()
    }

    fn upgrade_cards_to_v2(&mut self) -> Result<()> {
        let guard = self.search_cards_into_table(
            // can't add 'is:learn' here, as it matches on card type, not card queue
            "deck:filtered OR is:review",
            SortMode::NoOrder,
        )?;
        if guard.cards > 0 {
            let decks = guard.col.storage.get_decks_map()?;
            let configs = guard.col.storage.get_deck_config_map()?;
            guard.col.storage.for_each_card_in_search(|mut card| {
                let filtered_info = get_filter_info_for_card(&card, &decks, &configs);
                card.upgrade_to_v2(filtered_info);
                guard.col.storage.update_card(&card)
            })?;
        }
        Ok(())
    }
}
#[cfg(test)]
mod test {
    use super::*;

    #[test]
    fn v2_card() {
        let mut c = Card {
            ctype: CardType::Review,
            queue: CardQueue::DayLearn,
            ..Default::default()
        };
        // relearning cards should be reclassified
        c.upgrade_to_v2(None);
        assert_eq!(c.ctype, CardType::Relearn);

        // check step capping
        c.remaining_steps = 5005;
        c.upgrade_to_v2(Some(V1FilteredDeckInfo {
            reschedule: true,
            original_step_count: Some(2),
        }));
        assert_eq!(c.remaining_steps, 2);

        // with rescheduling off, relearning cards don't need changing
        c.upgrade_to_v2(Some(V1FilteredDeckInfo {
            reschedule: false,
            original_step_count: None,
        }));
        assert_eq!(c.ctype, CardType::Relearn);
        assert_eq!(c.queue, CardQueue::DayLearn);

        // but learning cards are reset to new
        c.ctype = CardType::Learn;
        c.upgrade_to_v2(Some(V1FilteredDeckInfo {
            reschedule: false,
            original_step_count: None,
        }));
        assert_eq!(c.ctype, CardType::New);
        assert_eq!(c.queue, CardQueue::PreviewRepeat);

        // (early) reviews should be moved back from the new queue
        c.ctype = CardType::Review;
        c.queue = CardQueue::New;
        c.upgrade_to_v2(Some(V1FilteredDeckInfo {
            reschedule: true,
            original_step_count: None,
        }));
        assert_eq!(c.ctype, CardType::Review);
        assert_eq!(c.queue, CardQueue::Review);
    }
}
