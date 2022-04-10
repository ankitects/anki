// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{
    collections::{HashMap, HashSet},
    mem,
};

use super::Context;
use crate::{
    decks::{immediate_parent_name, NormalDeck},
    prelude::*,
};

struct DeckContext<'d> {
    target_col: &'d mut Collection,
    usn: Usn,
    seen_parents: HashSet<String>,
    renamed_parents: Vec<(String, String)>,
    imported_decks: HashMap<DeckId, DeckId>,
}

impl<'d> DeckContext<'d> {
    fn new<'a: 'd>(target_col: &'a mut Collection, usn: Usn) -> Self {
        Self {
            target_col,
            usn,
            seen_parents: HashSet::new(),
            renamed_parents: Vec::new(),
            imported_decks: HashMap::new(),
        }
    }
}

impl Context<'_> {
    pub(super) fn import_decks_and_configs(&mut self) -> Result<HashMap<DeckId, DeckId>> {
        let mut ctx = DeckContext::new(self.target_col, self.usn);
        ctx.import_deck_configs(mem::take(&mut self.data.deck_configs))?;
        ctx.import_decks(mem::take(&mut self.data.decks))?;
        Ok(ctx.imported_decks)
    }
}

impl DeckContext<'_> {
    fn import_deck_configs(&mut self, mut configs: Vec<DeckConfig>) -> Result<()> {
        for config in &mut configs {
            config.usn = self.usn;
            self.target_col.add_deck_config_if_unique_undoable(config)?;
        }
        Ok(())
    }

    fn import_decks(&mut self, mut decks: Vec<Deck>) -> Result<()> {
        // ensure parents are seen before children
        decks.sort_unstable_by(|d1, d2| d1.name.as_native_str().cmp(d2.name.as_native_str()));
        for deck in &mut decks {
            // TODO: handle inconsistent capitalisation
            self.ensure_parents_exist(deck.name.as_native_str())?;
            self.maybe_reparent(deck);
            self.import_deck(deck)?;
        }
        Ok(())
    }

    fn ensure_parents_exist(&mut self, name: &str) -> Result<()> {
        if let Some(parent) = immediate_parent_name(name) {
            if !self.seen_parents.contains(parent) {
                self.ensure_parents_exist(parent)?;
                self.seen_parents.insert(parent.to_string());
                if let Some(new_parent) = self.reparented_name(parent) {
                    self.ensure_deck_exists(&new_parent)?;
                } else {
                    self.ensure_deck_exists(parent)?;
                };
            }
        }
        Ok(())
    }

    fn reparented_name(&self, name: &str) -> Option<String> {
        self.renamed_parents
            .iter()
            .find_map(|(old_parent, new_parent)| {
                name.starts_with(old_parent)
                    .then(|| name.replacen(old_parent, new_parent, 1))
            })
    }

    fn ensure_deck_exists(&mut self, name: &str) -> Result<()> {
        if let Some(deck) = self.target_col.storage.get_deck_by_name(name)? {
            if deck.is_filtered() {
                self.add_default_deck(name, true)?;
            }
        } else {
            self.add_default_deck(name, false)?;
        };
        Ok(())
    }

    fn add_default_deck(&mut self, name: &str, uniquify: bool) -> Result<()> {
        let mut deck = Deck::new_normal();
        deck.name = NativeDeckName::from_native_str(name);
        if uniquify {
            self.uniquify_name(&mut deck);
        }
        self.target_col.add_deck_inner(&mut deck, self.usn)
    }

    fn uniquify_name(&mut self, deck: &mut Deck) {
        let old_parent = format!("{}\x1f", deck.name.as_native_str());
        deck.uniquify_name();
        let new_parent = format!("{}\x1f", deck.name.as_native_str());
        self.renamed_parents.push((old_parent, new_parent));
    }

    fn maybe_reparent(&self, deck: &mut Deck) {
        if let Some(new_name) = self.reparented_name(deck.name.as_native_str()) {
            deck.name = NativeDeckName::from_native_str(new_name);
        }
    }

    fn import_deck(&mut self, deck: &mut Deck) -> Result<()> {
        if let Some(original) = self.get_deck_by_name(deck)? {
            if original.is_filtered() {
                self.uniquify_name(deck);
                self.add_deck(deck)
            } else {
                self.update_deck(deck, original)
            }
        } else {
            self.add_deck(deck)
        }
    }

    fn get_deck_by_name(&mut self, deck: &Deck) -> Result<Option<Deck>> {
        self.target_col
            .storage
            .get_deck_by_name(deck.name.as_native_str())
    }

    fn add_deck(&mut self, deck: &mut Deck) -> Result<()> {
        let old_id = mem::take(&mut deck.id);
        self.target_col.add_deck_inner(deck, self.usn)?;
        self.imported_decks.insert(old_id, deck.id);
        Ok(())
    }

    /// Caller must ensure decks are normal.
    fn update_deck(&mut self, deck: &Deck, original: Deck) -> Result<()> {
        let mut new_deck = original.clone();
        new_deck.normal_mut()?.update_with_other(deck.normal()?);
        self.imported_decks.insert(deck.id, new_deck.id);
        self.target_col
            .update_deck_inner(&mut new_deck, original, self.usn)
    }
}

impl Deck {
    fn uniquify_name(&mut self) {
        let new_name = format!("{} {}", self.name.as_native_str(), TimestampSecs::now());
        self.name = NativeDeckName::from_native_str(new_name);
    }
}

impl NormalDeck {
    fn update_with_other(&mut self, other: &Self) {
        self.markdown_description = other.markdown_description;
        self.description = other.description.clone();
        if other.config_id != 1 {
            self.config_id = other.config_id;
        }
    }
}
