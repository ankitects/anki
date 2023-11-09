// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

use std::collections::HashMap;
use std::mem;

use super::Context;
use crate::decks::NormalDeck;
use crate::prelude::*;

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
            self.maybe_reparent(deck);
            self.import_deck(deck)?;
        }
        Ok(())
    }

    fn import_deck(&mut self, deck: &mut Deck) -> Result<()> {
        if let Some(original) = self.get_deck_by_name(deck)? {
            if original.is_same_kind(deck) {
                return self.update_deck(deck, original);
            } else {
                self.uniquify_name(deck);
            }
        }
        self.ensure_valid_first_existing_parent(deck)?;
        self.add_deck(deck)
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

    /// Caller must ensure decks are of the same kind.
    fn update_deck(&mut self, deck: &Deck, original: Deck) -> Result<()> {
        let mut new_deck = original.clone();
        if let (Ok(new), Ok(old)) = (new_deck.normal_mut(), deck.normal()) {
            update_normal_with_other(new, old);
        } else if let (Ok(new), Ok(old)) = (new_deck.filtered_mut(), deck.filtered()) {
            *new = old.clone();
        } else {
            invalid_input!("decks have different kinds");
        }
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

    fn is_same_kind(&self, other: &Self) -> bool {
        self.is_filtered() == other.is_filtered()
    }
}

fn update_normal_with_other(normal: &mut NormalDeck, other: &NormalDeck) {
    if !other.description.is_empty() {
        normal.markdown_description = other.markdown_description;
        normal.description = other.description.clone();
    }
    if other.config_id != 1 {
        normal.config_id = other.config_id;
    }
    normal.review_limit = other.review_limit.or(normal.review_limit);
    normal.new_limit = other.new_limit.or(normal.new_limit);
    normal.review_limit_today = other.review_limit_today.or(normal.review_limit_today);
    normal.new_limit_today = other.new_limit_today.or(normal.new_limit_today);
}

#[cfg(test)]
mod test {
    use std::collections::HashSet;

    use super::*;

    #[test]
    fn parents() {
        let mut col = Collection::new();

        DeckAdder::new("filtered").filtered(true).add(&mut col);
        DeckAdder::new("PARENT").add(&mut col);

        let mut ctx = DeckContext::new(&mut col, Usn(1));
        ctx.unique_suffix = "★".to_string();

        let imports = vec![
            DeckAdder::new("unknown parent::child").deck(),
            DeckAdder::new("filtered::child").deck(),
            DeckAdder::new("parent::child").deck(),
            DeckAdder::new("NEW PARENT::child").deck(),
            DeckAdder::new("new parent").deck(),
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
