// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

//! Adding and updating.

use super::name::immediate_parent_name;
use crate::error::FilteredDeckError;
use crate::prelude::*;

impl Collection {
    /// Add a new deck. The id must be 0, as it will be automatically assigned.
    pub fn add_deck(&mut self, deck: &mut Deck) -> Result<OpOutput<()>> {
        self.transact(Op::AddDeck, |col| col.add_deck_inner(deck, col.usn()?))
    }

    pub fn update_deck(&mut self, deck: &mut Deck) -> Result<OpOutput<()>> {
        self.transact(Op::UpdateDeck, |col| {
            let existing_deck = col.storage.get_deck(deck.id)?.or_not_found(deck.id)?;
            col.update_deck_inner(deck, existing_deck, col.usn()?)
        })
    }

    /// Add or update an existing deck modified by the user. May add parents,
    /// or rename children as required. Prefer add_deck() or update_deck() to
    /// be explicit about your intentions; this function mainly exists so we
    /// can integrate with older Python code that behaved this way.
    pub fn add_or_update_deck(&mut self, deck: &mut Deck) -> Result<OpOutput<()>> {
        if deck.id.0 == 0 {
            self.add_deck(deck)
        } else {
            self.update_deck(deck)
        }
    }
}

impl Collection {
    /// Rename deck if not unique. Bumps mtime and usn if
    /// name was changed, but otherwise leaves it the same.
    pub(super) fn prepare_deck_for_update(&mut self, deck: &mut Deck, usn: Usn) -> Result<()> {
        if deck.name.maybe_normalize() {
            deck.set_modified(usn);
        }
        self.ensure_deck_name_unique(deck, usn)
    }

    pub(crate) fn add_deck_inner(&mut self, deck: &mut Deck, usn: Usn) -> Result<()> {
        require!(deck.id.0 == 0, "deck to add must have id 0");
        self.prepare_deck_for_update(deck, usn)?;
        deck.set_modified(usn);
        self.match_or_create_parents(deck, usn)?;
        self.add_deck_undoable(deck)
    }

    pub(crate) fn update_deck_inner(
        &mut self,
        deck: &mut Deck,
        original: Deck,
        usn: Usn,
    ) -> Result<()> {
        self.prepare_deck_for_update(deck, usn)?;
        if deck == &original {
            return Ok(());
        }
        deck.set_modified(usn);
        let name_changed = original.name != deck.name;
        if name_changed {
            // match closest parent name
            self.match_or_create_parents(deck, usn)?;
            // rename children
            self.rename_child_decks(&original, &deck.name, usn)?;
        }
        self.update_single_deck_undoable(deck, original)?;
        if name_changed {
            // after updating, we need to ensure all grandparents exist, which may not be
            // the case in the parent->child case
            self.create_missing_parents(&deck.name, usn)?;
        }
        Ok(())
    }

    /// Add/update a single deck when syncing/importing. Ensures name is unique
    /// & normalized, but does not check parents/children or update mtime
    /// (unless the name was changed). Caller must set up transaction.
    pub(crate) fn add_or_update_single_deck_with_existing_id(
        &mut self,
        deck: &mut Deck,
        usn: Usn,
    ) -> Result<()> {
        self.prepare_deck_for_update(deck, usn)?;
        self.add_or_update_deck_with_existing_id_undoable(deck)
    }

    pub(crate) fn recover_missing_deck(&mut self, did: DeckId, usn: Usn) -> Result<()> {
        let mut deck = Deck::new_normal();
        deck.id = did;
        deck.name = NativeDeckName::from_native_str(format!("recovered{}", did));
        deck.set_modified(usn);
        self.add_or_update_single_deck_with_existing_id(&mut deck, usn)
    }

    /// Add a single, normal deck with the provided name for a child deck.
    /// Caller must have done necessarily validation on name.
    fn add_parent_deck(&mut self, machine_name: &str, usn: Usn) -> Result<()> {
        let mut deck = Deck::new_normal();
        deck.name = NativeDeckName::from_native_str(machine_name);
        let parent_deck = self.first_existing_parent(machine_name, 0);
        if let Ok(Some(parent_deck)) = parent_deck {
            if let DeckKind::Normal(parent_deck) = parent_deck.kind {
                if let DeckKind::Normal(deck) = &mut deck.kind {
                    deck.config_id = parent_deck.config_id;
                }
            }
        }
        deck.set_modified(usn);
        self.add_deck_undoable(&mut deck)
    }

    /// If parent deck(s) exist, rewrite name to match their case.
    /// If they don't exist, create them.
    /// Returns an error if a DB operation fails, or if the first existing
    /// parent is a filtered deck.
    fn match_or_create_parents(&mut self, deck: &mut Deck, usn: Usn) -> Result<()> {
        let child_split: Vec<_> = deck.name.components().collect();
        if let Some(parent_deck) = self.first_existing_parent(deck.name.as_native_str(), 0)? {
            if parent_deck.is_filtered() {
                return Err(FilteredDeckError::MustBeLeafNode.into());
            }
            let parent_count = parent_deck.name.components().count();
            let need_create = parent_count != child_split.len() - 1;
            deck.name = NativeDeckName::from_native_str(format!(
                "{}\x1f{}",
                parent_deck.name,
                &child_split[parent_count..].join("\x1f")
            ));
            if let DeckKind::Normal(parent_deck) = parent_deck.kind {
                if let DeckKind::Normal(deck) = &mut deck.kind {
                    deck.config_id = parent_deck.config_id;
                }
            }
            if need_create {
                self.create_missing_parents(&deck.name, usn)?;
            }
            Ok(())
        } else if child_split.len() == 1 {
            // no parents required
            Ok(())
        } else {
            // no existing parents
            self.create_missing_parents(&deck.name, usn)
        }
    }

    fn create_missing_parents(&mut self, name: &NativeDeckName, usn: Usn) -> Result<()> {
        let mut machine_name = name.as_native_str();
        while let Some(parent_name) = immediate_parent_name(machine_name) {
            if self.storage.get_deck_id(parent_name)?.is_none() {
                self.add_parent_deck(parent_name, usn)?;
            }
            machine_name = parent_name;
        }
        Ok(())
    }

    pub(crate) fn first_existing_parent(
        &self,
        machine_name: &str,
        recursion_level: usize,
    ) -> Result<Option<Deck>> {
        require!(recursion_level < 11, "deck nesting level too deep");
        if let Some(parent_name) = immediate_parent_name(machine_name) {
            if let Some(parent_did) = self.storage.get_deck_id(parent_name)? {
                self.storage.get_deck(parent_did)
            } else {
                self.first_existing_parent(parent_name, recursion_level + 1)
            }
        } else {
            Ok(None)
        }
    }
}
