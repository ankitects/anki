// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use super::super::BuryMode;
use super::Context;
use super::DueCard;
use super::NewCard;
use super::QueueBuilder;
use crate::deckconfig::DeckConfig;
use crate::prelude::*;

pub(super) enum DueOrNewCard {
    Due(DueCard),
    New(NewCard),
}

impl DueOrNewCard {
    fn original_deck_id(&self) -> DeckId {
        match self {
            Self::Due(card) => card.original_deck_id.or(card.current_deck_id),
            Self::New(card) => card.original_deck_id.or(card.current_deck_id),
        }
    }

    fn note_id(&self) -> NoteId {
        match self {
            Self::Due(card) => card.note_id,
            Self::New(card) => card.note_id,
        }
    }
}

impl From<DueCard> for DueOrNewCard {
    fn from(card: DueCard) -> DueOrNewCard {
        DueOrNewCard::Due(card)
    }
}

impl From<NewCard> for DueOrNewCard {
    fn from(card: NewCard) -> DueOrNewCard {
        DueOrNewCard::New(card)
    }
}

impl Context {
    pub(super) fn bury_mode(&self, deck_id: DeckId) -> BuryMode {
        self.deck_map
            .get(&deck_id)
            .and_then(|deck| deck.config_id())
            .and_then(|config_id| self.config_map.get(&config_id))
            .map(BuryMode::from_deck_config)
            .unwrap_or_default()
    }
}

impl BuryMode {
    pub(crate) fn from_deck_config(config: &DeckConfig) -> BuryMode {
        let cfg = &config.inner;
        BuryMode {
            bury_new: cfg.bury_new,
            bury_reviews: cfg.bury_reviews,
            bury_interday_learning: cfg.bury_interday_learning,
        }
    }

    pub(crate) fn any_burying(self) -> bool {
        self.bury_interday_learning || self.bury_reviews || self.bury_new
    }
}

impl QueueBuilder {
    /// If burying is enabled in `new_settings`, existing entry will be updated.
    /// Returns a copy made before changing the entry, so that a card with
    /// burying enabled will bury future siblings, but not itself.
    pub(super) fn get_and_update_bury_mode_for_note(
        &mut self,
        card: DueOrNewCard,
    ) -> Option<BuryMode> {
        let mut previous_mode = None;
        let new_mode = self.context.bury_mode(card.original_deck_id());
        self.context
            .seen_note_ids
            .entry(card.note_id())
            .and_modify(|entry| {
                previous_mode = Some(*entry);
                entry.bury_new |= new_mode.bury_new;
                entry.bury_reviews |= new_mode.bury_reviews;
                entry.bury_interday_learning |= new_mode.bury_interday_learning;
            })
            .or_insert(new_mode);

        previous_mode
    }
}
