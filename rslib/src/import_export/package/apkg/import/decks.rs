// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::{collections::HashMap, mem};

use super::Context;
use crate::{decks::NormalDeck, prelude::*};

struct DeckContext<'d> {
    target_col: &'d mut Collection,
    usn: Usn,
    renamed_parents: Vec<(String, String)>,
    imported_decks: HashMap<DeckId, DeckId>,
    unique_suffix: String,
}

impl<'d> DeckContext<'d> {
    fn new<'a: 'd>(target_col: &'a mut Collection, usn: Usn) -> Self {
        Self {
            target_col,
            usn,
            renamed_parents: Vec::new(),
            imported_decks: HashMap::new(),
            unique_suffix: TimestampSecs::now().to_string(),
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
        decks.sort_unstable_by_key(|deck| deck.level());
        for deck in &mut decks {
            self.prepare_deck(deck);
            self.import_deck(deck)?;
        }
        Ok(())
    }

    fn prepare_deck(&mut self, deck: &mut Deck) {
        self.maybe_reparent(deck);
        if deck.is_filtered() {
            deck.kind = DeckKind::Normal(NormalDeck {
                config_id: 1,
                ..Default::default()
            });
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
            self.ensure_valid_first_existing_parent(deck)?;
            self.add_deck(deck)
        }
    }

    fn maybe_reparent(&self, deck: &mut Deck) {
        if let Some(new_name) = self.reparented_name(deck.name.as_native_str()) {
            deck.name = NativeDeckName::from_native_str(new_name);
        }
    }

    fn reparented_name(&self, name: &str) -> Option<String> {
        self.renamed_parents
            .iter()
            .find_map(|(old_parent, new_parent)| {
                name.starts_with(old_parent)
                    .then(|| name.replacen(old_parent, new_parent, 1))
            })
    }

    fn get_deck_by_name(&mut self, deck: &Deck) -> Result<Option<Deck>> {
        self.target_col
            .storage
            .get_deck_by_name(deck.name.as_native_str())
    }

    fn uniquify_name(&mut self, deck: &mut Deck) {
        let old_parent = format!("{}\x1f", deck.name.as_native_str());
        deck.uniquify_name(&self.unique_suffix);
        let new_parent = format!("{}\x1f", deck.name.as_native_str());
        self.renamed_parents.push((old_parent, new_parent));
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

    fn ensure_valid_first_existing_parent(&mut self, deck: &mut Deck) -> Result<()> {
        if let Some(ancestor) = self
            .target_col
            .first_existing_parent(deck.name.as_native_str(), 0)?
        {
            if ancestor.is_filtered() {
                self.add_unique_default_deck(ancestor.name.as_native_str())?;
                self.maybe_reparent(deck);
            }
        }
        Ok(())
    }

    fn add_unique_default_deck(&mut self, name: &str) -> Result<()> {
        let mut deck = Deck::new_normal();
        deck.name = NativeDeckName::from_native_str(name);
        self.uniquify_name(&mut deck);
        self.target_col.add_deck_inner(&mut deck, self.usn)
    }
}

impl Deck {
    fn uniquify_name(&mut self, suffix: &str) {
        let new_name = format!("{} {}", self.name.as_native_str(), suffix);
        self.name = NativeDeckName::from_native_str(new_name);
    }

    fn level(&self) -> usize {
        self.name.components().count()
    }
}

impl NormalDeck {
    fn update_with_other(&mut self, other: &Self) {
        if !other.description.is_empty() {
            self.markdown_description = other.markdown_description;
            self.description = other.description.clone();
        }
        if other.config_id != 1 {
            self.config_id = other.config_id;
        }
    }
}

#[cfg(test)]
mod test {
    use std::collections::HashSet;

    use super::*;
    use crate::{collection::open_test_collection, tests::new_deck_with_machine_name};

    #[test]
    fn parents() {
        let mut col = open_test_collection();

        col.add_deck_with_machine_name("filtered", true);
        col.add_deck_with_machine_name("PARENT", false);

        let mut ctx = DeckContext::new(&mut col, Usn(1));
        ctx.unique_suffix = "★".to_string();

        let imports = vec![
            new_deck_with_machine_name("unknown parent\x1fchild", false),
            new_deck_with_machine_name("filtered\x1fchild", false),
            new_deck_with_machine_name("parent\x1fchild", false),
            new_deck_with_machine_name("NEW PARENT\x1fchild", false),
            new_deck_with_machine_name("new parent", false),
        ];
        ctx.import_decks(imports).unwrap();
        let existing_decks: HashSet<_> = ctx
            .target_col
            .get_all_deck_names(true)
            .unwrap()
            .into_iter()
            .map(|(_, name)| name)
            .collect();

        // missing parents get created
        assert!(existing_decks.contains("unknown parent"));
        // ... and uniquified if their existing counterparts are filtered
        assert!(existing_decks.contains("filtered ★"));
        assert!(existing_decks.contains("filtered ★::child"));
        // the case of existing parents is matched
        assert!(existing_decks.contains("PARENT::child"));
        // the case of imported parents is matched, regardless of pass order
        assert!(existing_decks.contains("new parent::child"));
    }
}
