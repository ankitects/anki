// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod addupdate;
mod counts;
mod current;
pub mod filtered;
pub(crate) mod limits;
mod name;
mod remove;
mod reparent;
mod schema11;
mod service;
mod stats;
pub mod tree;
pub(crate) mod undo;

use std::sync::Arc;

pub use anki_proto::decks::deck::filtered::search_term::Order as FilteredSearchOrder;
pub use anki_proto::decks::deck::filtered::SearchTerm as FilteredSearchTerm;
pub use anki_proto::decks::deck::kind_container::Kind as DeckKind;
pub use anki_proto::decks::deck::Common as DeckCommon;
pub use anki_proto::decks::deck::Filtered as FilteredDeck;
pub use anki_proto::decks::deck::KindContainer as DeckKindContainer;
pub use anki_proto::decks::deck::Normal as NormalDeck;
pub use anki_proto::decks::Deck as DeckProto;
pub(crate) use counts::DueCounts;
pub(crate) use name::immediate_parent_name;
pub use name::NativeDeckName;
pub use schema11::DeckSchema11;

use crate::define_newtype;
use crate::error::FilteredDeckError;
use crate::markdown::render_markdown;
use crate::prelude::*;
use crate::text::sanitize_html_no_images;

define_newtype!(DeckId, i64);

impl DeckId {
    pub(crate) fn or(self, other: DeckId) -> Self {
        if self.0 == 0 {
            other
        } else {
            self
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub struct Deck {
    pub id: DeckId,
    pub name: NativeDeckName,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub common: DeckCommon,
    pub kind: DeckKind,
}

impl Deck {
    pub fn new_normal() -> Deck {
        Deck {
            id: DeckId(0),
            name: NativeDeckName::from_native_str(""),
            mtime_secs: TimestampSecs(0),
            usn: Usn(0),
            common: DeckCommon {
                study_collapsed: true,
                browser_collapsed: true,
                ..Default::default()
            },
            kind: DeckKind::Normal(NormalDeck {
                config_id: 1,
                // enable in the future
                // markdown_description = true,
                ..Default::default()
            }),
        }
    }

    /// Returns deck config ID if deck is a normal deck.
    pub fn config_id(&self) -> Option<DeckConfigId> {
        if let DeckKind::Normal(ref norm) = self.kind {
            Some(DeckConfigId(norm.config_id))
        } else {
            None
        }
    }

    // used by tests at the moment

    #[allow(dead_code)]
    pub(crate) fn normal(&self) -> Result<&NormalDeck> {
        match &self.kind {
            DeckKind::Normal(normal) => Ok(normal),
            _ => invalid_input!("deck not normal"),
        }
    }

    #[allow(dead_code)]
    pub(crate) fn normal_mut(&mut self) -> Result<&mut NormalDeck> {
        match &mut self.kind {
            DeckKind::Normal(normal) => Ok(normal),
            _ => invalid_input!("deck not normal"),
        }
    }

    pub(crate) fn filtered(&self) -> Result<&FilteredDeck> {
        if let DeckKind::Filtered(filtered) = &self.kind {
            Ok(filtered)
        } else {
            Err(FilteredDeckError::FilteredDeckRequired.into())
        }
    }

    #[allow(dead_code)]
    pub(crate) fn filtered_mut(&mut self) -> Result<&mut FilteredDeck> {
        if let DeckKind::Filtered(filtered) = &mut self.kind {
            Ok(filtered)
        } else {
            Err(FilteredDeckError::FilteredDeckRequired.into())
        }
    }

    pub(crate) fn set_modified(&mut self, usn: Usn) {
        self.mtime_secs = TimestampSecs::now();
        self.usn = usn;
    }

    pub fn rendered_description(&self) -> String {
        if let DeckKind::Normal(normal) = &self.kind {
            if normal.markdown_description {
                let description = render_markdown(&normal.description);
                // before allowing images, we'll need to handle relative image
                // links on the various platforms
                sanitize_html_no_images(&description)
            } else {
                String::new()
            }
        } else {
            String::new()
        }
    }

    pub fn maybe_inherit_parent_config(&mut self, parent_deck: &Deck) {
        if let DeckKind::Normal(parent_deck) = &parent_deck.kind {
            if let DeckKind::Normal(deck) = &mut self.kind {
                deck.config_id = parent_deck.config_id;
            }
        }
    }
}

impl Collection {
    pub fn get_or_create_normal_deck(&mut self, human_name: &str) -> Result<Deck> {
        let name = NativeDeckName::from_human_name(human_name);
        if let Some(did) = self.storage.get_deck_id(name.as_native_str())? {
            self.storage.get_deck(did).map(|opt| opt.unwrap())
        } else {
            let mut deck = Deck::new_normal();
            deck.name = name;
            self.add_or_update_deck(&mut deck)?;
            Ok(deck)
        }
    }
}

impl Collection {
    pub fn get_deck(&mut self, did: DeckId) -> Result<Option<Arc<Deck>>> {
        if let Some(deck) = self.state.deck_cache.get(&did) {
            return Ok(Some(deck.clone()));
        }
        if let Some(deck) = self.storage.get_deck(did)? {
            let deck = Arc::new(deck);
            self.state.deck_cache.insert(did, deck.clone());
            Ok(Some(deck))
        } else {
            Ok(None)
        }
    }

    pub(crate) fn default_deck_is_empty(&self) -> Result<bool> {
        self.storage.deck_is_empty(DeckId(1))
    }

    /// Get a deck based on its human name. If you have a machine name,
    /// use the method in storage instead.
    pub fn get_deck_id(&self, human_name: &str) -> Result<Option<DeckId>> {
        self.storage
            .get_deck_id(NativeDeckName::from_human_name(human_name).as_native_str())
    }
}

#[cfg(test)]
mod test {
    use crate::prelude::*;
    use crate::search::SortMode;

    fn sorted_names(col: &Collection) -> Vec<String> {
        col.storage
            .get_all_deck_names()
            .unwrap()
            .into_iter()
            .map(|d| d.1)
            .collect()
    }

    #[test]
    fn adding_updating() -> Result<()> {
        let mut col = Collection::new();

        let deck1 = col.get_or_create_normal_deck("foo")?;
        let deck2 = col.get_or_create_normal_deck("FOO")?;
        assert_eq!(deck1.id, deck2.id);
        assert_eq!(sorted_names(&col), vec!["Default", "foo"]);

        // missing parents should be automatically created, and case should match
        // existing parents
        let _deck3 = col.get_or_create_normal_deck("FOO::BAR::BAZ")?;
        assert_eq!(
            sorted_names(&col),
            vec!["Default", "foo", "foo::BAR", "foo::BAR::BAZ"]
        );

        Ok(())
    }

    #[test]
    fn renaming() -> Result<()> {
        let mut col = Collection::new();

        let _ = col.get_or_create_normal_deck("foo::bar::baz")?;
        let mut top_deck = col.get_or_create_normal_deck("foo")?;
        top_deck.name = NativeDeckName::from_native_str("other");
        col.add_or_update_deck(&mut top_deck)?;
        assert_eq!(
            sorted_names(&col),
            vec!["Default", "other", "other::bar", "other::bar::baz"]
        );

        // should do the right thing in the middle of the tree as well
        let mut middle = col.get_or_create_normal_deck("other::bar")?;
        middle.name = NativeDeckName::from_native_str("quux\x1ffoo");
        col.add_or_update_deck(&mut middle)?;
        assert_eq!(
            sorted_names(&col),
            vec!["Default", "other", "quux", "quux::foo", "quux::foo::baz"]
        );

        // add another child
        let _ = col.get_or_create_normal_deck("quux::foo::baz2");

        // quux::foo -> quux::foo::baz::four
        // means quux::foo::baz2 should be quux::foo::baz::four::baz2
        // and a new quux::foo should have been created
        middle.name = NativeDeckName::from_native_str("quux\x1ffoo\x1fbaz\x1ffour");
        col.add_or_update_deck(&mut middle)?;
        assert_eq!(
            sorted_names(&col),
            vec![
                "Default",
                "other",
                "quux",
                "quux::foo",
                "quux::foo::baz",
                "quux::foo::baz::four",
                "quux::foo::baz::four::baz",
                "quux::foo::baz::four::baz2"
            ]
        );

        // should handle name conflicts
        middle.name = NativeDeckName::from_native_str("other");
        col.add_or_update_deck(&mut middle)?;
        assert_eq!(middle.name.as_native_str(), "other+");

        // public function takes human name
        col.rename_deck(middle.id, "one::two")?;
        assert_eq!(
            sorted_names(&col),
            vec![
                "Default",
                "one",
                "one::two",
                "one::two::baz",
                "one::two::baz2",
                "other",
                "quux",
                "quux::foo",
                "quux::foo::baz",
            ]
        );

        Ok(())
    }

    #[test]
    fn default() -> Result<()> {
        // deleting the default deck will remove cards, but bring the deck back
        // as a top level deck
        let mut col = Collection::new();

        let mut default = col.get_or_create_normal_deck("default")?;
        default.name = NativeDeckName::from_native_str("one\x1ftwo");
        col.add_or_update_deck(&mut default)?;

        // create a non-default deck confusingly named "default"
        let _fake_default = col.get_or_create_normal_deck("default")?;

        // add a card to the real default
        let nt = col.get_notetype_by_name("Basic")?.unwrap();
        let mut note = nt.new_note();
        col.add_note(&mut note, default.id)?;
        assert_ne!(col.search_cards("", SortMode::NoOrder)?, vec![]);

        // add a subdeck
        let _ = col.get_or_create_normal_deck("one::two::three")?;

        // delete top level
        let top = col.get_or_create_normal_deck("one")?;
        col.remove_decks_and_child_decks(&[top.id])?;

        // should have come back as "Default+" due to conflict
        assert_eq!(sorted_names(&col), vec!["default", "Default+"]);

        // and the cards it contained should have been removed
        assert_eq!(col.search_cards("", SortMode::NoOrder)?, vec![]);

        Ok(())
    }
}
