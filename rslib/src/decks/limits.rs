// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;

use super::Deck;
use crate::{
    deckconfig::{DeckConfig, DeckConfigId},
    prelude::*,
};

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct RemainingLimits {
    pub review: u32,
    pub new: u32,
}

impl RemainingLimits {
    pub(crate) fn new(deck: &Deck, config: Option<&DeckConfig>, today: u32) -> Self {
        config
            .map(|config| {
                let (new_today, rev_today) = deck.new_rev_counts(today);
                RemainingLimits {
                    review: ((config.inner.reviews_per_day as i32) - rev_today).max(0) as u32,
                    new: ((config.inner.new_per_day as i32) - new_today).max(0) as u32,
                }
            })
            .unwrap_or_default()
    }

    pub(crate) fn cap_to(&mut self, limits: RemainingLimits) {
        self.review = self.review.min(limits.review);
        self.new = self.new.min(limits.new);
    }
}

impl Default for RemainingLimits {
    fn default() -> Self {
        RemainingLimits {
            review: 9999,
            new: 9999,
        }
    }
}

pub(crate) fn remaining_limits_map<'a>(
    decks: impl Iterator<Item = &'a Deck>,
    config: &'a HashMap<DeckConfigId, DeckConfig>,
    today: u32,
) -> HashMap<DeckId, RemainingLimits> {
    decks
        .map(|deck| {
            (
                deck.id,
                RemainingLimits::new(deck, deck.config_id().and_then(|id| config.get(&id)), today),
            )
        })
        .collect()
}
