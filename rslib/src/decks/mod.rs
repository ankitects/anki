// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

mod add;
mod counts;
mod current;
mod filtered;
mod name;
mod remove;
mod reparent;
mod schema11;
mod tree;
pub(crate) mod undo;

pub use crate::backend_proto::{
    deck::filtered::{search_term::Order as FilteredSearchOrder, SearchTerm as FilteredSearchTerm},
    deck::kind_container::Kind as DeckKind,
    deck::KindContainer as DeckKindContainer,
    deck::{Common as DeckCommon, Filtered as FilteredDeck, Normal as NormalDeck},
    Deck as DeckProto,
};
use crate::{
    backend_proto as pb,
    collection::Collection,
    deckconf::DeckConfId,
    define_newtype,
    error::FilteredDeckError,
    error::{AnkiError, Result},
    markdown::render_markdown,
    prelude::*,
    text::sanitize_html_no_images,
    timestamp::TimestampSecs,
    types::Usn,
};
pub(crate) use counts::DueCounts;
pub(crate) use name::{
    human_deck_name_to_native, immediate_parent_name, native_deck_name_to_human,
};
pub use schema11::DeckSchema11;
use std::sync::Arc;

define_newtype!(DeckId, i64);

#[derive(Debug, Clone, PartialEq)]
pub struct Deck {
    pub id: DeckId,
    pub name: String,
    pub mtime_secs: TimestampSecs,
    pub usn: Usn,
    pub common: DeckCommon,
    pub kind: DeckKind,
}

impl Deck {
    pub fn new_normal() -> Deck {
        Deck {
            id: DeckId(0),
            name: "".into(),
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

    fn reset_stats_if_day_changed(&mut self, today: u32) {
        let c = &mut self.common;
        if c.last_day_studied != today {
            c.new_studied = 0;
            c.learning_studied = 0;
            c.review_studied = 0;
            c.milliseconds_studied = 0;
            c.last_day_studied = today;
        }
    }

    /// Returns deck config ID if deck is a normal deck.
    pub(crate) fn config_id(&self) -> Option<DeckConfId> {
        if let DeckKind::Normal(ref norm) = self.kind {
            Some(DeckConfId(norm.config_id))
        } else {
            None
        }
    }

    // used by tests at the moment

    #[allow(dead_code)]
    pub(crate) fn normal(&self) -> Result<&NormalDeck> {
        if let DeckKind::Normal(normal) = &self.kind {
            Ok(normal)
        } else {
            Err(AnkiError::invalid_input("deck not normal"))
        }
    }

    #[allow(dead_code)]
    pub(crate) fn normal_mut(&mut self) -> Result<&mut NormalDeck> {
        if let DeckKind::Normal(normal) = &mut self.kind {
            Ok(normal)
        } else {
            Err(AnkiError::invalid_input("deck not normal"))
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

    /// Return the studied counts if studied today.
    /// May be negative if user has extended limits.
    pub(crate) fn new_rev_counts(&self, today: u32) -> (i32, i32) {
        if self.common.last_day_studied == today {
            (self.common.new_studied, self.common.review_studied)
        } else {
            (0, 0)
        }
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
}

impl Collection {
    pub(crate) fn get_deck(&mut self, did: DeckId) -> Result<Option<Arc<Deck>>> {
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
}

impl Collection {
    pub(crate) fn default_deck_is_empty(&self) -> Result<bool> {
        self.storage.deck_is_empty(DeckId(1))
    }

    pub fn get_or_create_normal_deck(&mut self, human_name: &str) -> Result<Deck> {
        let native_name = human_deck_name_to_native(human_name);
        if let Some(did) = self.storage.get_deck_id(&native_name)? {
            self.storage.get_deck(did).map(|opt| opt.unwrap())
        } else {
            let mut deck = Deck::new_normal();
            deck.name = native_name;
            self.add_or_update_deck(&mut deck)?;
            Ok(deck)
        }
    }

    /// Get a deck based on its human name. If you have a machine name,
    /// use the method in storage instead.
    pub(crate) fn get_deck_id(&self, human_name: &str) -> Result<Option<DeckId>> {
        let machine_name = human_deck_name_to_native(&human_name);
        self.storage.get_deck_id(&machine_name)
    }

    /// Apply input delta to deck, and its parents.
    /// Caller should ensure transaction.
    pub(crate) fn update_deck_stats(
        &mut self,
        today: u32,
        usn: Usn,
        input: pb::UpdateStatsIn,
    ) -> Result<()> {
        let did = input.deck_id.into();
        let mutator = |c: &mut DeckCommon| {
            c.new_studied += input.new_delta;
            c.review_studied += input.review_delta;
            c.milliseconds_studied += input.millisecond_delta;
        };
        if let Some(mut deck) = self.storage.get_deck(did)? {
            self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            for mut deck in self.storage.parent_decks(&deck)? {
                self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            }
        }
        Ok(())
    }

    /// Modify the deck's limits by adjusting the 'done today' count.
    /// Positive values increase the limit, negative value decrease it.
    /// Caller should ensure a transaction.
    pub(crate) fn extend_limits(
        &mut self,
        today: u32,
        usn: Usn,
        did: DeckId,
        new_delta: i32,
        review_delta: i32,
    ) -> Result<()> {
        let mutator = |c: &mut DeckCommon| {
            c.new_studied -= new_delta;
            c.review_studied -= review_delta;
        };
        if let Some(mut deck) = self.storage.get_deck(did)? {
            self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            for mut deck in self.storage.parent_decks(&deck)? {
                self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            }
            for mut deck in self.storage.child_decks(&deck)? {
                self.update_deck_stats_single(today, usn, &mut deck, mutator)?;
            }
        }

        Ok(())
    }

    pub(crate) fn counts_for_deck_today(
        &mut self,
        did: DeckId,
    ) -> Result<pb::CountsForDeckTodayOut> {
        let today = self.current_due_day(0)?;
        let mut deck = self.storage.get_deck(did)?.ok_or(AnkiError::NotFound)?;
        deck.reset_stats_if_day_changed(today);
        Ok(pb::CountsForDeckTodayOut {
            new: deck.common.new_studied,
            review: deck.common.review_studied,
        })
    }

    fn update_deck_stats_single<F>(
        &mut self,
        today: u32,
        usn: Usn,
        deck: &mut Deck,
        mutator: F,
    ) -> Result<()>
    where
        F: FnOnce(&mut DeckCommon),
    {
        let original = deck.clone();
        deck.reset_stats_if_day_changed(today);
        mutator(&mut deck.common);
        deck.set_modified(usn);
        self.update_single_deck_undoable(deck, original)
    }
}

#[cfg(test)]
mod test {
    use crate::{
        collection::{open_test_collection, Collection},
        error::Result,
        search::SortMode,
    };

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
        let mut col = open_test_collection();

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
        let mut col = open_test_collection();

        let _ = col.get_or_create_normal_deck("foo::bar::baz")?;
        let mut top_deck = col.get_or_create_normal_deck("foo")?;
        top_deck.name = "other".into();
        col.add_or_update_deck(&mut top_deck)?;
        assert_eq!(
            sorted_names(&col),
            vec!["Default", "other", "other::bar", "other::bar::baz"]
        );

        // should do the right thing in the middle of the tree as well
        let mut middle = col.get_or_create_normal_deck("other::bar")?;
        middle.name = "quux\x1ffoo".into();
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
        middle.name = "quux\x1ffoo\x1fbaz\x1ffour".into();
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
        middle.name = "other".into();
        col.add_or_update_deck(&mut middle)?;
        assert_eq!(middle.name, "other+");

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
        let mut col = open_test_collection();

        let mut default = col.get_or_create_normal_deck("default")?;
        default.name = "one\x1ftwo".into();
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
